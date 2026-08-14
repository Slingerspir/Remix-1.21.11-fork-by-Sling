package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.Render3DEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.exploits.Disabler;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ColorValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.Util;
import cn.remix.util.misc.TimerUtil;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.EntityUtil;
import cn.remix.util.player.TeleportUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.ProjectUtil;
import cn.remix.util.render.Render2D;
import cn.remix.util.render.Render3D;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

@lombok.Getter
public final class TpAura extends Module {
    private static final long VISUAL_FADE_MS = 1200L;

    private final ModeValue mode = new ModeValue("Mode", "Vanilla", "Vanilla", "Paper");
    private final ModeValue priority = new ModeValue("Priority", "Distance", "Distance", "Health", "LivingTime", "Armor");
    private final NumberValue vClip = new NumberValue("VClip", 20.0, 1.0, 128.0, 1.0);
    private final NumberValue moveDistance = new NumberValue("Move Distance", 20.0, 1.0, 128.0, 1.0);
    private final NumberValue range = new NumberValue("Range", 20.0, 3.0, 128.0, 1.0);
    private final NumberValue prev = new NumberValue("Prev", 0.0, 0.0, 5.0, 0.1);
    private final BoolValue prewarmPackets = new BoolValue("Prewarm Packets", true);
    private final NumberValue prewarmPacketCount = new NumberValue("Prewarm Count", 8, 1, 20, 1, prewarmPackets::getValue);
    private final BoolValue randomOffset = new BoolValue("Random Offset", true);
    private final NumberValue offsetXZ = new NumberValue("Offset XZ", 0.05f, 0.0f, 0.3f, 0.01f, randomOffset::getValue);
    private final NumberValue offsetY = new NumberValue("Offset Y", 0.01f, 0.0f, 0.2f, 0.01f, randomOffset::getValue);
    private final BoolValue safeTargetFallback = new BoolValue("Safe Target Fallback", true);
    private final BoolValue swing = new BoolValue("Swing", true);
    private final BoolValue fakeAutoBlock = new BoolValue("Fake AutoBlock", false);
    private final BoolValue useMace = new BoolValue("Use Mace", false);
    private final BoolValue useCooldown = new BoolValue("Use Cooldown", true);
    private final NumberValue useCooldownBaseTime = new NumberValue("Cooldown Base", 0.75f, 0.1f, 1.0f, 0.05f, useCooldown::getValue);
    private final NumberValue attackDelay = new NumberValue("Attack Delay", 50, 1, 2000, 25, () -> !useCooldown.getValue());
    private final NumberValue attackTimes = new NumberValue("Attack Times", 12, 1, 200, 1);
    private final NumberValue attackWaitTicks = new NumberValue("Attack Wait Ticks", 15, 0, 100, 1);
    private final BoolValue tryMissTotem = new BoolValue("Try Miss Totem", false);
    private final NumberValue renderPosScale = new NumberValue("Render Pos Scale", 0.6, 0.2, 1.0, 0.01);
    private final ModeValue highlightTarget = new ModeValue("Highlight Target", "2D", "Off", "2D", "Glow");
    private final BoolValue renderPos = new BoolValue("Render Pos", true);
    private final ColorValue renderPosColor = new ColorValue("Render Pos Color", new Color(255, 165, 0), renderPos::getValue);
    private final BoolValue renderTrail = new BoolValue("Render Trail", true);
    private final ColorValue renderTrailColor = new ColorValue("Render Trail Color", Color.PINK, renderTrail::getValue);

    private final TimerUtil attackTimer = new TimerUtil();
    private final List<VisualPoint> visualPoints = new ArrayList<>();
    private LivingEntity target;
    private LivingEntity chaseTarget;
    private boolean renderBlock;
    private Vector4f highlightBounds;
    private PendingReturn pendingReturn;
    private int attackWaitTicksLeft;

    public TpAura() {
        super("TpAura", Category.Combat);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Aura aura = getModule(Aura.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() != null) {
            clearPendingReturn();
            chaseTarget = aura.getTarget();
            target = null;
            renderBlock = false;
            return;
        }

        if (tickPendingReturn()) {
            return;
        }

        updateTarget();
        renderBlock = fakeAutoBlock.getValue() && target != null && isHoldingSword();

        if (target == null || !isReadyToAttack() || mc.player.distanceTo(target) > range.getValue()) {
            return;
        }

        doAura();
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
        if (!highlightTarget.is("2D") || highlightBounds == null) return;

        float x = highlightBounds.x;
        float y = highlightBounds.y;
        float width = highlightBounds.z - highlightBounds.x;
        float height = highlightBounds.w - highlightBounds.y;
        Render2D.drawOutline(event.getContext(), x - 2.0f, y - 2.0f, width + 4.0f, height + 4.0f, 2.5f, Color.RED.getRGB());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getType() == PacketEvent.Type.Send) {
            if (event.getPacket() instanceof UpdatePlayerAbilitiesC2SPacket || event.getPacket() instanceof CommonPongC2SPacket) {
                event.setCancelled();
            }
            return;
        }

