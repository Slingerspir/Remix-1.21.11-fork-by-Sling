package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.SlowEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.util.network.PacketUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Queue;

public class NoSlowDown extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Heypixel2");
    private final BoolValue keepSprint = new BoolValue("Keep Sprint", true);

    // ===== Heypixel2（nilore NoSlow 移植）状态 =====
    private enum Step { NONE, ARMED, EATING }

    private Step step = Step.NONE;
    private final Queue<Packet<?>> h2Queue = new ArrayDeque<>();
    private boolean swapInArmed;
    private boolean hasSwapped;
    private boolean bowActive;
    private boolean bowDelay;
    private boolean resFoodSwap;
    private int noUseTicks;

    public NoSlowDown() {
        super("NoSlowDown", Category.Move);
    }

    @Override
    public void onDisable() {
        if (mode.is("Heypixel2")) {
            release();
            restoreUseKeyState();
        }
    }

    @EventTarget
    public void onSlow(SlowEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Heypixel2")) {
            ItemStack stack = mc.player.getActiveItem();
            if (!mc.player.isUsingItem() || stack.isEmpty()) return;

            boolean eatOrDrink = isEatOrDrink(stack);
            boolean shield = stack.getUseAction() == UseAction.BLOCK;
            if (eatOrDrink || shield || bowActive) {
                event.setCancelled(true);
                if (keepSprint.getValue()) mc.player.setSprinting(true);
            }
            return;
        }

        if (mc.player.isUsingItem() && !mc.player.getActiveItem().isEmpty()) {
            event.setCancelled(true);

            if (keepSprint.getValue()) {
                mc.player.setSprinting(true);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!mode.is("Heypixel2") || mc.player == null || mc.world == null) return;
        h2Tick();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!mode.is("Heypixel2") || mc.player == null || mc.world == null) return;
        h2Packet(event);
    }

    // =====================================================================
    // Heypixel2：C0F 换手状态机 + 食物换手 + 弓延迟
    // =====================================================================

    private void h2Tick() {
        // Scaffold / 末影珍珠时立即释放
        if ((step != Step.NONE || bowActive)
                && (getModule(Scaffold.class).isEnabled() || mc.player.getMainHandStack().isOf(Items.ENDER_PEARL))) {
            release();
            return;
        }

        if (step != Step.NONE && step != Step.EATING) {
            releaseUseKey();
        }

        if (step == Step.NONE) {
            ItemStack stack = mc.player.getActiveItem();
            if (mc.player.isUsingItem() && mc.options.useKey.isPressed()
                    && !stack.isEmpty() && isUsable(stack.getUseAction())) {
                if (isLookingAtInteractableBlock()) return;

                Hand hand = mc.player.getActiveHand();
                if (hand == Hand.OFF_HAND || (hand == Hand.MAIN_HAND && mc.player.getOffHandStack().isEmpty())) {
                    step = Step.ARMED;
                    releaseUseKey();

                    if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                        PacketUtil.sendPacketNoEvent(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    }
                }
            }
        } else if (step == Step.EATING) {
            if (mc.player.isUsingItem()) {
                noUseTicks = 0;
            } else {
                noUseTicks++;
                if (noUseTicks >= 5) release();
            }
        } else {
            noUseTicks = 0;
        }
    }

    private void h2Packet(PacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (event.getType() == PacketEvent.Type.Received) {
            if (shouldQueuePacket(packet)) {
                event.setCancelled(true);
                h2Queue.add(packet);
                return;
            }

            if (packet instanceof PlayerPositionLookS2CPacket) {
                release();
                return;
            }

            if (step == Step.NONE) return;

            if (packet instanceof CommonPingS2CPacket) {
                event.setCancelled(true);
                h2Queue.add(packet);
                if (step == Step.ARMED && !swapInArmed) {
                    swapInArmed = true;
                    hasSwapped = true;
                    sendSwapOffhand();
                }
                return;
            }

            if (packet instanceof ScreenHandlerSlotUpdateS2CPacket && step == Step.ARMED && swapInArmed) {
                swapInArmed = false;
                mc.options.useKey.setPressed(true);
                step = Step.EATING;
                return;
            }

            if (packet instanceof EntityVelocityUpdateS2CPacket velocity
                    && velocity.getEntityId() == mc.player.getId()) {
                event.setCancelled(true);
                h2Queue.add(packet);
            }
            return;
        }

        // 出站
        if (packet instanceof PlayerInteractItemC2SPacket use) {
            ItemStack stack = mc.player.getStackInHand(use.getHand());

            if (shouldResSwapFood(use.getHand(), stack)) {
                resFoodSwap = true;
                event.setCancelled(true);
                sendSwapOffhand();
                PacketUtil.sendPacketNoEvent(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, use.getSequence(),
                        mc.player.getYaw(), mc.player.getPitch()));
                step = Step.EATING;
                hasSwapped = true;
                noUseTicks = 0;
                return;
            }

            if (isBowLike(stack.getUseAction())) {
                handleBowUse(stack);
            }
        }
    }

    private void release() {
        if (step == Step.NONE && h2Queue.isEmpty() && !hasSwapped) return;

        step = Step.NONE;
        noUseTicks = 0;
        swapInArmed = false;
        resFoodSwap = false;
        bowActive = false;
        bowDelay = false;

        flushQueue();
        if (hasSwapped) {
            sendSwapOffhand();
            hasSwapped = false;
        }
    }

    private void flushQueue() {
        if (mc.player == null) {
            h2Queue.clear();
            return;
        }
        Packet<?> packet;
        while ((packet = h2Queue.poll()) != null) {
            try {
                PacketUtil.receivePacketNoEvent(packet);
            } catch (Throwable ignored) {
                h2Queue.clear();
                break;
            }
        }
    }

    private void sendSwapOffhand() {
        PacketUtil.sendPacketNoEvent(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
    }

    private boolean shouldQueuePacket(Packet<?> packet) {
        if (!bowDelay) return false;

        if (packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof GameJoinS2CPacket
                || packet instanceof PlayerRespawnS2CPacket) {
            bowDelay = false;
            flushQueue();
            return false;
        }

        if (packet instanceof EntityStatusS2CPacket status) {
            Entity entity = status.getEntity(mc.world);
            if (entity != mc.player || status.getStatus() != 2) return false;
        }

        return isBlinkable(packet);
    }

    private boolean isBlinkable(Packet<?> packet) {
        if (packet instanceof KeepAliveS2CPacket || packet instanceof CommonPingS2CPacket) return true;

        if (packet instanceof EntityVelocityUpdateS2CPacket velocity
                && mc.player != null && velocity.getEntityId() == mc.player.getId()) {
            return true;
        }

        if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slot) {
            return slot.getSlot() == 45 || slot.getSyncId() == 0;
        }

        if (packet instanceof EntityEquipmentUpdateS2CPacket equipment) {
            for (Pair<EquipmentSlot, ItemStack> entry : equipment.getEquipmentList()) {
                if (entry.getFirst() == EquipmentSlot.OFFHAND) return true;
            }
        }
        return false;
    }

    private void handleBowUse(ItemStack stack) {
        UseAction action = stack.getUseAction();
        if (stack.getItem() instanceof CrossbowItem crossbow && CrossbowItem.isCharged(stack)) return;
        if (isStew(stack)) return;
        if (isLookingAtInteractableBlock()) return;
        if (!isBowLike(action)) return;

        bowActive = true;
        bowDelay = true;
    }

    private boolean shouldResSwapFood(Hand hand, ItemStack stack) {
        if (hand != Hand.MAIN_HAND || resFoodSwap) return false;
        if (stack.isEmpty() || !isEatOrDrink(stack)) return false;
        if (stack.getUseAction() == UseAction.BLOCK || isBowLike(stack.getUseAction())) return false;
        if (isStew(stack)) return false;
        if (isLookingAtInteractableBlock()) return false;
        if (!mc.player.getOffHandStack().isEmpty()) return false;
        return canSwapHands();
    }

    private boolean canSwapHands() {
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        if (main.isEmpty() || off.isEmpty()) return true;
        if (main.isOf(Items.ENCHANTED_GOLDEN_APPLE) && off.isOf(Items.GOLDEN_APPLE)) return false;
        if (main.isOf(Items.GOLDEN_APPLE) && off.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return false;
        return main.getItem() != off.getItem();
    }

    private boolean isEatOrDrink(ItemStack stack) {
        if (stack.isEmpty()) return false;
        UseAction action = stack.getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK || stack.getItem() instanceof PotionItem;
    }

    private boolean isUsable(UseAction action) {
        return action == UseAction.EAT || action == UseAction.DRINK || action == UseAction.SPEAR;
    }

    private boolean isBowLike(UseAction action) {
        return action == UseAction.BOW || action == UseAction.CROSSBOW || action == UseAction.SPEAR;
    }

    private boolean isStew(ItemStack stack) {
        return stack.isOf(Items.MUSHROOM_STEW) || stack.isOf(Items.RABBIT_STEW)
                || stack.isOf(Items.BEETROOT_SOUP) || stack.isOf(Items.SUSPICIOUS_STEW);
    }

    private boolean isLookingAtInteractableBlock() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return false;
        if (mc.world == null) return false;

        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.FENCE_GATES)
                || state.isIn(BlockTags.BUTTONS) || state.isIn(BlockTags.TRAPDOORS)
                || state.isIn(BlockTags.ANVIL) || state.isIn(BlockTags.BEDS)) {
            return true;
        }

        return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.FURNACE)
                || state.isOf(Blocks.BLAST_FURNACE) || state.isOf(Blocks.SMOKER) || state.isOf(Blocks.BARREL)
                || state.isOf(Blocks.ENDER_CHEST) || state.isOf(Blocks.BREWING_STAND) || state.isOf(Blocks.HOPPER)
                || state.isOf(Blocks.DISPENSER) || state.isOf(Blocks.DROPPER) || state.isOf(Blocks.JUKEBOX)
                || state.isOf(Blocks.NOTE_BLOCK) || state.isOf(Blocks.LEVER) || state.isOf(Blocks.REPEATER)
                || state.isOf(Blocks.COMPARATOR) || state.isOf(Blocks.DAYLIGHT_DETECTOR) || state.isOf(Blocks.CAKE)
                || state.isOf(Blocks.COMPOSTER) || state.isOf(Blocks.BEEHIVE) || state.isOf(Blocks.BEE_NEST)
                || state.isOf(Blocks.RESPAWN_ANCHOR) || state.isOf(Blocks.GRINDSTONE) || state.isOf(Blocks.STONECUTTER)
                || state.isOf(Blocks.CARTOGRAPHY_TABLE) || state.isOf(Blocks.LOOM) || state.isOf(Blocks.SMITHING_TABLE)
                || state.isOf(Blocks.LECTERN) || state.isOf(Blocks.BELL) || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.CRAFTING_TABLE) || state.isOf(Blocks.ENCHANTING_TABLE);
    }

    private void releaseUseKey() {
        mc.options.useKey.setPressed(false);
        while (mc.options.useKey.wasPressed()) {
            // drain queued clicks
        }
    }

    private void restoreUseKeyState() {
        if (mc.player == null) return;
        KeyBinding useKey = mc.options.useKey;
        InputUtil.Key key = InputUtil.fromTranslationKey(useKey.getBoundKeyTranslationKey());
        long handle = mc.getWindow().getHandle();
        boolean down = key.getCategory() == InputUtil.Type.MOUSE
                ? GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
        useKey.setPressed(down);
    }
}
