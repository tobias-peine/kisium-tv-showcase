package de.kisium.tv.source.xtream

import com.google.gson.annotations.SerializedName

data class XtreamAuthDto(
    @SerializedName("user_info") val userInfo: XtreamUserInfoDto? = null,
    @SerializedName("server_info") val serverInfo: XtreamServerInfoDto? = null,
)

data class XtreamUserInfoDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("auth") val auth: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String? = null,
    @SerializedName("active_cons") val activeConnections: String? = null,
    @SerializedName("max_connections") val maxConnections: String? = null,
)

data class XtreamServerInfoDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("https_port") val httpsPort: String? = null,
    @SerializedName("server_protocol") val protocol: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("time_now") val timeNow: String? = null,
)

data class XtreamCategoryDto(
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("parent_id") val parentId: Int? = null,
)

data class XtreamLiveStreamDto(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int? = null,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int? = null,
    @SerializedName("direct_source") val directSource: String? = null,
)

data class XtreamVodStreamDto(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("stream_id") val streamId: Int? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
    @SerializedName("direct_source") val directSource: String? = null,
)

data class XtreamSeriesDto(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("series_id") val seriesId: Int? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("last_modified") val lastModified: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("youtube_trailer") val youtubeTrailer: String? = null,
)

data class XtreamInfoDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("releasedate") val releaseDateAlt: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
)

data class XtreamEpisodeInfoDto(
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("releasedate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("season") val season: Int? = null,
)

data class XtreamEpisodeDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("episode_num") val episodeNum: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
    @SerializedName("info") val info: XtreamEpisodeInfoDto? = null,
    @SerializedName("season") val season: Int? = null,
)

data class XtreamSeriesInfoDto(
    @SerializedName("info") val info: XtreamInfoDto? = null,
    @SerializedName("episodes") val episodes: Map<String, List<XtreamEpisodeDto>>? = null,
    @SerializedName("seasons") val seasons: List<XtreamSeasonDto>? = null,
)

data class XtreamSeasonDto(
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("episode_count") val episodeCount: Int? = null,
)

data class XtreamEpgResponse(
    @SerializedName("epg_listings") val listings: List<XtreamEpgListingDto>? = null,
)

data class XtreamEpgListingDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("epg_id") val epgId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("lang") val lang: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: String? = null,
    @SerializedName("stop_timestamp") val stopTimestamp: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("now_playing") val nowPlaying: Int? = null,
    @SerializedName("has_archive") val hasArchive: Int? = null,
)

data class XtreamVodInfoDto(
    @SerializedName("movie_data") val movieData: XtreamVodStreamDto? = null,
    @SerializedName("info") val info: XtreamInfoDto? = null,
)
