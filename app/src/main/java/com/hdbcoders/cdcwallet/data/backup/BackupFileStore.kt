package com.hdbcoders.cdcwallet.data.backup

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Backup file I/O (spec 06 §6.2): the encrypted blob lands in the user-visible
 * MediaStore Downloads folder as `cdcvoucher.backup`. API 24–28 fall back to
 * the public Downloads directory directly (scoped storage enforcement starts
 * at 29).
 *
 * Overwrite-in-place (API 29+): the canonical `cdcvoucher.backup` row is found
 * by query and its bytes replaced - no insert/delete/rename ever happens after
 * the first export, so MediaStore's unique-name logic can never mint
 * "cdcvoucher (N).backup" siblings (the delete-then-rename dance used to race
 * that logic and strays accumulated in Downloads). Only the exact canonical,
 * app-owned name is ever adopted or written: a user-renamed copy such as
 * "cdcvoucher (perm).backup" or "cdcvoucher_dont_delete.backup" is never
 * matched, never modified.
 *
 * Crash exposure is bounded by ordering: BackupFlow encrypts the payload in
 * memory first, so the file write only ever streams complete bytes; a failure
 * mid-write leaves at most a truncated file whose previous content is lost
 * only for the milliseconds of the copy - and the source data lives safely in
 * the app's database, so a re-export always recovers.
 */
object BackupFileStore {

    const val FILE_NAME = "cdcvoucher.backup"
    private const val MIME_TYPE = "application/octet-stream"

    /** Temporary file name used by the API 24–28 raw-file path. */
    private const val PENDING_NAME = "cdcvoucher.backup.tmp"

    /**
     * The exact app-owned export family: the base name plus the numbered
     * siblings MediaStore minted on earlier runs. Used ONLY to recognize our
     * own rows when adopting a fallback - never to delete anything.
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

        // Overwrite-in-place (bugfix: delete-then-rename minted "cdcvoucher
        // (N).backup" siblings on API 29+ because MediaStore's unique-name
        // logic raced the delete). The canonical row is found by query and
        // its bytes replaced; no insert/delete/rename ever happens after the
        // first export, so MediaStore has no naming decision to make.
        findOwnExport(context)?.let { existing ->
            resolver.openOutputStream(existing, "wt")?.use { it.write(bytes) }
                ?: throw IOException("MediaStore write failed")
            return existing
        }

        // First export (or no app-owned row visible - see findOwnExport):
        // create a fresh item. Two ghost scenarios are handled by the retry:
        // - an invisible row holds the canonical NAME: MediaStore mints
        //   "cdcvoucher (1).backup" for us (harmless; the next export adopts
        //   it via findOwnExport's family fallback);
        // - an invisible row holds the physical PATH (_data UNIQUE collision):
        //   the first insert throws SQLiteConstraintException, so we retry
        //   once with an explicit "(1)" name, which lands on a free path.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = try {
            resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        } catch (e: SQLiteConstraintException) {
            android.util.Log.w(
                "BackupFileStore",
                "canonical path occupied by a foreign row; using family name",
                e,
            )
            val retry = ContentValues(values).apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$FILE_NAME (1)")
            }
            resolver.insert(collection, retry) ?: throw IOException("MediaStore insert failed")
        }
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("MediaStore write failed")
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /**
     * Uri of this app's own backup row in Downloads, or null.
     *
     * Found by OWNER, not by display name: an unfiltered query always returns
     * rows this app created (verified on API 36), while a
     * `DISPLAY_NAME = 'cdcvoucher.backup'` filter can silently miss them -
     * e.g. when an invisible ghost row (a shell-pushed fixture, owner redacted
     * to NULL for apps without media-read permissions) holds the canonical
     * name. Among our own rows we prefer the exact canonical name; otherwise
     * any app-owned family member is adopted as fallback (it gets overwritten;
     * renaming to canonical succeeds because the ghost name is not visible to
     * us). Rows owned by other packages - user-renamed spares like
     * "cdcvoucher (perm).backup" - are never visible and never touched.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun findOwnExport(context: Context): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        )
        var fallback: Uri? = null
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val ownerCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(ownerCol) != context.packageName) continue
                val name = cursor.getString(nameCol)
                if (name == FILE_NAME) {
                    return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol),
                    )
                }
                // App-owned sibling ("cdcvoucher (N).backup"): remember as a
                // fallback - it will be overwritten in place.
                if (EXPORT_FAMILY.matches(name) && fallback == null) {
                    fallback = ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol),
                    )
                }
            }
        }
        return fallback
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
