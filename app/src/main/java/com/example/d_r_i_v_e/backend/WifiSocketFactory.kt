package com.example.d_r_i_v_e.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Routes every socket through the active Wi-Fi network interface.
 *
 * Android silently falls back to mobile data when a Wi-Fi network has no
 * internet access (e.g. a local Pi hotspot). That means OkHttp requests to
 * 10.42.0.x time out because they go out over cellular instead of Wi-Fi.
 *
 * By delegating to the Wi-Fi network's own SocketFactory we force each
 * connection through the correct interface. If no Wi-Fi network is active the
 * factory falls back to the system default so the app keeps working normally.
 */
internal class WifiSocketFactory(context: Context) : SocketFactory() {

    private val appContext = context.applicationContext

    private fun delegate(): SocketFactory {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val wifiNet = cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifiNet == null) {
            Log.w(TAG, "No Wi-Fi network found — using default SocketFactory")
        }
        return wifiNet?.socketFactory ?: getDefault()
    }

    override fun createSocket(): Socket =
        delegate().createSocket()

    override fun createSocket(host: String, port: Int): Socket =
        delegate().createSocket(host, port)

    override fun createSocket(
        host: String, port: Int,
        localHost: InetAddress, localPort: Int
    ): Socket = delegate().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        delegate().createSocket(host, port)

    override fun createSocket(
        address: InetAddress, port: Int,
        localAddress: InetAddress, localPort: Int
    ): Socket = delegate().createSocket(address, port, localAddress, localPort)

    private companion object {
        const val TAG = "WifiSocketFactory"
    }
}
