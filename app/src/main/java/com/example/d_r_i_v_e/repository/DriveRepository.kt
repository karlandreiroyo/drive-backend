package com.example.d_r_i_v_e.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.routes.HistoryOutcome
import com.example.d_r_i_v_e.backend.routes.RemoteIncidentDto
import com.example.d_r_i_v_e.backend.routes.RemoteTripDto
import com.example.d_r_i_v_e.backend.routes.SyncApiClient
import com.example.d_r_i_v_e.backend.routes.SyncIncidentDto
import com.example.d_r_i_v_e.backend.routes.SyncOutcome
import com.example.d_r_i_v_e.backend.routes.SyncRequestDto
import com.example.d_r_i_v_e.backend.routes.SyncResponseDto
import com.example.d_r_i_v_e.backend.routes.SyncTripDto
import com.example.d_r_i_v_e.database.DriveDatabase
import com.example.d_r_i_v_e.database.TripEntity
import com.example.d_r_i_v_e.database.TripIncidentEntity
import com.example.d_r_i_v_e.database.TripWithIncidents
import com.example.d_r_i_v_e.sync.NetworkMonitor
import com.example.d_r_i_v_e.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Trip writes prefer the server; incidents are durable locally before upload. */
class DriveRepository internal constructor(
    context: Context,
    private val database: DriveDatabase = DriveDatabase.getInstance(context.applicationContext),
    private val upload: suspend (SyncRequestDto) -> SyncOutcome = { request ->
        uploadForCurrentUser(context.applicationContext, request)
    },
    private val enqueueSync: () -> Unit = { SyncScheduler.enqueue(context.applicationContext) }
) {
    private val appContext = context.applicationContext
    private val tripDao = database.tripDao()
    private val incidentDao = database.tripIncidentDao()
    private val writeMutex = Mutex()

    fun observeHistory(userId: String): Flow<List<TripWithIncidents>> =
        tripDao.observeHistory(userId)

    suspend fun startTrip(
        userId: String,
        startedAt: Long = System.currentTimeMillis()
    ): TripEntity = writeMutex.withLock {
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            startTime = startedAt,
            createdAt = startedAt,
            status = TripEntity.STATUS_IN_PROGRESS,
            origin = "Current location",
            destination = "Destination"
        )

        Log.d(TAG, "Trip startup: attempting remote write")
        val outcome = withTimeoutOrNull(START_UPLOAD_TIMEOUT_MS) {
            upload(tripRequest(trip))
        } ?: SyncOutcome.ConnectionFailed("Trip startup upload timed out.")
        if (outcome is SyncOutcome.Unauthorized) throw writeFailure(outcome)

        val acknowledged = outcome is SyncOutcome.Success && trip.id in outcome.response.tripsSynced
        if (!acknowledged) {
            Log.w(TAG, "Trip startup queued locally; outcome=${outcome.javaClass.simpleName}, acknowledged=false")
        }
        val storedTrip = trip.copy(isSynced = acknowledged)
        tripDao.upsert(storedTrip)
        if (!acknowledged) enqueueSync()
        storedTrip
    }

    suspend fun recordIncident(
        trip: TripEntity,
        label: String,
        confidence: Float,
        deviceTimestamp: Long = System.currentTimeMillis()
    ): TripIncidentEntity = writeMutex.withLock {
        val boundedConfidence = if (confidence.isFinite()) confidence.coerceIn(0f, 1f) else 0f
        val incident = TripIncidentEntity(
            id = UUID.randomUUID().toString(),
            tripId = trip.id,
            userId = trip.userId,
            description = "$label · ${(boundedConfidence * 100).toInt()}% confidence",
            time = SimpleDateFormat("h:mm a", Locale.US).format(Date(deviceTimestamp)),
            incidentType = label,
            confidence = boundedConfidence,
            deviceTimestamp = deviceTimestamp,
            createdAt = deviceTimestamp
        )
        val currentTrip = database.withTransaction {
            val localTrip = tripDao.findById(trip.id)
            val parent = localTrip ?: trip.copy(isSynced = false)
            if (parent.isDeleted) throw SyncWriteException("Cannot add an incident to a deleted trip.")
            if (parent.userId != incident.userId) throw SyncWriteException("Incident and trip owners do not match.")
            if (localTrip == null) tripDao.upsert(parent)
            incidentDao.upsert(incident)
            parent
        }
        Log.d(TAG, "Incident committed to local outbox")
        try {
            enqueueSync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Incident saved locally, but scheduling sync failed", e)
        }

        val parentNeedsSync = !currentTrip.isSynced
        val request = SyncRequestDto(
            trips = if (parentNeedsSync) listOf(currentTrip.toSyncDto()) else emptyList(),
            incidents = listOf(incident.toSyncDto()),
            deleteTripIds = emptyList()
        )
        try {
            val outcome = withTimeoutOrNull(INCIDENT_UPLOAD_TIMEOUT_MS) { upload(request) }
            if (outcome !is SyncOutcome.Success) {
                Log.w(TAG, "Incident saved locally; sync pending (${outcome?.javaClass?.simpleName ?: "timeout"})")
                return@withLock incident
            }
            val response = outcome.response
            val parentAcknowledged = !parentNeedsSync || currentTrip.id in response.tripsSynced
            val incidentAcknowledged = parentAcknowledged && incident.id in response.incidentsSynced
            database.withTransaction {
                if (parentNeedsSync && parentAcknowledged) tripDao.markSynced(listOf(currentTrip.id))
                if (incidentAcknowledged) {
                    incidentDao.markSynced(listOf(incident.id))
                    if (incident.id in response.emailSentIds) incidentDao.markEmailSent(listOf(incident.id))
                }
            }
            if (!incidentAcknowledged) Log.w(TAG, "Incident saved locally; server acknowledgement missing")
            incident.copy(
                isSynced = incidentAcknowledged,
                emailSent = incidentAcknowledged && incident.id in response.emailSentIds
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Incident remains saved locally; upload or acknowledgement failed", e)
            incident
        }
    }

    suspend fun finishTrip(
        tripId: String,
        finishedAt: Long = System.currentTimeMillis()
    ): Boolean = writeMutex.withLock {
        val trip = tripDao.findById(tripId) ?: return@withLock false
        if (trip.status != TripEntity.STATUS_IN_PROGRESS) return@withLock true

        val destination = trip.destination.ifBlank { "Destination" }
        val completedTrip = trip.copy(
            endTime = finishedAt,
            status = TripEntity.STATUS_COMPLETED,
            destination = destination
        )

        when (val outcome = upload(tripRequest(completedTrip))) {
            is SyncOutcome.Success -> {
                requireAck(outcome.response.tripsSynced, tripId, "trip")
                tripDao.finish(
                    tripId = tripId,
                    endTime = finishedAt,
                    status = TripEntity.STATUS_COMPLETED,
                    destination = destination,
                    isSynced = true
                )
                true
            }

            is SyncOutcome.ConnectionFailed -> {
                tripDao.finish(
                    tripId = tripId,
                    endTime = finishedAt,
                    status = TripEntity.STATUS_COMPLETED,
                    destination = destination,
                    isSynced = false
                )
                enqueueSync()
                true
            }

            else -> throw writeFailure(outcome)
        }
    }

    /**
     * Local-only close for logout/session-expiry, where the token may already be
     * gone. Marks the trip completed and unsynced so the worker uploads it on the
     * next authenticated sync; never touches the network or requires auth.
     */
    suspend fun closeTripOffline(
        tripId: String,
        finishedAt: Long = System.currentTimeMillis()
    ) = writeMutex.withLock {
        val trip = tripDao.findById(tripId) ?: return@withLock
        if (trip.status != TripEntity.STATUS_IN_PROGRESS) return@withLock
        tripDao.finish(
            tripId = tripId,
            endTime = maxOf(finishedAt, trip.startTime),
            status = TripEntity.STATUS_COMPLETED,
            destination = trip.destination.ifBlank { "Destination" },
            isSynced = false
        )
        enqueueSync()
    }

    /** Crash recovery marks the local session dirty so the constrained worker reconciles it. */
    suspend fun closeOrphanedTrips() {
        val orphan = tripDao.latestActiveTrip() ?: return
        writeMutex.withLock {
            val current = tripDao.findById(orphan.id) ?: return@withLock
            if (current.status == TripEntity.STATUS_IN_PROGRESS) {
                tripDao.finish(
                    tripId = current.id,
                    endTime = maxOf(System.currentTimeMillis(), current.startTime),
                    status = TripEntity.STATUS_COMPLETED,
                    destination = current.destination.ifBlank { "Destination" },
                    isSynced = false
                )
                enqueueSync()
            }
        }
    }

    suspend fun deleteTrip(tripId: String) = writeMutex.withLock {
        if (tripDao.findById(tripId) == null) return@withLock
        tripDao.markDeleted(tripId)
        enqueueSync()
    }

    fun requestSync() = enqueueSync()

    /**
     * Pulls the user's remote history into the local cache on login/startup.
     * The network call runs outside [writeMutex] so a slow fetch never blocks the
     * live detection write path; only the merge is serialized against writes.
     */
    suspend fun refreshHistoryFromRemote(): HistoryOutcome {
        val userId = AuthSession.user?.id?.takeIf { it.isNotBlank() }
        if (userId == null || AuthSession.token.isNullOrBlank()) {
            return HistoryOutcome.Unauthorized
        }
        if (!NetworkMonitor.isOnline(appContext)) {
            return HistoryOutcome.ConnectionFailed("Device is offline.")
        }

        val outcome = SyncApiClient.history()
        if (outcome is HistoryOutcome.Success) {
            writeMutex.withLock { mergeRemoteHistory(userId, outcome.response.trips) }
        }
        return outcome
    }

    suspend fun syncPending(): SyncOutcome = writeMutex.withLock {
        val userId = AuthSession.user?.id
        if (userId.isNullOrBlank()) return@withLock SyncOutcome.Unauthorized
        if (!NetworkMonitor.isOnline(appContext)) {
            return@withLock SyncOutcome.ConnectionFailed("Device is offline.")
        }

        lateinit var lastOutcome: SyncOutcome
        var fullBatch: Boolean
        do {
            val trips = tripDao.unsyncedTrips(userId).take(MAX_TRIPS)
            val incidents = incidentDao.unsyncedIncidents(userId).take(MAX_INCIDENTS)
            val deletes = tripDao.pendingDeletes(userId).take(MAX_DELETES)
            val outcome = SyncApiClient.sync(
                SyncRequestDto(
                    trips = trips.map { it.toSyncDto() },
                    incidents = incidents.map { it.toSyncDto() },
                    deleteTripIds = deletes.map { it.id }
                )
            )
            if (outcome !is SyncOutcome.Success) return@withLock outcome

            val response = outcome.response
            applySyncResponse(response)
            lastOutcome = outcome
            fullBatch = trips.size == MAX_TRIPS || incidents.size == MAX_INCIDENTS ||
                deletes.size == MAX_DELETES
            if (!fullBatch) return@withLock outcome

            val syncedTrips = response.tripsSynced.toSet()
            val syncedIncidents = response.incidentsSynced.toSet()
            val deletedTrips = response.deletedTripIds.toSet()
            val progressed = trips.any { it.id in syncedTrips } ||
                incidents.any { it.id in syncedIncidents } ||
                deletes.any { it.id in deletedTrips }
            if (!progressed) {
                return@withLock SyncOutcome.Failed("The server did not acknowledge any queued rows.")
            }
        } while (fullBatch)
        return@withLock lastOutcome
    }

    private suspend fun applySyncResponse(response: SyncResponseDto) {
        if (response.tripsSynced.isNotEmpty()) tripDao.markSynced(response.tripsSynced)
        if (response.incidentsSynced.isNotEmpty()) incidentDao.markSynced(response.incidentsSynced)
        if (response.emailSentIds.isNotEmpty()) incidentDao.markEmailSent(response.emailSentIds)
        if (response.deletedTripIds.isNotEmpty()) tripDao.deleteByIds(response.deletedTripIds)
    }

    /**
     * Upserts remote rows as synced without ever deleting local data or
     * overwriting a row that still has pending uploads or a pending delete.
     * Runs under [writeMutex]; scoped strictly to the authenticated [userId].
     */
    private suspend fun mergeRemoteHistory(userId: String, remoteTrips: List<RemoteTripDto>) {
        if (remoteTrips.isEmpty()) return
        val pendingTripIds = tripDao.locallyPendingTripIds(userId).toSet()
        val deletedTripIds = tripDao.pendingDeletes(userId).map { it.id }.toSet()
        val pendingIncidentIds = incidentDao.unsyncedIncidentIds(userId).toSet()

        for (remoteTrip in remoteTrips) {
            val trip = remoteTrip.toEntity()
            if (trip.userId != userId || trip.id.isBlank()) continue
            if (trip.id !in pendingTripIds) tripDao.upsert(trip)

            for (remoteIncident in remoteTrip.incidents) {
                val incident = remoteIncident.toEntity()
                if (incident.userId != userId || incident.id.isBlank()) continue
                if (incident.tripId != trip.id || incident.tripId in deletedTripIds) continue
                if (incident.id in pendingIncidentIds) continue
                incidentDao.upsert(incident)
            }
        }
    }

    private fun tripRequest(trip: TripEntity): SyncRequestDto = SyncRequestDto(
        trips = listOf(trip.toSyncDto()),
        incidents = emptyList(),
        deleteTripIds = emptyList()
    )

    private fun requireAck(ids: List<String>, id: String, label: String) {
        if (id !in ids) throw SyncWriteException("The server did not confirm the $label.")
    }

    private fun writeFailure(outcome: SyncOutcome): SyncWriteException = when (outcome) {
        SyncOutcome.Unauthorized -> SyncWriteException("Log in again to save driving data.")
        is SyncOutcome.ConnectionFailed -> SyncWriteException(outcome.message)
        is SyncOutcome.Retryable -> SyncWriteException(outcome.message)
        is SyncOutcome.Failed -> SyncWriteException(outcome.message)
        is SyncOutcome.Success -> SyncWriteException("The server did not confirm the write.")
    }

    companion object {
        private const val TAG = "DriveRepository"
        private const val START_UPLOAD_TIMEOUT_MS = 5_000L
        private const val INCIDENT_UPLOAD_TIMEOUT_MS = 5_000L
        private const val MAX_TRIPS = 100
        private const val MAX_INCIDENTS = 300
        private const val MAX_DELETES = 100

        @Volatile
        private var instance: DriveRepository? = null

        fun getInstance(context: Context): DriveRepository =
            instance ?: synchronized(this) {
                instance ?: DriveRepository(context.applicationContext).also { instance = it }
            }
    }
}

