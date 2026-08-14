package cn.remix.ui.screen.impl.token;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.screen.AbstractScreen;
import cn.remix.ui.screen.util.AdaptiveButton;
import cn.remix.ui.screen.util.AdaptiveTextBox;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import injection.accessor.MinecraftClientAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

public class TokenScreen extends AbstractScreen {
    private final Screen parent;
    private AdaptiveTextBox tokenBox;
    private volatile boolean loading = false;
    private int statusColor = new Color(255, 255, 255).getRGB();
    private String statusMessage = "Waiting for Token...";

    private static final String clientID = "00000000402b5328";
    private static final String scope = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String redirectURI = "https://login.live.com/oauth20_desktop.srf";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public TokenScreen(Screen parent) {
        super("Token Manager Screen");
        this.parent = parent;
    }

    @Override
    protected void initScreen() {
        float x = (this.width - 300) / 2f;
        float y = this.height / 2f - 10f;

        tokenBox = new AdaptiveTextBox("Paste your Token here");
        tokenBox.setBounds(x, y, 300, 22);
        textBoxes.add(tokenBox);

        AdaptiveButton btnAccess = new AdaptiveButton("Access Token", () -> handlePerformLogin(true));
        btnAccess.setBounds(x, y + 40f, 145, 22);
        buttons.add(btnAccess);

        AdaptiveButton btnRefresh = new AdaptiveButton("Refresh Token", () -> handlePerformLogin(false));
        btnRefresh.setBounds(x + 155, y + 40f, 145, 22);
        buttons.add(btnRefresh);

        AdaptiveButton btnBack = new AdaptiveButton("Back", () -> mc.setScreen(parent));
        btnBack.setBounds(x, y + 80f, 300, 22);
        buttons.add(btnBack);
    }

    private void handlePerformLogin(boolean access) {
        if (loading) return;
        String token = tokenBox.getText().trim();
        if (token.isEmpty()) {
            statusMessage = "不输入token登录你冯呢?";
            statusColor = new Color(255, 85, 85).getRGB();
            return;
        }

        loading = true;
        new Thread(() -> {
            try {
                if (access) {
                    statusMessage = "Validating Access Token...";
                    statusColor = new Color(255, 255, 85).getRGB();
                    login(token);
                } else {
                    statusMessage = "[1/5] Fetching MSA Token...";
                    statusColor = new Color(255, 255, 85).getRGB();
                    String msa = getMsaToken(token);

                    statusMessage = "[2/5] Exchanging XBL Token...";
                    JsonObject xbl = getXblToken(msa);
                    String xblToken = xbl.get("Token").getAsString();
                    String userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

                    statusMessage = "[3/5] Exchanging XSTS Token...";
                    String xsts = getXstsToken(xblToken);

                    statusMessage = "[4/5] Fetching MC Token...";
                    String mcToken = getAccessToken(userHash, xsts);

                    statusMessage = "[5/5] Profile...";
                    login(mcToken);
                }
            } catch (Exception e) {
                statusMessage = "Login failed: " + e.getMessage();
                statusColor = new Color(255, 85, 85).getRGB();
                loading = false;
            }
        }).start();
    }

    private String getMsaToken(String token) throws Exception {
        String data = "client_id=" + clientID + "&scope=" + scope + "&grant_type=refresh_token&redirect_uri=" + redirectURI + "&refresh_token=" + token;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://login.live.com/oauth20_token.srf")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(data)).build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        return json.get("access_token").getAsString();
    }

    private JsonObject getXblToken(String msa) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", msa);
        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://user.auth.xboxlive.com/user/authenticate")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return JsonParser.parseString(httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    private String getXstsToken(String xbl) throws Exception {
        JsonArray tokens = new JsonArray();
        tokens.add(xbl);
        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        props.add("UserTokens", tokens);
        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return JsonParser.parseString(httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject().get("Token").getAsString();
    }

    private String getAccessToken(String hash, String xsts) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + hash + ";" + xsts);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return JsonParser.parseString(httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject().get("access_token").getAsString();
    }

    private void login(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://api.minecraftservices.com/minecraft/profile")).header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new RuntimeException("Invalid Token");
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        String name = json.get("name").getAsString();
        String uuid = json.get("id").getAsString().replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})", "$1-$2-$3-$4-$5");

        Session session = new Session(name, UUID.fromString(uuid), token, Optional.empty(), Optional.empty());
        ((MinecraftClientAccessor) mc).setSession(session);
        ((MinecraftClientAccessor) mc).setUserApiService(new YggdrasilAuthenticationService(mc.getNetworkProxy()).createUserApiService(token));

        statusMessage = "Welcome: " + name;
        statusColor = new Color(85, 255, 85).getRGB();
        loading = false;
    }

    @Override
    protected void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        TrueTypeFont font50 = instance.getFontManager().getFont(50);
        TrueTypeFont font19 = instance.getFontManager().getFont(19);

        float titleWidth = font50.getStringWidth("Token Manager");
        font50.drawString(context, "Token Manager", (this.width - titleWidth) / 2f, this.height / 2f - 70f, -1, true);

        float statusWidth = font19.getStringWidth(statusMessage);
        font19.drawString(context, statusMessage, (this.width - statusWidth) / 2f, this.height / 2f - 35f, statusColor, true);
    }


    @Override
    public boolean keyPressed(KeyInput input) {
        if (super.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            handlePerformLogin(tokenBox.getText().trim().startsWith("eyJ"));
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}