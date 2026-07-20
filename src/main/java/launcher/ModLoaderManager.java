package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.MinecraftVersion;
import network.HttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ho tro cai MOD LOADER. Hien tai CHI lam Fabric - API cua Fabric ro rang,
 * on dinh va profile JSON tra ve dung dinh dang "arguments"/"libraries"
 * giong het vanilla (chi them "inheritsFrom"), nen ghep duoc thang voi
 * VersionManager/DownloadManager/MinecraftLauncher da co ma khong can
 * viet lai gi nhieu.
 *
 * CHUA LAM: Forge/NeoForge/Quilt. Forge dung co che "installer" rieng,
 * chay processor (thuc thi jar tuy y) de patch/tai thu vien - phuc tap va
 * de loi hon nhieu, khong co API on dinh de tu dong hoa an toan nhu Fabric.
 * Neu can Forge, nen lam rieng va can nhieu thoi gian kiem thu that hon.
 */
public class ModLoaderManager {

    private static final String FABRIC_META_BASE = "https://meta.fabricmc.net/v2/versions/loader/";

    private final HttpClient httpClient = new HttpClient();
    private final VersionManager versionManager;

    public ModLoaderManager(VersionManager versionManager) {
        this.versionManager = versionManager;
    }

    public record FabricLoaderVersion(String version, boolean stable) {
        @Override
        public String toString() {
            return version + (stable ? "" : " (khong on dinh)");
        }
    }

    /** Danh sach ban Fabric Loader tuong thich voi 1 phien ban Minecraft, moi nhat truoc. */
    public List<FabricLoaderVersion> fetchFabricLoaderVersions(String minecraftVersion)
            throws IOException, InterruptedException {

        String json = httpClient.getString(FABRIC_META_BASE + minecraftVersion);
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();

        List<FabricLoaderVersion> result = new ArrayList<>();
        for (var el : array) {
            JsonObject loader = el.getAsJsonObject().getAsJsonObject("loader");
            result.add(new FabricLoaderVersion(
                    loader.get("version").getAsString(),
                    loader.has("stable") && loader.get("stable").getAsBoolean()
            ));
        }
        return result;
    }

    /** Tien loi: lay ban Fabric Loader on dinh moi nhat, hoac ban dau tien neu khong co ban nao "stable". */
    public FabricLoaderVersion fetchLatestStableFabricLoader(String minecraftVersion)
            throws IOException, InterruptedException {

        List<FabricLoaderVersion> versions = fetchFabricLoaderVersions(minecraftVersion);
        if (versions.isEmpty()) {
            throw new IOException("Fabric khong ho tro Minecraft " + minecraftVersion);
        }
        return versions.stream().filter(FabricLoaderVersion::stable).findFirst().orElse(versions.get(0));
    }

    /**
     * Tai profile cua Fabric roi GOP voi version detail vanilla goc, tra ve
     * 1 JsonObject dung duoc y het ban vanilla thuong voi DownloadManager
     * va MinecraftLauncher (chi mainClass/libraries/arguments la khac).
     */
    public JsonObject installFabric(MinecraftVersion minecraftVersion, String fabricLoaderVersion)
            throws IOException, InterruptedException {

        String profileUrl = FABRIC_META_BASE + minecraftVersion.getId() + "/" + fabricLoaderVersion + "/profile/json";
        JsonObject fabricProfile = JsonParser.parseString(httpClient.getString(profileUrl)).getAsJsonObject();

        JsonObject vanillaDetail = versionManager.fetchVersionDetail(minecraftVersion);

        LauncherLogger.info("Da tai profile Fabric " + fabricLoaderVersion + " cho " + minecraftVersion.getId());
        return mergeProfiles(vanillaDetail, fabricProfile);
    }

    /** Gop 1 profile mod loader (co "inheritsFrom") voi ban vanilla goc: them library, doi mainClass, noi arguments. */
    private JsonObject mergeProfiles(JsonObject vanilla, JsonObject loaderProfile) {
        JsonObject merged = vanilla.deepCopy();

        if (loaderProfile.has("mainClass")) {
            merged.addProperty("mainClass", loaderProfile.get("mainClass").getAsString());
        }

        if (loaderProfile.has("libraries")) {
            JsonArray mergedLibs = merged.has("libraries") ? merged.getAsJsonArray("libraries") : new JsonArray();
            for (var lib : loaderProfile.getAsJsonArray("libraries")) {
                mergedLibs.add(lib);
            }
            merged.add("libraries", mergedLibs);
        }

        if (loaderProfile.has("arguments")) {
            JsonObject loaderArgs = loaderProfile.getAsJsonObject("arguments");
            JsonObject mergedArgs = merged.has("arguments") ? merged.getAsJsonObject("arguments") : new JsonObject();

            for (String key : List.of("game", "jvm")) {
                if (!loaderArgs.has(key)) continue;
                JsonArray mergedArr = mergedArgs.has(key) ? mergedArgs.getAsJsonArray(key) : new JsonArray();
                for (var arg : loaderArgs.getAsJsonArray(key)) {
                    mergedArr.add(arg);
                }
                mergedArgs.add(key, mergedArr);
            }
            merged.add("arguments", mergedArgs);
        }

        if (loaderProfile.has("id")) {
            merged.addProperty("id", loaderProfile.get("id").getAsString());
        }
        return merged;
    }
}