        if (event.getType() != PacketEvent.Type.Received) return;

        if (event.getPacket() instanceof EntityStatusS2CPacket packet && tryMissTotem.getValue() && packet.getStatus() == 35 && target != null && packet.getEntity(mc.world) == target) {
            Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d clipped = pos.add(0.0, vClip.getValue(), 0.0);
            sendMovePath(pos, clipped, false);
            attack(target);
            sendMovePath(clipped, pos, false);
            return;
        }

        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
            Disabler disabler = getModule(Disabler.class);
            if (disabler != null && disabler.isEnabled()) {
                disabler.setEnabled(false);
            }
        }
    }

    private void resetState() {
        target = null;
        chaseTarget = null;
        renderBlock = false;
        highlightBounds = null;
        pendingReturn = null;
        attackWaitTicksLeft = 0;
        visualPoints.clear();
        attackTimer.reset();
    }

    private boolean tickPendingReturn() {
        if (pendingReturn == null) {
            return false;
        }

        if (attackWaitTicksLeft > 0) {
            attackWaitTicksLeft--;
            return true;
        }

        sendMovePath(pendingReturn.from(), pendingReturn.via(), false);
        sendMovePath(pendingReturn.via(), pendingReturn.to(), false);
        syncClientPosition(pendingReturn.to());
        clearPendingReturn();
        return true;
    }

    private void clearPendingReturn() {
        pendingReturn = null;
        attackWaitTicksLeft = 0;
    }

    private void updateTarget() {
        if (isValidChaseTarget(chaseTarget)) {
            target = chaseTarget;
            setSuffix(target == null ? "" : priority.getValue());
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

    private void doAura() {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d targetPos = getPredictedPos(target, prev.getValue());
        Vec3d attackPos = safeTargetFallback.getValue() && isInvalidPosition(targetPos) ? findNearestSafePos(targetPos) : targetPos;

        if (attackPos == null) {
            Util.log("No safe attack position.");
            return;
        }

        if (mc.player.distanceTo(target) <= 3.0) {
            attack(target);
            attackTimer.reset();
            return;
        }

        Vec3d entry = TeleportUtil.findVClipVecToMove(playerPos, 1.8, false);
        Vec3d exit = TeleportUtil.findVClipVecToMove(attackPos, 1.8, false);

        if (entry == null || exit == null) {
            Util.log("No safe teleport path.");
            return;
        }

        if (Math.max(packetCount(playerPos, entry), Math.max(packetCount(entry, attackPos), Math.max(packetCount(attackPos, exit), packetCount(exit, playerPos)))) > 20) {
            Util.log("TP packet limit exceeded.");
            return;
        }

        sendPrewarmPackets();
        sendMovePath(playerPos, entry, false);
        sendMovePath(entry, attackPos, false);
        attack(target);
        int waitTicks = attackWaitTicks.getValue().intValue();
        if (waitTicks <= 0) {
            sendMovePath(attackPos, exit, false);
            sendMovePath(exit, playerPos, false);
            syncClientPosition(playerPos);
        } else {
            pendingReturn = new PendingReturn(attackPos, exit, playerPos);
            attackWaitTicksLeft = waitTicks;
        }
        attackTimer.reset();
    }

    private void sendMovePath(Vec3d from, Vec3d to, boolean onGround) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance / Math.max(0.1, moveDistance.getValue())));

        for (int i = 1; i < steps; i++) {
            Vec3d point = from.lerp(to, (double) i / steps);
            sendMovePacket(point, onGround);
            recordPoint(point);
        }

        sendMovePacket(to, onGround);
        recordPoint(to);
    }

    private void sendMovePacket(Vec3d vec, boolean onGround) {
        PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(vec.x, vec.y, vec.z, onGround, mc.player.horizontalCollision));
    }

    private void sendPrewarmPackets() {
        if (!prewarmPackets.getValue()) {
            return;
        }

        int count = prewarmPacketCount.getValue().intValue();
        if (mode.is("Vanilla")) {
            count = Math.min(count, 4);
        }

        for (int i = 0; i < count; i++) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
        }
    }

    private void syncClientPosition(Vec3d base) {
        Vec3d syncPos = randomOffset.getValue() ? getRandomOffset(base) : base;
        if (!syncPos.equals(base)) {
            sendMovePacket(syncPos, false);
            recordPoint(syncPos);
        }
        mc.player.setPosition(syncPos.x, syncPos.y, syncPos.z);
    }

    private Vec3d getRandomOffset(Vec3d base) {
        double xz = offsetXZ.getValue();
        double y = offsetY.getValue();
        if (xz <= 0.0 && y <= 0.0) {
            return base;
        }

        List<Vec3d> offsets = new ArrayList<>();
        offsets.add(base.add(xz, y, 0.0));
        offsets.add(base.add(-xz, y, 0.0));
        offsets.add(base.add(0.0, y, xz));
        offsets.add(base.add(0.0, y, -xz));
        offsets.add(base.add(xz, y, xz));
        offsets.add(base.add(-xz, y, -xz));
        offsets.add(base.add(xz, y, -xz));
        offsets.add(base.add(-xz, y, xz));
        Collections.shuffle(offsets);

        for (Vec3d offset : offsets) {
            if (!isInvalidPosition(offset)) {
                return offset;
            }
        }

        Vec3d verticalOffset = base.add(0.0, y, 0.0);
        return isInvalidPosition(verticalOffset) ? base : verticalOffset;
    }

    private void attack(LivingEntity entity) {
        if (useMace.getValue() && !mc.player.getMainHandStack().isOf(Items.MACE)) {
            return;
        }

        for (int i = 0; i < attackTimes.getValue().intValue(); i++) {
            PacketUtil.sendPacket(PlayerInteractEntityC2SPacket.attack(entity, false));
        }

        if (swing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
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
            if (alpha <= 0.01f) continue;

            if (renderPos.getValue()) {
                float distanceAlpha = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(point.pos()) < 6.0 ? 0.4f : 1.0f;
                Render3D.drawOutlinedBox(
                        event.getMatrixStack(),
                        getPointBox(point.pos()),
                        0.02,
                        ColorUtil.applyAlpha(renderPosColor.getValue().getRGB(), Math.round(255.0f * alpha * distanceAlpha)),
                        false
                );
            }

            if (renderTrail.getValue() && i > 0) {
                VisualPoint prevPoint = visualPoints.get(i - 1);
                float lineAlpha = Math.min(alpha, getAlpha(prevPoint.time()));
                if (lineAlpha > 0.01f) {
                    renderTrailSegment(event, prevPoint.pos(), point.pos(), lineAlpha);
                }
            }
        }
    }

    private void updateHighlightBounds(Render3DEvent event) {
        if (!highlightTarget.is("2D") || target == null) {
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

    private void renderTrailSegment(Render3DEvent event, Vec3d from, Vec3d to, float alpha) {
        int color = ColorUtil.applyAlpha(renderTrailColor.getValue().getRGB(), Math.round(255.0f * alpha));
        int steps = Math.max(2, (int) Math.ceil(from.distanceTo(to) / 0.35));

        for (int i = 0; i <= steps; i++) {
            Vec3d point = from.lerp(to, (double) i / steps);
            double pad = 0.035;
            Render3D.drawBox(event.getMatrixStack(), new Box(
                    point.x - pad, point.y - pad, point.z - pad,
                    point.x + pad, point.y + pad, point.z + pad
            ), color, false);
        }
    }

    private Box getPointBox(Vec3d pos) {
        double halfWidth = renderPosScale.getValue() / 2.0;
        return new Box(pos.x - halfWidth, pos.y, pos.z - halfWidth, pos.x + halfWidth, pos.y + 1.8, pos.z + halfWidth);
    }

    private Vec3d getPredictedPos(LivingEntity entity, double ticks) {
        Vec3d currentPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        Vec3d lastPos = currentPos.subtract(entity.getX() - entity.lastX, entity.getY() - entity.lastY, entity.getZ() - entity.lastZ);
        return lastPos.lerp(currentPos, Math.max(0.0, Math.min(1.0, ticks)));
    }

    private int packetCount(Vec3d from, Vec3d to) {
        return (int) Math.ceil(from.distanceTo(to) / Math.max(0.1, moveDistance.getValue()));
    }

    private boolean isInvalidPosition(Vec3d pos) {
        if (mc.world == null || mc.player == null) {
            return true;
        }

        Box box = mc.player.getBoundingBox().offset(pos.subtract(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
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

    private boolean isHoldingSword() {
        return mc.player != null && mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
    }

    private record VisualPoint(Vec3d pos, long time) {}

    private record PendingReturn(Vec3d from, Vec3d via, Vec3d to) {}
}
