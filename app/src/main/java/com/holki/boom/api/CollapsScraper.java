package com.holki.boom.api;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holki.boom.model.MediaItem;
import com.holki.boom.model.StreamSource;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                StreamSource source = new StreamSource();
                source.setTitle(media.getTitle());

                String kpId = media.getKinopoiskId();

                // Step 1: Query Alloha by name (or KP if known)
                try {
                    String queryParam = (kpId != null && !kpId.isEmpty())
                            ? "kp=" + kpId
                            : "name=" + URLEncoder.encode(media.getTitle(), "UTF-8");
                    String allohaUrl = "https://api.alloha.tv/?token=" + ALLOHA_TOKEN + "&" + queryParam;

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
                                    kpId = data.get("id_kp").getAsString();
                                    media.setKinopoiskId(kpId);
                                }

                                if (media.isTv()) {
                                    // Parse TV series season & episode from Alloha
                                    boolean foundEpisode = false;
                                    if (data.has("seasons") && data.get("seasons").isJsonObject()) {
                                        JsonObject seasons = data.getAsJsonObject("seasons");
                                        String seasonKey = String.valueOf(season);
                                        if (seasons.has(seasonKey) && seasons.get(seasonKey).isJsonObject()) {
                                            JsonObject seasonObj = seasons.getAsJsonObject(seasonKey);
                                            if (seasonObj.has("episodes") && seasonObj.get("episodes").isJsonObject()) {
                                                JsonObject episodes = seasonObj.getAsJsonObject("episodes");
                                                String epKey = String.valueOf(episode);
                                                if (episodes.has(epKey) && episodes.get(epKey).isJsonObject()) {
                                                    JsonObject epObj = episodes.getAsJsonObject(epKey);
                                                    if (epObj.has("iframe") && !epObj.get("iframe").isJsonNull()) {
                                                        source.setWebEmbedUrl(epObj.get("iframe").getAsString());
                                                        foundEpisode = true;
                                                    }

                                                    // Collect translation options for this episode
                                                    if (epObj.has("translation") && epObj.get("translation").isJsonObject()) {
                                                        JsonObject trObj = epObj.getAsJsonObject("translation");
                                                        for (Map.Entry<String, JsonElement> entry : trObj.entrySet()) {
                                                            if (entry.getValue().isJsonObject()) {
                                                                JsonObject trItem = entry.getValue().getAsJsonObject();
                                                                String trName = trItem.has("translation") ? trItem.get("translation").getAsString() : "Озвучка";
                                                                if (trItem.has("iframe") && !trItem.get("iframe").isJsonNull()) {
                                                                    source.addAlternativeEmbed("Alloha (" + trName + ")", trItem.get("iframe").getAsString());
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!foundEpisode && data.has("iframe") && !data.get("iframe").isJsonNull()) {
                                        String baseIframe = data.get("iframe").getAsString();
                                        baseIframe += "&season=" + season + "&episode=" + episode;
                                        source.setWebEmbedUrl(baseIframe);
                                    }
                                } else {
                                    // Movie
                                    if (data.has("iframe") && !data.get("iframe").isJsonNull()) {
                                        source.setWebEmbedUrl(data.get("iframe").getAsString());
                                    }

                                    // Collect translation options
                                    if (data.has("translation_iframe") && data.get("translation_iframe").isJsonObject()) {
                                        JsonObject trMap = data.getAsJsonObject("translation_iframe");
                                        for (Map.Entry<String, JsonElement> entry : trMap.entrySet()) {
                                            if (entry.getValue().isJsonObject()) {
                                                JsonObject trItem = entry.getValue().getAsJsonObject();
                                                String trName = trItem.has("name") ? trItem.get("name").getAsString() : "Озвучка";
                                                if (trItem.has("iframe") && !trItem.get("iframe").isJsonNull()) {
                                                    source.addAlternativeEmbed(trName, trItem.get("iframe").getAsString());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // Step 2: Try Collaps direct HLS stream (if KP ID is available)
                if (kpId != null && !kpId.isEmpty()) {
                    String type = media.isTv() ? "series" : "movie";
                    String collapsUrl = "https://api.delivembd.ws/embed/" + type + "/" + kpId;
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
                                StreamSource parsed = parseCollapsHtml(html, media.getTitle(), season, episode, media.isTv());
                                if (parsed != null && parsed.getHlsMasterUrl() != null) {
                                    source.setHlsMasterUrl(parsed.getHlsMasterUrl());
                                    source.setReferer("https://api.delivembd.ws/");
                                    source.setDirectStream(true);
                                    if (!parsed.getAudioTracks().isEmpty()) {
                                        source.setAudioTracks(parsed.getAudioTracks());
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Step 3: Add Universal Balancer Alternatives
                String identifier = (kpId != null && !kpId.isEmpty()) ? kpId : String.valueOf(media.getId());
                source.addAlternativeEmbed("Kinobox / Kinohost (Мультиплеер)", "https://kinohost.web.app/movie/" + identifier);
                source.addAlternativeEmbed("Kinohub VIP", "https://on.kinohub.vip/movie/" + identifier + "/");

                if (kpId != null && !kpId.isEmpty()) {
                    String collapsEmbed = "https://api.delivembd.ws/embed/" + (media.isTv() ? "series/" : "movie/") + kpId;
                    if (media.isTv()) {
                        collapsEmbed += "?season=" + season + "&episode=" + episode;
                    }
                    source.addAlternativeEmbed("Collaps Server", collapsEmbed);
                }

                // International VidSrc fallback for movies/series
                String imdbOrTmdb = (media.getImdbId() != null && !media.getImdbId().isEmpty()) ? media.getImdbId() : String.valueOf(media.getId());
                if (media.isTv()) {
                    source.addAlternativeEmbed("VidSrc World (EN)", "https://vidsrc.to/embed/tv/" + imdbOrTmdb + "/" + season + "/" + episode);
                } else {
                    source.addAlternativeEmbed("VidSrc World (EN)", "https://vidsrc.to/embed/movie/" + imdbOrTmdb);
                }

                // Ensure primary webEmbedUrl is set
                if (source.getWebEmbedUrl() == null || source.getWebEmbedUrl().isEmpty()) {
                    if (!source.getAlternativeEmbeds().isEmpty()) {
                        source.setWebEmbedUrl(source.getAlternativeEmbeds().get(0).getUrl());
                    } else {
                        source.setWebEmbedUrl("https://kinohost.web.app/movie/" + identifier);
                    }
                }

                final StreamSource finalSource = source;
                mainHandler.post(() -> callback.onSuccess(finalSource));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private StreamSource parseCollapsHtml(String html, String title, int season, int episode, boolean isTv) {
        StreamSource src = new StreamSource();
        src.setTitle(title);
        src.setReferer("https://api.delivembd.ws/");

        if (isTv) {
            Pattern epPattern = Pattern.compile("\"episode\"\\s*:\\s*\"?" + episode + "\"?[^}]*?\"hls\"\\s*:\\s*\"([^\"]+\\.m3u8[^\"]*)\"");
            Matcher epMatcher = epPattern.matcher(html);
            if (epMatcher.find()) {
                src.setHlsMasterUrl(epMatcher.group(1));
                src.setDirectStream(true);
            }
        }

        if (src.getHlsMasterUrl() == null) {
            Pattern hlsPattern = Pattern.compile("[\"']?hls[\"']?\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']");
            Matcher m = hlsPattern.matcher(html);
            if (m.find()) {
                src.setHlsMasterUrl(m.group(1));
                src.setDirectStream(true);
            }
        }

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
