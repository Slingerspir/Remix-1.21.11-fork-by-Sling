package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.move.Clipper;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import net.minecraft.util.math.Box;

import java.awt.*;

public final class ClipHUD extends Module {
    private final BoolValue highlightBlock = new BoolValue("Highlight Block", true);
    private final NumberValue highlightAnimationSpeed = new NumberValue("Highlight Animation Speed", 0.2f, 0.01f, 1.0f, 0.01f, highlightBlock::getValue);
    private Box renderBox;

    public ClipHUD() {
        super("ClipHUD", Category.Render);
    }

    @Override
    public void onDisable() {
        renderBox = null;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Clipper.ClipTarget target = getClipTarget();
        if (target == null) return;

        TrueTypeFont font = instance.getFontManager().getFont(16);
        String text = String.format("%s Clipper Available (%.1fm)", target.down() ? "v" : "^", target.distance());
        float x = mc.getWindow().getScaledWidth() / 2.0f - font.getStringWidth(text) - 12.0f;
        float y = mc.getWindow().getScaledHeight() / 2.0f - font.getHeight() / 2.0f;
        font.drawStringWithShadow(event.getContext(), text, x, y, new Color(190, 85, 255).getRGB());
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!highlightBlock.getValue()) return;

        Clipper.ClipTarget target = getClipTarget();
        if (target == null) {
            renderBox = null;
            return;
        }

        Box targetBox = new Box(target.platform());
        renderBox = renderBox != null ? lerpBox(renderBox, targetBox, highlightAnimationSpeed.getValue()) : targetBox;
        Render3D.drawBox(event.getMatrixStack(), renderBox, ColorUtil.applyAlpha(new Color(135, 206, 250).getRGB(), 70));
    }

    private Clipper.ClipTarget getClipTarget() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return null;

        Clipper clipper = getModule(Clipper.class);
        if (clipper == null) return null;

        return clipper.getTarget();
    }

    private Box lerpBox(Box from, Box to, float speed) {
        float t = Math.clamp(speed, 0.01f, 1.0f);
        return new Box(
                lerp(from.minX, to.minX, t),
                lerp(from.minY, to.minY, t),
                lerp(from.minZ, to.minZ, t),
                lerp(from.maxX, to.maxX, t),
                lerp(from.maxY, to.maxY, t),
                lerp(from.maxZ, to.maxZ, t)
        );
    }

    private double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
