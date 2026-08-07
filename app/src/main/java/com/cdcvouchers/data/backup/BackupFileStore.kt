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
            // Remove previous exports first (spec 06 §6.2: one predictable
            // file in Downloads). Match the whole "cdcvoucher*" family —
            // earlier runs created cdcvoucher (1).backup…(n).backup, and
            // MediaStore's unique-file logic treats those siblings as making
            // the base name unavailable. Cleaning them also clears stale
            // index rows left by uninstall/reinstall cycles.
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$FILE_NAME%"),
            )
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
        // API 24–28 raw-file path: drop any previous export so we always land
        // a fresh cdcvoucher.backup rather than accumulating (n) copies.
        if (file.exists() && !file.delete()) throw IOException("Cannot remove previous backup")
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
}
