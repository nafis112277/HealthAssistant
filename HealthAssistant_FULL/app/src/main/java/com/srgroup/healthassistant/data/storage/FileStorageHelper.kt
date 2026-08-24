package com.srgroup.healthassistant.data.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies a user-picked document (prescription photo / lab report PDF)
 * into app-private storage (filesDir/health_records/). We copy rather
 * than just keeping the picked Uri because SAF grants on a content://
 * Uri aren't guaranteed to survive reboots/permission changes - copying
 * once up front is simpler and more reliable for a records archive.
 */
object FileStorageHelper {

    fun copyToPrivateStorage(context: Context, sourceUri: Uri, suggestedExtension: String = ""): String {
        val recordsDir = File(context.filesDir, "health_records").apply { mkdirs() }
        val fileName = "${UUID.randomUUID()}$suggestedExtension"
        val destFile = File(recordsDir, fileName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open picked file")

        return destFile.absolutePath
    }
}