class SyncWriteException(message: String) : Exception(message)

private suspend fun uploadForCurrentUser(context: Context, request: SyncRequestDto): SyncOutcome {
    if (!NetworkMonitor.isOnline(context)) return SyncOutcome.ConnectionFailed("Device is offline.")
    val userId = AuthSession.user?.id
    if (AuthSession.token.isNullOrBlank() || userId.isNullOrBlank() ||
        request.trips.any { it.userId != userId } || request.incidents.any { it.userId != userId }
    ) {
        return SyncOutcome.Unauthorized
    }
    return SyncApiClient.sync(request)
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

private fun RemoteTripDto.toEntity(): TripEntity {
    val startMillis = startTime.toEpochMillis(System.currentTimeMillis())
    return TripEntity(
        id = id,
        userId = userId,
        startTime = startMillis,
        endTime = endTime.toEpochMillisOrNull(),
        status = status?.takeIf { it.isNotBlank() } ?: TripEntity.STATUS_COMPLETED,
        origin = origin ?: "",
        destination = destination ?: "",
        createdAt = createdAt.toEpochMillis(startMillis),
        isSynced = true,
        isDeleted = false
    )
}

private fun RemoteIncidentDto.toEntity(): TripIncidentEntity {
    val deviceMillis = if (deviceTimestamp > 0) deviceTimestamp
    else createdAt.toEpochMillis(System.currentTimeMillis())
    return TripIncidentEntity(
        id = id,
        tripId = tripId,
        userId = userId,
        description = description?.takeIf { it.isNotBlank() } ?: "Detection",
        time = time ?: "",
        incidentType = incidentType?.takeIf { it.isNotBlank() } ?: "Detection",
        confidence = confidence.toFloat().coerceIn(0f, 1f),
        deviceTimestamp = deviceMillis,
        isSynced = true,
        emailSent = emailSent,
        createdAt = createdAt.toEpochMillis(deviceMillis)
    )
}

private fun String?.toEpochMillisOrNull(): Long? {
    if (this.isNullOrBlank()) return null
    return parseIsoMillis(this)
}

private fun String?.toEpochMillis(fallback: Long): Long {
    if (this.isNullOrBlank()) return fallback
    return parseIsoMillis(this) ?: fallback
}

/**
 * PostgREST emits timestamptz as ISO-8601 with a +00:00 offset (or Z) and a
 * variable-length fraction. java.time needs API 26, so normalize the fraction to
 * three digits and the offset to ±HHMM, then parse with SimpleDateFormat.
 */
private fun parseIsoMillis(value: String): Long? {
    val normalized = value.trim()
        .replace(Regex("\\.(\\d+)")) { "." + it.groupValues[1].take(3).padEnd(3, '0') }
        .replace("Z", "+0000")
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ"
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
                .parse(normalized)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}
