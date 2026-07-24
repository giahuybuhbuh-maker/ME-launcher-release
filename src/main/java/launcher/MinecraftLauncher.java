package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.JavaRuntime;
import model.MinecraftVersion;
import model.UserProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Ghep tat ca cac phan tu Phase 1-3 lai thanh 1 lenh java hoan chinh va
 * chay Minecraft that qua ProcessManager: classpath (libraries + client
 * jar), tham so JVM va tham so game (doc thang tu "arguments" trong JSON
 * chi tiet phien ban, thay the cac ${placeholder}), va giai nen thu vien
 * native (LWJGL...) ra 1 thu muc rieng truoc khi chay.
 *
 * CHI HO TRO dinh dang "arguments" (JSON) dung tu Minecraft 1.13 tro di.
 * Ban cu hon dung "minecraftArguments" dang chuoi CHUA duoc ho tro.
 *
 * QUAN TRONG: ham launch() can 1 UserProfile co accessToken HOP LE. Lop
 * nay khong tu tao tai khoan hay token gia - Phase 5 (dang nhap Microsoft
 * that) moi la noi tao ra UserProfile de truyen vao day.
 */
public class MinecraftLauncher {

    private final ProcessManager processManager = new ProcessManager();

    /**
     * Khoi chay Minecraft that. HAM NAY CHAN (block) cho toi khi game dong
     * lai - phai goi tren luong nen, khong goi truc tiep tren FX thread.
     */
    public void launch(MinecraftVersion version, JsonObject versionDetail, JavaRuntime javaRuntime,
                        UserProfile profile, Consumer<String> onGameOutput) throws Exception {

        if (!profile.isLoggedIn()) {
            throw new IllegalStateException("Chua dang nhap - can UserProfile co accessToken hop le (Phase 5).");
        }
        if (!versionDetail.has("arguments")) {
            throw new UnsupportedOperationException(
                    "Phien ban " + version.getId() + " dung dinh dang minecraftArguments cu, chua ho tro. "
                            + "Chon phien ban 1.13 tro len.");
        }

        Path nativesDir = extractNatives(versionDetail, version.getId());
        String classpath = buildClasspath(versionDetail, version.getId());

        List<String> command = new ArrayList<>();
        command.add(resolveJavaBinary(javaRuntime));
        command.add("-Xms" + ConfigManager.get().getMinMemoryMB() + "M");
        command.add("-Xmx" + ConfigManager.get().getMaxMemoryMB() + "M");
        command.addAll(performanceJvmFlags());
        command.addAll(buildJvmArguments(versionDetail, classpath, nativesDir));
        command.add(versionDetail.get("mainClass").getAsString());
        command.addAll(buildGameArguments(versionDetail, version, profile));

        LauncherLogger.info("Lenh khoi chay: " + redactForLogging(command));

        processManager.runAndWait(
                command,
                Path.of("."),
                onGameOutput,
                exitCode -> LauncherLogger.info("Minecraft (" + version.getId() + ") da thoat, exit code = " + exitCode)
        );
    }

    /**
     * "Aikar's flags" - bo tham so JVM tinh chinh G1GC duoc cong dong
     * Minecraft dung rong rai tu 2018 (von cho server Paper/Spigot, nhung
     * phan tinh chinh GC cung giup ich cho client vi cung co "tick" sinh
     * ra nhieu object ngan han). Muc tieu chinh: giam giat/lag do
     * garbage collection dung lai (stop-the-world) dot ngot.
     * Tren Java hien dai G1GC thuong da la mac dinh, nen phan tac dung
     * lon nhat thuc ra la cac tham so tinh chinh vung heap ben duoi,
     * khong han la rieng "-XX:+UseG1GC".
     */
    private List<String> performanceJvmFlags() {
        return List.of(
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=200",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:+AlwaysPreTouch",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:+PerfDisableSharedMem",
                "-XX:MaxTenuringThreshold=1"
        );
    }

