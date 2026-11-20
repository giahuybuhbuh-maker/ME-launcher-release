package launcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.JavaRuntime;
import network.FileDownloader;
import network.HttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tu dong tai va quan ly JRE rieng cho tung phien ban Minecraft, luu trong
 * runtime/java/<component>/. Dua tren he thong "Java runtime manifest" cong
 * khai cua Mojang (co the tham khao them tai wiki.vg - Game_files), giong
 * co che tai assets: 1 manifest goc -> tro toi manifest rieng cho tung
 * OS/component -> danh sach hang tram file nho can tai.
 */
public class JavaManager {

    private static final String RUNTIME_INDEX_URL =
            "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private static final Path RUNTIME_DIR = Utils.getAppDataDir().resolve("runtime").resolve("java");

    private final HttpClient httpClient = new HttpClient();
    private final FileDownloader fileDownloader = new FileDownloader();

    /** Ten platform dung dinh dang cua Mojang trong all.json (vd "windows-x64"). */
    private String resolveMojangPlatformKey() {
        Utils.OS os = Utils.getOperatingSystem();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean is64 = arch.contains("64");

        return switch (os) {
            case WINDOWS -> is64 ? "windows-x64" : "windows-x86";
            case MAC -> arch.contains("aarch64") || arch.contains("arm") ? "mac-os-arm64" : "mac-os";
            case LINUX -> is64 ? "linux" : "linux-i386";
            default -> "linux";
        };
    }

    /**
     * Tren macOS, goi JRE cua Mojang dat file ben trong jre.bundle/Contents/Home/.
     * Tren Windows/Linux, file nam truc tiep o thu muc componentDir.
     */
    private Path resolveJavaHome(Path componentDir) {
        if (Utils.getOperatingSystem() == Utils.OS.MAC) {
            Path macBundleHome = componentDir.resolve("jre.bundle").resolve("Contents").resolve("Home");
            if (Files.isDirectory(macBundleHome)) {
                return macBundleHome;
            }
        }
        return componentDir;
    }

    private Path resolveJavaBinary(Path componentDir) {
        String javaBinaryRelative = Utils.getOperatingSystem() == Utils.OS.WINDOWS
                ? "bin/javaw.exe" : "bin/java";
        return resolveJavaHome(componentDir).resolve(javaBinaryRelative);
    }

    /**
     * Dam bao co san JRE cho component + majorVersion yeu cau. Neu da tai
     * roi (co file danh dau .ok) thi tra ve luon, khong tai lai.
     */
    public JavaRuntime ensureRuntime(String component, int majorVersion) throws IOException, InterruptedException {
        Path componentDir = RUNTIME_DIR.resolve(component);
        Path markerFile = componentDir.resolve(".ok");
        String platformKey = resolveMojangPlatformKey();

        JavaRuntime runtime = new JavaRuntime();
        runtime.setComponent(component);
        runtime.setMajorVersion(majorVersion);
        runtime.setPlatform(platformKey);

        Path existingBinary = resolveJavaBinary(componentDir);
        if (Files.exists(markerFile) && Files.exists(existingBinary)) {
            Utils.ensureExecutable(existingBinary);
            runtime.setPath(resolveJavaHome(componentDir).toAbsolutePath().toString());
            LauncherLogger.info("Da co san JRE '" + component + "', bo qua tai lai.");
            return runtime;
        }

        LauncherLogger.info("Chua co JRE '" + component + "' cho " + platformKey + ", bat dau tai tu Mojang...");
        downloadRuntime(component, platformKey, componentDir);

        Path downloadedBinary = resolveJavaBinary(componentDir);
        Utils.ensureExecutable(downloadedBinary);
        runtime.setPath(resolveJavaHome(componentDir).toAbsolutePath().toString());

        Files.createDirectories(componentDir);
        Files.writeString(markerFile, "ok");
        LauncherLogger.info("Da tai xong JRE '" + component + "'.");
        return runtime;
    }

