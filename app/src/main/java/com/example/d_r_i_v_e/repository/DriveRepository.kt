package com.example.d_r_i_v_e.repository

import android.content.Context
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.routes.SyncApiClient
import com.example.d_r_i_v_e.backend.routes.SyncIncidentDto
import com.example.d_r_i_v_e.backend.routes.SyncOutcome
import com.example.d_r_i_v_e.backend.routes.SyncRequestDto
import com.example.d_r_i_v_e.backend.routes.SyncTripDto
import com.example.d_r_i_v_e.database.DriveDatabase
import com.example.d_r_i_v_e.database.TripEntity
import com.example.d_r_i_v_e.database.TripIncidentEntity
import com.example.d_r_i_v_e.database.TripWithIncidents
import com.example.d_r_i_v_e.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Single source of truth for drive data. Every write lands in Room first and
 * schedules a network-constrained sync; nothing in the detection or alert path
 * waits on the network.
 */
class DriveRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val tripDao = DriveDatabase.getInstance(appContext).tripDao()
    private val incidentDao = DriveDatabase.getInstance(appContext).tripIncidentDao()

    fun observeHistory(userId: String): Flow<List<TripWithIncidents>> =
        tripDao.observeHistory(userId)

    suspend fun startTrip(
        userId: String,
        startedAt: Long = System.currentTimeMillis()
    ): TripEntity {
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startedAt,
            createdAt = startedAt,
            status = TripEntity.STATUS_IN_PROGRESS,
            origin = "Current location",
            destination = "Destination"
        )
        tripDao.upsert(trip)
        return trip
    }

    suspend fun recordIncident(
        trip: TripEntity,
        label: String,
        confidence: Float,
        deviceTimestamp: Long = System.currentTimeMillis()
    ): TripIncidentEntity {
        val boundedConfidence = confidence.coerceIn(0f, 1f)
        val incident = TripIncidentEntity(
            id = UUID.randomUUID().toString(),
            tripId = trip.id,
            userId = trip.userId,
            description = "$label · ${(boundedConfidence * 100).toInt()}% confidence",
            time = SimpleDateFormat("h:mm a", Locale.US).format(Date(deviceTimestamp)),
            incidentType = label,
            confidence = boundedConfidence,
            deviceTimestamp = deviceTimestamp,
            isSynced = false,
            emailSent = false,
            createdAt = deviceTimestamp
        )
        incidentDao.upsert(incident)
        SyncScheduler.enqueue(appContext)
        return incident
    }

    suspend fun finishTrip(
        tripId: String,
        finishedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val trip = tripDao.findById(tripId) ?: return false
        if (trip.status == TripEntity.STATUS_IN_PROGRESS) {
            tripDao.finish(
                tripId = tripId,
                endTime = finishedAt,
                status = TripEntity.STATUS_COMPLETED,
                destination = trip.destination.ifBlank { "Destination" }
            )
            // The in-progress row may already be on Supabase; push the final
            // end_time/status on the next sync.
            tripDao.markUnsynced(tripId)
        }
        SyncScheduler.enqueue(appContext)
        return true
    }

    /** Closes a session that was left open when the process was killed. */
    suspend fun closeOrphanedTrips() {
        val orphan = tripDao.latestActiveTrip() ?: return
        tripDao.finish(
            tripId = orphan.id,
            endTime = maxOf(System.currentTimeMillis(), orphan.startTime),
            status = TripEntity.STATUS_COMPLETED,
            destination = orphan.destination.ifBlank { "Destination" }
        )
        tripDao.markUnsynced(orphan.id)
        SyncScheduler.enqueue(appContext)
    }

    suspend fun deleteTrip(tripId: String) {
        val trip = tripDao.findById(tripId) ?: return
        if (trip.isSynced) {
            // Keep the row as a tombstone until the server confirms the delete.
            tripDao.markDeleted(tripId)
            SyncScheduler.enqueue(appContext)
        } else {
            tripDao.delete(tripId)
        }
    }

    fun requestSync() = SyncScheduler.enqueue(appContext)

    suspend fun syncPending(): SyncOutcome {
        val userId = AuthSession.user?.id
        if (userId.isNullOrBlank()) return SyncOutcome.Unauthorized

        val trips = tripDao.unsyncedTrips(userId)
        val incidents = incidentDao.unsyncedIncidents(userId)
        val deletes = tripDao.pendingDeletes(userId)

        // Sent even when everything is already synced: the backend uses this
        // call to retry emergency emails that Brevo could not deliver earlier.
        val request = SyncRequestDto(
            trips = trips.map { it.toSyncDto() },
            incidents = incidents.map { it.toSyncDto() },
            deleteTripIds = deletes.map { it.id }
        )

        val outcome = SyncApiClient.sync(request)
        if (outcome is SyncOutcome.Success) {
            val response = outcome.response
            if (response.tripsSynced.isNotEmpty()) {
                tripDao.markSynced(response.tripsSynced)
            }
            if (response.incidentsSynced.isNotEmpty()) {
                incidentDao.markSynced(response.incidentsSynced)
            }
            if (response.emailSentIds.isNotEmpty()) {
                incidentDao.markEmailSent(response.emailSentIds)
            }
            if (response.deletedTripIds.isNotEmpty()) {
                tripDao.deleteByIds(response.deletedTripIds)
            }
        }
        return outcome
    }

    companion object {
        @Volatile
        private var instance: DriveRepository? = null

        fun getInstance(context: Context): DriveRepository =
            instance ?: synchronized(this) {
                instance ?: DriveRepository(context).also { instance = it }
            }
    }
}

private fun TripEntity.toSyncDto(): SyncTripDto = SyncTripDto(
    id = id,
    userId = userId,
    startTime = startTime.toUtcIso(),
    endTime = endTime?.toUtcIso(),
    status = status,
    origin = origin,
    destination = destination,
    createdAt = createdAt.toUtcIso()
)

private fun TripIncidentEntity.toSyncDto(): SyncIncidentDto = SyncIncidentDto(
    id = id,
    tripId = tripId,
    userId = userId,
    description = description,
    time = time,
    incidentType = incidentType,
    confidence = confidence.toDouble(),
    deviceTimestamp = deviceTimestamp,
    isSynced = true,
    createdAt = createdAt.toUtcIso()
)

private fun Long.toUtcIso(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this))
