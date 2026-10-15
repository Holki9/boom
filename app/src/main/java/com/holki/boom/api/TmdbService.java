package com.holki.boom.api;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holki.boom.model.MediaItem;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TmdbService {
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String API_KEY = "4ef0d7355d9ffb5151e987764708ce96";
    private static final String LANG = "ru-RU";

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public void getTrending(Callback<List<MediaItem>> callback) {
        String url = BASE_URL + "/trending/all/day?api_key=" + API_KEY + "&language=" + LANG;
        fetchMediaList(url, callback);
    }

    public void getPopularMovies(Callback<List<MediaItem>> callback) {
        String url = BASE_URL + "/movie/popular?api_key=" + API_KEY + "&language=" + LANG + "&page=1";
        fetchMediaList(url, callback);
    }

    public void getPopularTv(Callback<List<MediaItem>> callback) {
        String url = BASE_URL + "/tv/popular?api_key=" + API_KEY + "&language=" + LANG + "&page=1";
        fetchMediaList(url, callback);
    }

    public void getCartoons(Callback<List<MediaItem>> callback) {
        String url = BASE_URL + "/discover/movie?api_key=" + API_KEY + "&language=" + LANG + "&with_genres=16&sort_by=popularity.desc";
        fetchMediaList(url, callback);
    }

    public void getAnime(Callback<List<MediaItem>> callback) {
        String url = BASE_URL + "/discover/tv?api_key=" + API_KEY + "&language=" + LANG + "&with_genres=16&with_original_language=ja&sort_by=popularity.desc";
        fetchMediaList(url, callback);
    }

    public void search(String query, Callback<List<MediaItem>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String url = BASE_URL + "/search/multi?api_key=" + API_KEY + "&language=" + LANG + "&query=" + encoded;
                List<MediaItem> results = executeMediaList(url);
                mainHandler.post(() -> callback.onSuccess(results));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void fetchDetails(MediaItem item, Callback<MediaItem> callback) {
        executor.execute(() -> {
            try {
                String type = item.isTv() ? "tv" : "movie";
                String url = BASE_URL + "/" + type + "/" + item.getId() + "?api_key=" + API_KEY + "&language=" + LANG + "&append_to_response=external_ids";
                Request request = new Request.Builder().url(url).build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject obj = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        if (obj.has("overview") && !obj.get("overview").isJsonNull()) {
                            String ov = obj.get("overview").getAsString();
                            if (!ov.isEmpty()) item.setOverview(ov);
                        }
                        if (obj.has("genres") && obj.get("genres").isJsonArray()) {
                            List<String> glist = new ArrayList<>();
                            for (JsonElement g : obj.getAsJsonArray("genres")) {
                                glist.add(g.getAsJsonObject().get("name").getAsString());
                            }
                            item.setGenres(glist);
                        }
                        if (obj.has("external_ids") && obj.get("external_ids").isJsonObject()) {
                            JsonObject ext = obj.getAsJsonObject("external_ids");
                            if (ext.has("imdb_id") && !ext.get("imdb_id").isJsonNull()) {
                                item.setImdbId(ext.get("imdb_id").getAsString());
                            }
                        }
                    }
                }
                mainHandler.post(() -> callback.onSuccess(item));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private void fetchMediaList(String url, Callback<List<MediaItem>> callback) {
        executor.execute(() -> {
            try {
                List<MediaItem> items = executeMediaList(url);
                mainHandler.post(() -> callback.onSuccess(items));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private List<MediaItem> executeMediaList(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "BOOM/1.0 (Android; SM-S928B)")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }

            String jsonStr = response.body().string();
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");
            List<MediaItem> list = new ArrayList<>();

            if (results != null) {
                for (JsonElement el : results) {
                    JsonObject o = el.getAsJsonObject();
                    MediaItem m = new MediaItem();
                    m.setId(o.get("id").getAsLong());

                    if (o.has("media_type")) {
                        m.setMediaType(o.get("media_type").getAsString());
                    } else if (o.has("first_air_date")) {
                        m.setMediaType("tv");
                    } else {
                        m.setMediaType("movie");
                    }

                    // Ignore people in search
                    if ("person".equalsIgnoreCase(m.getMediaType())) continue;

                    if (o.has("title") && !o.get("title").isJsonNull()) {
                        m.setTitle(o.get("title").getAsString());
                    } else if (o.has("name") && !o.get("name").isJsonNull()) {
                        m.setTitle(o.get("name").getAsString());
                    }

                    if (o.has("original_title") && !o.get("original_title").isJsonNull()) {
                        m.setOriginalTitle(o.get("original_title").getAsString());
                    } else if (o.has("original_name") && !o.get("original_name").isJsonNull()) {
                        m.setOriginalTitle(o.get("original_name").getAsString());
                    }

                    if (o.has("overview") && !o.get("overview").isJsonNull()) {
                        m.setOverview(o.get("overview").getAsString());
                    }

                    if (o.has("poster_path") && !o.get("poster_path").isJsonNull()) {
                        m.setPosterPath(o.get("poster_path").getAsString());
                    }

                    if (o.has("backdrop_path") && !o.get("backdrop_path").isJsonNull()) {
                        m.setBackdropPath(o.get("backdrop_path").getAsString());
                    }

                    if (o.has("release_date") && !o.get("release_date").isJsonNull()) {
                        m.setReleaseDate(o.get("release_date").getAsString());
                    } else if (o.has("first_air_date") && !o.get("first_air_date").isJsonNull()) {
                        m.setReleaseDate(o.get("first_air_date").getAsString());
                    }

                    if (o.has("vote_average") && !o.get("vote_average").isJsonNull()) {
                        m.setVoteAverage(o.get("vote_average").getAsDouble());
                    }

                    if (o.has("vote_count") && !o.get("vote_count").isJsonNull()) {
                        m.setVoteCount(o.get("vote_count").getAsInt());
                    }

                    // Don't show titles without posters unless search
                    if (m.getPosterPath() != null && !m.getPosterPath().isEmpty()) {
                        list.add(m);
                    }
                }
            }
            return list;
        }
    }
}
