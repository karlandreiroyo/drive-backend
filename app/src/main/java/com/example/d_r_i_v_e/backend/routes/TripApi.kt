package com.example.d_r_i_v_e.backend.routes

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.d_r_i_v_e.Incident
import com.example.d_r_i_v_e.TripEntry
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.database.TripWithIncidents
import com.example.d_r_i_v_e.repository.DriveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TripHistoryUiState(
    val isLoading: Boolean = true,
    val trips: List<TripEntry> = emptyList()
)

/**
 * Reads completed trips straight from Room, so history is available offline
 * and updates the moment the sync worker, a new trip, or a delete lands.
 */
class TripHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DriveRepository.getInstance(application)

    private val _state = MutableStateFlow(TripHistoryUiState())
    val state: StateFlow<TripHistoryUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var observingUserId: String? = null

    fun refresh() {
        val userId = AuthSession.user?.id?.takeIf { it.isNotBlank() }
        if (userId == null) {
            clear()
            return
        }

        if (userId != observingUserId || observeJob?.isActive != true) {
            observingUserId = userId
            observeJob?.cancel()
            _state.value = TripHistoryUiState(isLoading = true)
            observeJob = viewModelScope.launch {
                repository.observeHistory(userId).collect { rows ->
                    _state.value = TripHistoryUiState(
                        isLoading = false,
                        trips = rows.map { it.toTripEntry() }
                    )
                }
            }
        }

        // Pull the user's remote history into Room (no-op offline), then drain
        // any local outbox. The Room observer above emits the merged result.
        viewModelScope.launch {
            runCatching { repository.refreshHistoryFromRemote() }
                .onFailure { Log.w(TAG, "Couldn't refresh remote history", it) }
        }
        repository.requestSync()
    }

    fun deleteTrip(tripId: String) {
        _state.update { state ->
            state.copy(trips = state.trips.filterNot { it.id == tripId })
        }
        viewModelScope.launch {
            runCatching { repository.deleteTrip(tripId) }
                .onFailure { Log.w(TAG, "Couldn't delete trip $tripId", it) }
        }
    }

    fun clear() {
        observeJob?.cancel()
        observeJob = null
        observingUserId = null
        _state.value = TripHistoryUiState()
    }

    private companion object {
        const val TAG = "TripHistoryViewModel"
    }
}

private fun TripWithIncidents.toTripEntry(): TripEntry = TripEntry(
    id = trip.id,
    dateLabel = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US)
        .format(Date(trip.startTime)),
    totalDuration = formatDurationText(trip.startTime, trip.endTime),
    incidents = incidents
        .sortedBy { it.deviceTimestamp }
        .map {
            Incident(
                description = it.description,
                time = it.time,
                type = it.incidentType,
                confidence = it.confidence
            )
        }
)

private fun formatDurationText(startTime: Long, endTime: Long?): String {
    if (endTime == null || endTime <= startTime) return "Duration unavailable"
    val totalMinutes = (endTime - startTime) / 60_000
    if (totalMinutes < 1) return "Less than 1 min total driving"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes mins total driving"
        hours > 0 -> "$hours hr total driving"
        else -> "$minutes mins total driving"
    }
}
