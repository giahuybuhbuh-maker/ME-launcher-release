package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.MinecraftVersion;
import network.HttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Doc version manifest tu Mojang de lay danh sach phien ban, va doc file
 * JSON chi tiet cua tung phien ban khi can. Ngoai ra con nhan dien duoc
 * cac phien ban CAI THU CONG (OptiFine/Forge/NeoForge...) neu nguoi dung
 * tu chay trinh cai dat GOC cua ho, tro vao thu muc game/ cua launcher nay.
 *
 * Luu y: fetchVersionDetail() tra ve JsonObject THO (khong ep vao 1 model
 * cung nhac), vi cau truc file nay rat phuc tap (libraries, downloads,
 * assetIndex...) va se duoc Phase 3 (DownloadManager) / Phase 4
 * (MinecraftLauncher) doc tiep, moi noi chi lay dung phan minh can.
 */
public class VersionManager {

    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final HttpClient httpClient = new HttpClient();
    private List<MinecraftVersion> cachedVersions;

    /** Tai danh sach TAT CA phien ban (release + snapshot + beta/alpha cu) tu Mojang. Co cache. */
    public List<MinecraftVersion> fetchVersionList() throws IOException, InterruptedException {
        if (cachedVersions != null) return cachedVersions;

        String json = httpClient.getString(MANIFEST_URL);
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

        LauncherLogger.info("Da tai " + versions.size() + " phien ban tu version manifest.");
        cachedVersions = versions;
        return versions;
    }

    /** Chi lay cac ban "release" chinh thuc (an bot snapshot/beta/alpha cho gon UI). */
    public List<MinecraftVersion> fetchReleaseVersions() throws IOException, InterruptedException {
        return fetchVersionList().stream().filter(MinecraftVersion::isRelease).toList();
    }

    /**
     * Quet game/versions/ tim cac thu muc co file <id>.json nhung KHONG co
     * trong manifest chinh thuc cua Mojang - tuc la phien ban da duoc cai
     * THU CONG (vd: chay trinh cai dat cua OptiFine/Forge/NeoForge, tro
     * vao thu muc game/ cua launcher nay). Cac phien ban nay khong co URL
     * online, se duoc doc thang tu file khi can (xem fetchVersionDetail).
     */
    public List<MinecraftVersion> scanCustomLocalVersions() throws IOException, InterruptedException {
        Set<String> knownIds = new HashSet<>();
        for (MinecraftVersion v : fetchVersionList()) knownIds.add(v.getId());

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
     * Tai file JSON chi tiet cua 1 phien ban. Neu version.getUrl() rong
     * (phien ban cai thu cong - xem scanCustomLocalVersions), doc thang
     * tu file cuc bo thay vi goi mang; neu file do co "inheritsFrom"
     * (OptiFine/Forge/NeoForge deu lam vay), tu dong gop voi ban vanilla goc.
     */
    public JsonObject fetchVersionDetail(MinecraftVersion version) throws IOException, InterruptedException {
        if (version.getUrl() == null || version.getUrl().isBlank()) {
            return readLocalVersionDetail(version.getId());
        }
        String json = httpClient.getString(version.getUrl());
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonObject readLocalVersionDetail(String id) throws IOException, InterruptedException {
        Path dir = FileManager.versionsDir().resolve(id);
        Path jsonFile = findVersionJsonFile(dir, id);
        if (jsonFile == null) {
            throw new IOException("Khong tim thay file profile cuc bo trong: " + dir);
        }
        JsonObject localDetail = JsonParser.parseString(Files.readString(jsonFile)).getAsJsonObject();

        if (!localDetail.has("inheritsFrom")) {
            return localDetail;
        }

        String parentId = localDetail.get("inheritsFrom").getAsString();
        MinecraftVersion parentVersion = fetchVersionList().stream()
                .filter(v -> v.getId().equals(parentId))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Phien ban cuc bo '" + id + "' ke thua tu '" + parentId + "' nhung khong tim thay ban nay."));

        JsonObject parentDetail = fetchVersionDetail(parentVersion);
        return mergeWithParent(parentDetail, localDetail);
    }

   /** Gop 1 profile cai thu cong (co "inheritsFrom") voi ban vanilla goc - ho tro ca 1.13+ va 1.12.2 tro xuong. */
    private JsonObject mergeWithParent(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();

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

    /**
     * Tim file JSON profile cua 1 phien ban ben trong thu muc cua no. Uu
     * tien file dat ten TRUNG voi thu muc (chuan Mojang/Fabric), nhung neu
     * khong co thi CHAP NHAN bat ky file .json nao khac trong thu muc do -
     * vi 1 so mod loader (Forge la vi du da gap) co the dat ten file .json
     * KHONG trung voi ten thu muc, khien cach kiem tra cu (doi hoi trung
     * ten tuyet doi) bo qua am tham ca thu muc do, khong bao loi gi ca.
     */
    private Path findVersionJsonFile(Path versionDir, String folderName) throws IOException {
        if (!Files.isDirectory(versionDir)) return null;

        Path exactMatch = versionDir.resolve(folderName + ".json");
        if (Files.exists(exactMatch)) return exactMatch;

        try (var stream = Files.list(versionDir)) {
            return stream.filter(p -> p.toString().endsWith(".json")).findFirst().orElse(null);
        }
    }
}
