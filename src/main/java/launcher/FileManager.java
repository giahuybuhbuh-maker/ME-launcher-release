package launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Quan ly file cuc bo trong game/ - cung cap duong dan chuan (giong cau
 * truc .minecraft that: versions/, libraries/, assets/objects/...) va
 * kiem tra 1 file da co san & dung checksum chua, de DownloadManager
 * KHONG tai lai nhung gi da co san moi lan bam PLAY.
 */
public class FileManager {

    private static final Path GAME_DIR = Path.of("game");

    public static Path gameDir() { return GAME_DIR; }
    public static Path versionsDir() { return GAME_DIR.resolve("versions"); }
    public static Path librariesDir() { return GAME_DIR.resolve("libraries"); }
    public static Path assetsDir() { return GAME_DIR.resolve("assets"); }
    public static Path assetObjectsDir() { return assetsDir().resolve("objects"); }
    public static Path assetIndexesDir() { return assetsDir().resolve("indexes"); }
    public static Path modsDir() { return GAME_DIR.resolve("mods"); }

    public static Path versionJar(String versionId) {
        return versionsDir().resolve(versionId).resolve(versionId + ".jar");
    }

    public static Path versionJson(String versionId) {
        return versionsDir().resolve(versionId).resolve(versionId + ".json");
    }

    /**
     * True neu file da ton tai VA (dua tren nhung gi biet duoc) co ve hop
     * le, khong can tai lai.
     *
     * Uu tien kiem tra bang KICH THUOC file (gan nhu tuc thi, chi 1 lenh
     * stat) thay vi hash lai toan bo noi dung - voi vai nghin file asset,
     * hash lai het moi lan bam PLAY co the ton vai PHUT du khong co gi
     * thay doi. SHA-1 van duoc xac thuc ngay sau khi TAI file (trong
     * Downloader), o day chi can biet "co can tai lai khong".
     */
    public static boolean isValid(Path file, String expectedSha1, long expectedSize) {
        if (!Files.exists(file)) return false;

        if (expectedSize > 0) {
            try {
                return Files.size(file) == expectedSize;
            } catch (IOException e) {
                return false;
            }
        }

        // Khong biet truoc kich thuoc (vd thu vien kieu Fabric/Maven) - danh
        // gia bang sha1 neu co, hoac coi la hop le khi file da ton tai.
        if (expectedSha1 == null || expectedSha1.isBlank()) return true;
        return hashMatches(file, expectedSha1);
    }

    private static boolean hashMatches(Path file, String expectedSha1) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            return actual.equalsIgnoreCase(expectedSha1);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    /** Tong dung luong da dung trong game/ (byte) - huu ich de hien thi cho nguoi dung sau nay. */
    public static long calculateGameDirSize() throws IOException {
        if (!Files.exists(GAME_DIR)) return 0;
        try (var stream = Files.walk(GAME_DIR)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0L; }
                    })
                    .sum();
        }
    }
}
