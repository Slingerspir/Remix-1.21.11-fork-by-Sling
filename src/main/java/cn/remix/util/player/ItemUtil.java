package cn.remix.util.player;

import cn.remix.module.impl.player.InventoryManager;
import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.slot.Slot;

@UtilityClass
public class ItemUtil implements IMinecraft {

    public boolean isUseless(int slotIndex, ItemStack stack) {
        if (mc.player == null || stack == null || stack.isEmpty()) return true;

        // Armor
        if (isArmor(stack)) {
            EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
            if (equippable == null) return true;
            return slotIndex == -1 ? !(getArmorScore(stack) > getBestArmorScore(equippable.slot())) : slotIndex != getBestArmorSlot(equippable.slot());
        }

        // Weapon
        if (isSword(stack)) {
            return slotIndex == -1 ? !(getWeaponScore(stack) > getBestWeaponScore()) : slotIndex != getBestWeaponSlot();
        }

        // Tool
        if (isTool(stack)) {
            TagKey<Item> toolTag = getToolTag(stack);
            return slotIndex == -1 ? !(getToolScore(stack) > getBestToolScore(toolTag)) : slotIndex != getBestToolSlot(toolTag);
        }

        // Pearl
        if (stack.getItem() instanceof EnderPearlItem) {
            return false;
        }

        // Projectile (Snowball, Egg)
        if (isProjectile(stack)) {
            return false;
        }

        // Food
        if (stack.contains(DataComponentTypes.FOOD)) {
            if (isGoldenApple(stack)) {
                return false;
            }

            InventoryManager inventoryManager = instance.getModuleManager().getModule(InventoryManager.class);
            if (!inventoryManager.getKeepFood().getValue()) return true;
            int limit = inventoryManager.getFoodLimit().getValue().intValue();

            if (slotIndex == -1) {
                return getPlayerFoodCount() >= limit;
            } else {
                int food = 0;
                int[] prioritySlots = new int[36];
                int index = 0;

                for (int i = 36; i <= 44; i++) prioritySlots[index++] = i;
                for (int i = 9; i <= 35; i++) prioritySlots[index++] = i;

                for (int s : prioritySlots) {
                    if (s == slotIndex) break;

                    if (s >= mc.player.currentScreenHandler.slots.size()) continue;

                    Slot tempSlot = mc.player.currentScreenHandler.getSlot(s);
                    if (tempSlot != null && tempSlot.hasStack()) {
                        ItemStack tempStack = tempSlot.getStack();
                        if (tempStack.contains(DataComponentTypes.FOOD) && !isGoldenApple(tempStack)) {
                            food += tempStack.getCount();
                        }
                    }
                }
                return food >= limit;
            }
        }

        // Block
        if (stack.getItem() instanceof BlockItem blockItem) {
            InventoryManager inventoryManager = instance.getModuleManager().getModule(InventoryManager.class);
            if (!BlockUtil.isPlaceable(blockItem.getBlock())) return true;

            int limit = inventoryManager.getBlockLimit().getValue().intValue();

            if (slotIndex == -1) {
                return getPlayerBlockCount() >= limit;
            } else {
                int block = 0;
                int[] prioritySlots = new int[36];
                int index = 0;

                for (int i = 36; i <= 44; i++) prioritySlots[index++] = i;
                for (int i = 9; i <= 35; i++) prioritySlots[index++] = i;

                for (int s : prioritySlots) {
                    if (s == slotIndex) break;

                    if (s >= mc.player.currentScreenHandler.slots.size()) continue;

                    Slot tempSlot = mc.player.currentScreenHandler.getSlot(s);
                    if (tempSlot != null && tempSlot.hasStack()) {
                        ItemStack tempStack = tempSlot.getStack();
                        if (tempStack.getItem() instanceof BlockItem tempBlockItem) {
                            if (BlockUtil.isPlaceable(tempBlockItem.getBlock())) {
                                block += tempStack.getCount();
                            }
                        }
                    }
                }
                return block >= limit;
            }
        }

        return stack.getItem() instanceof HoeItem;
    }

