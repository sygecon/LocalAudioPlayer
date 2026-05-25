package com.aspada.localaudioplayer;

import java.time.Duration;

public class AppUtils {
    public AppUtils() {}

    public static String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    // Преобразовать длительность в миллисекундах в формат HH:MM:SS
    public static String formatDuration(long millis) {
        if (millis < 1) {
            return "0:00";
        }
        Duration duration = Duration.ofMillis(millis);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart(); // Минуты, оставшиеся после часов
        long seconds = duration.toSecondsPart(); // Секунды, оставшиеся после минут

        if (hours < 1) {
            return String.format("%02d:%02d", minutes, seconds);
        }
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    //    public String getMimeType() {
//        File file = new File(this.path);
//        MimeTypeMap  mimeMap = MimeTypeMap.getSingleton();
//        String = file.getName();
//            var lastDotIndex = name.lastIndexOf(".");
//            if (lastDotIndex >= 0 && lastDotIndex + 1 < name.length()) {
//                name = name.substring(name.lastIndexOf(".") + 1);
//            }
//            return mimeMap.getMimeTypeFromExtension(name);
//    }
}
