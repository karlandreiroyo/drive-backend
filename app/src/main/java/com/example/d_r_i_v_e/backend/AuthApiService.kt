package com.example.d_r_i_v_e.backend

import android.os.Build
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface AuthApiService {
    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<SignUpResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/me")
    suspend fun me(): Response<LoginResponse>

    companion object {
        private const val TAG = "AuthApiService"

        private fun isEmulator(): Boolean {
            val hardware = Build.HARDWARE.lowercase()
            val product = Build.PRODUCT.lowercase()
            val model = Build.MODEL.lowercase()
            val fingerprint = Build.FINGERPRINT.lowercase()

            return hardware.contains("goldfish") ||
                    hardware.contains("ranchu") ||
                    model.contains("google_sdk") ||
                    model.contains("emulator") ||
                    model.contains("android sdk built for x86") ||
                    product.contains("sdk") ||
                    product.contains("google_sdk") ||
                    product.contains("emulator") ||
                    fingerprint.startsWith("generic")
        }

        fun getBaseUrl(): String {
            val url = ApiConfig.getBaseUrl()
            Log.d(TAG, "Resolved Base URL: $url (isEmulator = ${isEmulator()}, Model = ${Build.MODEL}, Device = ${Build.DEVICE})")
            return url
        }

        fun create(): AuthApiService {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val currentBaseUrl = ApiConfig.getBaseUrl()
                    val currentHttpUrl = currentBaseUrl.toHttpUrl()

                    val newUrl = original.url.newBuilder()
                        .scheme(currentHttpUrl.scheme)
                        .host(currentHttpUrl.host)
                        .port(currentHttpUrl.port)
                        .build()

                    val builder = original.newBuilder().url(newUrl)
                    AuthSession.token?.let { token ->
                        builder.header("Authorization", "Bearer $token")
                    }
                    val request = builder.build()
                    Log.d(TAG, "--> Sending HTTP ${request.method} to ${request.url}")
                    try {
                        chain.proceed(request)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Network Request Failed for ${request.url}: ${e.message}", e)
                        throw e
                    }
                }
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            return Retrofit.Builder()
                .baseUrl("http://127.0.0.1:5000/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApiService::class.java)
        }
    }
}
