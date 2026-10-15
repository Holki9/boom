package com.holki.boom.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.holki.boom.model.MediaItem;
import com.holki.boom.model.WatchHistoryItem;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WatchHistoryStore {
    private static final String PREF_NAME = "boom_prefs";
    private static final String KEY_HISTORY = "watch_history";
    private static final String KEY_BOOKMARKS = "user_bookmarks";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public WatchHistoryStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void saveProgress(WatchHistoryItem item) {
        if (item == null) return;
        List<WatchHistoryItem> list = getHistory();
        // Remove existing item with same mediaId
        Iterator<WatchHistoryItem> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().getMediaId() == item.getMediaId()) {
                it.remove();
                break;
            }
        }
        item.setLastWatchedTime(System.currentTimeMillis());
        list.add(0, item);
        if (list.size() > 50) {
            list = list.subList(0, 50);
        }
        prefs.edit().putString(KEY_HISTORY, gson.toJson(list)).apply();
    }

    public synchronized List<WatchHistoryItem> getHistory() {
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<WatchHistoryItem>>() {}.getType();
            List<WatchHistoryItem> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public synchronized WatchHistoryItem getProgress(long mediaId) {
        for (WatchHistoryItem item : getHistory()) {
            if (item.getMediaId() == mediaId) return item;
        }
        return null;
    }

    public synchronized boolean isBookmarked(long mediaId) {
        for (MediaItem item : getBookmarks()) {
            if (item.getId() == mediaId) return true;
        }
        return false;
    }

    public synchronized void toggleBookmark(MediaItem item) {
        if (item == null) return;
        List<MediaItem> list = getBookmarks();
        boolean found = false;
        Iterator<MediaItem> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().getId() == item.getId()) {
                it.remove();
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(0, item);
        }
        prefs.edit().putString(KEY_BOOKMARKS, gson.toJson(list)).apply();
    }

    public synchronized List<MediaItem> getBookmarks() {
        String json = prefs.getString(KEY_BOOKMARKS, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<MediaItem>>() {}.getType();
            List<MediaItem> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
