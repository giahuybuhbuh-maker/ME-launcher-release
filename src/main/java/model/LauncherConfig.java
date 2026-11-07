package model;

public class LauncherConfig {

    // Lệnh Java mặc định (Aikar's Flags) hiển thị sẵn trong ô Java Arguments
    public static final String DEFAULT_JVM_ARGS =
            "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
            "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
            "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
            "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
            "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
            "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
            "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";

    // --- CÁC TRƯỜNG CŨ CỦA BẠN (GIỮ NGUYÊN 100%) ---
    private int minMemoryMB = 1024;
    private int maxMemoryMB = 4096;
    private String javaPath = "";
    private String gameDirPath = "";
    private boolean darkTheme = true;
    private String performanceMode = "ultrafast";
    private String selectedVersion = "";
    private int windowWidth = 960;
    private int windowHeight = 600;

    // --- CÁC TRƯỜNG MỚI BỔ SUNG THEO MẪU TLAUNCHER ---
    private int gameWidth = 925;
    private int gameHeight = 530;
    private boolean fullscreen = false;
    private boolean showSnapshots = false;
    private boolean showModified = true;
    private boolean showBeta = false;
    private boolean showAlpha = false;
    private boolean showOldReleases = true;
    private String customJvmArgs = DEFAULT_JVM_ARGS;
    private String customGameArgs = "";

    public int getMinMemoryMB() { return minMemoryMB; }
    public void setMinMemoryMB(int minMemoryMB) { this.minMemoryMB = minMemoryMB; }

    public int getMaxMemoryMB() { return maxMemoryMB; }
    public void setMaxMemoryMB(int maxMemoryMB) { this.maxMemoryMB = maxMemoryMB; }

    public String getJavaPath() { return javaPath == null ? "" : javaPath; }
    public void setJavaPath(String javaPath) { this.javaPath = javaPath; }

    public String getGameDirPath() { return gameDirPath == null ? "" : gameDirPath; }
    public void setGameDirPath(String gameDirPath) { this.gameDirPath = gameDirPath; }

    public boolean isDarkTheme() { return darkTheme; }
    public void setDarkTheme(boolean darkTheme) { this.darkTheme = darkTheme; }

    public String getPerformanceMode() { return performanceMode == null ? "ultrafast" : performanceMode; }
    public void setPerformanceMode(String performanceMode) { this.performanceMode = performanceMode; }

    public String getSelectedVersion() { return selectedVersion == null ? "" : selectedVersion; }
    public void setSelectedVersion(String selectedVersion) { this.selectedVersion = selectedVersion; }

    public int getWindowWidth() { return windowWidth > 0 ? windowWidth : 960; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }

    public int getWindowHeight() { return windowHeight > 0 ? windowHeight : 600; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }

    public int getGameWidth() { return gameWidth > 0 ? gameWidth : 925; }
    public void setGameWidth(int gameWidth) { this.gameWidth = gameWidth; }

    public int getGameHeight() { return gameHeight > 0 ? gameHeight : 530; }
    public void setGameHeight(int gameHeight) { this.gameHeight = gameHeight; }

    public boolean isFullscreen() { return fullscreen; }
    public void setFullscreen(boolean fullscreen) { this.fullscreen = fullscreen; }

    public boolean isShowSnapshots() { return showSnapshots; }
    public void setShowSnapshots(boolean showSnapshots) { this.showSnapshots = showSnapshots; }

    public boolean isShowModified() { return showModified; }
    public void setShowModified(boolean showModified) { this.showModified = showModified; }

    public boolean isShowBeta() { return showBeta; }
    public void setShowBeta(boolean showBeta) { this.showBeta = showBeta; }

    public boolean isShowAlpha() { return showAlpha; }
    public void setShowAlpha(boolean showAlpha) { this.showAlpha = showAlpha; }

    public boolean isShowOldReleases() { return showOldReleases; }
    public void setShowOldReleases(boolean showOldReleases) { this.showOldReleases = showOldReleases; }

    public String getCustomJvmArgs() {
        return (customJvmArgs == null || customJvmArgs.isBlank()) ? DEFAULT_JVM_ARGS : customJvmArgs;
    }
    public void setCustomJvmArgs(String customJvmArgs) { this.customJvmArgs = customJvmArgs; }

    public String getCustomGameArgs() { return customGameArgs == null ? "" : customGameArgs; }
    public void setCustomGameArgs(String customGameArgs) { this.customGameArgs = customGameArgs; }
}