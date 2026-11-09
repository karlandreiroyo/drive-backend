package com.example.d_r_i_v_e.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local cache/outbox for `public.trip_incidents`; pending rows sync when connected. */
@Entity(
    tableName = "trip_incidents",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trip_id"]),
        Index(value = ["user_id"]),
        Index(value = ["is_synced"]),
        Index(value = ["email_sent"])
    ]
)
data class TripIncidentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val description: String,
    val time: String,
    @ColumnInfo(name = "incident_type") val incidentType: String,
    val confidence: Float,
    @ColumnInfo(name = "device_timestamp") val deviceTimestamp: Long,
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "email_sent", defaultValue = "0") val emailSent: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
