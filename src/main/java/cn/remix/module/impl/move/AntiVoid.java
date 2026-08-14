package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.BlockShapeEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

public final class AntiVoid extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Ghost Block", "Blink", "Flag", "Ghost Block");
    private final NumberValue voidLevel = new NumberValue("Void Level", 0, -256, 0, 1);
    private final NumberValue triggerFallDistance = new NumberValue("Fall Distance", 0.5, 0.0, 6.0, 0.1);
    private final NumberValue flagHeight = new NumberValue("Flag Height", 0.42f, 0.01f, 10.0f, 0.01f, () -> mode.is("Flag"));
    private final BoolValue silent = new BoolValue("Silent", false, () -> mode.is("Flag"));

    private Vec3d rescuePosition;
    private boolean likelyFalling;
    private boolean blinking;
    private boolean silentFlag;
    private boolean checkingVoid;

    public AntiVoid() {
        super("AntiVoid", Category.Move);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        stopBlink(true);
        reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        setSuffix(mode.getValue());

        if (isExempt()) {
            likelyFalling = false;
            rescuePosition = position();
            stopBlink(true);
            return;
        }

        boolean overVoid = isOverVoid(mc.player.getBlockPos());
        likelyFalling = overVoid && !mc.player.isOnGround() && mc.player.getVelocity().y < 0.0;

        if (!overVoid) {
            rescuePosition = position();
            stopBlink(true);
            return;
        }

        if (!likelyFalling || rescuePosition == null) return;

        if (mode.is("Blink")) {
            if (!blinking) {
                instance.getPacketManager().getBlink().start(this);
                blinking = true;
            }
            if (mc.player.fallDistance >= triggerFallDistance.getValue()) {
                mc.player.setPosition(rescuePosition.x, rescuePosition.y, rescuePosition.z);
                mc.player.setVelocity(0.0, 0.0, 0.0);
                mc.player.fallDistance = 0.0f;
                discardBlink();
            }
        } else {
            stopBlink(true);
            if (mode.is("Flag") && mc.player.fallDistance >= triggerFallDistance.getValue()) {
                if (silent.getValue()) {
                    silentFlag = true;
                } else {
                    mc.player.setPosition(mc.player.getX(), mc.player.getY() + flagHeight.getValue(), mc.player.getZ());
                }
                mc.player.fallDistance = 0.0f;
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.isPost() || !silentFlag || !mode.is("Flag")) return;
        event.setY(event.getY() + flagHeight.getValue());
        silentFlag = false;
    }

    @EventTarget
    public void onBlockShape(BlockShapeEvent event) {
        if (checkingVoid || !mode.is("Ghost Block") || !likelyFalling || rescuePosition == null || !event.getShape().isEmpty()) return;
        if (event.getPos().getY() < Math.floor(rescuePosition.y)) {
            event.setShape(VoxelShapes.fullCube());
        }
    }

    private boolean isOverVoid(BlockPos origin) {
        int bottom = Math.max(mc.world.getBottomY(), voidLevel.getValue().intValue());
        BlockPos.Mutable pos = origin.mutableCopy();
        checkingVoid = true;
        try {
            for (int y = origin.getY(); y >= bottom; y--) {
                pos.setY(y);
                if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                    return false;
                }
            }
            return true;
        } finally {
            checkingVoid = false;
        }
    }

    private boolean isExempt() {
        Fly fly = getModule(Fly.class);
        return mc.player.isDead() || mc.player.getAbilities().creativeMode || mc.player.getAbilities().flying
                || mc.player.isGliding() || (fly != null && fly.isEnabled());
    }

    private void stopBlink(boolean releasePackets) {
        if (blinking) {
            instance.getPacketManager().getBlink().dispatch(this, releasePackets);
            blinking = false;
        }
    }

    private void discardBlink() {
        if (!blinking) return;
        instance.getPacketManager().getBlink().packets.clear();
        instance.getPacketManager().getBlink().dispatch(this, false);
        blinking = false;
    }

    private Vec3d position() {
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void reset() {
        rescuePosition = mc.player == null ? null : position();
        likelyFalling = false;
        blinking = false;
        silentFlag = false;
        checkingVoid = false;
    }
}
