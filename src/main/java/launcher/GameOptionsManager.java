package launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Doc/ghi game/options.txt - file cai dat DO HOA/GAMEPLAY cua chinh
 * MINECRAFT (khac voi config/launcher.json la cai dat cua LAUNCHER).
 *
 * Chi ghi de dung NHUNG DONG lien quan hieu nang/do hoa theo che do nguoi
 * dung chon, KHONG dong lai toan bo file - giu nguyen keybind, resource
 * pack, va cac cai dat khac nguoi dung da tung tu chinh trong game.
 *
 * CANH BAO: 1 vai dong (vd "ao" - smooth lighting) da doi dinh dang giua
 * cac phien ban Minecraft (ban cu dung so 0/1/2, ban moi dung true/false).
 * Neu sai dinh dang cho phien ban dang choi, theo kinh nghiem chung
 * Minecraft se chi bo qua dong do va dung mac dinh, khong lam hong file.
 * Chua kiem chung tren tat ca phien ban.
 */
public class GameOptionsManager {

    private static Path optionsFile() { return FileManager.gameDir().resolve("options.txt"); }

    public enum Mode { ULTRAFAST, NORMAL, HIGH_QUALITY }

    /** NORMAL = khong dong gi ca, giu nguyen cai dat hien co (hoac mac dinh cua Minecraft neu chua tung chay). */
    public void applyMode(Mode mode) throws IOException {
        if (mode == Mode.NORMAL) return;

        Map<String, String> options = readExisting();
        Map<String, String> overrides = presetFor(mode);
        options.putAll(overrides);
        write(options);

        LauncherLogger.info("Da ap dung che do '" + mode + "' vao options.txt ("
                + overrides.size() + " dong duoc chinh, cac dong khac giu nguyen).");
    }

    private Map<String, String> presetFor(Mode mode) {
        Map<String, String> m = new LinkedHashMap<>();
        if (mode == Mode.ULTRAFAST) {
            m.put("graphicsMode", "0");   // Fast
            m.put("renderDistance", "6");
            m.put("simulationDistance", "6");
            m.put("particles", "2");      // Minimal
            m.put("ao", "false");         // tat smooth lighting
            m.put("enableVsync", "false");
            m.put("entityShadows", "false");
            m.put("renderClouds", "false");
            m.put("mipmapLevels", "0");
        } else if (mode == Mode.HIGH_QUALITY) {
            // Dung Fancy (1) chu khong phai Fabulous (2): Fabulous dep hon
            // nhung nang GPU hon han, de "muot" thi Fancy hop ly hon.
            m.put("graphicsMode", "1");
            m.put("renderDistance", "16");
            m.put("simulationDistance", "12");
            m.put("particles", "0");      // All
            m.put("ao", "true");
            m.put("enableVsync", "true");
            m.put("entityShadows", "true");
            m.put("renderClouds", "true");
            m.put("mipmapLevels", "4");
        }
        return m;
    }

    private Map<String, String> readExisting() throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(optionsFile())) return map;

        for (String line : Files.readAllLines(optionsFile())) {
            int idx = line.indexOf(':');
            if (idx <= 0) continue;
            map.put(line.substring(0, idx), line.substring(idx + 1));
        }
        return map;
    }

    private void write(Map<String, String> options) throws IOException {
        Files.createDirectories(optionsFile().getParent());
        StringBuilder sb = new StringBuilder();
        for (var entry : options.entrySet()) {
            sb.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        Files.writeString(optionsFile(), sb.toString());
    }
}
