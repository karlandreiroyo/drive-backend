package com.example.d_r_i_v_e.backend

import android.util.Log
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

        fun create(): AuthApiService {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                    AuthSession.token?.let { token ->
                        builder.header("Authorization", "Bearer $token")
                    }
                    val request = builder.build()
                    Log.d(TAG, "--> Sending HTTP ${request.method} to ${request.url}")
                    try {
                        chain.proceed(request)
                    } catch (e: Exception) {
                        Log.e(TAG, "Network request failed for ${request.url}: ${e.message}", e)
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
                .baseUrl(ApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApiService::class.java)
        }
    }
}
