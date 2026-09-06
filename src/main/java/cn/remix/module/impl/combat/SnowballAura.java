package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SnowballAura extends Module {
    private final NumberValue range = new NumberValue("Range", 8, 3, 16, 0.5);
    private final NumberValue throwRange = new NumberValue("Throw Range", 6, 3, 12, 0.5);
    private final NumberValue delay = new NumberValue("Delay", 0, 0, 20, 1);
    private final BoolValue ignoreTeammates = new BoolValue("Ignore Teammates", true);
    private final BoolValue targetPlayers = new BoolValue("Players", true);
    private final BoolValue targetMonsters = new BoolValue("Monsters", true);
    private final BoolValue autoSwitch = new BoolValue("Auto Switch", true);
    private final BoolValue predictiveAiming = new BoolValue("Predictive", true);

    private int tickCounter = 0;
    private int lastSlot = -1;

    public SnowballAura() {
        super("SnowballAura", Category.Combat);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        lastSlot = -1;
    }

    @Override
    public void onDisable() {
        if (autoSwitch.getValue() && lastSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(lastSlot);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (tickCounter < delay.getValue().intValue()) {
            tickCounter++;
            return;
        }
        tickCounter = 0;

        Entity target = findTarget();
        if (target == null) return;
        if (mc.player.distanceTo(target) > throwRange.getValue()) return;

        boolean offhand = mc.player.getOffHandStack().isOf(Items.SNOWBALL);
        boolean mainhand = mc.player.getMainHandStack().isOf(Items.SNOWBALL);
        if (!offhand && !mainhand) {
            int slot = findSnowballSlot();
            if (slot == -1) return;
            if (autoSwitch.getValue()) {
                if (lastSlot == -1) lastSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(slot);
            } else {
                return;
            }
        }

        throwSnowball(target, offhand);
    }

    private Entity findTarget() {
        double r = range.getValue();
        Box box = new Box(mc.player.getX() - r, mc.player.getY() - r, mc.player.getZ() - r,
                mc.player.getX() + r, mc.player.getY() + r, mc.player.getZ() + r);

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.world.getOtherEntities(mc.player, box)) {
            if (!isValidTarget(entity)) continue;
            double dist = mc.player.squaredDistanceTo(entity);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    private boolean isValidTarget(Entity target) {
        if (target == mc.player || !target.isAlive()) return false;
        if (mc.player.distanceTo(target) > range.getValue()) return false;

        if (target instanceof PlayerEntity player) {
            if (!targetPlayers.getValue()) return false;
            if (ignoreTeammates.getValue() && isTeammate(player)) return false;
            return true;
        }
        return target instanceof Monster && targetMonsters.getValue();
    }

    private boolean isTeammate(PlayerEntity target) {
        return mc.player.getScoreboardTeam() != null && target.getScoreboardTeam() != null
                && mc.player.getScoreboardTeam().getName().equals(target.getScoreboardTeam().getName());
    }

    private int findSnowballSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.SNOWBALL)) return i;
        }
        return -1;
    }

    private void throwSnowball(Entity target, boolean offhand) {
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        aimAt(target);
        mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
        mc.interactionManager.interactItem(mc.player, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void aimAt(Entity target) {
        Vec3d targetPos = predictiveAiming.getValue() ? predictedPos(target) : target.getEyePos();
        Vec3d playerPos = mc.player.getEyePos();

        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;

        double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double pitch = Math.atan2(dy, distance) * 180.0 / Math.PI;

        mc.player.setYaw((float) yaw);
        mc.player.setPitch((float) -pitch);
    }

    private Vec3d predictedPos(Entity target) {
        Vec3d current = target.getEyePos();
        Vec3d velocity = target.getVelocity();
        double time = mc.player.distanceTo(target) / 1.5;
        return new Vec3d(current.x + velocity.x * time, current.y + velocity.y * time, current.z + velocity.z * time);
    }
}
