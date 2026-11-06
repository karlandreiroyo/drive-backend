package com.example.d_r_i_v_e.backend

import android.content.Context
import com.google.gson.Gson

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

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
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
}
