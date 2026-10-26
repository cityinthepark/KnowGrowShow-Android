package com.stoneofhope.stoneofhope.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * "Leaving the app → push" (iOS pushes on scenePhase background). WorkManager keeps the backup
 * push alive if the process is killed, and waits for a network instead of skipping.
 */
@HiltWorker
class PushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cloudSync: CloudSync,
    private val identity: SyncIdentity,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ownerUid = identity.currentAuthUid ?: return Result.success()
        cloudSync.push(ownerUid)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "cloud-push"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
