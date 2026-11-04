package org.futo.inputmethod.latin.uix.actions.gif

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.dataStore

/**
 * A GIF the user has used or starred. Only provider metadata is stored here; the bytes live in
 * [GifCache] (recent GIFs) or the favorites directory (starred GIFs).
 */
@Serializable
data class StoredGif(
    val id: String,
    val title: String,
    val previewUrl: String,
    val fullUrl: String,
    val provider: String,
    val favorite: Boolean = false,
    val lastUsed: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
) {
    fun toGifItem(): GifItem = GifItem(
        id = id,
        title = title,
        previewUrl = previewUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
        provider = provider
    )
}

/**
 * Pure JSON/codec layer for [StoredGif]. Kept free of Android APIs so it can be unit tested.
 */
object GifLibraryCodec {
    const val MAX_RECENTS = 40
    const val MAX_FAVORITES = 200

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): List<StoredGif> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<StoredGif>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(items: List<StoredGif>): String = json.encodeToString(items)

    fun recordUse(
        items: List<StoredGif>,
        item: GifItem,
        provider: GifProviderKind,
        now: Long
    ): List<StoredGif> {
        val existing = items.firstOrNull { it.id == item.id && it.provider == provider.id }
        val updated = StoredGif(
            id = item.id,
            title = item.title,
            previewUrl = item.previewUrl,
            fullUrl = item.fullUrl,
            provider = provider.id,
            favorite = existing?.favorite == true,
            lastUsed = now,
            width = item.width,
            height = item.height
        )

        return normalize(
            items.filterNot { it.id == item.id && it.provider == provider.id } + updated
        )
    }

    fun toggleFavorite(
        items: List<StoredGif>,
        item: GifItem,
        provider: GifProviderKind,
        now: Long
    ): List<StoredGif> {
        val existing = items.firstOrNull { it.id == item.id && it.provider == provider.id }
        val updated = StoredGif(
            id = item.id,
            title = item.title,
            previewUrl = item.previewUrl,
            fullUrl = item.fullUrl,
            provider = provider.id,
            favorite = existing?.favorite != true,
            lastUsed = now,
            width = item.width,
            height = item.height
        )

        return normalize(
            items.filterNot { it.id == item.id && it.provider == provider.id } + updated
        )
    }

    private fun normalize(items: List<StoredGif>): List<StoredGif> {
        val favorites = items
            .filter { it.favorite }
            .sortedByDescending { it.lastUsed }
            .take(MAX_FAVORITES)
        val recents = items
            .filter { !it.favorite }
            .sortedByDescending { it.lastUsed }
            .take(MAX_RECENTS)

        return (favorites + recents).sortedByDescending { it.lastUsed }
    }
}

val GifLibraryKey = SettingsKey(
    stringPreferencesKey("gif_library_v1"),
    ""
)

/**
 * Persistent local library of recent and starred GIFs.
 */
object GifLibrary {
    suspend fun recordUse(context: Context, item: GifItem, provider: GifProviderKind) {
        context.dataStore.edit { preferences ->
            val current = GifLibraryCodec.decode(preferences[GifLibraryKey.key] ?: "")
            preferences[GifLibraryKey.key] = GifLibraryCodec.encode(
                GifLibraryCodec.recordUse(current, item, provider, System.currentTimeMillis())
            )
        }
    }

    /**
     * Toggles the favorite flag and mirrors the GIF bytes into the persistent favorites
     * directory so starred GIFs can be inserted again without a network request.
     */
    suspend fun toggleFavorite(
        context: Context,
        item: GifItem,
        provider: GifProviderKind
    ): Boolean {
        var nowFavorite = false

        context.dataStore.edit { preferences ->
            val current = GifLibraryCodec.decode(preferences[GifLibraryKey.key] ?: "")
            val updated = GifLibraryCodec.toggleFavorite(
                current,
                item,
                provider,
                System.currentTimeMillis()
            )
            nowFavorite = updated
                .firstOrNull { it.id == item.id && it.provider == provider.id }
                ?.favorite == true
            preferences[GifLibraryKey.key] = GifLibraryCodec.encode(updated)
        }

        if (nowFavorite) {
            GifCache.saveFavorite(context, item)
        } else {
            GifCache.deleteFavorite(context, item)
        }

        return nowFavorite
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(GifLibraryKey.key)
        }
        GifCache.clearFavorites(context)
    }
}
