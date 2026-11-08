package com.example.d_r_i_v_e.backend.routes

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.database.TripEntity
import com.example.d_r_i_v_e.ml.DetectionResult
import com.example.d_r_i_v_e.repository.DriveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetectionEvent(
    val label: String,
    val confidence: Float,
    val timestamp: Long
)

/**
 * State of the on-device write. Detection never waits for the network; the
 * sync worker uploads [LocalLogStatus.SavedOffline] rows later.
 */
enum class LocalLogStatus { Idle, Saving, SavedOffline, Failed }

/**
 * Owns the driving session: opens a local trip, appends incidents to Room, and
 * closes the trip. Internet access lives entirely in [com.example.d_r_i_v_e.sync.TripSyncWorker].
 */
class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DriveRepository.getInstance(application)

    private val _sessionDetectionCount = MutableStateFlow(0)
    val sessionDetectionCount: StateFlow<Int> = _sessionDetectionCount.asStateFlow()

    private val _sessionIncidents = MutableStateFlow<List<DetectionEvent>>(emptyList())
    val sessionIncidents: StateFlow<List<DetectionEvent>> = _sessionIncidents.asStateFlow()

    private val _sessionStartedAt = MutableStateFlow<Long?>(null)
    val sessionStartedAt: StateFlow<Long?> = _sessionStartedAt.asStateFlow()

    private val _lastEvent = MutableStateFlow<DetectionEvent?>(null)
    val lastEvent: StateFlow<DetectionEvent?> = _lastEvent.asStateFlow()

    private val _logStatus = MutableStateFlow(LocalLogStatus.Idle)
    val logStatus: StateFlow<LocalLogStatus> = _logStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // The camera must not start before this row exists, so the UI waits for
    // [onReady] instead of trusting a fire-and-forget insert.
    private var activeTrip: TripEntity? = null

    fun startSession(onReady: () -> Unit = {}) {
        if (activeTrip != null) {
            onReady()
            return
        }

        val userId = AuthSession.user?.id?.takeIf { it.isNotBlank() }
        if (userId == null) {
            _logStatus.value = LocalLogStatus.Failed
            _statusMessage.value = "Log in again to start a driving session."
            return
        }

        val startedAt = System.currentTimeMillis()
        _sessionStartedAt.value = startedAt
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null

        viewModelScope.launch {
            try {
                activeTrip = repository.startTrip(userId, startedAt)
                onReady()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't open a local trip", e)
                _sessionStartedAt.value = null
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't start the session on this device."
            }
        }
    }

    fun onDetection(result: DetectionResult) {
        val trip = activeTrip ?: return

        _sessionDetectionCount.update { it + 1 }
        val event = DetectionEvent(
            label = result.label,
            confidence = result.confidence,
            timestamp = System.currentTimeMillis()
        )
        _lastEvent.value = event
        _sessionIncidents.update { (it + event).takeLast(500) }

        val timestamp = event.timestamp
        viewModelScope.launch {
            _logStatus.value = LocalLogStatus.Saving
            try {
                repository.recordIncident(
                    trip = trip,
                    label = result.label,
                    confidence = result.confidence,
                    deviceTimestamp = timestamp
                )
                _logStatus.value = LocalLogStatus.SavedOffline
                _statusMessage.value = null
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't store incident locally", e)
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't save this incident on the device."
            }
        }
    }

    fun finishSession(onResult: (String) -> Unit = {}) {
        val trip = activeTrip
        val startedAt = _sessionStartedAt.value
        val finishedAt = System.currentTimeMillis()

        activeTrip = null
        _sessionStartedAt.value = null
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null

        if (trip == null || startedAt == null) {
            onResult("No active driving session.")
            return
        }

        viewModelScope.launch {
            val saved = try {
                repository.finishTrip(trip.id, finishedAt)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't close the local trip", e)
                false
            }
            onResult(
                if (saved) {
                    "Trip saved to history. Sync runs automatically when online."
                } else {
                    "Couldn't save this trip on the device."
                }
            )
        }
    }

    fun resetSession() {
        // Sign-out or session expiry can arrive mid-drive; close the local row
        // so it is not left as an eternal IN_PROGRESS trip.
        activeTrip?.let { trip ->
            viewModelScope.launch {
                runCatching { repository.finishTrip(trip.id, System.currentTimeMillis()) }
            }
        }
        activeTrip = null
        _sessionStartedAt.value = null
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null
    }

    private companion object {
        const val TAG = "DetectionViewModel"
    }
}
