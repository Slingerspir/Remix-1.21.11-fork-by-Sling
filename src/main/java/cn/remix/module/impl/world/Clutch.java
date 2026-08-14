package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.BlockUtil;
import cn.remix.util.player.MovementUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class Clutch extends Module {
    private final BoolValue allowPlacing = new BoolValue("Allow Placing", true);
    private final ModeValue turnOnMode = new ModeValue("Turn On Mode", "Void Hit", "Void", "Void Hit", "Void Hit Ignore Fireball");
    private final NumberValue maxBlocks = new NumberValue("Max Blocks", 6, 0, 20, 2);
    private final ModeValue turnOffMode = new ModeValue("Turn Off Mode", "Forward", "Movement", "Forward", "None");
    private boolean wasScaffoldEnabled;
    private boolean clutching;
    private boolean pendingTurnOff;
    private boolean fireballing;
    private int fireballTicks;
    private int airTicks;

    public Clutch() {
        super("Clutch", Category.World);
    }

    @Override
    public void onEnable() {
        Scaffold scaffold = getModule(Scaffold.class);
        wasScaffoldEnabled = scaffold != null && scaffold.isEnabled();
        clutching = false;
        pendingTurnOff = false;
        fireballing = false;
        fireballTicks = 0;
        airTicks = 0;
    }

    @Override
    public void onDisable() {
        Scaffold scaffold = getModule(Scaffold.class);
        if (scaffold != null && !wasScaffoldEnabled && scaffold.isEnabled()) {
            scaffold.setEnabled(false);
        }
        mc.options.useKey.setPressed(false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        Scaffold scaffold = getModule(Scaffold.class);
        if (scaffold == null) return;

        updateFireballState();

        if (mc.player.isOnGround()) {
            airTicks = 0;
        } else {
            airTicks++;
        }

        int fallDistance = fallDistance();
        boolean overVoid = fallDistance == -1;
        boolean trigger = shouldTrigger(overVoid);

        if (trigger && !clutching && !scaffold.isEnabled() && BlockUtil.getBlockSlot(false) != -1) {
            clutching = true;
            pendingTurnOff = false;
            scaffold.setEnabled(true);
        }

        if (clutching && maxBlocks.getValue() > 0 && airTicks >= maxBlocks.getValue()) {
            pendingTurnOff = true;
        }

        if (clutching && (!overVoid || mc.player.isOnGround())) {
            clutching = false;
            pendingTurnOff = true;
        }

        if (pendingTurnOff) {
            tryTurnOff(scaffold);
        }
    }

    private boolean shouldTrigger(boolean overVoid) {
        if (!overVoid || mc.player.isOnGround() || mc.player.getAbilities().flying || fireballing) {
            return false;
        }

        return switch (turnOnMode.getValue()) {
            case "Void" -> true;
            case "Void Hit Ignore Fireball" -> mc.player.hurtTime > 0;
            default -> mc.player.hurtTime > 0 && !isHoldingFireCharge();
        };
    }

    private void tryTurnOff(Scaffold scaffold) {
        boolean shouldDisable = switch (turnOffMode.getValue()) {
            case "Movement" -> MovementUtil.isMoving();
            case "Forward" -> mc.options.forwardKey.isPressed();
            default -> !scaffold.isEnabled();
        };

        if (shouldDisable) {
            if (!wasScaffoldEnabled && scaffold.isEnabled()) {
                scaffold.setEnabled(false);
            }

            if (allowPlacing.getValue() && mc.options.useKey.isPressed()) {
                mc.options.useKey.setPressed(true);
            }
            pendingTurnOff = false;
        }
    }

    private void updateFireballState() {
        if (isHoldingFireCharge() && mc.options.useKey.isPressed() && mc.player.getPitch() > 12.0f) {
            fireballing = true;
            fireballTicks = 20;
        }

        if (fireballTicks > 0) {
            fireballTicks--;
            if (fireballTicks <= 1) {
                fireballing = false;
            }
        }
    }

    private boolean isHoldingFireCharge() {
        ItemStack stack = mc.player.getMainHandStack();
        return !stack.isEmpty() && stack.getItem().toString().contains("fire_charge");
    }

    private int fallDistance() {
        BlockPos playerPos = mc.player.getBlockPos();
        int startY = playerPos.getY() - 1;
        int bottomY = mc.world.getBottomY();

        for (int y = startY; y >= bottomY; y--) {
            BlockPos pos = new BlockPos(playerPos.getX(), y, playerPos.getZ());
            if (!mc.world.getBlockState(pos).isAir()) {
                return startY - y;
            }
        }

        return -1;
    }
}
