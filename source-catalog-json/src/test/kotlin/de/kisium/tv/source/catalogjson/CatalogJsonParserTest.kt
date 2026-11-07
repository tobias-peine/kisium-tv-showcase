package de.kisium.tv.source.catalogjson

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonParserTest {
    private val parser = CatalogJsonParser(Gson())

    private val document = """
        {
          "schema_version": 1,
          "generated_at": "2026-11-07T11:38:24Z",
          "source": {"id": "xtream:abc123", "type": "xtream"},
          "pagination": {"page_size": 100, "total": 2},
          "movies": [
            {"id": "101", "stream_id": 101, "title": "Alpha", "category_name": "Filme", "rating": 8.2, "added": 1800000000, "container_extension": "mkv", "image_url": "http://img/a.jpg"},
            {"id": "102", "stream_id": 102, "title": "Beta", "category_name": "Doku", "rating": 7.0, "added": 1700000000}
          ]
        }
    """.trimIndent()

    @Test
    fun parses_and_maps_movies() {
        val parsed = parser.parse(document)
        assertEquals(1, parsed.schemaVersion)
        val movies = parser.toPosterItems(parsed)
        assertEquals(2, movies.size)
        assertEquals("Alpha", movies[0].title)
        assertEquals(101, movies[0].streamId)
        assertEquals("xtream:abc123", movies[0].sourceId)
        assertEquals("mkv", movies[0].containerExtension)
        assertEquals(8.2, movies[0].rating!!, 0.001)
        assertEquals("Doku", movies[1].categoryName)
    }

    @Test
    fun rejects_unknown_schema() {
        val invalid = document.replace("\"schema_version\": 1", "\"schema_version\": 2")
        try {
            parser.parse(invalid)
            throw AssertionError("schema_version 2 must be rejected")
        } catch (expected: CatalogJsonException) {
            assertTrue(expected.message!!.contains("schema_version"))
        }
    }

    @Test
    fun skips_invalid_movies_and_deduplicates() {
        val raw = """
            {
              "schema_version": 1,
              "source": {"id": "xtream:test"},
              "movies": [
                {"stream_id": 1, "title": "First", "added": 1},
                {"stream_id": 1, "title": "Duplicate", "added": 2},
                {"stream_id": null, "title": "No id", "added": 3},
                {"stream_id": 2, "title": "", "added": 4}
              ]
            }
        """.trimIndent()
        val movies = parser.toPosterItems(parser.parse(raw))
        assertEquals(1, movies.size)
        assertEquals("Duplicate", movies[0].title)
    }
}
