package launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.UserProfile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dang nhap Microsoft THAT qua "device code flow" (nguoi dung mo trinh
 * duyet, nhap 1 ma), roi doi token qua Xbox Live -> XSTS -> Minecraft de
 * lay ve access token that su dung de chay game duoc.
 * Ho tro song song che do tai khoan Offline (Crack) khong can ban quyen.
 */
public class AuthManager {

    // THAY BANG CLIENT ID THAT CUA BAN (xem README - "Chuan bi dang nhap Microsoft")
    private static final String CLIENT_ID = "DIEN_CLIENT_ID_CUA_BAN_VAO_DAY";

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String MC_ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

    public record DeviceCodeInfo(String deviceCode, String userCode, String verificationUri,
                                  int expiresIn, int intervalSeconds) {}

    /**
     * Tao tai khoan Offline (Crack) theo chuan UUID v3 cua Minecraft ("OfflinePlayer:<username>").
     * Giu nguyen dinh dang UUID co dau gach ngang (8-4-4-4-12) de Minecraft parse duoc.
     */
    public UserProfile createOfflineProfile(String username) {
        String cleanName = (username == null || username.isBlank()) ? "Player" : username.trim();
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + cleanName).getBytes(StandardCharsets.UTF_8)
        );

        UserProfile profile = new UserProfile();
        profile.setUsername(cleanName);
        profile.setUuid(offlineUuid.toString());
        profile.setAccessToken("0"); // Token mac dinh cho che do Offline
        profile.setOffline(true);
        return profile;
    }

    /** Buoc 1: xin ma dang nhap tu Microsoft. Hien userCode + verificationUri cho nguoi dung. */
    public DeviceCodeInfo requestDeviceCode() throws IOException, InterruptedException {
        String body = "client_id=" + urlEncode(CLIENT_ID) + "&scope=" + urlEncode("XboxLive.signin offline_access");
        JsonObject json = postForm(DEVICE_CODE_URL, body);

        return new DeviceCodeInfo(
                json.get("device_code").getAsString(),
                json.get("user_code").getAsString(),
                json.get("verification_uri").getAsString(),
                json.get("expires_in").getAsInt(),
                json.has("interval") ? json.get("interval").getAsInt() : 5
        );
    }

    /**
     * Buoc 2: TU DONG hoi Microsoft lien tuc (theo interval) cho toi khi
     * nguoi dung dang nhap xong trong trinh duyet (hoac het han). Khi xong,
     * tu dong lam tiep buoc Xbox Live -> XSTS -> Minecraft. Goi onStatus de
     * bao tien do. HAM NAY CHAN (block) - phai goi tren luong nen.
     */
    public UserProfile pollUntilLoggedIn(DeviceCodeInfo deviceCodeInfo, Consumer<String> onStatus)
            throws IOException, InterruptedException {

        long deadline = System.currentTimeMillis() + deviceCodeInfo.expiresIn() * 1000L;
        int intervalMs = Math.max(deviceCodeInfo.intervalSeconds(), 1) * 1000;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(intervalMs);

            String body = "client_id=" + urlEncode(CLIENT_ID)
                    + "&grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:device_code")
                    + "&device_code=" + urlEncode(deviceCodeInfo.deviceCode());

            JsonObject json = postFormAllowError(TOKEN_URL, body);

            if (json.has("access_token")) {
                if (onStatus != null) onStatus.accept("Dang nhap Microsoft OK, dang xac thuc Xbox Live...");
                return finishLogin(json.get("access_token").getAsString(), onStatus);
            }

            String error = json.has("error") ? json.get("error").getAsString() : "unknown_error";
            if ("authorization_pending".equals(error)) {
                if (onStatus != null) onStatus.accept("Dang cho ban dang nhap trong trinh duyet...");
            } else if ("slow_down".equals(error)) {
                intervalMs += 5000;
            } else {
                throw new IOException("Dang nhap Microsoft that bai: " + error);
            }
        }

        throw new IOException("Het thoi gian dang nhap - bam Dang nhap de thu lai.");
    }

    private UserProfile finishLogin(String msAccessToken, Consumer<String> onStatus) throws IOException, InterruptedException {
        // Buoc 3: Xbox Live (XBL)
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");

        JsonObject xblResponse = postJson(XBL_AUTH_URL, xblBody.toString());
        String xblToken = xblResponse.get("Token").getAsString();
        String userHash = xblResponse.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        if (onStatus != null) onStatus.accept("Dang xac thuc XSTS...");

        // Buoc 4: XSTS
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");

        JsonObject xstsResponse = postJsonAllowError(XSTS_AUTH_URL, xstsBody.toString());
        if (xstsResponse.has("XErr")) {
            throw new IOException(describeXstsError(xstsResponse.get("XErr").getAsLong()));
        }
        String xstsToken = xstsResponse.get("Token").getAsString();

        if (onStatus != null) onStatus.accept("Dang dang nhap Minecraft...");

        // Buoc 5: Minecraft
        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject mcResponse = postJson(MC_LOGIN_URL, mcBody.toString());
        String mcAccessToken = mcResponse.get("access_token").getAsString();

        if (onStatus != null) onStatus.accept("Dang kiem tra quyen so huu Minecraft...");
        if (!ownsMinecraft(mcAccessToken)) {
            throw new IOException("Tai khoan nay chua so huu Minecraft: Java Edition.");
        }

        if (onStatus != null) onStatus.accept("Dang lay thong tin nhan vat...");
        return fetchProfile(mcAccessToken);
    }

    private boolean ownsMinecraft(String mcAccessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MC_ENTITLEMENTS_URL))
                .header("Authorization", "Bearer " + mcAccessToken)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return false;

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.has("items") && !json.getAsJsonArray("items").isEmpty();
    }

    private UserProfile fetchProfile(String mcAccessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                .header("Authorization", "Bearer " + mcAccessToken)
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Khong lay duoc thong tin nhan vat (HTTP " + response.statusCode() + ")");
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        UserProfile profile = new UserProfile();
        profile.setUsername(json.get("name").getAsString());
        profile.setUuid(formatUuid(json.get("id").getAsString()));
        profile.setAccessToken(mcAccessToken);
        profile.setOffline(false);

        if (json.has("skins")) {
            for (var skinEl : json.getAsJsonArray("skins")) {
                JsonObject skin = skinEl.getAsJsonObject();
                if ("ACTIVE".equals(skin.get("state").getAsString())) {
                    profile.setSkinUrl(skin.get("url").getAsString());
                    break;
                }
            }
        }
        if (json.has("capes")) {
            for (var capeEl : json.getAsJsonArray("capes")) {
                JsonObject cape = capeEl.getAsJsonObject();
                if ("ACTIVE".equals(cape.get("state").getAsString())) {
                    profile.setCapeUrl(cape.get("url").getAsString());
                    break;
                }
            }
        }

        return profile;
    }

    private String formatUuid(String rawId) {
        if (rawId.contains("-")) return rawId;
        return rawId.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5"
        );
    }

    private String describeXstsError(long xErr) {
        if (xErr == 2148916233L) return "Tai khoan chua co ho so Xbox - mo account.xbox.com de tao truoc roi thu lai.";
        if (xErr == 2148916238L) return "Tai khoan tre em (duoi 18) - can nguoi lon them vao Family truoc.";
        return "Loi Xbox Live (XErr=" + xErr + ") - tra ma nay tren wiki.vg (Microsoft Authentication Scheme) de biet chi tiet.";
    }

    private JsonObject postForm(String url, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = sendForm(url, body);
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private JsonObject postFormAllowError(String url, String body) throws IOException, InterruptedException {
        return JsonParser.parseString(sendForm(url, body).body()).getAsJsonObject();
    }

    private HttpResponse<String> sendForm(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject postJson(String url, String jsonBody) throws IOException, InterruptedException {
        HttpResponse<String> response = sendJson(url, jsonBody);
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private JsonObject postJsonAllowError(String url, String jsonBody) throws IOException, InterruptedException {
        return JsonParser.parseString(sendJson(url, jsonBody).body()).getAsJsonObject();
    }

    private HttpResponse<String> sendJson(String url, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}