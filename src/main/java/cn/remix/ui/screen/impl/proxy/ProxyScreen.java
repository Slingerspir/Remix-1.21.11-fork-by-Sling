package cn.remix.ui.screen.impl.proxy;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.screen.AbstractScreen;
import cn.remix.ui.screen.util.AdaptiveButton;
import cn.remix.ui.screen.util.AdaptiveTextBox;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class ProxyScreen extends AbstractScreen {
    private final Screen parent;
    private AdaptiveTextBox ipBox;
    private AdaptiveTextBox portBox;
    private AdaptiveTextBox usernameBox;
    private AdaptiveTextBox passwordBox;
    private int statusColor = new Color(160, 160, 160).getRGB();
    private String statusMessage = "No Proxy Set";

    @Getter
    private static Proxy proxy = null;

    public ProxyScreen(Screen parent) {
        super("Proxy Screen");
        this.parent = parent;
    }

    @Override
    protected void initScreen() {
        float x = (this.width - 300) / 2f;
        float y = this.height / 2f - 40f;

        ipBox = new AdaptiveTextBox("IP Address");
        ipBox.setBounds(x, y, 195, 22);
        textBoxes.add(ipBox);

        portBox = new AdaptiveTextBox("Port");
        portBox.setBounds(x + 205, y, 95, 22);
        textBoxes.add(portBox);

        usernameBox = new AdaptiveTextBox("Username (Optional)");
        usernameBox.setBounds(x, y + 32f, 145, 22);
        textBoxes.add(usernameBox);

        passwordBox = new AdaptiveTextBox("Password (Optional)");
        passwordBox.setPasswordMode(true);
        passwordBox.setBounds(x + 155, y + 32f, 145, 22);
        textBoxes.add(passwordBox);

        if (proxy != null) {
            ipBox.setText(proxy.host);
            portBox.setText(String.valueOf(proxy.port));
            if (proxy.username != null) usernameBox.setText(proxy.username);
            if (proxy.password != null) passwordBox.setText(proxy.password);
            statusMessage = "Current Proxy: " + proxy.host + ":" + proxy.port;
            statusColor = new Color(85, 255, 85).getRGB();
        }

        AdaptiveButton btnSet = new AdaptiveButton("Set Proxy", this::handleSetProxy);
        btnSet.setBounds(x, y + 72f, 145, 22);
        buttons.add(btnSet);

        AdaptiveButton btnClear = new AdaptiveButton("Clear Proxy", this::handleClearProxy);
        btnClear.setBounds(x + 155, y + 72f, 145, 22);
        buttons.add(btnClear);

        AdaptiveButton btnBack = new AdaptiveButton("Back", () -> mc.setScreen(parent));
        btnBack.setBounds(x, y + 112f, 300, 22);
        buttons.add(btnBack);
    }

    private void handleSetProxy() {
        String ip = ipBox.getText().trim();
        String portStr = portBox.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            statusMessage = "不输入IP和端口代理你冯呢?";
            statusColor = new Color(255, 85, 85).getRGB();
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            String text = (ip.equals("8964") || port == 8964) ? "Proxy Set: " + ip + ":" + port + " (一个见证区?)" : "Proxy Set: " + ip + ":" + port;
            proxy = new Proxy(ip, port, usernameBox.getText().trim().isEmpty() ? null : usernameBox.getText().trim(), passwordBox.getText().trim().isEmpty() ? null : passwordBox.getText().trim());
            statusMessage = text;
            statusColor = new Color(85, 255, 85).getRGB();
        } catch (NumberFormatException e) {
            statusMessage = "端口必须是数字!";
            statusColor = new Color(255, 85, 85).getRGB();
        }
    }

    private void handleClearProxy() {
        proxy = null;
        for (AdaptiveTextBox box : textBoxes) {
            box.setText("");
        }
        statusMessage = "Proxy Cleared";
        statusColor = new Color(160, 160, 160).getRGB();
    }

    @Override
    protected void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        TrueTypeFont font50 = instance.getFontManager().getFont(50);
        TrueTypeFont font19 = instance.getFontManager().getFont(19);

        float titleWidth = font50.getStringWidth("Proxy");
        font50.drawString(context, "Proxy", (this.width - titleWidth) / 2f, this.height / 2f - 95f, -1, true);

        float statusWidth = font19.getStringWidth(statusMessage);
        font19.drawString(context, statusMessage, (this.width - statusWidth) / 2f, this.height / 2f - 65f, statusColor, true);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (super.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            handleSetProxy();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_TAB) {
            for (int i = 0; i < textBoxes.size(); i++) {
                if (textBoxes.get(i).isFocused()) {
                    textBoxes.get(i).setFocused(false);
                    textBoxes.get((i + 1) % textBoxes.size()).setFocused(true);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}