package org.futo.inputmethod.latin.uix.actions.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class GifApiTest {

    private fun param(url: String, name: String): String? {
        val query = URI(url).rawQuery ?: return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it[0] == name }
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    @Test
    fun `tenor search url carries query key and pagination`() {
        val url = buildTenorRequestUrl(
            apiKey = "test-key",
            query = "happy cat",
            pos = "CURSOR",
            limit = 12
        )

        assertTrue(url.startsWith("https://tenor.googleapis.com/v2/search?"))
        assertEquals("test-key", param(url, "key"))
        assertEquals("happy cat", param(url, "q"))
        assertEquals("CURSOR", param(url, "pos"))
        assertEquals("12", param(url, "limit"))
        assertEquals("high", param(url, "contentfilter"))
        assertEquals("tinygif,gif,mediumgif", param(url, "media_filter"))
    }

    @Test
    fun `tenor blank query uses featured endpoint`() {
        val url = buildTenorRequestUrl("test-key", "", null)

        assertTrue(url.startsWith("https://tenor.googleapis.com/v2/featured?"))
        assertNull(param(url, "q"))
        assertNull(param(url, "pos"))
    }

    @Test
    fun `giphy search url carries query rating and offset`() {
        val url = buildGiphyRequestUrl("giphy-key", "dogs", offset = 24, limit = 10)

        assertTrue(url.startsWith("https://api.giphy.com/v1/gifs/search?"))
        assertEquals("giphy-key", param(url, "api_key"))
        assertEquals("dogs", param(url, "q"))
        assertEquals("24", param(url, "offset"))
        assertEquals("10", param(url, "limit"))
        assertEquals("pg", param(url, "rating"))
    }

    @Test
    fun `giphy blank query uses trending endpoint`() {
        val url = buildGiphyRequestUrl("giphy-key", "", offset = 0)

        assertTrue(url.startsWith("https://api.giphy.com/v1/gifs/trending?"))
        assertNull(param(url, "q"))
    }

    @Test
    fun `parse tenor response skips malformed entries`() {
        val body = """
            {
              "results": [
                {
                  "id": "123",
                  "content_description": "A happy cat",
                  "media_formats": {
                    "tinygif": { "url": "https://media.tenor.com/a/tiny.gif", "dims": [220, 160] },
                    "gif": { "url": "https://media.tenor.com/a/full.gif", "dims": [498, 360] }
                  }
                },
                {
                  "id": "bad",
                  "content_description": "No formats",
                  "media_formats": {}
                },
                {
                  "content_description": "Missing id",
                  "media_formats": {
                    "tinygif": { "url": "https://media.tenor.com/b/tiny.gif" }
                  }
                }
              ],
              "next": "CAEQ"
            }
        """.trimIndent()

        val page = parseTenorResponse(body)

        assertEquals(1, page?.items?.size)
        assertEquals("123", page?.items?.get(0)?.id)
        assertEquals("A happy cat", page?.items?.get(0)?.title)
        assertEquals("https://media.tenor.com/a/tiny.gif", page?.items?.get(0)?.previewUrl)
        assertEquals("https://media.tenor.com/a/full.gif", page?.items?.get(0)?.fullUrl)
        assertEquals(220, page?.items?.get(0)?.width)
        assertEquals(160, page?.items?.get(0)?.height)
        assertEquals("CAEQ", page?.next)
    }

    @Test
    fun `parse tenor response falls back to the full gif for preview`() {
        val body = """
            {
              "results": [
                {
                  "id": "only-gif",
                  "media_formats": {
                    "gif": { "url": "https://media.tenor.com/a/full.gif", "dims": [10, 20] }
                  }
                }
              ]
            }
        """.trimIndent()

        val item = parseTenorResponse(body)?.items?.single()

        assertEquals("https://media.tenor.com/a/full.gif", item?.previewUrl)
        assertEquals("https://media.tenor.com/a/full.gif", item?.fullUrl)
        assertEquals("GIF", item?.title)
        assertNull(parseTenorResponse(body)?.next)
    }

    @Test
    fun `parse giphy response extracts urls and computes next offset`() {
        val body = """
            {
              "data": [
                {
                  "id": "g1",
                  "title": "A dog",
                  "images": {
                    "fixed_width": { "url": "https://media.giphy.com/a/200w.gif", "width": "200", "height": "150" },
                    "original": { "url": "https://media.giphy.com/a/giphy.gif", "width": "480", "height": "360" }
                  }
                }
              ],
              "pagination": { "offset": 0, "count": 1, "total_count": 25 }
            }
        """.trimIndent()

        val page = parseGiphyResponse(body)

        assertEquals(1, page?.items?.size)
        assertEquals("g1", page?.items?.get(0)?.id)
        assertEquals("A dog", page?.items?.get(0)?.title)
        assertEquals("https://media.giphy.com/a/200w.gif", page?.items?.get(0)?.previewUrl)
        assertEquals("https://media.giphy.com/a/giphy.gif", page?.items?.get(0)?.fullUrl)
        assertEquals(200, page?.items?.get(0)?.width)
        assertEquals(150, page?.items?.get(0)?.height)
        assertEquals("1", page?.next)
    }

    @Test
    fun `parse giphy response stops at the last page`() {
        val body = """
            {
              "data": [
                {
                  "id": "g1",
                  "images": {
                    "fixed_width": { "url": "https://media.giphy.com/a/200w.gif", "width": "200", "height": "150" }
                  }
                }
              ],
              "pagination": { "offset": 24, "count": 1, "total_count": 25 }
            }
        """.trimIndent()

        assertNull(parseGiphyResponse(body)?.next)
    }

    @Test
    fun `malformed responses return null instead of an empty page`() {
        assertNull(parseTenorResponse("not json"))
        assertNull(parseGiphyResponse("{\"data\": 3}"))
    }
}
