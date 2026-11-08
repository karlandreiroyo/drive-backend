package com.example.d_r_i_v_e.backend

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiConfig {
    // Live backend hosted on Railway.
    const val BASE_URL = "https://drive-backend.up.railway.app/"

    fun friendlyNetworkError(e: Throwable): String = when (e) {
        is SocketTimeoutException ->
            "The server took too long to respond. Please try again."
        is UnknownHostException ->
            "Can't reach the server. Check your internet connection."
        is ConnectException ->
            "Can't connect to the server. Please try again shortly."
        else -> e.localizedMessage ?: "A network error occurred. Please try again."
    }
}
