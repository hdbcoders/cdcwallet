package com.cdcvouchers.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Backup file I/O (spec 06 §6.2): the encrypted blob lands in the user-visible
 * MediaStore Downloads folder as `cdcvoucher.backup`. API 24–28 fall back to
 * the public Downloads directory directly (scoped storage enforcement starts
 * at 29).
 */
object BackupFileStore {

    const val FILE_NAME = "cdcvoucher.backup"
    private const val MIME_TYPE = "application/octet-stream"

    fun writeToDownloads(context: Context, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("MediaStore write failed")
            return uri
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create Downloads dir")
        val file = File(dir, FILE_NAME)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
}
