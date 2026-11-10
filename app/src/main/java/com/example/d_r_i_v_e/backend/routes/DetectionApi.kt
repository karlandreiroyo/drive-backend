package com.example.d_r_i_v_e.backend.routes

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.database.TripEntity
import com.example.d_r_i_v_e.ml.DetectionResult
import com.example.d_r_i_v_e.ml.DriverMonitorFrame
import com.example.d_r_i_v_e.repository.DriveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DetectionEvent(
    val label: String,
    val confidence: Float,
    val timestamp: Long
)

/** State of the repository write: online acknowledgement or offline queue. */
enum class LocalLogStatus { Idle, Saving, SavedOnline, SavedOffline, Failed }

/** Owns the driving session while the repository routes writes online or offline. */
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

    // Latest per-frame metrics from the monitor; drives the Live Feed cards.
    private val _monitorFrame = MutableStateFlow<DriverMonitorFrame?>(null)
    val monitorFrame: StateFlow<DriverMonitorFrame?> = _monitorFrame.asStateFlow()

    // Live status of the Raspberry Pi + ESP32 node, polled while a session runs.
    private val _hardwareStatus = MutableStateFlow(HardwareStatus())
    val hardwareStatus: StateFlow<HardwareStatus> = _hardwareStatus.asStateFlow()
    private var hardwarePollJob: Job? = null

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
        _monitorFrame.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null

        viewModelScope.launch {
            try {
                activeTrip = repository.startTrip(userId, startedAt)
                startHardwarePolling()
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't open a trip", e)
                _sessionStartedAt.value = null
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't start the driving session."
            }
        }
    }

    /** Called on the analyzer thread for every analyzed frame. */
    fun onMonitorFrame(frame: DriverMonitorFrame) {
        _monitorFrame.value = frame
    }

    private fun startHardwarePolling() {
        if (hardwarePollJob?.isActive == true) return
        hardwarePollJob = viewModelScope.launch {
            while (isActive) {
                _hardwareStatus.value = DeviceApiClient.fetchStatus()
                delay(2_500)
            }
        }
    }

    private fun stopHardwarePolling() {
        hardwarePollJob?.cancel()
        hardwarePollJob = null
        _hardwareStatus.value = HardwareStatus()
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
                val incident = repository.recordIncident(
                    trip = trip,
                    label = result.label,
                    confidence = result.confidence,
                    deviceTimestamp = timestamp
                )
                _logStatus.value = if (incident.isSynced) {
                    LocalLogStatus.SavedOnline
                } else {
                    LocalLogStatus.SavedOffline
                }
                _statusMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save incident", e)
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't save this incident."
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
        _monitorFrame.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null
        stopHardwarePolling()

        if (trip == null || startedAt == null) {
            onResult("No active driving session.")
            return
        }

        viewModelScope.launch {
            val saved = try {
                repository.finishTrip(trip.id, finishedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't close trip", e)
                false
            }
            onResult(
                if (saved) {
                    "Trip saved. Offline changes sync automatically when online."
                } else {
                    "Couldn't save this trip."
                }
            )
        }
    }

    fun resetSession() {
        // Sign-out or session expiry can arrive mid-drive, after the token is
        // already gone. Close the row locally (no auth/network) so it is not left
        // as an eternal IN_PROGRESS trip; the worker syncs it on next login.
        activeTrip?.let { trip ->
            val tripId = trip.id
            viewModelScope.launch {
                runCatching { repository.closeTripOffline(tripId, System.currentTimeMillis()) }
                    .onFailure { Log.w(TAG, "Couldn't close trip on reset", it) }
            }
        }
        activeTrip = null
        _sessionStartedAt.value = null
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _monitorFrame.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null
        stopHardwarePolling()
    }

    private companion object {
        const val TAG = "DetectionViewModel"
    }
}
