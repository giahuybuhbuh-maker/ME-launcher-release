package launcher;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.LauncherConfig;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SettingsController {

    @FXML private VBox settingsContainer;
    @FXML private Spinner<Integer> minMemorySpinner;
    @FXML private Spinner<Integer> maxMemorySpinner;
    @FXML private TextField javaPathField;
    @FXML private TextField gameDirField;
    @FXML private CheckBox darkThemeCheckBox;
    @FXML private RadioButton ultrafastRadio;
    @FXML private RadioButton normalRadio;
    @FXML private RadioButton highQualityRadio;
    @FXML private RadioButton hideLauncherRadio;
    @FXML private RadioButton keepLauncherOpenRadio;
    @FXML private RadioButton lowRamRadio;
    @FXML private RadioButton highRamRadio;
    @FXML private Label infoLabel;

    @FXML private Slider maxMemorySlider;
    @FXML private TextField resWidthField;
    @FXML private TextField resHeightField;
    @FXML private CheckBox fullscreenCheckBox;
    @FXML private CheckBox showSnapshotsCheckBox;
    @FXML private CheckBox showModifiedCheckBox;
    @FXML private CheckBox showBetaCheckBox;
    @FXML private CheckBox showAlphaCheckBox;
    @FXML private CheckBox showOldReleasesCheckBox;
    @FXML private TextField customJvmArgsField;
    @FXML private TextField customGameArgsField;
    @FXML private ComboBox<String> languageComboBox;
    @FXML private CheckBox showConsoleOnErrorCheckBox;

    @FXML
    public void initialize() {
        LauncherConfig config = ConfigManager.get();

        // Nếu hệ thống vẫn nạp file settings.fxml cũ từ cache, tự động tạo và chèn các ô mới vào giao diện
        ensureDynamicFieldsIfOldFxml();

        minMemorySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(512, 32768, config.getMinMemoryMB(), 256));
        maxMemorySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(512, 32768, config.getMaxMemoryMB(), 256));

        if (maxMemorySlider != null) {
            maxMemorySlider.setValue(config.getMaxMemoryMB());
            maxMemorySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int rounded = (int) (Math.round(newVal.doubleValue() / 128.0) * 128);
                if (maxMemorySpinner.getValue() != rounded) {
                    maxMemorySpinner.getValueFactory().setValue(rounded);
                }
            });
            maxMemorySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && Math.abs(maxMemorySlider.getValue() - newVal) >= 64) {
                    maxMemorySlider.setValue(Math.min(newVal, maxMemorySlider.getMax()));
                }
            });
        }

        javaPathField.setText(config.getJavaPath());
        gameDirField.setText(config.getGameDirPath());
        darkThemeCheckBox.setSelected(config.isDarkTheme());

        if (resWidthField != null) resWidthField.setText(String.valueOf(config.getGameWidth()));
        if (resHeightField != null) resHeightField.setText(String.valueOf(config.getGameHeight()));
        if (fullscreenCheckBox != null) fullscreenCheckBox.setSelected(config.isFullscreen());

        if (showSnapshotsCheckBox != null) showSnapshotsCheckBox.setSelected(config.isShowSnapshots());
        if (showModifiedCheckBox != null) showModifiedCheckBox.setSelected(config.isShowModified());
        if (showBetaCheckBox != null) showBetaCheckBox.setSelected(config.isShowBeta());
        if (showAlphaCheckBox != null) showAlphaCheckBox.setSelected(config.isShowAlpha());
        if (showOldReleasesCheckBox != null) showOldReleasesCheckBox.setSelected(config.isShowOldReleases());

        if (customJvmArgsField != null) customJvmArgsField.setText(config.getCustomJvmArgs());
        if (customGameArgsField != null) customGameArgsField.setText(config.getCustomGameArgs());

        ToggleGroup modeGroup = new ToggleGroup();
        ultrafastRadio.setToggleGroup(modeGroup);
        normalRadio.setToggleGroup(modeGroup);
        highQualityRadio.setToggleGroup(modeGroup);

        String mode = config.getPerformanceMode();
        if ("normal".equals(mode)) {
            normalRadio.setSelected(true);
        } else if ("high_quality".equals(mode)) {
            highQualityRadio.setSelected(true);
        } else {
            ultrafastRadio.setSelected(true);
        }

        if (hideLauncherRadio != null && keepLauncherOpenRadio != null) {
            ToggleGroup launcherBehaviorGroup = new ToggleGroup();
            hideLauncherRadio.setToggleGroup(launcherBehaviorGroup);
            keepLauncherOpenRadio.setToggleGroup(launcherBehaviorGroup);
            if (config.isHideLauncherOnPlay()) {
                hideLauncherRadio.setSelected(true);
            } else {
                keepLauncherOpenRadio.setSelected(true);
            }
        }

        if (lowRamRadio != null && highRamRadio != null) {
            ToggleGroup ramModeGroup = new ToggleGroup();
            lowRamRadio.setToggleGroup(ramModeGroup);
            highRamRadio.setToggleGroup(ramModeGroup);
            if (config.isLowRamMode()) {
                lowRamRadio.setSelected(true);
            } else {
                highRamRadio.setSelected(true);
            }
        }

        if (languageComboBox != null) {
            languageComboBox.getItems().setAll("Tiếng Việt", "English");
            languageComboBox.getSelectionModel().select(
                    "en".equalsIgnoreCase(config.getLanguage()) ? "English" : "Tiếng Việt");
        }
        if (showConsoleOnErrorCheckBox != null) {
            showConsoleOnErrorCheckBox.setSelected(config.isShowConsoleOnError());
        }

        Platform.runLater(this::enhanceReadability);
    }

    /**
     * Phòng trường hợp file settings.fxml cũ vẫn được nạp: tự động tạo ô Java Arguments,
     * Minecraft Arguments và Độ phân giải chèn thẳng vào cửa sổ Cài đặt.
     */
    private void ensureDynamicFieldsIfOldFxml() {
        if (customJvmArgsField != null) return;
        if (javaPathField == null || !(javaPathField.getParent() instanceof Node javaBox)) return;
        if (!(javaBox.getParent() instanceof VBox rootVBox)) return;

        customJvmArgsField = new TextField();
        customJvmArgsField.setPromptText("JVM arguments (vd: -XX:+UseG1GC -XX:MaxGCPauseMillis=200)");

        customGameArgsField = new TextField();
        customGameArgsField.setPromptText("Minecraft arguments (vd: --server 127.0.0.1)");

        VBox argsBox = new VBox(6,
                new Label("Java Arguments (Lenh Java khi chay - JVM Flags):"),
                customJvmArgsField,
                new Label("Minecraft Arguments (Tham so game):"),
                customGameArgsField
        );

        resWidthField = new TextField("925");
        resWidthField.setPrefWidth(80);
        resHeightField = new TextField("530");
        resHeightField.setPrefWidth(80);
        fullscreenCheckBox = new CheckBox("Fullscreen");
        HBox resRow = new HBox(8, resWidthField, new Label("x"), resHeightField, fullscreenCheckBox);
        resRow.setAlignment(Pos.CENTER_LEFT);
        VBox resBox = new VBox(6, new Label("Do phan giai (Resolution):"), resRow);

        int idx = rootVBox.getChildren().indexOf(javaBox);
        if (idx >= 0) {
            rootVBox.getChildren().add(idx, resBox);
            rootVBox.getChildren().add(idx + 1, argsBox);
        } else {
            rootVBox.getChildren().addAll(resBox, argsBox);
        }
    }

    private void enhanceReadability() {
        if (infoLabel == null || infoLabel.getScene() == null) return;
        Scene scene = infoLabel.getScene();
        LauncherConfig config = ConfigManager.get();

        if (scene.getWindow() instanceof Stage stage) {
            LauncherApplication.loadWindowIcons(stage);
            stage.setWidth(Math.max(stage.getWidth(), 640));
            stage.setHeight(Math.max(stage.getHeight(), 650));
        }
        Parent root = scene.getRoot();
        if (root == null) return;

        LauncherApplication.applyLauncherRamMode(scene, config);

        // O che do Low RAM: bo dropshadow tren tung chu de giam 20-35MB RAM do hoa JavaFX
        String readableTextStyle = config.isLowRamMode()
                ? "-fx-text-fill: #FFFFFF; -fx-font-weight: 700; -fx-effect: null;"
                : "-fx-text-fill: #FFFFFF; -fx-font-weight: 700; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.98), 7, 0.85, 0, 1);";
        applyStyleRecursively(root, readableTextStyle);

        // Áp dụng góc vuông 90 độ cho toàn bộ cửa sổ Cài đặt
        Utils.applySquareTheme(scene);
        LanguageManager.applyToScene(scene);
    }

    private void applyStyleRecursively(Node node, String textStyle) {
        if (node instanceof Label || node instanceof CheckBox || node instanceof RadioButton) {
            String existing = node.getStyle() == null ? "" : node.getStyle();
            node.setStyle(existing + ";" + textStyle + "; -fx-background-radius: 0px; -fx-border-radius: 0px;");
        } else if (node instanceof TextField tf) {
            tf.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); -fx-text-fill: #111827; "
                    + "-fx-prompt-text-fill: #4B5563; -fx-font-weight: 600; "
                    + "-fx-background-radius: 0px; -fx-border-radius: 0px;");
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (!(child instanceof javafx.scene.control.Button) && !(child instanceof Spinner<?>)) {
                    applyStyleRecursively(child, textStyle);
                }
            }
        }
    }

    @FXML
    private void onBrowseGameDirClicked() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Chọn thư mục lưu file game (.minecraft / game)");

        String current = gameDirField.getText();
        if (current != null && !current.isBlank() && new File(current).isDirectory()) {
            chooser.setInitialDirectory(new File(current));
        }

        File selected = chooser.showDialog(infoLabel.getScene().getWindow());
        if (selected != null) {
            gameDirField.setText(selected.getAbsolutePath());
            infoLabel.setText("Lưu ý: đổi thư mục KHÔNG tự chuyển file cũ sang - cần tải lại ở chỗ mới.");
        }
    }

    @FXML
    private void onBrowseJavaPathClicked() {
        FileChooser chooser = new FileChooser();
        boolean isWin = Utils.getOperatingSystem() == Utils.OS.WINDOWS;
        chooser.setTitle(isWin
                ? "Chọn file thực thi Java (javaw.exe hoặc java.exe)"
                : "Chọn file thực thi Java (bin/java)");
        if (isWin) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Java Executable (javaw.exe, java.exe)", "javaw.exe", "java.exe", "*.exe"));
        }

        File selected = chooser.showOpenDialog(infoLabel.getScene().getWindow());
        if (selected != null) {
            javaPathField.setText(selected.getAbsolutePath());
            infoLabel.setText("Đã chọn đường dẫn Java: " + selected.getAbsolutePath());
        }
    }

    @FXML
    private void onResetDefaultsClicked() {
        minMemorySpinner.getValueFactory().setValue(1024);
        maxMemorySpinner.getValueFactory().setValue(4096);
        if (maxMemorySlider != null) maxMemorySlider.setValue(4096);

        javaPathField.setText("");
        gameDirField.setText("");
        darkThemeCheckBox.setSelected(true);
        ultrafastRadio.setSelected(true);
        if (hideLauncherRadio != null) hideLauncherRadio.setSelected(true);
        if (lowRamRadio != null) lowRamRadio.setSelected(true);

        if (resWidthField != null) resWidthField.setText("925");
        if (resHeightField != null) resHeightField.setText("530");
        if (fullscreenCheckBox != null) fullscreenCheckBox.setSelected(false);

        if (showSnapshotsCheckBox != null) showSnapshotsCheckBox.setSelected(false);
        if (showModifiedCheckBox != null) showModifiedCheckBox.setSelected(true);
        if (showBetaCheckBox != null) showBetaCheckBox.setSelected(false);
        if (showAlphaCheckBox != null) showAlphaCheckBox.setSelected(false);
        if (showOldReleasesCheckBox != null) showOldReleasesCheckBox.setSelected(true);

        if (customJvmArgsField != null) customJvmArgsField.setText(LauncherConfig.DEFAULT_JVM_ARGS);
        if (customGameArgsField != null) customGameArgsField.setText("");
        if (languageComboBox != null) languageComboBox.getSelectionModel().select("Tiếng Việt");
        if (showConsoleOnErrorCheckBox != null) showConsoleOnErrorCheckBox.setSelected(true);

        infoLabel.setText("Đã khôi phục về mặc định (Nhấn 'Lưu' để áp dụng).");
    }

    @FXML
    private void onSaveClicked() {
        commitSpinnerText(minMemorySpinner);
        commitSpinnerText(maxMemorySpinner);

        int min = minMemorySpinner.getValue();
        int max = maxMemorySpinner.getValue();
        if (min > max) {
            infoLabel.setText("Lỗi: RAM tối thiểu không được lớn hơn RAM tối đa!");
            return;
        }

        String cleanJavaPath = javaPathField.getText() == null
                ? ""
                : javaPathField.getText().replace("\"", "").trim();

        String cleanGameDir = gameDirField.getText() == null
                ? ""
                : gameDirField.getText().replace("\"", "").trim();

        LauncherConfig config = ConfigManager.get();
        config.setMinMemoryMB(min);
        config.setMaxMemoryMB(max);
        config.setJavaPath(cleanJavaPath);
        config.setGameDirPath(cleanGameDir);
        config.setDarkTheme(darkThemeCheckBox.isSelected());

        if (resWidthField != null) {
            try { config.setGameWidth(Integer.parseInt(resWidthField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (resHeightField != null) {
            try { config.setGameHeight(Integer.parseInt(resHeightField.getText().trim())); }
            catch (NumberFormatException ignored) {}
        }
        if (fullscreenCheckBox != null) config.setFullscreen(fullscreenCheckBox.isSelected());

        if (showSnapshotsCheckBox != null) config.setShowSnapshots(showSnapshotsCheckBox.isSelected());
        if (showModifiedCheckBox != null) config.setShowModified(showModifiedCheckBox.isSelected());
        if (showBetaCheckBox != null) config.setShowBeta(showBetaCheckBox.isSelected());
        if (showAlphaCheckBox != null) config.setShowAlpha(showAlphaCheckBox.isSelected());
        if (showOldReleasesCheckBox != null) config.setShowOldReleases(showOldReleasesCheckBox.isSelected());

        if (hideLauncherRadio != null) {
            config.setHideLauncherOnPlay(hideLauncherRadio.isSelected());
        }

        if (lowRamRadio != null && highRamRadio != null) {
            config.setLauncherRamMode(highRamRadio.isSelected() ? "high" : "low");
        }

        if (customJvmArgsField != null) config.setCustomJvmArgs(customJvmArgsField.getText().trim());
        if (customGameArgsField != null) config.setCustomGameArgs(customGameArgsField.getText().trim());

        if (ultrafastRadio.isSelected()) config.setPerformanceMode("ultrafast");
        else if (highQualityRadio.isSelected()) config.setPerformanceMode("high_quality");
        else config.setPerformanceMode("normal");

        if (languageComboBox != null) {
            config.setLanguage("English".equals(languageComboBox.getValue()) ? "en" : "vi");
        }
        if (showConsoleOnErrorCheckBox != null) {
            config.setShowConsoleOnError(showConsoleOnErrorCheckBox.isSelected());
        }

        ConfigManager.save();
        LauncherApplication.syncLauncherCfgRamMode(config);
        ConsoleWindow.trimBufferIfNeeded();
        ConsoleWindow.refreshLanguage();
        applyWindowsHighPerformanceSetting(cleanJavaPath);

        LauncherLogger.info("Da luu cai dat: RAM " + min + "-" + max + "MB, javaPath="
                + (config.getJavaPath().isBlank() ? "(tu dong)" : config.getJavaPath())
                + ", res=" + config.getGameWidth() + "x" + config.getGameHeight()
                + ", fullscreen=" + config.isFullscreen()
                + ", hideLauncherOnPlay=" + config.isHideLauncherOnPlay()
                + ", launcherRamMode=" + config.getLauncherRamMode()
                + ", language=" + config.getLanguage()
                + ", showConsoleOnError=" + config.isShowConsoleOnError());
        closeWindow();
    }

    private void applyWindowsHighPerformanceSetting(String customJavaPath) {
        if (Utils.getOperatingSystem() != Utils.OS.WINDOWS) return;

        Thread gpuThread = new Thread(() -> {
            try {
                String regKey = "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences";
                List<Path> javaExecutables = new ArrayList<>();

                Path runtimeDir = Utils.getAppDataDir().resolve("runtime").resolve("java").toAbsolutePath().normalize();
                if (Files.exists(runtimeDir)) {
                    try (var stream = Files.walk(runtimeDir)) {
                        stream.filter(Files::isRegularFile)
                              .filter(p -> {
                                  String name = p.getFileName().toString().toLowerCase();
                                  return name.equals("javaw.exe") || name.equals("java.exe");
                              })
                              .forEach(javaExecutables::add);
                    }
                }

                String currentJavaHome = System.getProperty("java.home");
                if (currentJavaHome != null) {
                    Path binDir = Path.of(currentJavaHome, "bin");
                    if (Files.exists(binDir.resolve("javaw.exe"))) {
                        javaExecutables.add(binDir.resolve("javaw.exe"));
                    }
                }

                if (customJavaPath != null && !customJavaPath.isBlank()) {
                    Path customP = Path.of(customJavaPath);
                    if (Files.isRegularFile(customP)) {
                        javaExecutables.add(customP);
                    } else if (Files.isDirectory(customP)) {
                        if (Files.exists(customP.resolve("bin").resolve("javaw.exe"))) {
                            javaExecutables.add(customP.resolve("bin").resolve("javaw.exe"));
                        } else if (Files.exists(customP.resolve("javaw.exe"))) {
                            javaExecutables.add(customP.resolve("javaw.exe"));
                        }
                    }
                }

                for (Path exe : javaExecutables) {
                    String absPath = exe.toAbsolutePath().normalize().toString();
                    new ProcessBuilder("reg", "add", regKey, "/v", absPath,
                            "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f")
                            .start().waitFor();
                }
            } catch (Exception e) {
                LauncherLogger.warn("Khong the cap nhat Windows High Performance GPU: " + e.getMessage());
            }
        }, "gpu-high-perf-thread");
        gpuThread.setDaemon(true);
        gpuThread.start();
    }

    private void commitSpinnerText(Spinner<Integer> spinner) {
        if (spinner == null || spinner.getEditor() == null) return;
        try {
            String text = spinner.getEditor().getText().trim();
            if (!text.isEmpty()) {
                int val = Integer.parseInt(text);
                spinner.getValueFactory().setValue(val);
            }
        } catch (NumberFormatException ignored) {}
    }

    @FXML
    private void onSuggestRamClicked() {
        long totalBytes;
        try {
            var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            totalBytes = osBean.getTotalMemorySize();
        } catch (Exception e) {
            infoLabel.setText("Không đọc được RAM hệ thống, bạn tự chỉnh nhé.");
            return;
        }

        long totalMB = totalBytes / (1024 * 1024);
        int suggested = (int) Math.min(Math.max((totalMB / 2) / 512 * 512, 2048), 8192);
        maxMemorySpinner.getValueFactory().setValue(suggested);
        if (maxMemorySlider != null) maxMemorySlider.setValue(suggested);
        infoLabel.setText(String.format("Máy có ~%.1fGB RAM - đề xuất %dMB cho Minecraft (bấm Lưu để áp dụng).",
                totalMB / 1024.0, suggested));
    }

    @FXML
    private void onCancelClicked() {
        closeWindow();
    }

    @FXML
    private void onAboutClicked() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/about.fxml"));
            Parent root = loader.load();

            Stage aboutStage = new Stage();
            aboutStage.setTitle(LanguageManager.t("about.title"));
            aboutStage.initOwner(infoLabel.getScene().getWindow());
            aboutStage.initModality(Modality.WINDOW_MODAL);
            aboutStage.setResizable(false);
            LauncherApplication.loadWindowIcons(aboutStage);

            Scene scene = new Scene(root);
            LauncherApplication.applyLauncherRamMode(scene, ConfigManager.get());
            scene.getStylesheets().addAll(infoLabel.getScene().getStylesheets());
            Utils.applySquareTheme(scene);
            LanguageManager.applyToScene(scene);
            aboutStage.setScene(scene);
            aboutStage.show();
        } catch (IOException e) {
            LauncherLogger.error("Khong mo duoc About", e);
        }
    }

    @FXML
    private void onOpenConsoleClicked() {
        ConsoleWindow.show();
    }

    private void closeWindow() {
        ((Stage) infoLabel.getScene().getWindow()).close();
    }
}