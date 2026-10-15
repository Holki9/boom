package com.holki.boom.ui;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
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
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.holki.boom.R;
import com.holki.boom.data.WatchHistoryStore;
import com.holki.boom.model.StreamSource;
import com.holki.boom.model.WatchHistoryItem;

import java.util.Locale;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webViewPlayer;
    private FrameLayout flFullscreenContainer;
    private RelativeLayout rlControls;
    private View gestureOverlay;

    private TextView tvTitle, tvCurrentTime, tvTotalTime, btnExoSource, btnFloatingSource;
    private ImageView btnBack, btnFloatingBack, btnPlayPause, btnReplay10, btnForward10, btnAudioTrack, btnAspectRatio, btnFullscreen;
    private SeekBar seekBar;
    private ProgressBar pbBuffering;

    // Gesture HUDs
    private LinearLayout llBrightnessHud, llVolumeHud;
    private TextView tvBrightnessValue, tvVolumeValue;
    private TextView tvSeekLeftBadge, tvSeekRightBadge;

    private StreamSource streamSource;
    private com.holki.boom.model.MediaItem mediaItem;
    private int season = 1;
    private int episode = 1;

    private WatchHistoryStore historyStore;
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customView;

    private boolean isControlsVisible = true;
    private final Runnable hideControlsRunnable = this::hideControls;
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 1000);
        }
    };

    private int resizeModeIndex = 0; // 0=fit, 1=zoom (20:9 fill), 2=fill
    private static final int[] RESIZE_MODES = {
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive fullscreen
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

        initViews();
        setupControls();
        setupGestures();
        setupBackHandling();

        if (streamSource != null && streamSource.getHlsMasterUrl() != null && !streamSource.getHlsMasterUrl().isEmpty()) {
            initExoPlayer(streamSource.getHlsMasterUrl());
        } else if (streamSource != null && streamSource.getWebEmbedUrl() != null && !streamSource.getWebEmbedUrl().isEmpty()) {
            initWebView(streamSource.getWebEmbedUrl());
        } else {
            Toast.makeText(this, "Поток недоступен", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        webViewPlayer = findViewById(R.id.webViewPlayer);
        flFullscreenContainer = findViewById(R.id.flFullscreenContainer);
        rlControls = findViewById(R.id.rlPlayerControls);
        gestureOverlay = findViewById(R.id.viewGestureOverlay);

        tvTitle = findViewById(R.id.tvPlayerTitle);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBar = findViewById(R.id.sbPlayer);
        pbBuffering = findViewById(R.id.pbPlayerBuffering);

        btnBack = findViewById(R.id.btnPlayerBack);
        btnFloatingBack = findViewById(R.id.btnFloatingBack);
        btnFloatingSource = findViewById(R.id.btnFloatingSource);
        btnExoSource = findViewById(R.id.btnExoSource);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnReplay10 = findViewById(R.id.btnReplay10);
        btnForward10 = findViewById(R.id.btnForward10);
        btnAudioTrack = findViewById(R.id.btnAudioTrack);
        btnAspectRatio = findViewById(R.id.btnAspectRatio);
        btnFullscreen = findViewById(R.id.btnPlayerFullscreen);

        llBrightnessHud = findViewById(R.id.llBrightnessHud);
        llVolumeHud = findViewById(R.id.llVolumeHud);
        tvBrightnessValue = findViewById(R.id.tvBrightnessValue);
        tvVolumeValue = findViewById(R.id.tvVolumeValue);
        tvSeekLeftBadge = findViewById(R.id.tvSeekLeftBadge);
        tvSeekRightBadge = findViewById(R.id.tvSeekRightBadge);

        String title = mediaItem != null ? mediaItem.getTitle() : "Воспроизведение";
        if (mediaItem != null && mediaItem.isTv()) {
            title += " (Сезон " + season + ", Серия " + episode + ")";
        }
        tvTitle.setText(title);
    }

    private void setWebFloatingButtonsVisibility(int visibility) {
        if (btnFloatingBack != null) btnFloatingBack.setVisibility(visibility);
        if (btnFloatingSource != null) btnFloatingSource.setVisibility(visibility);
    }

    private void initExoPlayer(String streamUrl) {
        playerView.setVisibility(View.VISIBLE);
        webViewPlayer.setVisibility(View.GONE);
        setWebFloatingButtonsVisibility(View.GONE);
        rlControls.setVisibility(View.VISIBLE);
        gestureOverlay.setVisibility(View.VISIBLE);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem item = MediaItem.fromUri(streamUrl);
        player.setMediaItem(item);
        player.prepare();

        // Restore saved position
        if (mediaItem != null) {
            WatchHistoryItem saved = historyStore.getProgress(mediaItem.getId());
            if (saved != null && saved.getPositionMs() > 5000) {
                player.seekTo(saved.getPositionMs());
            }
        }

        player.play();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    pbBuffering.setVisibility(View.VISIBLE);
                } else {
                    pbBuffering.setVisibility(View.GONE);
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
                // Fallback to web embed if HLS stream error occurs
                if (streamSource != null && streamSource.getWebEmbedUrl() != null) {
                    initWebView(streamSource.getWebEmbedUrl());
                } else {
                    Toast.makeText(PlayerActivity.this, "Ошибка прямого потока, выберите источник", Toast.LENGTH_SHORT).show();
                    showSourceSelectorDialog();
                }
            }
        });

        handler.post(updateProgressRunnable);
    }

    private void initWebView(String embedUrl) {
        if (player != null) {
            player.release();
            player = null;
        }
        playerView.setVisibility(View.GONE);
        rlControls.setVisibility(View.GONE);
        gestureOverlay.setVisibility(View.GONE);
        setWebFloatingButtonsVisibility(View.VISIBLE);
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
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webViewPlayer, true);

        webViewPlayer.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                flFullscreenContainer.addView(customView);
                flFullscreenContainer.setVisibility(View.VISIBLE);
                webViewPlayer.setVisibility(View.GONE);
                setWebFloatingButtonsVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                flFullscreenContainer.removeView(customView);
                flFullscreenContainer.setVisibility(View.GONE);
                webViewPlayer.setVisibility(View.VISIBLE);
                setWebFloatingButtonsVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
                customView = null;
                customViewCallback = null;
            }
        });

        webViewPlayer.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("intent:") || url.startsWith("market:") || url.startsWith("tg:") || url.startsWith("viber:") || url.startsWith("whatsapp:")) {
                    return true;
                }
                return false;
            }
        });

        // Wrap embed inside full-screen HTML iframe with proper referrer
        String html = "<!DOCTYPE html><html><head>" +
                "<meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">" +
                "<style>" +
                "* { margin: 0; padding: 0; box-sizing: border-box; }" +
                "html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }" +
                "iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }" +
                "</style>" +
                "</head><body>" +
                "<iframe src=\"" + embedUrl + "\" allowfullscreen=\"true\" allow=\"autoplay *; fullscreen *; encrypted-media *; picture-in-picture *\"></iframe>" +
                "</body></html>";

        webViewPlayer.loadDataWithBaseURL("https://kinogo.inc/", html, "text/html", "UTF-8", null);
    }

    private void setupControls() {
        btnBack.setOnClickListener(v -> finish());
        btnFloatingBack.setOnClickListener(v -> finish());

        btnFloatingSource.setOnClickListener(v -> showSourceSelectorDialog());
        btnExoSource.setOnClickListener(v -> showSourceSelectorDialog());

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
            String[] names = {"Оригинал (Fit)", "Заполнение 20:9 (Zoom)", "Растянуть (Fill)"};
            Toast.makeText(this, names[resizeModeIndex], Toast.LENGTH_SHORT).show();
            scheduleHideControls();
        });

        btnAudioTrack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showAudioTracksDialog();
        });

        btnFullscreen.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.length;
            playerView.setResizeMode(RESIZE_MODES[resizeModeIndex]);
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

    private void showSourceSelectorDialog() {
        if (streamSource == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_tracks, null);
        dialog.setContentView(view);

        TextView tvHeading = view.findViewById(R.id.tvTracksHeading);
        if (tvHeading != null) {
            tvHeading.setText("Выберите источник / плеер");
        }

        LinearLayout container = view.findViewById(R.id.llTracksContainer);

        if (streamSource.getAlternativeEmbeds().isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Доступен только один основной источник");
            emptyView.setTextColor(getColor(R.color.text_muted));
            container.addView(emptyView);
        } else {
            for (StreamSource.EmbedOption option : streamSource.getAlternativeEmbeds()) {
                TextView optView = new TextView(this);
                optView.setText("⚡  " + option.getName());
                optView.setTextSize(15f);
                optView.setTextColor(getColor(R.color.text_primary));
                optView.setPadding(0, 24, 0, 24);
                optView.setOnClickListener(v -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Загрузка: " + option.getName(), Toast.LENGTH_SHORT).show();
                    initWebView(option.getUrl());
                });
                container.addView(optView);
            }
        }

        dialog.show();
    }

    private void setupGestures() {
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
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
                if (e1 == null || e2 == null) return false;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                float deltaY = distanceY; // scrolling up = positive

                if (e1.getX() < screenWidth * 0.45f) {
                    // Left half: Brightness
                    adjustBrightness(deltaY);
                } else if (e1.getX() > screenWidth * 0.55f) {
                    // Right half: Volume
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
                if (customView != null) {
                    if (webViewPlayer != null && webViewPlayer.getWebChromeClient() != null) {
                        webViewPlayer.getWebChromeClient().onHideCustomView();
                    }
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
        handler.postDelayed(hideControlsRunnable, 4000);
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

            // Save watch progress
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

    private void showAudioTracksDialog() {
        if (player == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_tracks, null);
        dialog.setContentView(view);

        LinearLayout container = view.findViewById(R.id.llTracksContainer);

        Tracks tracks = player.getCurrentTracks();
        int trackNum = 1;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                TrackGroup tg = group.getMediaTrackGroup();
                for (int i = 0; i < tg.length; i++) {
                    final int trackIdx = i;
                    final TrackGroup finalTg = tg;
                    String label = tg.getFormat(i).label;
                    if (label == null || label.isEmpty()) {
                        String lang = tg.getFormat(i).language;
                        label = "Дорожка " + trackNum + (lang != null ? " (" + lang + ")" : "");
                    }

                    // Map with Scraper audio names if available
                    if (streamSource != null && streamSource.getAudioTracks().size() > (trackNum - 1)) {
                        label = streamSource.getAudioTracks().get(trackNum - 1).getName();
                    }

                    TextView trackView = new TextView(this);
                    trackView.setText(label);
                    trackView.setTextSize(15f);
                    trackView.setTextColor(getColor(group.isTrackSelected(i) ? R.color.accent_red : R.color.text_primary));
                    trackView.setPadding(0, 24, 0, 24);
                    trackView.setOnClickListener(tv -> {
                        TrackSelectionParameters params = player.getTrackSelectionParameters()
                                .buildUpon()
                                .setOverrideForType(new TrackSelectionOverride(finalTg, trackIdx))
                                .build();
                        player.setTrackSelectionParameters(params);
                        Toast.makeText(this, "Выбрано: " + trackView.getText(), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                    container.addView(trackView);
                    trackNum++;
                }
            }
        }

        if (trackNum == 1) {
            TextView noneView = new TextView(this);
            noneView.setText("Доступна только основная дорожка");
            noneView.setTextColor(getColor(R.color.text_muted));
            container.addView(noneView);
        }

        dialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
            updateProgress();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateProgressRunnable);
        handler.removeCallbacks(hideControlsRunnable);
        if (player != null) {
            player.release();
            player = null;
        }
        if (webViewPlayer != null) {
            webViewPlayer.destroy();
        }
    }
}
