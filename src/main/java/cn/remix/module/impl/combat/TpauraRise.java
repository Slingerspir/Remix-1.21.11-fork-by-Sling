package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.MathUtil;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render3D;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@lombok.Getter
public final class TpauraRise extends Module {

    private enum PathPhase {
        IDLE, OUTBOUND, RETURNING
    }

    private final ModeValue mode = new ModeValue("Mode", "TeleportAura", "TeleportAura", "Watchdog");
    private final ModeValue targets = new ModeValue("Targets", "Single", "Single", "Multiple");
    private final NumberValue range = new NumberValue("Range", 32, 3, 100, 1);
    private final NumberValue minCps = new NumberValue("Min CPS", 10, 1, 20, 1);
    private final NumberValue maxCps = new NumberValue("Max CPS", 15, 1, 20, 1);
    private final BoolValue cooldown19 = new BoolValue("1.9 Cooldown", false);
    private final NumberValue stepSize = new NumberValue("Step Size", 1.0f, 0.5f, 4.0f, 0.1f);
    private final NumberValue packetsPerTick = new NumberValue("Packets/Tick", 5, 1, 20, 1);
    private final BoolValue renderPath = new BoolValue("Render Path", true);
    private final BoolValue players = new BoolValue("Players", true);
    private final BoolValue hostile = new BoolValue("Hostile", false);
    private final BoolValue invisibles = new BoolValue("Invisibles", true);
    private final BoolValue teammates = new BoolValue("Teammates", false);

    private final TimerUtil clickStopWatch = new TimerUtil();
    private final TimerUtil lagbackTimer = new TimerUtil();
    private final List<Vec3d> path = new ArrayList<>();
    private final Deque<Packet<?>> heldPackets = new ArrayDeque<>();
    private LivingEntity target;
    private LivingEntity pendingAttackTarget;
    private LivingEntity attackEntity;
    private long nextSwing;
    private boolean blinking;
    private boolean skipNextTick = true;
    private int blinkTicks;
    private PathPhase phase = PathPhase.IDLE;
    private int pathIndex;

    public TpauraRise() {
        super("TpauraRise", Category.Combat);
    }

    @Override
    public void onEnable() {
        resetState();

        Disabler disabler = getModule(Disabler.class);
        if (disabler != null) {
            disabler.onTpAuraEnabled();
        }
    }

    @Override
    public void onDisable() {
        if (blinking) {
            stopBlink();
        }
        heldPackets.clear();
        resetState();

        Disabler disabler = getModule(Disabler.class);
        if (disabler != null) {
            disabler.onTpAuraDisabled();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.is("Watchdog")) {
            onWatchdogUpdate();
        } else {
            onTeleportAuraUpdate();
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        if (mode.is("Watchdog")) {
            if (skipNextTick) {
                skipNextTick = false;
                return;
            }

            updateWatchdogTarget();
            if (target == null) return;

            if (blinking) {
                event.setX(target.getX());
                event.setY(target.getY());
                event.setZ(target.getZ());
                pendingAttackTarget = target;

                float[] rotations = RotationUtil.getRotations(target.getEntityPos());
                if (rotations != null) {
                    event.setYaw(rotations[0]);
                    event.setPitch(rotations[1]);
                }
            } else {
                event.setX(mc.player.getX());
                event.setY(mc.player.getY());
                event.setZ(mc.player.getZ());
            }
            return;
        }

        if (phase != PathPhase.IDLE) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.getType() == PacketEvent.Type.Received) {
            if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
                if (mode.is("Watchdog")) {
                    if (!blinking) {
                        startBlink();
                    }
                } else if (phase != PathPhase.IDLE) {
                    
                    
                    resetPath();
                    lagbackTimer.reset();
                }
            }
        } else if (event.getType() == PacketEvent.Type.Send && mode.is("Watchdog")) {
            if (blinking && !event.isCancelled()) {
                heldPackets.add(event.getPacket());
                event.setCancelled();
            }
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        if (blinking) {
            stopBlink();
        }
        heldPackets.clear();
        resetPath();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || !mode.is("TeleportAura") || !renderPath.getValue() || path.isEmpty()) return;

        for (Vec3d point : path) {
            double pad = 0.05;
            Render3D.drawBox(event.getMatrixStack(), new Box(
                    point.x - pad, point.y - pad, point.z - pad,
                    point.x + pad, point.y + 1.0 + pad, point.z + pad
            ), ColorUtil.applyAlpha(Color.CYAN.getRGB(), 120), false);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!mode.is("Watchdog") || target == null || !blinking) return;

