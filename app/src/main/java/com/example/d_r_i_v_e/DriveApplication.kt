package com.example.d_r_i_v_e

import android.app.Application
import com.example.d_r_i_v_e.repository.DriveRepository
import com.example.d_r_i_v_e.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DriveApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Catches rows stranded by a process death between local write and upload.
        SyncScheduler.schedulePeriodic(this)
        appScope.launch {
            DriveRepository.getInstance(this@DriveApplication).closeOrphanedTrips()
        }
    }
}
