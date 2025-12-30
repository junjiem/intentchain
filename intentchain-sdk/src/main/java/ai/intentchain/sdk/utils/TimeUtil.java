package ai.intentchain.sdk.utils;

/**
 *
 */
public class TimeUtil {
    private TimeUtil() {
    }

    public static String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        }
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
            minutes = minutes % 60;
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
            seconds = seconds % 60;
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }
        return sb.toString().trim();
    }
}
