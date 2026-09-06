package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.player.ItemUtil;
import lombok.Getter;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

@Getter
public final class SwordPearl extends Module {
    private final NumberValue windChargeDelay = new NumberValue("Wind Charge Delay", 100, 0, 500, 10);
    private final NumberValue switchBackDelay = new NumberValue("Switch Back Delay", 100, 0, 1000, 10);
    private final NumberValue retriggerDelay = new NumberValue("Retrigger Delay", 800, 0, 2000, 50);
    private final ModeValue switchTo = new ModeValue("Switch To", "Mace", "Mace", "Sword");
    private final NumberValue lookUpAngle = new NumberValue("Look Up Angle", 60, 30, 89, 1);

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil retriggerTimer = new TimerUtil();
    private boolean pending;
    private boolean active;
    private int state;
    private long waitMs;

    public SwordPearl() {
        super("SwordPearl", Category.Combat);
    }

    @Override
    public void onDisable() {
        resetSequence();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Send) return;

        if (event.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
            if (packet.getHand() != Hand.MAIN_HAND) return;
            if (!mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) return;
            if (mc.player.getPitch() > -lookUpAngle.getValue()) return;

            event.setCancelled();

            if (pending || active) return;
            if (!retriggerTimer.hasTimeElapsed(retriggerDelay.getValue().longValue())) return;

            pending = true;
            retriggerTimer.reset();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            resetSequence();
            return;
        }

        if (pending) {
            pending = false;
            if (!startSequence()) {
                resetSequence();
            }
            return;
        }

        if (!active) return;

        switch (state) {
            case 1 -> {
                if (timer.hasTimeElapsed(waitMs)) {
                    throwWindCharge();
                    state = 2;
                    waitMs = jitter(switchBackDelay.getValue().longValue());
                    timer.reset();
                }
            }
            case 2 -> {
                if (timer.hasTimeElapsed(waitMs)) {
                    switchBack();
                    resetSequence();
                }
            }
        }
    }

    private boolean startSequence() {
        active = true;
        state = 1;
        waitMs = jitter(windChargeDelay.getValue().longValue());
        timer.reset();

        if (ItemUtil.findItemSlot(Items.ENDER_PEARL) == -1) {
            return false;
        }
        throwPearl();
        return true;
    }

    private long jitter(long base) {
        return base + (long) (Math.random() * 30);
    }

    private void throwPearl() {
        int slot = ItemUtil.findItemSlot(Items.ENDER_PEARL);
        if (slot == -1) return;
        ItemUtil.switchToSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void throwWindCharge() {
        int slot = ItemUtil.findItemSlot(Items.WIND_CHARGE);
        if (slot == -1) return;
        ItemUtil.switchToSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void switchBack() {
        if (switchTo.is("Mace")) {
            int slot = ItemUtil.getBestMaceSlot(-1);
            if (slot != -1) ItemUtil.switchToSlot(slot);
        } else {
            int slot = ItemUtil.getBestWeaponSlot();
            if (slot != -1) ItemUtil.switchToSlot(slot);
        }
    }

    private void resetSequence() {
        pending = false;
        active = false;
        state = 0;
    }
}
