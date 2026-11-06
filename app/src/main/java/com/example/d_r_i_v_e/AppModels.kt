package com.example.d_r_i_v_e

data class Incident(
    val description: String,
    val time: String
)

data class TripEntry(
    val id: String,
    val dateLabel: String,
    val totalDuration: String,
    val incidents: List<Incident> = emptyList()
)
