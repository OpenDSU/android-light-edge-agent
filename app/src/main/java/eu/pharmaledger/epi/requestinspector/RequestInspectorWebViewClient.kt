package eu.pharmaledger.epi.requestinspector

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
open class RequestInspectorWebViewClient @JvmOverloads constructor(
        webView: WebView,
        private val options: RequestInspectorOptions = RequestInspectorOptions()
) : WebViewClient() {

    private val interceptionJavascriptInterface = RequestInspectorJavaScriptInterface(webView)

    init {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
    }

    final override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
    ): WebResourceResponse? {
        var nonce: Int? = null
        if (request.requestHeaders != null && request.requestHeaders.containsKey("nonce")) {
            val nonceHeader = request.requestHeaders.get("nonce")
            try {
                if (nonceHeader != null) {
                    nonce = nonceHeader.toInt()
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "FAILED to parse NONCE $nonceHeader to int")
            }
        }
        val recordedRequest = interceptionJavascriptInterface.findRecordedRequestForUrl(
                nonce,
                request.url.toString()
        )
        val webViewRequest = WebViewRequest.create(request, recordedRequest)
        return shouldInterceptRequest(view, webViewRequest)
    }

    open fun shouldInterceptRequest(
            view: WebView,
            webViewRequest: WebViewRequest
    ): WebResourceResponse? {
        logWebViewRequest(webViewRequest)
        return null
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun logWebViewRequest(webViewRequest: WebViewRequest) {
        Log.i(LOG_TAG, "Sending request from WebView: $webViewRequest")
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        Log.i(LOG_TAG, "Page started loading, enabling request inspection. URL: $url")
        RequestInspectorJavaScriptInterface.enabledRequestInspection(
                view,
                options.extraJavaScriptToInject
        )
        super.onPageStarted(view, url, favicon)
    }

    companion object {
        private const val LOG_TAG = "RequestInspectorWebView"
    }
}
