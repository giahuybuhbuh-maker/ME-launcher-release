package launcher;

import java.io.File;

public class Utils {

    private Utils() {
        // Lop tien ich static, khong cho khoi tao instance
    }

    public enum OS { WINDOWS, MAC, LINUX, UNKNOWN }

    public static OS getOperatingSystem() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) return OS.WINDOWS;
        if (osName.contains("mac")) return OS.MAC;
        if (osName.contains("nux") || osName.contains("nix")) return OS.LINUX;
        return OS.UNKNOWN;
    }

    public static File getWorkingDirectory() {
        return new File(System.getProperty("user.dir"));
    }

    /** Doi so byte thanh chuoi de doc, vd: 1536 -> "1.5 KB". Dung khi hien thi tien do tai file. */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
