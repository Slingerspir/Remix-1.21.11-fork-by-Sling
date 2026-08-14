package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.MovementUtil;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.world.GameMode;

import java.util.Random;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public final class NoFall extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Spoof", "Spoof", "No Ground", "Blink", "Verus", "Vulcan", "Vulcan 277", "Matrix New", "Watchdog A", "Watchdog B", "CubeCraft");
    private final NumberValue distanceThreshold = new NumberValue("Distance Threshold", 2.5, 1.0, 4.5, 0.25);
    private final NumberValue watchDogATimer = new NumberValue("Watchdog A Timer", 0.6f, 0.1f, 1.0f, 0.05f, () -> mode.is("Watchdog A"));
    private final BoolValue ignoreVoid = new BoolValue("Ignore Void", true);
    private final BoolValue disableInAdventure = new BoolValue("Disable In Adventure", true);
    private final BoolValue ignoreFallResistanceBlocks = new BoolValue("Ignore Fall Resistance Blocks", true);
    private final NumberValue blinkMaximumFallDistance = new NumberValue("Blink Maximum Fall Distance", 20.0f, 2.0f, 50.0f, 0.5f, () -> mode.is("Blink"));

    private final NumberValue cubecraftFallDistance = new NumberValue("CubeCraft Fall Distance", 3.2f, 1.0f, 5.0f, 0.1f, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftMinDelay = new NumberValue("CubeCraft Min Delay", 50, 0, 200, 10, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftMaxDelay = new NumberValue("CubeCraft Max Delay", 150, 0, 300, 10, () -> mode.is("CubeCraft"));
    private final BoolValue cubecraftRandomizeGround = new BoolValue("Randomize Ground", true, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftGroundChance = new NumberValue("Ground Chance", 80, 0, 100, 5, () -> mode.is("CubeCraft") && cubecraftRandomizeGround.getValue());
    private final BoolValue cubecraftSimulateStutter = new BoolValue("Simulate Stutter", true, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftStutterChance = new NumberValue("Stutter Chance", 8, 0, 25, 5, () -> mode.is("CubeCraft") && cubecraftSimulateStutter.getValue());
    private final BoolValue cubecraftSmoothFall = new BoolValue("Smooth Fall", true, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftSmoothFactor = new NumberValue("Smooth Factor", 0.7f, 0.3f, 1.0f, 0.05f, () -> mode.is("CubeCraft") && cubecraftSmoothFall.getValue());
    private final BoolValue cubecraftAntiRubberband = new BoolValue("Anti Rubberband", true, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftRubberbandDelay = new NumberValue("Rubberband Delay", 300, 0, 600, 20, () -> mode.is("CubeCraft") && cubecraftAntiRubberband.getValue());
    private final BoolValue cubecraftPacketLoss = new BoolValue("Simulate Packet Loss", false, () -> mode.is("CubeCraft"));
    private final NumberValue cubecraftLossChance = new NumberValue("Loss Chance", 5, 0, 15, 5, () -> mode.is("CubeCraft") && cubecraftPacketLoss.getValue());

    private double initialY;
    private double dynamic = 3.0;
    private boolean falling;
    private int ticks;
    private boolean vulCanNoFall;
    private boolean vulCantNoFall;
    private boolean vulCanNextSpoof;
    private boolean blinkFall;

    private final TimerUtil cubecraftTimer = new TimerUtil();
    private final TimerUtil cubecraftDelayTimer = new TimerUtil();
    private final TimerUtil cubecraftStutterTimer = new TimerUtil();
    private final Random random = new Random();
    private double cubecraftCurrentFallDistance = 0;
    private boolean cubecraftIsStuttering = false;
    private int cubecraftStutterTicks = 0;
    private boolean cubecraftHasSpoofed = false;
    private double cubecraftLastY = 0;
    private int cubecraftPacketCount = 0;

    public NoFall() {
        super("NoFall", Category.Move);
    }

    @Override
    public void onEnable() {
        resetState();
        cubecraftTimer.reset();
        cubecraftDelayTimer.reset();
        cubecraftStutterTimer.reset();
        cubecraftCurrentFallDistance = 0;
        cubecraftIsStuttering = false;
        cubecraftStutterTicks = 0;
        cubecraftHasSpoofed = false;
        cubecraftLastY = 0;
        cubecraftPacketCount = 0;
    }

    @Override
    public void onDisable() {
        resetState();
        stopBlink();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        String currentMode = mode.getValue();
        setSuffix(currentMode);

        ticks++;
        if (ticks >= 60) {
            ticks = 0;
        }

        if (shouldSkip()) {
            if (currentMode.equals("Blink") && (mc.player.isOnGround() || shouldSkipPacketModes())) {
                stopBlink();
            }
            if (currentMode.equals("CubeCraft")) {
                resetCubecraftState();
            }
            initialY = mc.player.getY();
            dynamic = 3.0;
            falling = false;
            return;
        }

        if (currentMode.equals("CubeCraft")) {
            handleCubecraftUpdate();
        } else {
            handleUpdateMode();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent motionEvent) {
        if (mc.player == null || mc.world == null || motionEvent.isPost()) return;

        String currentMode = mode.getValue();
        setSuffix(currentMode);

        if (currentMode.equals("No Ground") && !shouldSkip()) {
            motionEvent.setOnGround(false);
        }

        if (currentMode.equals("CubeCraft") && !shouldSkip()) {
            handleCubecraftMotion(motionEvent);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        if (mc.player == null || mc.world == null || packetEvent.getType() != PacketEvent.Type.Send) return;
        if (!(packetEvent.getPacket() instanceof PlayerMoveC2SPacket packet)) return;
        if (shouldSkipPacketModes()) return;

        String currentMode = mode.getValue();
        PlayerMoveC2SPacketAccessor accessor = (PlayerMoveC2SPacketAccessor) packet;

        if (currentMode.equals("CubeCraft")) {
            handleCubecraftPacket(packet, accessor);
            return;
        }

        if (currentMode.equals("Blink") && blinkFall) {
            accessor.setOnGround(true);
        } else if (currentMode.equals("Spoof")) {
            if (mc.player.getVelocity().y <= -0.5 || mc.player.fallDistance > distanceThreshold.getValue()) {
                accessor.setOnGround(true);
            }
        } else if (currentMode.equals("Verus")) {
            if (mc.player.fallDistance > 3.35f) {
                spoofGroundAndStopFall(accessor);
            }
        } else if (currentMode.equals("Vulcan")) {
            accessor.setOnGround(true);
            double y = Math.round(mc.player.getY() * 2.0) / 2.0;
            accessor.setY(y);
            mc.player.setPosition(mc.player.getX(), packet.getY(mc.player.getY()), mc.player.getZ());
        } else if (currentMode.equals("Vulcan 277")) {
            if (mc.player.fallDistance > 7.0f) {
                spoofGroundAndStopFall(accessor);
            }
        } else if (currentMode.equals("Matrix New")) {
            handleMatrixPacket(packet, accessor);
        }
    }


    private void handleCubecraftUpdate() {
        if (mc.player == null) return;

        double fallDistance = mc.player.fallDistance;
        double motionY = mc.player.getVelocity().y;
        double currentY = mc.player.getY();

        if (motionY < 0) {
            cubecraftCurrentFallDistance += Math.abs(motionY);
        } else {
            cubecraftCurrentFallDistance = 0;
        }

        if (cubecraftSmoothFall.getValue()) {
            float smoothFactor = cubecraftSmoothFactor.getValue();
            cubecraftCurrentFallDistance = cubecraftCurrentFallDistance * smoothFactor +
                    Math.abs(motionY) * (1 - smoothFactor);
        }

        if (cubecraftAntiRubberband.getValue()) {
            if (Math.abs(currentY - cubecraftLastY) > 0.5 && cubecraftLastY != 0) {
                if (!cubecraftDelayTimer.hasTimeElapsed(cubecraftRubberbandDelay.getValue().longValue())) {
                    return;
                }
            }
        }
        cubecraftLastY = currentY;

        if (cubecraftSimulateStutter.getValue() && !cubecraftIsStuttering) {
            if (random.nextInt(100) < cubecraftStutterChance.getValue().intValue()) {
                cubecraftIsStuttering = true;
                cubecraftStutterTicks = 1 + random.nextInt(3);
                cubecraftStutterTimer.reset();
            }
        }

        if (cubecraftIsStuttering) {
            if (cubecraftStutterTicks > 0) {
                cubecraftStutterTicks--;
                return;
            } else {
                cubecraftIsStuttering = false;
            }
        }

        float threshold = cubecraftFallDistance.getValue();
        boolean shouldSpoof = cubecraftCurrentFallDistance > threshold || fallDistance > threshold;

        if (shouldSpoof && !cubecraftHasSpoofed) {
            long min = cubecraftMinDelay.getValue().longValue();
            long max = cubecraftMaxDelay.getValue().longValue();
            long delay = min + (max > min ? random.nextInt((int)(max - min)) : 0);

            if (cubecraftTimer.hasTimeElapsed(delay)) {
                spoofCubecraftFall();
                cubecraftHasSpoofed = true;
                cubecraftTimer.reset();
            }
        }

        if (mc.player.isOnGround() && cubecraftHasSpoofed) {
            resetCubecraftState();
        }

        if (cubecraftHasSpoofed && cubecraftTimer.hasTimeElapsed(1000 + random.nextInt(500))) {
            resetCubecraftState();
        }
    }

    private void handleCubecraftMotion(MotionEvent motionEvent) {
        if (mc.player == null) return;

        if (cubecraftRandomizeGround.getValue()) {
            if (random.nextInt(100) < cubecraftGroundChance.getValue().intValue()) {
                motionEvent.setOnGround(mc.player.isOnGround());
            } else {
                motionEvent.setOnGround(!mc.player.isOnGround());
            }
        }

        if (!mc.player.isOnGround() && mc.player.fallDistance > 1.0f) {
            if (random.nextInt(100) < 5) {
                motionEvent.setOnGround(false);
            }
        }
    }

    private void handleCubecraftPacket(PlayerMoveC2SPacket packet, PlayerMoveC2SPacketAccessor accessor) {
        if (mc.player == null) return;

        if (cubecraftPacketLoss.getValue()) {
            if (random.nextInt(100) < cubecraftLossChance.getValue().intValue()) {
                return;
            }
        }

        if (cubecraftHasSpoofed) {
            accessor.setOnGround(true);

            double yOffset = (random.nextDouble() - 0.5) * 0.001;
            accessor.setY(packet.getY(mc.player.getY()) + yOffset);

            mc.player.fallDistance = 0.0f;

            cubecraftPacketCount++;
            if (cubecraftPacketCount > 3) {
                cubecraftPacketCount = 0;
                cubecraftDelayTimer.reset();
            }
        }
    }

    private void spoofCubecraftFall() {
        if (mc.player == null) return;

        mc.player.fallDistance = 0.0f;

        mc.player.setVelocity(
                mc.player.getVelocity().x * (0.9 + random.nextDouble() * 0.2),
                random.nextDouble() * 0.1,
                mc.player.getVelocity().z * (0.9 + random.nextDouble() * 0.2)
        );

        PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));

        if (random.nextInt(100) < 30) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }
    }

    private void resetCubecraftState() {
        cubecraftCurrentFallDistance = 0;
        cubecraftHasSpoofed = false;
        cubecraftIsStuttering = false;
        cubecraftStutterTicks = 0;
        cubecraftPacketCount = 0;
        cubecraftTimer.reset();
        cubecraftDelayTimer.reset();
    }


    private void handleUpdateMode() {
        if (mc.player == null) return;

        if (mode.is("Blink")) {
            handleBlinkUpdate();
        } else if (blinkFall) {
            stopBlink();
        }

        if (mode.is("Vulcan")) {
            handleVulcanUpdate();
        }

        if (mc.player.fallDistance > distanceThreshold.getValue()) {
            falling = true;
        }

        double motionY = mc.player.getVelocity().y;
        double predictedY = mc.player.getY() + motionY;
        double distanceFallen = initialY - predictedY;

        if (motionY >= -1.0) {
            dynamic = 3.0;
        } else if (motionY < -2.0) {
            dynamic = 5.0;
        } else {
            dynamic = 4.0;
        }

        if (!falling) {
            return;
        }

        if (mode.is("No Ground")) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
        } else if (mode.is("Watchdog A")) {
            if (distanceFallen >= dynamic) {
                PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                initialY = mc.player.getY();
            }
        } else if (mode.is("Watchdog B")) {
            if (distanceFallen >= 3.0) {
                PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                initialY = mc.player.getY();
            }
        }
    }

    private void handleBlinkUpdate() {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isOnGround()) {
            stopBlink();
            return;
        }

        boolean damagingFallAhead = !mc.world.getCollisions(
                mc.player,
                mc.player.getBoundingBox().stretch(0.0, -distanceThreshold.getValue(), 0.0)
        ).iterator().hasNext();

        if (!blinkFall && damagingFallAhead) {
            blinkFall = true;
            instance.getPacketManager().getBlink().start(this);
        }

        if (blinkFall && mc.player.fallDistance > blinkMaximumFallDistance.getValue()) {
            stopBlink();
        }
    }

    private void stopBlink() {
        if (blinkFall) {
            instance.getPacketManager().getBlink().dispatch(this, true);
            blinkFall = false;
        }
    }

    private void handleVulcanUpdate() {
        if (mc.player == null) return;

        if (!vulCanNoFall && mc.player.fallDistance > 3.25f) {
            vulCanNoFall = true;
        }
        if (vulCanNoFall && mc.player.isOnGround() && vulCantNoFall) {
            vulCantNoFall = false;
        }
        if (vulCantNoFall) {
            return;
        }
        if (vulCanNextSpoof) {
            mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y - 0.1, mc.player.getVelocity().z);
            mc.player.fallDistance = -0.1f;
            MovementUtil.strafe(0.3f);
            vulCanNextSpoof = false;
        }
        if (mc.player.fallDistance > 3.5625f) {
            mc.player.fallDistance = 0.0f;
            vulCanNextSpoof = true;
        }
    }

    private void handleMatrixPacket(PlayerMoveC2SPacket packet, PlayerMoveC2SPacketAccessor accessor) {
        if (mc.player == null || mc.world == null) return;

        if (!mc.player.isOnGround()) {
            if (mc.player.fallDistance > 2.69f) {
                accessor.setOnGround(true);
                mc.player.fallDistance = 0.0f;
            }
        }

        boolean collidingBelow = mc.world.getCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, mc.player.getVelocity().y, 0.0))
                .iterator()
                .hasNext();
        if (collidingBelow && !packet.isOnGround() && mc.player.getVelocity().y < -0.6) {
            accessor.setOnGround(true);
        }
    }

    private void spoofGroundAndStopFall(PlayerMoveC2SPacketAccessor accessor) {
        accessor.setOnGround(true);
        mc.player.fallDistance = 0.0f;
        mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
    }

    private boolean shouldSkipPacketModes() {
        if (mc.player == null) return true;
        if (mc.player.isGliding() || mc.player.getAbilities().creativeMode || mc.player.getAbilities().flying) {
            return true;
        }
        if (disableInAdventure.getValue()
                && mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode() == GameMode.ADVENTURE) {
            return true;
        }
        if (ignoreVoid.getValue() && isOverVoid()) {
            return true;
        }
        return ignoreFallResistanceBlocks.getValue() && isAboveFallResistanceBlock();
    }

    private boolean shouldSkip() {
        if (mc.player == null || mc.world == null) return true;
        if (mc.player.isDead() || shouldSkipPacketModes()) {
            return true;
        }
        if (mc.player.isOnGround()) {
            return true;
        }
        if (!mc.world.getBlockState(mc.player.getBlockPos().down()).isAir()) {
            return true;
        }
        return mc.player.getVelocity().y > -0.0784;
    }

    private boolean isOverVoid() {
        if (mc.player == null || mc.world == null) return false;

        int bottomY = mc.world.getBottomY();
        for (int y = mc.player.getBlockY(); y >= bottomY; y--) {
            if (!mc.world.getBlockState(mc.player.getBlockPos().withY(y)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private boolean isAboveFallResistanceBlock() {
        if (mc.player == null || mc.world == null) return false;

        for (int i = 0; i <= 3; i++) {
            Block block = mc.world.getBlockState(mc.player.getBlockPos().down(i)).getBlock();
            if (block == Blocks.WATER
                    || block == Blocks.POWDER_SNOW
                    || block == Blocks.COBWEB
                    || block == Blocks.SLIME_BLOCK
                    || block == Blocks.HAY_BLOCK
                    || block == Blocks.SWEET_BERRY_BUSH
                    || block instanceof BedBlock) {
                return true;
            }
        }
        return false;
    }

    private void resetState() {
        stopBlink();
        initialY = mc.player == null ? 0.0 : mc.player.getY();
        dynamic = 3.0;
        falling = false;
        ticks = 0;
        vulCanNoFall = false;
        vulCantNoFall = false;
        vulCanNextSpoof = false;
        blinkFall = false;
        resetCubecraftState();
    }
}