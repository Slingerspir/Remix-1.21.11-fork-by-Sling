package cn.remix.ui.clickgui;

import cn.remix.module.Module;
import cn.remix.module.value.Value;
import cn.remix.module.value.impl.*;
import cn.remix.ui.clickgui.component.Component;
import cn.remix.ui.clickgui.component.impl.*;
import cn.remix.ui.clickgui.panel.impl.ModulePanel;
import cn.remix.util.IMinecraft;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.misc.KeyUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class ModuleButton implements IMinecraft {
    public static final float height = 16;
    private final ModulePanel modulePanel;
    private final Module module;
    private final List<Component> components = new ArrayList<>();
    private final EasingAnimation openAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 200);
    private final EasingAnimation toggleAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    private final EasingAnimation hoverAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 150);
    private boolean extended, binding;
    private float renderHeight = height;

    public ModuleButton(ModulePanel modulePanel, Module module) {
        this.modulePanel = modulePanel;
        this.module = module;
        for (Value value : module.getValues()) {
            if (value instanceof BoolValue bool) components.add(new BoolComponent(this, bool));
            else if (value instanceof NumberValue num) components.add(new NumberComponent(this, num));
            else if (value instanceof ModeValue mode) components.add(new ModeComponent(this, mode));
            else if (value instanceof MultiBoolValue multi) components.add(new MultiBoolComponent(this, multi));
            else if (value instanceof ColorValue color) components.add(new ColorComponent(this, color));
        }
    }

    public float render(DrawContext context, float x, float y, float width, int mouseX, int mouseY, float globalAlpha) {
        var font = instance.getFontManager().getBoldFont(16);
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int alphaInt = (int) (255 * globalAlpha);

        hoverAnimation.run(hovered ? 1 : 0);
        int bg = ColorUtil.interpolate(new Color(34, 34, 38).getRGB(), new Color(42, 42, 48).getRGB(), hoverAnimation.getValue().floatValue());
        Render2D.drawRect(context, x, y, width, height, ColorUtil.applyAlpha(bg, alphaInt));

        toggleAnimation.run(module.isEnabled() ? 1 : 0);
        float fontY = y + (height - font.getHeight()) / 2.0f + 0.5f;
        int txtColor = ColorUtil.interpolate(new Color(170, 170, 170).getRGB(), Color.WHITE.getRGB(), toggleAnimation.getValue().floatValue());

        font.drawString(context, binding ? "Bind: " + KeyUtil.getKeyName(module.getKey()) : module.getName(), x + 7, fontY, ColorUtil.applyAlpha(txtColor, alphaInt));
        if (!components.isEmpty()) font.drawString(context, extended ? "-" : "+", x + width - 9, fontY, ColorUtil.applyAlpha(new Color(136, 136, 136).getRGB(), alphaInt));

        float totalHeight = 0;
        for (Component c : components) totalHeight += c.getHeight();

        openAnimation.run(extended ? 1 : 0);
        float animHeight = openAnimation.getValue().floatValue() * totalHeight;

        if (animHeight > 0.5f) {
            Render2D.beginScissor(context, x, y + height, width, animHeight);
            float offset = 0;
            for (Component c : components) {
                if (c.getHeight() < 0.01f && !c.getValue().isVisible()) continue;
                c.render(context, x, y + height + offset, width, mouseX, mouseY, globalAlpha);
                offset += c.getHeight();
            }
            Render2D.endScissor(context);
        }
        return this.renderHeight = height + animHeight;
    }

    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y, float width) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            if (button == 0) module.toggle();
            else if (button == 1) extended = !extended;
            else if (button == 2) binding = true;
            return;
        }
        if (extended && openAnimation.getValue().floatValue() > 0.5f) {
            for (Component c : components) {
                if (c.getHeight() >= 0.01f || c.getValue().isVisible()) c.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(c -> c.mouseReleased(mouseX, mouseY, button));
    }

    public boolean keyTyped(int keyCode) {
        if (binding) {
            module.setKey(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode);
            binding = false;
            return true;
        }
        return false;
    }
}