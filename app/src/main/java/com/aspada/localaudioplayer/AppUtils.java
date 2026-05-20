package com.aspada.localaudioplayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /**
     * Вместо самого URL лучшим решением является использование его хэш-суммы
     * @return String
     */
    public static String generateKeyFromUrl(String input) {
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
