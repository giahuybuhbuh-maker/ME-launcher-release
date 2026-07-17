package launcher;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.JavaRuntime;
import model.MinecraftVersion;
import model.UserProfile;

import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;

public class LauncherController {

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


    /** Tai danh sach phien ban tu Mojang tren luong nen, khong lam dung giao dien. */
    private void loadVersionsAsync() {
        statusLabel.setText("Dang tai danh sach phien ban tu Mojang...");
        versionComboBox.setDisable(true);

        Task<List<MinecraftVersion>> task = new Task<>() {
            @Override
            protected List<MinecraftVersion> call() throws Exception {
                return versionManager.fetchReleaseVersions();
            }
        };

        task.setOnSucceeded(e -> {
            List<MinecraftVersion> releases = task.getValue();
            versionComboBox.getItems().setAll(releases);

            String savedVersion = ConfigManager.get().getSelectedVersion();
            MinecraftVersion toSelect = releases.stream()
                    .filter(v -> v.getId().equals(savedVersion))
                    .findFirst()
                    .orElse(releases.isEmpty() ? null : releases.get(0));
            versionComboBox.getSelectionModel().select(toSelect);

            versionComboBox.setDisable(false);
            statusLabel.setText("San sang");
            LauncherLogger.info("Da nap " + releases.size() + " phien ban release len giao dien.");
        });

        task.setOnFailed(e -> {
            LauncherLogger.error("Khong tai duoc danh sach phien ban tu Mojang", task.getException());
            statusLabel.setText("Khong ket noi duoc Mojang - kiem tra mang roi mo lai launcher");
        });

        Thread thread = new Thread(task, "version-fetch-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onPlayClicked() {
        MinecraftVersion selected = versionComboBox.getValue();
        if (selected == null) {
            statusLabel.setText("Chua chon duoc phien ban nao");
            return;
        }
        if (currentProfile == null || !currentProfile.isLoggedIn()) {
            statusLabel.setText("Can dang nhap Microsoft truoc (bam nut Dang nhap)");
            return;
        }

        LauncherLogger.info("Nguoi dung nhan PLAY voi phien ban: " + selected.getId());
        playButton.setDisable(true);
        versionComboBox.setDisable(true);
        downloadProgressBar.setVisible(true);
        statusLabel.setText("Dang kiem tra file cua " + selected.getId() + "...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                boolean useFabric = fabricCheckBox.isSelected();

                // Fetch 1 lan duy nhat (hoac gop voi Fabric), dung chung cho tai file,
                // chuan bi JRE, va chay game.
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
                    javaRuntime.setPath(customJavaPath);
                    LauncherLogger.info("Dung duong dan Java tuy chinh tu Cai dat: " + customJavaPath);
                } else {
                    String javaComponent = versionManager.getRequiredJavaComponent(detail);
                    int javaMajor = versionManager.getRequiredJavaMajorVersion(detail);
                    javaRuntime = javaManager.ensureRuntime(javaComponent, javaMajor);
                }

                Platform.runLater(() -> {
                    downloadProgressBar.setVisible(false);
                    statusLabel.setText("Dang khoi chay " + selected.getId() + "...");
                });

                // Chan (block) o day cho toi khi Minecraft dong lai.
                minecraftLauncher.launch(selected, detail, javaRuntime, currentProfile,
                        line -> LauncherLogger.info("[MC] " + line));

                return null;
            }
        };

        downloadProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            downloadProgressBar.progressProperty().unbind();
            downloadProgressBar.setVisible(false);
            playButton.setDisable(false);
            versionComboBox.setDisable(false);
            statusLabel.setText("Da dong Minecraft (" + selected.getId() + ")");
        });

        task.setOnFailed(e -> {
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
        } catch (IOException e) {
            LauncherLogger.error("Khong mo duoc Cai dat", e);
        }
    }

    @FXML
    public void initialize() {
        LauncherLogger.info("Khoi tao giao dien launcher...");
        downloadProgressBar.setVisible(false);

        // Giữ nguyên đoạn code cũ của bạn về version
        versionComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                ConfigManager.get().setSelectedVersion(newVal.getId());
                ConfigManager.save();
            }
        });
        
        loadVersionsAsync();

        // THÊM MỚI: Đọc tên đã lưu từ bộ nhớ đệm
        Preferences prefs = Preferences.userNodeForPackage(LauncherController.class);
        String savedUsername = prefs.get("offline_username", "");
        
        // Nếu đã từng đăng nhập trước đó thì tự động load luôn
        if (!savedUsername.isEmpty()) {
            currentProfile = authManager.loginOffline(savedUsername);
            profileLabel.setText("Da dang nhap: " + currentProfile.getUsername());
            loginButton.setText("Doi ten");
        }
    }

    @FXML
    private void onLoginClicked() {
        LauncherLogger.info("Nguoi dung nhan Dang nhap Offline");

        // Lấy lại tên cũ để điền sẵn vào ô cho người chơi đỡ phải gõ lại
        Preferences prefs = Preferences.userNodeForPackage(LauncherController.class);
        String savedUsername = prefs.get("offline_username", "Steve");

        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(savedUsername);
        dialog.setTitle("Dang nhap Launcher");
        dialog.setHeaderText("Che do choi Offline (Crack)");
        dialog.setContentText("Vui long nhap ten nhan vat cua ban:");

        java.util.Optional<String> result = dialog.showAndWait();
        
        result.ifPresent(username -> {
            String finalName = username.trim();
            if (!finalName.isEmpty()) {
                currentProfile = authManager.loginOffline(finalName);
                
                // THÊM MỚI: Lưu tên mới nhập vào bộ nhớ đệm
                prefs.put("offline_username", finalName);
                
                profileLabel.setText("Da dang nhap: " + currentProfile.getUsername());
                loginButton.setText("Doi ten");
                loginButton.setDisable(false);
                LauncherLogger.info("Da tao ho so Offline cho: " + currentProfile.getUsername());
            }
        });
    }

    /** Tai anh skin tu URL that (cua tai khoan Microsoft da dang nhap) va chi hien phan mat (8x8 px trong texture 64x64). */
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

