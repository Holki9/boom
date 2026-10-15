package com.holki.boom.api;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holki.boom.model.MediaItem;
import com.holki.boom.model.StreamSource;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CollapsScraper {
    private static final String ALLOHA_TOKEN = "04941a9a3ca3ac16e2b4327347bbc1";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(StreamSource streamSource);
        void onError(Exception e);
    }

    public void resolveStream(MediaItem media, int season, int episode, Callback callback) {
        executor.execute(() -> {
            try {
                StreamSource source = null;

                // Step 1: Try Alloha API (has extensive Russian catalog with KP IDs and direct embeds)
                try {
                    String query = URLEncoder.encode(media.getTitle(), "UTF-8");
                    String allohaUrl = "https://api.alloha.tv/?token=" + ALLOHA_TOKEN + "&name=" + query;
                    Request req = new Request.Builder()
                            .url(allohaUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build();

                    try (Response resp = client.newCall(req).execute()) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            String json = resp.body().string();
                            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                            if (root.has("data") && root.get("data").isJsonObject()) {
                                JsonObject data = root.getAsJsonObject("data");
                                if (data.has("id_kp") && !data.get("id_kp").isJsonNull()) {
                                    media.setKinopoiskId(data.get("id_kp").getAsString());
                                }
                                if (data.has("iframe") && !data.get("iframe").isJsonNull()) {
                                    String iframe = data.get("iframe").getAsString();
                                    source = new StreamSource();
                                    source.setTitle(media.getTitle());
                                    source.setWebEmbedUrl(iframe);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // Step 2: Try Collaps using Kinopoisk ID if available
                if (media.getKinopoiskId() != null && !media.getKinopoiskId().isEmpty()) {
                    String type = media.isTv() ? "series" : "movie";
                    String collapsUrl = "https://api.delivembd.ws/embed/" + type + "/" + media.getKinopoiskId();
                    if (media.isTv()) {
                        collapsUrl += "?season=" + season + "&episode=" + episode;
                    }

                    try {
                        Request cReq = new Request.Builder()
                                .url(collapsUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .build();

                        try (Response cResp = client.newCall(cReq).execute()) {
                            if (cResp.isSuccessful() && cResp.body() != null) {
                                String html = cResp.body().string();
                                StreamSource parsed = parseCollapsHtml(html, media.getTitle());
                                if (parsed != null && parsed.getHlsMasterUrl() != null) {
                                    source = parsed;
                                    source.setWebEmbedUrl(collapsUrl);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Step 3: Fallback if no direct stream found: use Alloha embed or generic embed
                if (source == null) {
                    source = new StreamSource();
                    source.setTitle(media.getTitle());
                    // Use clean web player
                    String encodedTitle = URLEncoder.encode(media.getTitle(), "UTF-8");
                    source.setWebEmbedUrl("https://kinobox.tv/api/players?title=" + encodedTitle);
                }

                final StreamSource finalSource = source;
                mainHandler.post(() -> callback.onSuccess(finalSource));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private StreamSource parseCollapsHtml(String html, String title) {
        StreamSource src = new StreamSource();
        src.setTitle(title);

        // Find hls: "https://...master.m3u8..."
        Pattern hlsPattern = Pattern.compile("hls:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']");
        Matcher m = hlsPattern.matcher(html);
        if (m.find()) {
            src.setHlsMasterUrl(m.group(1));
            src.setDirectStream(true);
        }

        // Find audio track names: audio: {"names":["Рус. Дублированный", ...]}
        Pattern audioPattern = Pattern.compile("audio:\\s*\\{[^}]*\"names\":\\s*\\[([^\\]]+)\\]");
        Matcher am = audioPattern.matcher(html);
        if (am.find()) {
            String namesStr = am.group(1);
            Pattern namePat = Pattern.compile("[\"']([^\"']+)[\"']");
            Matcher nm = namePat.matcher(namesStr);
            List<StreamSource.AudioTrack> tracks = new ArrayList<>();
            while (nm.find()) {
                tracks.add(new StreamSource.AudioTrack(nm.group(1), "ru", null));
            }
            src.setAudioTracks(tracks);
        }

        return src;
    }
}
