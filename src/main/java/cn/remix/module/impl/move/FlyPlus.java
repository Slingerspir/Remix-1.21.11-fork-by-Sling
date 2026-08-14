package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
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
public class FlyPlus extends Module {

    private final ModeValue mode = new ModeValue("Mode", "Sentinel20thApr",
            "Vanilla", "Creative", "Jetpack", "AirWalk",
            "Sentinel10thMar", "Sentinel20thApr", "Sentinel26thDec", "Sentinel27thJan");

    private final NumberValue horizontalSpeed = new NumberValue("Horizontal Speed", 3.5f, 0.1f, 10.0f, 0.1f);
    private final NumberValue verticalSpeed = new NumberValue("Vertical Speed", 0.7f, 0.1f, 5.0f, 0.1f);
    private final NumberValue glideSpeed = new NumberValue("Glide Speed", 0.0f, -1.0f, 1.0f, 0.05f);
    private final BoolValue bypassVanillaCheck = new BoolValue("Bypass Vanilla", true);

    private final NumberValue jumpHeight = new NumberValue("Jump Height", 0.42f, 0.1f, 1.0f, 0.01f, () -> isSentinelMode());
    private final NumberValue jumpSpeed = new NumberValue("Jump Speed", 0.35f, 0.1f, 1.0f, 0.01f, () -> isSentinelMode());
    private final NumberValue sentinelTicks = new NumberValue("Sentinel Ticks", 11, 1, 30, 1, () -> isSentinelMode());
    private final NumberValue reboostTicks = new NumberValue("Reboost Ticks", 30, 10, 60, 1, () -> isSentinel20thApr());
    private final BoolValue boostOnce = new BoolValue("Boost Once", false, () -> isSentinel20thApr());
    private final BoolValue nostalgia = new BoolValue("Nostalgia", false, () -> isSentinel20thApr() || isSentinel26thDec());
    private final NumberValue sentinelTimer = new NumberValue("Sentinel Timer", 0.5f, 0.1f, 1.0f, 0.05f, () -> isSentinel26thDec());

    private final TimerUtil timer = new TimerUtil();
    private int tickCounter = 0;
    private boolean hasBeenHurt = false;
    private boolean hasBeenTeleported = false;
    private boolean spoofOnGround = false;
    private boolean isFlying = false;

    public FlyPlus() {
        super("FlyPlus", Category.Move);
    }

    private boolean isSentinelMode() {
        String m = mode.getValue();
        return m.equals("Sentinel10thMar") || m.equals("Sentinel20thApr") ||
                m.equals("Sentinel26thDec") || m.equals("Sentinel27thJan");
    }

    private boolean isSentinel20thApr() {
        return mode.is("Sentinel20thApr");
    }

