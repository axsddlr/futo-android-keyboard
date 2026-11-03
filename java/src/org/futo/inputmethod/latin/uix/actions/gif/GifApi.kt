package org.futo.inputmethod.latin.uix.actions.gif

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.uix.SettingsKey
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Settings backing the optional online GIF search feature.
 *
 * Everything is off by default. The API keys are non-secret client identifiers, but they are
 * still stored in the app's private DataStore, and can alternatively be supplied at build time
 * with -PtenorApiKey=... / -PgiphyApiKey=... (or the matching environment variables).
 */
object GifSearchSettings {
    val Enabled = SettingsKey(
        booleanPreferencesKey("gif_search_enabled"),
        false
    )

    val Provider = SettingsKey(
        stringPreferencesKey("gif_search_provider"),
        GifProviderKind.Tenor.id
    )

    val TenorApiKey = SettingsKey(
        stringPreferencesKey("gif_search_tenor_api_key"),
        BuildConfig.TENOR_API_KEY
    )

    val GiphyApiKey = SettingsKey(
        stringPreferencesKey("gif_search_giphy_api_key"),
        BuildConfig.GIPHY_API_KEY
    )
}

enum class GifProviderKind(val id: String, val displayName: String) {
    Tenor("tenor", "Tenor"),
    Giphy("giphy", "GIPHY");

    companion object {
        fun fromId(id: String?): GifProviderKind =
            entries.firstOrNull { it.id == id } ?: Tenor
    }
}

data class GifItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val provider: String = ""
)

data class GifPage(
    val items: List<GifItem>,
    val next: String?
)

sealed class GifResult {
    data class Success(val page: GifPage) : GifResult()
    data class Error(val message: String) : GifResult()
}

private const val TENOR_BASE = "https://tenor.googleapis.com/v2"
private const val GIPHY_BASE = "https://api.giphy.com/v1/gifs"
private const val TENOR_CLIENT_KEY = "futo_android_keyboard"
private const val TENOR_MEDIA_FILTER = "tinygif,gif,mediumgif"
private const val GIPHY_RATING = "pg"

const val GIF_SEARCH_PAGE_SIZE = 30

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

/**
 * Builds a Tenor v2 request URL. A blank query returns curated/featured GIFs.
 */
fun buildTenorRequestUrl(
    apiKey: String,
    query: String,
    pos: String?,
    limit: Int = GIF_SEARCH_PAGE_SIZE,
    contentFilter: String = "high"
): String {
    val endpoint = if (query.isBlank()) "featured" else "search"
    val params = mutableListOf(
        "key=${encode(apiKey)}",
        "client_key=${encode(TENOR_CLIENT_KEY)}",
        "limit=$limit",
        "media_filter=${encode(TENOR_MEDIA_FILTER)}",
        "contentfilter=${encode(contentFilter)}"
    )

    if (query.isNotBlank()) params.add("q=${encode(query)}")
    if (!pos.isNullOrBlank()) params.add("pos=${encode(pos)}")

    return "$TENOR_BASE/$endpoint?${params.joinToString("&")}"
}

/**
 * Builds a GIPHY v1 request URL. A blank query returns trending GIFs.
 */
fun buildGiphyRequestUrl(
    apiKey: String,
    query: String,
    offset: Int,
    limit: Int = GIF_SEARCH_PAGE_SIZE
): String {
    val endpoint = if (query.isBlank()) "trending" else "search"
    val params = mutableListOf(
        "api_key=${encode(apiKey)}",
        "limit=$limit",
        "offset=$offset",
        "rating=$GIPHY_RATING"
    )

    if (query.isNotBlank()) {
        params.add("q=${encode(query)}")
        params.add("lang=en")
    }

    return "$GIPHY_BASE/$endpoint?${params.joinToString("&")}"
}

fun buildGifRequestUrl(
    provider: GifProviderKind,
    apiKey: String,
    query: String,
    cursor: String?
): String = when (provider) {
    GifProviderKind.Tenor -> buildTenorRequestUrl(apiKey, query, cursor)
    GifProviderKind.Giphy -> buildGiphyRequestUrl(apiKey, query, cursor?.toIntOrNull() ?: 0)
}

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.intOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
}

/**
 * Parses a Tenor v2 search/featured response. Malformed entries are skipped; malformed
 * documents return null so the caller can show an error instead of an empty page.
 */
