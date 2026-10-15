package com.holki.boom.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.holki.boom.R;
import com.holki.boom.api.TmdbService;
import com.holki.boom.model.MediaItem;
import com.holki.boom.ui.adapter.MediaCardAdapter;

import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private final TmdbService tmdbService = new TmdbService();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private EditText etSearch;
    private ImageView btnClear;
    private LinearLayout llPopularQueries;
    private ChipGroup chipGroupSuggestions;
    private ProgressBar pbSearch;
    private TextView tvEmpty;
    private RecyclerView rvResults;
    private MediaCardAdapter adapter;

    private static final String[] POPULAR_SUGGESTIONS = {
            "Дюна", "Острые козырьки", "Гарри Поттер", "Рик и Морти",
            "Интерстеллар", "Игра престолов", "Ведьмак", "Человек-паук", "Во все тяжкие"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupSuggestions();
        setupSearchListener();
    }

    private void initViews() {
        findViewById(R.id.btnSearchBack).setOnClickListener(v -> finish());
        etSearch = findViewById(R.id.etSearchQuery);
        btnClear = findViewById(R.id.btnSearchClear);
        llPopularQueries = findViewById(R.id.llPopularQueries);
        chipGroupSuggestions = findViewById(R.id.chipGroupSuggestions);
        pbSearch = findViewById(R.id.pbSearch);
        tvEmpty = findViewById(R.id.tvSearchEmpty);
        rvResults = findViewById(R.id.rvSearchResults);

        rvResults.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new MediaCardAdapter(this, item -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("media_item", item);
            startActivity(intent);
        }, true);
        rvResults.setAdapter(adapter);

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            btnClear.setVisibility(View.GONE);
            llPopularQueries.setVisibility(View.VISIBLE);
            rvResults.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
        });
    }

    private void setupSuggestions() {
        for (String suggestion : POPULAR_SUGGESTIONS) {
            Chip chip = new Chip(this);
            chip.setText(suggestion);
            chip.setTextColor(getColor(R.color.text_primary));
            chip.setChipBackgroundColorResource(R.color.card_dark);
            chip.setStrokeColorResource(R.color.card_stroke);
            chip.setStrokeWidth(2f);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                etSearch.setText(suggestion);
                etSearch.setSelection(suggestion.length());
                performSearch(suggestion);
            });
            chipGroupSuggestions.addView(chip);
        }
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    llPopularQueries.setVisibility(View.VISIBLE);
                    rvResults.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.GONE);
                    pbSearch.setVisibility(View.GONE);
                } else {
                    llPopularQueries.setVisibility(View.GONE);
                    searchRunnable = () -> performSearch(q);
                    searchHandler.postDelayed(searchRunnable, 350);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performSearch(String query) {
        pbSearch.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        tmdbService.search(query, new TmdbService.Callback<>() {
            @Override
            public void onSuccess(List<MediaItem> results) {
                pbSearch.setVisibility(View.GONE);
                if (results == null || results.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvResults.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvResults.setVisibility(View.VISIBLE);
                    adapter.setItems(results);
                }
            }

            @Override
            public void onError(Exception e) {
                pbSearch.setVisibility(View.GONE);
                tvEmpty.setText("Ошибка при поиске");
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }
}
