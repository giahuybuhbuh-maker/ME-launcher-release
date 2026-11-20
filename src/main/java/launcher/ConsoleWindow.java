package launcher;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Cua so Console hien thi log theo thoi gian thuc, tu dong bat len
 * khi ME Launcher hoac Minecraft gap loi (crash / exception).
 */
public class ConsoleWindow {

    private static final int LOW_RAM_BUFFER_LINES = 200;
    private static final int HIGH_RAM_BUFFER_LINES = 600;
    private static final Deque<String> LOG_BUFFER = new ArrayDeque<>(HIGH_RAM_BUFFER_LINES);

    private static Stage consoleStage;
    private static TextArea logArea;
    private static Label headerLabel;
    private static Button copyButton;
    private static Button openLogsButton;
    private static Button clearButton;
    private static Button closeButton;
    private static boolean hasErrorBanner = false;

    private ConsoleWindow() {
    }

    private static int getMaxBufferLines() {
        try {
            return ConfigManager.get().isLowRamMode() ? LOW_RAM_BUFFER_LINES : HIGH_RAM_BUFFER_LINES;
        } catch (Throwable ignored) {
            return LOW_RAM_BUFFER_LINES;
        }
    }

    /** Cat gon bo dem log ngay khi nguoi dung chuyen sang che do Low RAM. */
    public static void trimBufferIfNeeded() {
        int maxLines = getMaxBufferLines();
        synchronized (LOG_BUFFER) {
            while (LOG_BUFFER.size() > maxLines) {
                LOG_BUFFER.removeFirst();
            }
        }
    }

    /** Ghi 1 dong log vao bo dem vong (200 dong o Low RAM, 600 dong o High RAM). */
    public static void appendLine(String line) {
        if (line == null) return;
        int maxLines = getMaxBufferLines();
        synchronized (LOG_BUFFER) {
            while (LOG_BUFFER.size() >= maxLines) {
                LOG_BUFFER.removeFirst();
            }
            LOG_BUFFER.addLast(line);
        }

        try {
            Platform.runLater(() -> {
                if (logArea != null && consoleStage != null && consoleStage.isShowing()) {
                    logArea.appendText(line + "\n");
                    int maxChars = maxLines == LOW_RAM_BUFFER_LINES ? 40_000 : 120_000;
                    if (logArea.getLength() > maxChars) {
                        logArea.deleteText(0, maxChars / 3);
                    }
                    logArea.positionCaret(logArea.getLength());
                }
            });
        } catch (IllegalStateException ignored) {
            // Bo qua neu JavaFX chua khoi tao xong (log da duoc luu trong LOG_BUFFER)
        }
    }

    /** Mo cua so Console thu cong (tu nut Mo Console trong Cai dat). */
    public static void show() {
        try {
            Platform.runLater(() -> openInternal(null));
        } catch (IllegalStateException ignored) {
        }
    }

    /** Tu dong mo Console khi Launcher hoac game bi loi (neu tuy chon dang bat). */
    public static void showOnError(String reason) {
        boolean enabled = true;
        try {
            enabled = ConfigManager.get().isShowConsoleOnError();
        } catch (Throwable ignored) {
        }
        if (!enabled) return;

        try {
            Platform.runLater(() -> openInternal(reason));
        } catch (IllegalStateException ignored) {
        }
    }

    /** Cap nhat lai ngon ngu cua cua so Console khi nguoi dung doi Language. */
    public static void refreshLanguage() {
        try {
            Platform.runLater(() -> {
                if (consoleStage == null) return;
                consoleStage.setTitle(LanguageManager.t("console.title"));
                if (headerLabel != null && !hasErrorBanner) {
                    headerLabel.setText(LanguageManager.t("console.header.ready"));
                }
                if (copyButton != null) copyButton.setText(LanguageManager.t("console.copy"));
                if (openLogsButton != null) openLogsButton.setText(LanguageManager.t("console.openLogs"));
                if (clearButton != null) clearButton.setText(LanguageManager.t("console.clear"));
                if (closeButton != null) closeButton.setText(LanguageManager.t("console.close"));
            });
        } catch (IllegalStateException ignored) {
        }
    }

