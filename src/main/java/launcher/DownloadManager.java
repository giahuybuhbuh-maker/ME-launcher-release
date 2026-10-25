package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.MinecraftVersion;
import network.FileDownloader;
import network.HttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Tai va xac thuc toan bo file can de chay 1 phien ban Minecraft: client
 * jar, libraries (loc theo OS hien tai qua "rules"), va assets (am thanh,
 * texture, ngon ngu...). Dung FileManager de bo qua file da co san & dung
 * checksum, va network.FileDownloader de tai song song phan con thieu.
 *
 * Da bo sung ho tro "${arch}" (32/64-bit) trong classifiers cho cac phien
 * ban cu (1.13 tro xuong nhu 1.12.2, 1.8.9, 1.7.10...).
 */
public class DownloadManager {

    private final HttpClient httpClient = new HttpClient();
    private final FileDownloader fileDownloader = new FileDownloader(8);

    public record DownloadResult(int totalFiles, int downloadedFiles, int failedFiles, List<String> errors) {}

    public DownloadResult ensureVersionFiles(MinecraftVersion version, JsonObject detail,
                                              BiConsumer<Integer, Integer> onProgress)
            throws IOException, InterruptedException {

        List<FileDownloader.DownloadTask> tasks = new ArrayList<>();
        addClientJarTask(detail, version.getId(), tasks);
        addLibraryTasks(detail, tasks);

        Path assetIndexFile = downloadAssetIndex(detail);
        if (assetIndexFile != null) {
            addAssetTasks(assetIndexFile, tasks);
        }

        List<FileDownloader.DownloadTask> toDownload = tasks.stream()
                .filter(t -> !FileManager.isValid(t.destination(), t.expectedSha1(), t.expectedSize()))
                .toList();

        int total = tasks.size();
        int alreadyValid = total - toDownload.size();
        LauncherLogger.info("Phien ban " + version.getId() + ": " + total + " file, "
                + alreadyValid + " da co san, can tai " + toDownload.size() + " file.");

        List<String> errors = new ArrayList<>();
        int[] done = {alreadyValid};
        if (onProgress != null) onProgress.accept(done[0], total);

        fileDownloader.downloadAll(toDownload, (url, error) -> {
            synchronized (done) {
                done[0]++;
                if (error != null) errors.add(url + " -> " + error.getMessage());
                if (onProgress != null) onProgress.accept(done[0], total);
            }
        });

        return new DownloadResult(total, total - errors.size(), errors.size(), errors);
    }

    private void addClientJarTask(JsonObject detail, String versionId, List<FileDownloader.DownloadTask> tasks) {
        if (!detail.has("downloads")) return;
        JsonObject downloads = detail.getAsJsonObject("downloads");
        if (!downloads.has("client")) return;

        JsonObject client = downloads.getAsJsonObject("client");
        String url = client.get("url").getAsString();
        String sha1 = client.get("sha1").getAsString();
        long size = client.has("size") ? client.get("size").getAsLong() : 0;
        tasks.add(new FileDownloader.DownloadTask(url, FileManager.versionJar(versionId), sha1, size));
    }

