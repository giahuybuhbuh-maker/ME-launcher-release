package launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.JavaRuntime;
import model.LauncherConfig;
import model.MinecraftVersion;
import model.UserProfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class LauncherController {

    private static final Path OFFLINE_ACCOUNT_FILE = Path.of("offline_account.json").toAbsolutePath().normalize();
    private static final String MRPACK_CONVERTER_URL = "https://fabulously-optimized.github.io/mrpack-to-zip/";

    private final VersionManager versionManager = new VersionManager();
    private final DownloadManager downloadManager = new DownloadManager();
    private final JavaManager javaManager = new JavaManager();
    private final AuthManager authManager = new AuthManager();
    private final MinecraftLauncher minecraftLauncher = new MinecraftLauncher();
    private final ModLoaderManager modLoaderManager = new ModLoaderManager(versionManager);

    private UserProfile currentProfile; // null cho toi khi dang nhap xong

    @FXML
    private ComboBox<MinecraftVersion> versionComboBox;

    @FXML
    private CheckBox fabricCheckBox;

    @FXML
    private Button playButton;

    @FXML
    private Button settingsButton;

    @FXML
    private Button loginButton;

    @FXML
    private Label profileLabel;

    @FXML
    private ImageView skinImageView;

    @FXML
    private ProgressBar downloadProgressBar;

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        LauncherLogger.info("Khoi tao giao dien launcher...");

        // Giup JavaFX khong tu tat ngam khi an cua so Launcher luc dang chay Minecraft (Bug #2)
        Platform.setImplicitExit(false);

        downloadProgressBar.setVisible(false);
        FileManager.ensureLauncherProfilesExist();

        // Dam bao file setting luon duoc tao va luu tren o cung (Bug #1)
        ConfigManager.get();
        ConfigManager.save();

        // Tu dong nap lai ten nhan vat Crack (Offline) da luu tu lan truoc (Bug #1)
        loadSavedOfflineProfile();

        // Khi doi phien ban (ke ca lan chon dau tien tu code), luu lai
        // luon vao config va cap nhat trang thai lam mo cua nut Fabric.
        versionComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                ConfigManager.get().setSelectedVersion(newVal.getId());
                ConfigManager.save();
                updateFabricAvailability(newVal);
            }
        });

        loadVersionsAsync(false);
    }

    /**
     * [SỬA BUG #1] Tự động đọc lại tên nhân vật Crack đã lưu từ file offline_account.json
     */
    private void loadSavedOfflineProfile() {
        if (!Files.exists(OFFLINE_ACCOUNT_FILE)) return;
        try {
            String jsonStr = Files.readString(OFFLINE_ACCOUNT_FILE, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (json.has("username")) {
                String savedUsername = json.get("username").getAsString().trim();
                if (!savedUsername.isEmpty()) {
                    currentProfile = authManager.createOfflineProfile(savedUsername);
                    profileLabel.setText("Offline: " + currentProfile.getUsername());
                    loginButton.setText("Doi tai khoan");
                    LauncherLogger.info("Da tu dong khoi phuc tai khoan Offline: " + currentProfile.getUsername());
                }
            }
        } catch (Exception e) {
            LauncherLogger.warn("Khong doc duoc file offline_account.json: " + e.getMessage());
        }
    }

    /**
     * [SỬA BUG #1] Lưu tên nhân vật Crack xuống file offline_account.json
     */
    private void saveOfflineProfile(String username) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("username", username);
            Files.writeString(OFFLINE_ACCOUNT_FILE, json.toString(), StandardCharsets.UTF_8);
            LauncherLogger.info("Da luu ten Offline vao: " + OFFLINE_ACCOUNT_FILE);
        } catch (IOException e) {
            LauncherLogger.warn("Khong luu duoc file offline_account.json: " + e.getMessage());
        }
    }

    /**
     * Tu dong lam mo (Disable + Opacity) o chon Fabric khi nguoi dung chon
     * phien ban 1.13.2 tro xuong hoac phien ban cai thu cong (OptiFine/Forge).
     */
    private void updateFabricAvailability(MinecraftVersion version) {
        if (version == null || fabricCheckBox == null) return;

        boolean isCustomLocal = "custom-local".equals(version.getType());
        boolean supported = !isCustomLocal && isFabricSupportedVersion(version.getId());

        if (!supported) {
            fabricCheckBox.setSelected(false);
            fabricCheckBox.setDisable(true);
            fabricCheckBox.setOpacity(0.4);
            if (isCustomLocal) {
                fabricCheckBox.setTooltip(new Tooltip("Phien ban cai thu cong da co san Mod Loader rieng."));
            } else {
                fabricCheckBox.setTooltip(new Tooltip("Fabric chi ho tro tu phien ban Minecraft 1.14 tro len."));
            }
        } else {
            fabricCheckBox.setDisable(false);
            fabricCheckBox.setOpacity(1.0);
            fabricCheckBox.setTooltip(new Tooltip("Tu dong cai va ghep Fabric Loader cho phien ban nay."));
        }
    }

    /**
     * Kiem tra phien ban co ho tro Fabric hay khong (Fabric ho tro tu 1.14 tro len;
     * cac ban 1.13.2, 1.12.2, 1.8.9, 1.7.10... tra ve false).
     */
    private boolean isFabricSupportedVersion(String versionId) {
        if (versionId == null || versionId.isBlank()) return false;
        String clean = versionId.split("-")[0].trim();

        if (clean.startsWith("1.")) {
            String[] parts = clean.split("\\.");
            if (parts.length >= 2) {
                try {
                    int minor = Integer.parseInt(parts[1]);
                    return minor >= 14;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        if (clean.startsWith("a1.") || clean.startsWith("b1.") || clean.startsWith("c0.")
                || clean.startsWith("rd-") || clean.startsWith("inf-")) {
            return false;
        }
        return true;
    }

    private void loadVersionsAsync(boolean keepCurrentSelection) {
        String previouslySelected = keepCurrentSelection && versionComboBox.getValue() != null
                ? versionComboBox.getValue().getId()
                : null;

        statusLabel.setText("Dang tai danh sach phien ban...");
        versionComboBox.setDisable(true);

        Task<List<MinecraftVersion>> task = new Task<>() {
            @Override
            protected List<MinecraftVersion> call() throws Exception {
                LauncherConfig config = ConfigManager.get();
                List<MinecraftVersion> versions = new java.util.ArrayList<>(versionManager.fetchFilteredVersions(config));

                // Chỉ chèn thêm các bản Mod (NeoForge, Forge, OptiFine...) nếu bật "Hiện bản Mod" trong Cài đặt
                if (config.isShowModified()) {
                    List<MinecraftVersion> customVersions = versionManager.scanCustomLocalVersions();

                    // [SỬA BUG #6 - HIỂN THỊ] Đưa các bản cài thủ công (NeoForge 1.21.1, Forge, OptiFine...)
                    // lên nằm ngay sát phía trên phiên bản gốc (vd 1.21.1) thay vì đẩy xuống đáy danh sách
                    for (MinecraftVersion custom : customVersions) {
                        String parentId = findCustomParentVersionId(custom.getId());
                        int insertIndex = 0; // Mặc định đưa lên đầu danh sách nếu không tìm thấy bản gốc
                        if (parentId != null) {
                            for (int i = 0; i < versions.size(); i++) {
                                if (versions.get(i).getId().equals(parentId)) {
                                    insertIndex = i;
                                    break;
                                }
                            }
                        }
                        versions.add(insertIndex, custom);
                    }
                }
                return versions;
            }
        };

        task.setOnSucceeded(e -> {
            List<MinecraftVersion> versions = task.getValue();
            versionComboBox.getItems().setAll(versions);

            String targetId = previouslySelected != null ? previouslySelected : ConfigManager.get().getSelectedVersion();
            MinecraftVersion toSelect = versions.stream()
                    .filter(v -> v.getId().equals(targetId))
                    .findFirst()
                    .orElse(versions.isEmpty() ? null : versions.get(0));
            versionComboBox.getSelectionModel().select(toSelect);
            updateFabricAvailability(toSelect);

            versionComboBox.setDisable(false);
            if (versionManager.isLastFetchOffline()) {
                if (versions.isEmpty()) {
                    statusLabel.setText("Che do Offline - Chua co phien ban nao duoc tai tren may");
                } else {
                    statusLabel.setText("Che do Offline (" + versions.size() + " phien ban da cai san)");
                }
            } else {
                statusLabel.setText("San sang");
            }
            LauncherLogger.info("Da nap " + versions.size() + " phien ban len giao dien.");
        });

        task.setOnFailed(e -> {
            LauncherLogger.error("Loi khi tai danh sach phien ban", task.getException());
            versionComboBox.setDisable(false);
            statusLabel.setText("Khong doc duoc danh sach phien ban: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "load-versions-thread");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Tìm phiên bản gốc (inheritsFrom) của NeoForge / Forge / OptiFine để xếp nằm cạnh nhau
     */
    private String findCustomParentVersionId(String customId) {
        try {
            Path dir = FileManager.versionsDir().resolve(customId);
            if (!Files.isDirectory(dir)) return null;
            try (var stream = Files.list(dir)) {
                Path jsonFile = stream.filter(p -> p.toString().endsWith(".json")).findFirst().orElse(null);
                if (jsonFile != null) {
                    JsonObject obj = JsonParser.parseString(Files.readString(jsonFile, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (obj.has("inheritsFrom")) {
                        return obj.get("inheritsFrom").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @FXML
    private void onRefreshVersionsClicked() {
        loadVersionsAsync(true);
    }

    @FXML
    private void onPlayClicked() {
        MinecraftVersion selected = versionComboBox.getValue();
        if (selected == null) {
            statusLabel.setText("Chua chon duoc phien ban nao");
            return;
        }
        // Neu chua dang nhap, hoi ngay ten nhan vat Offline de choi luon
        if (currentProfile == null || !currentProfile.isLoggedIn()) {
            if (!promptOfflineLogin()) {
                statusLabel.setText("Can dang nhap Offline hoac Microsoft truoc khi choi");
                return;
            }
        }

        LauncherLogger.info("Nguoi dung nhan PLAY voi phien ban: " + selected.getId());
        playButton.setDisable(true);
        versionComboBox.setDisable(true);
        downloadProgressBar.setVisible(true);
        statusLabel.setText("Dang kiem tra file cua " + selected.getId() + "...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                boolean isCustomLocal = "custom-local".equals(selected.getType());
                boolean useFabric = fabricCheckBox.isSelected() && !isCustomLocal && isFabricSupportedVersion(selected.getId());
                if (fabricCheckBox.isSelected() && isCustomLocal) {
                    LauncherLogger.warn("Bo qua tick Fabric vi '" + selected.getId()
                            + "' da la phien ban cai thu cong (OptiFine/Forge/NeoForge...).");
                }

                JsonObject detail;
                if (useFabric) {
                    try {
                        Platform.runLater(() -> statusLabel.setText("Dang tim ban Fabric Loader phu hop..."));
                        ModLoaderManager.FabricLoaderVersion fabricLoader =
                                modLoaderManager.fetchLatestStableFabricLoader(selected.getId());
                        LauncherLogger.info("Dung Fabric Loader " + fabricLoader.version() + " cho " + selected.getId());
                        detail = modLoaderManager.installFabric(selected, fabricLoader.version());
                    } catch (Exception fabricNetEx) {
                        // Nếu mất mạng khi tick chọn Fabric, tự tìm profile Fabric đã cài sẵn trên máy cho phiên bản này
                        MinecraftVersion localFabric = findInstalledFabricProfile(selected.getId());
                        if (localFabric != null) {
                            LauncherLogger.info("Khong co mang - dung Fabric da cai san tren may: " + localFabric.getId());
                            detail = versionManager.fetchVersionDetail(localFabric);
                        } else {
                            throw new IOException("Khong co mang de tai Fabric Loader moi cho " + selected.getId()
                                    + " (ban co the bo tick Fabric de chay Vanilla Offline).", fabricNetEx);
                        }
                    }
                } else {
                    detail = versionManager.fetchVersionDetail(selected);
                }

                // Kiểm tra và tải file thiếu; nếu đang mất mạng nhưng phiên bản đã có sẵn .jar trên máy thì cho phép vào thẳng game
                try {
                    DownloadManager.DownloadResult downloadResult = downloadManager.ensureVersionFiles(
                            selected, detail, (done, total) -> {
                                updateProgress(done, total);
                                Platform.runLater(() -> statusLabel.setText(
                                        "Dang tai " + selected.getId() + ": " + done + "/" + total + " file"));
                            });

                    if (downloadResult.failedFiles() > 0) {
                        if (isVersionJarAvailableLocally(selected.getId(), detail)) {
                            LauncherLogger.warn("Khong tai duoc " + downloadResult.failedFiles()
                                    + " file do mat mang, nhung da co san file game tren may -> Tiep tuc chay Offline!");
                        } else {
                            throw new IOException(downloadResult.failedFiles() + " file tai loi: " + downloadResult.errors());
                        }
                    }
                } catch (Exception dlEx) {
                    if (isVersionJarAvailableLocally(selected.getId(), detail)) {
                        LauncherLogger.warn("Bo qua kiem tra tai file online do mat mang, khoi chay Offline: " + dlEx.getMessage());
                    } else {
                        throw dlEx;
                    }
                }

                Platform.runLater(() -> statusLabel.setText("Dang chuan bi Java runtime..."));
                JavaRuntime javaRuntime;
                String customJavaPath = ConfigManager.get().getJavaPath();
                if (customJavaPath != null && !customJavaPath.isBlank()) {
                    javaRuntime = new JavaRuntime();
                    javaRuntime.setComponent("custom");
                    javaRuntime.setPath(customJavaPath.trim());
                    LauncherLogger.info("Dung duong dan Java tuy chinh tu Cai dat: " + customJavaPath);
                } else {
                    try {
                        String javaComponent = versionManager.getRequiredJavaComponent(detail);
                        int javaMajor = versionManager.getRequiredJavaMajorVersion(detail);
                        javaRuntime = javaManager.ensureRuntime(javaComponent, javaMajor);
                    } catch (Exception javaEx) {
                        // Dự phòng khi mất mạng: dùng bản Java có sẵn trong runtime/java hoặc hệ thống
                        LauncherLogger.warn("Khong tai duoc Java online, chuyen sang tim Java co san tren may: " + javaEx.getMessage());
                        javaRuntime = new JavaRuntime();
                        javaRuntime.setComponent("local-fallback");
                        javaRuntime.setPath(resolveSystemJavaBinary());
                    }
                }

                // [SỬA BUG #5] Tự động thêm Java vào Windows Graphics Settings ở chế độ High Performance
                ensureWindowsHighPerformanceGpu(javaRuntime);

                GameOptionsManager.Mode mode = switch (ConfigManager.get().getPerformanceMode()) {
                    case "ultrafast" -> GameOptionsManager.Mode.ULTRAFAST;
                    case "high_quality" -> GameOptionsManager.Mode.HIGH_QUALITY;
                    default -> GameOptionsManager.Mode.NORMAL;
                };
                new GameOptionsManager().applyMode(mode);

                // [SỬA BUG #2] Ẩn cửa sổ ME Launcher khi Minecraft bắt đầu chạy
                Platform.runLater(() -> {
                    downloadProgressBar.setVisible(false);
                    statusLabel.setText("Dang chay " + selected.getId() + "...");
                    Stage stage = (Stage) playButton.getScene().getWindow();
                    if (stage != null) {
                        stage.hide();
                    }
                });

                minecraftLauncher.launch(selected, detail, javaRuntime, currentProfile,
                        line -> LauncherLogger.info("[MC] " + line));

                return null;
            }
        };

        downloadProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            // [SỬA BUG #2] Hiện lại cửa sổ ME Launcher khi đóng Minecraft
            Stage stage = (Stage) playButton.getScene().getWindow();
            if (stage != null) {
                stage.show();
                stage.toFront();
            }
            downloadProgressBar.progressProperty().unbind();
            downloadProgressBar.setVisible(false);
            playButton.setDisable(false);
            versionComboBox.setDisable(false);
            statusLabel.setText("Da dong Minecraft (" + selected.getId() + ")");
        });

        task.setOnFailed(e -> {
            // [SỬA BUG #2] Hiện lại cửa sổ ME Launcher nếu game bị lỗi
            Stage stage = (Stage) playButton.getScene().getWindow();
            if (stage != null) {
                stage.show();
                stage.toFront();
            }
            downloadProgressBar.progressProperty().unbind();
            downloadProgressBar.setVisible(false);
            playButton.setDisable(false);
            versionComboBox.setDisable(false);
            LauncherLogger.error("Loi khi chay " + selected.getId(), task.getException());
            statusLabel.setText("Loi: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "play-thread");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Kiểm tra xem phiên bản (hoặc bản gốc inheritsFrom của nó) đã có sẵn file .jar trên ổ cứng chưa
     */
    private boolean isVersionJarAvailableLocally(String versionId, JsonObject detail) {
        if (Files.exists(FileManager.versionJar(versionId))) {
            return true;
        }
        if (detail != null && detail.has("inheritsFrom")) {
            String parentId = detail.get("inheritsFrom").getAsString();
            if (Files.exists(FileManager.versionJar(parentId))) {
                return true;
            }
        }
        if (detail != null && detail.has("id")) {
            String detailId = detail.get("id").getAsString();
            return Files.exists(FileManager.versionJar(detailId));
        }
        return false;
    }

    /**
     * Tìm profile Fabric đã cài sẵn trong game/versions/ khi người dùng bật tick Fabric lúc không có mạng
     */
    private MinecraftVersion findInstalledFabricProfile(String gameVersionId) {
        Path versionsDir = FileManager.versionsDir();
        if (!Files.exists(versionsDir)) return null;
        try (var stream = Files.list(versionsDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String folderName = dir.getFileName().toString();
                if (folderName.startsWith("fabric-loader-") && folderName.endsWith("-" + gameVersionId)) {
                    MinecraftVersion v = new MinecraftVersion();
                    v.setId(folderName);
                    v.setType("custom-local");
                    v.setUrl("");
                    return v;
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * [SỬA BUG #5] Tự động đăng ký javaw.exe vào Windows Graphics Settings (High Performance GPU)
     */
    private void ensureWindowsHighPerformanceGpu(JavaRuntime javaRuntime) {
        if (Utils.getOperatingSystem() != Utils.OS.WINDOWS || javaRuntime == null) return;
        try {
            String rawPath = javaRuntime.getPath();
            if (rawPath == null || rawPath.isBlank()) return;
            rawPath = rawPath.replace("\"", "").trim();

            Path candidate = Path.of(rawPath);
            if (Files.isDirectory(candidate)) {
                if (Files.exists(candidate.resolve("bin").resolve("javaw.exe"))) {
                    candidate = candidate.resolve("bin").resolve("javaw.exe");
                } else if (Files.exists(candidate.resolve("javaw.exe"))) {
                    candidate = candidate.resolve("javaw.exe");
                }
            }
            Path absJavaExe = candidate.toAbsolutePath().normalize();
            if (!Files.exists(absJavaExe)) return;

            new ProcessBuilder(
                    "reg", "add",
                    "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
                    "/v", absJavaExe.toString(),
                    "/t", "REG_SZ",
                    "/d", "GpuPreference=2;",
                    "/f"
            ).start().waitFor();
            LauncherLogger.info("Da kich hoat Windows Graphics High Performance cho: " + absJavaExe);
        } catch (Exception e) {
            LauncherLogger.warn("Khong the tu dong dat GPU High Performance: " + e.getMessage());
        }
    }

    /**
     * [NÂNG CẤP] Hộp thoại Cài đặt Mod rõ ràng, có Logo riêng từng Mod Loader
     * và nút Convert mrpack sang zip (https://fabulously-optimized.github.io/mrpack-to-zip/).
     */
    @FXML
    private void onModLoaderHelpClicked() {
        String gamePath = FileManager.gameDir().toAbsolutePath().normalize().toString();

        Stage dialog = new Stage();
        dialog.setTitle("Cài đặt Mod Loader & Chuyển đổi Modpack");
        dialog.initOwner(playButton.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(false);
        LauncherApplication.loadWindowIcons(dialog);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setPrefWidth(640);
        root.setStyle("-fx-background-color: #1b1f24; -fx-border-color: #33383f; -fx-border-width: 1px; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px;");

        Label titleLabel = new Label("CÀI ĐẶT MOD LOADER & CONVERT MRPACK");
        titleLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #7ed957;");

        Label introLabel = new Label(
                "OptiFine, Forge, NeoForge và Quilt cần cài thủ công 1 lần; với gói Modrinth (.mrpack) " +
                "bạn có thể chuyển sang .zip để chép mod nhanh hơn:"
        );
        introLabel.setWrapText(true);
        introLabel.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #d1d5db;");

        // 1. Hàng nút có Logo riêng từng Mod Loader
        Label step1Label = new Label("1. Chọn Mod Loader để tải bộ cài (.jar) hoặc bấm Convert mrpack:");
        step1Label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        HBox modButtonsRow = new HBox(10);
        modButtonsRow.getChildren().addAll(
                createModLogoButton("OptiFine", "OF", "#e53935", "/assets/optifine.png",
                        "https://optifine.net/downloads"),
                createModLogoButton("Forge", "FG", "#d97706", "/assets/forge.png",
                        "https://files.minecraftforge.net/"),
                createModLogoButton("NeoForge", "NF", "#ea580c", "/assets/neoforge.png",
                        "https://neoforged.net/"),
                createModLogoButton("Quilt", "QL", "#7c3aed", "/assets/quilt.png",
                        "https://quiltmc.org/en/install/")
        );

        Button btnConvertMrpack = createModLogoButton(
                "Convert mrpack", "MR", "#10b981", "/assets/mrpack.png",
                MRPACK_CONVERTER_URL
        );
        btnConvertMrpack.setMaxWidth(Double.MAX_VALUE);

        // 2. Đường dẫn thư mục game để dán khi cài Mod Loader
        Label step2Label = new Label("2. Sao chép đường dẫn thư mục game (dán bằng Ctrl + V khi trình cài đặt hỏi thư mục):");
        step2Label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        TextField pathField = new TextField(gamePath);
        pathField.setEditable(false);
        pathField.setStyle("-fx-background-color: #111418; -fx-text-fill: #e6e6e6; -fx-border-color: #3f454d; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 7 10 7 10;");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button btnCopyPath = new Button("Copy đường dẫn");
        btnCopyPath.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 7 14 7 14; -fx-cursor: hand;");
        btnCopyPath.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(gamePath);
            Clipboard.getSystemClipboard().setContent(content);
            btnCopyPath.setText("Đã Copy!");
            btnCopyPath.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; "
                    + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 7 14 7 14; -fx-cursor: hand;");
            LauncherLogger.info("Da copy duong dan thu muc game/ vao clipboard.");
        });

        HBox pathRow = new HBox(8, pathField, btnCopyPath);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        // 3. Chạy bộ cài (.jar) hoặc mở thư mục mods
        Label step3Label = new Label("3. Chạy file installer (.jar) vừa tải hoặc mở thư mục mods:");
        step3Label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label step3Hint = new Label(
                "Sau khi cài Mod Loader xong, bấm nút làm mới (🔄) cạnh ô chọn phiên bản để phiên bản mới hiện ra."
        );
        step3Hint.setWrapText(true);
        step3Hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #b0b6bd;");

        Button btnRunInstaller = new Button("Chạy file installer da tai");
        btnRunInstaller.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 8 16 8 16; -fx-cursor: hand;");
        btnRunInstaller.setOnAction(e -> {
            dialog.close();
            runModLoaderInstallerJar();
        });

        Button btnOpenMods = new Button("Mở thư mục mods");
        btnOpenMods.setStyle("-fx-background-color: #33383f; -fx-text-fill: #e6e6e6; -fx-font-weight: bold; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 8 14 8 14; -fx-cursor: hand;");
        btnOpenMods.setOnAction(e -> {
            try {
                Path modsDir = FileManager.gameDir().resolve("mods").toAbsolutePath().normalize();
                Files.createDirectories(modsDir);
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(modsDir.toFile());
                }
            } catch (Exception ex) {
                LauncherLogger.warn("Khong mo duoc thu muc mods: " + ex.getMessage());
            }
        });

        Button btnClose = new Button("Dong");
        btnClose.setStyle("-fx-background-color: #3f454d; -fx-text-fill: #e6e6e6; -fx-font-weight: bold; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 8 18 8 18; -fx-cursor: hand;");
        btnClose.setOnAction(e -> dialog.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionRow = new HBox(10, btnRunInstaller, btnOpenMods, spacer, btnClose);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(
                titleLabel,
                introLabel,
                step1Label,
                modButtonsRow,
                btnConvertMrpack,
                step2Label,
                pathRow,
                step3Label,
                step3Hint,
                actionRow
        );

        Scene scene = new Scene(root);
        Utils.applySquareTheme(scene);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /**
     * Tạo nút bấm góc vuông 90 độ kèm Logo riêng của từng Mod Loader / công cụ bên cạnh chữ.
     */
    private Button createModLogoButton(String labelText, String shortCode, String accentHex,
                                       String resourceImagePath, String url) {
        Node logoGraphic = buildModLogoGraphic(shortCode, accentHex, resourceImagePath);
        Button btn = new Button(labelText, logoGraphic);
        btn.setGraphicTextGap(8);
        btn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btn, Priority.ALWAYS);

        String baseStyle = "-fx-background-color: #262b33; -fx-text-fill: #ffffff; -fx-font-weight: bold; "
                + "-fx-font-size: 13px; -fx-border-color: " + accentHex + "; -fx-border-width: 0 0 0 3px; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 8 12 8 12; "
                + "-fx-cursor: hand; -fx-alignment: CENTER_LEFT;";
        String hoverStyle = "-fx-background-color: #33383f; -fx-text-fill: #ffffff; -fx-font-weight: bold; "
                + "-fx-font-size: 13px; -fx-border-color: " + accentHex + "; -fx-border-width: 0 0 0 3px; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px; -fx-padding: 8 12 8 12; "
                + "-fx-cursor: hand; -fx-alignment: CENTER_LEFT;";

        btn.setStyle(baseStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(baseStyle));
        btn.setOnAction(e -> openBrowserSafely(url));
        return btn;
    }

    private Node buildModLogoGraphic(String shortCode, String accentHex, String resourceImagePath) {
        try (var is = getClass().getResourceAsStream(resourceImagePath)) {
            if (is != null) {
                Image img = new Image(is, 20, 20, true, true);
                if (!img.isError() && img.getWidth() > 0) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(20);
                    iv.setFitHeight(20);
                    return iv;
                }
            }
        } catch (Exception ignored) {}

        Label badge = new Label(shortCode);
        badge.setMinSize(22, 22);
        badge.setMaxSize(22, 22);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color: " + accentHex + "; -fx-text-fill: #ffffff; "
                + "-fx-font-size: 10px; -fx-font-weight: bold; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px;");
        return badge;
    }

    /**
     * [ĐÃ SỬA] Mở bộ cài Forge / NeoForge / OptiFine (.jar) bằng Java đầy đủ
     * và tự động làm mới danh sách phiên bản ngay khi cài xong.
     */
    private void runModLoaderInstallerJar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chon file installer (.jar) da tai ve");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File .jar", "*.jar"));

        File selected = chooser.showOpenDialog(playButton.getScene().getWindow());
        if (selected == null) return;

        // Đảm bảo có sẵn launcher_profiles.json ở cả thư mục game/ và .minecraft mặc định
        FileManager.ensureLauncherProfilesExist();
        if (Utils.getOperatingSystem() == Utils.OS.WINDOWS) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                try {
                    Path defaultMcProfile = Path.of(appData, ".minecraft", "launcher_profiles.json");
                    if (!Files.exists(defaultMcProfile)) {
                        Files.createDirectories(defaultMcProfile.getParent());
                        Path gameProfile = FileManager.gameDir().resolve("launcher_profiles.json");
                        if (Files.exists(gameProfile)) {
                            Files.copy(gameProfile, defaultMcProfile);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Tự động copy sẵn đường dẫn thư mục game/ vào Clipboard để người dùng chỉ cần bấm Ctrl+V
        String gamePath = FileManager.gameDir().toAbsolutePath().normalize().toString();
        ClipboardContent content = new ClipboardContent();
        content.putString(gamePath);
        Clipboard.getSystemClipboard().setContent(content);

        statusLabel.setText("Dang chuan bi Java de mo trinh cai dat " + selected.getName() + "...");

        Task<Integer> installerTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                String javaBinary = resolveSystemJavaBinary();
                LauncherLogger.info("Da chay installer: " + selected.getAbsolutePath() + " (bang " + javaBinary + ")");

                Platform.runLater(() -> statusLabel.setText(
                        "Dang mo trinh cai dat... (Da copy san duong dan '" + gamePath + "', hay Ctrl+V khi chon thu muc)."));

                ProcessBuilder pb = new ProcessBuilder(javaBinary, "-jar", selected.getAbsolutePath());
                pb.directory(selected.getParentFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LauncherLogger.info("[Installer] " + line);
                    }
                }
                return process.waitFor();
            }
        };

        installerTask.setOnSucceeded(e -> {
            statusLabel.setText("Da dong bo cai " + selected.getName() + " - Dang cap nhat danh sach phien ban...");
            loadVersionsAsync(true);
        });

        installerTask.setOnFailed(e -> {
            Throwable ex = installerTask.getException();
            LauncherLogger.error("Khong chay duoc file installer", ex);
            statusLabel.setText("Khong chay duoc file installer: " + ex.getMessage());
            Alert errAlert = new Alert(Alert.AlertType.ERROR, "Khong chay duoc file installer: " + ex.getMessage());
            Utils.applySquareTheme(errAlert);
            errAlert.showAndWait();
        });

        Thread thread = new Thread(installerTask, "installer-jar-thread");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * [ĐÃ SỬA] Tìm bản Java đầy đủ (có giao diện Swing) để chạy bộ cài Forge / NeoForge
     */
    private String resolveSystemJavaBinary() {
        boolean isWin = Utils.getOperatingSystem() == Utils.OS.WINDOWS;
        String exeName = isWin ? "javaw.exe" : "java";
        String fallbackExe = isWin ? "java.exe" : "java";

        // 1. Kiểm tra đường dẫn Java tùy chỉnh trong Cài đặt
        String customPath = ConfigManager.get().getJavaPath();
        if (customPath != null && !customPath.isBlank()) {
            Path p = Path.of(customPath.replace("\"", "").trim());
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize().toString();
            }
            if (Files.isDirectory(p)) {
                if (Files.isRegularFile(p.resolve("bin").resolve(exeName))) {
                    return p.resolve("bin").resolve(exeName).toAbsolutePath().normalize().toString();
                }
                if (Files.isRegularFile(p.resolve("bin").resolve(fallbackExe))) {
                    return p.resolve("bin").resolve(fallbackExe).toAbsolutePath().normalize().toString();
                }
                if (Files.isRegularFile(p.resolve(exeName))) {
                    return p.resolve(exeName).toAbsolutePath().normalize().toString();
                }
            }
        }

        // 2. Tìm trong thư mục runtime/java/ (bản Java đầy đủ do Mojang tải về)
        Path mojangRuntimeDir = Path.of("runtime", "java").toAbsolutePath().normalize();
        if (Files.isDirectory(mojangRuntimeDir)) {
            try (var stream = Files.walk(mojangRuntimeDir, 4)) {
                Optional<Path> found = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.equalsIgnoreCase(exeName) || name.equalsIgnoreCase(fallbackExe);
                        })
                        .findFirst();
                if (found.isPresent()) {
                    return found.get().toAbsolutePath().normalize().toString();
                }
            } catch (IOException ignored) {}
        }

        // 3. Kiểm tra JAVA_HOME trên hệ thống
        String envJavaHome = System.getenv("JAVA_HOME");
        if (envJavaHome != null && !envJavaHome.isBlank()) {
            Path envBin = Path.of(envJavaHome.replace("\"", "").trim(), "bin", exeName);
            if (Files.isRegularFile(envBin)) {
                return envBin.toAbsolutePath().normalize().toString();
            }
        }

        // 4. Nếu máy chưa có bản Java đầy đủ nào, tự động tải Java 21 của Mojang về runtime/java/
        try {
            Platform.runLater(() -> statusLabel.setText("Dang tai Java 21 day du de mo bo cai Forge/NeoForge..."));
            JavaRuntime downloaded = javaManager.ensureRuntime("java-runtime-delta", 21);
            Path downloadedBin = Path.of(downloaded.getPath(), "bin", exeName);
            if (Files.isRegularFile(downloadedBin)) {
                return downloadedBin.toAbsolutePath().normalize().toString();
            }
        } catch (Exception e) {
            LauncherLogger.warn("Khong tu tai duoc Java 21 cho installer: " + e.getMessage());
        }

        // 5. Dự phòng cuối cùng: dùng java.home hiện tại
        String javaHome = System.getProperty("java.home");
        return Path.of(javaHome, "bin", exeName).toString();
    }

    @FXML
    private void onSettingsClicked() {
        LauncherLogger.info("Mo cua so Cai dat");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/settings.fxml"));
            Parent root = loader.load();

            Stage settingsStage = new Stage();
            settingsStage.setTitle("Cai dat");
            settingsStage.initOwner(settingsButton.getScene().getWindow());
            settingsStage.initModality(Modality.WINDOW_MODAL);
            settingsStage.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(settingsButton.getScene().getStylesheets());
            Utils.applySquareTheme(scene);
            settingsStage.setScene(scene);
            settingsStage.showAndWait();

            // [SỬA BUG #1] Đảm bảo lưu lại file config ngay sau khi đóng cửa sổ Cài đặt
            ConfigManager.save();

            // Tự động làm mới lại danh sách phiên bản theo các ô tích (Snapshots, Beta, Alpha, Bản cũ, Bản Mod)
            loadVersionsAsync(true);
        } catch (IOException e) {
            LauncherLogger.error("Khong mo duoc Cai dat", e);
        }
    }

    @FXML
    private void onLoginClicked() {
        LauncherLogger.info("Nguoi dung nhan Dang nhap");

        Alert choiceAlert = new Alert(Alert.AlertType.CONFIRMATION);
        choiceAlert.setTitle("Chon phuong thuc dang nhap");
        choiceAlert.setHeaderText("Ban muon su dung tai khoan nao?");
        choiceAlert.setContentText("Chon 'Choi Offline (Crack)' de nhap ten nhan vat bat ky va choi ngay, hoac 'Tai khoan Microsoft' neu ban co ban quyen.");

        ButtonType btnOffline = new ButtonType("Choi Offline (Crack)");
        ButtonType btnMicrosoft = new ButtonType("Tai khoan Microsoft");
        ButtonType btnCancel = new ButtonType("Huy", ButtonBar.ButtonData.CANCEL_CLOSE);
        choiceAlert.getButtonTypes().setAll(btnOffline, btnMicrosoft, btnCancel);

        Utils.applySquareTheme(choiceAlert);
        Optional<ButtonType> result = choiceAlert.showAndWait();
        if (result.isEmpty() || result.get() == btnCancel) return;

        if (result.get() == btnOffline) {
            promptOfflineLogin();
        } else if (result.get() == btnMicrosoft) {
            startMicrosoftLogin();
        }
    }

    private boolean promptOfflineLogin() {
        String defaultName = (currentProfile != null && currentProfile.getUsername() != null)
                ? currentProfile.getUsername() : "Player";

        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setTitle("Dang nhap Offline (Crack)");
        dialog.setHeaderText("Nhap ten nhan vat Minecraft cua ban:");
        dialog.setContentText("Ten nhan vat (3 - 16 ky tu):");

        Utils.applySquareTheme(dialog);
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String username = result.get().trim();
            currentProfile = authManager.createOfflineProfile(username);
            // [SỬA BUG #1] Lưu tên Crack ra file offline_account.json ngay lập tức
            saveOfflineProfile(username);

            profileLabel.setText("Offline: " + currentProfile.getUsername());
            loginButton.setText("Doi tai khoan");
            LauncherLogger.info("Da dang nhap Offline voi ten: " + currentProfile.getUsername()
                    + " (UUID: " + currentProfile.getUuid() + ")");
            return true;
        }
        return false;
    }

    private void startMicrosoftLogin() {
        loginButton.setDisable(true);
        profileLabel.setText("Dang bat dau dang nhap...");

        Task<UserProfile> task = new Task<>() {
            @Override
            protected UserProfile call() throws Exception {
                AuthManager.DeviceCodeInfo deviceCode = authManager.requestDeviceCode();

                Platform.runLater(() -> {
                    profileLabel.setText("Vao " + deviceCode.verificationUri() + " - ma: " + deviceCode.userCode());
                    openBrowserSafely(deviceCode.verificationUri());
                });

                return authManager.pollUntilLoggedIn(deviceCode,
                        status -> Platform.runLater(() -> profileLabel.setText(status)));
            }
        };

        task.setOnSucceeded(e -> {
            currentProfile = task.getValue();
            profileLabel.setText("Da dang nhap: " + currentProfile.getUsername());
            loginButton.setText("Doi tai khoan");
            loginButton.setDisable(false);
            LauncherLogger.info("Dang nhap thanh cong: " + currentProfile.getUsername());
            loadSkinFace(currentProfile.getSkinUrl());
        });

        task.setOnFailed(e -> {
            LauncherLogger.error("Dang nhap that bai", task.getException());
            profileLabel.setText("Dang nhap loi: " + task.getException().getMessage());
            loginButton.setDisable(false);
        });

        Thread thread = new Thread(task, "login-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadSkinFace(String skinUrl) {
        if (skinUrl == null || skinUrl.isBlank()) return;

        Thread thread = new Thread(() -> {
            try {
                Image fullSkin = new Image(skinUrl);
                Platform.runLater(() -> {
                    skinImageView.setImage(fullSkin);
                    skinImageView.setViewport(new Rectangle2D(8, 8, 8, 8));
                });
            } catch (Exception e) {
                LauncherLogger.warn("Khong tai duoc anh skin: " + e.getMessage());
            }
        }, "skin-load-thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void openBrowserSafely(String uri) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(uri));
            }
        } catch (Exception e) {
            LauncherLogger.warn("Khong tu mo duoc trinh duyet, hay tu mo thu cong: " + uri);
        }
    }
}
