package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.util.render.Render2D;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Compass extends Module {
    private final BoolValue compassOnly = new BoolValue("Compass Only", true);
    private final BoolValue noPlayerOnly = new BoolValue("No Player Only", true);

    public Compass() {
        super("Compass", Category.Render);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (compassOnly.getValue() && hasCompass()) return;
        if (noPlayerOnly.getValue() && hasNearbyPlayer()) return;

        Vec3d spawn = new Vec3d(mc.world.getSpawnPoint().getPos().getX(), mc.player.getY(), mc.world.getSpawnPoint().getPos().getZ());
        double dx = spawn.x - mc.player.getX();
        double dz = spawn.z - mc.player.getZ();

        if (dx == 0 && dz == 0) return;

        float angle = (float) Math.toDegrees(Math.atan2(dx, dz));
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float diff = MathHelper.wrapDegrees(angle - yaw);

        float cx = mc.getWindow().getScaledWidth() / 2.0f;
        float cy = mc.getWindow().getScaledHeight() / 2.0f;
        float length = 32.0f;

        double rad = Math.toRadians(diff);
        float ex = cx + (float) (Math.sin(rad) * length);
        float ey = cy - (float) (Math.cos(rad) * length);

        Render2D.drawLine(event.getContext(), cx, cy, ex, ey, 2.0f, 0xFFFF5555);
    }

    private boolean hasCompass() {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.COMPASS)) return true;
        }
        return false;
    }

    private boolean hasNearbyPlayer() {
        if (mc.player == null || mc.world == null) return false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (mc.player.distanceTo(player) < 20.0f) return true;
        }
        return false;
    }
}
