package launcher;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.LauncherConfig;

import java.io.IOException;
import java.util.Objects;

public class LauncherApplication extends Application {

    public static final String APP_TITLE = "ME Launcher";

    @Override
    public void start(Stage primaryStage) throws IOException {
        LauncherLogger.info("Dang khoi dong " + APP_TITLE + "...");
        LauncherConfig config = ConfigManager.load();

        showLoadingScreen(primaryStage, config);
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

        // Khi co icon.ico/logo that trong src/main/resources/assets, mo dong nay:
        // primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/assets/icon.png")));

        primaryStage.setScene(scene);
        LauncherLogger.info(APP_TITLE + " da khoi dong thanh cong.");
    }

    private Parent loadFxml(String path) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(getClass().getResource(path), "Khong tim thay " + path + " trong resources"));
        return loader.load();
    }

    private void applyStylesheets(Scene scene, LauncherConfig config) {
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());
        if (config.isDarkTheme()) {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/dark.css")).toExternalForm());
        }
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
