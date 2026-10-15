package com.holki.boom.model;

import java.io.Serializable;

public class TorrentItem implements Serializable {
    private String title;
    private String magnetUrl;
    private String size;
    private int seeders;
    private int leechers;
    private String qualityTag; // e.g. "4K UHD", "1080p", "720p", "HDR", "Dolby Vision"
    private String voiceTag;   // e.g. "Дубляж", "RHS", "LostFilm", "Original"

    public TorrentItem() {}

    public TorrentItem(String title, String magnetUrl, String size, int seeders, int leechers) {
        this.title = title;
        this.magnetUrl = magnetUrl;
        this.size = size;
        this.seeders = seeders;
        this.leechers = leechers;
        detectTags();
    }

    public void detectTags() {
        if (title == null) return;
        String upper = title.toUpperCase();
        if (upper.contains("2160P") || upper.contains("4K") || upper.contains("UHD")) {
            qualityTag = "4K UHD";
        } else if (upper.contains("1080P") || upper.contains("BDRIP 1080")) {
            qualityTag = "1080p";
        } else if (upper.contains("720P")) {
            qualityTag = "720p";
        } else {
            qualityTag = "HD";
        }

        if (upper.contains("DOLBY VISION") || upper.contains("DV")) {
            qualityTag += " • DV";
        } else if (upper.contains("HDR")) {
            qualityTag += " • HDR";
        }

        if (upper.contains("RED HEAD SOUND") || upper.contains("RHS")) {
            voiceTag = "Red Head Sound";
        } else if (upper.contains("ДУБЛЯЖ") || upper.contains("ДУБЛИРОВАННЫЙ")) {
            voiceTag = "Дубляж";
        } else if (upper.contains("LOSTFILM")) {
            voiceTag = "LostFilm";
        } else if (upper.contains("КУБИК В КУБЕ")) {
            voiceTag = "Кубик в кубе";
        } else if (upper.contains("NEWSTUDIO")) {
            voiceTag = "NewStudio";
        } else {
            voiceTag = "Многоголосый";
        }
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; detectTags(); }

    public String getMagnetUrl() { return magnetUrl; }
    public void setMagnetUrl(String magnetUrl) { this.magnetUrl = magnetUrl; }

    public String getSize() { return size != null ? size : "?"; }
    public void setSize(String size) { this.size = size; }

    public int getSeeders() { return seeders; }
    public void setSeeders(int seeders) { this.seeders = seeders; }

    public int getLeechers() { return leechers; }
    public void setLeechers(int leechers) { this.leechers = leechers; }

    public String getQualityTag() { return qualityTag != null ? qualityTag : "1080p"; }
    public String getVoiceTag() { return voiceTag != null ? voiceTag : "Перевод"; }
}
