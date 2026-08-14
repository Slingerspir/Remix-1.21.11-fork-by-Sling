package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.MultiBoolValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.List;

public final class MultiBoolComponent extends Component {
    private final EasingAnimation[] animations;
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);

    public MultiBoolComponent(ModuleButton parent, MultiBoolValue value) {
        super(parent, value);
        List<BoolValue> subValues = value.getValues();
        this.animations = new EasingAnimation[subValues.size()];
        for (int i = 0; i < animations.length; i++) this.animations[i] = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    }

    @Override
    public float getHeight() {
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1.0 : 0.0);
        var font = instance.getFontManager().getFont(16);
        return (font.getHeight() + 6.0f + (((MultiBoolValue) getValue()).getValues().size() * 14.0f)) * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        var font = instance.getFontManager().getFont(16);
        List<BoolValue> subValues = ((MultiBoolValue) getValue()).getValues();
        this.height = (font.getHeight() + 6.0f + (subValues.size() * 14.0f)) * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01f) return;

        int alpha = MathHelper.clamp((int) (255.0f * finalProgress), 0, 255);
        String title = " - " + getValue().getName() + " - ";
        font.drawString(context, title, x + (width - font.getStringWidth(title)) / 2.0f, y + 2.0f, new Color(204, 204, 204, alpha).getRGB());

        float offset = font.getHeight() + 4.0f;
        for (int i = 0; i < subValues.size(); i++) {
            BoolValue bool = subValues.get(i);
            font.drawString(context, bool.getName(), x + 4.0f, y + offset + (14.0f - font.getHeight()) / 2.0f + 0.5f, new Color(170, 170, 170, alpha).getRGB());
            animations[i].run(bool.getValue() ? 1.0 : 0.0);
            Render2D.drawRect(context, x + width - 11.0f, y + offset + 3.5f, 7.0f, 7.0f, ColorUtil.applyAlpha(ColorUtil.interpolate(new Color(58, 58, 63, alpha).getRGB(), parent.getModulePanel().getAccent(), animations[i].getValue().floatValue()), alpha));
            offset += 14.0f;
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || visibleAnimation.getValue() < 0.8) return;
        float offset = instance.getFontManager().getFont(16).getHeight() + 4.0f;
        for (BoolValue bool : ((MultiBoolValue) getValue()).getValues()) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y + offset && mouseY <= y + offset + 14.0f) bool.toggle();
            offset += 14.0f;
        }
    }
}