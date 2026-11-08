package com.example.d_r_i_v_e.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TripIncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(incident: TripIncidentEntity)

    @Query(
        "SELECT * FROM trip_incidents WHERE user_id = :userId AND is_synced = 0 " +
            "AND trip_id NOT IN (SELECT id FROM trips WHERE is_deleted = 1) " +
            "ORDER BY device_timestamp ASC"
    )
    suspend fun unsyncedIncidents(userId: String): List<TripIncidentEntity>

    @Query("UPDATE trip_incidents SET is_synced = 1 WHERE id IN (:incidentIds)")
    suspend fun markSynced(incidentIds: List<String>)

    @Query("UPDATE trip_incidents SET email_sent = 1 WHERE id IN (:incidentIds)")
    suspend fun markEmailSent(incidentIds: List<String>)
}
