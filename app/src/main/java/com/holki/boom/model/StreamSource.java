package com.holki.boom.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StreamSource implements Serializable {
    private String title;
    private String hlsMasterUrl;
    private List<AudioTrack> audioTracks = new ArrayList<>();
    private List<SubtitleTrack> subtitles = new ArrayList<>();
    private String webEmbedUrl;
    private String referer;
    private String activeTranslationName = "Основная";
    private boolean isDirectStream;
    private List<EmbedOption> alternativeEmbeds = new ArrayList<>();

    public static class EmbedOption implements Serializable {
        private String name;
        private String url;

        public EmbedOption(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() { return name; }
        public String getUrl() { return url; }
    }

    public static class AudioTrack implements Serializable {
        private String name;
        private String language;
        private String url;

        public AudioTrack(String name, String language, String url) {
            this.name = name;
            this.language = language;
            this.url = url;
        }

        public String getName() { return name != null ? name : "Основная дорожка"; }
        public String getLanguage() { return language; }
        public String getUrl() { return url; }
    }

    public static class SubtitleTrack implements Serializable {
        private String language;
        private String url;

        public SubtitleTrack(String language, String url) {
            this.language = language;
            this.url = url;
        }

        public String getLanguage() { return language; }
        public String getUrl() { return url; }
    }

    public StreamSource() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getHlsMasterUrl() { return hlsMasterUrl; }
    public void setHlsMasterUrl(String hlsMasterUrl) { this.hlsMasterUrl = hlsMasterUrl; }

    public List<AudioTrack> getAudioTracks() { return audioTracks; }
    public void setAudioTracks(List<AudioTrack> audioTracks) { this.audioTracks = audioTracks; }

    public List<SubtitleTrack> getSubtitles() { return subtitles; }
    public void setSubtitles(List<SubtitleTrack> subtitles) { this.subtitles = subtitles; }

    public String getWebEmbedUrl() { return webEmbedUrl; }
    public void setWebEmbedUrl(String webEmbedUrl) { this.webEmbedUrl = webEmbedUrl; }

    public String getReferer() { return referer != null ? referer : "https://kinogo.inc/"; }
    public void setReferer(String referer) { this.referer = referer; }

    public String getActiveTranslationName() { return activeTranslationName; }
    public void setActiveTranslationName(String activeTranslationName) { this.activeTranslationName = activeTranslationName; }

    public boolean isDirectStream() { return isDirectStream; }
    public void setDirectStream(boolean directStream) { isDirectStream = directStream; }

    public List<EmbedOption> getAlternativeEmbeds() { return alternativeEmbeds; }
    public void setAlternativeEmbeds(List<EmbedOption> alternativeEmbeds) { this.alternativeEmbeds = alternativeEmbeds; }

    public void addAlternativeEmbed(String name, String url) {
        if (url != null && !url.isEmpty()) {
            this.alternativeEmbeds.add(new EmbedOption(name, url));
        }
    }
}
