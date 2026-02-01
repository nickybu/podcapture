package com.podcapture.youtube

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages YouTube cookies for authentication and CAPTCHA bypass.
 */
class YouTubeCookieManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get stored cookies as a single string for the Cookie header.
     */
    fun getCookies(): String? {
        val cookies = prefs.getString(KEY_COOKIES, null)
        return if (cookies.isNullOrBlank()) null else cookies
    }

    /**
     * Store cookies from a WebView session.
     */
    fun setCookies(cookies: String) {
        prefs.edit { putString(KEY_COOKIES, cookies) }
    }

    /**
     * Clear all stored cookies.
     */
    fun clearCookies() {
        prefs.edit { remove(KEY_COOKIES) }
    }

    /**
     * Check if we have stored cookies.
     */
    fun hasCookies(): Boolean {
        return !prefs.getString(KEY_COOKIES, null).isNullOrBlank()
    }

    companion object {
        private const val PREFS_NAME = "youtube_cookies"
        private const val KEY_COOKIES = "cookies"
    }
}
