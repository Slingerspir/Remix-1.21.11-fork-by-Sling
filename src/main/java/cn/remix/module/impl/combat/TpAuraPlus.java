package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.Render3D;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@lombok.Getter
public final class TpAuraPlus extends Module {

    private static final long VISUAL_FADE_MS = 1200L;
    private static final long LAGBACK_PAUSE_MS = 1200L;
    private static final int MAX_PATH_POINTS = 80;

    private enum Phase {
        IDLE, OUTBOUND, AIM, HOLD, RETURNING, PAUSED
    }

    private final ModeValue priority = new ModeValue("Priority", "Distance", "Distance", "Health", "LivingTime", "Armor");
    private final NumberValue range = new NumberValue("Range", 12.0, 3.0, 64.0, 1.0);
    private final NumberValue stepSize = new NumberValue("Step Size", 1.0f, 0.5f, 8.0f, 0.1f);
    private final NumberValue packetsPerTick = new NumberValue("Packets/Tick", 2, 1, 20, 1);
    private final NumberValue vClip = new NumberValue("VClip", 8.0, 2.0, 64.0, 1.0);
    private final NumberValue prev = new NumberValue("Prev", 0.0, 0.0, 5.0, 0.1);
    private final BoolValue safeTargetFallback = new BoolValue("Safe Target Fallback", true);
    private final BoolValue randomOffset = new BoolValue("Random Offset", true);
    private final NumberValue offsetXZ = new NumberValue("Offset XZ", 0.05f, 0.0f, 0.3f, 0.01f, randomOffset::getValue);
    private final NumberValue offsetY = new NumberValue("Offset Y", 0.01f, 0.0f, 0.2f, 0.01f, randomOffset::getValue);
    private final BoolValue rotate = new BoolValue("Rotate", true);
    private final BoolValue swing = new BoolValue("Swing", true);
    private final BoolValue useMace = new BoolValue("Use Mace", false);
    private final BoolValue useCooldown = new BoolValue("Use Cooldown", true);
    private final NumberValue useCooldownBaseTime = new NumberValue("Cooldown Base", 0.75f, 0.1f, 1.0f, 0.05f, useCooldown::getValue);
    private final NumberValue attackDelay = new NumberValue("Attack Delay", 50, 1, 2000, 25, () -> !useCooldown.getValue());
    private final NumberValue attackTimes = new NumberValue("Attack Times", 1, 1, 50, 1);
    private final NumberValue preAttackDelay = new NumberValue("Pre-Attack Delay", 1, 0, 10, 1);
    private final NumberValue attackWaitTicks = new NumberValue("Return Delay Ticks", 10, 0, 100, 1);
    private final BoolValue tryMissTotem = new BoolValue("Try Miss Totem", false);
    private final BoolValue lagbackDisable = new BoolValue("Disable on Lagback", false);
    private final BoolValue antiCorrection = new BoolValue("Anti Teleport", true);
    private final BoolValue renderTrail = new BoolValue("Render Trail", true);
    private final ColorValue renderTrailColor = new ColorValue("Render Trail Color", Color.CYAN, renderTrail::getValue);

    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil lagbackTimer = new TimerUtil();
    private final List<VisualPoint> visualPoints = new ArrayList<>();
    private final List<Vec3d> path = new ArrayList<>();
    private final Deque<Packet<?>> pendingPackets = new ArrayDeque<>();

    private Phase phase = Phase.IDLE;
    private int pathIndex;
    private int holdTicksLeft;
    private int preAttackTicksLeft;
    private Vec3d attackPos;
    private LivingEntity target;
    private LivingEntity chaseTarget;
    private LivingEntity attackEntity;
    private boolean recovering;
    private long lastCycleEndTime;
    private Vector4f highlightBounds;

