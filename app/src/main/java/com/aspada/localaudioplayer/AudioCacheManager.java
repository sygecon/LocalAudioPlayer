package com.aspada.localaudioplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AudioCacheManager {

    private static final String CACHE_FILE = "audio_cache.json";
    private static final long CACHE_VALID_DURATION = 24 * 3600000 * 24; // 24 часа * 24 дня

    private final Context context;
    private final Gson gson;
    private final AudioFolderScanner scanner;

    public interface CacheCallback {
        void onDataReady(List<FolderItem> folders, boolean fromCache);
        void onScanStarted();
        void onScanProgress(String folderName);
        void onScanComplete();
    }

    public AudioCacheManager(Context context) {
        this.context = context;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.scanner = new AudioFolderScanner();
    }

    /**
     * Загружает данные: сначала пробует кэш, если невалиден — сканирует
     */
    public void loadData(CacheCallback callback) {
        // Пробуем загрузить из кэша
        List<FolderItem> cached = loadFromCache();

        if (cached != null && isCacheValid(cached)) {
            Log.d("Cache", "Загружено из кэша");
            callback.onDataReady(cached, true);
        } else {
            Log.d("Cache", "Кэш устарел или отсутствует, запускаем сканирование");
            startScan(callback);
        }
    }

    /**
     * Принудительное сканирование
     */
    public void forceRescan(CacheCallback callback) {
        clearCache();
        startScan(callback);
    }

    /**
     * Запускает сканирование в фоновом потоке
     */
    private void startScan(CacheCallback callback) {
        callback.onScanStarted();

        new Thread(() -> {
            try {
                // Симулируем прогресс
                callback.onScanProgress("Сканирование папок...");
                List<FolderItem> folders = scanner.scanAndBuildTree();

                // Сохраняем в кэш
                saveToCache(folders);

                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onScanComplete();
                    callback.onDataReady(folders, false);
                });

            } catch (Exception e) {
                Log.e("Cache", "Ошибка сканирования", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Пробуем загрузить старый кэш как fallback
                    List<FolderItem> oldCache = loadFromCache();
                    callback.onDataReady(oldCache != null ? oldCache : new ArrayList<>(), true);
                });
            }
        }).start();
    }

    /**
     * Проверяет, валиден ли кэш
     */
    private boolean isCacheValid(List<FolderItem> cachedFolders) {
        // Проверяем время создания кэша
        File cacheFile = getCacheFile();
        long cacheAge = System.currentTimeMillis() - cacheFile.lastModified();

        // Если кэш старше допустимого — невалиден
        if (cacheAge > CACHE_VALID_DURATION) {
            return false;
        }

        // Быстрая проверка: изменилось ли что-то в корневых папках
        for (FolderItem cached : cachedFolders) {
            File folder = new File(cached.getPath());
            if (!folder.exists()) {
                return false; // Папка удалена
            }

            // Проверяем время изменения корневой папки
            if (folder.lastModified() > cacheFile.lastModified()) {
                return false; // Что-то изменилось
            }
        }

        return true;
    }

    /**
     * Сохраняет структуру в JSON-файл
     */
    private void saveToCache(List<FolderItem> folders) {
        try {
            File cacheFile = getCacheFile();
            FileWriter writer = new FileWriter(cacheFile);
            gson.toJson(folders, writer);
            writer.close();

            Log.d("Cache", "Сохранено в кэш: " + cacheFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("Cache", "Ошибка сохранения кэша", e);
        }
    }

    /**
     * Загружает структуру из JSON-файла.
     * Возвращает файл кэша в приватной директории приложения
     */
    private File getCacheFile() {
        return new File(context.getCacheDir(), CACHE_FILE);
    }

    /**
     * Очищает кэш
     */
    public void clearCache() {
        File cacheFile = getCacheFile();
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    private List<FolderItem> loadFromCache() {
        try {
            File cacheFile = getCacheFile();
            if (!cacheFile.exists()) {
                Log.d("Cache", "Файл кэша не найден");
                return null;
            }

            FileReader reader = new FileReader(cacheFile);
            Type listType = new TypeToken<List<FolderItem>>(){}.getType();
            List<FolderItem> folders = gson.fromJson(reader, listType);
            reader.close();

            // Проверяем, что десериализация прошла успешно
            if (folders == null) {
                Log.e("Cache", "Кэш поврежден, удаляем");
                if (! cacheFile.delete()) return null;
                return null;
            }

            Log.d("Cache", "Загружено из кэша: " + folders.size() + " папок");
            return folders;

        } catch (IOException e) {
            Log.e("Cache", "Ошибка загрузки кэша: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e("Cache", "Непредвиденная ошибка: " + e.getMessage());
            // Удаляем поврежденный кэш
            if (! getCacheFile().delete()) return null;
            return null;
        }
    }
}
