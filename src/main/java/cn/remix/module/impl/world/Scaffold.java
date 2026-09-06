package cn.remix.module.impl.world;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.LivingUpdateEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.management.RotationManager;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.animation.Easing;
import cn.remix.util.animation.EasingAnimation;
import cn.remix.util.Util;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.*;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

@Getter
public class Scaffold extends Module {
    public static NumberValue delay = new NumberValue("Delay", 0, 0, 200, 10);
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Telly Bridge", "Native", "Heypixel");
    private final NumberValue tellyTick = new NumberValue("Telly Tick", 1, 1, 5, 1, () -> !mode.is("Normal"));
    private final ModeValue rotationMode = new ModeValue("Rotation Mode", "Normal", "Normal", "Facing", "Hit Vec", "Nearest", "Hypixel");
    private final NumberValue shrink = new NumberValue("Shrink", .1f, 0, .45f, .01f, () -> rotationMode.is("Nearest") || rotationMode.is("Hypixel"));
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 180, 0, 180, 5);
    private final ModeValue towerMode = new ModeValue("Tower Mode", "None", "None", "Vanilla");
    public static BoolValue downwards = new BoolValue("Downwards", false);
    private final BoolValue autoJump = new BoolValue("Auto Jump", false);
    private final BoolValue sprint = new BoolValue("Sprint", false);
    private final BoolValue rayCast = new BoolValue("Ray Cast", false);
    private final BoolValue maxStack = new BoolValue("Max Stack", false);
    private final BoolValue itemSpoof = new BoolValue("Item Spoof", false);
    private final BoolValue noSwing = new BoolValue("No Swing", false);
    private final BoolValue movementFix = new BoolValue("Movement Fix", false);
    private final BoolValue hud = new BoolValue("HUD", true);
    private final NumberValue hudOpacity = new NumberValue("HUD Opacity", 180, 0, 255, 5, hud::getValue);
    private final BoolValue renderBlock = new BoolValue("Render Block", true);
    private final ColorValue renderBlockColorA = new ColorValue("Render Block Color A", new Color(98, 255, 76), renderBlock::getValue);
    private final ColorValue renderBlockColorB = new ColorValue("Render Block Color B", new Color(76, 190, 255), renderBlock::getValue);
    private final NumberValue renderBlockApproach = new NumberValue("Render Block Approach", 0.35f, 0.05f, 1.0f, 0.05f, renderBlock::getValue);
    private final EasingAnimation blockSlideAnimation = new EasingAnimation(Easing.EASE_OUT_CUBIC, 220);
    private final TimerUtil delayTimer = new TimerUtil();
    private boolean canRotation;
    private boolean canPlace;
    private int oldSlot;
    private double keepYCoord;
    private float[] rotations;
    private PlaceInfo data;
    private Vec3d renderBlockPos;
    private BlockPos lastRenderBlock;
    private long lastRenderBlockTime;
    private long lastRenderFrameTime;
    private int displayedBlocks = -1;
    private int previousBlocks = -1;
    private int blocksPlaced;
    private int bps;
    private long lastBpsReset = System.currentTimeMillis();

    public Scaffold() {
        super("Scaffold", Category.World);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        oldSlot = mc.player.getInventory().getSelectedSlot();
        canPlace = false;
        data = null;
        renderBlockPos = null;
        lastRenderBlock = null;
        lastRenderBlockTime = 0L;
        lastRenderFrameTime = 0L;
    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.world == null) return;

        if (itemSpoof.getValue()) {
            ItemSpoofUtil.stopSpoof();
        }

        mc.player.getInventory().setSelectedSlot(oldSlot);
        canPlace = false;
        data = null;
        renderBlockPos = null;
        lastRenderBlock = null;
        lastRenderBlockTime = 0L;
        lastRenderFrameTime = 0L;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!renderBlock.getValue() || mc.player == null || mc.world == null) {
            renderBlockPos = null;
            lastRenderBlock = null;
            return;
        }

        long now = System.currentTimeMillis();
        long delta = lastRenderFrameTime == 0L ? 16L : Math.min(100L, now - lastRenderFrameTime);
        lastRenderFrameTime = now;
        BlockPos currentTarget = getRenderBlockPos();
        if (currentTarget != null) {
            lastRenderBlock = currentTarget;
            lastRenderBlockTime = now;
        }

        BlockPos target = currentTarget != null ? currentTarget : lastRenderBlock;
        if (target == null) {
            renderBlockPos = null;
            return;
        }

        Vec3d targetVec = new Vec3d(target.getX(), target.getY(), target.getZ());
        if (renderBlockPos == null) {
            renderBlockPos = targetVec;
        } else {
            double rate = 1.0 - Math.pow(1.0 - renderBlockApproach.getValue(), delta / 16.6667);
            renderBlockPos = new Vec3d(
                    renderBlockPos.x + (targetVec.x - renderBlockPos.x) * rate,
                    renderBlockPos.y + (targetVec.y - renderBlockPos.y) * rate,
                    renderBlockPos.z + (targetVec.z - renderBlockPos.z) * rate
            );
        }

        float fade = currentTarget != null ? 1.0f : 1.0f - Math.clamp((now - lastRenderBlockTime) / 500.0f, 0.0f, 1.0f) * 0.5f;
        Render3D.drawBox(event.getMatrixStack(), renderBlockPos, ColorUtil.applyAlpha(getRenderBlockColor(now), Math.round(70.0f * fade)));
    }

    public void renderHud(DrawContext context) {
        getAnimation().run(isEnabled() && hud.getValue() ? 1 : 0);
        float alpha = getAnimation().getValue().floatValue();
        if (alpha <= 0.01f || mc.player == null) return;

        int blocks = getBlockCount();
        updateBlockSlide(blocks);
        TrueTypeFont font = instance.getFontManager().getFont(16);
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int alphaInt = Math.round(hudOpacity.getValue() * alpha);
        float x = sw / 2.0f + 10.0f;
        float y = sh / 2.0f + 6.0f;
        String suffix = " blocks left";

        blockSlideAnimation.run(1);
        float slide = blockSlideAnimation.getValue().floatValue();
        String oldDigits = String.format("%3d", Math.max(0, previousBlocks));
        String newDigits = String.format("%3d", Math.max(0, displayedBlocks));
        float digitWidth = font.getStringWidth("8");
        float digitHeight = font.getHeight();
        int color = ColorUtil.applyAlpha(getBlockCountColor(displayedBlocks), alphaInt);
        int oldColor = ColorUtil.applyAlpha(getBlockCountColor(previousBlocks), Math.round(alphaInt * (1.0f - slide)));

        for (int i = 0; i < newDigits.length(); i++) {
            float digitX = x + i * digitWidth;
            char oldChar = oldDigits.charAt(i);
            char newChar = newDigits.charAt(i);
            if (previousBlocks >= 0 && oldChar != newChar && slide < 1.0f) {
                float shift = digitHeight * slide;
                if (oldChar != ' ') {
                    font.drawStringWithShadow(context, String.valueOf(oldChar), digitX, y - shift, oldColor);
                }
                if (newChar != ' ') {
                    font.drawStringWithShadow(context, String.valueOf(newChar), digitX, y + digitHeight - shift, color);
                }
            } else if (newChar != ' ') {
                font.drawStringWithShadow(context, String.valueOf(newChar), digitX, y, color);
            }
        }

        font.drawStringWithShadow(context, suffix, x + digitWidth * 3.0f + 2.0f, y, color);

        font.drawStringWithShadow(context, bps + " b/s", x, y + digitHeight + 2.0f, ColorUtil.applyAlpha(-1, alphaInt));
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (autoJump.getValue() && MovementUtil.isMoving() && mc.player.isOnGround()) {
            event.setJumping(true);
        }

        if (isDownwards()) {
            event.setSneaking(false);
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.options.jumpKey.isPressed()) {
            if (towerMode.is("Vanilla")) {
                mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        setSuffix(mode.getValue());
        if (BlockUtil.getBlockSlot(maxStack.getValue()) == -1) {
            toggle();
            return;
        }

        if (mc.player.isOnGround()) {
            keepYCoord = Math.floor(mc.player.getY() - 1.0);
        }

        BlockPos targetBlock = BlockPos.ofFloored(mc.player.getX(), getYLevel() - (Scaffold.isDownwards() ? 1 : 0), mc.player.getZ());
        data = getBlockData(targetBlock);

        if (itemSpoof.getValue()) {
            ItemSpoofUtil.startSpoof(oldSlot);
        }

        mc.player.getInventory().setSelectedSlot(BlockUtil.getBlockSlot(maxStack.getValue()));

        switch (mode.getValue()) {
            case "Normal" -> canRotation = canPlace = true;
            case "Telly Bridge" -> canRotation = canPlace = Util.offGroundTicks >= tellyTick.getValue().intValue() || !MovementUtil.isMoving();
            case "Native", "Heypixel" -> {
                canRotation = false;
                canPlace = true;
            }
        }

        if (canPlace && data != null) {
            if ((mode.is("Native") || mode.is("Heypixel")) && !isAimed(data.blockPos())) {
                return;
            }

            boolean rayCast = true;
            if (this.rayCast.getValue()) {
                rayCast = RayCastUtil.overBlock(data.blockPos(), data.facing(), false);
            }

            if (rayCast) {
                if (delayTimer.hasTimeElapsed(delay.getValue())) {
                    place(data.blockPos(), data.facing(), getVec(data.blockPos(), data.facing()));
                    delayTimer.reset();
                }
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent e) {
        if (mc.player == null || mc.world == null || data == null) return;

        if (mode.is("Native")) {
            applyNativeRotation(RotationUtil.getRotations(data.blockPos()));
            rotations = null;
            return;
        }

        switch (rotationMode.getValue()) {
            case "Normal" -> rotations = RotationUtil.getRotations(data.blockPos());
            case "Hit Vec" -> rotations = RotationUtil.getRotations(getVec(data.blockPos(), data.facing()));
            case "Nearest", "Hypixel" -> rotations = new float[]{RotationUtil.getNearestRotation(data.blockPos(), data.facing(), RotationManager.currentRotations, shrink.getValue())[0], RotationUtil.getRotations(data.blockPos())[1]};
            case "Facing" -> rotations = RotationUtil.getRotations(data.blockPos(), data.facing());
        }
    }

    private void place(BlockPos pos, Direction facing, Vec3d hitVec) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, facing, pos, false)) == ActionResult.SUCCESS) {
            blocksPlaced++;
            long now = System.currentTimeMillis();
            if (now - lastBpsReset >= 1000) {
                bps = (int) Math.round(blocksPlaced * 1000.0 / (now - lastBpsReset));
                blocksPlaced = 0;
                lastBpsReset = now;
            }
            if (noSwing.getValue()) {
                PacketUtil.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void applyNativeRotation(float[] target) {
        if (target == null) return;

        float speed = rotationSpeed.getValue();
        float yawDelta = MathHelper.wrapDegrees(target[0] - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(target[1] - mc.player.getPitch());
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDelta, -speed, speed));
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(pitchDelta, -speed, speed), -90, 90));
    }

    private boolean isAimed(BlockPos pos) {
        float[] need = RotationUtil.getRotations(pos);
        if (need == null) return false;

        float yawDiff = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - need[0]));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(mc.player.getPitch() - need[1]));
        return yawDiff < 12 && pitchDiff < 12;
    }

    public double getYLevel() {
        if (mc.player == null) return 0.0;

        double posY = mc.player.getY();
        if (!autoJump.getValue()) return posY - 1.0;
        return posY - 1.0 >= keepYCoord && Math.max(posY, keepYCoord) - Math.min(posY, keepYCoord) <= 3.0 && !mc.options.jumpKey.isPressed() ? keepYCoord : posY - 1.0;
    }

    public PlaceInfo getBlockData(BlockPos belowBlockPos) {
        if (mc.player == null || mc.world == null) return null;
        if (!mc.world.getBlockState(belowBlockPos).isAir()) return null;

        final double reachSq = 4.5 * 4.5;
        final Vec3d eye = mc.player.getEyePos();

        PlaceInfo best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int sx = (x == 0 ? 1 : -1); sx <= 1; sx += 2) {
                    for (int sz = (z == 0 ? 1 : -1); sz <= 1; sz += 2) {
                        BlockPos blockPos = belowBlockPos.add(x * sx, 0, z * sz);
                        if (!mc.world.getBlockState(blockPos).isAir()) continue;

                        for (Direction direction : Direction.values()) {
                            if (!isDownwards() && direction == Direction.UP) continue;

                            BlockPos neighborPos = blockPos.offset(direction);
                            BlockState neighborState = mc.world.getBlockState(neighborPos);
                            if (neighborState.isReplaceable() || !neighborState.getFluidState().isEmpty()) continue;

                            Direction facing = direction.getOpposite();
                            Vec3d hitVec = new Vec3d(
                                    neighborPos.getX() + 0.5 + facing.getOffsetX() * 0.5,
                                    neighborPos.getY() + 0.5 + facing.getOffsetY() * 0.5,
                                    neighborPos.getZ() + 0.5 + facing.getOffsetZ() * 0.5
                            );

                            double distSq = eye.squaredDistanceTo(hitVec);
                            if (distSq > reachSq) continue;

                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                best = new PlaceInfo(neighborPos, facing);
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    public Vec3d getVec(BlockPos pos, Direction facing) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        switch (facing) {
            case NORTH -> z -= 0.5;
            case SOUTH -> z += 0.5;
            case WEST  -> x -= 0.5;
            case EAST  -> x += 0.5;
            case UP    -> y += 0.5;
        }
        return new Vec3d(x, y, z);
    }

    public int getRotationSpeed() {
        if (mc.player == null || mc.world == null) return 0;

        int speed = rotationSpeed.getValue().intValue();
        if (rotationMode.is("Hypixel")) {
            if (mc.options.jumpKey.isPressed() && !MovementUtil.movementInput()) {
                speed = rotationSpeed.getValue().intValue();
            } else if (mc.player.getMovement().y <= 0) {
                speed = rotationSpeed.getValue().intValue();
            } else  if (!canPlace) {
                speed = rotationSpeed.getValue().intValue();
            } else if (Util.offGroundTicks == tellyTick.getValue().intValue())
                speed = 120;
            else {
                speed = 35;
            }
        }
        return speed;
    }

    public static boolean isDownwards() {
        return downwards.getValue() && mc.options.sneakKey.isPressed();
    }

    private int getBlockCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && BlockUtil.isPlaceable(blockItem.getBlock())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int getBlockCountColor(int count) {
        if (count <= 0) return 0xFF7A1018;
        if (count < 10) return 0xFFFF3333;
        if (count < 20) return 0xFFFF9A26;
        if (count < 32) return 0xFFFFE066;
        return 0xFFFFFFFF;
    }

    private void updateBlockSlide(int blocks) {
        if (displayedBlocks == -1) {
            displayedBlocks = blocks;
            previousBlocks = blocks;
            blockSlideAnimation.setValue(1);
            return;
        }

        if (blocks != displayedBlocks) {
            previousBlocks = displayedBlocks;
            displayedBlocks = blocks;
            blockSlideAnimation.setValue(0);
            blockSlideAnimation.setStartValue(0);
            blockSlideAnimation.setDestinationValue(0);
            blockSlideAnimation.reset();
        }
    }

    private BlockPos getRenderBlockPos() {
        if (data == null) return null;
        return data.blockPos().offset(data.facing());
    }

    private int getRenderBlockColor(long now) {
        float wave = (float) ((Math.sin(now / 420.0) + 1.0) * 0.5);
        return ColorUtil.interpolate(renderBlockColorA.getValue().getRGB(), renderBlockColorB.getValue().getRGB(), wave);
    }

    public record PlaceInfo(BlockPos blockPos, Direction facing) {}
}
