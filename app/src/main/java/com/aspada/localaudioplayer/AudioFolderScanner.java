package com.aspada.localaudioplayer;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudioFolderScanner {
    // Целевые папки
    private static final String[] targetFolders = {
            "Music", "Downloads", "Audiobooks", "Podcasts"
    };

    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".m4a", ".m4b", ".ogg", ".wav",
            ".flac", ".aac", ".opus", ".wma", ".aiff"
    };

    /**
     * Сканирует и строит полное дерево папок
     */
    public List<FolderItem> scanAndBuildTree() {
        List<FolderItem> rootFolders = new ArrayList<>();

        for (String folderName : targetFolders) {

            // Путь к корню общего хранилища
            File folder;
            if (folderName.equalsIgnoreCase("Downloads")) {
                folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "");
            } else
            if (folderName.equalsIgnoreCase("Music")) {
                folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "");
            } else
            if (folderName.equalsIgnoreCase("Podcasts")) {
                folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS), "");
            } else
            if (folderName.equalsIgnoreCase("Audiobooks")) {
                folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS), "");
            } else {
                folder = new File(Environment.getExternalStorageDirectory(), folderName);
            }

            if (folder.exists() && folder.isDirectory()) {
                FolderItem rootFolder = new FolderItem(folderName, folder.getAbsolutePath(), true);
                scanFolderRecursive(folder, rootFolder);

                // Добавляем только если есть содержимое
                if (rootFolder.hasContent()) {
                    rootFolders.add(rootFolder);
                }
            }
        }

        return rootFolders;
    }

    /**
     * Получает длительность аудиофайла
     */
    private long getAudioDuration(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            // No need to manually call release()
            retriever.release();
            return durationStr != null ? Long.parseLong(durationStr) : 0;
        } catch (Exception e) {
            // Handle exceptions
            return 0;
        }
    }

    /**
     * Естественная сортировка: "2" перед "10", "01" перед "02"
     */
    private int naturalCompare(String a, String b) {
        // Разбиваем строки на части: буквы и числа
        String[] aParts = a.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] bParts = b.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

        int minLength = Math.min(aParts.length, bParts.length);

        for (int i = 0; i < minLength; i++) {
            // Если обе части — числа, сравниваем как числа
            if (aParts[i].matches("\\d+") && bParts[i].matches("\\d+")) {
                int numCompare = Integer.compare(
                        Integer.parseInt(aParts[i]),
                        Integer.parseInt(bParts[i])
                );
                if (numCompare != 0) return numCompare;
            } else {
                // Иначе сравниваем как строки (без учета регистра)
                int strCompare = aParts[i].compareToIgnoreCase(bParts[i]);
                if (strCompare != 0) return strCompare;
            }
        }

        return Integer.compare(aParts.length, bParts.length);
    }

    private boolean isAudioFile(String fileName) {
        String name = fileName.toLowerCase();
        for (String ext : AUDIO_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private void scanFolderRecursive(File folder, FolderItem folderItem) {
        File[] files = folder.listFiles();
        if (files == null) return;

        // Сохраняем время изменения папки
        folderItem.setModified(folder.lastModified());

        List<File> subDirs = new ArrayList<>();
        List<File> audioFiles = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                subDirs.add(file);
            } else if (isAudioFile(file.getName())) {
                audioFiles.add(file);
            }
        }

        // Сортируем
        audioFiles.sort((a, b) -> naturalCompare(a.getName(), b.getName()));

        for (File audioFile : audioFiles) {
            String url = Uri.fromFile(audioFile).toString();
            long duration = getAudioDuration(audioFile);

            folderItem.addFiles(new FolderItem.AudioItem(url, audioFile.getName(), duration
//                    , audioFile.length(), audioFile.lastModified()
            ));
        }

        subDirs.sort((a, b) -> naturalCompare(a.getName(), b.getName()));

        for (File subDir : subDirs) {
            FolderItem subFolderItem = new FolderItem(
                    subDir.getName(),
                    subDir.getAbsolutePath(),
                    false
            );
            scanFolderRecursive(subDir, subFolderItem);

            if (subFolderItem.hasContent()) {
                folderItem.add(subFolderItem);
            }
        }
    }
}
