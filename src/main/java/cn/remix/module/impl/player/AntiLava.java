package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.BlockUtil;
import cn.remix.util.player.ItemSpoofUtil;
import cn.remix.util.player.RotationUtil;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@Getter
public class AntiLava extends Module {
    private final NumberValue range = new NumberValue("Range", 3, 3, 6, .5f);
    private final BoolValue itemSpoof = new BoolValue("Item Spoof", false);
    private final BoolValue noSwing = new BoolValue("No Swing", false);
    private final BoolValue movementFix = new BoolValue("Movement Fix", false);
    private BlockPos target = null;
    private float[] rotations = null;

    public AntiLava() {
        super("AntiLava", Category.Player);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
            reset();
            return;
        }

        target = getLava();
        if (target == null) {
            rotations = null;
            return;
        }

        int blockSlot = BlockUtil.getBlockSlot(false);
        if (blockSlot == -1) {
            reset();
            return;
        }

        BlockHitResult hitResult = getHitResult(target);
        if (hitResult == null) {
            reset();
            return;
        }

        place(hitResult, blockSlot);
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null || target == null) return;

        Vec3d targetVec = new Vec3d(this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);
        rotations = RotationUtil.getRotations(targetVec);
    }

    private void reset() {
        if (itemSpoof.getValue()) {
            ItemSpoofUtil.stopSpoof();
        }
        target = null;
        rotations = null;
    }

    private BlockPos getLava() {
        if (mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = range.getValue().intValue();
        BlockPos closestPos = null;
        double minDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos currentPos = playerPos.add(x, y, z);
                    FluidState fluidState = mc.world.getFluidState(currentPos);

                    if (fluidState.getFluid() == Fluids.LAVA && fluidState.isStill()) {
                        double distance = mc.player.squaredDistanceTo(new Vec3d(currentPos.getX() + 0.5, currentPos.getY() + 0.5, currentPos.getZ() + 0.5));
                        if (distance <= range.getValue() * range.getValue() && distance < minDistance) {
                            minDistance = distance;
                            closestPos = currentPos;
                        }
                    }
                }
            }
        }
        return closestPos;
    }

    public void place(BlockHitResult hitResult, int slot) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        if (itemSpoof.getValue()) {
            ItemSpoofUtil.startSpoof(oldSlot);
        }

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult).isAccepted()) {
            if (noSwing.getValue()) {
                PacketUtil.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }

        mc.player.getInventory().setSelectedSlot(oldSlot);
        if (itemSpoof.getValue()) {
            ItemSpoofUtil.stopSpoof();
        }
    }

    public BlockHitResult getHitResult(BlockPos targetPos) {
        if (mc.player == null || mc.world == null) return null;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = targetPos.offset(direction);
            BlockState neighborState = mc.world.getBlockState(neighborPos);
            if (!neighborState.isAir() && neighborState.getFluidState().isEmpty() && !neighborState.isReplaceable()) {
                Direction oppositeDirection = direction.getOpposite();
                Vec3d hitVec = new Vec3d(
                        neighborPos.getX() + 0.5 + oppositeDirection.getOffsetX() * 0.5,
                        neighborPos.getY() + 0.5 + oppositeDirection.getOffsetY() * 0.5,
                        neighborPos.getZ() + 0.5 + oppositeDirection.getOffsetZ() * 0.5
                );
                return new BlockHitResult(hitVec, oppositeDirection, neighborPos, false);
            }
        }
        return null;
    }
}