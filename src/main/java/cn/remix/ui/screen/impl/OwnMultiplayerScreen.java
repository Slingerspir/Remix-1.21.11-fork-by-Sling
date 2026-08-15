package cn.remix.ui.screen.impl;

import cn.remix.Client;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.*;

public final class OwnMultiplayerScreen extends MultiplayerScreen {

    private static final Identifier BACKGROUND = Identifier.of("remix", "textures/mainmenu/background.png");
    private final Screen parent;

    public OwnMultiplayerScreen(Screen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = this.width;
        int height = this.height;

        Render2D.drawTexture(context, BACKGROUND, 0, 0, width, height, 0, 0, 1, 1, 0xFFFFFFFF);
        Render2D.drawRect(context, 0, 0, width, height, new Color(0, 0, 0, 60).getRGB());

        TrueTypeFont font24 = Client.instance.getFontManager().getFont(24);
        String title = "Multiplayer";
        float titleX = (width - font24.getStringWidth(title)) / 2f;
        font24.drawString(context, title, titleX, 10, new Color(255, 255, 255, 180).getRGB(), false);

        if (this.serverListWidget != null) {
            float listY = 42;
            float listHeight = height - 102;
            Render2D.drawRect(context, 16, listY, width - 32, listHeight, new Color(0, 0, 0, 50).getRGB());
            Render2D.drawOutline(context, 16, listY, width - 32, listHeight, 1.0f, new Color(255, 255, 255, 15).getRGB());

            this.refreshWidgetPositions();
            this.serverListWidget.render(context, mouseX, mouseY, delta);
        }

        for (var child : this.children()) {
            if (child instanceof ButtonWidget button) {
                drawStyledButton(context, button, mouseX, mouseY);
            }
        }

        TrueTypeFont font12 = Client.instance.getFontManager().getFont(12);
        String footer = "Servers are stored in .minecraft/servers.dat";
        float footerX = (width - font12.getStringWidth(footer)) / 2f;
        font12.drawString(context, footer, footerX, height - 12,
                new Color(180, 185, 200, 50).getRGB(), false);
    }

    private void drawStyledButton(DrawContext context, ButtonWidget button, int mouseX, int mouseY) {
        if (button == null) return;

        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();

        boolean hovered = mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
        boolean active = button.active;

        int bgAlpha = active ? (hovered ? 40 : 20) : 10;
        int bgColor = new Color(255, 255, 255, bgAlpha).getRGB();
        Render2D.drawRect(context, x, y, width, height, bgColor);

        int borderAlpha = active ? (hovered ? 80 : 30) : 15;
        int borderColor = new Color(255, 255, 255, borderAlpha).getRGB();
        Render2D.drawOutline(context, x, y, width, height, 1.0f, borderColor);

        TrueTypeFont font14 = Client.instance.getFontManager().getFont(14);
        String text = button.getMessage().getString();
        int textColor = active ?
                (hovered ? new Color(255, 255, 255, 255).getRGB() :
                        new Color(200, 200, 220, 200).getRGB()) :
                new Color(150, 150, 170, 120).getRGB();

        float textX = x + (width - font14.getStringWidth(text)) / 2f;
        float textY = y + (height - font14.getHeight()) / 2f + 1f;
        font14.drawString(context, text, textX, textY, textColor, false);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}