package org.futo.inputmethod.latin.uix.actions.gif

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.actions.clipboard.CLIPBOARD_AUTHORITY
import org.futo.inputmethod.latin.uix.actions.clipboard.ClipboardPasteRequest
import org.futo.inputmethod.latin.uix.actions.clipboard.ClipboardProviderState
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val CACHE_DIR = "gif_cache"
private const val FAVORITES_DIR = "gif_saved"
private const val CACHE_FILE_EXTENSION = ".gif"
private const val MAX_DOWNLOAD_BYTES = 30L * 1024L * 1024L
private const val MAX_CACHE_BYTES = 100L * 1024L * 1024L
private const val MAX_CACHE_AGE_MS = 7L * 24L * 60L * 60L * 1000L
private const val MAX_CACHE_FILES = 200

/**
 * Temporary on-disk cache for GIFs that are about to be inserted into the target app.
 *
 * The files are served to the target app through the existing clipboard ContentProvider
 * (which grants per-URI read access), so no new exported provider is required.
 */
object GifCache {

    private fun cacheDirectory(context: Context): File =
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    private fun favoritesDirectory(context: Context): File =
        File(context.filesDir, FAVORITES_DIR).apply { mkdirs() }

    fun favoriteFile(context: Context, item: GifItem): File =
        File(favoritesDirectory(context), fileNameFor(item))

    fun favoriteFileOrNull(context: Context, item: GifItem): File? =
        favoriteFile(context, item).takeIf { it.isFile && it.length() > 0 }

    fun fileNameFor(item: GifItem): String {
        val safeId = item.id
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "gif" }
        return "${safeId}_${item.fullUrl.hashCode().toUInt()}$CACHE_FILE_EXTENSION"
    }

    suspend fun download(context: Context, item: GifItem): File? = withContext(Dispatchers.IO) {
        val directory = cacheDirectory(context)
        val destination = File(directory, fileNameFor(item))
        if (destination.isFile && destination.length() > 0 && destination.length() <= MAX_DOWNLOAD_BYTES) {
            return@withContext destination
        }

        val temporary = File(directory, destination.name + ".tmp")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(item.fullUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "FUTO-Keyboard")
            }

            if (connection.responseCode !in 200..299) return@withContext null

            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_DOWNLOAD_BYTES) return@withContext null

            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_DOWNLOAD_BYTES) {
                            output.close()
                            temporary.delete()
                            return@withContext null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (written <= 0) {
                temporary.delete()
                return@withContext null
            }

            if (destination.exists()) destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.delete()
                return@withContext null
            }

            destination
        } catch (_: Exception) {
            temporary.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Registers a downloaded GIF with the clipboard ContentProvider and returns a URI that the
     * current input connection can grant to the target app.
     */
    fun registerForInsertion(
        file: File,
        lifetimeMillis: Long = 5L * 60L * 1000L
    ): Uri {
        val request = ClipboardProviderState.addRequest(
            ClipboardPasteRequest(
                file = file,
                mimeType = "image/gif",
                expiration = System.currentTimeMillis() + lifetimeMillis
            )
        )
        return "content://$CLIPBOARD_AUTHORITY/clip/$request".toUri()
    }

    /**
     * Copies a GIF into persistent storage so a favorite can be used offline and survives
     * cache pruning.
     */
    suspend fun saveFavorite(context: Context, item: GifItem): Boolean = withContext(Dispatchers.IO) {
        val destination = favoriteFile(context, item)
        if (destination.isFile && destination.length() > 0) return@withContext true

        val cached = download(context, item) ?: return@withContext false
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        try {
            cached.copyTo(temporary, overwrite = true)
            if (destination.exists()) destination.delete()
            temporary.renameTo(destination)
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    fun deleteFavorite(context: Context, item: GifItem) {
        favoriteFile(context, item).delete()
    }

    fun clearFavorites(context: Context) {
        favoritesDirectory(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Puts a GIF URI on the clipboard as a fallback for apps that reject keyboard image
     * insertion. The system grants the pasting app read access to the content URI.
     */
    fun copyToClipboard(context: Context, uri: Uri): Boolean {
        return try {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "GIF", uri))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun prune(context: Context) {
        val directory = cacheDirectory(context)
        val now = System.currentTimeMillis()
        val files = directory.listFiles()?.filter { it.isFile } ?: return

        files.forEach { file ->
            if (now - file.lastModified() > MAX_CACHE_AGE_MS) file.delete()
        }

        val remaining = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
            ?: return
        var total = remaining.sumOf { it.length() }
        var count = remaining.size
        for (file in remaining) {
            if (total <= MAX_CACHE_BYTES && count <= MAX_CACHE_FILES) break
            total -= file.length()
            count -= 1
            file.delete()
        }
    }

    fun clear(context: Context) {
        cacheDirectory(context).listFiles()?.forEach { it.delete() }
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
