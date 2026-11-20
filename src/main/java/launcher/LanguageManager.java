package launcher;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quan ly da ngon ngu nhe (Tieng Viet / English) cho ME Launcher.
 * Khong dung thu vien ngoai, tu dong dich cac Node giao dien theo cau hinh da luu.
 */
public class LanguageManager {

    private static final Map<String, String> VI_TO_EN = new LinkedHashMap<>();
    private static final Map<String, String> EN_TO_VI = new LinkedHashMap<>();

    static {
        // loading.fxml
        put("Dang khoi dong...", "Starting...");

        // launcher.fxml
        put("Chua dang nhap", "Not logged in");
        put("Dang nhap", "Login");
        put("Doi tai khoan", "Switch account");
        put("Cai dat", "Settings");
        put("Chao mung tro lai!", "Welcome back!");
        put("San sang phieu luu trong the gioi Minecraft", "Ready for your next Minecraft adventure");
        put("San sang", "Ready");
        put("CHOI", "PLAY");

        // about.fxml
        put("Phien ban 1.0.0", "Version 1.0.0");
        put("Launcher Minecraft tu viet bang Java + JavaFX", "Custom Minecraft Launcher built with Java + JavaFX");
        put("Dang nhap qua tai khoan Microsoft that (OAuth device code flow)",
                "Sign in with Microsoft account (OAuth device code flow)");

        // settings.fxml
        put("Cài đặt (Settings)", "Settings (ME Launcher)");
        put("Giới thiệu", "About");
        put("Thư mục lưu file game (Directory):", "Game directory:");
        put("vd: D:\\MinecraftData (để trống = thư mục 'game' mặc định)",
                "e.g. D:\\MinecraftData (leave empty for default 'game' folder)");
        put("Độ phân giải (Resolution):", "Resolution:");
        put("Fullscreen (Toàn màn hình)", "Fullscreen");
        put("Danh sách phiên bản (Version list):", "Version list:");
        put("Hiện Snapshots", "Show Snapshots");
        put("Hiện bản Mod (NeoForge/Forge/OptiFine)", "Show Modded (NeoForge/Forge/OptiFine)");
        put("Hiện Beta", "Show Beta");
        put("Hiện Alpha", "Show Alpha");
        put("Hiện bản cũ (≤ 1.13)", "Show Old Releases (≤ 1.13)");
        put("Java Arguments (Lệnh Java khi chạy - JVM Flags):", "Java Arguments (JVM Flags):");
        put("Minecraft Arguments (Tham số vào game tùy chỉnh):", "Minecraft Arguments (Custom game flags):");
        put("Đường dẫn Java tùy chỉnh (Java path - để trống = tự động tải):",
                "Custom Java path (leave empty for auto-download):");
        put("Phân bổ bộ nhớ RAM (Memory allocation - MB):", "Memory allocation (MB):");
        put("Gợi ý theo máy", "Auto-suggest");
        put("RAM tối thiểu (MB):", "Minimum RAM (MB):");
        put("Chế độ chơi (chỉnh đồ họa vanilla trong options.txt):",
                "Performance mode (adjusts vanilla options.txt):");
        put("Giao diện tối (dark theme)", "Dark theme");
        put("Hành vi Launcher khi mở game:", "Launcher behavior on game launch:");
        put("Ẩn Launcher khi mở game", "Hide Launcher when game starts");
        put("Hiện (giữ) Launcher khi mở game", "Keep Launcher open");
        put("Ngôn ngữ (Language):", "Language:");
        put("Tự hiện Console khi Launcher hoặc game bị lỗi",
                "Auto-show Console when Launcher or game crashes");
        put("Mở Console", "Open Console");
        put("Đặt lại mặc định (Reset)", "Reset Defaults");
        put("Hủy", "Cancel");
        put("Lưu (Save)", "Save");
    }

    private LanguageManager() {
    }

    private static void put(String vi, String en) {
        VI_TO_EN.put(vi, en);
        EN_TO_VI.put(en, vi);
    }

    public static String getCurrentLanguage() {
        try {
            String lang = ConfigManager.get().getLanguage();
            return "en".equalsIgnoreCase(lang) ? "en" : "vi";
        } catch (Throwable ignored) {
            return "vi";
        }
    }

    public static boolean isEnglish() {
        return "en".equals(getCurrentLanguage());
    }

    /** Dich nhanh theo khoa cho Console va tieu de cua so. */
    public static String t(String key) {
        boolean en = isEnglish();
        return switch (key) {
            case "console.title" -> en ? "ME Launcher - Error Console" : "ME Launcher - Bảng điều khiển lỗi (Console)";
            case "console.header.ready" -> en ? "Launcher & Minecraft Log Console" : "Nhật ký hệ thống ME Launcher & Minecraft";
            case "console.header.error" -> en ? "An error occurred! Details below:" : "Phát hiện lỗi! Xem chi tiết bên dưới:";
            case "console.copy" -> en ? "Copy Log" : "Copy Log";
            case "console.copied" -> en ? "Copied!" : "Đã Copy!";
            case "console.openLogs" -> en ? "Open logs folder" : "Mở thư mục logs";
            case "console.clear" -> en ? "Clear" : "Xóa";
            case "console.close" -> en ? "Close" : "Đóng";
            case "settings.title" -> en ? "Settings" : "Cài đặt";
            case "about.title" -> en ? "About" : "Giới thiệu";
            default -> key;
        };
    }

    /** Quet va dich toan bo Node trong 1 Scene theo ngon ngu hien tai. */
    public static void applyToScene(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        applyToNode(scene.getRoot(), isEnglish());
    }

    public static void applyToNode(Node node, boolean toEnglish) {
        if (node == null) return;

        Map<String, String> dict = toEnglish ? VI_TO_EN : EN_TO_VI;

        if (node instanceof Labeled labeled) {
            String text = labeled.getText();
            if (text != null && dict.containsKey(text)) {
                labeled.setText(dict.get(text));
            }
        } else if (node instanceof TextInputControl input) {
            String prompt = input.getPromptText();
            if (prompt != null && dict.containsKey(prompt)) {
                input.setPromptText(dict.get(prompt));
            }
        }

        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            applyToNode(scrollPane.getContent(), toEnglish);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToNode(child, toEnglish);
            }
        }
    }
}