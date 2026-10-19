package launcher;

import com.google.gson.JsonObject;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    @FXML
    public void initialize() {
        LauncherLogger.info("Khoi tao giao dien launcher...");

        downloadProgressBar.setVisible(false);
        FileManager.ensureLauncherProfilesExist();

        // Khi doi phien ban (ke ca lan chon dau tien tu code), luu lai
        // luon vao config de lan sau mo launcher nho dung phien ban nay.
        versionComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                ConfigManager.get().setSelectedVersion(newVal.getId());
                ConfigManager.save();
            }
        });

        loadVersionsAsync(false);
    }

    /**
     * Tai danh sach phien ban tu Mojang + quet phien ban cai thu cong tren
     * luong nen, khong lam dung giao dien.
     *
     * @param keepCurrentSelection true = giu nguyen phien ban dang chon
     *        (dung khi bam nut lam moi thu cong, vd sau khi vua cai xong
     *        Forge/OptiFine ma khong muon mat lua chon hien tai); false =
     *        chon theo cau hinh da luu (dung luc khoi dong app).
     */
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
                // Them ca cac phien ban cai thu cong (OptiFine/Forge/NeoForge...)
                // vao CUOI danh sach, de nguoi dung van thay va chon duoc.
                releases.addAll(versionManager.scanCustomLocalVersions());
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

            versionComboBox.setDisable(false);
            statusLabel.setText("San sang");
            LauncherLogger.info("Da nap " + releases.size() + " phien ban release len giao dien.");
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
     * Quet lai danh sach phien ban thu cong (nut 🔄 canh o chon phien ban).
     * Can dung sau khi cai xong Forge/OptiFine/NeoForge/Quilt trong luc
     * launcher VAN DANG MO - vi launcher chi tu quet 1 lan luc khoi dong,
     * khong tu phat hien phien ban moi neu khong dong roi mo lai app.
     */
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
                boolean isCustomLocal = "custom-local".equals(selected.getType());
                boolean useFabric = fabricCheckBox.isSelected() && !isCustomLocal;
                if (fabricCheckBox.isSelected() && isCustomLocal) {
                    LauncherLogger.warn("Bo qua tick Fabric vi '" + selected.getId()
                            + "' da la phien ban cai thu cong (OptiFine/Forge/NeoForge...).");
                }

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

                GameOptionsManager.Mode mode = switch (ConfigManager.get().getPerformanceMode()) {
                    case "ultrafast" -> GameOptionsManager.Mode.ULTRAFAST;
                    case "high_quality" -> GameOptionsManager.Mode.HIGH_QUALITY;
                    default -> GameOptionsManager.Mode.NORMAL;
                };
                new GameOptionsManager().applyMode(mode);

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

    /**
     * Cho nguoi dung chon 1 file installer (.jar) da tai ve (Forge/NeoForge/
     * Quilt/OptiFine) va TU CHAY giup bang chinh JRE dang chay ME Launcher -
     * khong can nguoi dung tu mo terminal go "java -jar ...".
     */
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

    /**
     * Java binary de chay cac file installer ben ngoai (Forge/NeoForge...).
     * Uu tien duong dan Java tuy chinh nguoi dung dat trong Cai dat; neu
     * khong co, dung chinh JRE dang chay ME Launcher (jpackage da bundle
     * san, luon co that, khong can nguoi dung tu cai Java rieng).
     */
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
        } catch (IOException e) {
            LauncherLogger.error("Khong mo duoc Cai dat", e);
        }
    }

    @FXML
    private void onLoginClicked() {
        LauncherLogger.info("Nguoi dung nhan Dang nhap");
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

