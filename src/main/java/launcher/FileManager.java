package launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Quan ly file cuc bo trong game/ - cung cap duong dan chuan (giong cau
 * truc .minecraft that: versions/, libraries/, assets/objects/...) va
 * kiem tra 1 file da co san & dung checksum chua, de DownloadManager
 * KHONG tai lai nhung gi da co san moi lan bam PLAY.
 */
public class FileManager {

    /**
     * Thu muc game/ - mac dinh la "game" (tuong doi voi noi chay app), nhung
     * nguoi dung co the doi sang thu muc bat ky qua Cai dat (vd de qua o
     * dia khac). Doc lai tu ConfigManager moi lan goi (khong cache static)
     * de luon phan anh dung lua chon hien tai, ke ca khi vua doi trong luc
     * app dang chay.
     */
    public static Path gameDir() {
        String custom = ConfigManager.get().getGameDirPath();
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        return Path.of("game");
    }

    public static Path versionsDir() { return gameDir().resolve("versions"); }
    public static Path librariesDir() { return gameDir().resolve("libraries"); }
    public static Path assetsDir() { return gameDir().resolve("assets"); }
    public static Path assetObjectsDir() { return assetsDir().resolve("objects"); }
    public static Path assetIndexesDir() { return assetsDir().resolve("indexes"); }
    public static Path modsDir() { return gameDir().resolve("mods"); }

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
        Path dir = gameDir();
        if (!Files.exists(dir)) return 0;
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0L; }
                    })
                    .sum();
        }
    }

    /**
     * Tao san 2 file "danh dau" trong game/ NEU CHUA CO:
     * launcher_profiles.json va launcher_profiles_microsoft_store.json.
     *
     * Trinh cai dat cua Forge/NeoForge (va co the ca OptiFine) kiem tra
     * su TON TAI cua 1 trong 2 file nay truoc khi chiu cai vao 1 thu muc,
     * coi day la dau hieu "da co 1 launcher that (Mojang/Microsoft) chay
     * qua roi" - neu khong thay se bao loi "missing a launcher profile".
     * Launcher nay dung co che dang nhap kieu Microsoft nen tao ca 2 ten
     * file de chac chan, khong biet truoc ban cai dat kiem tra ten nao.
     *
     * Chi tao khi CHUA CO san - khong ghi de/pha hong file that (vd neu
     * nguoi dung tung dung .minecraft that va copy du lieu qua).
     */
    public static void ensureLauncherProfilesExist() {
        String minimalJson = """
                {
                  "profiles": {
                    "me-launcher": {
                      "name": "ME Launcher",
                      "type": "custom",
                      "created": "2026-01-01T00:00:00.000Z",
                      "lastUsed": "2026-01-01T00:00:00.000Z",
                      "icon": "Furnace",
                      "lastVersionId": "latest-release"
                    }
                  },
                  "settings": {
                    "enableSnapshots": false,
                    "enableAdvanced": false,
                    "keepLauncherOpen": false,
                    "profileSorting": "ByLastPlayed",
                    "showGameLog": false
                  },
                  "version": 3
                }
                """;

        for (String fileName : List.of("launcher_profiles.json", "launcher_profiles_microsoft_store.json")) {
            Path target = gameDir().resolve(fileName);
            try {
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, minimalJson);
                    LauncherLogger.info("Da tao file danh dau " + fileName + " (de Forge/NeoForge installer nhan dien thu muc).");
                } else {
                    LauncherLogger.info("File danh dau " + fileName + " da co san tu truoc, khong tao lai.");
                }
            } catch (IOException e) {
                LauncherLogger.warn("Khong tao duoc " + fileName + ": " + e.getMessage());
            }
        }
    }
}
