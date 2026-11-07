package de.kisium.tv.source.xtream

import de.kisium.tv.core.model.MediaKind
import de.kisium.tv.core.model.PosterItem
import java.security.MessageDigest

/**
 * Fallback parser for the classic get.php M3U endpoint. The primary path is the
 * JSON player_api, but some panels only expose a useful playlist via get.php.
 */
class M3uParser(private val sourceId: String) {
    fun parse(content: String): List<PosterItem> {
        val result = mutableListOf<PosterItem>()
        var pending: PendingEntry? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attributes = ATTRIBUTE_REGEX.findAll(line).associate { match ->
                    match.groupValues[1].lowercase() to match.groupValues[2]
                }
                val title = line.substringAfter(',', "").trim()
                pending = PendingEntry(
                    title = title.ifEmpty {
                        attributes["tvg-name"] ?: "Sender"
                    },
                    logo = attributes["tvg-logo"],
                    group = attributes["group-title"],
                    epgId = attributes["tvg-id"],
                )
            } else if (!line.startsWith("#") && pending != null) {
                val entry = pending ?: return@forEach
                result += PosterItem(
                    id = stableId(entry.epgId ?: line),
                    sourceId = sourceId,
                    kind = MediaKind.LIVE,
                    title = entry.title,
                    subtitle = entry.group ?: "Live-TV",
                    imageUrl = entry.logo,
                    backdropUrl = entry.logo,
                    categoryName = entry.group,
                    streamId = null,
                    previewUrl = line,
                    directUrl = line,
                )
                pending = null
            }
        }
        return result
    }

    private fun stableId(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private data class PendingEntry(
        val title: String,
        val logo: String?,
        val group: String?,
        val epgId: String?,
    )

    companion object {
        private val ATTRIBUTE_REGEX = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    }
}
