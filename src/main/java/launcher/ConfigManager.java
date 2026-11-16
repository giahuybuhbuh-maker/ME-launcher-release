package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import model.LauncherConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final Path CONFIG_DIR = Path.of("config").toAbsolutePath().normalize();
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("launcher.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static LauncherConfig config;

    public static LauncherConfig load() {
        if (config != null) return config;

        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
                config = GSON.fromJson(json, LauncherConfig.class);
                if (config == null) {
                    config = new LauncherConfig();
                }
                LauncherLogger.info("Da tai cau hinh tu " + CONFIG_FILE);
            } else {
                config = new LauncherConfig();
                save();
                LauncherLogger.info("Khong tim thay file cau hinh, da tao file moi tai: " + CONFIG_FILE);
            }
        } catch (Exception e) {
            LauncherLogger.error("Loi khi doc file cau hinh", e);
            config = new LauncherConfig();
            save();
        }

        return config;
    }

    public static void save() {
        if (config == null) {
            config = new LauncherConfig();
        }
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(config), StandardCharsets.UTF_8);
            LauncherLogger.info("Da luu cau hinh vao " + CONFIG_FILE);
        } catch (IOException e) {
            LauncherLogger.error("Loi khi luu file cau hinh", e);
        }
    }

    public static LauncherConfig get() {
        return config != null ? config : load();
    }
}