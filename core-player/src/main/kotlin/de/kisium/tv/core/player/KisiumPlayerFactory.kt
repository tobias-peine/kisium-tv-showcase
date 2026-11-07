package de.kisium.tv.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import de.kisium.tv.core.model.PlaybackRequest
import okhttp3.OkHttpClient

@UnstableApi
object KisiumPlayerFactory {
    fun create(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000, // min buffer
                32_000, // max buffer
                1_200,  // play buffer
                2_500,  // rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
            }
    }

    fun mediaSource(
        request: PlaybackRequest,
        client: OkHttpClient,
        urlOverride: String? = null,
    ): MediaSource {
        val url = urlOverride ?: request.url
        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent("KisiumTV/1.0 (Android TV)")
            .setDefaultRequestProperties(request.headers)
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(request.itemKey)
            .build()
        return if (looksLikeHls(url)) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(
                dataSourceFactory,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
            ).createMediaSource(item)
        }
    }

    fun prepare(
        player: ExoPlayer,
        request: PlaybackRequest,
        client: OkHttpClient,
        urlOverride: String? = null,
        startPositionMs: Long = request.startPositionMs,
    ) {
        player.setMediaSource(mediaSource(request, client, urlOverride))
        if (startPositionMs > 0L) player.seekTo(startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    private fun looksLikeHls(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".m3u8") || clean.contains(".m3u8/")
    }
}
