package org.futo.inputmethod.latin.uix.actions.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifLibraryTest {

    private fun stored(
        id: String,
        provider: GifProviderKind = GifProviderKind.Giphy,
        favorite: Boolean = false,
        lastUsed: Long = 0L
    ) = StoredGif(
        id = id,
        title = "title-$id",
        previewUrl = "https://example.test/$id/tiny.gif",
        fullUrl = "https://example.test/$id/full.gif",
        provider = provider.id,
        favorite = favorite,
        lastUsed = lastUsed
    )

    private fun item(id: String, provider: GifProviderKind = GifProviderKind.Giphy) = GifItem(
        id = id,
        title = "new-$id",
        previewUrl = "https://example.test/$id/new-tiny.gif",
        fullUrl = "https://example.test/$id/new-full.gif",
        width = 100,
        height = 100,
        provider = provider.id
    )

    @Test
    fun `decode tolerates empty and malformed data`() {
        assertTrue(GifLibraryCodec.decode("").isEmpty())
        assertTrue(GifLibraryCodec.decode("not json").isEmpty())
    }

    @Test
    fun `encode and decode round trip`() {
        val items = listOf(stored("a", favorite = true, lastUsed = 5), stored("b"))

        val decoded = GifLibraryCodec.decode(GifLibraryCodec.encode(items))

        assertEquals(items, decoded)
    }

    @Test
    fun `record use preserves favorite and refreshes metadata`() {
        val existing = stored("a", favorite = true, lastUsed = 1)

        val result = GifLibraryCodec.recordUse(
            listOf(existing),
            item("a"),
            GifProviderKind.Giphy,
            now = 50
        )

        val updated = result.single()
        assertTrue(updated.favorite)
        assertEquals(50L, updated.lastUsed)
        assertEquals("new-a", updated.title)
        assertEquals("https://example.test/a/new-full.gif", updated.fullUrl)
    }

    @Test
    fun `record use caps non-favorites but keeps all favorites`() {
        val favorites = (0 until 5).map { stored("fav-$it", favorite = true, lastUsed = it.toLong()) }
        val recents = (0 until 50).map { stored("recent-$it", lastUsed = 100L + it) }

        val result = GifLibraryCodec.recordUse(
            favorites + recents,
            item("new"),
            GifProviderKind.Giphy,
            now = 1000
        )

        assertEquals(5, result.count { it.favorite })
        assertEquals(GifLibraryCodec.MAX_RECENTS, result.count { !it.favorite })
        assertTrue(result.any { it.id == "new" })
    }

    @Test
    fun `toggle favorite flips state and is reversible`() {
        val once = GifLibraryCodec.toggleFavorite(
            emptyList(),
            item("a"),
            GifProviderKind.Giphy,
            now = 10
        )
        assertTrue(once.single().favorite)
        assertEquals(10L, once.single().lastUsed)

        val twice = GifLibraryCodec.toggleFavorite(
            once,
            item("a"),
            GifProviderKind.Giphy,
            now = 20
        )
        assertFalse(twice.single().favorite)
        assertEquals(20L, twice.single().lastUsed)
    }

    @Test
    fun `entries from different providers do not collide`() {
        val tenor = GifLibraryCodec.recordUse(
            emptyList(),
            item("same", GifProviderKind.Tenor),
            GifProviderKind.Tenor,
            now = 1
        )
        val both = GifLibraryCodec.recordUse(
            tenor,
            item("same", GifProviderKind.Giphy),
            GifProviderKind.Giphy,
            now = 2
        )

        assertEquals(2, both.size)
        assertEquals(setOf("tenor", "giphy"), both.map { it.provider }.toSet())
    }
}
