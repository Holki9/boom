package com.holki.boom.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Headless zero-UI background stream extractor.
 * Intercepts .m3u8 and .mp4 stream URLs from web balancers (Alloha, Kinobox, Collaps, Rezka)
 * so playback happens 100% inside native ExoPlayer with Samsung One UI controls.
 */
public class StreamSniffer {
    public interface Callback {
        void onStreamResolved(String streamUrl, String referer);
        void onError(String message);
    }

    private final Context context;
    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isResolved = new AtomicBoolean(false);
    private Runnable timeoutRunnable;

    private static final Pattern AD_FILTER_PATTERN = Pattern.compile(
            "(vast|preroll|midroll|postroll|adriver|yandex|doubleclick|googlesyndication|acint|counter|banner|marketgid|adnxs|teads|moatads)",
            Pattern.CASE_INSENSITIVE
    );

    public StreamSniffer(Context context) {
        this.context = context.getApplicationContext();
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public void sniff(String embedUrl, Callback callback) {
        destroy();
        isResolved.set(false);

        handler.post(() -> {
            try {
                webView = new WebView(context);
                WebSettings ws = webView.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setDatabaseEnabled(true);
                ws.setMediaPlaybackRequiresUserGesture(false);
                ws.setAllowFileAccess(true);
                ws.setAllowContentAccess(true);
                ws.setUseWideViewPort(true);
                ws.setLoadWithOverviewMode(true);
                ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(webView, true);

                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void onStreamDetected(String url) {
                        handleDetectedUrl(url, embedUrl, callback);
                    }
                }, "BoomBridge");

                webView.setWebChromeClient(new WebChromeClient());
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        if (request != null && request.getUrl() != null) {
                            String reqUrl = request.getUrl().toString();
                            if (isTargetStream(reqUrl)) {
                                handleDetectedUrl(reqUrl, embedUrl, callback);
                            }
                        }
                        return super.shouldInterceptRequest(view, request);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        injectHooks(view);
                    }
                });

                timeoutRunnable = () -> {
                    if (isResolved.compareAndSet(false, true)) {
                        destroy();
                        callback.onError("Direct stream not sniffed");
                    }
                };
                handler.postDelayed(timeoutRunnable, 2200);

                String finalEmbedUrl = embedUrl;
                if (!finalEmbedUrl.contains("autoplay=")) {
                    finalEmbedUrl += (finalEmbedUrl.contains("?") ? "&" : "?") + "autoplay=1";
                }

                String html = "<!DOCTYPE html><html><head>" +
                        "<meta charset=\"utf-8\">" +
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                        "<script>" +
                        "window.addEventListener('message', function(ev) {" +
                        "  if (ev && ev.data && ev.data.action === 'streamFound') {" +
                        "    if (window.BoomBridge) window.BoomBridge.onStreamDetected(ev.data.url);" +
                        "  }" +
                        "});" +
                        "</script>" +
                        "</head><body style=\"margin:0;padding:0;background:#000;\">" +
                        "<iframe src=\"" + finalEmbedUrl + "\" allow=\"autoplay *; fullscreen *; encrypted-media *\"></iframe>" +
                        "</body></html>";

                webView.loadDataWithBaseURL("https://kinogo.inc/", html, "text/html", "UTF-8", null);

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    private boolean isTargetStream(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        if (AD_FILTER_PATTERN.matcher(lower).find()) return false;
        return lower.contains(".m3u8") || lower.contains(".mp4");
    }

    private void handleDetectedUrl(String url, String embedUrl, Callback callback) {
        if (!isTargetStream(url)) return;

        if (isResolved.compareAndSet(false, true)) {
            handler.removeCallbacks(timeoutRunnable);
            handler.post(() -> {
                String referer = "https://kinogo.inc/";
                try {
                    java.net.URI uri = new java.net.URI(embedUrl);
                    referer = uri.getScheme() + "://" + uri.getHost() + "/";
                } catch (Exception ignored) {}

                callback.onStreamResolved(url, referer);

                handler.postDelayed(this::destroy, 500);
            });
        }
    }

    private void injectHooks(WebView view) {
        String js = "(function() {" +
                "  function report(url) {" +
                "    if (!url || typeof url !== 'string') return;" +
                "    if (url.indexOf('.m3u8') !== -1 || url.indexOf('.mp4') !== -1) {" +
                "      if (window.BoomBridge) { window.BoomBridge.onStreamDetected(url); }" +
                "    }" +
                "  }" +
                "  try {" +
                "    document.querySelectorAll('video').forEach(function(v) {" +
                "      if (v.src) report(v.src);" +
                "      v.addEventListener('loadstart', function() { report(v.src); });" +
                "    });" +
                "    var obs = new MutationObserver(function(muts) {" +
                "      muts.forEach(function(m) {" +
                "        m.addedNodes.forEach(function(n) {" +
                "          if (n.nodeName === 'VIDEO') {" +
                "            if (n.src) report(n.src);" +
                "            n.addEventListener('loadstart', function() { report(n.src); });" +
                "          }" +
                "        });" +
                "      });" +
                "    });" +
                "    obs.observe(document.documentElement, { childList: true, subtree: true });" +
                "  } catch(e) {}" +
                "  var hlsCheck = setInterval(function() {" +
                "    if (window.Hls && window.Hls.prototype && !window.Hls.prototype.__boom) {" +
                "      window.Hls.prototype.__boom = true;" +
                "      var orig = window.Hls.prototype.loadSource;" +
                "      window.Hls.prototype.loadSource = function(src) {" +
                "        report(src);" +
                "        return orig.apply(this, arguments);" +
                "      };" +
                "    }" +
                "  }, 100);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    public void destroy() {
        handler.removeCallbacks(timeoutRunnable);
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.removeJavascriptInterface("BoomBridge");
                webView.destroy();
            } catch (Exception ignored) {}
            webView = null;
        }
    }
}
