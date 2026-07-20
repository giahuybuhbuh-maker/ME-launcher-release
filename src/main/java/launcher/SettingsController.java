package launcher;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.LauncherConfig;

import java.io.IOException;

public class SettingsController {

    @FXML private Spinner<Integer> minMemorySpinner;
    @FXML private Spinner<Integer> maxMemorySpinner;
    @FXML private TextField javaPathField;
    @FXML private CheckBox darkThemeCheckBox;
    @FXML private Label infoLabel;

    @FXML
    public void initialize() {
        LauncherConfig config = ConfigManager.get();

        minMemorySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(512, 32768, config.getMinMemoryMB(), 512));
        maxMemorySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(512, 32768, config.getMaxMemoryMB(), 512));
        javaPathField.setText(config.getJavaPath());
        darkThemeCheckBox.setSelected(config.isDarkTheme());
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
        config.setDarkTheme(darkThemeCheckBox.isSelected());
        ConfigManager.save();

        LauncherLogger.info("Da luu cai dat: RAM " + min + "-" + max + "MB, javaPath="
                + (config.getJavaPath().isBlank() ? "(tu dong)" : config.getJavaPath())
                + ", darkTheme=" + config.isDarkTheme());
        closeWindow();
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