    private static void openInternal(String errorReason) {
        if (consoleStage == null) {
            buildStage();
        }

        hasErrorBanner = errorReason != null && !errorReason.isBlank();
        if (hasErrorBanner) {
            headerLabel.setText(LanguageManager.t("console.header.error") + " " + errorReason);
            headerLabel.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-font-size: 13px;");
        } else {
            headerLabel.setText(LanguageManager.t("console.header.ready"));
            headerLabel.setStyle("-fx-text-fill: #7ed957; -fx-font-weight: bold; -fx-font-size: 13px;");
        }

        StringBuilder snapshot = new StringBuilder();
        synchronized (LOG_BUFFER) {
            for (String line : LOG_BUFFER) {
                snapshot.append(line).append('\n');
            }
        }
        logArea.setText(snapshot.toString());
        logArea.positionCaret(logArea.getLength());

        refreshLanguage();
        if (!consoleStage.isShowing()) {
            consoleStage.show();
        }
        consoleStage.toFront();
    }

    private static void buildStage() {
        consoleStage = new Stage();
        consoleStage.setTitle(LanguageManager.t("console.title"));
        consoleStage.setWidth(820);
        consoleStage.setHeight(480);
        LauncherApplication.loadWindowIcons(consoleStage);

        // Chi an cua so khi bam X de khong thoat nham Launcher chinh
        consoleStage.setOnCloseRequest(e -> {
            e.consume();
            consoleStage.hide();
        });

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #111418; -fx-border-color: #33383f; -fx-border-width: 1px;");

        headerLabel = new Label(LanguageManager.t("console.header.ready"));
        headerLabel.setWrapText(true);
        HBox topBar = new HBox(headerLabel);
        topBar.setPadding(new Insets(10, 14, 10, 14));
        topBar.setStyle("-fx-background-color: #1b1f24; -fx-border-color: #2f353d; -fx-border-width: 0 0 1 0;");
        root.setTop(topBar);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-control-inner-background: #0d1117; -fx-text-fill: #e6edf3; "
                + "-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: 12px; "
                + "-fx-background-radius: 0px; -fx-border-radius: 0px;");
        root.setCenter(logArea);

        copyButton = new Button(LanguageManager.t("console.copy"));
        copyButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 14 7 14; -fx-cursor: hand;");
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(logArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copyButton.setText(LanguageManager.t("console.copied"));
        });

        openLogsButton = new Button(LanguageManager.t("console.openLogs"));
        openLogsButton.setStyle("-fx-background-color: #33383f; -fx-text-fill: #e6e6e6; -fx-font-weight: bold; -fx-padding: 7 14 7 14; -fx-cursor: hand;");
        openLogsButton.setOnAction(e -> Utils.openDirectory(LauncherLogger.getLogDir()));

        clearButton = new Button(LanguageManager.t("console.clear"));
        clearButton.setStyle("-fx-background-color: #33383f; -fx-text-fill: #e6e6e6; -fx-font-weight: bold; -fx-padding: 7 14 7 14; -fx-cursor: hand;");
        clearButton.setOnAction(e -> {
            synchronized (LOG_BUFFER) {
                LOG_BUFFER.clear();
            }
            logArea.clear();
        });

        closeButton = new Button(LanguageManager.t("console.close"));
        closeButton.setStyle("-fx-background-color: #4b5563; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 16 7 16; -fx-cursor: hand;");
        closeButton.setOnAction(e -> consoleStage.hide());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottomBar = new HBox(8, copyButton, openLogsButton, clearButton, spacer, closeButton);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 14, 10, 14));
        bottomBar.setStyle("-fx-background-color: #1b1f24; -fx-border-color: #2f353d; -fx-border-width: 1 0 0 0;");
        root.setBottom(bottomBar);

        Scene scene = new Scene(root);
        Utils.applySquareTheme(scene);
        consoleStage.setScene(scene);
    }
}