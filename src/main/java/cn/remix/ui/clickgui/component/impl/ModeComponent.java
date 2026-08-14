package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.ModeValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

public final class ModeComponent extends Component {
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);

    public ModeComponent(ModuleButton parent, ModeValue value) {
        super(parent, value);
    }

    @Override
    public float getHeight() {
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1.0 : 0.0);
        return 14.0f * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 14.0f * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01f) return;

        ModeValue mv = (ModeValue) getValue();
        var font = instance.getFontManager().getFont(16);
        float textY = y + (14.0f - font.getHeight()) / 2.0f + 1.0f;

        int alpha = MathHelper.clamp((int) (255.0f * finalProgress), 0, 255);
        font.drawString(context, mv.getName(), x + 4.0f, textY, new Color(204, 204, 204, alpha).getRGB());
        font.drawString(context, mv.getValue(), x + width - 4.0f - font.getStringWidth(mv.getValue()), textY, ColorUtil.applyAlpha(parent.getModulePanel().getAccent(), alpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (visibleAnimation.getValue() < 0.8f || !hovered(mouseX, mouseY) || (button != 0 && button != 1)) return;
        ModeValue mv = (ModeValue) getValue();
        String[] modes = mv.getModes();
        int index = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(mv.getValue())) { index = i; break; }
        }
        mv.setValue(modes[(index + (button == 0 ? 1 : -1) + modes.length) % modes.length]);
    }
}