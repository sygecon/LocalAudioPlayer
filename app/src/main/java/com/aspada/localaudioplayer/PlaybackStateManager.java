package com.aspada.localaudioplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackStateManager {
    private static final String PREF_NAME   = "melap_playback_states";
    private static final String KEY_STATES  = "states";

    private static final String KEY_HISTORY_PATH = "listened_path";
    private static final String KEY_HISTORY_TRACK = "listened_track";
    private static final String KEY_HISTORY_POSITION = "listened_position";

    private static final int MAX_STATES     = 30;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Map<String, PlaybackState> cache = new ConcurrentHashMap<>();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();

    public PlaybackStateManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadStates(); // загружаем в кэш при создании
    }

    /**
     * Загружает все сохранённые состояния в кэш.
     */
    private void loadStates() {
        String json = prefs.getString(KEY_STATES, "{}");
        try {
            Type type = new TypeToken<Map<String, PlaybackState>>(){}.getType();
            Map<String, PlaybackState> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                cache.putAll(loaded);
                // На всякий случай обрезаем, если вдруг оказалось больше лимита
                if (cache.size() > MAX_STATES) {
                    trimCache();
                    persistCacheAsync();
                }
            }
        } catch (Exception e) {
            Log.e("PlaybackState", "Failed to load states", e);
        }
    }

    /**
     * Сохраняет состояние для папки.
     */
    public void saveState(String folderId, int trackIndex, long positionMs) {
        if (folderId.isEmpty()) return;
        String key = AppUtils.generateKeyFromUrl(folderId);
        cache.put(key, new PlaybackState(trackIndex, positionMs));

        if (cache.size() > MAX_STATES) {
            trimCache();
        }
        persistCacheAsync();
    }

    /**
     * Получает состояние для папки (из кэша).
     */
    @Nullable
    public PlaybackState getState(String folderId) {
        String key = AppUtils.generateKeyFromUrl(folderId);
        return cache.get(key);
    }

    /**
     * Удаляет состояние (если нужно).
     */
    public void removeState(String folderId) {
        if (folderId.isEmpty()) return;
        String key = AppUtils.generateKeyFromUrl(folderId);
        if (cache.remove(key) != null) {
            persistCacheAsync();
        }
    }

    /**
     * Удаляет самые старые записи, оставляя только MAX_STATES.
     */
    private void trimCache() {
        List<Map.Entry<String, PlaybackState>> entries = new ArrayList<>(cache.entrySet());
        // Сортируем по timestamp: новые – выше, старые – ниже
        entries.sort(
                (a, b) -> Long.compare(b.getValue().getTimestamp(), a.getValue().getTimestamp()));
        while (entries.size() > MAX_STATES) {
            cache.remove(entries.remove(entries.size() - 1).getKey());
        }
    }

    /**
     * Асинхронно сохраняет кэш в SharedPreferences.
     */
    private void persistCacheAsync() {
        // Копируем данные для фона, чтобы избежать ConcurrentModification
        final Map<String, PlaybackState> snapshot = new ConcurrentHashMap<>(cache);
        saveExecutor.execute(() -> {
            String json = gson.toJson(snapshot);
            prefs.edit().putString(KEY_STATES, json).apply();
        });
    }

    /**
     * Необязательно: синхронное сохранение (если нужно в onDestroy).
     */
    public void saveNow() {
        String json = gson.toJson(cache);
        prefs.edit().putString(KEY_STATES, json).apply(); // синхронно
    }

    // Пройти по кэшу и удалить папки, которых больше нет на устройстве
    public void cleanupOldStates() {
        for (String folderId : cache.keySet()) {
            File folder = new File(folderId);
            if (!folder.exists()) {
                if (cache.remove(folderId) != null) {
                    persistCacheAsync();
                }
            }
        }
    }

    /**
     * HISTORY LISTENED
     */
    // Current Folder Path
    public void saveMarkHistoryPath(String path) {
        prefs.edit().putString(KEY_HISTORY_PATH, path).apply();
    }

    public String getMarkHistoryPath() {
        return prefs.getString(KEY_HISTORY_PATH, "");
    }

    // Track number
    public void saveMarkHistoryTrack(int numTrack) {
        prefs.edit().putInt(KEY_HISTORY_TRACK, numTrack).apply();
    }

    public int getMarkHistoryTrack() {
        return prefs.getInt(KEY_HISTORY_TRACK, 0);
    }

    // Position
    public void saveMarkHistoryPosition(int pos) {
        prefs.edit().putInt(KEY_HISTORY_POSITION, pos).apply();
    }

    public int getMarkHistoryPosition() {
        return prefs.getInt(KEY_HISTORY_POSITION, 0);
    }

    //  Clear History
    public void clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY_PATH)
            .remove(KEY_HISTORY_TRACK)
            .remove(KEY_HISTORY_POSITION)
            .apply();
    }
}