        String text = String.format("Target: %s [%d%%]", target.getName().getString(),
                (int) (target.getHealth() / target.getMaxHealth() * 100.0f));
        float x = event.getContext().getScaledWindowWidth() / 2.0f - mc.textRenderer.getWidth(text) / 2.0f;
        float y = event.getContext().getScaledWindowHeight() / 2.0f + 20.0f;
        event.getContext().drawTextWithShadow(mc.textRenderer, text, Math.round(x), Math.round(y), 0xFFFFFFFF);
    }

    private void onTeleportAuraUpdate() {
        if (phase != PathPhase.IDLE) {
            tickPathPhase();
            return;
        }

        if (!lagbackTimer.hasTimeElapsed(1000)) {
            return;
        }

        List<LivingEntity> livings = getTargets();
        if (livings.isEmpty()) {
            target = null;
            return;
        }

        livings.sort(Comparator.comparingDouble(entity -> mc.player.distanceTo(entity)));
        target = livings.getFirst();

        if (target != null && !mc.player.isDead()) {
            doAttack(livings);
        }
    }

    private void doAttack(List<LivingEntity> livings) {
        boolean ready;
        if (cooldown19.getValue()) {
            ready = mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
        } else {
            ready = clickStopWatch.hasTimeElapsed(nextSwing);
        }

        if (ready && target != null && !mc.options.attackKey.isPressed() && !mc.options.useKey.isPressed()) {
            if (!cooldown19.getValue()) {
                long cps = MathUtil.getRandomInRange(minCps.getValue().longValue(), maxCps.getValue().longValue());
                nextSwing = 1000L / Math.max(1L, cps);
            }

            double currentRange = range.getValue();
            if (targets.is("Single")) {
                if (mc.player.distanceTo(target) <= currentRange) {
                    startAttack(target);
                }
            } else {
                livings.removeIf(entity -> mc.player.distanceTo(entity) > currentRange);
                if (!livings.isEmpty()) {
                    for (LivingEntity living : livings) {
                        startAttack(living);
                    }
                }
            }

            clickStopWatch.reset();
        }
    }

    private void startAttack(LivingEntity living) {
        Vec3d from = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d to = new Vec3d(living.getX(), living.getY(), living.getZ());

        path.clear();
        path.addAll(buildPath(from, to));
        if (path.isEmpty()) return;

        attackEntity = living;
        pathIndex = 0;
        phase = PathPhase.OUTBOUND;
    }

    private void tickPathPhase() {
        int budget = Math.max(1, packetsPerTick.getValue().intValue());
        int sent = 0;

        while (pathIndex < path.size() && sent < budget) {
            Vec3d point = path.get(pathIndex);
            PacketUtil.sendPacketNoEvent(new PlayerMoveC2SPacket.PositionAndOnGround(
                    point.x, point.y, point.z, true, mc.player.horizontalCollision));
            pathIndex++;
            sent++;
        }

        if (pathIndex >= path.size()) {
            if (phase == PathPhase.OUTBOUND) {
                if (attackEntity != null) {
                    AttackEvent event = new AttackEvent(attackEntity);
                    instance.getEventManager().call(event);
                    if (!event.isCancelled()) {
                        PacketUtil.sendPacketNoEvent(PlayerInteractEntityC2SPacket.attack(attackEntity, false));
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
                Collections.reverse(path);
                pathIndex = 0;
                phase = PathPhase.RETURNING;
            } else {
                resetPath();
            }
        }
    }

    private void onWatchdogUpdate() {
        if (pendingAttackTarget != null && blinking) {
            AttackEvent attackEvent = new AttackEvent(pendingAttackTarget);
            instance.getEventManager().call(attackEvent);
            if (!attackEvent.isCancelled()) {
                PacketUtil.sendPacketNoEvent(PlayerInteractEntityC2SPacket.attack(pendingAttackTarget, false));
            }
            pendingAttackTarget = null;
        }

        if (blinking && ++blinkTicks > 1) {
            stopBlink();
        }
    }

    private void updateWatchdogTarget() {
        List<LivingEntity> list = getTargets();
        if (list.isEmpty()) {
            target = null;
            return;
        }

        list.sort(Comparator.comparingDouble(RotationUtil::getRotationDifference));
        target = list.getFirst();
    }

    private List<Vec3d> buildPath(Vec3d from, Vec3d to) {
        List<Vec3d> points = new ArrayList<>();
        if (!isFinite(from) || !isFinite(to)) {
            return points;
        }

        double step = Math.max(0.5, stepSize.getValue());
        double distance = from.distanceTo(to);
        if (distance < 0.05) return points;

        int steps = Math.max(1, (int) Math.ceil(distance / step));
        for (int s = 1; s <= steps; s++) {
            Vec3d point = from.lerp(to, s / (double) steps);
            if (!isFinite(point) || !isInLoadedChunk(point)) {
                
                return new ArrayList<>();
            }
            points.add(point);
            if (points.size() >= 200) {
                break;
            }
        }
        return points;
    }

    private boolean isFinite(Vec3d vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    private boolean isInLoadedChunk(Vec3d vec) {
        if (mc.world == null) return false;
        ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(vec));
        return mc.world.isChunkLoaded(chunkPos.x, chunkPos.z);
    }

    private List<LivingEntity> getTargets() {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity entity : instance.getTargetManager().getTargets()) {
            if (!EntityUtil.isSelected(entity)) continue;

            if (entity instanceof PlayerEntity player) {
                if (!players.getValue()) continue;
                if (!teammates.getValue() && instance.getFriendManager().isFriend(player.getName().getString())) continue;
            } else if (!hostile.getValue()) {
                continue;
            }

            if (!invisibles.getValue() && entity.isInvisible()) continue;
            list.add(entity);
        }
        return list;
    }

    private void startBlink() {
        if (!blinking) {
            blinking = true;
            blinkTicks = 0;
            heldPackets.clear();
        }
    }

    private void stopBlink() {
        if (blinking) {
            blinking = false;
            while (!heldPackets.isEmpty()) {
                PacketUtil.sendPacketNoEvent(heldPackets.poll());
            }
            skipNextTick = false;
        }
    }

    private void resetPath() {
        phase = PathPhase.IDLE;
        path.clear();
        pathIndex = 0;
        attackEntity = null;
    }

    private void resetState() {
        target = null;
        pendingAttackTarget = null;
        blinkTicks = 0;
        skipNextTick = true;
        blinking = false;
        lagbackTimer.time = 0L;
        resetPath();
        clickStopWatch.reset();
        nextSwing = 0L;
    }
}
