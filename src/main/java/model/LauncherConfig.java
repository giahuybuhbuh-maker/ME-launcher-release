package model;

/** Duoc luu/doc thanh JSON tai config/launcher.json boi launcher.ConfigManager. */
public class LauncherConfig {

    private String selectedVersion = "1.21.1";
    private String javaPath = "";
    private int minMemoryMB = 1024;
    private int maxMemoryMB = 4096;
    private int windowWidth = 960;
    private int windowHeight = 600;
    private boolean darkTheme = true;

    public String getSelectedVersion() { return selectedVersion; }
    public void setSelectedVersion(String selectedVersion) { this.selectedVersion = selectedVersion; }

    public String getJavaPath() { return javaPath; }
    public void setJavaPath(String javaPath) { this.javaPath = javaPath; }

    public int getMinMemoryMB() { return minMemoryMB; }
    public void setMinMemoryMB(int minMemoryMB) { this.minMemoryMB = minMemoryMB; }

    public int getMaxMemoryMB() { return maxMemoryMB; }
    public void setMaxMemoryMB(int maxMemoryMB) { this.maxMemoryMB = maxMemoryMB; }

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }

    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }

    public boolean isDarkTheme() { return darkTheme; }
    public void setDarkTheme(boolean darkTheme) { this.darkTheme = darkTheme; }
}
