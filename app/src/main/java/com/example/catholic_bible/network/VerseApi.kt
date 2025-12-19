package com.example.catholic_bible.network

import com.example.catholic_bible.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface VerseApi {
    @GET("verse/today")
    suspend fun getVerseToday(): VerseResponse
}

object VerseService {
    val api: VerseApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL) // must end with "/"
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VerseApi::class.java)
    }
}