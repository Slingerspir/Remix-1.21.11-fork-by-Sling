package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class LegitNoFall extends Module {
    private final NumberValue checkDown = new NumberValue("Check Down", 1, 0, 3, 1);
    private final BoolValue inventorySwap = new BoolValue("Inventory Swap", true);
    private final NumberValue offset = new NumberValue("Offset", 0.3, 0.0, 1.0, 0.05);

    private boolean hasPlacedWater;
    private BlockPos lastPos;
    private float rotationYaw;
    private float rotationPitch;
    private int lastSlot = -1;
    private int lastSelect = -1;

    public LegitNoFall() {
        super("LegitNoFall", Category.Move);
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
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.world.getRegistryKey() == World.NETHER) return;

        rotationYaw = mc.player.getYaw();
        rotationPitch = mc.player.getPitch();

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int waterSlot = hasPlacedWater ? findItem(Items.BUCKET) : findItem(Items.WATER_BUCKET);
        if (waterSlot == -1) return;

        if (hasPlacedWater && lastPos != null) {
            doSwap(waterSlot);
            Vec3d targetAim = new Vec3d(lastPos.getX() + 0.5, lastPos.getY(), lastPos.getZ() + 0.5);
            useBucket(targetAim);
            restoreSlot(waterSlot, oldSlot);
            hasPlacedWater = false;
            lastPos = null;
            return;
        }

        if (!checkFalling()) return;

        BlockPos basePos = mc.player.getBlockPos().down(checkDown.getValue().intValue());
        double offsetValue = offset.getValue();
        double[] offsets = new double[]{offsetValue, -offsetValue};

        for (double x : offsets) {
            for (double z : offsets) {
                BlockPos groundPos = new BlockPos(
                        MathHelper.floor(mc.player.getX() + x),
                        basePos.getY(),
                        MathHelper.floor(mc.player.getZ() + z)
                );

                if (mc.world.isAir(groundPos) || mc.world.getBlockState(groundPos).isReplaceable()) continue;
                if (getPlaceSide(groundPos.up()) == null || behindWall(groundPos.up())) continue;

                doSwap(waterSlot);
                Vec3d targetAim = new Vec3d(groundPos.getX() + 0.5, groundPos.getY() + 1.0, groundPos.getZ() + 0.5);
                useBucket(targetAim);
                lastPos = groundPos.up();
                restoreSlot(waterSlot, oldSlot);
                hasPlacedWater = true;
                return;
            }
        }
    }

    private void useBucket(Vec3d targetAim) {
        snapAt(targetAim);
        mc.player.swingHand(Hand.MAIN_HAND);
        float[] rotation = getRotation(targetAim);
        PacketUtil.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 1, rotation[0], rotation[1]));
        snapBack();
    }

    private boolean behindWall(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d testVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5);
        HitResult result = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                testVec,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return result != null && result.getType() != HitResult.Type.MISS;
    }

    private Direction getPlaceSide(BlockPos pos) {
        if (mc.world == null) return null;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!mc.world.getBlockState(neighbor).isAir() && !mc.world.getBlockState(neighbor).isReplaceable()) {
                return direction;
            }
        }
        return null;
    }

    private boolean checkFalling() {
        return mc.player.fallDistance > mc.player.getSafeFallDistance()
                && !mc.player.isOnGround()
                && !mc.player.isGliding();
    }

    private int findItem(Item item) {
        if (mc.player == null) return -1;

        int maxSlot = inventorySwap.getValue() ? 36 : 9;
        for (int i = 0; i < maxSlot; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return inventorySwap.getValue() && i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    private void doSwap(int slot) {
        if (inventorySwap.getValue()) {
            inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            switchToSlot(slot);
        }
    }

    private void restoreSlot(int usedSlot, int oldSlot) {
        if (inventorySwap.getValue()) {
            doSwap(usedSlot);
        } else {
            switchToSlot(oldSlot);
        }
    }

    private void switchToSlot(int slot) {
        if (slot < 0 || slot > 8) return;

        mc.player.getInventory().setSelectedSlot(slot);
        PacketUtil.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private void inventorySwap(int slot, int selectedSlot) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect);
            lastSlot = -1;
            lastSelect = -1;
            return;
        }
        if (slot - 36 == selectedSlot) return;

        if (slot >= 36) {
            lastSlot = slot;
            lastSelect = selectedSlot;
            switchToSlot(slot - 36);
            return;
        }

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, selectedSlot, SlotActionType.SWAP, mc.player);
    }

    private void snapAt(Vec3d vec) {
        float[] angle = getRotation(vec);
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                angle[0],
                angle[1],
                mc.player.isOnGround(),
                mc.player.horizontalCollision
        ));
    }

    private void snapBack() {
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                rotationYaw,
                rotationPitch,
                mc.player.isOnGround(),
                mc.player.horizontalCollision
        ));
    }

    private float[] getRotation(Vec3d vec) {
        Vec3d eyesPos = mc.player.getEyePos();
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
    }

    private void reset() {
        hasPlacedWater = false;
        lastPos = null;
        lastSlot = -1;
        lastSelect = -1;
    }
}
