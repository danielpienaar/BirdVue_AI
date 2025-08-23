package com.varsitycollege.birdvue.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.varsitycollege.birdvue.data.BirdInfoDatabase

class ClearCacheWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            BirdInfoDatabase.clearExpiredCache(applicationContext)
            Log.d("ClearCacheWorker", "Periodic cache clearing successful.")
            Result.success()
        } catch (e: Exception) {
            Log.e("ClearCacheWorker", "Periodic cache clearing failed.", e)
            Result.failure()
        }
    }
}