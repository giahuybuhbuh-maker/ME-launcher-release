package launcher;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.LauncherConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LauncherApplication extends Application {

    // [SỬA BUG #3] Đã xóa chữ [build-profiles-fix]
    public static final String APP_TITLE = "ME Launcher v0.1.7";

    @Override
    public void start(Stage primaryStage) throws IOException {
        LauncherLogger.info("Dang khoi dong " + APP_TITLE + "...");
        LauncherConfig config = ConfigManager.load();

        // Tu dong ghi log va bat Console neu co loi bat ngo tren bat ky luong nao
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LauncherLogger.error("Loi khong xac dinh tren luong [" + thread.getName() + "]", throwable));

        // Đảm bảo khi người dùng bấm nút X đóng cửa sổ chính sẽ lưu Setting và thoát hoàn toàn
        primaryStage.setOnCloseRequest(event -> {
            LauncherLogger.info(APP_TITLE + " dang dong lai.");
            ConfigManager.save();
            Platform.exit();
            System.exit(0);
        });

        // Mở màn hình giao diện trước rồi mới nạp icon để file .ico không bao giờ chặn khởi động
        showLoadingScreen(primaryStage, config);

        // Nạp icon ME_Launcher.ico cho cửa sổ chính và thanh Taskbar
        loadWindowIcons(primaryStage);
    }

    /**
     * Bat/tat class CSS low-ram-mode hoac high-ram-mode tren root cua Scene
     * de giam RAM ngay lap tuc (tat anh nen 7MB & dropshadow khi o che do Low).
     */
    public static void applyLauncherRamMode(Scene scene, LauncherConfig config) {
        if (scene == null || scene.getRoot() == null) return;
        boolean lowRam = config == null || config.isLowRamMode();
        List<String> classes = scene.getRoot().getStyleClass();
        classes.remove("low-ram-mode");
        classes.remove("high-ram-mode");
        classes.add(lowRam ? "low-ram-mode" : "high-ram-mode");
    }

    /**
     * Tu dong cap nhat co gioi han RAM/GC/Prism trong app/ME Launcher.cfg
     * (chi ap dung khi dang chay ban dong goi jpackage co file .cfg).
     */
    public static void syncLauncherCfgRamMode(LauncherConfig config) {
        if (config == null) return;
        Path cfgPath = resolveLauncherCfgPath();
        if (cfgPath == null || !Files.isRegularFile(cfgPath)) return;

        try {
            List<String> originalLines = Files.readAllLines(cfgPath, StandardCharsets.UTF_8);
            List<String> newLines = new ArrayList<>();
            String appPathOption = "java-options=-Djpackage.app-path=$APPDIR\\..\\ME Launcher.exe";
            boolean inJavaOptions = false;

            for (String rawLine : originalLines) {
                String line = rawLine.replace("\uFEFF", "");
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("[JavaOptions]")) {
                    inJavaOptions = true;
                    continue;
                }
                if (inJavaOptions) {
                    if (trimmed.startsWith("[")) {
                        inJavaOptions = false;
                        newLines.add(line);
                    } else if (trimmed.startsWith("java-options=-Djpackage.app-path=")) {
                        appPathOption = trimmed;
                    }
                    continue;
                }
                newLines.add(line);
            }

            // Bo bot dong trong thua o cuoi phan [Application]
            while (!newLines.isEmpty() && newLines.get(newLines.size() - 1).isBlank()) {
                newLines.remove(newLines.size() - 1);
            }

            newLines.add("");
            newLines.add("[JavaOptions]");
            newLines.add(appPathOption);

            // macOS can tran -Xmx cao hon de tranh OutOfMemoryError khi nap bang Apple Color Emoji (sbix)
            boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");

            if (config.isLowRamMode()) {
                // Che do Low: muc tieu tong RAM < 150MB
                newLines.add("java-options=-Xms16m");
                newLines.add("java-options=" + (isMac ? "-Xmx384m" : "-Xmx112m"));
                newLines.add("java-options=-Xss256k");
                newLines.add("java-options=-XX:+UseSerialGC");
                newLines.add("java-options=-XX:MinHeapFreeRatio=5");
                newLines.add("java-options=-XX:MaxHeapFreeRatio=15");
                newLines.add("java-options=-XX:TieredStopAtLevel=1");
                newLines.add("java-options=-XX:MaxMetaspaceSize=72m");
                newLines.add("java-options=-XX:ReservedCodeCacheSize=16m");
                newLines.add("java-options=-Dprism.maxvram=48m");
                newLines.add("java-options=-Dprism.targetvram=24m");
            } else {
                // Che do High: muc tieu tong RAM ~ 200-400MB (du nen & hieu ung muot)
                newLines.add("java-options=-Xms64m");
                newLines.add("java-options=" + (isMac ? "-Xmx512m" : "-Xmx256m"));
                newLines.add("java-options=-XX:+UseG1GC");
                newLines.add("java-options=-XX:MaxMetaspaceSize=128m");
                newLines.add("java-options=-XX:ReservedCodeCacheSize=48m");
                newLines.add("java-options=-Dprism.maxvram=192m");
                newLines.add("java-options=-Dprism.targetvram=96m");
            }

            newLines.add("java-options=-Dfile.encoding=UTF-8");
            newLines.add("java-options=--enable-native-access=ALL-UNNAMED");

            Files.write(cfgPath, newLines, StandardCharsets.UTF_8);
            LauncherLogger.info("Da cap nhat che do RAM (" + config.getLauncherRamMode() + ") vao: " + cfgPath);
        } catch (Exception e) {
            LauncherLogger.warn("Khong the cap nhat ME Launcher.cfg: " + e.getMessage());
        }
    }

    private static Path resolveLauncherCfgPath() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            try {
                Path exe = Path.of(appPath).toAbsolutePath().normalize();
                Path parent = exe.getParent();
                if (parent != null) {
                    Path winOrLinuxCfg = parent.resolve("app").resolve("ME Launcher.cfg");
                    if (Files.isRegularFile(winOrLinuxCfg)) return winOrLinuxCfg;
                    if (parent.getParent() != null) {
                        Path macCfg = parent.getParent().resolve("app").resolve("ME Launcher.cfg");
                        if (Files.isRegularFile(macCfg)) return macCfg;
                        Path linuxCfg = parent.getParent().resolve("lib").resolve("app").resolve("ME Launcher.cfg");
                        if (Files.isRegularFile(linuxCfg)) return linuxCfg;
                    }
                }
            } catch (Exception ignored) {}
        }
        Path localCfg = Path.of("app", "ME Launcher.cfg").toAbsolutePath().normalize();
        return Files.isRegularFile(localCfg) ? localCfg : null;
    }

    /**
     * Tự động tìm và nạp ME_Launcher.ico trong resources/assets/ hoặc thư mục ngoài.
     * Có thể gọi lại cho các cửa sổ phụ (như Cài đặt, Giới thiệu).
     */
    public static void loadWindowIcons(Stage stage) {
        if (stage == null) return;

        try {
            // 1. Tìm trong resources đóng gói sẵn trong jar
            for (String resPath : List.of(
                    "/assets/icon.png",
                    "/assets/ME_Launcher.ico",
                    "/assets/ME-launcher.ico",
                    "/ME_Launcher.ico")) {
                try (InputStream is = LauncherApplication.class.getResourceAsStream(resPath)) {
                    if (is != null) {
                        Image img = decodeIconBytes(is.readAllBytes());
                        if (img != null && !img.isError() && img.getWidth() > 0) {
                            stage.getIcons().setAll(img);
                            LauncherLogger.info("Da nap icon tu resources: " + resPath);
                            return;
                        }
                    }
                } catch (Throwable e) {
                    LauncherLogger.warn("Khong doc duoc icon " + resPath + ": " + e.getMessage());
                }
            }

            // 2. Dự phòng: Tìm file ME_Launcher.ico nằm ngoài thư mục cài đặt hoặc src/main/resources/assets
            for (String filePath : List.of(
                    "ME_Launcher.ico",
                    "ME-launcher.ico",
                    "assets/ME_Launcher.ico",
                    "src/main/resources/assets/ME_Launcher.ico",
                    "main/resources/assets/ME_Launcher.ico")) {
                Path p = Path.of(filePath);
                if (Files.exists(p)) {
                    try {
                        Image img = decodeIconBytes(Files.readAllBytes(p));
                        if (img != null && !img.isError() && img.getWidth() > 0) {
                            stage.getIcons().setAll(img);
                            LauncherLogger.info("Da nap icon tu file: " + p.toAbsolutePath());
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            LauncherLogger.warn("Bo qua nap icon de dam bao Launcher khoi dong an toan: " + t.getMessage());
        }
    }

    /**
     * Hỗ trợ đọc cả file .ico chứa PNG, file .ico Bitmap 32-bit BGRA và file .png
     */
    private static Image decodeIconBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return null;

        try {
            // Nếu bản thân file là PNG (89 50 4E 47)
            if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                return new Image(new ByteArrayInputStream(bytes));
            }

            // Quét tìm frame PNG nhúng bên trong file .ico (nếu có)
            for (int i = 0; i <= bytes.length - 8; i++) {
                if (bytes[i] == (byte) 0x89 && bytes[i + 1] == (byte) 0x50
                        && bytes[i + 2] == (byte) 0x4E && bytes[i + 3] == (byte) 0x47
                        && bytes[i + 4] == (byte) 0x0D && bytes[i + 5] == (byte) 0x0A
                        && bytes[i + 6] == (byte) 0x1A && bytes[i + 7] == (byte) 0x0A) {
                    Image pngImg = new Image(new ByteArrayInputStream(bytes, i, bytes.length - i));
                    if (!pngImg.isError() && pngImg.getWidth() > 0) {
                        return pngImg;
                    }
                }
            }

            // Nếu là định dạng Windows ICO (00 00 01 00)
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (buf.getShort(0) == 0 && buf.getShort(2) == 1) {
                int count = buf.getShort(4) & 0xFFFF;
                if (count <= 0 || count > 64) return null;

                int bestEntryOffset = -1;
                int maxArea = -1;

                for (int i = 0; i < count; i++) {
                    int entryPos = 6 + i * 16;
                    if (entryPos + 16 > bytes.length) break;
                    int w = (bytes[entryPos] & 0xFF) == 0 ? 256 : (bytes[entryPos] & 0xFF);
                    int h = (bytes[entryPos + 1] & 0xFF) == 0 ? 256 : (bytes[entryPos + 1] & 0xFF);
                    if (w * h >= maxArea) {
                        maxArea = w * h;
                        bestEntryOffset = entryPos;
                    }
                }

                if (bestEntryOffset != -1) {
                    int dataSize = buf.getInt(bestEntryOffset + 8);
                    int dataOffset = buf.getInt(bestEntryOffset + 12);
                    if (dataOffset > 0 && dataSize >= 40 && dataOffset + dataSize <= bytes.length
                            && dataOffset + 16 <= bytes.length) {
                        // Frame DIB Bitmap 32-bit nhúng bên trong ICO
                        int headerSize = buf.getInt(dataOffset);
                        int width = buf.getInt(dataOffset + 4);
                        int height = buf.getInt(dataOffset + 8) / 2;
                        short bpp = buf.getShort(dataOffset + 14);

                        if (bpp == 32 && width > 0 && width <= 512 && height > 0 && height <= 512) {
                            WritableImage wImg = new WritableImage(width, height);
                            PixelWriter pw = wImg.getPixelWriter();
                            int pixelStart = dataOffset + headerSize;
                            for (int y = 0; y < height; y++) {
                                int srcRow = height - 1 - y;
                                for (int x = 0; x < width; x++) {
                                    int pxIndex = pixelStart + (srcRow * width + x) * 4;
                                    if (pxIndex >= 0 && pxIndex + 4 <= bytes.length) {
                                        int b = bytes[pxIndex] & 0xFF;
                                        int g = bytes[pxIndex + 1] & 0xFF;
                                        int r = bytes[pxIndex + 2] & 0xFF;
                                        int a = bytes[pxIndex + 3] & 0xFF;
                                        int argb = (a << 24) | (r << 16) | (g << 8) | b;
                                        pw.setArgb(x, y, argb);
                                    }
                                }
                            }
                            return wImg;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /** Hien loading.fxml truoc (it nhat 1 giay de nguoi dung kip thay), roi chuyen sang man hinh chinh. */
    private void showLoadingScreen(Stage primaryStage, LauncherConfig config) throws IOException {
        Parent root = loadFxml("/ui/loading.fxml");
        Scene scene = new Scene(root, config.getWindowWidth(), config.getWindowHeight());
        applyStylesheets(scene, config);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> {
            try {
                showMainScreen(primaryStage, config);
            } catch (IOException ex) {
                LauncherLogger.error("Khong the mo man hinh chinh", ex);
            }
        });
        pause.play();
    }

    private void showMainScreen(Stage primaryStage, LauncherConfig config) throws IOException {
        Parent root = loadFxml("/ui/launcher.fxml");
        Scene scene = new Scene(root, config.getWindowWidth(), config.getWindowHeight());
        applyStylesheets(scene, config);

        primaryStage.setScene(scene);
        if (config.isLowRamMode()) {
            System.gc();
        }
        LauncherLogger.info(APP_TITLE + " da khoi dong thanh cong.");
    }

    private Parent loadFxml(String path) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource(path), "Khong tim thay " + path + " trong resources"));
        return loader.load();
    }

    private void applyStylesheets(Scene scene, LauncherConfig config) {
        // Gan class low-ram-mode / high-ram-mode truoc khi nap CSS de khong tai anh nen thua o che do Low
        applyLauncherRamMode(scene, config);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());
        if (config.isDarkTheme()) {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/dark.css")).toExternalForm());
        }
        // Ép toàn bộ nút bấm và ô nhập sang góc vuông 90 độ
        Utils.applySquareTheme(scene);
        // Áp dụng ngôn ngữ đã lưu trong Cài đặt
        LanguageManager.applyToScene(scene);
    }

    @Override
    public void stop() {
        LauncherLogger.info(APP_TITLE + " dang dong lai.");
        ConfigManager.save();
    }

    public static void main(String[] args) {
        launch(args);
    }
}