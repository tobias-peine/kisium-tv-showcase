package de.kisium.tv.source.xtream

import com.google.gson.Gson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap

class XtreamApiFactory(
    private val gson: Gson,
    private val client: OkHttpClient,
) {
    private val clients = ConcurrentHashMap<String, XtreamApi>()

    fun create(config: XtreamConfig): XtreamApi {
        return clients.getOrPut(config.baseUrl) {
            Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(XtreamApi::class.java)
        }
    }

    fun liveStreamUrl(config: XtreamConfig, streamId: Int, extension: String = "m3u8"): String {
        return streamUrl(config, "live", streamId, extension)
    }

    fun movieStreamUrl(config: XtreamConfig, streamId: Int, extension: String = "mp4"): String {
        return streamUrl(config, "movie", streamId, extension)
    }

    fun episodeStreamUrl(config: XtreamConfig, episodeId: Int, extension: String = "mp4"): String {
        return streamUrl(config, "series", episodeId, extension)
    }

    fun m3uUrl(config: XtreamConfig): String {
        return base(config)
            .addPathSegment("get.php")
            .addQueryParameter("username", config.username)
            .addQueryParameter("password", config.password)
            .addQueryParameter("type", "m3u_plus")
            .addQueryParameter("output", "ts")
            .build()
            .toString()
    }

    fun xmltvUrl(config: XtreamConfig): String {
        return base(config)
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", config.username)
            .addQueryParameter("password", config.password)
            .build()
            .toString()
    }

    private fun streamUrl(config: XtreamConfig, type: String, id: Int, extension: String): String {
        return base(config)
            .addPathSegment(type)
            .addPathSegment(config.username)
            .addPathSegment(config.password)
            .addPathSegment("$id.${extension.ifBlank { "mp4" }}")
            .build()
            .toString()
    }

    private fun base(config: XtreamConfig): HttpUrl.Builder {
        return config.baseUrl.toHttpUrl().newBuilder()
    }
}
