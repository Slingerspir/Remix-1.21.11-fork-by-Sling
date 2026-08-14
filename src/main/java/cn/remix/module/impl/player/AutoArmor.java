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
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AutoArmor extends Module {

    private final BoolValue inventoryOnly = new BoolValue("Inventory Only", false);
    private final NumberValue delay = new NumberValue("Delay", 50, 0, 1000, 10);

    private final ModeValue equipMode = new ModeValue("Equip Mode", "Normal", "Normal", "Instant", "AntiCheat");

    private final NumberValue minDelay = new NumberValue("Min Delay", 30, 0, 200, 5);
    private final NumberValue maxDelay = new NumberValue("Max Delay", 80, 0, 300, 5);
    private final BoolValue randomizeOrder = new BoolValue("Randomize Order", true);
    private final NumberValue pauseChance = new NumberValue("Pause Chance", 15, 0, 50, 5);
    private final BoolValue simulateHover = new BoolValue("Simulate Hover", true);
    private final BoolValue dropOldArmor = new BoolValue("Drop Old Armor", true);

    private final TimerUtil armorTimer = new TimerUtil();

    private final TimerUtil actionTimer = new TimerUtil();
    private final TimerUtil hoverTimer = new TimerUtil();
    private final Random random = new Random();

    private List<Integer> armorOrder = new ArrayList<>();
    private int currentItemIndex = 0;
    private boolean isEquipping = false;
    private int currentSlotProcessing = -1;
    private boolean hasEquippedAny = false;

    public AutoArmor() {
        super("AutoArmor", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isEquipping = false;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        String mode = equipMode.getValue();
        setSuffix(mode);

        if (inventoryOnly.getValue()) {
            if (!(mc.currentScreen instanceof InventoryScreen)) {
                resetState();
                return;
            }
        } else {
            if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
                resetState();
                return;
            }
        }

        switch (mode) {
            case "Instant" -> equipInstant();
            case "AntiCheat" -> equipAntiCheat();
            default -> equipNormal();
        }
    }

    private void equipNormal() {
        boolean instant = delay.getValue().doubleValue() == 0.0;

        if (!instant && !armorTimer.hasTimeElapsed(delay.getValue().longValue())) {
            return;
        }

        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        for (EquipmentSlot slot : armorSlots) {
            if (equipArmor(slot, ItemUtil.getBestArmorSlot(slot)) && !instant) {
                armorTimer.reset();
                break;
            }
        }
    }

    private void equipInstant() {
        boolean equipped = false;

        if (dropOldArmor.getValue()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot slot : armorSlots) {
                int targetSlot = getTargetSlot(slot);
                if (targetSlot != -1) {
                    Slot currentArmorSlot = mc.player.currentScreenHandler.getSlot(targetSlot);
                    if (currentArmorSlot.hasStack()) {
                        ClickSlotUtil.dropAll(targetSlot);
                        equipped = true;
                    }
                }
            }
        }

        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : armorSlots) {
            int bestSlot = ItemUtil.getBestArmorSlot(slot);
            if (bestSlot >= 9 && bestSlot <= 44) {
                ClickSlotUtil.shiftClick(bestSlot);
                equipped = true;
            }
        }

        if (equipped) {
            armorTimer.reset();
        }
    }

    private void equipAntiCheat() {
        if (armorOrder.isEmpty() || shouldRefreshOrder()) {
            initializeArmorOrder();
            currentItemIndex = 0;
            isEquipping = true;
            hasEquippedAny = false;
        }

        if (currentItemIndex >= armorOrder.size()) {
            if (isEquipping) {
                isEquipping = false;
                if (hasEquippedAny) {
                    armorTimer.reset();
                }
            }
            return;
        }

        int armorAction = armorOrder.get(currentItemIndex);

        if (simulateHover.getValue()) {
            simulateArmorHover(armorAction);
        }

        long currentDelay = getRandomDelay();

        if (armorTimer.hasTimeElapsed(currentDelay)) {
            boolean result = false;

            switch (armorAction) {
                case 0 -> result = equipHelmet();
                case 1 -> result = equipChestplate();
                case 2 -> result = equipLeggings();
                case 3 -> result = equipBoots();
            }

            if (result) {
                hasEquippedAny = true;
                actionTimer.reset();
            }
			
            currentItemIndex++;
            armorTimer.reset();

            if (random.nextInt(100) < pauseChance.getValue().intValue()) {
                actionTimer.reset();
            }
        }
    }


    private void initializeArmorOrder() {
        armorOrder.clear();

        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armorSlots.length; i++) {
            EquipmentSlot slot = armorSlots[i];
            int bestSlot = ItemUtil.getBestArmorSlot(slot);
            if (bestSlot >= 9 && bestSlot <= 44) {
                armorOrder.add(i);
            }
        }

        if (randomizeOrder.getValue() && armorOrder.size() > 1) {
            Collections.shuffle(armorOrder, random);
        }
    }

    private boolean shouldRefreshOrder() {
        if (currentItemIndex >= armorOrder.size()) {
            return true;
        }
        return false;
    }

    private long getRandomDelay() {
        long min = minDelay.getValue().longValue();
        long max = maxDelay.getValue().longValue();

        if (min >= max) {
            return min;
        }

        return min + random.nextInt((int) (max - min));
    }

    private void simulateArmorHover(int armorAction) {
        int slot = -1;
        switch (armorAction) {
            case 0 -> { 
                int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.HEAD);
                if (bestSlot >= 9 && bestSlot <= 44) slot = bestSlot;
            }
            case 1 -> { 
                int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.CHEST);
                if (bestSlot >= 9 && bestSlot <= 44) slot = bestSlot;
            }
            case 2 -> {
                int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.LEGS);
                if (bestSlot >= 9 && bestSlot <= 44) slot = bestSlot;
            }
            case 3 -> {
                int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.FEET);
                if (bestSlot >= 9 && bestSlot <= 44) slot = bestSlot;
            }
        }

        if (slot != -1 && slot != currentSlotProcessing) {
            currentSlotProcessing = slot;
            hoverTimer.reset();
        }

        if (currentSlotProcessing != -1) {
            if (!hoverTimer.hasTimeElapsed(20 + random.nextInt(40))) {
                return;
            }
        }
    }


    private boolean equipHelmet() {
        if (mc.player == null) return false;

        if (dropOldArmor.getValue()) {
            Slot currentArmorSlot = mc.player.currentScreenHandler.getSlot(5);
            if (currentArmorSlot.hasStack()) {
                ClickSlotUtil.dropAll(5);
                return true;
            }
        }

        int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.HEAD);
        if (bestSlot >= 9 && bestSlot <= 44) {
            ClickSlotUtil.shiftClick(bestSlot);
            return true;
        }
        return false;
    }

    private boolean equipChestplate() {
        if (mc.player == null) return false;

        if (dropOldArmor.getValue()) {
            Slot currentArmorSlot = mc.player.currentScreenHandler.getSlot(6);
            if (currentArmorSlot.hasStack()) {
                ClickSlotUtil.dropAll(6);
                return true;
            }
        }

        int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.CHEST);
        if (bestSlot >= 9 && bestSlot <= 44) {
            ClickSlotUtil.shiftClick(bestSlot);
            return true;
        }
        return false;
    }

    private boolean equipLeggings() {
        if (mc.player == null) return false;

        if (dropOldArmor.getValue()) {
            Slot currentArmorSlot = mc.player.currentScreenHandler.getSlot(7);
            if (currentArmorSlot.hasStack()) {
                ClickSlotUtil.dropAll(7);
                return true;
            }
        }

        int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.LEGS);
        if (bestSlot >= 9 && bestSlot <= 44) {
            ClickSlotUtil.shiftClick(bestSlot);
            return true;
        }
        return false;
    }

    private boolean equipBoots() {
        if (mc.player == null) return false;

        if (dropOldArmor.getValue()) {
            Slot currentArmorSlot = mc.player.currentScreenHandler.getSlot(8);
            if (currentArmorSlot.hasStack()) {
                ClickSlotUtil.dropAll(8);
                return true;
            }
        }

        int bestSlot = ItemUtil.getBestArmorSlot(EquipmentSlot.FEET);
        if (bestSlot >= 9 && bestSlot <= 44) {
            ClickSlotUtil.shiftClick(bestSlot);
            return true;
        }
        return false;
    }


    private boolean equipArmor(EquipmentSlot equipmentSlot, int bestSlot) {
        if (mc.player == null) return false;

        if (bestSlot >= 9 && bestSlot <= 44) {
            int targetSlot = switch (equipmentSlot) {
                case HEAD -> 5;
                case CHEST -> 6;
                case LEGS -> 7;
                case FEET -> 8;
                default -> -1;
            };

            if (targetSlot != -1) {
                Slot currentArmorSlot = mc.player.currentScreenHandler.getSlot(targetSlot);
                if (currentArmorSlot.hasStack()) {
                    ClickSlotUtil.dropAll(targetSlot);
                    armorTimer.reset();
                    return true;
                }
            }

            ClickSlotUtil.shiftClick(bestSlot);
            armorTimer.reset();
            return true;
        }
        return false;
    }

    private int getTargetSlot(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
    }

    private void resetState() {
        armorOrder.clear();
        currentItemIndex = 0;
        isEquipping = false;
        hasEquippedAny = false;
        currentSlotProcessing = -1;
        actionTimer.reset();
        hoverTimer.reset();
    }
}