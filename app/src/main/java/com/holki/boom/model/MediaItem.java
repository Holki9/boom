package com.holki.boom.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MediaItem implements Serializable {
    private long id;
    private String title;
    private String originalTitle;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private String mediaType; // "movie" or "tv"
    private String releaseDate;
    private double voteAverage;
    private int voteCount;
    private List<String> genres = new ArrayList<>();
    private String imdbId;
    private String kinopoiskId;

    public MediaItem() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title != null ? title : (originalTitle != null ? originalTitle : "Без названия"); }
    public void setTitle(String title) { this.title = title; }

    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }

    public String getOverview() { return overview != null && !overview.isEmpty() ? overview : "Описание скоро появится."; }
    public void setOverview(String overview) { this.overview = overview; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getFullPosterUrl() {
        if (posterPath == null || posterPath.isEmpty()) return null;
        if (posterPath.startsWith("http")) return posterPath;
        return "https://image.tmdb.org/t/p/w500" + posterPath;
    }

    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }

    public String getFullBackdropUrl() {
        if (backdropPath == null || backdropPath.isEmpty()) return getFullPosterUrl();
        if (backdropPath.startsWith("http")) return backdropPath;
        return "https://image.tmdb.org/t/p/w1280" + backdropPath;
    }

    public String getMediaType() { return mediaType != null ? mediaType : "movie"; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public boolean isTv() { return "tv".equalsIgnoreCase(mediaType); }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public String getYear() {
        if (releaseDate != null && releaseDate.length() >= 4) {
            return releaseDate.substring(0, 4);
        }
        return "";
    }

    public double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(double voteAverage) { this.voteAverage = voteAverage; }

    public String getFormattedRating() {
        if (voteAverage <= 0) return "—";
        return String.format(java.util.Locale.US, "%.1f", voteAverage);
    }

    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }

    public String getKinopoiskId() { return kinopoiskId; }
    public void setKinopoiskId(String kinopoiskId) { this.kinopoiskId = kinopoiskId; }
}
