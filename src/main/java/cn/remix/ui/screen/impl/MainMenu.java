package cn.remix.ui.screen.impl;

import cn.remix.ui.font.TrueTypeFont;
import cn.remix.ui.screen.AbstractScreen;
import cn.remix.ui.screen.impl.proxy.ProxyScreen;
import cn.remix.ui.screen.impl.token.TokenScreen;
import cn.remix.ui.screen.util.AdaptiveButton;
import cn.remix.util.render.Render2D;
import injection.MixinTitleScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.util.Identifier;

import java.awt.*;

public class MainMenu extends AbstractScreen {

    private static final Identifier LOGO = Identifier.of("remix", "textures/mainmenu/remix.png");
    private static final Identifier BACKGROUND = Identifier.of("remix", "textures/mainmenu/background.png");

    public MainMenu() {
        super("Main Menu");
    }

    @Override
    protected void initScreen() {
        buttons.clear();

        float centerX = this.width / 2f;
        float centerY = this.height / 2f;
        float gap = 30f;
        float buttonWidth = 200f;
        float buttonHeight = 26f;
        float startY = centerY + 20f;

        AdaptiveButton single = new AdaptiveButton("Singleplayer", () -> mc.setScreen(new OwnSelectWorldScreen(this)));
        single.setBounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight);
        buttons.add(single);

        AdaptiveButton multi = new AdaptiveButton("Multiplayer", () -> mc.setScreen(new MultiplayerScreen(this)));
        multi.setBounds(centerX - buttonWidth / 2, startY + gap, buttonWidth, buttonHeight);
        buttons.add(multi);

        AdaptiveButton token = new AdaptiveButton("Token Manager", () -> mc.setScreen(new TokenScreen(this)));
        token.setBounds(centerX - buttonWidth / 2, startY + gap * 2, buttonWidth, buttonHeight);
        buttons.add(token);

        float halfWidth = 97f;
        AdaptiveButton options = new AdaptiveButton("Options", () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        options.setBounds(centerX - halfWidth - 3f, startY + gap * 3, halfWidth, buttonHeight);
        buttons.add(options);

        AdaptiveButton proxy = new AdaptiveButton("Proxy", () -> mc.setScreen(new ProxyScreen(this)));
        proxy.setBounds(centerX + 3f, startY + gap * 3, halfWidth, buttonHeight);
        buttons.add(proxy);

        AdaptiveButton quit = new AdaptiveButton("Quit", mc::close);
        quit.setBounds(centerX - buttonWidth / 2, startY + gap * 4, buttonWidth, buttonHeight);
        buttons.add(quit);

        
        AdaptiveButton vanillaBtn = new AdaptiveButton("Vanilla Menu", () -> {
            System.setProperty("remix.custom_menu", "false");
            mc.setScreen(new TitleScreen());
        });
        vanillaBtn.setBounds(10, this.height - 36, 100, 26);
        buttons.add(vanillaBtn);
    }

    @Override
    protected void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        float screenWidth = this.width;
        float screenHeight = this.height;
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        Render2D.drawTexture(context, BACKGROUND, 0, 0, screenWidth, screenHeight, 0, 0, 1, 1, 0xFFFFFFFF);

        Render2D.drawRect(context, 0, 0, screenWidth, screenHeight, new Color(0, 0, 0, 70).getRGB());

        float logoSize = 72f;
        float logoX = (screenWidth - logoSize) / 2f;
        float logoY = screenHeight * 0.18f;
        Render2D.drawTexture(context, LOGO, logoX, logoY, logoSize, logoSize);

        TrueTypeFont font28 = instance.getFontManager().getFont(28);
        String title = "Remix Client Fork By Sling";
        float titleX = (screenWidth - font28.getStringWidth(title)) / 2f;
        float titleY = logoY + logoSize + 24f;
        font28.drawString(context, title, titleX, titleY, new Color(255, 255, 255, 220).getRGB(), false);

        TrueTypeFont font12 = instance.getFontManager().getFont(12);
        String version = "v1.0.0 | MC 1.21.11";
        float verX = (screenWidth - font12.getStringWidth(version)) / 2f;
        float verY = titleY + 24f;
        font12.drawString(context, version, verX, verY, new Color(180, 185, 200, 130).getRGB(), false);

        float gap = 30f;
        float buttonWidth = 200f;
        float startY = centerY + 20f;
        float cardWidth = buttonWidth + 40f;
        float cardHeight = gap * 5 + 30f;
        float cardX = centerX - cardWidth / 2;
        float cardY = startY - 15f;
        Render2D.drawRect(context, cardX, cardY, cardWidth, cardHeight, new Color(0, 0, 0, 80).getRGB());

        for (AdaptiveButton btn : buttons) {
            btn.render(context, mouseX, mouseY, delta);
        }

        String userText = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
        String footerText = "Logged in as " + userText;
        float footerX = screenWidth - font12.getStringWidth(footerText) - 14;
        float footerY = screenHeight - 22;
        font12.drawString(context, footerText, footerX, footerY, new Color(180, 185, 200, 100).getRGB(), false);
    }
}