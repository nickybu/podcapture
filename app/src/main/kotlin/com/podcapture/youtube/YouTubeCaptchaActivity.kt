package com.podcapture.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.podcapture.R
import org.koin.android.ext.android.inject

/**
 * Activity that displays a WebView for solving YouTube CAPTCHAs.
 * After the user completes verification, cookies are saved for future requests.
 */
class YouTubeCaptchaActivity : AppCompatActivity() {

    private val cookieManager: YouTubeCookieManager by inject()

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: Toolbar
    private lateinit var doneButton: Button
    private lateinit var instructionsText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_captcha)

        val url = intent.getStringExtra(EXTRA_URL) ?: YOUTUBE_URL

        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)
        doneButton = findViewById(R.id.doneButton)
        instructionsText = findViewById(R.id.instructionsText)

        // Setup toolbar
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Setup done button
        doneButton.setOnClickListener {
            saveCookiesAndFinish()
        }

        // Setup WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            userAgentString = USER_AGENT
        }

        // Enable cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                url?.let { currentUrl ->
                    if (isSuccessfulNavigation(currentUrl)) {
                        Log.d(TAG, "CAPTCHA appears to be solved, URL: $currentUrl")
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }

        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    private fun isSuccessfulNavigation(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
               url == "https://www.youtube.com/" ||
               url == "https://m.youtube.com/" ||
               (url.contains("youtube.com") && !url.contains("sorry"))
    }

    private fun saveCookiesAndFinish() {
        // Collect cookies from all YouTube-related domains
        val domains = listOf(
            "https://www.youtube.com",
            "https://youtube.com",
            "https://m.youtube.com",
            "https://accounts.google.com",
            "https://www.google.com"
        )

        val allCookies = StringBuilder()
        val cookieMgr = CookieManager.getInstance()

        for (domain in domains) {
            val cookies = cookieMgr.getCookie(domain)
            if (!cookies.isNullOrBlank()) {
                Log.d(TAG, "Cookies from $domain: ${cookies.take(50)}...")
                if (allCookies.isNotEmpty()) {
                    allCookies.append("; ")
                }
                allCookies.append(cookies)
            }
        }

        val finalCookies = allCookies.toString()
        if (finalCookies.isNotBlank()) {
            Log.d(TAG, "Saving ${finalCookies.length} chars of cookies")
            cookieManager.setCookies(finalCookies)
            setResult(RESULT_OK)
        } else {
            Log.w(TAG, "No cookies to save")
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        private const val TAG = "YouTubeCaptchaActivity"
        const val EXTRA_URL = "url"
        private const val YOUTUBE_URL = "https://www.youtube.com"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun createIntent(context: Context, url: String? = null): Intent {
            return Intent(context, YouTubeCaptchaActivity::class.java).apply {
                url?.let { putExtra(EXTRA_URL, it) }
            }
        }
    }
}