    private void addLibraryTasks(JsonObject detail, List<FileDownloader.DownloadTask> tasks) {
        if (!detail.has("libraries")) return;

        String currentOs = switch (Utils.getOperatingSystem()) {
            case WINDOWS -> "windows";
            case MAC -> "osx";
            case LINUX -> "linux";
            default -> "linux";
        };
        String archBits = System.getProperty("os.arch", "").contains("64") ? "64" : "32";

        for (JsonElement libEl : detail.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();

            if (lib.has("rules") && !isRuleAllowed(lib.getAsJsonArray("rules"), currentOs)) {
                continue; // thu vien nay khong danh cho he dieu hanh hien tai
            }

            if (lib.has("downloads")) {
                // Kieu vanilla (Mojang): downloads.artifact co san path/url/sha1
                JsonObject downloads = lib.getAsJsonObject("downloads");

                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    String path = artifact.get("path").getAsString();
                    String url = artifact.get("url").getAsString();
                    String sha1 = artifact.get("sha1").getAsString();
                    long size = artifact.has("size") ? artifact.get("size").getAsLong() : 0;
                    tasks.add(new FileDownloader.DownloadTask(
                            url, FileManager.librariesDir().resolve(path), sha1, size));
                }

                // Kieu native cu (truoc ~1.19 va 1.13 tro xuong): file .dll/.so nam trong "classifiers"
                // Ho tro thay the bien ${arch} (64/32) cho cac ban 1.8.9, 1.7.10...
                if (lib.has("natives") && downloads.has("classifiers")) {
                    JsonObject natives = lib.getAsJsonObject("natives");
                    if (natives.has(currentOs)) {
                        String classifierKey = natives.get(currentOs).getAsString().replace("${arch}", archBits);
                        JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                        if (classifiers.has(classifierKey)) {
                            JsonObject nativeArtifact = classifiers.getAsJsonObject(classifierKey);
                            String path = nativeArtifact.get("path").getAsString();
                            String url = nativeArtifact.get("url").getAsString();
                            String sha1 = nativeArtifact.get("sha1").getAsString();
                            long size = nativeArtifact.has("size") ? nativeArtifact.get("size").getAsLong() : 0;
                            tasks.add(new FileDownloader.DownloadTask(
                                    url, FileManager.librariesDir().resolve(path), sha1, size));
                        }
                    }
                }
            } else if (lib.has("name")) {
                // Kieu Fabric/Maven hoac Forge/OptiFine cu: "name": "group:artifact:version"
                String relativePath = mavenCoordinateToPath(lib.get("name").getAsString());
                Path dest = FileManager.librariesDir().resolve(relativePath);
                if (lib.has("url")) {
                    String repoBaseUrl = lib.get("url").getAsString();
                    String fullUrl = repoBaseUrl + (repoBaseUrl.endsWith("/") ? "" : "/") + relativePath;
                    tasks.add(new FileDownloader.DownloadTask(fullUrl, dest, null, 0));
                } else if (!Files.exists(dest)) {
                    // Fallback ve kho thu vien chinh thuc cua Mojang neu khong chi dinh "url"
                    String fullUrl = "https://libraries.minecraft.net/" + relativePath;
                    tasks.add(new FileDownloader.DownloadTask(fullUrl, dest, null, 0));
                }
            }
        }
    }

    private String mavenCoordinateToPath(String coordinate) {
        String[] parts = coordinate.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private boolean isRuleAllowed(JsonArray rules, String currentOsName) {
        boolean allowed = false;
        for (JsonElement ruleEl : rules) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean matches = true;
            if (rule.has("os")) {
                JsonObject osObj = rule.getAsJsonObject("os");
                if (osObj.has("name")) {
                    matches = osObj.get("name").getAsString().equals(currentOsName);
                }
            }
            if (matches) {
                allowed = "allow".equals(action);
            }
        }
        return allowed;
    }

    private Path downloadAssetIndex(JsonObject detail) throws IOException, InterruptedException {
        if (!detail.has("assetIndex")) return null;
        JsonObject assetIndex = detail.getAsJsonObject("assetIndex");
        String id = assetIndex.get("id").getAsString();
        String url = assetIndex.get("url").getAsString();

        Path destination = FileManager.assetIndexesDir().resolve(id + ".json");
        if (!Files.exists(destination)) {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, httpClient.getString(url));
        }
        return destination;
    }

    private void addAssetTasks(Path assetIndexFile, List<FileDownloader.DownloadTask> tasks) throws IOException {
        JsonObject index = JsonParser.parseString(Files.readString(assetIndexFile)).getAsJsonObject();
        if (!index.has("objects")) return;

        JsonObject objects = index.getAsJsonObject("objects");
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String hashPrefix = hash.substring(0, 2);
            long size = obj.has("size") ? obj.get("size").getAsLong() : 0;

            String url = "https://resources.download.minecraft.net/" + hashPrefix + "/" + hash;
            Path destination = FileManager.assetObjectsDir().resolve(hashPrefix).resolve(hash);
            tasks.add(new FileDownloader.DownloadTask(url, destination, hash, size));
        }
    }
}
