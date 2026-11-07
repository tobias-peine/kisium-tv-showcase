package de.kisium.tv.source.xtream

import java.security.MessageDigest

data class XtreamConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    val normalizedServer: String = normalizeServerUrl(serverUrl)
    val baseUrl: String = "$normalizedServer/"
    val sourceId: String = "xtream:" + sha1("$normalizedServer|$username").take(12)

    companion object {
        fun normalizeServerUrl(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return ""
            if (!value.startsWith("http://", ignoreCase = true) &&
                !value.startsWith("https://", ignoreCase = true)
            ) {
                value = "http://$value"
            }
            value = value.substringBefore("?")
            value = value.removeSuffix("/")
            value = value.removeSuffix("/player_api.php")
            value = value.removeSuffix("/get.php")
            value = value.removeSuffix("/xmltv.php")
            return value.trimEnd('/')
        }

        private fun sha1(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
