package com.holki.boom.ui;

import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import java.util.concurrent.TimeUnit;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.holki.boom.R;
import com.holki.boom.api.CollapsScraper;
import com.holki.boom.api.StreamSniffer;
import com.holki.boom.data.WatchHistoryStore;
import com.holki.boom.model.StreamSource;
import com.holki.boom.model.WatchHistoryItem;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webViewPlayer;
    private FrameLayout flFullscreenContainer;
    private RelativeLayout rlControls;
    private View gestureOverlay;

    // Top Bar UI
    private TextView tvTitle, tvSubtitle;
    private ImageView btnBack, btnPip, btnLock;
    private TextView btnAudioTrack, btnEpisodes, btnAspectRatio, btnSpeed;

    // Center Controls
    private LinearLayout llCenterControls;
    private ImageView btnReplay10, btnPlayPause, btnForward10;

    // Bottom Bar
    private TextView tvCurrentTime, tvTotalTime, btnNextEpisode;
    private SeekBar seekBar;
    private ImageView btnFullscreen;

    // Gesture & Status HUDs
    private LinearLayout llBrightnessHud, llVolumeHud, llPlayerBuffering, llLockOverlay;
    private TextView tvBrightnessValue, tvVolumeValue, tvBufferingStatus;
    private TextView tvSeekLeftBadge, tvSeekRightBadge;
    private ProgressBar pbPlayerBuffering;

    private StreamSource streamSource;
    private com.holki.boom.model.MediaItem mediaItem;
    private int season = 1;
    private int episode = 1;

    private WatchHistoryStore historyStore;
    private AudioManager audioManager;
    private StreamSniffer streamSniffer;
    private CollapsScraper collapsScraper;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isControlsVisible = true;
    private boolean isLocked = false;
    private boolean isWebPlaying = false;
    private float currentPlaybackSpeed = 1.0f;

    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    private int resizeModeIndex = 1; // Default to 20:9 zoom for Galaxy S24 Ultra display
    private static final int[] RESIZE_MODES = {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
    };
    private static final String[] RESIZE_NAMES = {
            "Оригинал (Fit)",
            "20:9 Заполнение экрана (Zoom)",
            "Растянуть (Fill)"
    };
    private static final String[] RESIZE_BTN_LABELS = {
            "↔ Fit",
            "↔ 20:9",
            "↔ Fill"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Samsung One UI Immersive Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_player);

        streamSource = (StreamSource) getIntent().getSerializableExtra("stream_source");
        mediaItem = (com.holki.boom.model.MediaItem) getIntent().getSerializableExtra("media_item");
        season = getIntent().getIntExtra("season", 1);
        episode = getIntent().getIntExtra("episode", 1);

        historyStore = new WatchHistoryStore(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        streamSniffer = new StreamSniffer(this);
        collapsScraper = new CollapsScraper();

        initViews();
        setupControls();
        setupGestures();
        setupBackHandling();

        startStreamResolution();
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        webViewPlayer = findViewById(R.id.webViewPlayer);
        flFullscreenContainer = findViewById(R.id.flFullscreenContainer);
        rlControls = findViewById(R.id.rlPlayerControls);
        gestureOverlay = findViewById(R.id.viewGestureOverlay);

        tvTitle = findViewById(R.id.tvPlayerTitle);
        tvSubtitle = findViewById(R.id.tvPlayerSubtitle);
        btnBack = findViewById(R.id.btnPlayerBack);
        btnPip = findViewById(R.id.btnPip);
        btnAudioTrack = findViewById(R.id.btnAudioTrack);
        btnEpisodes = findViewById(R.id.btnEpisodes);
        btnAspectRatio = findViewById(R.id.btnAspectRatio);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnLock = findViewById(R.id.btnLock);

        llCenterControls = findViewById(R.id.llCenterControls);
        btnReplay10 = findViewById(R.id.btnReplay10);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnForward10 = findViewById(R.id.btnForward10);

        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBar = findViewById(R.id.sbPlayer);
        btnNextEpisode = findViewById(R.id.btnNextEpisode);
        btnFullscreen = findViewById(R.id.btnPlayerFullscreen);

        llBrightnessHud = findViewById(R.id.llBrightnessHud);
        llVolumeHud = findViewById(R.id.llVolumeHud);
        tvBrightnessValue = findViewById(R.id.tvBrightnessValue);
        tvVolumeValue = findViewById(R.id.tvVolumeValue);
        tvSeekLeftBadge = findViewById(R.id.tvSeekLeftBadge);
        tvSeekRightBadge = findViewById(R.id.tvSeekRightBadge);

        llPlayerBuffering = findViewById(R.id.llPlayerBuffering);
        pbPlayerBuffering = findViewById(R.id.pbPlayerBuffering);
        tvBufferingStatus = findViewById(R.id.tvBufferingStatus);
        llLockOverlay = findViewById(R.id.llLockOverlay);

        // Apply initial aspect ratio for Galaxy S24 Ultra
        playerView.setResizeMode(RESIZE_MODES[resizeModeIndex]);
        btnAspectRatio.setText(RESIZE_BTN_LABELS[resizeModeIndex]);

        updateTitleInfo();

        if (mediaItem != null && mediaItem.isTv()) {
            btnEpisodes.setVisibility(View.VISIBLE);
            btnNextEpisode.setVisibility(View.VISIBLE);
        } else {
            btnEpisodes.setVisibility(View.GONE);
            btnNextEpisode.setVisibility(View.GONE);
        }
    }

    private void updateTitleInfo() {
        String mainTitle = mediaItem != null ? mediaItem.getTitle() : "Воспроизведение";
        tvTitle.setText(mainTitle);

        if (mediaItem != null && mediaItem.isTv()) {
            tvSubtitle.setText("Сезон " + season + " • Серия " + episode);
        } else {
            String activeTr = streamSource != null ? streamSource.getActiveTranslationName() : "Дубляж";
            tvSubtitle.setText("Full HD 1080p • " + activeTr);
        }
    }

    private void showBuffering(String message) {
        if (tvBufferingStatus != null) tvBufferingStatus.setText(message);
        if (llPlayerBuffering != null) llPlayerBuffering.setVisibility(View.VISIBLE);
        if (llCenterControls != null) llCenterControls.setVisibility(View.INVISIBLE);
    }

    private void hideBuffering() {
        if (llPlayerBuffering != null) llPlayerBuffering.setVisibility(View.GONE);
        if (llCenterControls != null && !isLocked) llCenterControls.setVisibility(View.VISIBLE);
    }

    private void startStreamResolution() {
        if (streamSource != null && streamSource.getHlsMasterUrl() != null && !streamSource.getHlsMasterUrl().isEmpty()) {
            startExoPlayback(streamSource.getHlsMasterUrl(), streamSource.getReferer());
        } else if (streamSource != null && streamSource.getWebEmbedUrl() != null && !streamSource.getWebEmbedUrl().isEmpty()) {
            showBuffering("Получение прямого потока...");
            streamSniffer.sniff(streamSource.getWebEmbedUrl(), new StreamSniffer.Callback() {
                @Override
                public void onStreamResolved(String streamUrl, String referer) {
                    hideBuffering();
                    streamSource.setHlsMasterUrl(streamUrl);
                    streamSource.setReferer(referer);
                    startExoPlayback(streamUrl, referer);
                }

                @Override
                public void onError(String message) {
                    hideBuffering();
                    // Unified playback with native Samsung One UI controls overlaid
                    initWebPlayerUnified(streamSource.getWebEmbedUrl());
                }
            });
        } else {
            Toast.makeText(this, "Поток недоступен", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startExoPlayback(String streamUrl, String referer) {
        playerView.setVisibility(View.VISIBLE);
        webViewPlayer.setVisibility(View.GONE);
        flFullscreenContainer.setVisibility(View.GONE);
        rlControls.setVisibility(View.VISIBLE);
        gestureOverlay.setVisibility(View.VISIBLE);

        if (player == null) {
            Map<String, String> defaultHeaders = new HashMap<>();
            defaultHeaders.put("Referer", referer != null ? referer : "https://kinogo.inc/");
            defaultHeaders.put("Origin", "https://kinogo.inc");
            defaultHeaders.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(defaultHeaders.get("User-Agent"))
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(defaultHeaders);

            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);

            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                    .build();

            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        showBuffering("Буферизация...");
                    } else {
                        hideBuffering();
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                    if (isPlaying) {
                        scheduleHideControls();
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    hideBuffering();
                    if (streamSource != null && streamSource.getWebEmbedUrl() != null) {
                        initWebPlayerUnified(streamSource.getWebEmbedUrl());
                    } else {
                        Toast.makeText(PlayerActivity.this, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            handler.post(updateProgressRunnable);
        }

        MediaItem item = MediaItem.fromUri(streamUrl);
        player.setMediaItem(item);
        player.setPlaybackSpeed(currentPlaybackSpeed);
        player.prepare();

        // Restore saved position
        if (mediaItem != null) {
            WatchHistoryItem saved = historyStore.getProgress(mediaItem.getId());
            if (saved != null && saved.getPositionMs() > 5000) {
                player.seekTo(saved.getPositionMs());
            }
        }

        player.play();
    }

    private final OkHttpClient webClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();

    private void sendWebPlayerCommand(String jsonCommand) {
        if (webViewPlayer == null) return;
        String js = "(function() {" +
                "  var cmd = " + jsonCommand + ";" +
                "  try { window.postMessage(cmd, '*'); } catch(e){}" +
                "  var iframes = document.querySelectorAll('iframe');" +
                "  for (var i = 0; i < iframes.length; i++) {" +
                "    try { iframes[i].contentWindow.postMessage(cmd, '*'); } catch(e){}" +
                "  }" +
                "})();";
        webViewPlayer.evaluateJavascript(js, null);
    }

    private void initWebPlayerUnified(String embedUrl) {
        if (player != null) {
            player.release();
            player = null;
        }
        playerView.setVisibility(View.GONE);
        webViewPlayer.setVisibility(View.VISIBLE);
        // KEEP Samsung One UI controls & gestures overlay active on top!
        rlControls.setVisibility(View.VISIBLE);
        gestureOverlay.setVisibility(View.VISIBLE);
        hideBuffering();

        WebSettings ws = webViewPlayer.getSettings();
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

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webViewPlayer, true);

        // Add Bridge for time and play state sync to our native Samsung UI
        webViewPlayer.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onWebTimeUpdate(float current, float duration) {
                handler.post(() -> {
                    if (player == null && duration > 0) {
                        long posMs = (long) (current * 1000);
                        long durMs = (long) (duration * 1000);
                        int progress = (int) ((posMs * 1000) / durMs);
                        seekBar.setProgress(progress);
                        tvCurrentTime.setText(formatTime(posMs));
                        tvTotalTime.setText(formatTime(durMs));
                        saveProgressToStore(posMs, durMs);
                    }
                });
            }

            @JavascriptInterface
            public void onWebPlayStateChanged(boolean playing) {
                handler.post(() -> {
                    isWebPlaying = playing;
                    btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
                    if (playing) {
                        hideBuffering();
                        scheduleHideControls();
                    }
                });
            }
        }, "BoomBridge");

        webViewPlayer.setWebChromeClient(new WebChromeClient());
        webViewPlayer.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return super.shouldInterceptRequest(view, request);
                }
                String url = request.getUrl().toString();

                if (isBalancerHtml(url, request)) {
                    WebResourceResponse resp = interceptAndCleanHtml(request, url);
                    if (resp != null) return resp;
                } else if (isBalancerCss(url)) {
                    WebResourceResponse resp = interceptAndAppendCss(request, url);
                    if (resp != null) return resp;
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }
        });

        String finalEmbedUrl = embedUrl;
        if (!finalEmbedUrl.contains("autoplay=")) {
            finalEmbedUrl += (finalEmbedUrl.contains("?") ? "&" : "?") + "autoplay=1";
        }

        String html = "<!DOCTYPE html><html><head>" +
                "<meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                "<style>* { margin:0; padding:0; box-sizing:border-box; } html, body { width:100%; height:100%; background:#000; overflow:hidden; } iframe { width:100%; height:100%; border:none; position:absolute; top:0; left:0; }</style>" +
                "<script>" +
                "window.addEventListener('message', function(ev) {" +
                "  if (!ev || !ev.data) return;" +
                "  if (ev.data.action === 'webTimeUpdate') {" +
                "    if (window.BoomBridge) window.BoomBridge.onWebTimeUpdate(ev.data.current, ev.data.duration);" +
                "  } else if (ev.data.action === 'webPlayState') {" +
                "    if (window.BoomBridge) window.BoomBridge.onWebPlayStateChanged(ev.data.playing);" +
                "  }" +
                "});" +
                "</script>" +
                "</head><body>" +
                "<iframe id=\"playerFrame\" src=\"" + finalEmbedUrl + "\" allowfullscreen=\"true\" allow=\"autoplay *; fullscreen *; encrypted-media *\"></iframe>" +
                "</body></html>";

        webViewPlayer.loadDataWithBaseURL("https://kinogo.inc/", html, "text/html", "UTF-8", null);

        // Restore saved position after load
        if (mediaItem != null) {
            WatchHistoryItem saved = historyStore.getProgress(mediaItem.getId());
            if (saved != null && saved.getPositionMs() > 5000) {
                long posSec = saved.getPositionMs() / 1000;
                handler.postDelayed(() -> {
                    sendWebPlayerCommand("{\"action\":\"seekTime\",\"time\":" + posSec + "}");
                }, 2500);
            }
        }

        handler.post(updateProgressRunnable);
    }

    private boolean isBalancerHtml(String url, WebResourceRequest request) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        boolean isBalancerHost = lower.contains("stravers.live") || lower.contains("alloha") ||
                lower.contains("delivembd") || lower.contains("kinohost") ||
                lower.contains("kinohub") || lower.contains("vidsrc") || lower.contains("/embed");
        if (!isBalancerHost) return false;

        String accept = request.getRequestHeaders() != null ? request.getRequestHeaders().get("Accept") : null;
        boolean acceptsHtml = accept != null && accept.contains("text/html");
        return acceptsHtml || (!lower.contains(".js") && !lower.contains(".css") && !lower.contains(".m3u8") && !lower.contains(".ts") && !lower.contains(".png") && !lower.contains(".jpg") && !lower.contains(".svg"));
    }

    private boolean isBalancerCss(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return (lower.contains("stravers.live") || lower.contains("alloha") || lower.contains("delivembd")) && lower.contains(".css");
    }

    private static String getCleanerCssRules() {
        return ".selects, .select, .ui, [data-select], .select__drop, .select__item, " +
                "[class*=\"allplay\"], [data-allplay], .allplay__video-wrapper > *:not(video), " +
                ".rmp-control-bar, .plyr__controls, .vjs-control-bar, .jw-controls, " +
                "[class*=\"controls\"], [class*=\"control-bar\"], [class*=\"watermark\"], " +
                "[class*=\"logo\"], [class*=\"brand\"], .btn.trailer, .modal, .error_message, " +
                ".rmp-ad-vast-video-player, [data-allplay*=\"ads\"], .time_save, .time_save__btn, " +
                ".time_save__close, [class*=\"time_save\"], [class*=\"modal\"] { " +
                "display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; width: 0 !important; height: 0 !important; } " +
                "html, body { width: 100vw !important; height: 100vh !important; overflow: hidden !important; background: #000 !important; margin: 0 !important; padding: 0 !important; } " +
                "video, .allplay__video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; object-fit: cover !important; z-index: 999999 !important; background: #000 !important; }";
    }

    private static String getBalancerInjectionHead() {
        return "<style id=\"boom-cleaner-style\">" + getCleanerCssRules() + "</style>" +
                "<script id=\"boom-bridge-script\">" +
                "(function() {" +
                "  function findVid() { return document.querySelector('video') || document.querySelector('.allplay__video'); }" +
                "  function bindVid(v) {" +
                "    if (!v || v.__boomInit) return;" +
                "    v.__boomInit = true;" +
                "    v.style.objectFit = 'cover';" +
                "    v.addEventListener('timeupdate', function() {" +
                "      var msg = { action: 'webTimeUpdate', current: v.currentTime, duration: v.duration || 0 };" +
                "      try { window.parent.postMessage(msg, '*'); } catch(e){}" +
                "      try { if (window.BoomBridge && window.BoomBridge.onWebTimeUpdate) window.BoomBridge.onWebTimeUpdate(v.currentTime, v.duration || 0); } catch(e){}" +
                "    });" +
                "    v.addEventListener('play', function() {" +
                "      var msg = { action: 'webPlayState', playing: true };" +
                "      try { window.parent.postMessage(msg, '*'); } catch(e){}" +
                "      try { if (window.BoomBridge && window.BoomBridge.onWebPlayStateChanged) window.BoomBridge.onWebPlayStateChanged(true); } catch(e){}" +
                "    });" +
                "    v.addEventListener('pause', function() {" +
                "      var msg = { action: 'webPlayState', playing: false };" +
                "      try { window.parent.postMessage(msg, '*'); } catch(e){}" +
                "      try { if (window.BoomBridge && window.BoomBridge.onWebPlayStateChanged) window.BoomBridge.onWebPlayStateChanged(false); } catch(e){}" +
                "    });" +
                "    if (v.paused) { v.play().catch(function(){}); }" +
                "  }" +
                "  window.addEventListener('message', function(ev) {" +
                "    if (!ev || !ev.data) return;" +
                "    var cmd = ev.data;" +
                "    var v = findVid();" +
                "    if (!v) return;" +
                "    if (cmd.action === 'play') { v.play().catch(function(){}); }" +
                "    else if (cmd.action === 'pause') { v.pause(); }" +
                "    else if (cmd.action === 'togglePlay') { if (v.paused) v.play().catch(function(){}); else v.pause(); }" +
                "    else if (cmd.action === 'seekPercent') { if (v.duration) v.currentTime = v.duration * cmd.percent; }" +
                "    else if (cmd.action === 'seekDelta') { v.currentTime += cmd.delta; }" +
                "    else if (cmd.action === 'seekTime') { v.currentTime = cmd.time; }" +
                "    else if (cmd.action === 'speed') { v.playbackRate = cmd.rate; }" +
                "    else if (cmd.action === 'aspect') { v.style.objectFit = cmd.mode; }" +
                "  });" +
                "  setInterval(function() {" +
                "    var v = findVid();" +
                "    if (v) bindVid(v);" +
                "    var tsBtn = document.querySelector('.time_save__btn, .time_save button, [class*=\"time_save\"] button, .modal button');" +
                "    if (tsBtn) { try { tsBtn.click(); } catch(e){} }" +
                "    document.querySelectorAll('button, div, span, a').forEach(function(el) {" +
                "      var t = (el.innerText || el.textContent || '').toLowerCase();" +
                "      if (t.indexOf('продолжить') !== -1 || t.indexOf('пропустить') !== -1 || t.indexOf('skip') !== -1) { try { el.click(); } catch(e){} }" +
                "    });" +
                "  }, 250);" +
                "})();" +
                "</script>";
    }

    private WebResourceResponse interceptAndCleanHtml(WebResourceRequest request, String url) {
        try {
            Request.Builder reqBuilder = new Request.Builder().url(url);
            if (request.getRequestHeaders() != null) {
                for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                    reqBuilder.header(entry.getKey(), entry.getValue());
                }
            }
            if (request.getRequestHeaders() == null || !request.getRequestHeaders().containsKey("Referer")) {
                reqBuilder.header("Referer", "https://kinogo.inc/");
            }

            Response resp = webClient.newCall(reqBuilder.build()).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                String originalHtml = resp.body().string();
                originalHtml = originalHtml.replace("saveTime: 1", "saveTime: 0")
                        .replace("autoplay: 0", "autoplay: 1");
                String injection = getBalancerInjectionHead();
                String modifiedHtml;
                if (originalHtml.contains("</head>")) {
                    modifiedHtml = originalHtml.replace("</head>", injection + "</head>");
                } else {
                    modifiedHtml = injection + originalHtml;
                }

                Map<String, String> responseHeaders = new HashMap<>();
                responseHeaders.put("Access-Control-Allow-Origin", "*");
                responseHeaders.put("Access-Control-Allow-Credentials", "true");
                responseHeaders.put("Content-Type", "text/html; charset=utf-8");

                return new WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        200,
                        "OK",
                        responseHeaders,
                        new ByteArrayInputStream(modifiedHtml.getBytes(StandardCharsets.UTF_8))
                );
            }
        } catch (Exception e) {
            android.util.Log.e("BOOM_PLAYER", "HTML interception error: " + e.getMessage());
        }
        return null;
    }

    private WebResourceResponse interceptAndAppendCss(WebResourceRequest request, String url) {
        try {
            Request.Builder reqBuilder = new Request.Builder().url(url);
            if (request.getRequestHeaders() != null) {
                for (Map.Entry<String, String> entry : request.getRequestHeaders().entrySet()) {
                    reqBuilder.header(entry.getKey(), entry.getValue());
                }
            }
            Response resp = webClient.newCall(reqBuilder.build()).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                String originalCss = resp.body().string();
                String extraCss = "\n" + getCleanerCssRules() + "\n";
                String modifiedCss = originalCss + extraCss;

                Map<String, String> responseHeaders = new HashMap<>();
                responseHeaders.put("Access-Control-Allow-Origin", "*");
                responseHeaders.put("Content-Type", "text/css; charset=utf-8");

                return new WebResourceResponse(
                        "text/css",
                        "UTF-8",
                        200,
                        "OK",
                        responseHeaders,
                        new ByteArrayInputStream(modifiedCss.getBytes(StandardCharsets.UTF_8))
                );
            }
        } catch (Exception e) {
            android.util.Log.e("BOOM_PLAYER", "CSS interception error: " + e.getMessage());
        }
        return null;
    }

    private void setupControls() {
        btnBack.setOnClickListener(v -> finish());

        btnPip.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            enterPipMode();
        });

        btnPlayPause.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            } else if (webViewPlayer != null) {
                toggleWebPlay();
            }
            scheduleHideControls();
        });

        btnReplay10.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            seekBy(-10000);
            showSeekBadge(false);
            scheduleHideControls();
        });

        btnForward10.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            seekBy(10000);
            showSeekBadge(true);
            scheduleHideControls();
        });

        btnAspectRatio.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cycleAspectRatio();
            scheduleHideControls();
        });

        btnFullscreen.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cycleAspectRatio();
        });

        btnAudioTrack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showAudioTracksBottomSheet();
        });

        btnEpisodes.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showEpisodesBottomSheet();
        });

        btnNextEpisode.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            playNextEpisode();
        });

        btnSpeed.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showSpeedBottomSheet();
        });

        btnLock.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            lockControls();
        });

        llLockOverlay.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            unlockControls();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (player != null) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            long newPos = (duration * progress) / 1000;
                            player.seekTo(newPos);
                        }
                    } else if (webViewPlayer != null) {
                        seekWebPercent(progress / 1000f);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                scheduleHideControls();
            }
        });
    }

    private void cycleAspectRatio() {
        resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.length;
        playerView.setResizeMode(RESIZE_MODES[resizeModeIndex]);
        btnAspectRatio.setText(RESIZE_BTN_LABELS[resizeModeIndex]);

        String fitVal = (resizeModeIndex == 0) ? "contain" : (resizeModeIndex == 1 ? "cover" : "fill");
        sendWebPlayerCommand("{\"action\":\"aspect\",\"mode\":\"" + fitVal + "\"}");

        Toast.makeText(this, RESIZE_NAMES[resizeModeIndex], Toast.LENGTH_SHORT).show();
    }

    private void toggleWebPlay() {
        if (webViewPlayer == null) return;
        isWebPlaying = !isWebPlaying;
        btnPlayPause.setImageResource(isWebPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        sendWebPlayerCommand("{\"action\":\"togglePlay\"}");
    }

    private void seekWebPercent(float frac) {
        sendWebPlayerCommand("{\"action\":\"seekPercent\",\"percent\":" + frac + "}");
    }

    private void seekWebDelta(float deltaSeconds) {
        sendWebPlayerCommand("{\"action\":\"seekDelta\",\"delta\":" + deltaSeconds + "}");
    }

    private void lockControls() {
        isLocked = true;
        hideControls();
        llLockOverlay.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Экран заблокирован", Toast.LENGTH_SHORT).show();
    }

    private void unlockControls() {
        isLocked = false;
        llLockOverlay.setVisibility(View.GONE);
        showControls();
        Toast.makeText(this, "Экран разблокирован", Toast.LENGTH_SHORT).show();
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Rational rational = new Rational(16, 9);
                if (player != null && player.getVideoSize() != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                    int w = player.getVideoSize().width;
                    int h = player.getVideoSize().height;
                    float ratio = (float) w / h;
                    if (ratio >= 0.42f && ratio <= 2.38f) {
                        rational = new Rational(w, h);
                    }
                }
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                        .setAspectRatio(rational)
                        .build();
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        boolean isPlaying = (player != null && player.isPlaying()) || isWebPlaying;
        if (isPlaying && !isLocked) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            rlControls.setVisibility(View.GONE);
            llBrightnessHud.setVisibility(View.GONE);
            llVolumeHud.setVisibility(View.GONE);
            gestureOverlay.setVisibility(View.GONE);
            if (llLockOverlay != null) llLockOverlay.setVisibility(View.GONE);
            hideBuffering();
        } else {
            gestureOverlay.setVisibility(View.VISIBLE);
            showControls();
        }
    }

    private void showAudioTracksBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_tracks, null);
        dialog.setContentView(view);

        TextView tvHeading = view.findViewById(R.id.tvTracksHeading);
        TextView tvSubheading = view.findViewById(R.id.tvTracksSubheading);
        LinearLayout container = view.findViewById(R.id.llTracksContainer);

        tvHeading.setText("Озвучка и перевод");
        tvSubheading.setText("Выберите предпочтительный вариант:");

        if (streamSource != null && !streamSource.getAlternativeEmbeds().isEmpty()) {
            for (StreamSource.EmbedOption option : streamSource.getAlternativeEmbeds()) {
                TextView optView = new TextView(this);
                optView.setText("💬  " + option.getName());
                optView.setTextSize(15f);
                boolean isActive = option.getName().equals(streamSource.getActiveTranslationName());
                optView.setTextColor(getColor(isActive ? R.color.accent_red : R.color.text_primary));
                optView.setTypeface(null, isActive ? Typeface.BOLD : Typeface.NORMAL);
                optView.setPadding(0, 24, 0, 24);

                optView.setOnClickListener(v -> {
                    dialog.dismiss();
                    streamSource.setActiveTranslationName(option.getName());
                    updateTitleInfo();
                    long currentPos = player != null ? player.getCurrentPosition() : (long)(seekBar.getProgress() / 1000f * (player != null ? player.getDuration() : 1));
                    showBuffering("Загрузка: " + option.getName() + "...");

                    streamSniffer.sniff(option.getUrl(), new StreamSniffer.Callback() {
                        @Override
                        public void onStreamResolved(String streamUrl, String referer) {
                            hideBuffering();
                            streamSource.setHlsMasterUrl(streamUrl);
                            streamSource.setReferer(referer);
                            startExoPlayback(streamUrl, referer);
                            if (player != null && currentPos > 0) {
                                player.seekTo(currentPos);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            hideBuffering();
                            initWebPlayerUnified(option.getUrl());
                        }
                    });
                });
                container.addView(optView);
            }
        }

        if (player != null) {
            Tracks tracks = player.getCurrentTracks();
            int trackNum = 1;
            for (Tracks.Group group : tracks.getGroups()) {
                if (group.getType() == C.TRACK_TYPE_AUDIO) {
                    TrackGroup tg = group.getMediaTrackGroup();
                    for (int i = 0; i < tg.length; i++) {
                        final int trackIdx = i;
                        final TrackGroup finalTg = tg;
                        String rawLabel = tg.getFormat(i).label;
                        if (rawLabel == null || rawLabel.isEmpty()) {
                            String lang = tg.getFormat(i).language;
                            rawLabel = "Дорожка " + trackNum + (lang != null ? " (" + lang + ")" : "");
                        }
                        final String trackLabel = rawLabel;

                        TextView trackView = new TextView(this);
                        trackView.setText("🔊  " + trackLabel);
                        trackView.setTextSize(15f);
                        boolean isSelected = group.isTrackSelected(i);
                        trackView.setTextColor(getColor(isSelected ? R.color.accent_red : R.color.text_primary));
                        trackView.setPadding(0, 24, 0, 24);

                        trackView.setOnClickListener(tv -> {
                            TrackSelectionParameters params = player.getTrackSelectionParameters()
                                    .buildUpon()
                                    .setOverrideForType(new TrackSelectionOverride(finalTg, trackIdx))
                                    .build();
                            player.setTrackSelectionParameters(params);
                            Toast.makeText(this, "Выбрано: " + trackLabel, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                        container.addView(trackView);
                        trackNum++;
                    }
                }
            }
        }

        if (container.getChildCount() == 0) {
            TextView noneView = new TextView(this);
            noneView.setText("Доступен только один основной перевод");
            noneView.setTextColor(getColor(R.color.text_muted));
            container.addView(noneView);
        }

        dialog.show();
    }

    private void showEpisodesBottomSheet() {
        if (mediaItem == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_tracks, null);
        dialog.setContentView(view);

        TextView tvHeading = view.findViewById(R.id.tvTracksHeading);
        TextView tvSubheading = view.findViewById(R.id.tvTracksSubheading);
        LinearLayout container = view.findViewById(R.id.llTracksContainer);

        tvHeading.setText("Выбор серии");
        tvSubheading.setText("Текущий сезон: " + season);

        for (int ep = 1; ep <= 24; ep++) {
            final int targetEp = ep;
            TextView epView = new TextView(this);
            epView.setText("Серия " + ep + (ep == episode ? "  ✓ (Текущая)" : ""));
            epView.setTextSize(15f);
            epView.setTextColor(getColor(ep == episode ? R.color.accent_red : R.color.text_primary));
            epView.setTypeface(null, ep == episode ? Typeface.BOLD : Typeface.NORMAL);
            epView.setPadding(0, 22, 0, 22);

            epView.setOnClickListener(v -> {
                dialog.dismiss();
                if (targetEp == episode) return;
                episode = targetEp;
                updateTitleInfo();
                loadEpisodeStream();
            });
            container.addView(epView);
        }

        dialog.show();
    }

    private void playNextEpisode() {
        episode++;
        updateTitleInfo();
        Toast.makeText(this, "Запуск серии " + episode + "...", Toast.LENGTH_SHORT).show();
        loadEpisodeStream();
    }

    private void loadEpisodeStream() {
        showBuffering("Поиск серии " + episode + "...");
        collapsScraper.resolveStream(mediaItem, season, episode, new CollapsScraper.Callback() {
            @Override
            public void onSuccess(StreamSource newSource) {
                streamSource = newSource;
                startStreamResolution();
            }

            @Override
            public void onError(Exception e) {
                hideBuffering();
                Toast.makeText(PlayerActivity.this, "Ошибка загрузки серии", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSpeedBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_tracks, null);
        dialog.setContentView(view);

        TextView tvHeading = view.findViewById(R.id.tvTracksHeading);
        TextView tvSubheading = view.findViewById(R.id.tvTracksSubheading);
        LinearLayout container = view.findViewById(R.id.llTracksContainer);

        tvHeading.setText("Скорость воспроизведения");
        tvSubheading.setText("Выберите темп видео:");

        float[] speeds = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        String[] labels = {"0.5x (Замедленно)", "0.75x", "1.0x (Обычная)", "1.25x", "1.5x", "2.0x (Быстро)"};

        for (int i = 0; i < speeds.length; i++) {
            final float speed = speeds[i];
            String label = labels[i];

            TextView sView = new TextView(this);
            sView.setText(label);
            sView.setTextSize(15f);
            boolean isSelected = (Math.abs(currentPlaybackSpeed - speed) < 0.05f);
            sView.setTextColor(getColor(isSelected ? R.color.accent_red : R.color.text_primary));
            sView.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            sView.setPadding(0, 24, 0, 24);

            sView.setOnClickListener(v -> {
                currentPlaybackSpeed = speed;
                if (player != null) {
                    player.setPlaybackSpeed(speed);
                } else if (webViewPlayer != null) {
                    sendWebPlayerCommand("{\"action\":\"speed\",\"rate\":" + speed + "}");
                }
                btnSpeed.setText(speed == 1.0f ? "1.0x" : (speed + "x"));
                dialog.dismiss();
            });
            container.addView(sView);
        }

        dialog.show();
    }

    private void setupGestures() {
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isLocked) {
                    llLockOverlay.setVisibility(View.VISIBLE);
                    handler.postDelayed(() -> {
                        if (isLocked) llLockOverlay.setVisibility(View.GONE);
                    }, 2500);
                    return true;
                }
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isLocked) return true;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (e.getX() < screenWidth / 2f) {
                    seekBy(-10000);
                    showSeekBadge(false);
                } else {
                    seekBy(10000);
                    showSeekBadge(true);
                }
                gestureOverlay.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (isLocked || e1 == null || e2 == null) return false;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                float deltaY = distanceY;

                if (e1.getX() < screenWidth * 0.45f) {
                    adjustBrightness(deltaY);
                } else if (e1.getX() > screenWidth * 0.55f) {
                    adjustVolume(deltaY);
                }
                return true;
            }
        });

        gestureOverlay.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                llBrightnessHud.setVisibility(View.GONE);
                llVolumeHud.setVisibility(View.GONE);
            }
            return true;
        });
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isLocked) {
                    unlockControls();
                    return;
                }
                finish();
            }
        });
    }

    private void adjustBrightness(float deltaY) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float current = lp.screenBrightness;
        if (current < 0) current = 0.5f;
        current += (deltaY / 1500f);
        current = Math.max(0.01f, Math.min(1.0f, current));
        lp.screenBrightness = current;
        getWindow().setAttributes(lp);

        int percent = (int) (current * 100);
        tvBrightnessValue.setText(percent + "%");
        llBrightnessHud.setVisibility(View.VISIBLE);
    }

    private void adjustVolume(float deltaY) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int change = (deltaY > 0) ? 1 : -1;
        int next = Math.max(0, Math.min(max, current + change));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);

        int percent = (int) ((next * 100f) / max);
        tvVolumeValue.setText(percent + "%");
        llVolumeHud.setVisibility(View.VISIBLE);
    }

    private void showSeekBadge(boolean isForward) {
        TextView badge = isForward ? tvSeekRightBadge : tvSeekLeftBadge;
        badge.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> badge.setVisibility(View.GONE), 800);
    }

    private void seekBy(long deltaMs) {
        if (player != null) {
            long pos = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + deltaMs));
            player.seekTo(pos);
        } else if (webViewPlayer != null) {
            seekWebDelta(deltaMs / 1000f);
        }
    }

    private void toggleControls() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        if (isLocked) return;
        rlControls.setVisibility(View.VISIBLE);
        isControlsVisible = true;
        scheduleHideControls();
    }

    private void hideControls() {
        rlControls.setVisibility(View.GONE);
        isControlsVisible = false;
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 4500);
    }

    private void updateProgress() {
        if (player != null) {
            long pos = player.getCurrentPosition();
            long dur = player.getDuration();

            if (dur > 0) {
                int progress = (int) ((pos * 1000) / dur);
                seekBar.setProgress(progress);
                tvCurrentTime.setText(formatTime(pos));
                tvTotalTime.setText(formatTime(dur));

                saveProgressToStore(pos, dur);
            }
        }
    }

    private void saveProgressToStore(long pos, long dur) {
        if (mediaItem == null) return;
        WatchHistoryItem item = new WatchHistoryItem();
        item.setMediaId(mediaItem.getId());
        item.setTitle(mediaItem.getTitle());
        item.setPosterUrl(mediaItem.getFullPosterUrl());
        item.setMediaType(mediaItem.getMediaType());
        item.setPositionMs(pos);
        item.setDurationMs(dur);
        item.setSeason(season);
        item.setEpisode(episode);
        historyStore.saveProgress(item);
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hour = totalSec / 3600;

        if (hour > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hour, min, sec);
        } else {
            return String.format(Locale.US, "%02d:%02d", min, sec);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isInPictureInPictureMode()) {
            player.pause();
            updateProgress();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateProgressRunnable);
        handler.removeCallbacks(hideControlsRunnable);
        if (streamSniffer != null) {
            streamSniffer.destroy();
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (webViewPlayer != null) {
            webViewPlayer.destroy();
        }
    }
}
