package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

public final class NumberComponent extends Component {
    private final EasingAnimation animation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private boolean dragging;

    public NumberComponent(ModuleButton parent, NumberValue value) {
        super(parent, value);
    }

    @Override
    public float getHeight() {
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1.0 : 0.0);
        return 20.0f * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 20.0f * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01f) return;
        if (dragging) updateValue(mouseX);

        NumberValue nv = (NumberValue) getValue();
        var font = instance.getFontManager().getFont(16);
        String display = nv.getInc() % 1.0f == 0.0f ? String.valueOf(nv.getValue().longValue()) : String.format("%.2f", nv.getValue());

        int alpha = MathHelper.clamp((int) (255.0f * finalProgress), 0, 255);
        font.drawString(context, nv.getName(), x + 4.0f, y + 2.0f, new Color(204, 204, 204, alpha).getRGB());
        font.drawString(context, display, x + width - 6.0f - font.getStringWidth(display), y + 2.0f, new Color(154, 154, 170, alpha).getRGB());

        float barW = width - 10.0f;
        animation.run(MathHelper.clamp((nv.getValue() - nv.getMin()) / (nv.getMax() - nv.getMin()), 0.0f, 1.0f));
        float ratio = animation.getValue().floatValue();

        Render2D.drawRect(context, x + 4, y + 14, barW, 2, ColorUtil.applyAlpha(new Color(58, 58, 63, alpha).getRGB(), alpha));
        Render2D.drawRect(context, x + 4, y + 14, barW * ratio, 2, ColorUtil.applyAlpha(parent.getModulePanel().getAccent(), alpha));
        Render2D.drawRect(context, x + 4 + barW * ratio - 1, y + 12, 3, 6, ColorUtil.applyAlpha(Color.WHITE.getRGB(), alpha));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && visibleAnimation.getValue() > 0.8 && hovered(mouseX, mouseY) && mouseY >= y + 11.0f) {
            dragging = true;
            updateValue(mouseX);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
    }

    private void updateValue(double mouseX) {
        NumberValue nv = (NumberValue) getValue();
        float ratio = MathHelper.clamp((float) ((mouseX - (x + 4.0f)) / (width - 10.0f)), 0.0f, 1.0f);
        nv.setValue(Math.round((nv.getMin() + ratio * (nv.getMax() - nv.getMin())) / nv.getInc()) * nv.getInc());
    }
}