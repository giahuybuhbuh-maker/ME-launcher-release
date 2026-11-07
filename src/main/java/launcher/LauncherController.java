package launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.JavaRuntime;
import model.MinecraftVersion;
import model.UserProfile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class LauncherController {

    private static final Path OFFLINE_ACCOUNT_FILE = Path.of("offline_account.json").toAbsolutePath().normalize();

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

        statusLabel.setText("Dang tai danh sach phien ban tu Mojang...");
        versionComboBox.setDisable(true);

        Task<List<MinecraftVersion>> task = new Task<>() {
            @Override
            protected List<MinecraftVersion> call() throws Exception {
                List<MinecraftVersion> releases = new java.util.ArrayList<>(versionManager.fetchReleaseVersions());
                List<MinecraftVersion> customVersions = versionManager.scanCustomLocalVersions();

                // [SỬA BUG #6 - HIỂN THỊ] Đưa các bản cài thủ công (NeoForge 1.21.1, Forge, OptiFine...)
                // lên nằm ngay sát phía trên phiên bản gốc (vd 1.21.1) thay vì đẩy xuống đáy danh sách
                for (MinecraftVersion custom : customVersions) {
                    String parentId = findCustomParentVersionId(custom.getId());
                    int insertIndex = 0; // Mặc định đưa lên đầu danh sách nếu không tìm thấy bản gốc
                    if (parentId != null) {
                        for (int i = 0; i < releases.size(); i++) {
                            if (releases.get(i).getId().equals(parentId)) {
                                insertIndex = i;
                                break;
                            }
                        }
                    }
                    releases.add(insertIndex, custom);
                }
                return releases;
            }
        };

        task.setOnSucceeded(e -> {
            List<MinecraftVersion> releases = task.getValue();
            versionComboBox.getItems().setAll(releases);

            String targetId = previouslySelected != null ? previouslySelected : ConfigManager.get().getSelectedVersion();
            MinecraftVersion toSelect = releases.stream()
                    .filter(v -> v.getId().equals(targetId))
                    .findFirst()
                    .orElse(releases.isEmpty() ? null : releases.get(0));
            versionComboBox.getSelectionModel().select(toSelect);
            updateFabricAvailability(toSelect);

            versionComboBox.setDisable(false);
            statusLabel.setText("San sang");
            LauncherLogger.info("Da nap " + releases.size() + " phien ban len giao dien.");
        });

        task.setOnFailed(e -> {
            LauncherLogger.error("Khong tai duoc danh sach phien ban tu Mojang", task.getException());
            statusLabel.setText("Khong ket noi duoc Mojang - kiem tra mang roi mo lai launcher");
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
                    JsonObject obj = JsonParser.parseString(Files.readString(jsonFile)).getAsJsonObject();
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
                    Platform.runLater(() -> statusLabel.setText("Dang tim ban Fabric Loader phu hop..."));
                    ModLoaderManager.FabricLoaderVersion fabricLoader =
                            modLoaderManager.fetchLatestStableFabricLoader(selected.getId());
                    LauncherLogger.info("Dung Fabric Loader " + fabricLoader.version() + " cho " + selected.getId());
                    detail = modLoaderManager.installFabric(selected, fabricLoader.version());
                } else {
                    detail = versionManager.fetchVersionDetail(selected);
                }

                DownloadManager.DownloadResult downloadResult = downloadManager.ensureVersionFiles(
                        selected, detail, (done, total) -> {
                            updateProgress(done, total);
                            Platform.runLater(() -> statusLabel.setText(
                                    "Dang tai " + selected.getId() + ": " + done + "/" + total + " file"));
                        });

                if (downloadResult.failedFiles() > 0) {
                    throw new IOException(downloadResult.failedFiles() + " file tai loi: " + downloadResult.errors());
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
                    String javaComponent = versionManager.getRequiredJavaComponent(detail);
                    int javaMajor = versionManager.getRequiredJavaMajorVersion(detail);
                    javaRuntime = javaManager.ensureRuntime(javaComponent, javaMajor);
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

    @FXML
    private void onModLoaderHelpClicked() {
        String gamePath = FileManager.gameDir().toAbsolutePath().normalize().toString();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Cai OptiFine / Forge / NeoForge / Quilt");
        alert.setHeaderText("Cac mod loader nay can cai THU CONG 1 lan, launcher chua tu dong hoa duoc:");
        alert.setContentText(
                "1. Bam nut ben duoi de mo dung trang tai (hoac copy duong dan roi tu mo trinh duyet)\n" +
                "2. Tai file installer (.jar) chinh chu cua ho ve may\n" +
                "3. Bam nut \"Chay file installer da tai\" ben duoi, chon dung file .jar vua tai -\n" +
                "   launcher se tu chay giup, khong can tu go lenh java -jar\n" +
                "4. Khi no hoi chon thu muc \".minecraft\", dan dung duong dan nay:\n\n" +
                gamePath + "\n\n" +
                "5. Cai xong, mo lai ME Launcher (hoac bam nut 🔄 canh o chon phien ban) -\n" +
                "   phien ban moi se tu hien ra de chon."
        );
        alert.getDialogPane().setPrefWidth(500);

        ButtonType btnOptifine = new ButtonType("Mo OptiFine.net");
        ButtonType btnForge = new ButtonType("Mo Forge");
        ButtonType btnNeoForge = new ButtonType("Mo NeoForge");
        ButtonType btnQuilt = new ButtonType("Mo Quilt");
        ButtonType btnCopyPath = new ButtonType("Copy duong dan thu muc");
        ButtonType btnRunInstaller = new ButtonType("Chay file installer da tai");
        ButtonType btnClose = new ButtonType("Dong", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnOptifine, btnForge, btnNeoForge, btnQuilt, btnCopyPath, btnRunInstaller, btnClose);

        Optional<ButtonType> result = alert.showAndWait();
        result.ifPresent(button -> {
            if (button == btnOptifine) openBrowserSafely("https://optifine.net/downloads");
            else if (button == btnForge) openBrowserSafely("https://files.minecraftforge.net/");
            else if (button == btnNeoForge) openBrowserSafely("https://neoforged.net/");
            else if (button == btnQuilt) openBrowserSafely("https://quiltmc.org/en/install/");
            else if (button == btnCopyPath) {
                ClipboardContent content = new ClipboardContent();
                content.putString(gamePath);
                Clipboard.getSystemClipboard().setContent(content);
                LauncherLogger.info("Da copy duong dan thu muc game/ vao clipboard.");
            } else if (button == btnRunInstaller) {
                runModLoaderInstallerJar();
            }
        });
    }

    private void runModLoaderInstallerJar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chon file installer (.jar) da tai ve");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File .jar", "*.jar"));

        File selected = chooser.showOpenDialog(playButton.getScene().getWindow());
        if (selected == null) return;

        try {
            String javaBinary = resolveSystemJavaBinary();
            new ProcessBuilder(javaBinary, "-jar", selected.getAbsolutePath())
                    .directory(selected.getParentFile())
                    .start();
            LauncherLogger.info("Da chay installer: " + selected.getAbsolutePath() + " (bang " + javaBinary + ")");
            statusLabel.setText("Dang mo trinh cai dat... lam theo huong dan trong cua so moi hien ra.");
        } catch (IOException e) {
            LauncherLogger.error("Khong chay duoc file installer", e);
            new Alert(Alert.AlertType.ERROR, "Khong chay duoc file installer: " + e.getMessage()).showAndWait();
        }
    }

    private String resolveSystemJavaBinary() {
        String customPath = ConfigManager.get().getJavaPath();
        if (customPath != null && !customPath.isBlank()) {
            return customPath;
        }
        String javaHome = System.getProperty("java.home");
        String exeName = Utils.getOperatingSystem() == Utils.OS.WINDOWS ? "javaw.exe" : "java";
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
            settingsStage.setScene(scene);
            settingsStage.showAndWait();

            // [SỬA BUG #1] Đảm bảo lưu lại file config ngay sau khi đóng cửa sổ Cài đặt
            ConfigManager.save();
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