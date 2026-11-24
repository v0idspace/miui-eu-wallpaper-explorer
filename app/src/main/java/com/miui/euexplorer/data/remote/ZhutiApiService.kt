package com.miui.euexplorer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit gateway for the Xiaomi EU Theme Store (Zhuti V9) wallpaper catalog.
 *
 * Note: `cardStart` is a 0-based card/page index (0..832), where each card yields up to 12 wallpapers.
 */
interface ZhutiApiService {

    @GET("app/v9/uipages/search/tag")
    suspend fun searchByTag(
        @Query("type") type: String = "WALLPAPER",
        @Query("tag") tag: String,
        @Query("cardCount") cardCount: Int = 1,
        @Query("cardStart") cardStart: Int = 0,
        @Query("region") region: String = "EU"
    ): ZhutiApiResponse<ZhutiSearchData>
}
