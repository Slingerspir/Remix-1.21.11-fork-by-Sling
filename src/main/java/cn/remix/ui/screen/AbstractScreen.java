package cn.remix.ui.screen;

import cn.remix.ui.screen.util.AdaptiveButton;
import cn.remix.ui.screen.util.AdaptiveTextBox;
import cn.remix.util.IMinecraft;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractScreen extends Screen implements IMinecraft {
    protected final List<AdaptiveButton> buttons = new ArrayList<>();
    protected final List<AdaptiveTextBox> textBoxes = new ArrayList<>();
    protected float centerX;
    protected float centerY;

    public AbstractScreen(String title) {
        super(Text.literal(title));
    }

    @Override
    protected final void init() {
        this.buttons.clear();
        this.textBoxes.clear();
        this.centerX = (this.width - 200) / 2f;
        this.centerY = this.height / 4f + 48f;
        initScreen();
    }

    protected abstract void initScreen();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Render2D.drawRect(context, 0, 0, this.width, this.height, new Color(28, 28, 28).getRGB());
        renderScreen(context, mouseX, mouseY, delta);
        for (AdaptiveTextBox box : textBoxes) {
            box.render(context);
        }
        for (AdaptiveButton btn : buttons) {
            btn.render(context, mouseX, mouseY);
        }
    }

    protected void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (AdaptiveTextBox box : textBoxes) {
            box.mouseClicked(click);
        }
        if (click.button() == 0) {
            for (AdaptiveButton btn : buttons) {
                if (btn.isHovered(click.x(), click.y())) {
                    btn.onClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean charTyped(CharInput input) {
        for (AdaptiveTextBox box : textBoxes) {
            if (box.charTyped(input)) return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        for (AdaptiveTextBox box : textBoxes) {
            if (box.keyPressed(input)) return true;
        }
        return super.keyPressed(input);
    }
}