    public TpAuraPlus() {
        super("TpAuraPlus", Category.Combat);
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
        resetState();

        Disabler disabler = getModule(Disabler.class);
        if (disabler != null) {
            disabler.onTpAuraDisabled();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Aura aura = getModule(Aura.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() != null) {
            resetCycle();
            chaseTarget = aura.getTarget();
            target = null;
            return;
        }

        if (phase != Phase.IDLE && (attackEntity == null || !attackEntity.isAlive() || attackEntity.isDead())) {
            resetCycle();
        }

        updateTarget();

        switch (phase) {
            case IDLE -> startCycle();
            case OUTBOUND -> tickOutbound();
            case AIM -> tickAim();
            case HOLD -> tickHold();
            case RETURNING -> tickReturning();
            case PAUSED -> {
                if (lagbackTimer.hasTimeElapsed(LAGBACK_PAUSE_MS)) {
                    phase = Phase.IDLE;
                }
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {

        if (phase == Phase.OUTBOUND || phase == Phase.AIM || phase == Phase.HOLD || phase == Phase.RETURNING) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        updateHighlightBounds(event);
        pruneVisuals();
        renderVisuals(event);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (highlightBounds == null) return;

        float x = highlightBounds.x;
        float y = highlightBounds.y;
        float width = highlightBounds.z - highlightBounds.x;
        float height = highlightBounds.w - highlightBounds.y;
        Render2D.drawOutline(event.getContext(), x - 2.0f, y - 2.0f, width + 4.0f, height + 4.0f, 2.5f, Color.ORANGE.getRGB());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != PacketEvent.Type.Received) return;

        if (event.getPacket() instanceof EntityStatusS2CPacket packet && tryMissTotem.getValue() && packet.getStatus() == 35 && target != null && packet.getEntity(mc.world) == target) {
            if (phase == Phase.IDLE) {
                attackPos = getPredictedPos(target, 0.0);
                attackEntity = target;
                path.clear();
                path.addAll(buildPath(getClientPos(), attackPos));
                pathIndex = 0;
                phase = Phase.OUTBOUND;
            }
            return;
        }

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            boolean inCycle = phase != Phase.IDLE;
            boolean protectedWindow = System.currentTimeMillis() - lastCycleEndTime < 2000L;
            if (antiCorrection.getValue() && (inCycle || protectedWindow)) {

                event.setCancelled();
                PacketUtil.sendPacketNoEvent(new TeleportConfirmC2SPacket(packet.teleportId()));
                if (inCycle) {
                    recoverFromCorrection(packet);
                } else {
                    resetCycle();
                    phase = Phase.PAUSED;
                    lagbackTimer.reset();
                }
            } else {
                handleLagback();
            }
        }
    }

    private void resetState() {
        resetCycle();
        visualPoints.clear();
        attackTimer.reset();
        lagbackTimer.reset();
    }

    private void resetCycle() {
        phase = Phase.IDLE;
        path.clear();
        pathIndex = 0;
        holdTicksLeft = 0;
        preAttackTicksLeft = 0;
        attackPos = null;
        attackEntity = null;
        recovering = false;
        pendingPackets.clear();
    }

    private void markCycleEnd() {
        lastCycleEndTime = System.currentTimeMillis();
    }

    private void startCycle() {
        if (target == null || !isReadyToAttack()) return;
        if (mc.player.distanceTo(target) > range.getValue()) return;

        if (mc.player.distanceTo(target) <= 6.0) {
            attackEntity = target;
            attackNow();
            flushPending();
            attackTimer.reset();
            return;
        }

        Vec3d targetPos = getPredictedPos(target, prev.getValue());
        attackEntity = target;
        Vec3d attack = safeTargetFallback.getValue() ? findVisibleAttackPos(targetPos, target) : targetPos;
        if (attack == null) {
            Util.log("[TpAuraPlus] No safe attack position.");
            return;
        }

        attackPos = attack;

        List<Vec3d> outbound = buildOutboundPath(getClientPos(), attack);
        if (outbound.isEmpty()) {
            Util.log("[TpAuraPlus] TP path empty.");
            return;
        }

        path.clear();
        path.addAll(outbound);
        pathIndex = 0;
        phase = Phase.OUTBOUND;
    }

    private void tickOutbound() {

        flushPending();

        int sent = 0;
        int budget = Math.max(1, packetsPerTick.getValue().intValue());
        Vec3d lastSent = null;

        while (pathIndex < path.size() && sent < budget) {
            Vec3d point = path.get(pathIndex);
            boolean last = pathIndex == path.size() - 1;
            if (last) {
                sendDelayed(point, getAttackRotations(), isOnGroundAt(point));
            } else {
                sendDelayed(point, null, isOnGroundAt(point));
            }
            recordPoint(point);
            pathIndex++;
            sent++;
            lastSent = point;
        }

        if (pathIndex < path.size() && lastSent != null && !isOnGroundAt(lastSent)) {
            sendDelayed(lastSent, null, true);
        }

        if (pathIndex >= path.size()) {
            preAttackTicksLeft = Math.max(0, preAttackDelay.getValue().intValue());
            phase = preAttackTicksLeft > 0 ? Phase.AIM : Phase.HOLD;
            if (preAttackTicksLeft <= 0) {
                attackNow();
                holdTicksLeft = attackWaitTicks.getValue().intValue();
                if (holdTicksLeft <= 0) {
                    buildReturnPath();
                    phase = Phase.RETURNING;
                }
            }
        }
    }

    private void tickAim() {
        flushPending();

        if (preAttackTicksLeft > 0) {
            preAttackTicksLeft--;
        }
        if (preAttackTicksLeft <= 0) {
            attackNow();
            holdTicksLeft = attackWaitTicks.getValue().intValue();
            phase = holdTicksLeft > 0 ? Phase.HOLD : Phase.RETURNING;
            if (phase == Phase.RETURNING) {
                buildReturnPath();
            }
        }
    }

    private void tickHold() {
        flushPending();
        pinPosition();

        if (holdTicksLeft > 0) {
            holdTicksLeft--;
        }
        if (holdTicksLeft <= 0) {
            buildReturnPath();
            phase = Phase.RETURNING;
        }
    }

    private void tickReturning() {
        flushPending();

        int sent = 0;
        int budget = Math.max(1, packetsPerTick.getValue().intValue());

        while (pathIndex < path.size() && sent < budget) {
            Vec3d point = path.get(pathIndex);
            sendDelayed(point, null, isOnGroundAt(point));
            recordPoint(point);
            pathIndex++;
            sent++;
        }

        if (pathIndex >= path.size()) {

            flushPending();
            if (recovering) {
                recovering = false;
                resetCycle();
                phase = Phase.PAUSED;
                lagbackTimer.reset();
            } else {
                markCycleEnd();
                resetCycle();
                attackTimer.reset();
            }
        }
    }

    private void buildReturnPath() {
        if (attackPos == null) {
            resetCycle();
            return;
        }
        Vec3d returnTo = getClientPos();
        path.clear();
        path.addAll(buildOutboundPath(attackPos, returnTo));
        pathIndex = 0;
    }

    private Vec3d getClientPos() {
        return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void attackNow() {
        if (attackEntity == null || mc.player == null) {
            resetCycle();
            return;
        }

        if (useMace.getValue() && !mc.player.getMainHandStack().isOf(Items.MACE)) {
            resetCycle();
            return;
        }

        int times = Math.max(1, attackTimes.getValue().intValue());
        for (int i = 0; i < times; i++) {
            pendingPackets.add(PlayerInteractEntityC2SPacket.attack(attackEntity, false));
        }

        if (swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void sendDelayed(Vec3d vec, float[] rot, boolean onGround) {
        if (rot != null) {
            pendingPackets.add(new PlayerMoveC2SPacket.Full(vec.x, vec.y, vec.z, rot[0], rot[1], onGround, mc.player.horizontalCollision));
        } else {
            pendingPackets.add(new PlayerMoveC2SPacket.PositionAndOnGround(vec.x, vec.y, vec.z, onGround, mc.player.horizontalCollision));
        }
    }

    private void flushPending() {
        while (!pendingPackets.isEmpty()) {
            PacketUtil.sendPacketNoEvent(pendingPackets.poll());
        }
    }

    private void pinPosition() {
        if (attackPos == null || mc.player == null) return;
        sendDelayed(attackPos, null, true);
    }

    private void recoverFromCorrection(PlayerPositionLookS2CPacket packet) {
        Vec3d corrected = decodeCorrection(packet);
        Vec3d real = getClientPos();

        pendingPackets.clear();
        resetCycle();

        if (corrected != null && corrected.squaredDistanceTo(real) > 1.0) {
            path.clear();
            path.addAll(buildPath(corrected, real));
            pathIndex = 0;
            recovering = true;
            phase = Phase.RETURNING;
        } else {
            phase = Phase.PAUSED;
            lagbackTimer.reset();
        }
    }

    private Vec3d decodeCorrection(PlayerPositionLookS2CPacket packet) {
        if (mc.player == null) return null;
        Vec3d pos = packet.change().position();
        Set<PositionFlag> relatives = packet.relatives();
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        if (relatives.contains(PositionFlag.X)) x += mc.player.getX();
        if (relatives.contains(PositionFlag.Y)) y += mc.player.getY();
        if (relatives.contains(PositionFlag.Z)) z += mc.player.getZ();
        return new Vec3d(x, y, z);
    }

    private List<Vec3d> buildOutboundPath(Vec3d from, Vec3d to) {
        if (hasLineOfSight(from.add(0.0, 1.0, 0.0), to.add(0.0, 1.0, 0.0))) {
            return buildPath(from, to);
        }

        Vec3d aboveFrom = findSafeAbove(from, vClip.getValue());
        Vec3d aboveTo = findSafeAbove(to, vClip.getValue());
        if (aboveFrom == null || aboveTo == null) {
            return buildPath(from, to);
        }
        if (!hasLineOfSight(aboveFrom.add(0.0, 1.0, 0.0), aboveTo.add(0.0, 1.0, 0.0))) {
            return buildPath(from, to);
        }
        return buildPath(from, aboveFrom, aboveTo, to);
    }

    private List<Vec3d> buildPath(Vec3d... waypoints) {
        List<Vec3d> points = new ArrayList<>();
        double step = Math.max(0.5, stepSize.getValue());

        for (int i = 0; i < waypoints.length - 1; i++) {
            Vec3d from = waypoints[i];
            Vec3d to = waypoints[i + 1];
            if (from == null || to == null) continue;

            double distance = from.distanceTo(to);
            if (distance < 0.05) continue;

            int steps = Math.max(1, (int) Math.ceil(distance / step));
            for (int s = 1; s <= steps; s++) {
                points.add(from.lerp(to, s / (double) steps));
            }
        }

        List<Vec3d> result = new ArrayList<>();
        Vec3d last = null;
        for (Vec3d point : points) {
            if (last == null || last.squaredDistanceTo(point) > 1.0E-4) {
                result.add(point);
                last = point;
            }
        }

        if (result.size() > MAX_PATH_POINTS) {
            return new ArrayList<>(result.subList(0, MAX_PATH_POINTS));
        }
        return result;
    }

    private Vec3d findSafeAbove(Vec3d from, double maxUp) {
        if (mc.world == null) return null;
        for (double dy = 2.0; dy <= maxUp; dy += 0.5) {
            Vec3d candidate = new Vec3d(from.x, from.y + dy, from.z);
            if (!isInvalidPosition(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Vec3d findVisibleAttackPos(Vec3d desired, LivingEntity entity) {
        Box targetBox = entity != null ? entity.getBoundingBox() : new Box(desired, desired.add(1.0, 1.8, 1.0));
        Vec3d targetBody = new Vec3d(desired.x, desired.y + 1.0, desired.z);

        if (hasLineOfSight(desired.add(0.0, 1.0, 0.0), targetBody) && reachDistance(desired, targetBox) <= 3.5) {
            return desired;
        }

        List<Vec3d> candidates = new ArrayList<>();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Vec3d test = desired.add(dx, dy, dz);
                    if (!isInvalidPosition(test) && hasLineOfSight(test.add(0.0, 1.0, 0.0), targetBody)) {
                        candidates.add(test);
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(candidate -> reachDistance(candidate, targetBox)));
        for (Vec3d candidate : candidates) {
            if (reachDistance(candidate, targetBox) <= 3.5) {
                return candidate;
            }
        }

        return safeTargetFallback.getValue() ? findNearestSafePos(desired) : desired;
    }

    private double reachDistance(Vec3d pos, Box box) {
        Vec3d eye = pos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
        return Math.sqrt(box.squaredMagnitude(eye));
    }

    private boolean hasLineOfSight(Vec3d from, Vec3d to) {
        if (mc.world == null) return false;
        RaycastContext context = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        return mc.world.raycast(context).getType() == HitResult.Type.MISS;
    }

    private float[] getAttackRotations() {
        if (!rotate.getValue() || attackPos == null || attackEntity == null) return null;
        Vec3d eye = attackPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
        Vec3d body = new Vec3d(attackEntity.getX(), attackEntity.getY() + attackEntity.getHeight() * 0.8, attackEntity.getZ());
        return RotationUtil.getRotations(eye, body);
    }

    private boolean isOnGroundAt(Vec3d pos) {
        if (mc.world == null) return false;
        BlockState below = mc.world.getBlockState(BlockPos.ofFloored(pos.x, pos.y - 0.1, pos.z));
        return !below.getCollisionShape(mc.world, BlockPos.ofFloored(pos.x, pos.y - 0.1, pos.z)).isEmpty();
    }

    private void handleLagback() {
        Disabler disabler = getModule(Disabler.class);
        if (disabler != null && disabler.isEnabled()) {
            disabler.setEnabled(false);
            Util.log("[TpAuraPlus] Lagback, disabling Disabler.");
        }

        if (phase != Phase.IDLE) {
            resetCycle();
            if (lagbackDisable.getValue()) {
                setEnabled(false);
                return;
            }
            phase = Phase.PAUSED;
            lagbackTimer.reset();
        }
    }

    private void updateTarget() {
        if (isValidChaseTarget(chaseTarget)) {
            target = chaseTarget;
            setSuffix(priority.getValue());
            return;
        }

        target = instance.getTargetManager().getTargets().stream()
                .filter(EntityUtil::isSelected)
                .filter(entity -> mc.player.distanceTo(entity) <= range.getValue())
                .min(sortTargets())
                .orElse(null);

        if (target != null) {
            chaseTarget = target;
        }
        setSuffix(target == null ? "" : priority.getValue());
    }

    private boolean isValidChaseTarget(LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isDead()
                && entity.getHealth() > 0
                && EntityUtil.isSelected(entity)
                && mc.player.distanceTo(entity) <= range.getValue();
    }

    private Comparator<LivingEntity> sortTargets() {
        return switch (priority.getValue()) {
            case "Health" -> Comparator.comparingDouble(entity -> entity.getHealth() + entity.getAbsorptionAmount());
            case "LivingTime" -> Comparator.comparingInt((LivingEntity entity) -> entity.age).reversed();
            case "Armor" -> Comparator.comparingInt(LivingEntity::getArmor);
            default -> Comparator.comparingDouble(entity -> mc.player.distanceTo(entity));
        };
    }

    private boolean isReadyToAttack() {
        return useCooldown.getValue()
                ? mc.player.getAttackCooldownProgress(-1.0f) >= useCooldownBaseTime.getValue()
                : attackTimer.hasTimeElapsed(attackDelay.getValue());
    }

    private Vec3d getPredictedPos(LivingEntity entity, double ticks) {
        Vec3d currentPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vec3d lastPos = currentPos.subtract(entity.getX() - entity.lastX, entity.getY() - entity.lastY, entity.getZ() - entity.lastZ);
        return lastPos.lerp(currentPos, Math.max(0.0, Math.min(1.0, ticks)));
    }

    private boolean isInvalidPosition(Vec3d pos) {
        if (mc.world == null || mc.player == null) {
            return true;
        }

        Box box = mc.player.getBoundingBox().offset(pos.subtract(getClientPos()));
        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

        for (BlockPos blockPos : BlockPos.iterate(min, max)) {
            BlockState state = mc.world.getBlockState(blockPos);
            if (!state.getCollisionShape(mc.world, blockPos).isEmpty() || state.isOf(Blocks.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private Vec3d findNearestSafePos(Vec3d desired) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Vec3d test = desired.add(dx, dy, dz);
                    if (!isInvalidPosition(test)) {
                        return test;
                    }
                }
            }
        }
        return null;
    }

    private void recordPoint(Vec3d pos) {
        if (visualPoints.size() >= 10) {
            visualPoints.remove(0);
        }
        visualPoints.add(new VisualPoint(pos, System.currentTimeMillis()));
    }

    private void pruneVisuals() {
        long now = System.currentTimeMillis();
        Iterator<VisualPoint> iterator = visualPoints.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().time() > VISUAL_FADE_MS) {
                iterator.remove();
            }
        }
    }

    private void renderVisuals(Render3DEvent event) {
        for (int i = 0; i < visualPoints.size(); i++) {
            VisualPoint point = visualPoints.get(i);
            float alpha = getAlpha(point.time());
            if (alpha <= 0.01f || !renderTrail.getValue()) continue;

            if (i > 0) {
                VisualPoint prevPoint = visualPoints.get(i - 1);
                float lineAlpha = Math.min(alpha, getAlpha(prevPoint.time()));
                if (lineAlpha > 0.01f) {
                    int color = ColorUtil.applyAlpha(renderTrailColor.getValue().getRGB(), Math.round(255.0f * lineAlpha));
                    int steps = Math.max(2, (int) Math.ceil(point.pos().distanceTo(prevPoint.pos()) / 0.35));
                    for (int s = 0; s <= steps; s++) {
                        Vec3d mid = prevPoint.pos().lerp(point.pos(), (double) s / steps);
                        double pad = 0.035;
                        Render3D.drawBox(event.getMatrixStack(), new Box(
                                mid.x - pad, mid.y - pad, mid.z - pad,
                                mid.x + pad, mid.y + pad, mid.z + pad
                        ), color, false);
                    }
                }
            }
        }
    }

    private void updateHighlightBounds(Render3DEvent event) {
        if (target == null) {
            highlightBounds = null;
            return;
        }

        Vec3d[] vectors = ProjectUtil.getVectors(target);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean visible = false;

        for (Vec3d vector : vectors) {
            Vec3d projected = ProjectUtil.worldSpaceToScreenSpace(vector, event.getProjectionMatrix(), event.getModelViewMatrix());
            if (projected.z > 0 && projected.z < 1) {
                minX = Math.min((float) projected.x, minX);
                minY = Math.min((float) projected.y, minY);
                maxX = Math.max((float) projected.x, maxX);
                maxY = Math.max((float) projected.y, maxY);
                visible = true;
            }
        }

        highlightBounds = visible ? new Vector4f(minX, minY, maxX, maxY) : null;
    }

    private float getAlpha(long time) {
        float progress = Math.max(0.0f, 1.0f - (System.currentTimeMillis() - time) / (float) VISUAL_FADE_MS);
        return progress * progress;
    }

    private record VisualPoint(Vec3d pos, long time) {}
}