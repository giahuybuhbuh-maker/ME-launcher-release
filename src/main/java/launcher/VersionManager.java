package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.LauncherConfig;
import model.MinecraftVersion;
import network.HttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Doc version manifest tu Mojang de lay danh sach phien ban, va doc file
 * JSON chi tiet cua tung phien ban khi can. Ngoai ra con nhan dien duoc
 * cac phien ban CAI THU CONG (OptiFine/Forge/NeoForge...) neu nguoi dung
 * tu chay trinh cai dat GOC cua ho, tro vao thu muc game/ cua launcher nay.
 */
public class VersionManager {

    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final HttpClient httpClient = new HttpClient();
    private List<MinecraftVersion> cachedVersions;
    private boolean lastFetchOffline = false;

    public boolean isLastFetchOffline() {
        return lastFetchOffline;
    }

    private Path getManifestCacheFile() {
        return FileManager.gameDir().resolve("version_manifest_v2.json");
    }

    /** Tai danh sach TAT CA phien ban (release + snapshot + beta/alpha cu) tu Mojang. Co cache & ho tro Offline. */
    public List<MinecraftVersion> fetchVersionList() throws IOException, InterruptedException {
        if (cachedVersions != null && !lastFetchOffline) return cachedVersions;

        try {
            String json = httpClient.getString(MANIFEST_URL);
            lastFetchOffline = false;

            // Lưu cache manifest xuống ổ cứng để dùng khi mất mạng
            try {
                Path cacheFile = getManifestCacheFile();
                Files.createDirectories(cacheFile.getParent());
                Files.writeString(cacheFile, json, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}

            List<MinecraftVersion> versions = parseManifestJson(json);
            LauncherLogger.info("Da tai " + versions.size() + " phien ban tu version manifest.");
            cachedVersions = versions;
            return versions;
        } catch (Exception networkEx) {
            lastFetchOffline = true;
            LauncherLogger.warn("Khong co ket noi mang toi Mojang, chuyen sang quet cac phien ban da cai tren may: "
                    + networkEx.getMessage());

            List<MinecraftVersion> installedOffline = scanInstalledVersionsForOffline();
            cachedVersions = installedOffline;
            return installedOffline;
        }
    }

    private List<MinecraftVersion> parseManifestJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray versionsArray = root.getAsJsonArray("versions");

        List<MinecraftVersion> versions = new ArrayList<>();
        for (var element : versionsArray) {
            JsonObject obj = element.getAsJsonObject();
            MinecraftVersion version = new MinecraftVersion();
            version.setId(obj.get("id").getAsString());
            version.setType(obj.get("type").getAsString());
            version.setUrl(obj.get("url").getAsString());
            version.setTime(obj.has("time") ? obj.get("time").getAsString() : null);
            version.setReleaseTime(obj.has("releaseTime") ? obj.get("releaseTime").getAsString() : null);
            version.setSha1(obj.has("sha1") ? obj.get("sha1").getAsString() : null);
            version.setComplianceLevel(obj.has("complianceLevel") ? obj.get("complianceLevel").getAsInt() : 0);
            versions.add(version);
        }
        return versions;
    }

