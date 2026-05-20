package com.aspada.localaudioplayer;

public class PlaybackState {
    private final int trackIndex;
    private final long positionMs;
    private final long timestamp;

    public PlaybackState(int trackIndex, long positionMs) {
        this.trackIndex = trackIndex;
        this.positionMs = positionMs;
        this.timestamp  = System.currentTimeMillis();
    }

    public int getTrackIndex() {
        return this.trackIndex;
    }

    public long getPositionMs() {
        return this.positionMs;
    }

    public long getTimestamp() {
        return this.timestamp;
    }
}
