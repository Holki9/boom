package com.holki.boom.ui;

import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
import androidx.core.widget.NestedScrollView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
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
import androidx.media3.exoplayer.source.MediaSource;
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
import java.util.List;
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
    }

    private void hideBuffering() {
        if (llPlayerBuffering != null) llPlayerBuffering.setVisibility(View.GONE);
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
                    // Fallback to web view if sniffing fails
                    Toast.makeText(PlayerActivity.this, "Прямой поток не ответил, запуск резервного плеера", Toast.LENGTH_SHORT).show();
                    initWebViewFallback(streamSource.getWebEmbedUrl());
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
                        Toast.makeText(PlayerActivity.this, "Ошибка потока, переход в веб-режим", Toast.LENGTH_SHORT).show();
                        initWebViewFallback(streamSource.getWebEmbedUrl());
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

    private void initWebViewFallback(String embedUrl) {
        if (player != null) {
            player.release();
            player = null;
        }
        playerView.setVisibility(View.GONE);
        rlControls.setVisibility(View.GONE);
        gestureOverlay.setVisibility(View.GONE);
        webViewPlayer.setVisibility(View.VISIBLE);

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

        webViewPlayer.setWebChromeClient(new WebChromeClient());
        webViewPlayer.setWebViewClient(new WebViewClient());

        String html = "<!DOCTYPE html><html><head>" +
                "<meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                "<style>* { margin:0; padding:0; box-sizing:border-box; } html, body { width:100%; height:100%; background:#000; overflow:hidden; } iframe { width:100%; height:100%; border:none; }</style>" +
                "</head><body>" +
                "<iframe src=\"" + embedUrl + "\" allowfullscreen=\"true\" allow=\"autoplay *; fullscreen *; encrypted-media *\"></iframe>" +
                "</body></html>";

        webViewPlayer.loadDataWithBaseURL("https://kinogo.inc/", html, "text/html", "UTF-8", null);
    }

    private void setupControls() {
        btnBack.setOnClickListener(v -> finish());

        btnPip.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            enterPipMode();
        });

        btnPlayPause.setOnClickListener(v -> {
            if (player == null) return;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            scheduleHideControls();
        });

        btnReplay10.setOnClickListener(v -> {
            if (player == null) return;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            seekBy(-10000);
            showSeekBadge(false);
            scheduleHideControls();
        });

        btnForward10.setOnClickListener(v -> {
            if (player == null) return;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            seekBy(10000);
            showSeekBadge(true);
            scheduleHideControls();
        });

        btnAspectRatio.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.length;
            playerView.setResizeMode(RESIZE_MODES[resizeModeIndex]);
            btnAspectRatio.setText(RESIZE_BTN_LABELS[resizeModeIndex]);
            Toast.makeText(this, RESIZE_NAMES[resizeModeIndex], Toast.LENGTH_SHORT).show();
            scheduleHideControls();
        });

        btnFullscreen.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.length;
            playerView.setResizeMode(RESIZE_MODES[resizeModeIndex]);
            btnAspectRatio.setText(RESIZE_BTN_LABELS[resizeModeIndex]);
            Toast.makeText(this, RESIZE_NAMES[resizeModeIndex], Toast.LENGTH_SHORT).show();
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
                if (fromUser && player != null) {
                    long duration = player.getDuration();
                    if (duration > 0) {
                        long newPos = (duration * progress) / 1000;
                        player.seekTo(newPos);
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
        if (player != null && player.isPlaying() && !isLocked) {
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

        // Alternative Embeds (Alloha Translations & Balancers)
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
                    long currentPos = player != null ? player.getCurrentPosition() : 0;
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
                            Toast.makeText(PlayerActivity.this, "Ошибка прямого потока, запуск плеера", Toast.LENGTH_SHORT).show();
                            initWebViewFallback(option.getUrl());
                        }
                    });
                });
                container.addView(optView);
            }
        }

        // HLS internal audio tracks
        if (player != null) {
            Tracks tracks = player.getCurrentTracks();
            int trackNum = 1;
            for (Tracks.Group group : tracks.getGroups()) {
                if (group.getType() == C.TRACK_TYPE_AUDIO) {
                    TrackGroup tg = group.getMediaTrackGroup();
                    for (int i = 0; i < tg.length; i++) {
                        final int trackIdx = i;
                        final TrackGroup finalTg = tg;
                        String label = tg.getFormat(i).label;
                        if (label == null || label.isEmpty()) {
                            String lang = tg.getFormat(i).language;
                            label = "Дорожка " + trackNum + (lang != null ? " (" + lang + ")" : "");
                        }

                        TextView trackView = new TextView(this);
                        trackView.setText("🔊  " + label);
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
                            Toast.makeText(this, "Выбрано: " + label, Toast.LENGTH_SHORT).show();
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
        if (player == null) return;
        long pos = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + deltaMs));
        player.seekTo(pos);
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
        if (player == null) return;
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
