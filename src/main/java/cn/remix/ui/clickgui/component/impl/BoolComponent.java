package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.BoolValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

public final class BoolComponent extends Component {
    private final EasingAnimation animation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);

    public BoolComponent(ModuleButton parent, BoolValue value) {
        super(parent, value);
    }

    @Override
    public float getHeight() {
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1 : 0);
        return 14 * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 14 * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01f) return;

        BoolValue bv = (BoolValue) getValue();
        var font = instance.getFontManager().getFont(16);
        int alpha = MathHelper.clamp((int) (255 * finalProgress), 0, 255);

        font.drawString(context, bv.getName(), x + 4, y + (14 - font.getHeight()) / 2.0f + 0.5f, new Color(204, 204, 204, alpha).getRGB());
        animation.run(bv.getValue() ? 1 : 0);

        int targetColor = ColorUtil.interpolate(new Color(58, 58, 63, alpha).getRGB(), parent.getModulePanel().getAccent(), animation.getValue().floatValue());
        Render2D.drawRect(context, x + width - 11, y + 3.5f, 7, 7, ColorUtil.applyAlpha(targetColor, alpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered(mouseX, mouseY) && visibleAnimation.getValue().floatValue() > 0.8f) ((BoolValue) getValue()).toggle();
    }
}