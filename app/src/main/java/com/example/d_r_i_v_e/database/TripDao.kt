package com.example.d_r_i_v_e.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query(
        "UPDATE trips SET end_time = :endTime, status = :status, destination = :destination, " +
            "is_synced = :isSynced WHERE id = :tripId"
    )
    suspend fun finish(
        tripId: String,
        endTime: Long,
        status: String,
        destination: String,
        isSynced: Boolean
    )

    @Query("SELECT * FROM trips WHERE status = 'IN_PROGRESS' ORDER BY start_time DESC LIMIT 1")
    suspend fun latestActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun findById(tripId: String): TripEntity?

    @Query(
        "SELECT * FROM trips WHERE user_id = :userId AND is_synced = 0 AND is_deleted = 0 " +
            "ORDER BY start_time ASC"
    )
    suspend fun unsyncedTrips(userId: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE user_id = :userId AND is_deleted = 1")
    suspend fun pendingDeletes(userId: String): List<TripEntity>

    @Query("SELECT id FROM trips WHERE user_id = :userId AND (is_synced = 0 OR is_deleted = 1)")
    suspend fun locallyPendingTripIds(userId: String): List<String>

    @Query("UPDATE trips SET is_synced = 1 WHERE id IN (:tripIds)")
    suspend fun markSynced(tripIds: List<String>)

    @Query("UPDATE trips SET is_deleted = 1 WHERE id = :tripId")
    suspend fun markDeleted(tripId: String)

    @Query("DELETE FROM trips WHERE id IN (:tripIds)")
    suspend fun deleteByIds(tripIds: List<String>)

    @Transaction
    @Query(
        "SELECT * FROM trips WHERE user_id = :userId AND status != 'IN_PROGRESS' " +
            "AND is_deleted = 0 ORDER BY start_time DESC"
    )
    fun observeHistory(userId: String): Flow<List<TripWithIncidents>>
}
