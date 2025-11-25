package mine;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class MineLogger {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Prevent instantiation
    private MineLogger() {}

    public static void log(String component, String message) {
        String time = LocalTime.now().format(TIME_FMT);
        String thread = Thread.currentThread().getName();

        // Uniform format：[time][thread][component] message
        System.out.printf("[%s][%s][%s] %s%n", time, thread, component, message);
    }
}
