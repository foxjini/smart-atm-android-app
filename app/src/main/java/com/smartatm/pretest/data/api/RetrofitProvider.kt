package com.smartatm.pretest.data.api

import com.smartatm.pretest.data.local.PreferencesManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitProvider(private val preferencesManager: PreferencesManager) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private var currentBaseUrl: String? = null
    private var cachedApiService: ApiService? = null

    private fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    fun getApiService(): ApiService {
        var url = preferencesManager.serverUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }

        if (cachedApiService != null && currentBaseUrl == url) {
            return cachedApiService!!
        }

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .client(createOkHttpClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        currentBaseUrl = url
        val service = retrofit.create(ApiService::class.java)
        cachedApiService = service
        return service
    }

    fun invalidate() {
        cachedApiService = null
        currentBaseUrl = null
    }
}
