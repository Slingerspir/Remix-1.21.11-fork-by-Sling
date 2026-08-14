package cn.remix.ui.screen.impl;

import cn.remix.ui.screen.AbstractScreen;
import cn.remix.ui.screen.impl.proxy.ProxyScreen;
import cn.remix.ui.screen.impl.token.TokenScreen;
import cn.remix.ui.screen.util.AdaptiveButton;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.util.Identifier;

public class MainMenu extends AbstractScreen {
    private static final Identifier LOGO = Identifier.of("remix", "textures/mainmenu/remix.png");

    public MainMenu() {
        super("Main Menu");
    }

    @Override
    protected void initScreen() {
        float gap = 28;

        AdaptiveButton single = new AdaptiveButton("Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this)));
        single.setBounds(centerX, centerY, 200, 22);
        buttons.add(single);

        AdaptiveButton multi = new AdaptiveButton("Multiplayer", () -> mc.setScreen(new MultiplayerScreen(this)));
        multi.setBounds(centerX, centerY + gap, 200, 22);
        buttons.add(multi);

        AdaptiveButton token = new AdaptiveButton("Token Manager", () -> mc.setScreen(new TokenScreen(this)));
        token.setBounds(centerX, centerY + gap * 2, 200, 22);
        buttons.add(token);

        AdaptiveButton options = new AdaptiveButton("Options", () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        options.setBounds(centerX, centerY + gap * 3, 98, 22);
        buttons.add(options);

        AdaptiveButton proxy = new AdaptiveButton("Proxy", () -> mc.setScreen(new ProxyScreen(this)));
        proxy.setBounds(centerX + 102f, centerY + gap * 3, 98, 22);
        buttons.add(proxy);

        AdaptiveButton quit = new AdaptiveButton("Quit", mc::close);
        quit.setBounds(centerX, centerY + gap * 4, 200, 22);
        buttons.add(quit);
    }

    @Override
    protected void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        float logoX = (this.width - 80) / 2f;
        float logoY = this.centerY - 80;
        Render2D.drawTexture(context, LOGO, logoX, logoY, 80, 80);
    }
}