    private String resolveJavaBinary(JavaRuntime javaRuntime) {
        String path = javaRuntime.getPath();

        // Duong dan tuy chinh (nhap trong Cai dat) co the da tro THANG vao file
        // thuc thi roi - neu vay dung luon, khong noi them bin/... nhu JRE Mojang tu quan ly.
        boolean looksLikeExecutable = path.endsWith(".exe") || path.endsWith("/java") || path.endsWith("\\java");
        if ("custom".equals(javaRuntime.getComponent()) && looksLikeExecutable) {
            return path;
        }

        String exeName = Utils.getOperatingSystem() == Utils.OS.WINDOWS ? "javaw.exe" : "java";
        return Path.of(path, "bin", exeName).toString();
    }

    private String buildClasspath(JsonObject detail, String versionId) {
        List<String> entries = new ArrayList<>();
        String currentOs = currentOsName();

        for (JsonElement libEl : detail.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();
            if (lib.has("rules") && !isRuleAllowed(lib.getAsJsonArray("rules"), currentOs)) continue;

            if (lib.has("downloads")) {
                JsonObject downloads = lib.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                    entries.add(FileManager.librariesDir().resolve(path).toAbsolutePath().toString());
                }
            } else if (lib.has("name") && lib.has("url")) {
                // Kieu Fabric/Maven: chi co "name" (Maven coordinate), khong co "downloads"
                String relativePath = mavenCoordinateToPath(lib.get("name").getAsString());
                entries.add(FileManager.librariesDir().resolve(relativePath).toAbsolutePath().toString());
            }
        }

        entries.add(FileManager.versionJar(versionId).toAbsolutePath().toString());

