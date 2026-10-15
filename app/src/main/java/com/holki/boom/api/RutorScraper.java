package com.holki.boom.api;

import android.os.Handler;
import android.os.Looper;

import com.holki.boom.model.TorrentItem;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RutorScraper {
    private static final String[] RUTOR_MIRRORS = {
            "http://rutor.info",
            "http://rutor.is",
            "http://6tor.org"
    };

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(List<TorrentItem> torrents);
        void onError(Exception e);
    }

    public void searchTorrents(String title, String year, Callback callback) {
        executor.execute(() -> {
            try {
                List<TorrentItem> items = new ArrayList<>();
                // Try title + year first
                String query = (year != null && !year.isEmpty()) ? (title + " " + year) : title;
                items = executeSearch(query);

                // If not found, try just title
                if (items.isEmpty() && year != null && !year.isEmpty()) {
                    items = executeSearch(title);
                }

                // Sort: 4K items first, then by seeders count
                Collections.sort(items, (a, b) -> {
                    boolean a4k = a.getQualityTag().contains("4K");
                    boolean b4k = b.getQualityTag().contains("4K");
                    if (a4k && !b4k) return -1;
                    if (!a4k && b4k) return 1;
                    return Integer.compare(b.getSeeders(), a.getSeeders());
                });

                final List<TorrentItem> finalItems = items;
                mainHandler.post(() -> callback.onSuccess(finalItems));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private List<TorrentItem> executeSearch(String query) {
        List<TorrentItem> list = new ArrayList<>();
        for (String mirror : RUTOR_MIRRORS) {
            try {
                // Rutor requires CP1251 encoding for Russian queries
                String encoded = URLEncoder.encode(query, "cp1251");
                String url = mirror + "/search/0/0/0/0/" + encoded;

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String html = response.body().string();
                        Document doc = Jsoup.parse(html);
                        Elements rows = doc.select("tr.gai, tr.tum");

                        for (Element row : rows) {
                            Element titleLink = row.selectFirst("a[href^=/torrent/]");
                            Element magnetLink = row.selectFirst("a[href^=magnet:]");
                            Elements alignRights = row.select("td[align=right]");
                            Element seedsEl = row.selectFirst("span.green");
                            Element leechEl = row.selectFirst("span.red");

                            if (titleLink != null && magnetLink != null) {
                                String tName = titleLink.text().trim();
                                String magnet = magnetLink.attr("href").trim();

                                String size = "?";
                                if (!alignRights.isEmpty()) {
                                    size = alignRights.last().text().replace("\u00a0", " ").trim();
                                }

                                int seeds = 0;
                                if (seedsEl != null) {
                                    try {
                                        seeds = Integer.parseInt(seedsEl.text().replaceAll("[^0-9]", ""));
                                    } catch (Exception ignored) {}
                                }

                                int leech = 0;
                                if (leechEl != null) {
                                    try {
                                        leech = Integer.parseInt(leechEl.text().replaceAll("[^0-9]", ""));
                                    } catch (Exception ignored) {}
                                }

                                TorrentItem item = new TorrentItem(tName, magnet, size, seeds, leech);
                                list.add(item);
                            }
                        }

                        if (!list.isEmpty()) {
                            return list;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Try next mirror
            }
        }
        return list;
    }
}
