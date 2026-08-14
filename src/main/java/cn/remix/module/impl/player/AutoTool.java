package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.util.player.ItemSpoofUtil;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoTool extends Module {
    private final ModeValue switchMode = new ModeValue("Switch Mode", "Switch", "Switch", "Spoof");

    private boolean mining = false;
    private int oldSlot = 0;

    public AutoTool() {
        super("AutoTool", Category.Player);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;

        oldSlot = mc.player.getInventory().getSelectedSlot();
        mining = false;
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(switchMode.getValue());

        if (mc.options.attackKey.isPressed() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
            BlockState blockState = mc.world.getBlockState(blockHit.getBlockPos());

            if (blockState.isAir()) {
                revert();
                return;
            }

            int bestSlot = findBestSlot(blockState);

            if (bestSlot != -1) {
                if (!mining) {
                    oldSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(bestSlot);

                    mining = true;

                    if (switchMode.is("Spoof")) {
                        ItemSpoofUtil.startSpoof(oldSlot);
                    }
                } else if (mc.player.getInventory().getSelectedSlot() != bestSlot) {
                    mc.player.getInventory().setSelectedSlot(bestSlot);
                }
            }
        } else {
            revert();
        }
    }

    private void revert() {
        if (mining) {
            reset();
        }
    }

    private void reset() {
        if (mc.player == null) return;

        if (switchMode.is("Spoof")) {
            ItemSpoofUtil.stopSpoof();
        }

        if (mining) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mining = false;
        }
    }

    private int findBestSlot(BlockState state) {
        if (mc.player == null) return -1;

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

        return bestSlot;
    }
}