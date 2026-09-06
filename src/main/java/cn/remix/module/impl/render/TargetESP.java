package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class TargetESP extends Module {
    private final BoolValue outline = new BoolValue("Outline", true);
    private final BoolValue tracer = new BoolValue("Tracer", true);
    private final NumberValue r = new NumberValue("Red", 0.95f, 0, 1, 0.01f);
    private final NumberValue g = new NumberValue("Green", 0.35f, 0, 1, 0.01f);
    private final NumberValue b = new NumberValue("Blue", 0.15f, 0, 1, 0.01f);
    private final NumberValue alpha = new NumberValue("Alpha", 0.9f, 0.05f, 1, 0.05f);
    private final NumberValue lineWidth = new NumberValue("Line Width", 2.0f, 1, 6, 0.5f);

    public TargetESP() {
        super("TargetESP", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Aura aura = getModule(Aura.class);
        if (aura == null || !aura.isEnabled()) return;

        LivingEntity target = aura.getTarget();
        if (target == null || !target.isAlive() || target.isDead()) return;

        int color = new java.awt.Color(r.getValue(), g.getValue(), b.getValue(), alpha.getValue()).getRGB();
        Box box = target.getBoundingBox();

        if (outline.getValue()) {
            Render3D.drawOutlinedBox(event.getMatrixStack(), box, lineWidth.getValue(), color, false);
            Render3D.drawBox(event.getMatrixStack(), box, ColorUtil.applyAlpha(color, 30), false);
        }

        if (tracer.getValue()) {
            Vec3d from = mc.player.getEyePos();
            Vec3d to = new Vec3d(target.getX(), target.getY() + target.getHeight() / 2.0, target.getZ());
            Render3D.drawLine(event.getMatrixStack(), from, to, color, false);
        }
    }
}