    public float getArmorScore(ItemStack stack) {
        if (!isArmor(stack)) return -1.0F;

        float score = 0.0F;
        String registryName = stack.getItem().toString().toLowerCase();
        if (registryName.contains("diamond") || registryName.contains("netherite")) {
            score += 20.0F;
        } else if (registryName.contains("iron")) {
            score += 15.0F;
        } else if (registryName.contains("chainmail")) {
            score += 12.0F;
        } else if (registryName.contains("gold")) {
            score += 11.0F;
        } else if (registryName.contains("leather")) {
            score += 7.0F;
        }

        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        if (modifiers != null && !modifiers.modifiers().isEmpty()) {
            score = 0.0F;
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.ARMOR)) {
                    score += (float) (entry.modifier().value() * 5);
                } else if (entry.attribute().equals(EntityAttributes.ARMOR_TOUGHNESS)) {
                    score += (float) (entry.modifier().value() * 3);
                }
            }
        }

        ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        if (enchantments != null && !enchantments.isEmpty()) {
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(entry);
                if (entry.matchesKey(Enchantments.PROTECTION)) {
                    score += level * 3.0F;
                } else if (entry.matchesKey(Enchantments.FIRE_PROTECTION) || entry.matchesKey(Enchantments.BLAST_PROTECTION) || entry.matchesKey(Enchantments.PROJECTILE_PROTECTION)) {
                    score += level * 1.5F;
                } else if (entry.matchesKey(Enchantments.UNBREAKING)) {
                    score += level * 1.0F;
                } else if (entry.matchesKey(Enchantments.THORNS)) {
                    score += level * 1.0F;
                }
            }
        }

        float maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            score += (maxDamage - stack.getDamage()) / maxDamage * 0.1F;
        }

        return score;
    }

    public float getWeaponScore(ItemStack stack) {
        if (!isSword(stack)) return -1.0F;

        float score = 0.0F;
        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);

        if (modifiers != null) {
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.ATTACK_DAMAGE)) {
                    score += (float) entry.modifier().value();
                } else if (entry.attribute().equals(EntityAttributes.ATTACK_SPEED)) {
                    score += (float) (entry.modifier().value() * 0.5F);
                }
            }
        }

        ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        if (enchantments != null && !enchantments.isEmpty()) {
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(entry);
                if (entry.matchesKey(Enchantments.SHARPNESS)) {
                    score += level * 1.25F;
                } else if (entry.matchesKey(Enchantments.FIRE_ASPECT)) {
                    score += level * 1.0F;
                } else if (entry.matchesKey(Enchantments.KNOCKBACK)) {
                    score += level * 0.5F;
                } else if (entry.matchesKey(Enchantments.UNBREAKING)) {
                    score += level * 0.1F;
                }
            }
        }

        float maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            score += (maxDamage - stack.getDamage()) / maxDamage * 0.1F;
        }

        return score;
    }

    public float getToolScore(ItemStack stack) {
        if (!isTool(stack)) return -1.0F;

        float score = 0.0F;
        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        if (modifiers != null) {
            for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                if (entry.attribute().equals(EntityAttributes.ATTACK_DAMAGE)) {
                    score += (float) entry.modifier().value();
                } else if (entry.attribute().equals(EntityAttributes.ATTACK_SPEED)) {
                    score += (float) (entry.modifier().value() * 0.5F);
                }
            }
        }

        ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        if (enchantments != null && !enchantments.isEmpty()) {
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(entry);
                if (entry.matchesKey(Enchantments.EFFICIENCY)) {
                    score += level * 4.0F;
                } else if (entry.matchesKey(Enchantments.FORTUNE) || entry.matchesKey(Enchantments.SILK_TOUCH)) {
                    score += level * 3.0F;
                } else if (entry.matchesKey(Enchantments.UNBREAKING)) {
                    score += level * 1.0F;
                } else if (entry.matchesKey(Enchantments.SHARPNESS)) {
                    score += level * 1.25F;
                }
            }
        }

        float maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            score += (maxDamage - stack.getDamage()) / maxDamage * 0.1F;
        }

        return score;
    }

    private float getFoodScore(ItemStack stack) {
        if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            return 1000.0F + stack.getCount();
        } else if (stack.getItem() == Items.GOLDEN_APPLE) {
            return 500.0F + stack.getCount();
        } else {
            return (float) stack.getCount();
        }
    }

    private boolean isBetterSlot(float score, int slot, float bestScore, int bestSlot, int targetSlot) {
        if (score > bestScore) return true;
        if (score < bestScore) return false;
        if (slot == targetSlot) return true;
        if (bestSlot == targetSlot) return false;
        boolean isHotbar = slot >= 36 && slot <= 44;
        boolean bestIsHotbar = bestSlot >= 36 && bestSlot <= 44;
        return isHotbar && !bestIsHotbar;
    }

    public int getBestArmorSlot(EquipmentSlot equipmentSlot) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        int targetSlot = switch (equipmentSlot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && isArmor(slot.getStack())) {
                    ItemStack stack = slot.getStack();
                    EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                    if (equippable != null && equippable.slot() == equipmentSlot) {
                        float score = getArmorScore(stack);
                        if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                            bestScore = score;
                            bestSlot = slot.id;
                        }
                    }
                }
            }
        }
        return bestSlot;
    }

    public int getBestWeaponSlot(int targetSlot) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && isSword(slot.getStack())) {
                    float score = getWeaponScore(slot.getStack());
                    if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                        bestScore = score;
                        bestSlot = slot.id;
                    }
                }
            }
        }
        return bestSlot;
    }

    public int getBestWeaponSlot() {
        return getBestWeaponSlot(-1);
    }

    public int getBestToolSlot(TagKey<Item> toolTag, int targetSlot) {
        if (mc.player == null || toolTag == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && slot.getStack().isIn(toolTag)) {
                    float score = getToolScore(slot.getStack());
                    if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                        bestScore = score;
                        bestSlot = slot.id;
                    }
                }
            }
        }
        return bestSlot;
    }

    public int getBestToolSlot(TagKey<Item> toolTag) {
        return getBestToolSlot(toolTag, -1);
    }

    public int getBestBlockSlot(int targetSlot) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && slot.getStack().getItem() instanceof BlockItem blockItem) {
                    if (BlockUtil.isPlaceable(blockItem.getBlock())) {
                        float score = slot.getStack().getCount();
                        if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                            bestScore = score;
                            bestSlot = slot.id;
                        }
                    }
                }
            }
        }
        return bestSlot;
    }

    public int getBestPearlSlot(int targetSlot) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && slot.getStack().getItem() instanceof EnderPearlItem) {
                    float score = slot.getStack().getCount();
                    if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                        bestScore = score;
                        bestSlot = slot.id;
                    }
                }
            }
        }
        return bestSlot;
    }

    public int getBestProjectileSlot(int targetSlot) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && isProjectile(slot.getStack())) {
                    float score = slot.getStack().getCount();
                    if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                        bestScore = score;
                        bestSlot = slot.id;
                    }
                }
            }
        }
        return bestSlot;
    }

    public int getBestFoodSlot(int targetSlot) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestScore = -1.0F;

        InventoryManager inventoryManager = instance.getModuleManager().getModule(InventoryManager.class);
        boolean keepFood = inventoryManager.getKeepFood().getValue();

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && slot.getStack().contains(DataComponentTypes.FOOD)) {
                    ItemStack stack = slot.getStack();

                    if (!isGoldenApple(stack) && !keepFood) continue;

                    float score = getFoodScore(stack);
                    if (isBetterSlot(score, slot.id, bestScore, bestSlot, targetSlot)) {
                        bestScore = score;
                        bestSlot = slot.id;
                    }
                }
            }
        }
        return bestSlot;
    }

    private float getBestArmorScore(EquipmentSlot slot) {
        if (mc.player == null) return -1;

        float equippedScore = getArmorScore(mc.player.getEquippedStack(slot));

        float inventoryBestScore = -1.0F;
        int bestSlot = getBestArmorSlot(slot);
        if (bestSlot != -1) {
            inventoryBestScore = getArmorScore(mc.player.currentScreenHandler.getSlot(bestSlot).getStack());
        }

        return Math.max(equippedScore, inventoryBestScore);
    }

    private float getBestWeaponScore() {
        if (mc.player == null) return -1;
        int bestSlot = getBestWeaponSlot();
        if (bestSlot == -1) return -1.0F;
        return getWeaponScore(mc.player.currentScreenHandler.getSlot(bestSlot).getStack());
    }

    private float getBestToolScore(TagKey<Item> toolTag) {
        if (mc.player == null || toolTag == null) return -1;
        int bestSlot = getBestToolSlot(toolTag);
        if (bestSlot == -1) return -1.0F;
        return getToolScore(mc.player.currentScreenHandler.getSlot(bestSlot).getStack());
    }

    private int getPlayerBlockCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && slot.getStack().getItem() instanceof BlockItem blockItem) {
                    if (BlockUtil.isPlaceable(blockItem.getBlock())) {
                        count += slot.getStack().getCount();
                    }
                }
            }
        }
        return count;
    }

    private int getPlayerFoodCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                if (slot.hasStack() && slot.getStack().contains(DataComponentTypes.FOOD)) {
                    ItemStack stack = slot.getStack();
                    if (!isGoldenApple(stack)) {
                        count += stack.getCount();
                    }
                }
            }
        }
        return count;
    }

    public boolean isArmor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && isArmorSlot(equippable.slot());
    }

    private boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    public boolean isSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
    }

    public boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SHOVELS);
    }

    private TagKey<Item> getToolTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.isIn(ItemTags.PICKAXES)) return ItemTags.PICKAXES;
        if (stack.isIn(ItemTags.AXES)) return ItemTags.AXES;
        if (stack.isIn(ItemTags.SHOVELS)) return ItemTags.SHOVELS;
        return null;
    }

    private boolean isProjectile(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.getItem() instanceof SnowballItem || stack.getItem() instanceof EggItem);
    }

    private boolean isGoldenApple(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE);
    }
}
