package launcher;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;

import java.io.File;

public class Utils {

    // CSS ép góc vuông 90 độ tuyệt đối cho mọi nút bấm, ô nhập, hộp kiểm, bảng chọn và hộp thoại
    private static final String SQUARE_CSS_DATA_URI = "data:text/css," +
            ".button, .toggle-button, .text-field, .text-area, .combo-box-base, " +
            ".combo-box, .combo-box .list-cell, .check-box > .box, .radio-button > .radio, " +
            ".spinner, .spinner .text-field, .spinner .increment-arrow-button, .spinner .decrement-arrow-button, " +
            ".progress-bar, .progress-bar > .bar, .progress-bar > .track, " +
            ".slider .track, .slider .thumb, .dialog-pane, .dialog-pane .button, .tooltip {" +
            "    -fx-background-radius: 0px !important;" +
            "    -fx-border-radius: 0px !important;" +
            "}" +
            ".button:focused, .toggle-button:focused, .text-field:focused, .combo-box-base:focused, " +
            ".check-box:focused > .box, .spinner:focused {" +
            "    -fx-background-radius: 0px !important;" +
            "    -fx-border-radius: 0px !important;" +
            "}";

    private Utils() {
        // Lop tien ich static, khong cho khoi tao instance
    }

    public enum OS { WINDOWS, MAC, LINUX, UNKNOWN }

    public static OS getOperatingSystem() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) return OS.WINDOWS;
        if (osName.contains("mac")) return OS.MAC;
        if (osName.contains("nux") || osName.contains("nix")) return OS.LINUX;
        return OS.UNKNOWN;
    }

    public static File getWorkingDirectory() {
        return new File(System.getProperty("user.dir"));
    }

    /** Doi so byte thanh chuoi de doc, vd: 1536 -> "1.5 KB". Dung khi hien thi tien do tai file. */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }

    /**
     * Ép góc vuông 90 độ cho toàn bộ Scene và các phần tử con bên trong.
     */
    public static void applySquareTheme(Scene scene) {
        if (scene == null) return;
        if (!scene.getStylesheets().contains(SQUARE_CSS_DATA_URI)) {
            scene.getStylesheets().add(SQUARE_CSS_DATA_URI);
        }
        if (scene.getRoot() != null) {
            applySquareNodesRecursively(scene.getRoot());
            Platform.runLater(() -> applySquareNodesRecursively(scene.getRoot()));
        }
        scene.rootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null) {
                applySquareNodesRecursively(newRoot);
            }
        });
    }

    /**
     * Ép góc vuông 90 độ cho hộp thoại Alert / Dialog (ví dụ hộp thoại Chọn phương thức đăng nhập).
     */
    public static void applySquareTheme(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) return;
        if (!dialog.getDialogPane().getStylesheets().contains(SQUARE_CSS_DATA_URI)) {
            dialog.getDialogPane().getStylesheets().add(SQUARE_CSS_DATA_URI);
        }
        dialog.setOnShown(e -> {
            if (dialog.getDialogPane().getScene() != null) {
                applySquareTheme(dialog.getDialogPane().getScene());
            }
            applySquareNodesRecursively(dialog.getDialogPane());
        });
    }

    /**
     * Quét đệ quy mọi Node trong giao diện để xóa toàn bộ bo góc inline (-fx-background-radius / -fx-border-radius).
     */
    public static void applySquareNodesRecursively(Node node) {
        if (node == null) return;

        if (node instanceof ButtonBase
                || node instanceof TextInputControl
                || node instanceof ComboBoxBase<?>
                || node instanceof Spinner<?>
                || node instanceof ProgressBar) {
            String currentStyle = node.getStyle() == null ? "" : node.getStyle();
            // Thay thế mọi cấu hình radius cũ (nếu có trong FXML hoặc code) về 0
            String squaredStyle = currentStyle
                    .replaceAll("-fx-background-radius\\s*:\\s*[^;]+;?", "")
                    .replaceAll("-fx-border-radius\\s*:\\s*[^;]+;?", "");
            if (!squaredStyle.isBlank() && !squaredStyle.trim().endsWith(";")) {
                squaredStyle += "; ";
            }
            squaredStyle += "-fx-background-radius: 0px; -fx-border-radius: 0px;";
            node.setStyle(squaredStyle);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applySquareNodesRecursively(child);
            }
        }
    }
}