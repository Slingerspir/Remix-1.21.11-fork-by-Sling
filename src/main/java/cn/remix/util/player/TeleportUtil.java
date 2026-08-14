package cn.remix.util.player;

import cn.remix.util.IMinecraft;
import cn.remix.util.network.PacketUtil;
import lombok.experimental.UtilityClass;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class TeleportUtil implements IMinecraft {

    public void sendMovePacket(double x, double y, double z, boolean onGround) {
        if (mc.player == null) return;
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }

    public void sendMovePacket(Vec3d vec, boolean onGround) {
        sendMovePacket(vec.x, vec.y, vec.z, onGround);
    }

    public void teleportDirect(double x, double y, double z) {
        if (mc.player == null) return;
        mc.player.setPosition(x, y, z);
        sendMovePacket(x, y, z, true);
    }

    public void teleportJump() {
        if (mc.player == null) return;
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        sendMovePacket(x, y + 0.4199999868869781, z, false);
        sendMovePacket(x, y + 0.7531999805212017, z, false);
        teleportDirect(x, y + 1.0, z);
    }

    public void teleportGlitch() {
        if (mc.player == null) return;
        double x = mc.player.getX();
        double y = Math.round(mc.player.getY());
        double z = mc.player.getZ();
        boolean onGround = mc.player.isOnGround();

        sendMovePacket(x, y, z, onGround);
        y -= 2.0 / 400.0;
        mc.player.setPosition(x, y, z);
        sendMovePacket(x, y, z, onGround);
        y -= (2.0 / 400.0) * 300.0;
        mc.player.setPosition(x, y, z);
        sendMovePacket(x, y, z, onGround);
    }

    public void doTp(Vec3d from, Vec3d to, double moveDistance, boolean onGround) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance / Math.max(0.1, moveDistance)));
        for (int i = 1; i < steps; i++) {
            sendMovePacket(from, onGround);
        }
        sendMovePacket(to, onGround);
    }

    public List<Vec3d> calculatePath(PlayerEntity player, Vec3d target, double stepSize) {
        List<Vec3d> path = new ArrayList<>();
        Vec3d from = new Vec3d(player.getX(), player.getY(), player.getZ());
        double distance = from.distanceTo(target);
        double step = Math.max(0.1, stepSize);

        if (distance <= step) {
            path.add(target);
            return path;
        }

        for (double progress = step; progress < distance; progress += step) {
            path.add(from.lerp(target, progress / distance));
        }
        path.add(target);
        return path;
    }

    public int teleportPath(PlayerEntity player, Vec3d target, double stepSize, int maxPackets, boolean clientTeleport) {
        int packets = 0;
        for (Vec3d pos : calculatePath(player, target, stepSize)) {
            sendMovePacket(pos, player.isOnGround());
            if (++packets >= maxPackets) {
                return packets;
            }
        }

        if (clientTeleport) {
            player.setPosition(target.x, target.y, target.z);
        }
        return packets;
    }

    public BlockHitResult raycastBlock(PlayerEntity player, double maxDistance) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(maxDistance));
        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }

    public boolean isSafePosition(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getBlockState(pos.up()).isReplaceable()
                && mc.world.getFluidState(pos).isEmpty()
                && mc.world.getFluidState(pos.up()).isEmpty()
                && !mc.world.getBlockState(pos).isOf(Blocks.POWDER_SNOW);
    }

    public Vec3d findVClipVecToMove(Vec3d from, double searchStep, boolean allowVoid) {
        if (mc.world == null) return null;
        int bottomY = mc.world.getBottomY();
        double step = 0.0;

        while (step <= 200.0) {
            Vec3d up = from.add(0.0, step, 0.0);
            if ((allowVoid || up.y > bottomY) && isSafePosition(BlockPos.ofFloored(up))) {
                return up;
            }

            Vec3d down = from.add(0.0, -step, 0.0);
            if ((allowVoid || down.y > bottomY) && isSafePosition(BlockPos.ofFloored(down))) {
                return down;
            }
            step += Math.max(0.1, searchStep);
        }
        return null;
    }

    public void playTeleportSound() {
        if (mc.player == null || mc.world == null) return;
        mc.world.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }
}