    private void downloadRuntime(String component, String platformKey, Path destDir)
            throws IOException, InterruptedException {

        // Buoc 1: doc all.json de tim URL manifest rieng cho platform+component nay
        JsonObject allRuntimes = JsonParser.parseString(httpClient.getString(RUNTIME_INDEX_URL)).getAsJsonObject();
        if (!allRuntimes.has(platformKey)) {
            throw new IOException("Mojang khong ho tro tu dong tai JRE cho platform: " + platformKey);
        }
        JsonObject platformObj = allRuntimes.getAsJsonObject(platformKey);
        if (!platformObj.has(component) || platformObj.getAsJsonArray(component).isEmpty()) {
            throw new IOException("Khong tim thay component JRE '" + component + "' cho " + platformKey);
        }
        JsonObject manifestRef = platformObj.getAsJsonArray(component)
                .get(0).getAsJsonObject()
                .getAsJsonObject("manifest");
        String manifestUrl = manifestRef.get("url").getAsString();

        // Buoc 2: doc manifest rieng do de lay danh sach TUNG file can tai
        JsonObject fileManifest = JsonParser.parseString(httpClient.getString(manifestUrl)).getAsJsonObject();
        JsonObject files = fileManifest.getAsJsonObject("files");

        List<FileDownloader.DownloadTask> tasks = new ArrayList<>();
        List<Path> executablePaths = new ArrayList<>();
        List<Map.Entry<Path, String>> symlinks = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            JsonObject fileObj = entry.getValue().getAsJsonObject();
            String type = fileObj.has("type") ? fileObj.get("type").getAsString() : "file";

            if ("link".equals(type) && fileObj.has("target")) {
                symlinks.add(new AbstractMap.SimpleEntry<>(destDir.resolve(relativePath), fileObj.get("target").getAsString()));
                continue;
            }
            if (!"file".equals(type)) continue; // bo qua "directory", chi tai file that su

            JsonObject rawDownload = fileObj.getAsJsonObject("downloads").getAsJsonObject("raw");
            String url = rawDownload.get("url").getAsString();
            String sha1 = rawDownload.get("sha1").getAsString();
            long size = rawDownload.has("size") ? rawDownload.get("size").getAsLong() : 0;

            Path destination = destDir.resolve(relativePath);
            tasks.add(new FileDownloader.DownloadTask(url, destination, sha1, size));

            if (fileObj.has("executable") && fileObj.get("executable").getAsBoolean()) {
                executablePaths.add(destination);
            }
        }

        LauncherLogger.info("JRE '" + component + "' co " + tasks.size() + " file can tai...");

        List<String> failed = new ArrayList<>();
        fileDownloader.downloadAll(tasks, (url, error) -> {
            if (error != null) failed.add(url + " -> " + error.getMessage());
        });

        if (!failed.isEmpty()) {
            throw new IOException("Tai JRE that bai " + failed.size() + " file, vd: " + failed.get(0));
        }

        // Tao symlink cho thu vien dong tren Linux/macOS (vd libjli.dylib, libjvm.so)
        if (Utils.getOperatingSystem() != Utils.OS.WINDOWS) {
            for (Map.Entry<Path, String> link : symlinks) {
                try {
                    Path linkPath = link.getKey();
                    Path targetPath = Path.of(link.getValue());
                    Files.createDirectories(linkPath.getParent());
                    Files.deleteIfExists(linkPath);
                    Files.createSymbolicLink(linkPath, targetPath);
                } catch (Exception e) {
                    LauncherLogger.warn("Khong the tao symlink " + link.getKey() + ": " + e.getMessage());
                }
            }
        }

        // Tren Linux/Mac, cac file thuc thi (java, javaw...) can duoc cap quyen +x,
        // vi qua trinh tai file thuong khong tu giu lai quyen nay.
        for (Path exe : executablePaths) {
            boolean ok = exe.toFile().setExecutable(true, false);
            if (!ok) LauncherLogger.warn("Khong the cap quyen thuc thi cho: " + exe);
        }
    }
}