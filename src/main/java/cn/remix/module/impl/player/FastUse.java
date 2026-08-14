package cn.remix.module.impl.player;

import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.BlockUtil;
import lombok.Getter;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;

@Getter
public final class FastUse extends Module {
    private final NumberValue cooldown = new NumberValue("Cooldown", 1, 0, 4, 1);
    private final BoolValue ignorePearls = new BoolValue("Ignore Pearls", true);

    public FastUse() {
        super("FastUse", Category.Player);
    }

    public int getItemUseCooldown(ItemStack itemStack) {
        if (mc.player == null) return 4;

        ItemStack mainStack = mc.player.getMainHandStack();
        ItemStack offStack = mc.player.getOffHandStack();

        if (!mainStack.isEmpty()) {
            if (mainStack.getItem() instanceof BlockItem blockItem
                    && (isInteractable(blockItem.getBlock()) || isClickable(blockItem.getBlock()))) {
                return 4;
            }
            if (ignorePearls.getValue() && mainStack.isOf(Items.ENDER_PEARL)) {
                return 4;
            }
        }

        if (ignorePearls.getValue() && offStack.isOf(Items.ENDER_PEARL) && !isUsable(mainStack.getItem())) {
            return 4;
        }

        return getCooldownValue();
    }

    public int getCooldownValue() {
        return cooldown.getValue().intValue();
    }

    private boolean isClickable(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof ButtonBlock
                || block instanceof AbstractPressurePlateBlock
                || block instanceof BlockWithEntity
                || block instanceof BedBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof EnchantingTableBlock
                || block instanceof TrapdoorBlock;
    }

    private boolean isInteractable(Block block) {
        return block instanceof AbstractFurnaceBlock
                || block instanceof BarrelBlock
                || block instanceof BeaconBlock
                || block instanceof BellBlock
                || block instanceof BrewingStandBlock
                || block instanceof CakeBlock
                || block instanceof CampfireBlock
                || block instanceof ChestBlock
                || block instanceof ChiseledBookshelfBlock
                || block instanceof ComposterBlock
                || block instanceof CrafterBlock
                || block instanceof DaylightDetectorBlock
                || block instanceof DecoratedPotBlock
                || block instanceof DispenserBlock
                || block instanceof DoorBlock
                || block instanceof DropperBlock
                || block instanceof EnderChestBlock
                || block instanceof FenceGateBlock
                || block instanceof FlowerPotBlock
                || block instanceof HopperBlock
                || block instanceof JukeboxBlock
                || block instanceof LeverBlock
                || block instanceof NoteBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof SmithingTableBlock
                || block instanceof TrapdoorBlock;
    }

    private boolean isUsable(Item item) {
        ItemStack stack = item.getDefaultStack();
        return stack.contains(DataComponentTypes.EQUIPPABLE)
                || stack.contains(DataComponentTypes.FOOD)
                || item instanceof SnowballItem
                || item instanceof EnderEyeItem
                || item instanceof EnderPearlItem
                || item instanceof FireChargeItem
                || item instanceof WritableBookItem
                || item instanceof FlintAndSteelItem
                || item instanceof BowItem
                || item instanceof WrittenBookItem
                || item instanceof TridentItem
                || item instanceof CrossbowItem
                || item instanceof PotionItem
                || item instanceof BlockItem
                || item instanceof EntityBucketItem
                || item instanceof EndCrystalItem
                || item instanceof SpawnEggItem
                || item instanceof ExperienceBottleItem
                || item instanceof FireworkRocketItem
                || item instanceof EmptyMapItem
                || item instanceof FishingRodItem
                || item instanceof CompassItem
                || item instanceof HoeItem
                || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof SpyglassItem;
    }
}
