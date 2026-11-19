package model;

/**
 * Thong tin tai khoan Minecraft sau khi dang nhap Microsoft thanh cong
 * hoac tao tai khoan Offline (Crack).
 * Phase 5 se dien du lieu that vao day sau buoc OAuth device code flow;
 * accessToken la thu bat buoc phai co de truyen vao MinecraftLauncher
 * (Phase 4) khi khoi chay game (--accessToken ...).
 */
public class UserProfile {

    private String username;
    private String uuid;
    private String accessToken;
    private String refreshToken;
    private String skinUrl;
    private String capeUrl;
    private boolean offline;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getSkinUrl() { return skinUrl; }
    public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }

    public String getCapeUrl() { return capeUrl; }
    public void setCapeUrl(String capeUrl) { this.capeUrl = capeUrl; }

    public boolean isOffline() { return offline; }
    public void setOffline(boolean offline) { this.offline = offline; }

    public boolean isLoggedIn() {
        return accessToken != null && !accessToken.isBlank();
    }
}
