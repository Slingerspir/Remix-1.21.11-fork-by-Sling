package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.player.EntityUtil;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class AttackCrystal extends Module {
    private final BoolValue attack = new BoolValue("Attack Crystals", true);
    private final BoolValue autoPlace = new BoolValue("Auto Place", true);
    private final NumberValue range = new NumberValue("Range", 4.5f, 2, 6, 0.5f);
    private final NumberValue placeRange = new NumberValue("Place Range", 4.5f, 2, 6, 0.5f);
    private final BoolValue autoSwitch = new BoolValue("Auto Switch", true);

    private final TimerUtil timer = new TimerUtil();

    public AttackCrystal() {
        super("CrystalAura", Category.Combat);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!timer.hasTimeElapsed(150)) return;
        timer.reset();

        if (attack.getValue()) {
            EndCrystalEntity crystal = null;
            double best = Double.MAX_VALUE;
            for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    mc.player.getBoundingBox().expand(range.getValue(), range.getValue(), range.getValue()), e -> true)) {
                if (!c.isAlive()) continue;
                double d = mc.player.squaredDistanceTo(c);
                if (d < best) {
                    best = d;
                    crystal = c;
                }
            }
            if (crystal != null) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }

        if (autoPlace.getValue()) {
            autoPlace();
        }
    }

    private void autoPlace() {
        PlayerEntity target = null;
        double best = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (!EntityUtil.isSelected(p)) continue;
            double d = mc.player.squaredDistanceTo(p);
            if (d < best && d <= placeRange.getValue() * placeRange.getValue()) {
                best = d;
                target = p;
            }
        }
        if (target == null) return;

        int crystalSlot = findCrystalSlot();
        if (crystalSlot == -1) return;

        BlockPos bestBase = null;
        double bestScore = Double.MAX_VALUE;
        int r = (int) Math.ceil(placeRange.getValue());
        BlockPos targetPos = target.getBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos base = targetPos.add(dx, dy, dz);
                    if (!mc.world.getBlockState(base.up()).isAir() || !mc.world.getBlockState(base.up(2)).isAir()) continue;
                    double d = mc.player.squaredDistanceTo(Vec3d.ofCenter(base));
                    if (d <= placeRange.getValue() * placeRange.getValue() && d < bestScore) {
                        bestScore = d;
                        bestBase = base;
                    }
                }
            }
        }
        if (bestBase == null) return;

        if (mc.player.getInventory().getSelectedSlot() != crystalSlot) {
            if (autoSwitch.getValue()) {
                mc.player.getInventory().setSelectedSlot(crystalSlot);
            } else {
                return;
            }
        }

        Vec3d hit = Vec3d.ofCenter(bestBase).add(0, 0.5, 0);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, bestBase, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }
}
