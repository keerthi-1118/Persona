package com.example.persona.data.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private var apiService: PersonaApiService? = null
    private var cookieJar: PersistentCookieJar? = null
    private var currentBaseUrl: String = "https://persona-b6kj.onrender.com/"

    fun getApiService(context: Context): PersonaApiService {
        val sharedPrefs = context.getSharedPreferences("persona_settings", Context.MODE_PRIVATE)
        val savedBaseUrl = sharedPrefs.getString("api_base_url", "https://persona-b6kj.onrender.com/") ?: "https://persona-b6kj.onrender.com/"
        
        if (apiService == null || savedBaseUrl != currentBaseUrl) {
            currentBaseUrl = savedBaseUrl
            if (!currentBaseUrl.endsWith("/")) {
                currentBaseUrl += "/"
            }
            
            val jar = cookieJar ?: PersistentCookieJar(context.applicationContext).also { cookieJar = it }
            
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .cookieJar(jar)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(PersonaApiService::class.java)
        }
        
        return apiService!!
    }

    fun getBaseUrl(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("persona_settings", Context.MODE_PRIVATE)
        return sharedPrefs.getString("api_base_url", "https://persona-b6kj.onrender.com/") ?: "https://persona-b6kj.onrender.com/"
    }

    fun setBaseUrl(context: Context, newUrl: String) {
        val sharedPrefs = context.getSharedPreferences("persona_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("api_base_url", newUrl).apply()
        apiService = null
    }

    fun logout() {
        cookieJar?.clear()
        apiService = null
    }
}
