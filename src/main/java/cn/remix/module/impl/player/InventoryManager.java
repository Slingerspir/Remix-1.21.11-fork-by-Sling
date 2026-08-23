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
import lombok.Getter;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class InventoryManager extends Module {
    private final BoolValue inventoryOnly = new BoolValue("Inventory Only", false);
    private final NumberValue delay = new NumberValue("Delay", 50, 0, 1000, 10);

    private final NumberValue weaponSlot = new NumberValue("Weapon Slot", 1, -1, 9, 1);
    private final NumberValue pickaxeSlot = new NumberValue("Pickaxe Slot", 2, -1, 9, 1);
    private final NumberValue axeSlot = new NumberValue("Axe Slot", 3, -1, 9, 1);
    private final NumberValue shovelSlot = new NumberValue("Shovel Slot", 4, -1, 9, 1);
    private final NumberValue blockSlot = new NumberValue("Block Slot", 5, -1, 9, 1);
    private final NumberValue pearlSlot = new NumberValue("Pearl Slot", 6, -1, 9, 1);
    private final NumberValue projectileSlot = new NumberValue("Projectile Slot", 7, -1, 9, 1);

    private final NumberValue bowSlot = new NumberValue("Bow Slot", 0, -1, 9, 1);
    private final NumberValue fishingRodSlot = new NumberValue("Fishing Rod Slot", 0, -1, 9, 1);
    private final NumberValue waterBucketSlot = new NumberValue("Water Bucket Slot", 0, -1, 9, 1);
    private final NumberValue lavaBucketSlot = new NumberValue("Lava Bucket Slot", 0, -1, 9, 1);

    @Getter
    private final BoolValue keepFood = new BoolValue("Keep Food", false);
    private final NumberValue foodSlot = new NumberValue("Food Slot", 8, -1, 9, 1);

    @Getter
    private final NumberValue foodLimit = new NumberValue("Food Limit", 64, 0, 256, 32, keepFood::getValue);
    @Getter
    private final NumberValue blockLimit = new NumberValue("Block Limit", 128, 0, 512, 64);

    private final ModeValue sortMode = new ModeValue("Sort Mode", "Normal", "Normal", "Instant", "AntiCheat");

    private final NumberValue minDelay = new NumberValue("Min Delay", 30, 0, 200, 5);
    private final NumberValue maxDelay = new NumberValue("Max Delay", 80, 0, 300, 5);
    private final BoolValue randomizeOrder = new BoolValue("Randomize Order", true);
    private final NumberValue pauseChance = new NumberValue("Pause Chance", 15, 0, 50, 5);
    private final BoolValue simulateHover = new BoolValue("Simulate Hover", true);

    private final TimerUtil timer = new TimerUtil();

    private final TimerUtil actionTimer = new TimerUtil();
    private final TimerUtil hoverTimer = new TimerUtil();
    private final Random random = new Random();

    private List<Integer> itemOrder = new ArrayList<>();
    private int currentItemIndex = 0;
    private boolean isSorting = false;
    private int currentSlotProcessing = -1;

    public InventoryManager() {
        super("InventoryManager", Category.Player);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isSorting = false;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        String mode = sortMode.getValue();
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
            case "Instant" -> sortInstant();
            case "AntiCheat" -> sortAntiCheat();
            default -> sortNormal();
        }
    }

    private void sortNormal() {
        boolean instant = delay.getValue().doubleValue() == 0.0;

        if (!instant && !timer.hasTimeElapsed(delay.getValue().longValue())) {
            return;
        }

        boolean sort = performSorting(instant);

        if (sort && !instant) {
            timer.reset();
            return;
        }

        if (instant || timer.hasTimeElapsed(delay.getValue().longValue())) {
            cleanupItems(instant);
        }
    }

    private void sortInstant() {
        performSorting(true);

        cleanupItems(true);

        timer.reset();
    }

    private void sortAntiCheat() {
        if (itemOrder.isEmpty() || shouldRefreshOrder()) {
            initializeItemOrder();
            currentItemIndex = 0;
            isSorting = true;
        }

        if (currentItemIndex >= itemOrder.size()) {
            if (isSorting) {
                isSorting = false;
                cleanupItemsAntiCheat();
            }
            return;
        }

        int itemAction = itemOrder.get(currentItemIndex);

        if (simulateHover.getValue()) {
            simulateItemHover(itemAction);
        }

        long currentDelay = getRandomDelay();

        if (timer.hasTimeElapsed(currentDelay)) {
            boolean result = false;

            switch (itemAction) {
                case 0 -> result = sortWeapon();
                case 1 -> result = sortPickaxe();
                case 2 -> result = sortAxe();
                case 3 -> result = sortShovel();
                case 4 -> result = sortBlock();
                case 5 -> result = sortPearl();
                case 6 -> result = sortProjectile();
                case 7 -> result = sortBow();
                case 8 -> result = sortFishingRod();
                case 9 -> result = sortWaterBucket();
                case 10 -> result = sortLavaBucket();
                case 11 -> result = sortFood();
            }

            if (result) {
                actionTimer.reset();
            }

            currentItemIndex++;
            timer.reset();

            if (random.nextInt(100) < pauseChance.getValue().intValue()) {
                actionTimer.reset();
            }
        }
    }


    private void initializeItemOrder() {
        itemOrder.clear();

        if (weaponSlot.getValue().intValue() != 0) itemOrder.add(0);
        if (pickaxeSlot.getValue().intValue() != 0) itemOrder.add(1);
        if (axeSlot.getValue().intValue() != 0) itemOrder.add(2);
        if (shovelSlot.getValue().intValue() != 0) itemOrder.add(3);
        if (blockSlot.getValue().intValue() != 0) itemOrder.add(4);
        if (pearlSlot.getValue().intValue() != 0) itemOrder.add(5);
        if (projectileSlot.getValue().intValue() != 0) itemOrder.add(6);
        if (bowSlot.getValue().intValue() != 0) itemOrder.add(7);
        if (fishingRodSlot.getValue().intValue() != 0) itemOrder.add(8);
        if (waterBucketSlot.getValue().intValue() != 0) itemOrder.add(9);
        if (lavaBucketSlot.getValue().intValue() != 0) itemOrder.add(10);
        if (foodSlot.getValue().intValue() != 0) itemOrder.add(11);

        if (randomizeOrder.getValue() && itemOrder.size() > 1) {
            Collections.shuffle(itemOrder, random);
        }
    }

    private boolean shouldRefreshOrder() {
        if (currentItemIndex >= itemOrder.size()) {
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

    private void simulateItemHover(int itemAction) {
        if (currentSlotProcessing != -1) {
            if (!hoverTimer.hasTimeElapsed(30 + random.nextInt(50))) {
                return;
            }
        }

        int slot = -1;
        switch (itemAction) {
            case 0 -> slot = getBestSlotForItem(weaponSlot.getValue().intValue(), 0);
            case 1 -> slot = getBestSlotForItem(pickaxeSlot.getValue().intValue(), 1);
            case 2 -> slot = getBestSlotForItem(axeSlot.getValue().intValue(), 2);
            case 3 -> slot = getBestSlotForItem(shovelSlot.getValue().intValue(), 3);
            case 4 -> slot = getBestSlotForItem(blockSlot.getValue().intValue(), 4);
            case 5 -> slot = getBestSlotForItem(pearlSlot.getValue().intValue(), 5);
            case 6 -> slot = getBestSlotForItem(projectileSlot.getValue().intValue(), 6);
            case 7 -> slot = getBestSlotForItem(bowSlot.getValue().intValue(), 7);
            case 8 -> slot = getBestSlotForItem(fishingRodSlot.getValue().intValue(), 8);
            case 9 -> slot = getBestSlotForItem(waterBucketSlot.getValue().intValue(), 9);
            case 10 -> slot = getBestSlotForItem(lavaBucketSlot.getValue().intValue(), 10);
            case 11 -> slot = getBestSlotForItem(foodSlot.getValue().intValue(), 11);
        }

        if (slot != -1 && slot != currentSlotProcessing) {
            currentSlotProcessing = slot;
            hoverTimer.reset();
        }
    }

    private int getBestSlotForItem(int slotVal, int itemType) {
        if (slotVal == 0) return -1;

        int targetSlot = getTargetSlot(slotVal);
        if (targetSlot == -1) return -1;

        switch (itemType) {
            case 0 -> { 
                int bestSlot = ItemUtil.getBestWeaponSlot(targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 1 -> { 
                int bestSlot = ItemUtil.getBestToolSlot(ItemTags.PICKAXES, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 2 -> { 
                int bestSlot = ItemUtil.getBestToolSlot(ItemTags.AXES, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 3 -> { 
                int bestSlot = ItemUtil.getBestToolSlot(ItemTags.SHOVELS, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 4 -> { 
                int bestSlot = ItemUtil.getBestBlockSlot(targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 5 -> { 
                int bestSlot = ItemUtil.getBestPearlSlot(targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 6 -> { 
                int bestSlot = ItemUtil.getBestProjectileSlot(targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 7 -> { 
                int bestSlot = findBestItemSlot(Items.BOW, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 8 -> { 
                int bestSlot = findBestItemSlot(Items.FISHING_ROD, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 9 -> { 
                int bestSlot = findBestItemSlot(Items.WATER_BUCKET, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 10 -> { 
                int bestSlot = findBestItemSlot(Items.LAVA_BUCKET, targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
            case 11 -> { 
                int bestSlot = ItemUtil.getBestFoodSlot(targetSlot);
                if (bestSlot != -1 && bestSlot != targetSlot) return bestSlot;
            }
        }
        return -1;
    }

    private void cleanupItemsAntiCheat() {
        if (!timer.hasTimeElapsed(getRandomDelay())) return;

        boolean hasItemsToDrop = false;

        int prefBow = getTargetSlot(bowSlot.getValue().intValue());
        int prefRod = getTargetSlot(fishingRodSlot.getValue().intValue());
        int prefWater = getTargetSlot(waterBucketSlot.getValue().intValue());
        int prefLava = getTargetSlot(lavaBucketSlot.getValue().intValue());

        int keepBowSlot = findBestItemSlot(Items.BOW, prefBow);
        int keepRodSlot = findBestItemSlot(Items.FISHING_ROD, prefRod);
        int keepWaterSlot = findBestItemSlot(Items.WATER_BUCKET, prefWater);
        int keepLavaSlot = findBestItemSlot(Items.LAVA_BUCKET, prefLava);

        for (int i = 5; i <= 45; i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);

            if (slot.hasStack()) {
                var item = slot.getStack().getItem();
                boolean isDuplicate = false;

                if (item == Items.BOW && i != keepBowSlot) isDuplicate = true;
                else if (item == Items.FISHING_ROD && i != keepRodSlot) isDuplicate = true;
                else if (item == Items.WATER_BUCKET && i != keepWaterSlot) isDuplicate = true;
                else if (item == Items.LAVA_BUCKET && i != keepLavaSlot) isDuplicate = true;

                if (isDuplicate || ItemUtil.isUseless(i, slot.getStack())) {
                    hasItemsToDrop = true;

                    if (timer.hasTimeElapsed(getRandomDelay())) {
                        ClickSlotUtil.dropAll(i);
                        timer.reset();
                        return;
                    }
                }
            }
        }

        if (!hasItemsToDrop) {
            resetState();
        }
    }


    private boolean performSorting(boolean instant) {
        boolean sort = false;

        int weapon = weaponSlot.getValue().intValue();
        if (weapon != 0) {
            if (sortItem(ItemUtil.getBestWeaponSlot(getTargetSlot(weapon)), weapon)) sort = true;
        }

        int pickaxe = pickaxeSlot.getValue().intValue();
        if (pickaxe != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestToolSlot(ItemTags.PICKAXES, getTargetSlot(pickaxe)), pickaxe)) sort = true;
        }

        int axe = axeSlot.getValue().intValue();
        if (axe != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestToolSlot(ItemTags.AXES, getTargetSlot(axe)), axe)) sort = true;
        }

        int shovel = shovelSlot.getValue().intValue();
        if (shovel != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestToolSlot(ItemTags.SHOVELS, getTargetSlot(shovel)), shovel)) sort = true;
        }

        int block = blockSlot.getValue().intValue();
        if (block != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestBlockSlot(getTargetSlot(block)), block)) sort = true;
        }

        int pearl = pearlSlot.getValue().intValue();
        if (pearl != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestPearlSlot(getTargetSlot(pearl)), pearl)) sort = true;
        }

        int projectile = projectileSlot.getValue().intValue();
        if (projectile != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestProjectileSlot(getTargetSlot(projectile)), projectile)) sort = true;
        }

        int bow = bowSlot.getValue().intValue();
        if (bow != 0 && (!sort || instant)) {
            if (sortItem(findBestItemSlot(Items.BOW, getTargetSlot(bow)), bow)) sort = true;
        }

        int fishingRod = fishingRodSlot.getValue().intValue();
        if (fishingRod != 0 && (!sort || instant)) {
            if (sortItem(findBestItemSlot(Items.FISHING_ROD, getTargetSlot(fishingRod)), fishingRod)) sort = true;
        }

        int waterBucket = waterBucketSlot.getValue().intValue();
        if (waterBucket != 0 && (!sort || instant)) {
            if (sortItem(findBestItemSlot(Items.WATER_BUCKET, getTargetSlot(waterBucket)), waterBucket)) sort = true;
        }

        int lavaBucket = lavaBucketSlot.getValue().intValue();
        if (lavaBucket != 0 && (!sort || instant)) {
            if (sortItem(findBestItemSlot(Items.LAVA_BUCKET, getTargetSlot(lavaBucket)), lavaBucket)) sort = true;
        }

        int food = foodSlot.getValue().intValue();
        if (food != 0 && (!sort || instant)) {
            if (sortItem(ItemUtil.getBestFoodSlot(getTargetSlot(food)), food)) sort = true;
        }

        return sort;
    }


    private boolean sortItem(int bestSlot, int slotVal) {
        if (bestSlot == -1 || slotVal == 0) return false;

        int targetSlot = getTargetSlot(slotVal);
        if (bestSlot != targetSlot) {
            ClickSlotUtil.swap(bestSlot, slotVal == -1 ? 40 : slotVal - 1);
            timer.reset();
            return true;
        }

        return false;
    }

    private int getTargetSlot(int slotVal) {
        if (slotVal == -1) return 45;
        if (slotVal >= 1 && slotVal <= 9) return 36 + slotVal - 1;
        return -1;
    }

    private int findBestItemSlot(net.minecraft.item.Item targetItem, int preferredSlot) {
        if (mc.player == null) return -1;

        if (preferredSlot >= 5 && preferredSlot <= 45) {
            Slot slot = mc.player.currentScreenHandler.getSlot(preferredSlot);
            if (slot.hasStack() && slot.getStack().getItem() == targetItem) {
                return preferredSlot;
            }
        }

        for (int i = 5; i <= 45; i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);
            if (slot.hasStack() && slot.getStack().getItem() == targetItem) {
                return i;
            }
        }
        return -1;
    }


    private void cleanupItems(boolean instant) {
        int prefBow = getTargetSlot(bowSlot.getValue().intValue());
        int prefRod = getTargetSlot(fishingRodSlot.getValue().intValue());
        int prefWater = getTargetSlot(waterBucketSlot.getValue().intValue());
        int prefLava = getTargetSlot(lavaBucketSlot.getValue().intValue());

        int keepBowSlot = findBestItemSlot(Items.BOW, prefBow);
        int keepRodSlot = findBestItemSlot(Items.FISHING_ROD, prefRod);
        int keepWaterSlot = findBestItemSlot(Items.WATER_BUCKET, prefWater);
        int keepLavaSlot = findBestItemSlot(Items.LAVA_BUCKET, prefLava);

        for (int i = 5; i <= 45; i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);

            if (slot.hasStack()) {
                var item = slot.getStack().getItem();
                boolean isDuplicate = false;

                if (item == Items.BOW && i != keepBowSlot) isDuplicate = true;
                else if (item == Items.FISHING_ROD && i != keepRodSlot) isDuplicate = true;
                else if (item == Items.WATER_BUCKET && i != keepWaterSlot) isDuplicate = true;
                else if (item == Items.LAVA_BUCKET && i != keepLavaSlot) isDuplicate = true;

                if (isDuplicate || ItemUtil.isUseless(i, slot.getStack())) {
                    ClickSlotUtil.dropAll(i);
                    timer.reset();
                    if (!instant) {
                        return;
                    }
                }
            }
        }
    }


    private boolean sortWeapon() {
        int weapon = weaponSlot.getValue().intValue();
        if (weapon == 0) return false;
        return sortItem(ItemUtil.getBestWeaponSlot(getTargetSlot(weapon)), weapon);
    }

    private boolean sortPickaxe() {
        int pickaxe = pickaxeSlot.getValue().intValue();
        if (pickaxe == 0) return false;
        return sortItem(ItemUtil.getBestToolSlot(ItemTags.PICKAXES, getTargetSlot(pickaxe)), pickaxe);
    }

    private boolean sortAxe() {
        int axe = axeSlot.getValue().intValue();
        if (axe == 0) return false;
        return sortItem(ItemUtil.getBestToolSlot(ItemTags.AXES, getTargetSlot(axe)), axe);
    }

    private boolean sortShovel() {
        int shovel = shovelSlot.getValue().intValue();
        if (shovel == 0) return false;
        return sortItem(ItemUtil.getBestToolSlot(ItemTags.SHOVELS, getTargetSlot(shovel)), shovel);
    }

    private boolean sortBlock() {
        int block = blockSlot.getValue().intValue();
        if (block == 0) return false;
        return sortItem(ItemUtil.getBestBlockSlot(getTargetSlot(block)), block);
    }

    private boolean sortPearl() {
        int pearl = pearlSlot.getValue().intValue();
        if (pearl == 0) return false;
        return sortItem(ItemUtil.getBestPearlSlot(getTargetSlot(pearl)), pearl);
    }

    private boolean sortProjectile() {
        int projectile = projectileSlot.getValue().intValue();
        if (projectile == 0) return false;
        return sortItem(ItemUtil.getBestProjectileSlot(getTargetSlot(projectile)), projectile);
    }

    private boolean sortBow() {
        int bow = bowSlot.getValue().intValue();
        if (bow == 0) return false;
        return sortItem(findBestItemSlot(Items.BOW, getTargetSlot(bow)), bow);
    }

    private boolean sortFishingRod() {
        int fishingRod = fishingRodSlot.getValue().intValue();
        if (fishingRod == 0) return false;
        return sortItem(findBestItemSlot(Items.FISHING_ROD, getTargetSlot(fishingRod)), fishingRod);
    }

    private boolean sortWaterBucket() {
        int waterBucket = waterBucketSlot.getValue().intValue();
        if (waterBucket == 0) return false;
        return sortItem(findBestItemSlot(Items.WATER_BUCKET, getTargetSlot(waterBucket)), waterBucket);
    }

    private boolean sortLavaBucket() {
        int lavaBucket = lavaBucketSlot.getValue().intValue();
        if (lavaBucket == 0) return false;
        return sortItem(findBestItemSlot(Items.LAVA_BUCKET, getTargetSlot(lavaBucket)), lavaBucket);
    }

    private boolean sortFood() {
        int food = foodSlot.getValue().intValue();
        if (food == 0) return false;
        return sortItem(ItemUtil.getBestFoodSlot(getTargetSlot(food)), food);
    }

    private void resetState() {
        itemOrder.clear();
        currentItemIndex = 0;
        isSorting = false;
        currentSlotProcessing = -1;
        actionTimer.reset();
        hoverTimer.reset();
    }
}