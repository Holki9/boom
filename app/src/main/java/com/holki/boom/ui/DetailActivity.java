package com.holki.boom.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.holki.boom.R;
import com.holki.boom.api.CollapsScraper;
import com.holki.boom.api.RutorScraper;
import com.holki.boom.api.TmdbService;
import com.holki.boom.data.WatchHistoryStore;
import com.holki.boom.model.MediaItem;
import com.holki.boom.model.StreamSource;
import com.holki.boom.model.TorrentItem;
import com.holki.boom.ui.adapter.EpisodeAdapter;
import com.holki.boom.ui.adapter.TorrentAdapter;

import java.util.List;

public class DetailActivity extends AppCompatActivity {
    private MediaItem media;
    private final TmdbService tmdbService = new TmdbService();
    private final CollapsScraper collapsScraper = new CollapsScraper();
    private final RutorScraper rutorScraper = new RutorScraper();
    private WatchHistoryStore historyStore;

    private ImageView ivBackdrop;
    private ImageView btnBookmark;
    private TextView tvTitle, tvOriginalTitle, tvYear, tvRating, tvGenres, tvOverview;
    private Button btnWatchOnline, btnTorrents4K;
    private LinearLayout llSeasonsContainer;
    private RecyclerView rvEpisodes;
    private View flLoading;

    private int currentSeason = 1;
    private int currentEpisode = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        media = (MediaItem) getIntent().getSerializableExtra("media_item");
        if (media == null) {
            finish();
            return;
        }

        historyStore = new WatchHistoryStore(this);

        initViews();
        bindData();
        loadFullDetails();
    }

    private void initViews() {
        ivBackdrop = findViewById(R.id.ivDetailBackdrop);
        btnBookmark = findViewById(R.id.btnDetailBookmark);
        tvTitle = findViewById(R.id.tvDetailTitle);
        tvOriginalTitle = findViewById(R.id.tvDetailOriginalTitle);
        tvYear = findViewById(R.id.tvDetailYear);
        tvRating = findViewById(R.id.tvDetailRating);
        tvGenres = findViewById(R.id.tvDetailGenres);
        tvOverview = findViewById(R.id.tvDetailOverview);
        btnWatchOnline = findViewById(R.id.btnWatchOnline);
        btnTorrents4K = findViewById(R.id.btnTorrents4K);
        llSeasonsContainer = findViewById(R.id.llSeasonsContainer);
        rvEpisodes = findViewById(R.id.rvEpisodes);
        flLoading = findViewById(R.id.flDetailLoading);

        findViewById(R.id.btnDetailBack).setOnClickListener(v -> finish());

        updateBookmarkIcon();
        btnBookmark.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            historyStore.toggleBookmark(media);
            updateBookmarkIcon();
            String msg = historyStore.isBookmarked(media.getId()) ? "Добавлено в закладки" : "Удалено из закладок";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        btnWatchOnline.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            playOnlineStream();
        });

        btnTorrents4K.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showTorrentsDialog();
        });
    }

    private void updateBookmarkIcon() {
        boolean isBookmarked = historyStore.isBookmarked(media.getId());
        btnBookmark.setImageResource(isBookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
    }

    private void bindData() {
        tvTitle.setText(media.getTitle());
        String orig = media.getOriginalTitle();
        tvOriginalTitle.setText(orig != null && !orig.isEmpty() ? orig : "");
        tvYear.setText(media.getYear());
        tvRating.setText(media.getFormattedRating());
        tvOverview.setText(media.getOverview());

        String backdrop = media.getFullBackdropUrl();
        if (backdrop != null) {
            Glide.with(this)
                    .load(backdrop)
                    .centerCrop()
                    .placeholder(R.color.card_dark)
                    .into(ivBackdrop);
        }

        if (media.isTv()) {
            llSeasonsContainer.setVisibility(View.VISIBLE);
            rvEpisodes.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            EpisodeAdapter episodeAdapter = new EpisodeAdapter(this, 16, ep -> {
                currentEpisode = ep;
                Toast.makeText(this, "Запуск серии " + ep + "...", Toast.LENGTH_SHORT).show();
                playOnlineStream();
            });
            rvEpisodes.setAdapter(episodeAdapter);
        } else {
            llSeasonsContainer.setVisibility(View.GONE);
        }
    }

    private void loadFullDetails() {
        tmdbService.fetchDetails(media, new TmdbService.Callback<>() {
            @Override
            public void onSuccess(MediaItem result) {
                media = result;
                if (media.getGenres() != null && !media.getGenres().isEmpty()) {
                    tvGenres.setText(String.join(" • ", media.getGenres()));
                }
                tvOverview.setText(media.getOverview());
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void playOnlineStream() {
        flLoading.setVisibility(View.VISIBLE);
        collapsScraper.resolveStream(media, currentSeason, currentEpisode, new CollapsScraper.Callback() {
            @Override
            public void onSuccess(StreamSource streamSource) {
                flLoading.setVisibility(View.GONE);
                Intent intent = new Intent(DetailActivity.this, PlayerActivity.class);
                intent.putExtra("stream_source", streamSource);
                intent.putExtra("media_item", media);
                intent.putExtra("season", currentSeason);
                intent.putExtra("episode", currentEpisode);
                startActivity(intent);
            }

            @Override
            public void onError(Exception e) {
                flLoading.setVisibility(View.GONE);
                Toast.makeText(DetailActivity.this, "Ошибка получения потока", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showTorrentsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_torrents, null);
        dialog.setContentView(view);

        ProgressBar pb = view.findViewById(R.id.pbTorrents);
        TextView tvStatus = view.findViewById(R.id.tvTorrentStatus);
        RecyclerView rv = view.findViewById(R.id.rvTorrents);
        view.findViewById(R.id.btnDialogClose).setOnClickListener(v -> dialog.dismiss());

        rv.setLayoutManager(new LinearLayoutManager(this));
        TorrentAdapter adapter = new TorrentAdapter(this);
        rv.setAdapter(adapter);

        pb.setVisibility(View.VISIBLE);
        tvStatus.setText("Поиск раздач 4K UHD и 1080p...");

        rutorScraper.searchTorrents(media.getTitle(), media.getYear(), new RutorScraper.Callback() {
            @Override
            public void onSuccess(List<TorrentItem> torrents) {
                pb.setVisibility(View.GONE);
                if (torrents.isEmpty()) {
                    tvStatus.setText("Раздачи не найдены");
                } else {
                    tvStatus.setText("Найдено раздач: " + torrents.size() + " (включая 4K и Дубляж)");
                    rv.setVisibility(View.VISIBLE);
                    adapter.setItems(torrents);
                }
            }

            @Override
            public void onError(Exception e) {
                pb.setVisibility(View.GONE);
                tvStatus.setText("Ошибка подключения к трекеру");
            }
        });

        dialog.show();
    }
}
