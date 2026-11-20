package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxedWebPreview(
    htmlBody: String,
    cssCode: String,
    jsCode: String,
    desktopMode: Boolean = false,
    reloadNonce: Int = 0,
    onConsoleLog: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val fullDocument = remember(htmlBody, cssCode, jsCode, desktopMode) {
        val viewportMeta = if (desktopMode) {
            "<meta name=\"viewport\" content=\"width=1024, initial-scale=0.35\">"
        } else {
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
        }
        if (htmlBody.contains("<html", ignoreCase = true)) {
            htmlBody
        } else {
            """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              $viewportMeta
              <style>$cssCode</style>
            </head>
            <body>
              $htmlBody
              <script>
                try {
                  $jsCode
                } catch (err) {
                  console.error(err.message);
                }
              </script>
            </body>
            </html>
            """.trimIndent()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = if (desktopMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxSize()
            },
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.useWideViewPort = desktopMode
                    settings.loadWithOverviewMode = desktopMode
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                onConsoleLog("[${it.messageLevel()}] ${it.message()}")
                            }
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                webView.settings.useWideViewPort = desktopMode
                webView.settings.loadWithOverviewMode = desktopMode
                val tagKey = "$fullDocument#$reloadNonce"
                if (webView.tag != tagKey) {
                    webView.tag = tagKey
                    webView.loadDataWithBaseURL(null, fullDocument, "text/html", "utf-8", null)
                }
            }
        )
    }
}