        String separator = Utils.getOperatingSystem() == Utils.OS.WINDOWS ? ";" : ":";
        return String.join(separator, entries);
    }

    /** Doi "group:artifact:version[:classifier]" thanh duong dan kieu Maven - giong het ham cung ten trong DownloadManager. */
    private String mavenCoordinateToPath(String coordinate) {
        String[] parts = coordinate.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    /** Giai nen file .dll/.so/.dylib tu thu vien native kieu cu ("classifiers") ra 1 thu muc rieng. */
    private Path extractNatives(JsonObject detail, String versionId) throws IOException {
        Path nativesDir = FileManager.versionsDir().resolve(versionId).resolve("natives");
        Files.createDirectories(nativesDir);

        String currentOs = currentOsName();

        for (JsonElement libEl : detail.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();
            if (lib.has("rules") && !isRuleAllowed(lib.getAsJsonArray("rules"), currentOs)) continue;
            if (!lib.has("natives") || !lib.has("downloads")) continue;

            JsonObject natives = lib.getAsJsonObject("natives");
            if (!natives.has(currentOs)) continue;
            String classifierKey = natives.get(currentOs).getAsString();

            JsonObject downloads = lib.getAsJsonObject("downloads");
            if (!downloads.has("classifiers")) continue;
            JsonObject classifiers = downloads.getAsJsonObject("classifiers");
            if (!classifiers.has(classifierKey)) continue;

            String path = classifiers.getAsJsonObject(classifierKey).get("path").getAsString();
            Path nativeJar = FileManager.librariesDir().resolve(path);
            extractJarSkippingMeta(nativeJar, nativesDir);
        }

        return nativesDir;
    }

    private void extractJarSkippingMeta(Path jarFile, Path destinationDir) throws IOException {
        if (!Files.exists(jarFile)) return;

        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;

                Path target = destinationDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destinationDir)) continue; // chan zip-slip

                Files.createDirectories(target.getParent());
                try (var in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private List<String> buildJvmArguments(JsonObject detail, String classpath, Path nativesDir) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("natives_directory", nativesDir.toAbsolutePath().toString());
        placeholders.put("launcher_name", "ME-Launcher");
        placeholders.put("launcher_version", "1.0.0");
        placeholders.put("classpath", classpath);

        JsonObject arguments = detail.getAsJsonObject("arguments");
        if (arguments.has("jvm")) {
            return processArgumentsArray(arguments.getAsJsonArray("jvm"), placeholders);
        }

        // Du phong neu thieu mang "jvm" (khong nen xay ra tu 1.13 tro len):
        List<String> fallback = new ArrayList<>();
        fallback.add("-Djava.library.path=" + placeholders.get("natives_directory"));
        fallback.add("-cp");
        fallback.add(classpath);
        return fallback;
    }

    private List<String> buildGameArguments(JsonObject detail, MinecraftVersion version, UserProfile profile) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("auth_player_name", profile.getUsername());
        placeholders.put("version_name", version.getId());
        placeholders.put("game_directory", FileManager.gameDir().toAbsolutePath().normalize().toString());
        placeholders.put("assets_root", FileManager.assetsDir().toAbsolutePath().toString());
        placeholders.put("assets_index_name", detail.getAsJsonObject("assetIndex").get("id").getAsString());
        placeholders.put("auth_uuid", profile.getUuid());
        placeholders.put("auth_access_token", profile.getAccessToken());
        placeholders.put("clientid", "");
        placeholders.put("auth_xuid", "");
        placeholders.put("user_type", "msa");
        placeholders.put("version_type", version.getType());

        return processArgumentsArray(detail.getAsJsonObject("arguments").getAsJsonArray("game"), placeholders);
    }

    /** Doc 1 mang "arguments.jvm"/"arguments.game", thay ${placeholder} va loc theo rules/features. */
    private List<String> processArgumentsArray(JsonArray argsArray, Map<String, String> placeholders) {
        String currentOs = currentOsName();
        List<String> result = new ArrayList<>();

        for (JsonElement el : argsArray) {
            if (el.isJsonPrimitive()) {
                result.add(substitute(el.getAsString(), placeholders));
                continue;
            }

            JsonObject conditional = el.getAsJsonObject();
            boolean allowed = !conditional.has("rules") || isRuleAllowed(conditional.getAsJsonArray("rules"), currentOs);
            if (!allowed) continue;

            JsonElement value = conditional.get("value");
            if (value.isJsonArray()) {
                for (JsonElement v : value.getAsJsonArray()) {
                    result.add(substitute(v.getAsString(), placeholders));
                }
            } else {
                result.add(substitute(value.getAsString(), placeholders));
            }
        }
        return result;
    }

    /**
     * Quy tac cua Mojang: mac dinh KHONG cho phep neu co mang rules, duyet
     * tuan tu, rule khop SAU CUNG quyet dinh ket qua. Rieng rule nao co
     * "features" (demo, custom resolution, quick play...) thi coi nhu
     * khong bao gio khop, vi launcher nay chua ho tro tinh nang nao trong do.
     */
    private boolean isRuleAllowed(JsonArray rules, String currentOs) {
        boolean allowed = false;
        for (JsonElement ruleEl : rules) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean matches = true;

            if (rule.has("os")) {
                JsonObject osObj = rule.getAsJsonObject("os");
                if (osObj.has("name")) {
                    matches = osObj.get("name").getAsString().equals(currentOs);
                }
            }
            if (rule.has("features")) {
                matches = false;
            }

            if (matches) {
                allowed = "allow".equals(action);
            }
        }
        return allowed;
    }

    private String substitute(String template, Map<String, String> placeholders) {
        String result = template;
        for (var entry : placeholders.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String currentOsName() {
        return switch (Utils.getOperatingSystem()) {
            case WINDOWS -> "windows";
            case MAC -> "osx";
            case LINUX -> "linux";
            default -> "linux";
        };
    }

    /** An accessToken khi ghi log lenh chay, tranh luu token nhay cam ra file .log dang doc duoc. */
    private String redactForLogging(List<String> command) {
        List<String> redacted = new ArrayList<>();
        for (int i = 0; i < command.size(); i++) {
            if (i > 0 && "--accessToken".equals(command.get(i - 1))) {
                redacted.add("***");
            } else {
                redacted.add(command.get(i));
            }
        }
        return String.join(" ", redacted);
    }
}
