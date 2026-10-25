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
 * jar), tham so JVM va tham so game (ho tro CA "arguments" tu 1.13+ VA
 * "minecraftArguments" cho 1.12.2 tro xuong), va giai nen thu vien
 * native (LWJGL...) ra 1 thu muc rieng truoc khi chay.
 */
public class MinecraftLauncher {

    private final ProcessManager processManager = new ProcessManager();

    public void launch(MinecraftVersion version, JsonObject versionDetail, JavaRuntime javaRuntime,
                        UserProfile profile, Consumer<String> onGameOutput) throws Exception {

        if (!profile.isLoggedIn()) {
            throw new IllegalStateException("Chua dang nhap - can UserProfile hop le.");
        }
        if (!versionDetail.has("arguments") && !versionDetail.has("minecraftArguments")) {
            throw new UnsupportedOperationException(
                    "File JSON cua phien ban " + version.getId() + " khong co arguments hoac minecraftArguments.");
        }

        Path nativesDir = extractNatives(versionDetail, version.getId());
        String classpath = buildClasspath(versionDetail, version.getId());

        List<String> command = new ArrayList<>();
        command.add(resolveJavaBinary(javaRuntime));
        command.add("-Xms" + ConfigManager.get().getMinMemoryMB() + "M");
        command.add("-Xmx" + ConfigManager.get().getMaxMemoryMB() + "M");

        if (!"normal".equals(ConfigManager.get().getPerformanceMode())) {
            command.addAll(performanceJvmFlags());
        }
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
            } else if (lib.has("name")) {
                String relativePath = mavenCoordinateToPath(lib.get("name").getAsString());
                entries.add(FileManager.librariesDir().resolve(relativePath).toAbsolutePath().toString());
            }
        }

        entries.add(FileManager.versionJar(versionId).toAbsolutePath().toString());

        String separator = Utils.getOperatingSystem() == Utils.OS.WINDOWS ? ";" : ":";
        return String.join(separator, entries);
    }

    private String mavenCoordinateToPath(String coordinate) {
        String[] parts = coordinate.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    /** Giai nen file .dll/.so/.dylib tu thu vien native (ho tro ca ${arch} cua ban 1.8.9 / 1.7.10). */
    private Path extractNatives(JsonObject detail, String versionId) throws IOException {
        Path nativesDir = FileManager.versionsDir().resolve(versionId).resolve("natives");
        Files.createDirectories(nativesDir);

        String currentOs = currentOsName();
        String archBits = System.getProperty("os.arch", "").contains("64") ? "64" : "32";

        for (JsonElement libEl : detail.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();
            if (lib.has("rules") && !isRuleAllowed(lib.getAsJsonArray("rules"), currentOs)) continue;
            if (!lib.has("natives") || !lib.has("downloads")) continue;

            JsonObject natives = lib.getAsJsonObject("natives");
            if (!natives.has(currentOs)) continue;
            String classifierKey = natives.get(currentOs).getAsString().replace("${arch}", archBits);

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
                if (!target.startsWith(destinationDir)) continue;

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

        if (detail.has("arguments")) {
            JsonObject arguments = detail.getAsJsonObject("arguments");
            if (arguments != null && arguments.has("jvm")) {
                return processArgumentsArray(arguments.getAsJsonArray("jvm"), placeholders);
            }
        }

        // Danh cho cac ban 1.12.2 tro xuong khong co mang "arguments.jvm":
        List<String> fallback = new ArrayList<>();
        fallback.add("-Djava.library.path=" + placeholders.get("natives_directory"));
        fallback.add("-cp");
        fallback.add(classpath);
        return fallback;
    }

    private List<String> buildGameArguments(JsonObject detail, MinecraftVersion version, UserProfile profile) {
        String assetIndexId = detail.has("assetIndex")
                ? detail.getAsJsonObject("assetIndex").get("id").getAsString()
                : (detail.has("assets") ? detail.get("assets").getAsString() : "legacy");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("auth_player_name", profile.getUsername());
        placeholders.put("version_name", version.getId());
        placeholders.put("game_directory", FileManager.gameDir().toAbsolutePath().normalize().toString());
        placeholders.put("assets_root", FileManager.assetsDir().toAbsolutePath().toString());
        placeholders.put("game_assets", FileManager.assetsDir().toAbsolutePath().toString());
        placeholders.put("assets_index_name", assetIndexId);
        placeholders.put("auth_uuid", profile.getUuid());
        placeholders.put("auth_access_token", profile.getAccessToken());
        placeholders.put("auth_session", profile.getAccessToken());
        placeholders.put("clientid", "");
        placeholders.put("auth_xuid", "");
        placeholders.put("user_type", profile.isOffline() ? "legacy" : "msa");
        placeholders.put("version_type", version.getType());
        placeholders.put("user_properties", "{}");

        // 1. Phien ban 1.13 tro len dung "arguments.game"
        if (detail.has("arguments") && detail.getAsJsonObject("arguments").has("game")) {
            return processArgumentsArray(detail.getAsJsonObject("arguments").getAsJsonArray("game"), placeholders);
        }

        // 2. Phien ban 1.12.2 tro xuong (1.12.2, 1.8.9, 1.7.10...) dung chuoi "minecraftArguments"
        List<String> legacyArgs = new ArrayList<>();
        if (detail.has("minecraftArguments")) {
            String raw = detail.get("minecraftArguments").getAsString();
            String resolved = substitute(raw, placeholders);
            for (String token : resolved.split(" ")) {
                if (!token.isBlank()) {
                    legacyArgs.add(token);
                }
            }
        }
        return legacyArgs;
    }

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