package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.util.render.Render3D;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class Projectile extends Module {
    private final BoolValue showBow = new BoolValue("Bow", true);
    private final BoolValue showPearls = new BoolValue("Pearls", true);
    private final BoolValue showPotions = new BoolValue("Potions", false);
    private final BoolValue showEggs = new BoolValue("Eggs", false);
    private final BoolValue showSnowballs = new BoolValue("Snowballs", false);

    private static final int MAX_STEPS = 200;

    public Projectile() {
        super("Projectile", Category.Render);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        var stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return;

        ProjectileConfig config = getConfig(stack);
        if (config == null) return;

        List<Vec3d> path = simulate(config);
        if (path.size() < 2) return;

        int color = config.color.getRGB();

        for (int i = 0; i < path.size() - 1; i++) {
            Render3D.drawLine(event.getMatrixStack(), path.get(i), path.get(i + 1), color, false);
        }

        Vec3d end = path.get(path.size() - 1);
        Render3D.drawBox(event.getMatrixStack(), end, color, false);
    }

    private record ProjectileConfig(double gravity, double velocity, Color color) {}

    private ProjectileConfig getConfig(net.minecraft.item.ItemStack stack) {
        var item = stack.getItem();

        if (item == Items.BOW || item == Items.CROSSBOW) {
            if (!showBow.getValue()) return null;
            float charge = item == Items.BOW ? getBowCharge(stack) : 1.0f;
            return new ProjectileConfig(0.05, 3.0 * charge, new Color(255, 200, 100));
        }
        if (item == Items.ENDER_PEARL) {
            if (!showPearls.getValue()) return null;
            return new ProjectileConfig(0.03, 1.5, new Color(80, 255, 160));
        }
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            if (!showPotions.getValue()) return null;
            return new ProjectileConfig(0.05, 0.5, new Color(255, 66, 249));
        }
        if (item == Items.EGG) {
            if (!showEggs.getValue()) return null;
            return new ProjectileConfig(0.03, 1.5, new Color(255, 255, 255));
        }
        if (item == Items.SNOWBALL) {
            if (!showSnowballs.getValue()) return null;
            return new ProjectileConfig(0.03, 1.5, new Color(255, 255, 255));
        }
        if (item == Items.TRIDENT) {
            if (!showBow.getValue()) return null;
            return new ProjectileConfig(0.015, 2.5, new Color(150, 220, 255));
        }
        return null;
    }

    private float getBowCharge(net.minecraft.item.ItemStack stack) {
        if (mc.player.isUsingItem() && mc.player.getActiveItem().equals(stack)) {
            int ticks = mc.player.getItemUseTime();
            float progress = ticks / 20.0f;
            progress = (progress * progress + progress * 2) / 3.0f;
            if (progress > 1) progress = 1;
            return progress;
        }
        return 1.0f;
    }

    private List<Vec3d> simulate(ProjectileConfig config) {
        Vec3d pos = mc.player.getEyePos();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        double radPitch = Math.toRadians(pitch);
        double radYaw = Math.toRadians(yaw);
        Vec3d dir = new Vec3d(
                -Math.cos(radPitch) * Math.sin(radYaw),
                -Math.sin(radPitch),
                Math.cos(radPitch) * Math.cos(radYaw)
        );

        Vec3d vel = dir.multiply(config.velocity);

        List<Vec3d> points = new ArrayList<>();
        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3d prev = pos;
            vel = new Vec3d(vel.x, vel.y - config.gravity, vel.z);
            pos = prev.add(vel);

            HitResult hit = mc.world.raycast(new RaycastContext(prev, pos,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getPos());
                return points;
            }

            points.add(pos);

            if (pos.y < mc.world.getBottomY()) break;
        }
        return points;
    }
}
