package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.MyApplicationTheme
import java.net.HttpURLConnection
import java.net.URL

const val LOCAL_ASSET_URL = "file:///android_asset/index.html"
const val SHEET_CSV_URL =
  "https://docs.google.com/spreadsheets/d/e/2PACX-1vSbo-rXZFWSjcsGF6ANovjL_kBBvKxjvWE1wKPZ2s5xrwVTVlD8b26DUj0wWU2RWSTkQbQUgsb05IHa/pub?output=csv"

class AndroidSheetBridge {
  @JavascriptInterface
  fun fetchGoogleSheetSync(): String {
    return try {
      var targetUrl = SHEET_CSV_URL
      var redirectCount = 0
      while (redirectCount < 5) {
        val url = URL(targetUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
          connectTimeout = 8000
          readTimeout = 8000
          instanceFollowRedirects = true
          setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
        }
        val status = conn.responseCode
        if (status in 300..399) {
          val location = conn.getHeaderField("Location")
          if (!location.isNullOrEmpty()) {
            targetUrl = location
            redirectCount++
            conn.disconnect()
            continue
          }
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
      }
      ""
    } catch (e: Exception) {
      ""
    }
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    hideSystemBars()
    setContent {
      MyApplicationTheme {
        SpielerPlusWebViewScreen()
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemBars()
    }
  }

  private fun hideSystemBars() {
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
  }
}

@Composable
fun SpielerPlusWebViewScreen() {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }

  // Handle hardware / gesture back button within WebView
  BackHandler(enabled = canGoBack) {
    webViewInstance?.goBack()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .testTag("webview_container")
  ) {
    SpielerPlusWebView(
      url = LOCAL_ASSET_URL,
      onWebViewCreated = { wv ->
        webViewInstance = wv
      },
      onBackStateChanged = { backAvailable ->
        canGoBack = backAvailable
      }
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpielerPlusWebView(
  url: String,
  onWebViewCreated: (WebView) -> Unit,
  onBackStateChanged: (Boolean) -> Unit
) {
  AndroidView(
    factory = { ctx ->
      WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        addJavascriptInterface(AndroidSheetBridge(), "AndroidBridge")

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          allowFileAccessFromFileURLs = true
          allowUniversalAccessFromFileURLs = true
          useWideViewPort = true
          loadWithOverviewMode = true
          cacheMode = WebSettings.LOAD_DEFAULT
          setSupportZoom(false)
          displayZoomControls = false
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
          ): Boolean {
            val reqUrl = request?.url?.toString() ?: return false

            // Keep internal app assets inside WebView
            if (reqUrl.startsWith("file:///android_asset")) {
              return false
            }

            // Open external URLs (e.g. Google Spreadsheet, SpielerPlus website) in system browser
            return try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
              ctx.startActivity(intent)
              true
            } catch (e: Exception) {
              false
            }
          }

          override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: Bitmap?) {
            super.onPageStarted(view, startedUrl, favicon)
            onBackStateChanged(view?.canGoBack() == true)
          }

          override fun onPageFinished(view: WebView?, finishedUrl: String?) {
            super.onPageFinished(view, finishedUrl)
            onBackStateChanged(view?.canGoBack() == true)
          }

          override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
          ) {
            super.onReceivedError(view, request, error)
          }
        }

        webChromeClient = object : WebChromeClient() {
          override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            onBackStateChanged(view?.canGoBack() == true)
          }
        }

        loadUrl(url)
        onWebViewCreated(this)
      }
    },
    update = { webView ->
      if (webView.url != url) {
        webView.loadUrl(url)
      }
    },
    modifier = Modifier.fillMaxSize()
  )
}

// Maintained for backward compatibility and screenshot testing
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}
