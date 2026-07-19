package model;

/**
 * Map truc tiep 1 phan tu trong mang "versions" cua
 * https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
 * Ten field giu nguyen nhu JSON de Gson tu doc duoc, khong can @SerializedName.
 * VersionManager (Phase 2) se la noi thuc su goi API va tao ra danh sach nay.
 */
public class MinecraftVersion {

    private String id;              // vd: "1.21.1"
    private String type;            // "release" | "snapshot" | "old_beta" | "old_alpha"
    private String url;             // url toi file JSON chi tiet cua rieng phien ban nay
    private String time;
    private String releaseTime;
    private String sha1;
    private int complianceLevel;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getReleaseTime() { return releaseTime; }
    public void setReleaseTime(String releaseTime) { this.releaseTime = releaseTime; }

    public String getSha1() { return sha1; }
    public void setSha1(String sha1) { this.sha1 = sha1; }

    public int getComplianceLevel() { return complianceLevel; }
    public void setComplianceLevel(int complianceLevel) { this.complianceLevel = complianceLevel; }

    public boolean isRelease() { return "release".equals(type); }
    public boolean isSnapshot() { return "snapshot".equals(type); }

    @Override
    public String toString() {
        // De sau nay dung thang ComboBox<MinecraftVersion> van hien thi dep (chi hien id)
        return id;
    }
}
