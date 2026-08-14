package cn.remix.ui.clickgui.panel.impl;

import cn.remix.module.Category;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.panel.Panel;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class ModulePanel extends Panel {
    public static final float maxHeight = 350;
    private final Category category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private final EasingAnimation barAlphaAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 250);
    private long lastScrollTime;

    public ModulePanel(Category category, float x, float y) {
        super(x, y);
        this.category = category;
        instance.getModuleManager().getModuleMap().values().stream()
                .filter(m -> m.getCategory() == category)
                .forEach(m -> buttons.add(new ModuleButton(this, m)));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float globalAlpha) {
        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }

        var font = instance.getFontManager().getBoldFont(18);
        int alphaInt = (int) (255 * globalAlpha);

        Render2D.drawRect(context, x, y, width, headerHeight, ColorUtil.applyAlpha(new Color(26, 26, 30).getRGB(), alphaInt));
        Render2D.drawRect(context, x, y + headerHeight - 1, width, 1, ColorUtil.applyAlpha(getAccent(), alphaInt));

        font.drawString(context, category.getName(), x + 7, y + (headerHeight - font.getHeight()) / 2.0f + 0.5f, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alphaInt));

        float totalH = totalHeight();
        float bodyH = Math.min(totalH, maxHeight);

        Render2D.drawRect(context, x, y + headerHeight, width, bodyH, ColorUtil.applyAlpha(new Color(22, 22, 25).getRGB(), alphaInt));
        updateScroll(totalH, maxHeight);

        Render2D.beginScissor(context, x, y + headerHeight, width, bodyH);
        float buttonY = y + headerHeight + scrollAnimation.getValue().floatValue();
        for (ModuleButton b : buttons) {
            buttonY += b.render(context, x, buttonY, width, mouseX, mouseY, globalAlpha);
        }
        Render2D.endScissor(context);

        boolean active = totalH > maxHeight && (isHovered(mouseX, mouseY, x, y + headerHeight, width, bodyH)
                || (System.currentTimeMillis() - lastScrollTime < 1000)
                || Math.abs(targetScrollY - scrollAnimation.getValue()) > 1.0);

        barAlphaAnimation.run(active ? 1 : 0);
        float barAlpha = barAlphaAnimation.getValue().floatValue() * globalAlpha;

        if (barAlpha > 0.01f) {
            float barH = (maxHeight / totalH) * bodyH;
            float barY = y + headerHeight + (float) (-scrollAnimation.getValue() / totalH * bodyH);
            Render2D.drawRect(context, x + width - 3, barY + 1, 2, barH - 2, ColorUtil.applyAlpha(new Color(128, 128, 128).getRGB(), (int) (120 * barAlpha)));
        }
    }

    @Override
    public void mouseClicked(Click click) {
        handleDrag(click);
        if (dragging) {
            return;
        }
        if (isHovered(click.x(), click.y(), x, y + headerHeight, width, Math.min(totalHeight(), maxHeight))) {
            float buttonY = y + headerHeight + scrollAnimation.getValue().floatValue();
            for (ModuleButton mb : buttons) {
                mb.mouseClicked(click.x(), click.y(), click.button(), x, buttonY, width);
                buttonY += mb.getRenderHeight();
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        buttons.forEach(mb -> mb.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public void mouseScroll(double mouseX, double mouseY, double amount) {
        super.mouseScroll(mouseX, mouseY, amount);
        if (isHovered(mouseX, mouseY, x, y, width, headerHeight + maxHeight)) {
            lastScrollTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean keyTyped(KeyInput input) {
        return buttons.stream().anyMatch(mb -> mb.keyTyped(input.key()));
    }

    private float totalHeight() {
        return (float) buttons.stream().mapToDouble(b -> b.getRenderHeight() == 0 ? ModuleButton.height : b.getRenderHeight()).sum();
    }
}