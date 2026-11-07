package de.kisium.tv.source.xtream

import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApi {
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String,
    ): XtreamAuthDto

    @GET("player_api.php")
    suspend fun liveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun vodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun seriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun liveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null,
    ): List<XtreamLiveStreamDto>

    @GET("player_api.php")
    suspend fun vodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null,
    ): List<XtreamVodStreamDto>

    @GET("player_api.php")
    suspend fun series(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String? = null,
    ): List<XtreamSeriesDto>

    @GET("player_api.php")
    suspend fun seriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: String,
    ): XtreamSeriesInfoDto

    @GET("player_api.php")
    suspend fun vodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: String,
    ): XtreamVodInfoDto

    @GET("player_api.php")
    suspend fun shortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: String,
        @Query("limit") limit: Int = 8,
    ): XtreamEpgResponse

    @GET("player_api.php")
    suspend fun simpleDataTable(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_simple_data_table",
        @Query("stream_id") streamId: String,
    ): XtreamEpgResponse
}
