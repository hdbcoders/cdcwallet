package com.cdcwallet.data.backup

import android.content.ContentResolver
import android.content.ContentUris
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
 *
 * Publish-then-cleanup (refactor H8): the new export is written to a pending
 * (hidden) MediaStore item - or a temp file on API 24–28 - and the previous
 * export is deleted only AFTER the new bytes are durable on disk, then the
 * pending item is renamed/published. A failed write therefore never destroys
 * the previous valid backup, and cleanup deletes only the app's own
 * exact-name exports - never a prefix match that could catch a user's
 * unrelated file.
 */
object BackupFileStore {

    const val FILE_NAME = "cdcvoucher.backup"
    private const val MIME_TYPE = "application/octet-stream"

    /** Temporary (pending) name while the bytes are being written. */
    private const val PENDING_NAME = "cdcvoucher.backup.tmp"

    /**
     * The exact app-owned export family: the base name plus the numbered
     * siblings MediaStore's unique-file logic created on earlier runs
     * ("cdcvoucher.backup", "cdcvoucher.backup (1)", ...). Anything else -
     * e.g. a user's own "cdcvoucher.backup.notes" - is never touched.
     */
    private val EXPORT_FAMILY: Regex = Regex("""^cdcvoucher\.backup( \(\d+\))?$""")

    fun writeToDownloads(context: Context, bytes: ByteArray): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            writeViaMediaStore(context, bytes)
        } else {
            writeViaFileApi(bytes)
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(context: Context, bytes: ByteArray): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // 1. Write the new backup to a PENDING item: hidden from the user
        //    until published, and never colliding with the existing export.
        val pendingValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PENDING_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val pendingUri = resolver.insert(collection, pendingValues)
            ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(pendingUri)?.use { it.write(bytes) }
                ?: throw IOException("MediaStore write failed")

            // 2. The complete backup is durable on disk BEFORE anything is
            //    deleted. A failure from here on never destroys the previous
            //    backup without a complete successor (refactor H8).
            deletePreviousExports(context, resolver, collection)

            // 3. Publish: adopt the canonical name (the old item is gone, so
            //    no "(1)" sibling is minted) and reveal the item.
            val nameValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.update(pendingUri, nameValues, null, null)
            nameValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(pendingUri, nameValues, null, null)
            return pendingUri
        } catch (e: Exception) {
            runCatching { resolver.delete(pendingUri, null, null) }
            throw e
        }
    }

    /**
     * Deletes only the app's own previous exports, matched EXACTLY against the
     * known family AND owned by this package (refactor H8). The old code
     * deleted every display name starting with "cdcvoucher.backup", which
     * could catch a user's unrelated file.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun deletePreviousExports(
        context: Context,
        resolver: ContentResolver,
        collection: Uri,
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        )
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        resolver.query(collection, projection, selection, arrayOf("$FILE_NAME%"), null)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val ownerCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val owner = cursor.getString(ownerCol)
                    if (EXPORT_FAMILY.matches(name) && owner == context.packageName) {
                        resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(idCol)), null, null)
                    }
                }
            }
    }

    /** API 24–28 raw-file path: temp file + atomic rename in the same
     *  directory, so a crash mid-write leaves either the old backup or the
     *  complete new one - never a truncated mix. */
    private fun writeViaFileApi(bytes: ByteArray): Uri {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create Downloads dir")
        val target = File(dir, FILE_NAME)
        val tmp = File(dir, PENDING_NAME)
        try {
            tmp.writeBytes(bytes)
            if (target.exists() && !target.delete()) {
                throw IOException("Cannot remove previous backup")
            }
            if (!tmp.renameTo(target)) {
                throw IOException("Cannot publish backup")
            }
            return Uri.fromFile(target)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri")
}
