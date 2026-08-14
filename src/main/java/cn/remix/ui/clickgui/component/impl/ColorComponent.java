package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.ColorValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

@Getter
public final class ColorComponent extends Component {
    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private int dragging = -1;

    public ColorComponent(ModuleButton parent, ColorValue value) {
        super(parent, value);
    }

    @Override
    public float getHeight() {
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1 : 0);
        return 78 * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 78 * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01f) return;

        ColorValue cv = (ColorValue) getValue();
        var font = instance.getFontManager().getFont(16);
        int alpha = MathHelper.clamp((int) (255 * finalProgress), 0, 255);

        font.drawString(context, cv.getName(), x + 4, y + (14 - font.getHeight()) / 2.0f + 1, new Color(204, 204, 204, alpha).getRGB());
        Render2D.drawRect(context, x + width - 11, y + 3.5f, 7, 7, ColorUtil.applyAlpha(cv.getValue().getRGB(), alpha));

        float satX = x + 4, satY = y + 14, satW = width - 8;
        if (dragging == 0) {
            cv.setHSB(cv.getHue(), MathHelper.clamp((mouseX - satX) / satW, 0, 1), 1 - MathHelper.clamp((mouseY - satY) / 50, 0, 1));
        } else if (dragging == 1) {
            cv.setHSB(MathHelper.clamp((mouseX - satX) / satW, 0, 1), cv.getSaturation(), cv.getBrightness());
        }

        for (int i = 0; i < (int) satW; i++) {
            float ratio = i / satW;
            Render2D.drawGradient(context, satX + i, satY, 1, 50, ColorUtil.applyAlpha(Color.HSBtoRGB(cv.getHue(), ratio, 1), alpha), ColorUtil.applyAlpha(0, alpha), false);
            Render2D.drawRect(context, satX + i, satY + 54, 1, 6, ColorUtil.applyAlpha(Color.HSBtoRGB(ratio, 1, 1), alpha));
        }

        int white = new Color(255, 255, 255, alpha).getRGB();
        Render2D.drawRect(context, satX + cv.getSaturation() * satW - 2, satY + (1 - cv.getBrightness()) * 50 - 2, 4, 4, white);
        Render2D.drawRect(context, satX + cv.getHue() * satW - 1, satY + 53, 2, 8, white);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (visibleAnimation.getValue().floatValue() < 0.8f || button != 0) return;

        float satX = x + 4, satW = width - 8;
        if (mouseX >= satX && mouseX <= satX + satW) {
            if (mouseY >= y + 14 && mouseY <= y + 64) dragging = 0;
            else if (mouseY >= y + 66 && mouseY <= y + 76) dragging = 1;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = -1;
    }
}