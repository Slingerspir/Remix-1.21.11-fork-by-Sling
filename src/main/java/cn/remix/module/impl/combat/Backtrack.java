package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.Render3D;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class Backtrack extends Module {

    private final NumberValue range = new NumberValue("Range", 3.0f, 1.0f, 6.0f, 0.1f);
    private final NumberValue minDelay = new NumberValue("Min Delay", 100, 0, 500, 10);
    private final NumberValue maxDelay = new NumberValue("Max Delay", 150, 0, 500, 10);
    private final NumberValue nextDelay = new NumberValue("Next Delay", 10, 0, 200, 5);
    private final NumberValue trackingBuffer = new NumberValue("Tracking Buffer", 500, 0, 2000, 50);
    private final NumberValue chance = new NumberValue("Chance", 50, 0, 100, 5);
    private final BoolValue pauseOnHurt = new BoolValue("Pause On Hurt", false);
    private final NumberValue hurtTime = new NumberValue("Hurt Time", 3, 0, 10, 1, () -> pauseOnHurt.getValue());

    private final ModeValue targetMode = new ModeValue("Target Mode", "Attack", "Attack", "Range", "Heypixel2");

    // ===== Heypixel2（nilore Backtrack 移植）=====
    private final NumberValue h2MaxRange = new NumberValue("H2 Max Range", 5.0f, 2.0f, 12.0f, 0.1f, () -> targetMode.is("Heypixel2"));
    private final NumberValue h2StartRange = new NumberValue("H2 Start Range", 2.8f, 0.1f, 6.0f, 0.1f, () -> targetMode.is("Heypixel2"));
    private final NumberValue h2MaxMs = new NumberValue("H2 Max MS", 1000, 50, 3000, 50, () -> targetMode.is("Heypixel2"));
    private final NumberValue h2Delay = new NumberValue("H2 Delay", 150, 1, 1000, 10, () -> targetMode.is("Heypixel2"));
    private final BoolValue h2Render = new BoolValue("H2 Render", true, () -> targetMode.is("Heypixel2"));

    private final Queue<PacketEntry> packetQueue = new ConcurrentLinkedQueue<>();
    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil trackingTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil delayTimer = new TimerUtil();
    private final Random random = new Random();

    private Entity target = null;
    private Vec3d targetPos = Vec3d.ZERO;
    private int currentDelay = 0;
    private boolean chancePassed = false;

    // Heypixel2 状态
    private final Queue<PacketEntry> h2Queue = new ConcurrentLinkedQueue<>();
    private final Map<Entity, Vec3d> h2Positions = new HashMap<>();
    private Entity h2Target;
    private long h2TrackingStart;
    private boolean h2Active;

    public Backtrack() {
        super("Backtrack", Category.Combat);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        packetQueue.clear();
        target = null;
        currentDelay = getRandomDelay();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();
        timer.reset();
        trackingTimer.reset();
        attackTimer.reset();
        delayTimer.reset();
        h2Cleanup();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        flushAllPackets();
        packetQueue.clear();
        target = null;
        h2Cleanup();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        packetQueue.clear();
        target = null;
        h2Cleanup();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        attackTimer.reset();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();

        if (targetMode.is("Heypixel2")) {
            Entity entity = event.getEntity();
            if (entity instanceof PlayerEntity player && player.isAlive()) {
                h2Target = player;
                h2TrackingStart = System.currentTimeMillis();
            }
            return;
        }

        if (!targetMode.is("Attack")) return;

        Entity entity = event.getEntity();
        if (entity == null) return;

        processTarget(entity);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (targetMode.is("Heypixel2")) {
            h2Tick();
            return;
        }

        if (targetMode.is("Range")) {
            Entity enemy = findNearestEnemy();
            if (enemy != null) {
                processTarget(enemy);
            } else {
                clearTarget();
            }
        }

        boolean shouldPause = false;
        if (pauseOnHurt.getValue() && target instanceof LivingEntity living) {
            shouldPause = living.hurtTime >= hurtTime.getValue().intValue();
        }

        if (shouldCancelPackets() && !shouldPause) {
            long now = System.currentTimeMillis();
            while (!packetQueue.isEmpty()) {
                PacketEntry entry = packetQueue.peek();
                if (entry == null) break;
                if (now - entry.getTimestamp() >= currentDelay) {
                    packetQueue.poll();
                    PacketUtil.receivePacketNoEvent(entry.getPacket());
                } else {
                    break;
                }
            }
        } else if (!packetQueue.isEmpty()) {
            flushAllPackets();
            clearTarget();
        }

        if (packetQueue.isEmpty()) {
            currentDelay = getRandomDelay();
        }

        setSuffix(packetQueue.size() + " queued");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Received) return;

        if (targetMode.is("Heypixel2")) {
            h2Receive(event);
            return;
        }

        Packet<?> packet = event.getPacket();

        String packetName = packet.getClass().getSimpleName();
        if (packetName.contains("Chat") || packetName.contains("Command")) {
            return;
        }

        if (packet instanceof PlayerPositionLookS2CPacket) {
            flushAllPackets();
            clearTarget();
            return;
        }

        if (target != null) {
            Vec3d pos = getPacketPosition(packet);
            if (pos != null) {
                double distToTracked = mc.player.squaredDistanceTo(targetPos);
                double distToActual = mc.player.squaredDistanceTo(pos);
                if (distToActual < distToTracked) {
                    flushAllPackets();
                    return;
                }
                targetPos = pos;
            }
        }

        if (shouldCancelPackets()) {
            event.setCancelled(true);
            packetQueue.add(new PacketEntry(packet));
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!targetMode.is("Heypixel2") || !h2Render.getValue()) return;
        if (!h2Active || h2Target == null || mc.player == null) return;

        Vec3d stored = h2Positions.get(h2Target);
        if (stored == null) return;

        float halfWidth = h2Target.getWidth() / 2.0f;
        float height = h2Target.getHeight();
        Box box = new Box(stored.x - halfWidth, stored.y, stored.z - halfWidth,
                stored.x + halfWidth, stored.y + height, stored.z + halfWidth);
        Render3D.drawOutlinedBox(event.getMatrixStack(), box, 2.0, 0xCCFFFFFF, false);
    }

    // =====================================================================
    // Heypixel2 实现
    // =====================================================================

    private void h2Receive(PacketEvent event) {
        if (event.isCancelled()) return;

        Packet<?> packet = event.getPacket();

        if (h2Target == null || !h2Target.isAlive() || h2Target.isRemoved()) {
            h2Cleanup();
            return;
        }

        if (packet instanceof PlayerPositionLookS2CPacket) {
            h2ForceRelease();
            return;
        }

        if (!h2ShouldBacktrack()) {
            h2DrainAndStop();
            return;
        }

        h2ProcessQueue();

        if (packet instanceof EntityS2CPacket move) {
            Entity entity = move.getEntity(mc.world);
            if (entity != null && entity.getId() == h2Target.getId() && move.isPositionChanged()) {
                Vec3d base = h2Positions.getOrDefault(h2Target,
                        new Vec3d(h2Target.getX(), h2Target.getY(), h2Target.getZ()));
                Vec3d next = base.add(move.getDeltaX() / 4096.0, move.getDeltaY() / 4096.0, move.getDeltaZ() / 4096.0);
                h2Positions.put(h2Target, next);
                event.setCancelled(true);
                h2Queue.add(new PacketEntry(packet));
                h2Active = true;
            }
            return;
        }

        if (packet instanceof EntityPositionS2CPacket teleport) {
            if (teleport.entityId() == h2Target.getId()) {
                h2Positions.put(h2Target, teleport.change().position());
                event.setCancelled(true);
                h2Queue.add(new PacketEntry(packet));
                h2Active = true;
            }
            return;
        }

        if (packet instanceof EntitiesDestroyS2CPacket destroy) {
            if (destroy.getEntityIds().contains(h2Target.getId())) {
                h2ForceRelease();
                return;
            }
        }

        if (h2Active) {
            event.setCancelled(true);
            h2Queue.add(new PacketEntry(packet));
        }
    }

    private void h2Tick() {
        if (h2Target != null && (!h2Target.isAlive() || h2Target.isRemoved())) {
            h2Cleanup();
            return;
        }

        if (h2Active) {
            h2ProcessQueue();
            if (h2Queue.isEmpty()) h2Active = false;
        }

        if (h2Active) {
            setSuffix(h2Queue.size() + " queued");
        } else {
            setSuffix(h2Target != null ? "Tracking" : "Idle");
        }
    }

    private boolean h2ShouldBacktrack() {
        if (h2Target == null || mc.player == null) return false;

        long now = System.currentTimeMillis();
        if (h2TrackingStart != 0 && now - h2TrackingStart > h2MaxMs.getValue().longValue()) return false;
        if (h2Target instanceof LivingEntity living && living.getHealth() <= 0) return false;

        Vec3d eye = mc.player.getEyePos();
        Box targetBox = h2Target.getBoundingBox();
        double current = eye.squaredDistanceTo(RotationUtil.getNearestPointBB(targetBox));
        double maxRange = h2MaxRange.getValue().doubleValue();
        if (current > maxRange * maxRange) return false;

        Vec3d stored = h2Positions.get(h2Target);
        if (stored != null) {
            float halfWidth = h2Target.getWidth() / 2.0f;
            float height = h2Target.getHeight();
            Box storedBox = new Box(stored.x - halfWidth, stored.y, stored.z - halfWidth,
                    stored.x + halfWidth, stored.y + height, stored.z + halfWidth);
            double storedDist = eye.squaredDistanceTo(RotationUtil.getNearestPointBB(storedBox));
            if (storedDist < current) return false;
            double startRange = h2StartRange.getValue().doubleValue();
            if (storedDist <= startRange * startRange) return true;
        }

        return h2Active;
    }

    private void h2ProcessQueue() {
        long now = System.currentTimeMillis();
        long delay = h2Delay.getValue().longValue();
        while (!h2Queue.isEmpty()) {
            PacketEntry entry = h2Queue.peek();
            if (entry == null) break;
            if (now - entry.getTimestamp() >= delay) {
                h2Queue.poll();
                try {
                    PacketUtil.receivePacketNoEvent(entry.getPacket());
                } catch (Throwable ignored) {
                    h2Queue.clear();
                    break;
                }
            } else {
                break;
            }
        }
    }

    private void h2ForceRelease() {
        h2ProcessQueue();
        h2Queue.clear();
        h2Positions.clear();
        h2Target = null;
        h2TrackingStart = 0;
        h2Active = false;
    }

    private void h2DrainAndStop() {
        h2ForceRelease();
    }

    private void h2Cleanup() {
        h2ForceRelease();
    }

    private void processTarget(Entity entity) {
        if (!shouldBacktrack(entity)) {
            return;
        }

        if (entity != target) {
            flushAllPackets();
            targetPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        }

        target = entity;
        trackingTimer.reset();
    }

    private boolean shouldBacktrack(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;

        double dist = mc.player.distanceTo(entity);
        double rangeVal = range.getValue().doubleValue();
        boolean inRange = dist >= rangeVal - 0.5 && dist <= rangeVal + 0.5;

        if (inRange) {
            trackingTimer.reset();
        }

        boolean inBuffer = trackingTimer.hasTimeElapsed(trackingBuffer.getValue().longValue());

        if (!canAttack(entity)) return false;

        if (pauseOnHurt.getValue() && entity instanceof LivingEntity living) {
            if (living.hurtTime >= hurtTime.getValue().intValue()) {
                return false;
            }
        }

        if (!chancePassed) return false;

        if (!timer.hasTimeElapsed(nextDelay.getValue().longValue())) return false;

        if (attackTimer.hasTimeElapsed(1000)) return false;

        return inRange || inBuffer;
    }

    private boolean canAttack(Entity entity) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.isInvisible()) return false;
        if (entity.isGlowing() && entity.isTeammate(mc.player)) return false;
        return true;
    }

    private Entity findNearestEnemy() {
        if (mc.world == null || mc.player == null) return null;

        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (!canAttack(entity)) continue;

            double dist = mc.player.squaredDistanceTo(entity);
            if (dist <= rangeSq && dist < nearestDist) {
                nearest = entity;
                nearestDist = dist;
            }
        }

        return nearest;
    }

    private Vec3d getPacketPosition(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        if (name.contains("Position") && !name.contains("Look")) {
            try {
                var method = packet.getClass().getMethod("getX");
                double x = (double) method.invoke(packet);
                method = packet.getClass().getMethod("getY");
                double y = (double) method.invoke(packet);
                method = packet.getClass().getMethod("getZ");
                double z = (double) method.invoke(packet);
                return new Vec3d(x, y, z);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean shouldCancelPackets() {
        if (target == null || !target.isAlive()) return false;

        if (pauseOnHurt.getValue() && target instanceof LivingEntity living) {
            if (living.hurtTime >= hurtTime.getValue().intValue()) {
                return false;
            }
        }

        return shouldBacktrack(target);
    }

    private void flushAllPackets() {
        while (!packetQueue.isEmpty()) {
            PacketEntry entry = packetQueue.poll();
            if (entry != null) {
                PacketUtil.receivePacketNoEvent(entry.getPacket());
            }
        }
    }

    private void clearTarget() {
        if (target != null) {
            delayTimer.reset();
        }
        target = null;
        targetPos = Vec3d.ZERO;
    }

    private int getRandomDelay() {
        int min = minDelay.getValue().intValue();
        int max = maxDelay.getValue().intValue();
        if (min >= max) return min;
        return min + random.nextInt(max - min);
    }


    @Getter
    private static class PacketEntry {
        private final Packet<?> packet;
        private final long timestamp;

        public PacketEntry(Packet<?> packet) {
            this.packet = packet;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public String getSuffix() {
        if (targetMode.is("Heypixel2")) {
            return h2Active ? h2Queue.size() + " queued" : (h2Target != null ? "Tracking" : "Idle");
        }
        if (packetQueue.isEmpty()) {
            return target != null ? "Tracking" : "Idle";
        }
        return packetQueue.size() + " queued";
    }
}
