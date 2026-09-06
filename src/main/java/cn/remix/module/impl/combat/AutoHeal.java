package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class AutoHeal extends Module {
    private final BoolValue speedCheck = new BoolValue("Speed Check", true);
    private final BoolValue regenCheck = new BoolValue("Regen Check", true);
    private final NumberValue delay = new NumberValue("Delay", 500, 300, 1000, 10);
    private final NumberValue health = new NumberValue("Health Percent", 0.5f, 0.1f, 1.0f, 0.05f);
    private final ModeValue mode = new ModeValue("Mode", "Soup", "Soup", "Head");

    private final TimerUtil timer = new TimerUtil();
    private int originalSlot = -1;

    public AutoHeal() {
        super("AutoHeal", Category.Combat);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (mc.player.hasStatusEffect(StatusEffects.SPEED) && speedCheck.getValue()) return;
        if (mc.player.hasStatusEffect(StatusEffects.REGENERATION) && regenCheck.getValue()) return;

        if (mc.player.getHealth() / mc.player.getMaxHealth() >= health.getValue()) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;

        int targetSlot = -1;
        boolean dropBowl = false;

        if (mode.is("Soup")) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.MUSHROOM_STEW)) {
                    targetSlot = i;
                    dropBowl = true;
                    break;
                }
            }
        } else if (mode.is("Head")) {
            for (int i = 0; i < 9; i++) {
                var stack = mc.player.getInventory().getStack(i);
                if (stack.isOf(Items.PLAYER_HEAD) && stack.getName().getString().toLowerCase().contains("golden head")) {
                    targetSlot = i;
                    break;
                }
            }
        }

        if (targetSlot == -1) return;

        originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(targetSlot);
        PacketUtil.sendPacketNoEvent(new UpdateSelectedSlotC2SPacket(targetSlot));

        PacketUtil.sendSequencedPacket(seq -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, seq, mc.player.getYaw(), mc.player.getPitch()));

        if (dropBowl) {
            PacketUtil.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        }

        // switch back next tick
        if (originalSlot >= 0 && originalSlot <= 8) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            PacketUtil.sendPacketNoEvent(new UpdateSelectedSlotC2SPacket(originalSlot));
        }

        timer.reset();
    }
}
