package com.srgroup.healthassistant.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the Gemma .task file (hundreds of MB to a few GB) as a
 * foreground worker - a plain background coroutine risks Android
 * killing the transfer once the app isn't visible; setForeground() with
 * a persistent notification is what keeps a multi-minute/hour download
 * alive the way a music or video-download app's does.
 */
class GemmaModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(progressPercent = 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return@withContext Result.failure(
            workDataOf(KEY_ERROR to "মডেল ডাউনলোড লিংক দেওয়া হয়নি")
        )

        setForeground(buildForegroundInfo(progressPercent = 0))

        val tempFile = GemmaModelRepository.tempFile(applicationContext)
        tempFile.parentFile?.mkdirs()

        try {
            val connection = URL(modelUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "সার্ভার থেকে ডাউনলোড ব্যর্থ (HTTP ${connection.responseCode})")
                )
            }

            val totalBytes = connection.contentLengthLong // -1 if server doesn't send it - progress just won't be shown, download still proceeds
            var downloadedBytes = 0L
            var lastReportedPercent = -1

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) return@withContext Result.failure(workDataOf(KEY_ERROR to "বাতিল করা হয়েছে"))

                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                setProgress(workDataOf(KEY_PROGRESS_PERCENT to percent))
                                setForeground(buildForegroundInfo(percent))
                            }
                        }
                    }
                }
            }

            // Rename only after a fully successful stream copy, so a crash or
            // kill mid-download leaves only the .download temp file behind,
            // never a truncated file at the real path that isDownloaded()
            // could mistake for complete (the size-floor check helps too,
            // but the two together are more robust than either alone).
            val finalFile = GemmaModelRepository.modelFile(applicationContext)
            if (!tempFile.renameTo(finalFile)) {
                return@withContext Result.failure(workDataOf(KEY_ERROR to "ফাইল সেভ করা যায়নি"))
            }

            Result.success(workDataOf(KEY_PROGRESS_PERCENT to 100))
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "ডাউনলোডে সমস্যা হয়েছে")))
        }
    }

    private fun buildForegroundInfo(progressPercent: Int): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AI মডেল ডাউনলোড হচ্ছে")
            .setContentText(if (progressPercent > 0) "$progressPercent%" else "শুরু হচ্ছে...")
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "মডেল ডাউনলোড", NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_MODEL_URL = "model_url"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 9001
    }
}
