package cn.remix.util.player;

import cn.remix.util.IMinecraft;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.item.ItemStack;

@Getter
@UtilityClass
public class ItemSpoofUtil implements IMinecraft {
    private boolean spoofing = false;
    private int spoofedSlot = 0;

    public void startSpoof(int slot) {
        if (!spoofing && slot >= 0 && slot < 36) {
            spoofing = true;
            spoofedSlot = slot;
        }
    }

    public void stopSpoof() {
        spoofing = false;
        spoofedSlot = 0;
    }

    public ItemStack getStack() {
        if (mc.player == null) return ItemStack.EMPTY;

        if (spoofing && spoofedSlot >= 0 && spoofedSlot < 36) {
            return mc.player.getInventory().getStack(spoofedSlot);
        }

        return mc.player.getMainHandStack();
    }
}