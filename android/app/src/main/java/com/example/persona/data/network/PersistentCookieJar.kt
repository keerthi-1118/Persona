package com.example.persona.data.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val sharedPreferences = context.getSharedPreferences("persona_cookies", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, List<Cookie>>()

    init {
        // Load saved cookies from SharedPreferences
        val allKeys = sharedPreferences.all
        for ((host, cookieString) in allKeys) {
            if (cookieString is String) {
                val cookieList = deserializeCookies(host, cookieString)
                cookies[host] = cookieList
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookiesList: List<Cookie>) {
        val host = url.host
        cookies[host] = cookiesList

        // Serialize and save to SharedPreferences
        val serialized = serializeCookies(cookiesList)
        sharedPreferences.edit().putString(host, serialized).apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookieList = cookies[host] ?: emptyList()
        
        // Filter out expired cookies
        val activeCookies = cookieList.filter { it.expiresAt > System.currentTimeMillis() }
        if (activeCookies.size < cookieList.size) {
            cookies[host] = activeCookies
            sharedPreferences.edit().putString(host, serializeCookies(activeCookies)).apply()
        }
        return activeCookies
    }

    private fun serializeCookies(cookies: List<Cookie>): String {
        return cookies.joinToString(";") { cookie ->
            "${cookie.name}=${cookie.value}|${cookie.expiresAt}|${cookie.domain}|${cookie.path}|${cookie.secure}|${cookie.httpOnly}"
        }
    }

    private fun deserializeCookies(host: String, cookieString: String): List<Cookie> {
        if (cookieString.isEmpty()) return emptyList()
        return cookieString.split(";").mapNotNull { part ->
            try {
                val subParts = part.split("|")
                if (subParts.size >= 6) {
                    val nameValue = subParts[0].split("=")
                    val name = nameValue[0]
                    val value = nameValue[1]
                    val expiresAt = subParts[1].toLong()
                    val domain = subParts[2]
                    val path = subParts[3]
                    val secure = subParts[4].toBoolean()
                    val httpOnly = subParts[5].toBoolean()

                    val builder = Cookie.Builder()
                        .name(name)
                        .value(value)
                        .expiresAt(expiresAt)
                        .path(path)

                    if (secure) builder.secure()
                    if (httpOnly) builder.httpOnly()
                    
                    if (domain == host) {
                        builder.hostOnlyDomain(domain)
                    } else {
                        builder.domain(domain)
                    }
                    builder.build()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun clear() {
        cookies.clear()
        sharedPreferences.edit().clear().apply()
    }
}
