package de.kisium.tv.source.xtream

import android.util.Xml
import de.kisium.tv.core.model.EpgEntry
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Streaming parser for xmltv.php. Full XMLTV files can be several hundred MB,
 * so this parser never builds a DOM and can be capped by [maxEntries].
 */
class XmltvParser {
    fun parse(input: InputStream, maxEntries: Int = 20_000): Map<String, List<EpgEntry>> {
        val result = linkedMapOf<String, MutableList<EpgEntry>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var inProgramme = false
        var channelId: String? = null
        var start: Long? = null
        var stop: Long? = null
        var title: String? = null
        var description: String? = null
        var count = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && count < maxEntries) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                    "programme" -> {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel")
                        start = parseXmltvDate(parser.getAttributeValue(null, "start"))
                        stop = parseXmltvDate(parser.getAttributeValue(null, "stop"))
                        title = null
                        description = null
                    }

                    "title" -> if (inProgramme) title = parser.nextText().trim()
                    "desc" -> if (inProgramme) description = parser.nextText().trim()
                }

                XmlPullParser.END_TAG -> if (
                    parser.name.equals("programme", ignoreCase = true) && inProgramme
                ) {
                    val channel = channelId
                    val from = start
                    val to = stop
                    if (channel != null && from != null && to != null && to > from) {
                        result.getOrPut(channel) { mutableListOf() }.add(
                            EpgEntry(
                                channelId = channel,
                                startMillis = from,
                                endMillis = to,
                                title = title?.ifBlank { "Ohne Titel" } ?: "Ohne Titel",
                                description = description?.takeIf { it.isNotBlank() },
                            ),
                        )
                        count++
                    }
                    inProgramme = false
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseXmltvDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim()
        val patterns = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(normalized)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
