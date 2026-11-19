package com.tarixustoz.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class OfflineSyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val cachedQuestionsCount: Int = 0,
    val pendingUploadsCount: Int = 0,
    val lastDeltaCount: Int = 0,
    val lastSkippedCount: Int = 0,
    val lastSyncMode: String = "",
    val cacheEpoch: Long = 1L
)

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val database: AppDatabase
) {

    companion object {
        const val PERIODIC_SYNC_WORK_NAME = "tarix_periodic_offline_sync"
        const val IMMEDIATE_SYNC_WORK_NAME = "tarix_immediate_offline_sync"
        const val PENDING_UPLOAD_WORK_NAME = "tarix_pending_progress_sync"
    }

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(
            6, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun triggerImmediateSync(forceRefresh: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putBoolean(OfflineSyncWorker.KEY_FORCE_REFRESH, forceRefresh)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            if (forceRefresh) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun triggerPendingProgressUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putBoolean(OfflineSyncWorker.KEY_UPLINK_ONLY, true)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            PENDING_UPLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private data class DeltaTelemetry(
        val deltaCount: Int,
        val skippedCount: Int,
        val syncMode: String,
        val cacheEpoch: Long
    )

    fun observeSyncStatus(): Flow<OfflineSyncStatus> {
        val immediateFlow = workManager.getWorkInfosForUniqueWorkFlow(IMMEDIATE_SYNC_WORK_NAME)
        val periodicFlow = workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK_NAME)
        val pendingFlow = workManager.getWorkInfosForUniqueWorkFlow(PENDING_UPLOAD_WORK_NAME)
        val telemetryFlow = combine(
            preferences.lastSyncDeltaCount,
            preferences.lastSyncSkippedCount,
            preferences.lastSyncMode,
            preferences.cacheEpoch
        ) { delta, skipped, mode, epoch ->
            DeltaTelemetry(delta, skipped, mode, epoch)
        }

        return combine(
            immediateFlow,
            periodicFlow,
            pendingFlow,
            preferences.lastOfflineSyncAtMs,
            database.questionDao().countFlow(),
            preferences.pendingSyncCount,
            telemetryFlow
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val immediateInfos = values[0] as List<WorkInfo>
            @Suppress("UNCHECKED_CAST")
            val periodicInfos = values[1] as List<WorkInfo>
            @Suppress("UNCHECKED_CAST")
            val uploadInfos = values[2] as List<WorkInfo>
            val lastSync = values[3] as Long
            val cachedCount = values[4] as Int
            val pendingCount = values[5] as Int
            val telemetry = values[6] as DeltaTelemetry

            val isRunning = (immediateInfos + periodicInfos + uploadInfos).any {
                it.state == WorkInfo.State.RUNNING
            }

            OfflineSyncStatus(
                isSyncing = isRunning,
                lastSyncedAtMs = lastSync,
                cachedQuestionsCount = cachedCount,
                pendingUploadsCount = pendingCount,
                lastDeltaCount = telemetry.deltaCount,
                lastSkippedCount = telemetry.skippedCount,
                lastSyncMode = telemetry.syncMode,
                cacheEpoch = telemetry.cacheEpoch
            )
        }
    }
}
