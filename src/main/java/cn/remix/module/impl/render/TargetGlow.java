package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render3D;
import cn.remix.util.shader.impl.FeatherShader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

public final class TargetGlow extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Feather", "Feather", "Box");

    // Feather（着色器羽化光晕）
    private final ColorValue color = new ColorValue("Color", Color.BLACK);
    private final NumberValue radius = new NumberValue("Radius", 6, 1, 30, 1);
    private final NumberValue feather = new NumberValue("Feather", 2, 0, 20, 1);
    private final NumberValue opacity = new NumberValue("Opacity", 150, 5, 255, 5);
    private final BoolValue pulse = new BoolValue("Pulse", true);
    private final NumberValue pulseSpeed = new NumberValue("Pulse Speed", 1.5f, 0.2f, 5.0f, 0.1f, pulse::getValue);

    // Box（原描边羽化）
    private final NumberValue size = new NumberValue("Box Size", 0.35f, 0.05f, 1.0f, 0.05f, () -> mode.is("Box"));
    private final NumberValue layers = new NumberValue("Box Layers", 8, 2, 20, 1, () -> mode.is("Box"));

    private final FeatherShader featherShader = new FeatherShader();

    public TargetGlow() {
        super("TargetGlow", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Aura aura = getModule(Aura.class);
        if (aura == null || !aura.isEnabled()) return;

        LivingEntity target = aura.getTarget();
        if (target == null || !target.isAlive() || target.isDead()) return;

        if (mode.is("Feather")) {
            renderFeather(event, target);
        } else {
            renderBox(event, target);
        }
    }

    private void renderFeather(Render3DEvent event, LivingEntity target) {
        // 将实体包围盒 8 角投影到屏幕，得到屏幕矩形
        var box = target.getBoundingBox();
        Vec3d[] corners = {
                new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ), new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ), new Vec3d(box.maxX, box.maxY, box.maxZ)
        };

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean anyVisible = false;
        for (Vec3d corner : corners) {
            Vec3d screen = ProjectUtil.worldSpaceToScreenSpace(corner, event.getProjectionMatrix(), event.getModelViewMatrix());
            if (screen.z > 0 && screen.z < 1) {
                minX = Math.min(minX, (float) screen.x);
                minY = Math.min(minY, (float) screen.y);
                maxX = Math.max(maxX, (float) screen.x);
                maxY = Math.max(maxY, (float) screen.y);
                anyVisible = true;
            }
        }
        if (!anyVisible) return;

        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float w = maxX - minX;
        float h = maxY - minY;
        if (w <= 0 || h <= 0) return;

        float pulseScale = 1.0f;
        if (pulse.getValue()) {
            double t = System.currentTimeMillis() / 1000.0 * pulseSpeed.getValue();
            pulseScale = 0.8f + 0.2f * (float) Math.sin(t * Math.PI * 2);
        }

        Color c = color.getValue();
        Color drawColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(opacity.getValue() * pulseScale));
        featherShader.draw(centerX, centerY, w + radius.getValue() * 2, h + radius.getValue() * 2,
                radius.getValue(), feather.getValue(), drawColor);
    }

    private void renderBox(Render3DEvent event, LivingEntity target) {
        var box = target.getBoundingBox();
        int baseColor = color.getValue().getRGB();
        int layerCount = layers.getValue().intValue();
        float featherSize = size.getValue();

        float pulseScale = 1.0f;
        if (pulse.getValue()) {
            double t = System.currentTimeMillis() / 1000.0 * pulseSpeed.getValue();
            pulseScale = 0.75f + 0.25f * (float) Math.sin(t * Math.PI * 2);
        }

        for (int i = 0; i < layerCount; i++) {
            float progress = (i + 1) / (float) layerCount;
            float lineWidth = featherSize * progress * 2.0f * pulseScale;
            int alpha = Math.round(opacity.getValue() * (1.0f - progress));
            int layerColor = ColorUtil.applyAlpha(baseColor, alpha);
            Render3D.drawOutlinedBox(event.getMatrixStack(), box, lineWidth, layerColor, false);
        }
    }
}
