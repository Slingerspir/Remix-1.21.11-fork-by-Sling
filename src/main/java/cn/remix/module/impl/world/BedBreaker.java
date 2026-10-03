package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.management.RotationManager;
import cn.remix.management.movement.MovementCorrection;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.RotationUtil;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * BedBreaker —— 自动直挖床。
 *
 * <p>直接对着床发破坏包，不管它被多少层防御包着。能这么干的原因是官方源码里
 * {@code ClientPlayerInteractionManager.attackBlock()} <b>只检查冒险模式限制和世界边界</b>，
 * 不看距离也不看视线；距离是服务端用 {@code getBlockInteractionRange()} 单独校验的，
 * 所以只要在交互距离内，隔着羊毛/末地石照样能把床挖掉，整个"先破防"的流程都省了。</p>
 *
 * <p>另一个坑：原版 {@code MinecraftClient.handleBlockBreaking()} 在攻击键没按住时会调
 * {@code cancelBlockBreaking()} 清掉进度，按住时又会按准星把目标切走 —— 两种情况都会让
 * 我们手动累积的进度归零。所以这里用一个静态标志 {@link #isHolding()}，由 Mixin 在
 * 模块接管挖掘期间整个跳过原版的那段逻辑。</p>
 */
public final class BedBreaker extends Module {
    private final NumberValue range = new NumberValue("Range", 4.5f, 1.0f, 6.0f, 0.1f);
    private final BoolValue throughWalls = new BoolValue("Through Walls", true);
    private final BoolValue rotate = new BoolValue("Rotate", true);
    private final NumberValue rotateSpeed = new NumberValue("Rotate Speed", 180, 30, 360, 10, rotate::getValue);
    private final BoolValue autoTool = new BoolValue("Auto Tool", true);
    private final BoolValue swing = new BoolValue("Swing", true);

    /**
     * 接管挖掘的"租约"：模块每 tick 续期，一旦停止更新（关模块、切世界、异常）就自动失效。
     * 不用单纯的布尔量，是为了防止标志卡住导致原版挖掘被永久屏蔽 —— 那样玩家就再也挖不动方块了。
     */
    private static volatile long holdingUntil;

    private BlockPos target;
    private int scanCooldown;
    private int oldSlot = -1;
    private boolean slotSwitched;

    public BedBreaker() {
        super("BedBreaker", Category.World);
    }

    public static boolean isHolding() {
        return holdingUntil > System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        release();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            release();
            return;
        }

        // 目标没了、或被挖掉了就重扫；否则也定期重扫，别一直抱着一个够不到的位置
        if (target == null || !isBed(target) || --scanCooldown <= 0) {
            findTarget();
            scanCooldown = 6;
        }
        if (target == null) {
            release();
            return;
        }

        double maxRange = maxRange();
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(target)) > maxRange * maxRange) {
            release();
            return;
        }
        Vec3d center = Vec3d.ofCenter(target);
        if (!throughWalls.getValue() && !hasLineOfSight(center)) {
            release();
            return;
        }

        BlockState state = mc.world.getBlockState(target);

        if (autoTool.getValue()) switchTool(state);
        if (rotate.getValue()) {
            RotationManager.setRotations(RotationUtil.getRotations(target), rotateSpeed.getValue(),
                    MovementCorrection.Silent);
        }

        // 接管挖掘：原版那段 handleBlockBreaking 会被 Mixin 跳过，进度由我们一次次累积
        holdingUntil = System.currentTimeMillis() + 250L;   // 租约续期
        mc.interactionManager.updateBlockBreakingProgress(target, sideOf(center));
        if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void release() {
        if (isHolding() && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        holdingUntil = 0L;
        if (slotSwitched && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            slotSwitched = false;
        }
        target = null;
    }

    // =====================================================================
    // 找床
    // =====================================================================

    private void findTarget() {
        target = null;
        if (mc.player == null || mc.world == null) return;

        int r = (int) Math.ceil(maxRange());
        BlockPos origin = mc.player.getBlockPos();
        Vec3d eye = mc.player.getEyePos();
        double limit = maxRange() * maxRange();

        // 床是双格，挖任意一半整张床都会掉。优先选靠下的 FOOT，避免在两半之间来回换目标；
        // 但如果玩家正好站在 HEAD 那侧、FOOT 已经超出交互距离，就退而求其次瞄准 HEAD。
        BlockPos bestFoot = null;
        double footDist = Double.MAX_VALUE;
        BlockPos bestHead = null;
        double headDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(origin.add(-r, -r, -r), origin.add(r, r, r))) {
            BlockState state = mc.world.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;

            double dist = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (dist > limit) continue;

            if (state.get(BedBlock.PART) == BedPart.FOOT) {
                if (dist < footDist) {
                    footDist = dist;
                    bestFoot = pos.toImmutable();
                }
            } else if (dist < headDist) {
                headDist = dist;
                bestHead = pos.toImmutable();
            }
        }
        target = bestFoot != null ? bestFoot : bestHead;
    }

    private boolean isBed(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).getBlock() instanceof BedBlock;
    }

    /** 服务端只认 getBlockInteractionRange() 以内的破坏包，超了纯属白挖。 */
    private double maxRange() {
        double configured = range.getValue();
        if (mc.player == null) return configured;
        try {
            return Math.min(configured, mc.player.getBlockInteractionRange());
        } catch (Throwable ignored) {
            return configured;
        }
    }

    private boolean hasLineOfSight(Vec3d to) {
        try {
            RaycastContext ctx = new RaycastContext(mc.player.getEyePos(), to,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            return mc.world.raycast(ctx).getType() == HitResult.Type.MISS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =====================================================================
    // 工具 / 破坏面
    // =====================================================================

    /** 换成对该方块挖掘速度最快的快捷栏槽位（和 AutoTool 同一套判定）。 */
    private void switchTool(BlockState state) {
        if (mc.player == null) return;

        float bestSpeed = 1.0F;
        int bestSlot = -1;
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot == -1 || mc.player.getInventory().getSelectedSlot() == bestSlot) return;

        if (!slotSwitched) {
            oldSlot = mc.player.getInventory().getSelectedSlot();
            slotSwitched = true;
        }
        mc.player.getInventory().setSelectedSlot(bestSlot);
    }

    /** 破坏包里那个方向：取玩家眼睛指向方块中心的主轴面，服务端按这个方向验证。 */
    private Direction sideOf(Vec3d center) {
        Vec3d d = mc.player.getEyePos().subtract(center);
        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
        if (ax >= ay && ax >= az) return d.x > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return d.y > 0 ? Direction.UP : Direction.DOWN;
        return d.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
