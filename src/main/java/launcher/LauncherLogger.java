package launcher;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log rieng cua LAUNCHER (khac voi game/logs/ la log cua chinh Minecraft
 * khi game duoc chay len).
 */
public class LauncherLogger {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Path LOG_DIR = Utils.getAppDataDir().resolve("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("launcher.log");

    static {
        try {
            Files.createDirectories(LOG_DIR);
        } catch (IOException e) {
            System.err.println("Khong the tao thu muc logs/: " + e.getMessage());
        }
    }

    public static Path getLogDir() {
        return LOG_DIR;
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message + (t != null ? " -> " + t : ""));
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            for (String traceLine : sw.toString().split("\\R")) {
                log("ERROR", "  " + traceLine);
            }
        }
        String reason = message + (t != null && t.getMessage() != null ? " (" + t.getMessage() + ")" : "");
        ConsoleWindow.showOnError(reason);
    }

    private static synchronized void log(String level, String message) {
        String line = String.format("[%s] [%s] %s",
                LocalDateTime.now().format(TIME_FORMAT), level, message);

        System.out.println(line);
        ConsoleWindow.appendLine(line);

        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE.toFile(), true))) {
            writer.println(line);
        } catch (IOException e) {
            System.err.println("Khong the ghi log ra file: " + e.getMessage());
        }
    }
}