    /**
     * Khi mất mạng (Offline), quét thư mục game/versions/ để lấy tất cả các phiên bản
     * đã có sẵn file .json và .jar trên máy giúp người dùng chơi bình thường.
     */
    public List<MinecraftVersion> scanInstalledVersionsForOffline() {
        List<MinecraftVersion> installed = new ArrayList<>();
        Path versionsDir = FileManager.versionsDir();
        if (!Files.exists(versionsDir)) return installed;

        try (var stream = Files.list(versionsDir)) {
            List<Path> dirs = stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .toList();

            for (Path dir : dirs) {
                String id = dir.getFileName().toString();
                Path jsonFile = findVersionJsonFile(dir, id);
                if (jsonFile == null) continue;

                try {
                    JsonObject obj = JsonParser.parseString(Files.readString(jsonFile, StandardCharsets.UTF_8)).getAsJsonObject();
                    boolean hasOwnJar = Files.exists(FileManager.versionJar(id));
                    boolean hasParentJar = false;

                    if (obj.has("inheritsFrom")) {
                        String parentId = obj.get("inheritsFrom").getAsString();
                        hasParentJar = Files.exists(FileManager.versionJar(parentId));
                    }

                    // Chỉ nạp những phiên bản đã tải đủ jar (hoặc bản mod có jar gốc)
                    if (!hasOwnJar && !hasParentJar) continue;

                    MinecraftVersion version = new MinecraftVersion();
                    version.setId(id);
                    if (obj.has("inheritsFrom")) {
                        version.setType("custom-local");
                    } else if (obj.has("type")) {
                        version.setType(obj.get("type").getAsString());
                    } else {
                        version.setType("release");
                    }
                    version.setUrl(""); // Đánh dấu đọc cục bộ
                    installed.add(version);
                } catch (Exception e) {
                    LauncherLogger.warn("Bo qua thu muc version hong khi quet Offline: " + id);
                }
            }
        } catch (IOException e) {
            LauncherLogger.warn("Loi khi quet thu muc versions Offline: " + e.getMessage());
        }

        LauncherLogger.info("Tim thay " + installed.size() + " phien ban da cai san tren may (Offline mode).");
        return installed;
    }

    /** Chi lay cac ban "release" chinh thuc (an bot snapshot/beta/alpha cho gon UI). */
    public List<MinecraftVersion> fetchReleaseVersions() throws IOException, InterruptedException {
        return fetchVersionList().stream().filter(MinecraftVersion::isRelease).toList();
    }

    /**
     * Lọc danh sách phiên bản theo các tùy chọn trong Cài đặt:
     * - Hiện Snapshots (showSnapshots)
     * - Hiện Beta (showBeta)
     * - Hiện Alpha (showAlpha)
     * - Hiện bản cũ <= 1.13 (showOldReleases)
     */
    public List<MinecraftVersion> fetchFilteredVersions(LauncherConfig config) throws IOException, InterruptedException {
        List<MinecraftVersion> all = fetchVersionList();
        if (config == null) {
            return fetchReleaseVersions();
        }

        // Nếu đang ở chế độ mất mạng (Offline), trả về các bản đã cài theo bộ lọc
        List<MinecraftVersion> filtered = new ArrayList<>();
        for (MinecraftVersion v : all) {
            String type = v.getType() == null ? "release" : v.getType().toLowerCase();

            switch (type) {
                case "release" -> {
                    if (!config.isShowOldReleases() && isOldReleaseVersion(v.getId())) {
                        continue;
                    }
                    filtered.add(v);
                }
                case "snapshot" -> {
                    if (config.isShowSnapshots()) {
                        filtered.add(v);
                    }
                }
                case "old_beta" -> {
                    if (config.isShowBeta()) {
                        filtered.add(v);
                    }
                }
                case "old_alpha" -> {
                    if (config.isShowAlpha()) {
                        filtered.add(v);
                    }
                }
                case "custom-local" -> {
                    if (config.isShowModified()) {
                        filtered.add(v);
                    }
                }
                default -> {
                    if (config.isShowSnapshots()) {
                        filtered.add(v);
                    }
                }
            }
        }
        return filtered;
    }

