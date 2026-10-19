package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import model.LauncherConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final Path CONFIG_DIR = Path.of("config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("launcher.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static LauncherConfig config;

    public static LauncherConfig load() {
        if (config != null) return config;

        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                config = GSON.fromJson(json, LauncherConfig.class);
                LauncherLogger.info("Da tai cau hinh tu " + CONFIG_FILE);
            } else {
                config = new LauncherConfig();
                save();
                LauncherLogger.info("Khong tim thay file cau hinh, da tao file moi.");
            }
        } catch (IOException e) {
            LauncherLogger.error("Loi khi doc file cau hinh", e);
            config = new LauncherConfig();
        }

        return config;
    }

    public static void save() {
        if (config == null) return;
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(config));
            LauncherLogger.info("Da luu cau hinh vao " + CONFIG_FILE);
        } catch (IOException e) {
            LauncherLogger.error("Loi khi luu file cau hinh", e);
        }
    }

    public static LauncherConfig get() {
        return config != null ? config : load();
    }
}
