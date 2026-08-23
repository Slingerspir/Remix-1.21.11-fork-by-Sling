package cn.remix.ui.clickgui.component.impl;

import cn.remix.module.value.impl.StringValue;
import cn.remix.ui.clickgui.ModuleButton;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class StringComponent extends Component {
    private static final int MAX_LENGTH = 64;

    private final EasingAnimation visibleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private boolean focused;

    public StringComponent(ModuleButton parent, StringValue value) {
        super(parent, value);
    }

    @Override
    public float getHeight() {
        visibleAnimation.run(getValue().isVisible() && parent.isExtended() ? 1.0 : 0.0);
        return 16.0f * visibleAnimation.getValue().floatValue();
    }

    @Override
    public void render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        super.render(context, x, y, width, mouseX, mouseY, globalAlpha);
        float progress = visibleAnimation.getValue().floatValue();
        this.height = 16.0f * progress;
        float finalProgress = progress * globalAlpha;
        if (finalProgress < 0.01f) return;

        StringValue sv = (StringValue) getValue();
        var font = instance.getFontManager().getFont(16);
        float textY = y + (16.0f - font.getHeight()) / 2.0f + 1.0f;
        int alpha = MathHelper.clamp((int) (255.0f * finalProgress), 0, 255);

        font.drawString(context, sv.getName(), x + 4.0f, textY, new Color(204, 204, 204, alpha).getRGB());

        String value = sv.getValue();
        float valueX = x + 8.0f + font.getStringWidth(sv.getName());
        float maxTextWidth = Math.max(20.0f, x + width - 6.0f - valueX);
        if (font.getStringWidth(value) > maxTextWidth) {
            while (!value.isEmpty() && font.getStringWidth(value + "...") > maxTextWidth) {
                value = value.substring(0, value.length() - 1);
            }
            value = value + "...";
        }

        int valueColor = focused
                ? parent.getModulePanel().getAccent()
                : new Color(170, 170, 170).getRGB();
        font.drawString(context, value, valueX, textY, ColorUtil.applyAlpha(valueColor, alpha));

        if (focused) {
            int cursorX = (int) (valueX + font.getStringWidth(value));
            Render2D.drawRect(context, cursorX, y + 3.0f, 1.0f, 10.0f, ColorUtil.applyAlpha(parent.getModulePanel().getAccent(), alpha));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (visibleAnimation.getValue() < 0.8f || button != 0) return;
        focused = hovered(mouseX, mouseY);
    }

    public boolean keyTyped(int keyCode) {
        if (!focused) return false;

        StringValue sv = (StringValue) getValue();
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String value = sv.getValue();
            if (!value.isEmpty()) {
                sv.setValue(value.substring(0, value.length() - 1));
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
        }
        return true;
    }

    public boolean charTyped(CharInput input) {
        if (!focused || !input.isValidChar()) return false;

        StringValue sv = (StringValue) getValue();
        if (sv.getValue().length() < MAX_LENGTH) {
            sv.setValue(sv.getValue() + input.asString());
        }
        return true;
    }
}
