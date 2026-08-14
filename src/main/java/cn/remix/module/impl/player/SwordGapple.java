package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.util.player.ClickSlotUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public final class SwordGapple extends Module {
    private final BoolValue enchantedGapples = new BoolValue("Enchanted Gapples", true);
    private final BoolValue normalGapples = new BoolValue("Normal Gapples", true);

    private boolean eating;
    private int foodSlot = -1;

    public SwordGapple() {
        super("SwordGapple", Category.Player);
    }

    @Override
    public void onDisable() {
        restoreOffhand();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            restoreOffhand();
            return;
        }

        boolean holdingSword = mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
        boolean pressingUse = mc.options.useKey.isPressed();

        if (holdingSword && pressingUse) {
            if (!eating) {
                startEating();
            }
        } else if (eating) {
            restoreOffhand();
        }
    }

    private void startEating() {
        if (isAllowedGapple(mc.player.getOffHandStack())) {
            eating = true;
            return;
        }

        int slot = findGappleSlot();
        if (slot == -1) return;

        foodSlot = slot;
        ClickSlotUtil.swap(slot, 40);
        eating = true;
    }

    private void restoreOffhand() {
        if (mc.player != null && eating && foodSlot != -1) {
            ClickSlotUtil.swap(foodSlot, 40);
        }
        eating = false;
        foodSlot = -1;
    }

    private int findGappleSlot() {
        if (mc.player == null) return -1;

        for (int i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
            if (mc.player.currentScreenHandler.getSlot(i).inventory != mc.player.getInventory()) continue;
            if (isAllowedGapple(mc.player.currentScreenHandler.getSlot(i).getStack())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isAllowedGapple(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return (enchantedGapples.getValue() && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE))
                || (normalGapples.getValue() && stack.isOf(Items.GOLDEN_APPLE));
    }
}
