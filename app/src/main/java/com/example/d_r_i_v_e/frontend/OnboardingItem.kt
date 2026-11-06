package com.example.d_r_i_v_e.frontend

import androidx.compose.ui.graphics.Color
import com.example.d_r_i_v_e.R

data class OnboardingItem(
    val backgroundColor: Color,
    val title: String,
    val description: String,
    val imageRes: Int,
    val isLastPage: Boolean = false
)

val onboardingItems = listOf(
    OnboardingItem(
        backgroundColor = Color(0xFF3E2723),
        title = "Your Guardian on the Road.",
        description = "Drive with confidence. D.R.I.V.E. connects seamlessly to your Anti-Drowsy Seat Sensor, keeping you safe on every journey, day or night.",
        imageRes = R.drawable.human_error_3
    ),
    OnboardingItem(
        backgroundColor = Color(0xFF1B2631),
        title = "Smart Fatigue Tracking.",
        description = "The system's camera continuously analyzes your blink frequency, eye closure, and head movements to detect early signs of drowsiness.",
        imageRes = R.drawable.human_error_2
    ),
    OnboardingItem(
        backgroundColor = Color(0xFF37474F),
        title = "Instant Physical Alerts.",
        description = "If drowsiness is detected, your seat will immediately activate vibration motors and a buzzer. Use this app to customize your alert intensities.",
        imageRes = R.drawable.human_error_1
    ),
    OnboardingItem(
        backgroundColor = Color(0xFF102027),
        title = "Your Privacy is Protected.",
        description = "All facial analysis is processed locally by the hardware in real time. We never record, store, or share your video footage.",
        imageRes = R.drawable.human_error_4,
        isLastPage = true
    )
)