    /**
     * Kiểm tra phiên bản chính thức có phải bản cũ (<= 1.13.2) hay không.
     * Ví dụ: 1.13.2, 1.12.2, 1.8.9, 1.7.10, 1.0 -> true.
     * Các bản 1.14+, 1.20.1, 1.21.4, 26.3 -> false.
     */
    private boolean isOldReleaseVersion(String id) {
        if (id == null || id.isBlank()) return false;
        String clean = id.split("-")[0].trim();
        if (clean.startsWith("1.")) {
            String[] parts = clean.split("\\.");
            if (parts.length >= 2) {
                try {
                    int minor = Integer.parseInt(parts[1]);
                    return minor <= 13;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Quet game/versions/ tim cac thu muc co file <id>.json nhung KHONG co
     * trong manifest chinh thuc cua Mojang - tuc la phien ban da duoc cai
     * THU CONG (vd: chay trinh cai dat cua OptiFine/Forge/NeoForge).
     */
    public List<MinecraftVersion> scanCustomLocalVersions() throws IOException, InterruptedException {
        if (lastFetchOffline) {
            // Khi Offline, scanInstalledVersionsForOffline() đã lấy cả bản custom-local rồi
            return List.of();
        }

        Set<String> knownIds = new HashSet<>();
        for (MinecraftVersion v : fetchVersionList()) knownIds.add(v.getId());

        // Bổ sung các ID từ file manifest cache (nếu có) để không nhận nhầm bản Vanilla là custom-local
        Path cacheFile = getManifestCacheFile();
        if (Files.exists(cacheFile)) {
            try {
                for (MinecraftVersion v : parseManifestJson(Files.readString(cacheFile, StandardCharsets.UTF_8))) {
                    knownIds.add(v.getId());
                }
            } catch (Exception ignored) {}
        }

        List<MinecraftVersion> customVersions = new ArrayList<>();
        Path versionsDir = FileManager.versionsDir();
        if (!Files.exists(versionsDir)) return customVersions;

        try (var stream = Files.list(versionsDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String id = dir.getFileName().toString();
                if (knownIds.contains(id)) continue;
                if (findVersionJsonFile(dir, id) == null) continue;

                MinecraftVersion version = new MinecraftVersion();
                version.setId(id);
                version.setType("custom-local");
                version.setUrl(""); // rong = doc file cuc bo, khong goi mang (xem fetchVersionDetail)
                customVersions.add(version);
                LauncherLogger.info("Tim thay phien ban cai thu cong: " + id);
            }
        }
        return customVersions;
    }

    /**
     * Tai file JSON chi tiet cua 1 phien ban.
     * - Neu da co san file JSON tren o cung va khong co mang (hoac url rong), doc thang tu o cung.
     * - Khi tai tu mang thanh cong, tu dong luu <id>.json xuong game/versions/<id>/<id>.json de danh cho luc Offline.
     */
    public JsonObject fetchVersionDetail(MinecraftVersion version) throws IOException, InterruptedException {
        Path localDir = FileManager.versionsDir().resolve(version.getId());
        Path localJsonFile = findVersionJsonFile(localDir, version.getId());

        if (version.getUrl() == null || version.getUrl().isBlank()) {
            return readLocalVersionDetail(version.getId());
        }

        try {
            String json = httpClient.getString(version.getUrl());
            // Lưu ngay file JSON xuống game/versions/<id>/<id>.json để lần sau mất mạng vẫn chơi được
            try {
                Files.createDirectories(localDir);
                Path targetJson = localDir.resolve(version.getId() + ".json");
                Files.writeString(targetJson, json, StandardCharsets.UTF_8);
            } catch (Exception e) {
                LauncherLogger.warn("Khong luu duoc cache JSON cho " + version.getId() + ": " + e.getMessage());
            }
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception networkEx) {
            if (localJsonFile != null && Files.exists(localJsonFile)) {
                LauncherLogger.info("Dang doc file JSON cuc bo (Offline) cho phien ban: " + version.getId());
                return readLocalVersionDetail(version.getId());
            }
            throw new IOException("Khong co mang va phien ban '" + version.getId() + "' chua duoc tai ve may.", networkEx);
        }
    }

    private JsonObject readLocalVersionDetail(String id) throws IOException, InterruptedException {
        Path dir = FileManager.versionsDir().resolve(id);
        Path jsonFile = findVersionJsonFile(dir, id);
        if (jsonFile == null) {
            throw new IOException("Khong tim thay file profile cuc bo trong: " + dir);
        }
        JsonObject localDetail = JsonParser.parseString(Files.readString(jsonFile, StandardCharsets.UTF_8)).getAsJsonObject();

        if (!localDetail.has("inheritsFrom")) {
            return localDetail;
        }

        String parentId = localDetail.get("inheritsFrom").getAsString();

        // Ưu tiên tìm bản gốc (parentId) ngay trên ổ cứng trước để chạy được cả khi mất mạng
        Path parentDir = FileManager.versionsDir().resolve(parentId);
        Path parentLocalJson = findVersionJsonFile(parentDir, parentId);
        if (parentLocalJson != null && Files.exists(parentLocalJson)) {
            JsonObject parentDetail = readLocalVersionDetail(parentId);
            return mergeWithParent(parentDetail, localDetail);
        }

        MinecraftVersion parentVersion = fetchVersionList().stream()
                .filter(v -> v.getId().equals(parentId))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Phien ban cuc bo '" + id + "' ke thua tu '" + parentId + "' nhung khong tim thay ban nay tren may."));

        JsonObject parentDetail = fetchVersionDetail(parentVersion);
        return mergeWithParent(parentDetail, localDetail);
    }

    /** Gop 1 profile cai thu cong (co "inheritsFrom") voi ban vanilla goc - ho tro ca 1.13+ va 1.12.2 tro xuong. */
    private JsonObject mergeWithParent(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();

        // Giữ lại thông tin inheritsFrom để MinecraftLauncher tìm được file .jar gốc khi cần
        if (child.has("inheritsFrom")) {
            merged.addProperty("inheritsFrom", child.get("inheritsFrom").getAsString());
        }

        if (child.has("mainClass")) {
            merged.addProperty("mainClass", child.get("mainClass").getAsString());
        }

        // Ho tro Forge / OptiFine cho cac phien ban 1.12.2, 1.8.9, 1.7.10...
        if (child.has("minecraftArguments")) {
            merged.addProperty("minecraftArguments", child.get("minecraftArguments").getAsString());
        }

        if (child.has("libraries")) {
            JsonArray mergedLibs = merged.has("libraries") ? merged.getAsJsonArray("libraries") : new JsonArray();
            for (JsonElement lib : child.getAsJsonArray("libraries")) {
                mergedLibs.add(lib);
            }
            merged.add("libraries", mergedLibs);
        }

        if (child.has("arguments")) {
            JsonObject childArgs = child.getAsJsonObject("arguments");
            JsonObject mergedArgs = merged.has("arguments") ? merged.getAsJsonObject("arguments") : new JsonObject();

            for (String key : List.of("game", "jvm")) {
                if (!childArgs.has(key)) continue;
                JsonArray mergedArr = mergedArgs.has(key) ? mergedArgs.getAsJsonArray(key) : new JsonArray();
                for (JsonElement arg : childArgs.getAsJsonArray(key)) {
                    mergedArr.add(arg);
                }
                mergedArgs.add(key, mergedArr);
            }
            merged.add("arguments", mergedArgs);
        }

        merged.addProperty("id", child.has("id") ? child.get("id").getAsString() : parent.get("id").getAsString());
        return merged;
    }

    /** Ten component JRE ma phien ban nay yeu cau, vd "java-runtime-gamma". */
    public String getRequiredJavaComponent(JsonObject versionDetail) {
        if (versionDetail.has("javaVersion")) {
            JsonObject javaVersion = versionDetail.getAsJsonObject("javaVersion");
            if (javaVersion.has("component")) {
                return javaVersion.get("component").getAsString();
            }
        }
        // Cac ban Minecraft rat cu khong co field javaVersion, Mojang quy uoc dung jre-legacy
        return "jre-legacy";
    }

    /** So major cua Java ma phien ban nay yeu cau, vd 21. */
    public int getRequiredJavaMajorVersion(JsonObject versionDetail) {
        if (versionDetail.has("javaVersion")) {
            JsonObject javaVersion = versionDetail.getAsJsonObject("javaVersion");
            if (javaVersion.has("majorVersion")) {
                return javaVersion.get("majorVersion").getAsInt();
            }
        }
        return 8;
    }

    private Path findVersionJsonFile(Path versionDir, String folderName) throws IOException {
        if (!Files.isDirectory(versionDir)) return null;

        Path exactMatch = versionDir.resolve(folderName + ".json");
        if (Files.exists(exactMatch)) return exactMatch;

        try (var stream = Files.list(versionDir)) {
            return stream.filter(p -> p.toString().endsWith(".json")).findFirst().orElse(null);
        }
    }
}