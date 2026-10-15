package com.holki.boom.model;

import java.io.Serializable;

public class WatchHistoryItem implements Serializable {
    private long mediaId;
    private String title;
    private String posterUrl;
    private String mediaType;
    private long positionMs;
    private long durationMs;
    private long lastWatchedTime;
    private int season = 1;
    private int episode = 1;
    private String streamUrl;

    public WatchHistoryItem() {}

    public long getMediaId() { return mediaId; }
    public void setMediaId(long mediaId) { this.mediaId = mediaId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public long getPositionMs() { return positionMs; }
    public void setPositionMs(long positionMs) { this.positionMs = positionMs; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public int getProgressPercent() {
        if (durationMs <= 0) return 0;
        return (int) Math.min(100, Math.max(0, (positionMs * 100) / durationMs));
    }

    public long getLastWatchedTime() { return lastWatchedTime; }
    public void setLastWatchedTime(long lastWatchedTime) { this.lastWatchedTime = lastWatchedTime; }

    public int getSeason() { return season; }
    public void setSeason(int season) { this.season = season; }

    public int getEpisode() { return episode; }
    public void setEpisode(int episode) { this.episode = episode; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
}
