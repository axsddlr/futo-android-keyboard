package org.futo.inputmethod.latin.uix.actions.gif

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads and caches the raw bytes of GIF previews.
 *
 * Bytes are cached rather than Bitmaps because the grid renders animated previews through
 * [GifMovieView]; keeping the encoded form also keeps the cache small.
 */
object GifPreviewLoader {
    private const val MAX_PREVIEW_BYTES = 8L * 1024L * 1024L
    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, ByteArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "FUTO-Keyboard")
            }

            if (connection.responseCode !in 200..299) return@withContext null
            if (connection.contentLengthLong > MAX_PREVIEW_BYTES) return@withContext null

            val output = ByteArrayOutputStream()
            var total = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PREVIEW_BYTES) return@withContext null
                    output.write(buffer, 0, read)
                }
            }

            val bytes = output.toByteArray()
            if (bytes.isEmpty()) return@withContext null
            cache.put(url, bytes)
            bytes
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * Minimal animated-GIF view used for the search grid.
 *
 * [Movie] decodes the encoded GIF once and draws individual frames on demand, which keeps
 * previews animated without pulling in an image-loading library. The view is recycled by the
 * lazy grid, so the decoded movie is replaced only when a different byte array arrives.
 */
class GifMovieView(context: Context) : View(context) {
    private var movie: Movie? = null
    private var currentBytes: ByteArray? = null
    private var animationStart = 0L

    fun setGif(bytes: ByteArray?) {
        if (bytes == null || bytes === currentBytes) return

        currentBytes = bytes
        movie = try {
            Movie.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
        animationStart = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val movie = movie ?: return
        val movieWidth = movie.width()
        val movieHeight = movie.height()
        if (movieWidth <= 0 || movieHeight <= 0 || width == 0 || height == 0) return

        val duration = movie.duration()
        if (duration > 0) {
            val frameTime = ((SystemClock.uptimeMillis() - animationStart) % duration).toInt()
            movie.setTime(frameTime)
        }

        // Center-crop the movie into the square grid cell.
        val scale = maxOf(
            width.toFloat() / movieWidth.toFloat(),
            height.toFloat() / movieHeight.toFloat()
        )
        canvas.save()
        canvas.translate(
            (width - movieWidth * scale) / 2.0f,
            (height - movieHeight * scale) / 2.0f
        )
        canvas.scale(scale, scale)
        movie.draw(canvas, 0.0f, 0.0f)
        canvas.restore()

        if (duration > 0) postInvalidateOnAnimation()
    }
}
