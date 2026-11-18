package com.tarixustoz.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val contentRepository: ContentRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val preferences: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "OfflineSyncWorker"
        const val KEY_FORCE_REFRESH = "force_refresh"
        const val KEY_UPLINK_ONLY = "uplink_only"
        const val KEY_CACHED_QUESTIONS = "cached_questions"
        const val KEY_LAST_SYNC_MS = "last_sync_ms"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        val uplinkOnly = inputData.getBoolean(KEY_UPLINK_ONLY, false)

        try {
            val uplinkOk = cloudSyncRepository.flushPendingUploads()

            if (uplinkOnly) {
                return@withContext if (uplinkOk || runAttemptCount >= 3) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }

            val selectedGrade = preferences.selectedGrade.first().coerceIn(6, 11)
            val prefs = preferences.getAllPreferences()
            val lastOpenedByCat = mutableMapOf<String, Int>()
            prefs.asMap().forEach { (key, value) ->
                if (key.name.startsWith("last_opened_ticket_") && value is Int) {
                    val cat = key.name.removePrefix("last_opened_ticket_")
                    lastOpenedByCat[cat] = value.coerceAtLeast(1)
                }
            }

            val syncResult = contentRepository.syncOfflineCache(
                grade = selectedGrade,
                forceRefresh = forceRefresh,
                lastOpenedByCategory = lastOpenedByCat
            )

            val nowMs = System.currentTimeMillis()
            if (syncResult.backendSynced || syncResult.syncedTicketsCount > 0) {
                preferences.setLastOfflineSyncAtMs(nowMs)
            }

            if (!syncResult.backendSynced && !uplinkOk && runAttemptCount < 2) {
                return@withContext Result.retry()
            }

            val output = Data.Builder()
                .putInt(KEY_CACHED_QUESTIONS, syncResult.cachedQuestionsTotal)
                .putLong(KEY_LAST_SYNC_MS, nowMs)
                .build()

            Result.success(output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "OfflineSyncWorker error (attempt $runAttemptCount)", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
