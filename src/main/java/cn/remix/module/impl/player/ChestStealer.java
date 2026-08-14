package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.player.ClickSlotUtil;
import cn.remix.util.player.ItemUtil;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Comparator;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class ChestStealer extends Module {
    private final BoolValue onlyBest = new BoolValue("Only Best", true);
    private final NumberValue delay = new NumberValue("Delay", 50, 0, 500, 10);
    private final NumberValue openDelay = new NumberValue("Open Delay", 50, 0, 500, 10);
    private final BoolValue autoClose = new BoolValue("Auto Close", true);

    private final ModeValue stealMode = new ModeValue("Steal Mode", "Normal", "Normal", "Instant", "AntiCheat");

    private final BoolValue auraEnabled = new BoolValue("Aura", false);
    private final NumberValue auraRange = new NumberValue("Aura Range", 4.5f, 1.0f, 6.0f, 0.1f, auraEnabled::getValue);
    private final NumberValue auraDelay = new NumberValue("Aura Delay", 5, 1, 20, 1, auraEnabled::getValue);
    private final BoolValue auraRotate = new BoolValue("Aura Rotate", true, auraEnabled::getValue);
    private final BoolValue auraSilent = new BoolValue("Aura Silent", true, auraEnabled::getValue);

    private final NumberValue minDelay = new NumberValue("Min Delay", 30, 0, 200, 5);
    private final NumberValue maxDelay = new NumberValue("Max Delay", 80, 0, 300, 5);
    private final BoolValue randomizeOrder = new BoolValue("Randomize Order", true);
    private final NumberValue pauseChance = new NumberValue("Pause Chance", 15, 0, 50, 5);

    private final TimerUtil clickTimer = new TimerUtil();
    private final TimerUtil openTimer = new TimerUtil();
    private final TimerUtil actionTimer = new TimerUtil();
    private final TimerUtil closeTimer = new TimerUtil();
    private final TimerUtil auraCooldown = new TimerUtil();
    private final Random random = new Random();

    private final List<Integer> slotOrder = new ArrayList<>();
    private int currentSlotIndex = 0;
    private boolean hasStolenAny = false;
    private boolean isClosing = false;

    private final Set<BlockPos> openedChests = new HashSet<>();

    public ChestStealer() {
        super("ChestStealer", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        slotOrder.clear();
        currentSlotIndex = 0;
        hasStolenAny = false;
        isClosing = false;
        openedChests.clear();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        openedChests.clear();
    }


    private void handleAura() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!auraEnabled.getValue()) return;

        if (mc.currentScreen instanceof GenericContainerScreen) {
            return;
        }

        if (!auraCooldown.hasTimeElapsed(auraDelay.getValue().longValue() * 50L)) {
            return;
        }

        List<BlockPos> chests = getChestsInRange();
        if (chests.isEmpty()) {
            return;
        }

        BlockPos target = getNearestChest(chests);
        if (target == null) {
            return;
        }

        if (openedChests.contains(target)) {
            return;
        }

        openChest(target);
    }

    private List<BlockPos> getChestsInRange() {
        List<BlockPos> chests = new ArrayList<>();
        float range = auraRange.getValue().floatValue();
        double rangeSq = range * range;
        BlockPos playerPos = mc.player.getBlockPos();

        int radius = (int) Math.ceil(range);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                        Vec3d chestCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        Vec3d playerPosVec = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        double distSq = playerPosVec.distanceTo(chestCenter);

                        if (distSq <= rangeSq) {
                            chests.add(pos);
                        }
                    }
                }
            }
        }

        return chests;
    }

    private BlockPos getNearestChest(List<BlockPos> chests) {
        if (chests.isEmpty()) return null;

        chests.sort(Comparator.comparingDouble(
                pos -> new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                        .distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
        ));

        return chests.get(0);
    }

    private void openChest(BlockPos pos) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (auraRotate.getValue()) {
            Vec3d chestCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3d playerPos = mc.player.getEyePos();
            Vec3d diff = chestCenter.subtract(playerPos);
            float[] rotations = getRotations(diff);

            if (!auraSilent.getValue()) {
                mc.player.setYaw(rotations[0]);
                mc.player.setPitch(rotations[1]);
            }
        }

        Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, pos, false);

        mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND, hitResult);
        openedChests.add(pos);
        auraCooldown.reset();
    }

    private float[] getRotations(Vec3d diff) {
        double x = diff.x;
        double y = diff.y;
        double z = diff.z;

        double distance = Math.sqrt(x * x + z * z);
        float yaw = (float) Math.toDegrees(-Math.atan2(x, z));
        float pitch = (float) Math.toDegrees(-Math.atan2(y, distance));

        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90, 90);

        return new float[]{yaw, pitch};
    }


    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        String mode = stealMode.getValue();
        setSuffix(mode + (auraEnabled.getValue() ? " +Aura" : ""));

        if (event.isPre()) {
            handleAura();
        }

        if (event.isPost()) return;

        if (mc.currentScreen instanceof GenericContainerScreen container) {
            if (!openTimer.hasTimeElapsed(openDelay.getValue().longValue())) return;

            GenericContainerScreenHandler handler = container.getScreenHandler();

            switch (mode) {
                case "Instant" -> stealInstant(handler);
                case "AntiCheat" -> stealAntiCheat(handler);
                default -> stealNormal(handler);
            }
        } else {
            openTimer.reset();
            resetState();
        }
    }

    private void stealNormal(GenericContainerScreenHandler handler) {
        boolean instant = delay.getValue().doubleValue() == 0.0;
        boolean hasRemaining = false;

        for (int i = 0; i < handler.getInventory().size(); i++) {
            Slot slot = handler.getSlot(i);

            if (slot.hasStack()) {
                if (onlyBest.getValue() && ItemUtil.isUseless(-1, slot.getStack())) continue;
                hasRemaining = true;

                if (instant) {
                    ClickSlotUtil.shiftClick(i);
                    hasStolenAny = true;
                } else if (clickTimer.hasTimeElapsed(delay.getValue().longValue())) {
                    ClickSlotUtil.shiftClick(i);
                    hasStolenAny = true;
                    clickTimer.reset();
                    return;
                }
            }
        }

        if ((!hasRemaining || instant) && autoClose.getValue()) {
            if (mc.player != null) {
                mc.player.closeHandledScreen();
            }
            resetState();
        }
    }

    private void stealInstant(GenericContainerScreenHandler handler) {
        boolean hasRemaining = false;

        for (int i = 0; i < handler.getInventory().size(); i++) {
            Slot slot = handler.getSlot(i);

            if (slot.hasStack()) {
                if (onlyBest.getValue() && ItemUtil.isUseless(-1, slot.getStack())) continue;
                hasRemaining = true;
                ClickSlotUtil.shiftClick(i);
                hasStolenAny = true;
            }
        }

        if (hasRemaining) {
            return;
        }

        if (autoClose.getValue() && hasStolenAny) {
            if (mc.player != null) {
                mc.player.closeHandledScreen();
            }
            resetState();
        }
    }

    private void stealAntiCheat(GenericContainerScreenHandler handler) {
        if (isClosing) {
            if (closeTimer.hasTimeElapsed(100 + random.nextInt(150))) {
                if (mc.player != null) {
                    mc.player.closeHandledScreen();
                }
                resetState();
                isClosing = false;
            }
            return;
        }

        if (slotOrder.isEmpty() || shouldRefreshOrder(handler)) {
            initializeSlotOrder(handler);
            currentSlotIndex = 0;
        }

        if (currentSlotIndex >= slotOrder.size()) {
            if (autoClose.getValue() && hasStolenAny) {
                isClosing = true;
                closeTimer.reset();
            }
            return;
        }

        int slotIndex = slotOrder.get(currentSlotIndex);
        Slot slot = handler.getSlot(slotIndex);

        if (!slot.hasStack() || (onlyBest.getValue() && ItemUtil.isUseless(-1, slot.getStack()))) {
            currentSlotIndex++;
            return;
        }

        long currentDelay = getRandomDelay();

        if (clickTimer.hasTimeElapsed(currentDelay)) {
            ClickSlotUtil.shiftClick(slotIndex);
            hasStolenAny = true;

            currentSlotIndex++;
            clickTimer.reset();

            if (random.nextInt(100) < pauseChance.getValue().intValue()) {
                actionTimer.reset();
            }
        }
    }

    private void initializeSlotOrder(GenericContainerScreenHandler handler) {
        slotOrder.clear();

        for (int i = 0; i < handler.getInventory().size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.hasStack()) {
                if (onlyBest.getValue() && ItemUtil.isUseless(-1, slot.getStack())) {
                    continue;
                }
                slotOrder.add(i);
            }
        }

        if (randomizeOrder.getValue() && slotOrder.size() > 1) {
            Collections.shuffle(slotOrder, random);
        }

        currentSlotIndex = 0;
        hasStolenAny = false;
    }

    private boolean shouldRefreshOrder(GenericContainerScreenHandler handler) {
        if (currentSlotIndex >= slotOrder.size()) {
            return true;
        }

        int slotIndex = slotOrder.get(currentSlotIndex);
        Slot slot = handler.getSlot(slotIndex);
        return !slot.hasStack() || (onlyBest.getValue() && ItemUtil.isUseless(-1, slot.getStack()));
    }

    private long getRandomDelay() {
        long min = minDelay.getValue().longValue();
        long max = maxDelay.getValue().longValue();

        if (min >= max) {
            return min;
        }

        return min + random.nextInt((int) (max - min));
    }

    private void resetState() {
        slotOrder.clear();
        currentSlotIndex = 0;
        hasStolenAny = false;
        isClosing = false;
        actionTimer.reset();
        closeTimer.reset();
    }


}