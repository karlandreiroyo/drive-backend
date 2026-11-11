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
enum class DrivingSessionState { Idle, Starting, Active, Stopping }

/** Owns the driving session while the repository routes writes online or offline. */
class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DriveRepository.getInstance(application)

    private val _sessionState = MutableStateFlow(DrivingSessionState.Idle)
    val sessionState: StateFlow<DrivingSessionState> = _sessionState.asStateFlow()
    private var sessionGeneration = 0

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

    @Volatile
    private var activeTrip: TripEntity? = null

    fun startSession() {
        if (_sessionState.value != DrivingSessionState.Idle) return

        val userId = AuthSession.user?.id?.takeIf { it.isNotBlank() }
        if (userId == null || AuthSession.token.isNullOrBlank()) {
            _logStatus.value = LocalLogStatus.Failed
            _statusMessage.value = "Log in again to start a driving session."
            return
        }

        val generation = ++sessionGeneration
        val startedAt = System.currentTimeMillis()
        _sessionState.value = DrivingSessionState.Starting
        _sessionStartedAt.value = null
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _monitorFrame.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null
        Log.d(TAG, "Session startup: opening trip")

        viewModelScope.launch {
            try {
                val trip = repository.startTrip(userId, startedAt)
                // A completed request must not reactivate a session after logout.
                if (generation != sessionGeneration || AuthSession.user?.id != userId ||
                    AuthSession.token.isNullOrBlank()
                ) {
                    repository.closeTripOffline(trip.id)
                    if (generation == sessionGeneration) resetSession()
                    return@launch
                }
                activeTrip = trip
                _sessionStartedAt.value = trip.startTime
                _sessionState.value = DrivingSessionState.Active
                startHardwarePolling()
                Log.d(TAG, "Session startup: trip ready, synced=${trip.isSynced}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Session startup failed while opening trip", e)
                if (generation != sessionGeneration) return@launch
                activeTrip = null
                _sessionState.value = DrivingSessionState.Idle
                _sessionStartedAt.value = null
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't start the driving session (${e.javaClass.simpleName}). Check Logcat: $TAG."
            }
        }
    }

    fun onCameraError(message: String) {
        if (_sessionState.value != DrivingSessionState.Active) return
        resetSession()
        _logStatus.value = LocalLogStatus.Failed
        _statusMessage.value = message
    }

    /** Called on the analyzer thread for every analyzed frame. */
    fun onMonitorFrame(frame: DriverMonitorFrame) {
        val trip = activeTrip ?: return
        viewModelScope.launch {
            if (activeTrip?.id == trip.id) _monitorFrame.value = frame
        }
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
        val event = DetectionEvent(
            label = result.label,
            confidence = result.confidence,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            if (activeTrip?.id != trip.id || _sessionState.value != DrivingSessionState.Active) return@launch
            _sessionDetectionCount.update { it + 1 }
            _lastEvent.value = event
            _sessionIncidents.update { (it + event).takeLast(500) }
            _logStatus.value = LocalLogStatus.Saving
            try {
                val incident = repository.recordIncident(
                    trip = trip,
                    label = result.label,
                    confidence = result.confidence,
                    deviceTimestamp = event.timestamp
                )
                if (activeTrip?.id != trip.id) return@launch
                _logStatus.value = if (incident.isSynced) {
                    LocalLogStatus.SavedOnline
                } else {
                    LocalLogStatus.SavedOffline
                }
                _statusMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't save incident", e)
                if (activeTrip?.id != trip.id) return@launch
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't save this incident."
            }
        }
    }

    fun finishSession(onResult: (String) -> Unit = {}) {
        if (_sessionState.value != DrivingSessionState.Active) return
        val trip = activeTrip ?: return
        val generation = ++sessionGeneration
        val finishedAt = System.currentTimeMillis()

        _sessionState.value = DrivingSessionState.Stopping
        activeTrip = null
        _sessionStartedAt.value = null
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _monitorFrame.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null
        stopHardwarePolling()

        viewModelScope.launch {
            val saved = try {
                repository.finishTrip(trip.id, finishedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't close trip remotely; trying local storage", e)
                try {
                    repository.closeTripOffline(trip.id, finishedAt)
                    true
                } catch (localError: CancellationException) {
                    throw localError
                } catch (localError: Exception) {
                    Log.e(TAG, "Couldn't close trip locally", localError)
                    false
                }
            }
            if (generation != sessionGeneration) return@launch
            _sessionState.value = DrivingSessionState.Idle
            if (!saved) {
                _logStatus.value = LocalLogStatus.Failed
                _statusMessage.value = "Couldn't save this trip. Check Logcat: $TAG."
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
        ++sessionGeneration
        val trip = activeTrip
        activeTrip = null
        _sessionState.value = DrivingSessionState.Idle
        _sessionStartedAt.value = null
        _sessionDetectionCount.value = 0
        _sessionIncidents.value = emptyList()
        _lastEvent.value = null
        _monitorFrame.value = null
        _logStatus.value = LocalLogStatus.Idle
        _statusMessage.value = null
        stopHardwarePolling()
        if (trip != null) {
            viewModelScope.launch {
                try {
                    repository.closeTripOffline(trip.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't close trip on reset", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DetectionViewModel"
    }
}
