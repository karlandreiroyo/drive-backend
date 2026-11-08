package com.example.d_r_i_v_e.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local mirror of `public.trips`. Times are stored as epoch millis locally and
 * converted to ISO-8601 UTC when the sync worker uploads them.
 *
 * [isSynced] and [isDeleted] are device-only bookkeeping and never uploaded.
 */
@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["is_synced"]),
        Index(value = ["is_deleted"])
    ]
)
data class TripEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    val status: String = STATUS_IN_PROGRESS,
    val origin: String = "",
    val destination: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false
) {
    companion object {
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
