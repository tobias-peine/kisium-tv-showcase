package de.kisium.tv.source.catalogjson

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import de.kisium.tv.core.model.KisiumServerDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Loads catalog.json from an HTTP(S) URL, a content:// URI or a local file path.
 *
 * All blocking I/O happens on Dispatchers.IO. The limit prevents a malformed
 * file from allocating an unbounded amount of memory on a 1–2 GB TV stick.
 */
class CatalogJsonLoader(
    context: Context,
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    private val appContext = context.applicationContext
    private val parser = CatalogJsonParser(gson)

    suspend fun load(location: String): CatalogDocument = withContext(Dispatchers.IO) {
        // Zentrale Netzwerkadresse: wird verwendet, wenn in den Settings nichts steht.
        val target = location.trim().ifBlank { DEFAULT_URI }
        val raw = when {
            target.startsWith("http://", ignoreCase = true) ||
                target.startsWith("https://", ignoreCase = true) -> readHttp(target)

            target.startsWith("content://", ignoreCase = true) -> {
                val input = appContext.contentResolver.openInputStream(Uri.parse(target))
                    ?: throw CatalogJsonException("Content-URI konnte nicht geöffnet werden.")
                input.use { readLimited(it) }
            }

            target.startsWith("file://", ignoreCase = true) -> {
                val path = Uri.parse(target).path
                    ?: throw CatalogJsonException("file://-URI ohne Pfad.")
                readFile(File(path))
            }

            else -> readFile(File(target))
        }
        parser.parse(raw)
    }

    private fun readHttp(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "KisiumTV/1.0 catalog-json")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw CatalogJsonException("Katalog-URL antwortet mit HTTP ${response.code}.")
            }
            val body = response.body ?: throw CatalogJsonException("Katalog-Antwort ist leer.")
            if (body.contentLength() > MAX_BYTES) {
                throw CatalogJsonException("Katalog ist größer als ${MAX_BYTES / 1024 / 1024} MB.")
            }
            body.byteStream().use { readLimited(it) }
        }
    }

    private fun readFile(file: File): String {
        if (!file.isFile) throw CatalogJsonException("Katalogdatei nicht gefunden: ${file.path}")
        if (file.length() > MAX_BYTES) {
            throw CatalogJsonException("Katalog ist größer als ${MAX_BYTES / 1024 / 1024} MB.")
        }
        return FileInputStream(file).use { readLimited(it) }
    }

    private fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) {
                throw CatalogJsonException("Katalog ist größer als ${MAX_BYTES / 1024 / 1024} MB.")
            }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    companion object {
        const val MAX_BYTES: Long = 32L * 1024L * 1024L
        const val DEFAULT_URI: String = KisiumServerDefaults.CATALOG_JSON_URI
    }
}
