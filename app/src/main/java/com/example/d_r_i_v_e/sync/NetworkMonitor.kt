package com.example.d_r_i_v_e.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * One-shot connectivity probe used to route writes: an immediate sync attempt
 * when the device is online, local-only storage plus a WorkManager sync when
 * it is not. WorkManager's NetworkType.CONNECTED constraint remains the
 * safety net that flushes queued rows the moment connectivity returns.
 */
object NetworkMonitor {

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
