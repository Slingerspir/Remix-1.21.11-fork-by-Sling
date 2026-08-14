package cn.remix.module.impl.move;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import cn.remix.util.player.TeleportUtil;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class Clipper extends Module {
    private static final long CHECK_INTERVAL_MS = 100L;

    private final NumberValue clipValue = new NumberValue("Clip Value", 160, 0, 256, 1);
    private final NumberValue protectY = new NumberValue("Protect Y", -63, -70, 256, 1);
    private final BoolValue voidProtect = new BoolValue("Void Protect", false);
    private final BoolValue sendFeedback = new BoolValue("Send Feedback", false);
    private final NumberValue moveDistance = new NumberValue("Move Distance", 10.0, 1.0, 50.0, 0.5);
    private final BoolValue noFlying = new BoolValue("No Flying", true);
    private final BoolValue noScaffolding = new BoolValue("No Scaffolding", true);

    private long lastCheck;
    private ClipTarget cachedTarget;

    public Clipper() {
        super("Clipper", Category.Move);
    }

    @Override
    public void onEnable() {
        refreshTarget(true);

        if (cachedTarget != null && mc.player != null) {
            Vec3d from = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d to = cachedTarget.position();
            TeleportUtil.doTp(from, to, moveDistance.getValue(), true);
            mc.player.setPosition(to);

            if (sendFeedback.getValue()) {
                Util.log(String.format("Clipped %.1fm.", to.y - from.y));
            }
        } else if (sendFeedback.getValue()) {
            Util.log("No clip platform found.");
        }

        setEnabled(false);
    }

    public ClipTarget getTarget() {
        refreshTarget(false);
        return cachedTarget;
    }

    public boolean isAvailable() {
        return getTarget() != null;
    }

    public void refreshTarget(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastCheck < CHECK_INTERVAL_MS) return;

        lastCheck = now;
        cachedTarget = findTarget();
    }

    private ClipTarget findTarget() {
        if (mc.player == null || mc.world == null) return null;
        if (noFlying.getValue() && mc.player.getAbilities().flying) return null;

        Scaffold scaffold = getModule(Scaffold.class);
        if (noScaffolding.getValue() && scaffold != null && scaffold.isEnabled()) return null;

        boolean down = mc.player.isSneaking();
        int feetY = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ()).getY();
        int max = Math.max(0, clipValue.getValue().intValue());

        for (int offset = 1; offset <= max; offset++) {
            int targetFeetY = down ? feetY - offset : feetY + offset;
            if (targetFeetY <= protectY.getValue()) continue;
            if (voidProtect.getValue() && targetFeetY <= mc.world.getBottomY()) continue;

            BlockPos feet = BlockPos.ofFloored(mc.player.getX(), targetFeetY, mc.player.getZ());
            BlockPos platform = feet.down();
            if (!isStandable(feet, platform)) continue;

            Vec3d targetPos = new Vec3d(mc.player.getX(), targetFeetY, mc.player.getZ());
            return new ClipTarget(targetPos, platform, Math.abs(targetPos.y - mc.player.getY()), down);
        }

        return null;
    }

    private boolean isStandable(BlockPos feet, BlockPos platform) {
        BlockState platformState = mc.world.getBlockState(platform);
        return isOpen(feet)
                && isOpen(feet.up())
                && !platformState.isAir()
                && !platformState.isReplaceable()
                && platformState.getFluidState().isEmpty();
    }

    private boolean isOpen(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isReplaceable() && state.getFluidState().isEmpty();
    }

    public record ClipTarget(Vec3d position, BlockPos platform, double distance, boolean down) {}
}
