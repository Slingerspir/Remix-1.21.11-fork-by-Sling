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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class Speed extends Module {
    private final ModeValue mode = new ModeValue("Mode", "SentinelDamage", "SentinelDamage", "Custom", "LegitHop", "Vulcan", "Watchdog", "NCP", "Matrix", "Verus", "Grim", "Intave", "Hylex", "BlocksMC", "Spartan");

    private final NumberValue sentinelSpeed = new NumberValue("SentinelSpeed", 0.5f, 0.1f, 5.0f, 0.05f, () -> mode.is("SentinelDamage"));
    private final NumberValue reboostTicks = new NumberValue("ReboostTicks", 30, 10, 50, 1, () -> mode.is("SentinelDamage"));
    private final NumberValue boostHeight = new NumberValue("BoostHeight", 3.25f, 1.0f, 5.0f, 0.25f, () -> mode.is("SentinelDamage"));
    private final BoolValue autoJump = new BoolValue("AutoJump", true, () -> mode.is("SentinelDamage"));
    private final BoolValue damageBoost = new BoolValue("DamageBoost", true, () -> mode.is("SentinelDamage"));
    private final NumberValue boostDuration = new NumberValue("BoostDuration", 90, 30, 200, 5, () -> mode.is("SentinelDamage"));

    private final NumberValue horizontalAcceleration = new NumberValue("HorizontalAcceleration", 0.05f, -0.1f, 0.2f, 0.01f, () -> mode.is("Custom"));
    private final NumberValue horizontalJumpOff = new NumberValue("HorizontalJumpOff", 0.1f, -0.5f, 1.0f, 0.05f, () -> mode.is("Custom"));
    private final NumberValue ticksToBoostOff = new NumberValue("TicksToBoostOff", 2, 0, 20, 1, () -> mode.is("Custom"));
    private final NumberValue jumpHeight = new NumberValue("JumpHeight", 0.42f, 0.0f, 3.0f, 0.01f, () -> mode.is("Custom"));
    private final NumberValue pullDown = new NumberValue("PullDown", 0.02f, 0.0f, 1.0f, 0.01f, () -> mode.is("Custom"));
    private final NumberValue pullDownDuringFall = new NumberValue("PullDownDuringFall", 0.05f, 0.0f, 1.0f, 0.01f, () -> mode.is("Custom"));
    private final NumberValue strafeStrength = new NumberValue("StrafeStrength", 1.0f, 0.1f, 1.0f, 0.05f, () -> mode.is("Custom"));
    private final NumberValue strafeSpeed = new NumberValue("StrafeSpeed", 1.0f, 0.1f, 10.0f, 0.1f, () -> mode.is("Custom"));
    private final BoolValue customStrafeSpeed = new BoolValue("CustomStrafeSpeed", false, () -> mode.is("Custom"));

    private final NumberValue baseSpeed = new NumberValue("BaseSpeed", 1.0f, 0.5f, 5.0f, 0.05f);
    private final BoolValue onGroundOnly = new BoolValue("OnGroundOnly", false);

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil boostTimer = new TimerUtil();
    private boolean hasBeenHurt = false;
    private int damageTicks = 0;
    private int reboostCounter = 0;
    private boolean isBoosting = false;
    private int boostTicks = 0;
    private boolean wasOnGround = false;
    private double targetSpeed = 0;

    public Speed() {
        super("Speed", Category.Move);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        hasBeenHurt = false;
        damageTicks = 0;
        reboostCounter = 0;
        isBoosting = false;
        boostTicks = 0;
        wasOnGround = false;
        targetSpeed = 0;
        timer.reset();
        boostTimer.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        hasBeenHurt = false;
        isBoosting = false;
        targetSpeed = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        String currentMode = mode.getValue();
        setSuffix(currentMode);

        if (!MovementUtil.isMoving()) {
            return;
        }

        if (onGroundOnly.getValue() && !mc.player.isOnGround()) {
            return;
        }

        switch (currentMode) {
            case "SentinelDamage" -> handleSentinelDamage();
            case "Custom" -> handleCustom();
            case "LegitHop" -> handleLegitHop();
            case "Vulcan" -> handleVulcan();
            case "Watchdog" -> handleWatchdog();
            case "NCP" -> handleNCP();
            case "Matrix" -> handleMatrix();
            case "Verus" -> handleVerus();
            case "Grim" -> handleGrim();
            case "Intave" -> handleIntave();
            case "Hylex" -> handleHylex();
            case "BlocksMC" -> handleBlocksMC();
            case "Spartan" -> handleSpartan();
            default -> handleCustom();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        String currentMode = mode.getValue();

        if (currentMode.equals("SentinelDamage") && (hasBeenHurt || isBoosting)) {
            event.setOnGround(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.getType() != PacketEvent.Type.Send) return;
        if (!(event.getPacket() instanceof PlayerMoveC2SPacket)) return;

        String currentMode = mode.getValue();

        if (currentMode.equals("SentinelDamage") && (hasBeenHurt || isBoosting)) {
            event.setCancelled(true);
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
        }
    }

    private void handleSentinelDamage() {
        if (mc.player == null) return;

        boolean onGround = mc.player.isOnGround();
        boolean isMoving = MovementUtil.isMoving();
        float speed = sentinelSpeed.getValue();
        double currentSpeed = MovementUtil.getSpeed();

        if (mc.player.hurtTime > 0 && !hasBeenHurt && damageBoost.getValue()) {
            hasBeenHurt = true;
            damageTicks = 0;
            reboostCounter = 0;
            isBoosting = true;

            sendBoostPackets();

            targetSpeed = speed * 1.2;
            timer.reset();
            boostTimer.reset();

            if (isMoving) {
                MovementUtil.strafe(targetSpeed);
            }
            return;
        }

        if (hasBeenHurt) {
            if (damageTicks < 20) {
                targetSpeed = speed * 1.2;
            } else if (damageTicks < 40) {
                targetSpeed = speed * 1.1;
            } else if (damageTicks < 60) {
                targetSpeed = speed * 1.0;
            } else {
                targetSpeed = speed * 0.9;
            }

            if (isMoving) {
                MovementUtil.strafe(targetSpeed);
            }

            if (autoJump.getValue() && onGround && isMoving) {
                mc.player.jump();
                MovementUtil.strafe(targetSpeed * 1.15);
            }

            damageTicks++;

            if (damageTicks % reboostTicks.getValue().intValue() == 0) {
                sendBoostPackets();
                reboostCounter++;
            }

            int duration = boostDuration.getValue().intValue();
            if (damageTicks > duration) {
                hasBeenHurt = false;
                isBoosting = false;
                reboostCounter = 0;
                damageTicks = 0;
                targetSpeed = 0;
            }

            if (isMoving && damageTicks % 2 == 0) {
                sendPositionUpdate();
            }

            return;
        }

        if (isMoving) {
            MovementUtil.strafe(MovementUtil.getSpeed() * 1.02);
        }
    }

    private void sendBoostPackets() {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float height = boostHeight.getValue();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false, false));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, y + height, z, yaw, pitch, false, false));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, yaw, pitch, false, false));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));

        timer.reset();
    }

    private void sendPositionUpdate() {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(
                x, y, z,
                mc.player.getYaw(),
                mc.player.getPitch(),
                false, false
        ));
    }

    private void handleCustom() {
        if (mc.player == null) return;

        boolean onGround = mc.player.isOnGround();
        boolean isMoving = MovementUtil.isMoving();

        if (!isMoving) return;

        double horizontalAccel = horizontalAcceleration.getValue().doubleValue();
        if (horizontalAccel != 0 && onGround) {
            var vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x * (1 + horizontalAccel), vel.y, vel.z * (1 + horizontalAccel));
        }

        double jumpOff = horizontalJumpOff.getValue().doubleValue();
        int boostOffTicks = ticksToBoostOff.getValue().intValue();
        if (jumpOff != 0 && !onGround && wasOnGround && boostTicks < boostOffTicks) {
            var vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x * (1 + jumpOff), vel.y, vel.z * (1 + jumpOff));
            boostTicks++;
        }

        double jumpH = jumpHeight.getValue().doubleValue();
        if (jumpH != 0.42f && onGround) {
            mc.player.jump();
            var vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, jumpH, vel.z);
        }

        double pull = pullDown.getValue().doubleValue();
        if (pull > 0 && onGround) {
            var vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, vel.y - pull, vel.z);
        }

        double pullFall = pullDownDuringFall.getValue().doubleValue();
        if (pullFall > 0 && !onGround && mc.player.getVelocity().y <= 0) {
            var vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, vel.y - pullFall, vel.z);
        }

        double strafeStr = strafeStrength.getValue().doubleValue();
        double strafeSpd = strafeSpeed.getValue().doubleValue();
        boolean customSpeed = customStrafeSpeed.getValue();

        double speed = customSpeed ? strafeSpd : MovementUtil.getSpeed();
        MovementUtil.strafe(speed * strafeStr);

        wasOnGround = onGround;
        if (onGround) {
            boostTicks = 0;
        }
    }

    private void handleLegitHop() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(MovementUtil.getSpeed() * 1.1);
        }
    }

    private void handleVulcan() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.25);
        }
        if (!mc.player.isOnGround() && MovementUtil.isMoving()) {
            MovementUtil.strafe(1.15);
        }
    }

    private void handleWatchdog() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.3);
        }
        if (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.1) {
            MovementUtil.strafe(1.15);
        }
    }

    private void handleNCP() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.4);
        }
        if (!mc.player.isOnGround()) {
            MovementUtil.strafe(1.25);
        }
    }

    private void handleMatrix() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.35);
        }
        if (!mc.player.isOnGround() && mc.player.getVelocity().y > -0.1) {
            MovementUtil.strafe(1.2);
        }
    }

    private void handleVerus() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.45);
        }
        if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0) {
            MovementUtil.strafe(1.0);
        }
    }

    private void handleGrim() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.32);
        }
        if (!mc.player.isOnGround()) {
            MovementUtil.strafe(1.18);
        }
    }

    private void handleIntave() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.38);
        }
        if (!mc.player.isOnGround() && mc.player.getVelocity().y > -0.05) {
            MovementUtil.strafe(1.25);
        }
    }

    private void handleHylex() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.5);
        }
        if (!mc.player.isOnGround()) {
            MovementUtil.strafe(1.3);
        }
    }

    private void handleBlocksMC() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.42);
        }
        if (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.1) {
            MovementUtil.strafe(1.2);
        }
    }

    private void handleSpartan() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() && MovementUtil.isMoving()) {
            mc.player.jump();
            MovementUtil.strafe(1.48);
        }
        if (!mc.player.isOnGround()) {
            MovementUtil.strafe(1.28);
        }
    }
}