package model;

import java.io.File;

/**
 * Dai dien 1 ban JRE trong runtime/java/. Moi phien ban Minecraft doi hoi
 * 1 "component" JRE rieng (Mojang dat ten vd: jre-legacy, java-runtime-gamma...);
 * JavaManager (Phase 2) se tai dung ban theo tung phien ban va dien vao day.
 */
public class JavaRuntime {

    private String component;    // vd: "java-runtime-gamma", "jre-legacy"
    private int majorVersion;    // vd: 21
    private String platform;     // vd: "windows-x64", "mac-os", "linux"
    private String path;         // duong dan thu muc JRE da giai nen

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public int getMajorVersion() { return majorVersion; }
    public void setMajorVersion(int majorVersion) { this.majorVersion = majorVersion; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public boolean isInstalled() {
        return path != null && !path.isBlank() && new File(path).exists();
    }
}
