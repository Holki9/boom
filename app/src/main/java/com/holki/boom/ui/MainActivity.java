package com.holki.boom.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.holki.boom.R;
import com.holki.boom.api.TmdbService;
import com.holki.boom.data.WatchHistoryStore;
import com.holki.boom.model.MediaItem;
import com.holki.boom.model.WatchHistoryItem;
import com.holki.boom.ui.adapter.HistoryAdapter;
import com.holki.boom.ui.adapter.MediaCardAdapter;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private final TmdbService tmdbService = new TmdbService();
    private WatchHistoryStore historyStore;

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llHomeContent;
    private RecyclerView rvCategoryGrid;

    // Category Tabs
    private TextView tabAll, tabMovies, tabSeries, tabCartoons, tabAnime;

    // Hero Banner
    private ImageView ivHeroBackdrop;
    private TextView tvHeroRating, tvHeroYear, tvHeroTitle;
    private Button btnHeroPlay, btnHeroInfo;
    private MediaItem heroMedia;

    // Carousels
    private LinearLayout llContinueWatching;
    private RecyclerView rvContinueWatching;
    private RecyclerView rvTrending, rvPopularMovies, rvPopularSeries, rvCartoons, rvAnime;

    private HistoryAdapter historyAdapter;
    private MediaCardAdapter trendingAdapter;
    private MediaCardAdapter popularMoviesAdapter;
    private MediaCardAdapter popularSeriesAdapter;
    private MediaCardAdapter cartoonsAdapter;
    private MediaCardAdapter animeAdapter;
    private MediaCardAdapter gridAdapter;

    private int activeTab = 0; // 0=All, 1=Movies, 2=Series, 3=Cartoons, 4=Anime

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        historyStore = new WatchHistoryStore(this);

        initViews();
        setupAdapters();
        setupListeners();
        loadAllData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.accent_red);

        llHomeContent = findViewById(R.id.llHomeContent);
        rvCategoryGrid = findViewById(R.id.rvCategoryGrid);
        rvCategoryGrid.setLayoutManager(new GridLayoutManager(this, 3));

        tabAll = findViewById(R.id.tabAll);
        tabMovies = findViewById(R.id.tabMovies);
        tabSeries = findViewById(R.id.tabSeries);
        tabCartoons = findViewById(R.id.tabCartoons);
        tabAnime = findViewById(R.id.tabAnime);

        ivHeroBackdrop = findViewById(R.id.ivHeroBackdrop);
        tvHeroRating = findViewById(R.id.tvHeroRating);
        tvHeroYear = findViewById(R.id.tvHeroYear);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        btnHeroPlay = findViewById(R.id.btnHeroPlay);
        btnHeroInfo = findViewById(R.id.btnHeroInfo);

        llContinueWatching = findViewById(R.id.llContinueWatching);
        rvContinueWatching = findViewById(R.id.rvContinueWatching);
        rvTrending = findViewById(R.id.rvTrending);
        rvPopularMovies = findViewById(R.id.rvPopularMovies);
        rvPopularSeries = findViewById(R.id.rvPopularSeries);
        rvCartoons = findViewById(R.id.rvCartoons);
        rvAnime = findViewById(R.id.rvAnime);

        rvContinueWatching.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvTrending.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPopularMovies.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPopularSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCartoons.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvAnime.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
    }

    private void setupAdapters() {
        historyAdapter = new HistoryAdapter(this, item -> {
            MediaItem m = new MediaItem();
            m.setId(item.getMediaId());
            m.setTitle(item.getTitle());
            m.setPosterPath(item.getPosterUrl());
            m.setMediaType(item.getMediaType());
            openDetail(m);
        });
        rvContinueWatching.setAdapter(historyAdapter);

        trendingAdapter = new MediaCardAdapter(this, this::openDetail, false);
        rvTrending.setAdapter(trendingAdapter);

        popularMoviesAdapter = new MediaCardAdapter(this, this::openDetail, false);
        rvPopularMovies.setAdapter(popularMoviesAdapter);

        popularSeriesAdapter = new MediaCardAdapter(this, this::openDetail, false);
        rvPopularSeries.setAdapter(popularSeriesAdapter);

        cartoonsAdapter = new MediaCardAdapter(this, this::openDetail, false);
        rvCartoons.setAdapter(cartoonsAdapter);

        animeAdapter = new MediaCardAdapter(this, this::openDetail, false);
        rvAnime.setAdapter(animeAdapter);

        gridAdapter = new MediaCardAdapter(this, this::openDetail, true);
        rvCategoryGrid.setAdapter(gridAdapter);
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(this::loadAllData);

        findViewById(R.id.btnHeaderSearch).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(this, SearchActivity.class));
        });

        findViewById(R.id.navSearch).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startActivity(new Intent(this, SearchActivity.class));
        });

        findViewById(R.id.btnHeaderBookmarks).setOnClickListener(v -> showBookmarks());
        findViewById(R.id.navBookmarks).setOnClickListener(v -> showBookmarks());

        findViewById(R.id.navHome).setOnClickListener(v -> selectTab(0));

        tabAll.setOnClickListener(v -> selectTab(0));
        tabMovies.setOnClickListener(v -> selectTab(1));
        tabSeries.setOnClickListener(v -> selectTab(2));
        tabCartoons.setOnClickListener(v -> selectTab(3));
        tabAnime.setOnClickListener(v -> selectTab(4));

        btnHeroPlay.setOnClickListener(v -> {
            if (heroMedia != null) openDetail(heroMedia);
        });

        btnHeroInfo.setOnClickListener(v -> {
            if (heroMedia != null) openDetail(heroMedia);
        });
    }

    private void selectTab(int index) {
        tabAll.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        activeTab = index;

        TextView[] tabs = {tabAll, tabMovies, tabSeries, tabCartoons, tabAnime};
        for (int i = 0; i < tabs.length; i++) {
            boolean isSel = (i == index);
            tabs[i].setBackgroundResource(isSel ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            tabs[i].setTextColor(getColor(isSel ? R.color.white : R.color.text_secondary));
        }

        if (index == 0) {
            llHomeContent.setVisibility(View.VISIBLE);
            rvCategoryGrid.setVisibility(View.GONE);
        } else {
            llHomeContent.setVisibility(View.GONE);
            rvCategoryGrid.setVisibility(View.VISIBLE);
            loadCategoryGrid(index);
        }
    }

    private void loadCategoryGrid(int index) {
        swipeRefresh.setRefreshing(true);
        TmdbService.Callback<List<MediaItem>> callback = new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                swipeRefresh.setRefreshing(false);
                gridAdapter.setItems(result);
            }

            @Override
            public void onError(Exception e) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show();
            }
        };

        if (index == 1) tmdbService.getPopularMovies(callback);
        else if (index == 2) tmdbService.getPopularTv(callback);
        else if (index == 3) tmdbService.getCartoons(callback);
        else if (index == 4) tmdbService.getAnime(callback);
    }

    private void loadAllData() {
        swipeRefresh.setRefreshing(true);
        loadHistory();

        // 1. Trending
        tmdbService.getTrending(new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                swipeRefresh.setRefreshing(false);
                if (result != null && !result.isEmpty()) {
                    setupHero(result.get(0));
                    trendingAdapter.setItems(result);
                }
            }

            @Override
            public void onError(Exception e) {
                swipeRefresh.setRefreshing(false);
            }
        });

        // 2. Popular Movies
        tmdbService.getPopularMovies(new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                popularMoviesAdapter.setItems(result);
            }

            @Override
            public void onError(Exception e) {}
        });

        // 3. Popular Series
        tmdbService.getPopularTv(new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                popularSeriesAdapter.setItems(result);
            }

            @Override
            public void onError(Exception e) {}
        });

        // 4. Cartoons
        tmdbService.getCartoons(new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                cartoonsAdapter.setItems(result);
            }

            @Override
            public void onError(Exception e) {}
        });

        // 5. Anime
        tmdbService.getAnime(new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> result) {
                animeAdapter.setItems(result);
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void setupHero(MediaItem media) {
        this.heroMedia = media;
        tvHeroTitle.setText(media.getTitle());
        tvHeroYear.setText(media.getYear());
        tvHeroRating.setText(media.getFormattedRating());

        String backdrop = media.getFullBackdropUrl();
        if (backdrop != null) {
            Glide.with(this)
                    .load(backdrop)
                    .centerCrop()
                    .placeholder(R.color.card_dark)
                    .into(ivHeroBackdrop);
        }
    }

    private void loadHistory() {
        List<WatchHistoryItem> history = historyStore.getHistory();
        if (history != null && !history.isEmpty()) {
            llContinueWatching.setVisibility(View.VISIBLE);
            historyAdapter.setItems(history);
        } else {
            llContinueWatching.setVisibility(View.GONE);
        }
    }

    private void showBookmarks() {
        List<MediaItem> bookmarks = historyStore.getBookmarks();
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "Список закладок пуст", Toast.LENGTH_SHORT).show();
            return;
        }
        llHomeContent.setVisibility(View.GONE);
        rvCategoryGrid.setVisibility(View.VISIBLE);
        gridAdapter.setItems(bookmarks);
    }

    private void openDetail(MediaItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("media_item", item);
        startActivity(intent);
    }
}
