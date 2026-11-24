package com.miui.euexplorer.di

import android.content.Context
import com.miui.euexplorer.BuildConfig
import com.miui.euexplorer.data.EndlessRandomCatalogEngine
import com.miui.euexplorer.data.FullQualityDownloader
import com.miui.euexplorer.data.WallpaperRepository
import com.miui.euexplorer.data.remote.RateLimitBackoffInterceptor
import com.miui.euexplorer.data.remote.Utf8BomConverterFactory
import com.miui.euexplorer.data.remote.ZhutiApiService
import com.miui.euexplorer.data.remote.ZhutiHeaderInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(RateLimitBackoffInterceptor())
            .addInterceptor(ZhutiHeaderInterceptor())
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val zhutiApiService: ZhutiApiService by lazy {
        val converter = Utf8BomConverterFactory(
            json.asConverterFactory("application/json".toMediaType())
        )
        Retrofit.Builder()
            .baseUrl("https://api.zhuti.xiaomi.com/")
            .client(okHttpClient)
            .addConverterFactory(converter)
            .build()
            .create(ZhutiApiService::class.java)
    }

    private val catalogEngine: EndlessRandomCatalogEngine by lazy {
        EndlessRandomCatalogEngine(apiService = zhutiApiService)
    }

    val wallpaperRepository: WallpaperRepository by lazy {
        WallpaperRepository(engine = catalogEngine)
    }

    val fullQualityDownloader: FullQualityDownloader by lazy {
        FullQualityDownloader(
            context = appContext,
            okHttpClient = okHttpClient
        )
    }
}
