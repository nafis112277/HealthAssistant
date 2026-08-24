package com.srgroup.healthassistant.ai

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Single source of truth for "where is the Gemma model file, and is it
 * actually usable" - both the downloader (this + the Worker) and
 * GemmaInferenceHelper read the same modelFile() so they can't disagree
 * about the path.
 */
object GemmaModelRepository {

    private const val UNIQUE_WORK_NAME = "gemma_model_download"
    private const val MIN_VALID_SIZE_BYTES = 100L * 1024 * 1024 // 100MB - smallest real quantized .task is well above this; guards against a truncated/corrupt file passing as "downloaded"

    fun modelFile(context: Context): File =
        File(context.filesDir, "models/gemma.task")

    fun tempFile(context: Context): File =
        File(context.filesDir, "models/gemma.task.download")

    fun isDownloaded(context: Context): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() >= MIN_VALID_SIZE_BYTES
    }

    /**
     * @param modelUrl direct download URL for the .task file (Kaggle
     *   model hub links usually need the user to accept a license first
     *   in a browser - point this at a URL your own hosting/CDN serves,
     *   or a Kaggle URL you've verified works with a bare GET + your
     *   API credentials baked into the request).
     * @param wifiOnly if true, download only proceeds on unmetered
     *   network - defaults to true since this is a multi-GB file and
     *   many patients/doctors in Bangladesh are on paid mobile data.
     */
    fun enqueueDownload(context: Context, modelUrl: String, wifiOnly: Boolean = true) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<GemmaModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(GemmaModelDownloadWorker.KEY_MODEL_URL to modelUrl))
            .build()

        // Unique by name + KEEP: a second download tap while one is already
        // running should not start a competing download of the same file.
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelDownload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        tempFile(context).delete()
    }

    /** Emits WorkInfo updates for the download so the UI can show progress/state. */
    fun observeDownload(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .asFlow()
            .map { list -> list.firstOrNull() }
}
