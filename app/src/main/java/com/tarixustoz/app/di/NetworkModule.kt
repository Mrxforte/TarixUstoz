package com.tarixustoz.app.di

import android.content.Context
import com.tarixustoz.app.BuildConfig
import com.tarixustoz.app.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "tarix_http_cache")
        val cache = Cache(cacheDir, 25L * 1024L * 1024L) // 25 MB HTTP disk cache

        return OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val path = request.url.encodedPath
                val isDailyOrBusted = path.contains("daily", ignoreCase = true) ||
                    request.url.queryParameter("nonce") != null ||
                    request.url.queryParameter("exclude") != null
                val hasConditionalRevalidation = !request.header("If-None-Match").isNullOrBlank() ||
                    !request.header("If-Modified-Since").isNullOrBlank() ||
                    request.header("Cache-Control")?.contains("no-cache", ignoreCase = true) == true

                if (request.method.equals("GET", ignoreCase = true) && !isDailyOrBusted) {
                    if (hasConditionalRevalidation) {
                        response.newBuilder()
                            .header("Cache-Control", "public, max-age=0, must-revalidate")
                            .removeHeader("Pragma")
                            .build()
                    } else {
                        val existingCacheControl = response.header("Cache-Control")
                        if (existingCacheControl.isNullOrBlank() || existingCacheControl.contains("no-store")) {
                            response.newBuilder()
                                .header("Cache-Control", CacheControl.Builder().maxAge(30, TimeUnit.MINUTES).build().toString())
                                .removeHeader("Pragma")
                                .build()
                        } else {
                            response
                        }
                    }
                } else if (isDailyOrBusted) {
                    response.newBuilder()
                        .header("Cache-Control", "no-store, no-cache, must-revalidate")
                        .removeHeader("Pragma")
                        .build()
                } else {
                    response
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.TARIX_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
