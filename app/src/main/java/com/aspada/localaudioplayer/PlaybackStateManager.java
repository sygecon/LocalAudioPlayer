package com.aspada.localaudioplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackStateManager {
    private static final String PREF_NAME   = "melap_playback_states";
    private static final String KEY_STATES  = "states";
    private static final int MAX_STATES     = 30;

    public static class State {
        public int trackIndex;
        public long positionMs;
        public long timestamp;
        public String folderPath;   // полный путь к папке

        public State(String folderPath, int trackIndex, long positionMs) {
            this.folderPath = folderPath;
            this.trackIndex = trackIndex;
            this.positionMs = positionMs;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Map<String, State> cache = new ConcurrentHashMap<>();

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
            Type type = new TypeToken<Map<String, State>>(){}.getType();
            Map<String, State> loaded = gson.fromJson(json, type);
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
        if (trackIndex < 0) return;
        String key = generateKeyFromUrl(folderId);
        cache.put(key, new State(folderId, trackIndex, positionMs));

        if (cache.size() > MAX_STATES) {
            trimCache();
        }
        persistCacheAsync();
    }

    /**
     * Получает состояние для папки (из кэша).
     */
    @Nullable
    public State getState(String folderId) {
        String key = generateKeyFromUrl(folderId);
        return cache.get(key);
    }

    /**
     * Удаляет состояние (если нужно).
     */
    public void removeState(String folderId) {
        if (folderId.isEmpty()) return;
        String key = generateKeyFromUrl(folderId);
        if (cache.remove(key) != null) {
            persistCacheAsync();
        }
    }

    /**
     * Возвращает список последних открытых папок (макс. maxCount),
     * отсортированных по времени последнего воспроизведения (сначала новые).
     * Включаются только записи с непустым folderPath.
     */
    public List<State> getRecentFolders(int maxCount) {
        List<State> result = new ArrayList<>();
        for (State state : cache.values()) {
            String folderPath = state.folderPath;
            if (folderPath != null && !folderPath.isEmpty()) {
                result.add(state);
            }
        }
        // Сортировка по убыванию timestamp
        result.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        if (result.size() > maxCount) {
            result = result.subList(0, maxCount);
        }
        return result;
    }

    /**
     * Удаляет самые старые записи, оставляя только MAX_STATES.
     */
    private void trimCache() {
        List<Map.Entry<String, State>> entries = new ArrayList<>(cache.entrySet());
        // Сортируем по timestamp: новые – выше, старые – ниже
        entries.sort(
                (a, b) -> Long.compare(b.getValue().timestamp, a.getValue().timestamp));
        while (entries.size() > MAX_STATES) {
            cache.remove(entries.remove(entries.size() - 1).getKey());
        }
    }

    /**
     * Асинхронно сохраняет кэш в SharedPreferences.
     */
    private void persistCacheAsync() {
        // Копируем данные для фона, чтобы избежать ConcurrentModification
        final Map<String, State> snapshot = new ConcurrentHashMap<>(cache);
        saveExecutor.execute(() -> {
            String json = gson.toJson(snapshot);
            prefs.edit().putString(KEY_STATES, json).apply();
        });
    }

    /**
     * Синхронное сохранение (если нужно в onDestroy).
     */
    public void saveNow() {
        String json = gson.toJson(cache);
        prefs.edit().putString(KEY_STATES, json).apply(); // синхронно
    }

    // Пройти по кэшу и удалить папки, которых больше нет на устройстве
    public void cleanupOldStates() {
        if (cache.isEmpty()) return;
        boolean isRemove = false;

        for (State state : cache.values()) {
            File folder = new File(state.folderPath);
            if (!folder.exists()) {
                String key = generateKeyFromUrl(state.folderPath);
                if (cache.remove(key) != null) {
                    isRemove = true;

                }
            }
        }

        if (isRemove) {
            persistCacheAsync();
        }
    }

    /**
     * Возвращает состояние с самым большим timestamp (последнее сохранённое) или null, если кэш пуст.
     */
    @Nullable
    public State getLastPlayedState() {
        if (cache.isEmpty()) return null;
        State last = null;
        long maxTimestamp = Long.MIN_VALUE;

        for (State state : cache.values()) {
            long tms = state.timestamp;
            if (tms > maxTimestamp) {
                maxTimestamp = tms;
                last = state;
            }
        }
        return last;
    }

    /**
     * Вместо самого URL используем его хэш
     */
    private static String generateKeyFromUrl(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Берём первые 16 символов (64 бита) – вероятность коллизии ничтожна
            return hexString.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 обязателен на Android, но на всякий случай fallback
            return String.valueOf(input.hashCode());
        }
    }
}
