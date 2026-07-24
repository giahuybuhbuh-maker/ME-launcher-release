package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.MinecraftVersion;
import network.HttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Doc version manifest tu Mojang de lay danh sach phien ban, va doc file
 * JSON chi tiet cua tung phien ban khi can.
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

    /** Tai file JSON chi tiet cua 1 phien ban cu the (libraries, downloads, javaVersion...). */
    public JsonObject fetchVersionDetail(MinecraftVersion version) throws IOException, InterruptedException {
        String json = httpClient.getString(version.getUrl());
        return JsonParser.parseString(json).getAsJsonObject();
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
}
