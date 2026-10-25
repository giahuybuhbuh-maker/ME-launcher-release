package launcher;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.LauncherConfig;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class SettingsController {

    @FXML private Spinner<Integer> minMemorySpinner;
    @FXML private Spinner<Integer> maxMemorySpinner;
    @FXML private TextField javaPathField;
    @FXML private TextField gameDirField;
    @FXML private CheckBox darkThemeCheckBox;
    @FXML private RadioButton ultrafastRadio;
    @FXML private RadioButton normalRadio;
    @FXML private RadioButton highQualityRadio;
    @FXML private Label infoLabel;

    @FXML
    public void initialize() {
        LauncherConfig config = ConfigManager.get();

        minMemorySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(512, 32768, config.getMinMemoryMB(), 512));
        maxMemorySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(512, 32768, config.getMaxMemoryMB(), 512));
        javaPathField.setText(config.getJavaPath());
        gameDirField.setText(config.getGameDirPath());
        darkThemeCheckBox.setSelected(config.isDarkTheme());

        ToggleGroup modeGroup = new ToggleGroup();
        ultrafastRadio.setToggleGroup(modeGroup);
        normalRadio.setToggleGroup(modeGroup);
        highQualityRadio.setToggleGroup(modeGroup);

        switch (config.getPerformanceMode()) {
            case "ultrafast" -> ultrafastRadio.setSelected(true);
            case "high_quality" -> highQualityRadio.setSelected(true);
            default -> normalRadio.setSelected(true);
        }
    }

    /** Mo hop thoai chon thu muc (native cua he dieu hanh) cho duong dan luu file game. */
    @FXML
    private void onBrowseGameDirClicked() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Chon thu muc luu file game");

        String current = gameDirField.getText();
        if (current != null && !current.isBlank() && new File(current).isDirectory()) {
            chooser.setInitialDirectory(new File(current));
        }

        File selected = chooser.showDialog(infoLabel.getScene().getWindow());
        if (selected != null) {
            gameDirField.setText(selected.getAbsolutePath());
            infoLabel.setText("Luu y: doi thu muc KHONG tu chuyen file cu sang - "
                    + "phien ban/mod da tai truoc do se can tai lai o cho moi.");
        }
    }

    @FXML
    private void onSaveClicked() {
        int min = minMemorySpinner.getValue();
        int max = maxMemorySpinner.getValue();
        if (min > max) {
            infoLabel.setText("RAM toi thieu khong duoc lon hon RAM toi da");
            return;
        }

        LauncherConfig config = ConfigManager.get();
        config.setMinMemoryMB(min);
        config.setMaxMemoryMB(max);
        config.setJavaPath(javaPathField.getText() == null ? "" : javaPathField.getText().trim());
        config.setGameDirPath(gameDirField.getText() == null ? "" : gameDirField.getText().trim());
        config.setDarkTheme(darkThemeCheckBox.isSelected());

        if (ultrafastRadio.isSelected()) config.setPerformanceMode("ultrafast");
        else if (highQualityRadio.isSelected()) config.setPerformanceMode("high_quality");
        else config.setPerformanceMode("normal");

        ConfigManager.save();

        LauncherLogger.info("Da luu cai dat: RAM " + min + "-" + max + "MB, javaPath="
                + (config.getJavaPath().isBlank() ? "(tu dong)" : config.getJavaPath())
                + ", gameDir=" + (config.getGameDirPath().isBlank() ? "(mac dinh)" : config.getGameDirPath())
                + ", darkTheme=" + config.isDarkTheme()
                + ", che do=" + config.getPerformanceMode());
        closeWindow();
    }

    /**
     * Doc RAM THAT cua he thong (qua OperatingSystemMXBean) va de xuat 1
     * muc Xmx hop ly - khoang mot nua RAM may co, gioi han 2048-8192MB.
     * Khong tu dong ap dung khi chon che do - nguoi dung phai bam nut nay,
     * de tranh ghi de gia tri ho da tu chinh ma khong bao truoc.
     */
    @FXML
    private void onSuggestRamClicked() {
        long totalBytes;
        try {
            var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            totalBytes = osBean.getTotalMemorySize();
        } catch (Exception e) {
            infoLabel.setText("Khong doc duoc RAM he thong, ban tu chinh nhe.");
            return;
        }

        long totalMB = totalBytes / (1024 * 1024);
        int suggested = (int) Math.min(Math.max((totalMB / 2) / 512 * 512, 2048), 8192);
        maxMemorySpinner.getValueFactory().setValue(suggested);
        infoLabel.setText(String.format("May co ~%.1fGB RAM - de xuat %dMB cho Minecraft (chua luu, bam Luu de ap dung).",
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
            aboutStage.setTitle("Gioi thieu");
            aboutStage.initOwner(infoLabel.getScene().getWindow());
            aboutStage.initModality(Modality.WINDOW_MODAL);
            aboutStage.setResizable(false);

            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(infoLabel.getScene().getStylesheets());
            aboutStage.setScene(scene);
            aboutStage.show();
        } catch (IOException e) {
            LauncherLogger.error("Khong mo duoc About", e);
        }
    }

    private void closeWindow() {
        ((Stage) infoLabel.getScene().getWindow()).close();
    }
}
