package com.example.d_r_i_v_e.database

import androidx.room.Embedded
import androidx.room.Relation

data class TripWithIncidents(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "trip_id")
    val incidents: List<TripIncidentEntity>
)
