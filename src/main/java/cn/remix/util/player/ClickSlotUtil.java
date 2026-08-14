package cn.remix.util.player;

import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.screen.slot.SlotActionType;

@UtilityClass
public class ClickSlotUtil implements IMinecraft {

    public void clickSlot(int slot, int button, SlotActionType action) {
        if (mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, action, mc.player);
    }

    public void click(int slot) {
        clickSlot(slot, 0, SlotActionType.PICKUP);
    }

    public void shiftClick(int slot) {
        clickSlot(slot, 0, SlotActionType.QUICK_MOVE);
    }

    public void drop(int slot) {
        clickSlot(slot, 0, SlotActionType.THROW);
    }

    public void dropAll(int slot) {
        clickSlot(slot, 1, SlotActionType.THROW);
    }

    public void swap(int slot, int hotbarSlot) {
        clickSlot(slot, hotbarSlot, SlotActionType.SWAP);
    }
}