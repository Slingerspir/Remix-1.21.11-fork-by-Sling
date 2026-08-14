package cn.remix.util.player;

import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

@UtilityClass
public class BlockUtil implements IMinecraft {

    public int getBlockSlot(boolean maxStack) {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        int maxCount = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }

            if (!isPlaceable(blockItem.getBlock())) {
                continue;
            }

            if (!maxStack) {
                return i;
            }

            int currentCount = stack.getCount();
            if (currentCount > maxCount) {
                maxCount = currentCount;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    public boolean isPlaceable(Block block) {
        if (block == Blocks.AIR || block instanceof BlockEntityProvider) return false;

        BlockState state = block.getDefaultState();
        return state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) || block instanceof SlabBlock || block instanceof StairsBlock;
    }

    public int getBlockCount() {
        if (mc.player == null) return 0;
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (isPlaceable(blockItem.getBlock())) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }
}
