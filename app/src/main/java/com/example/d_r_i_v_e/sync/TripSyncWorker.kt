package com.example.d_r_i_v_e.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.d_r_i_v_e.backend.AuthSession
import com.example.d_r_i_v_e.backend.routes.SyncOutcome
import com.example.d_r_i_v_e.repository.DriveRepository

class TripSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // WorkManager can start this process without the UI, so rehydrate the
        // bearer token before touching the network.
        AuthSession.restore(applicationContext)
        if (AuthSession.token.isNullOrBlank()) {
            Log.i(TAG, "No active session; leaving offline rows queued.")
            return Result.success()
        }

        val outcome = DriveRepository.getInstance(applicationContext).syncPending()
        return when (outcome) {
            is SyncOutcome.Success -> {
                Log.i(TAG, "Offline sync completed.")
                Result.success()
            }

            SyncOutcome.Unauthorized -> {
                Log.w(TAG, "Sync rejected: session expired.")
                Result.failure()
            }

            is SyncOutcome.ConnectionFailed -> {
                Log.w(TAG, "Sync connection unavailable: ${outcome.message}")
                Result.retry()
            }

            is SyncOutcome.Retryable -> {
                Log.w(TAG, "Sync will retry: ${outcome.message}")
                Result.retry()
            }

            is SyncOutcome.Failed -> {
                Log.w(TAG, "Sync failed permanently: ${outcome.message}")
                Result.failure()
            }
        }
    }

    private companion object {
        const val TAG = "TripSyncWorker"
    }
}
