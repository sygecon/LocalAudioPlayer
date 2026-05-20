package com.aspada.localaudioplayer;

public class AudioItem {
    private final String path;
    private final String title;
    private final long duration;

    public AudioItem(String path, String fileName, long duration) {
        this.path     = path;
        this.title    = AppUtils.removeExtension(fileName);
        this.duration = duration;
    }

    public String getUrl() {
        return this.path;
    }
    public String getTitle() {
        return this.title;
    }
    public long getDuration() {
        return this.duration;
    }
}
