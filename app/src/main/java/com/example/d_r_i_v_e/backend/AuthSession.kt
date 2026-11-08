package com.example.d_r_i_v_e.backend

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthSession {
    private const val PREFS = "VigilAuthSession"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user_json"

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var user: AuthUserDto? = null
        private set

    // 401s from protected endpoints are surfaced here so the navigation host
    // can clear the dead session and send the driver back to login.
    private val _expiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expiredEvents: SharedFlow<Unit> = _expiredEvents.asSharedFlow()

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedToken = prefs.getString(KEY_TOKEN, null)

        if (storedToken.isNullOrBlank() || isExpired(storedToken)) {
            token = null
            user = null
            if (!storedToken.isNullOrBlank()) prefs.edit().clear().apply()
            return
        }

        token = storedToken
        user = prefs.getString(KEY_USER, null)?.let { json ->
            runCatching { Gson().fromJson(json, AuthUserDto::class.java) }.getOrNull()
        }
    }

    fun save(context: Context, token: String, user: AuthUserDto?) {
        this.token = token
        this.user = user
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user?.let { Gson().toJson(it) })
            .apply()
    }

    fun clear(context: Context) {
        token = null
        user = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun notifySessionExpired() {
        _expiredEvents.tryEmit(Unit)
    }

    /**
     * Reads the JWT `exp` claim so an obviously expired token is dropped on
     * startup instead of waiting for a guaranteed 401.
     */
    private fun isExpired(jwt: String): Boolean {
        val payload = jwt.split('.').getOrNull(1) ?: return true
        return runCatching {
            val decoded = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val expiresAt = Gson().fromJson(String(decoded), JwtExpiry::class.java)?.exp ?: 0L
            expiresAt * 1_000L <= System.currentTimeMillis()
        }.getOrDefault(true)
    }

    private data class JwtExpiry(@SerializedName("exp") val exp: Long? = null)
}
