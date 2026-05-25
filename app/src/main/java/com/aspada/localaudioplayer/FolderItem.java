package com.aspada.localaudioplayer;

import java.util.ArrayList;
import java.util.List;

// Папка (может быть корневой или подпапкой)
public class FolderItem {

    public static class AudioItem {
        public String path;
        public String title;
        public long duration;

        public AudioItem(String path, String fileName, long duration) {
            this.path     = path;
            this.title    = AppUtils.removeExtension(fileName);
            this.duration = duration;
        }
    }

    private final String name;
    private final String path;
    private final boolean isRoot;

    private long lastModified;

    private List<FolderItem> subFolders;
    private List<AudioItem> audioFiles;

    public FolderItem(String name, String path, boolean isRoot) {
        this.name = name;
        this.path = path;
        this.isRoot = isRoot;

        this.subFolders = new ArrayList<>();
        this.audioFiles = new ArrayList<>();
    }

    public String getName() {
        return this.name;
    }

    public String getPath() {
        return this.path;
    }

    public boolean getIsRoot() {
        return this.isRoot;
    }
    //========
    public List<FolderItem> getFolders() {
        return this.subFolders;
    }

    public void add(FolderItem folders) {
        this.subFolders.add(folders);
    }
    //========
    public List<AudioItem> getFiles() {
        return this.audioFiles;
    }

    public void addFiles(AudioItem audioItems) {
        this.audioFiles.add(audioItems);
    }

    //========
    public long getModified() {
        return this.lastModified;
    }

    public void setModified(long dataModified) {
        this.lastModified = dataModified;
    }

//    // Метод для сравнения папок
//    public boolean isSameFolder(FolderItem other) {
//        return other != null && this.folderId.equals(other.folderId);
//    }

    public boolean hasContent() {
        return !this.subFolders.isEmpty() || !this.audioFiles.isEmpty();
    }

//    public int totalAudioCount() {
//        int count = audioFiles.size();
//        for (FolderItem sub : subFolders) {
//            count += sub.totalAudioCount();
//        }
//        return count;
//    }

//    private static class Holder {
//        private static final FolderItem HOLDER_INSTANCE = new FolderItem();
//    }
//    public static FolderItem getInstance() {
//        return Holder.HOLDER_INSTANCE;
//    }
}