fun parseTenorResponse(body: String): GifPage? {
    val root = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return null
    }

    val results = root["results"] as? JsonArray ?: return null
    val items = mutableListOf<GifItem>()

    for (element in results) {
        val obj = element as? JsonObject ?: continue
        val id = obj["id"].stringOrNull() ?: continue
        val media = obj["media_formats"] as? JsonObject ?: continue

        fun format(name: String): JsonObject? = media[name] as? JsonObject

        val preview = format("tinygif")
            ?: format("mediumgif")
            ?: format("gif")
            ?: continue
        val full = format("gif") ?: format("mediumgif") ?: preview

        val previewUrl = preview["url"].stringOrNull() ?: continue
        val fullUrl = full["url"].stringOrNull() ?: previewUrl
        val dims = preview["dims"] as? JsonArray

        items.add(
            GifItem(
                id = id,
                title = obj["content_description"].stringOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "GIF",
                previewUrl = previewUrl,
                fullUrl = fullUrl,
                width = dims?.getOrNull(0).intOrNull() ?: 0,
                height = dims?.getOrNull(1).intOrNull() ?: 0,
                provider = GifProviderKind.Tenor.id
            )
        )
    }

    return GifPage(items, root["next"].stringOrNull()?.takeIf { it.isNotBlank() })
}

/**
 * Parses a GIPHY v1 search/trending response. Malformed entries are skipped; malformed
 * documents return null.
 */
fun parseGiphyResponse(body: String): GifPage? {
    val root = try {
        Json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return null
    }

    val data = root["data"] as? JsonArray ?: return null
    val items = mutableListOf<GifItem>()

    for (element in data) {
        val obj = element as? JsonObject ?: continue
        val id = obj["id"].stringOrNull() ?: continue
        val images = obj["images"] as? JsonObject ?: continue

        val preview = (images["fixed_width"] ?: images["fixed_height_small"] ?: images["downsized"])
            as? JsonObject ?: continue
        val full = (images["original"] ?: images["downsized"]) as? JsonObject ?: preview

        val previewUrl = preview["url"].stringOrNull() ?: continue
        val fullUrl = full["url"].stringOrNull() ?: previewUrl

        items.add(
            GifItem(
                id = id,
                title = obj["title"].stringOrNull()?.takeIf { it.isNotBlank() } ?: "GIF",
                previewUrl = previewUrl,
                fullUrl = fullUrl,
                width = preview["width"].intOrNull() ?: 0,
                height = preview["height"].intOrNull() ?: 0,
                provider = GifProviderKind.Giphy.id
            )
        )
    }

    val pagination = root["pagination"] as? JsonObject
    val offset = pagination?.get("offset").intOrNull() ?: 0
    val count = pagination?.get("count").intOrNull() ?: items.size
    val total = pagination?.get("total_count").intOrNull() ?: 0
    val next = when {
        items.isEmpty() || count <= 0 -> null
        // Some GIPHY endpoints omit total_count (or return 0). Keep paging optimistically
        // until a page comes back empty.
        total > 0 -> if (offset + count < total) (offset + count).toString() else null
        else -> (offset + count).toString()
    }

    return GifPage(items, next)
}

private fun extractApiError(body: String): String? {
    return try {
        val root = Json.parseToJsonElement(body).jsonObject
        val error = root["error"] as? JsonObject
        error?.get("message").stringOrNull()
            ?: root["message"].stringOrNull()
            ?: root["error"].stringOrNull()
    } catch (_: Exception) {
        null
    }
}

/**
 * Fetches one page of GIFs. Runs off the main thread; never includes the API key in errors.
 */
suspend fun fetchGifPage(
    provider: GifProviderKind,
    apiKey: String,
    query: String,
    cursor: String?,
    limit: Int = GIF_SEARCH_PAGE_SIZE
): GifResult = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        return@withContext GifResult.Error("Missing API key")
    }

    val url = try {
        buildGifRequestUrl(provider, apiKey, query, cursor)
    } catch (e: Exception) {
        return@withContext GifResult.Error(e.message ?: "Invalid request")
    }

    var connection: HttpURLConnection? = null
    try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FUTO-Keyboard")
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""

        if (status !in 200..299) {
            val message = extractApiError(body)
            return@withContext GifResult.Error(
                message ?: "Server returned HTTP $status"
            )
        }

        val page = when (provider) {
            GifProviderKind.Tenor -> parseTenorResponse(body)
            GifProviderKind.Giphy -> parseGiphyResponse(body)
        }

        if (page == null) {
            GifResult.Error("Could not parse the GIF search response")
        } else {
            GifResult.Success(page)
        }
    } catch (e: Exception) {
        GifResult.Error(e.message ?: "Network error")
    } finally {
        connection?.disconnect()
    }
}