    private boolean isSentinel26thDec() {
        return mode.is("Sentinel26thDec");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        tickCounter = 0;
        hasBeenHurt = false;
        hasBeenTeleported = false;
        spoofOnGround = false;
        isFlying = false;
        timer.reset();

        if (isSentinel20thApr() || isSentinel26thDec()) {
            System.out.println("[FlyPlus] Sentinel mode enabled - requires damage to activate");
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MovementUtil.stop();
        instance.getPacketManager().getBlink().dispatch(this);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        setSuffix(mode.getValue());
        tickCounter++;

        if (mc.player.isOnGround()) {
            hasBeenHurt = false;
            isFlying = false;
        }

        String currentMode = mode.getValue();
        switch (currentMode) {
            case "Sentinel10thMar" -> handleSentinel10thMar();
            case "Sentinel20thApr" -> handleSentinel20thApr();
            case "Sentinel26thDec" -> handleSentinel26thDec();
            case "Sentinel27thJan" -> handleSentinel27thJan();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || event.isPost()) return;

        String currentMode = mode.getValue();

        switch (currentMode) {
            case "Vanilla" -> handleVanilla();
            case "Creative" -> handleCreative();
            case "Jetpack" -> handleJetpack();
            case "AirWalk" -> handleAirWalk();
            default -> {}
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        if (event.getType() != PacketEvent.Type.Send) return;
        if (!(event.getPacket() instanceof PlayerMoveC2SPacket)) return;

        String currentMode = mode.getValue();

        if (currentMode.equals("Sentinel10thMar") && spoofOnGround) {
            event.setCancelled(true);
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
            spoofOnGround = false;
            return;
        }

        if (currentMode.equals("AirWalk")) {
            event.setCancelled(true);
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
        }
    }

    private void handleVanilla() {
        if (mc.player == null) return;

        float hSpeed = horizontalSpeed.getValue().floatValue();
        float vSpeed = verticalSpeed.getValue().floatValue();

        mc.player.setVelocity(0, 0, 0);
        MovementUtil.strafe(hSpeed);

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, vSpeed, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -vSpeed, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, glideSpeed.getValue().floatValue(), mc.player.getVelocity().z);
        }

        if (bypassVanillaCheck.getValue() && tickCounter % 40 == 0) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.04, mc.player.getVelocity().z);
        }
    }

    private void handleCreative() {
        if (mc.player == null) return;

        float hSpeed = horizontalSpeed.getValue().floatValue();
        float vSpeed = verticalSpeed.getValue().floatValue();

        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed(hSpeed * 0.1f);

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, vSpeed, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -vSpeed, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }
    }

    private void handleJetpack() {
        if (mc.player == null) return;

        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(
                    mc.player.getVelocity().x * 1.1,
                    mc.player.getVelocity().y + 0.15,
                    mc.player.getVelocity().z * 1.1
            );
        }
        MovementUtil.strafe(horizontalSpeed.getValue().floatValue());
    }

    private void handleAirWalk() {
        if (mc.player == null) return;

        float hSpeed = horizontalSpeed.getValue().floatValue();
        MovementUtil.strafe(hSpeed);
    }

    private void handleSentinel10thMar() {
        if (mc.player == null) return;

        if (mc.player.isOnGround()) return;

        if (tickCounter % sentinelTicks.getValue().intValue() == 0) {
            float jumpH = jumpHeight.getValue().floatValue();
            float jumpS = jumpSpeed.getValue().floatValue();

            mc.player.setVelocity(mc.player.getVelocity().x, jumpH, mc.player.getVelocity().z);
            MovementUtil.strafe(jumpS);

            spoofOnGround = true;
        }
    }

    private void handleSentinel20thApr() {
        if (mc.player == null) return;

        if (mc.player.hurtTime > 0 && !hasBeenHurt) {
            hasBeenHurt = true;
            float hSpeed = horizontalSpeed.getValue().floatValue();
            MovementUtil.strafe(hSpeed);

            sendBoostPackets();

            if (nostalgia.getValue() && !hasBeenTeleported) {
                hasBeenTeleported = true;
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.42, mc.player.getZ());
            }

            System.out.println("[FlyPlus] Sentinel20thApr boosted!");
        }

        if (!hasBeenHurt) return;

        float vSpeed = verticalSpeed.getValue().floatValue();
        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, vSpeed, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -vSpeed, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }

        MovementUtil.strafe(horizontalSpeed.getValue().floatValue());

        if (tickCounter % reboostTicks.getValue().intValue() == 0 && !boostOnce.getValue()) {
            hasBeenHurt = false;
            sendBoostPackets();
        }

        if (boostOnce.getValue()) {
            isFlying = true;
        }
    }

    private void handleSentinel26thDec() {
        if (mc.player == null) return;

        if (mc.player.hurtTime > 0 && !hasBeenHurt) {
            hasBeenHurt = true;
            float hSpeed = horizontalSpeed.getValue().floatValue();
            MovementUtil.strafe(hSpeed);

            if (nostalgia.getValue() && !hasBeenTeleported) {
                hasBeenTeleported = true;
                mc.player.setPosition(mc.player.getX(), mc.player.getY() + 0.42, mc.player.getZ());
            }

            sendComplexBoostPackets();
            System.out.println("[FlyPlus] Sentinel26thDec boosted!");
        }

        if (!hasBeenHurt) return;

        float vSpeed = verticalSpeed.getValue().floatValue();
        if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, vSpeed, mc.player.getVelocity().z);
        } else if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -vSpeed, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }

        MovementUtil.strafe(horizontalSpeed.getValue().floatValue());

        if (boostOnce.getValue()) {
            isFlying = true;
        }
    }

    private void handleSentinel27thJan() {
        if (mc.player == null) return;

        if (mc.player.isOnGround()) return;

        float hSpeed = horizontalSpeed.getValue().floatValue();

        if (mc.options.sneakKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, -0.4, mc.player.getVelocity().z);
        } else if (mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.2, mc.player.getVelocity().z);
        }

        MovementUtil.strafe(hSpeed);
    }


    private void sendBoostPackets() {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, mc.player.getYaw(), mc.player.getPitch(), false, false));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, y + 3.25, z, mc.player.getYaw(), mc.player.getPitch(), false, false));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, mc.player.getYaw(), mc.player.getPitch(), false, false));
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
    }

    private void sendComplexBoostPackets() {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        double currentY = 4.0;
        double motionY = 0.0;

        while (currentY > 0) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(
                    x, y + currentY, z,
                    mc.player.getYaw(), mc.player.getPitch(),
                    currentY == 4.0, false
            ));

            currentY += motionY;
            motionY -= 0.08;
            motionY *= 0.98;
        }

        PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
    }


}