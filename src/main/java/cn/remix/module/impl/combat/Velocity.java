package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.Render2DEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import cn.remix.util.player.RotationUtil;
import cn.remix.util.render.ColorUtil;
import cn.remix.util.render.Render2D;
import injection.accessor.EntityVelocityUpdateS2CPacketAccessor;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

@Getter
public class Velocity extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Packet", "Reduce", "JumpReset", "heypixel", "Heypixel2");
    private final NumberValue horizontal = new NumberValue("Horizontal", 0, 0, 100, 1, () -> mode.is("Packet"));
    private final NumberValue vertical = new NumberValue("Vertical", 0, 0, 100, 1, () -> mode.is("Packet"));

    // ===== Heypixel2（AntiKB / NoXZ-Alink 移植）=====
    private final NumberValue h2MaxDelay = new NumberValue("H2 Max Delay", 10, 1, 120, 1, () -> mode.is("Heypixel2"));
    private final BoolValue h2AutoCount = new BoolValue("H2 Auto Count", true, () -> mode.is("Heypixel2"));
    private final NumberValue h2AttackAmount = new NumberValue("H2 Attack Amount", 5, 1, 20, 1, () -> mode.is("Heypixel2") && !h2AutoCount.getValue());
    private final BoolValue h2SprintCheck = new BoolValue("H2 Sprint Check", false, () -> mode.is("Heypixel2"));
    private final BoolValue h2RequireAura = new BoolValue("H2 Require Aura", true, () -> mode.is("Heypixel2"));
    private final BoolValue h2RenderBar = new BoolValue("H2 Render Bar", false, () -> mode.is("Heypixel2"));

    private LivingEntity attackTarget = null;
    private boolean jump = false;
    private boolean forward = false;
    private boolean attacking;
    private int reduceTicks;
    private int resetTicks;
    private int hurtWindowTicks;

    // Heypixel2 状态
    private final Deque<Packet<?>> h2Queue = new ArrayDeque<>();
    private boolean h2Suspending;
    private int h2DelayTicks;
    private int h2FlagCooldown;
    private LivingEntity h2Target;
    private int h2AttacksRemaining;
    private int h2AttackCooldown;
    private double h2KbX;
    private double h2KbY;
    private double h2KbZ;

    public Velocity() {
        super("Velocity", Category.Combat);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    private void reset() {
        attackTarget = null;
        attacking = false;
        reduceTicks = 0;
        resetTicks = 0;
        jump = false;
        forward = false;
        hurtWindowTicks = 0;
        resetH2();
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (jump) {
            event.setJumping(true);
            jump = false;
        }
        if (forward) {
            event.setForward(1);
            forward = false;
        }
        if (mode.is("Heypixel2") && h2Suspending) {
            event.setForward(1);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        setSuffix(mode.getValue());
        Packet<?> packet = event.getPacket();

        if (mode.is("Heypixel2")) {
            if (event.getType() == PacketEvent.Type.Received) {
                h2Receive(packet, event);
            }
            return;
        }

        if (event.getType() == PacketEvent.Type.Received) {
            if (mode.is("heypixel") && packet instanceof EntityDamageS2CPacket damage) {
                if (damage.entityId() == mc.player.getId()) {
                    hurtWindowTicks = 3;
                }
            }
            if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                if (velocity.getEntityId() == mc.player.getId()) {
                    if (mode.is("heypixel")) {
                        if (hurtWindowTicks > 0) {
                            hurtWindowTicks--;
                            event.setCancelled(true);
                        }
                        return;
                    }
                    switch (mode.getValue()) {
                        case "Normal" ->
                                event.setCancelled(true);

                        case "Packet" -> {
                            EntityVelocityUpdateS2CPacketAccessor accessor = (EntityVelocityUpdateS2CPacketAccessor) velocity;
                            double x = velocity.getVelocity().x * (horizontal.getValue() / 100.0);
                            double y = velocity.getVelocity().y * (vertical.getValue() / 100.0);
                            double z = velocity.getVelocity().z * (horizontal.getValue() / 100.0);
                            accessor.setVelocity(new Vec3d(x, y, z));
                        }

                        case "Reduce" -> {
                            if (velocity.getEntityId() == mc.player.getId() && velocity.getVelocity().y > 0) {
                                Entity entity = getEntity();
                                if (entity instanceof PlayerEntity livingEntity) {
                                    reduceTicks = 5;
                                    attackTarget = livingEntity;
                                    jump = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (mode.is("Heypixel2")) {
            h2Tick();
            return;
        }

        if (mode.is("heypixel")) {
            if (hurtWindowTicks > 0) hurtWindowTicks--;
            return;
        }

        if (mode.is("JumpReset")) {
            int hurt = mc.player.hurtTime;
            if (hurt >= 8) {
                jump = true;
                forward = true;
            } else if (hurt < 7 && hurt > 0) {
                jump = false;
                forward = false;
            }
            return;
        }

        if (mode.is("Reduce")) {
            if (resetTicks > 0) {
                resetTicks--;
                if (resetTicks <= 0) {
                    attacking = false;
                }
            }

            if (attackTarget != null && reduceTicks > 0) {
                if (RotationUtil.getDistanceToEntity(attackTarget) >= 3.0) {
                    return;
                }

                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                    mc.interactionManager.attackEntity(mc.player, attackTarget);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    Vec3d velocity = mc.player.getVelocity();
                    mc.player.setVelocity(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
                    attackTarget = null;
                    reduceTicks--;
                    resetTicks = 3;
                    attacking = true;
                }
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!mode.is("Heypixel2") || !h2RenderBar.getValue() || mc.player == null) return;
        if (!h2Suspending) return;

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float w = 100;
        float x = sw / 2f - w / 2f;
        float y = sh / 2f + sh * 0.10f;
        float progress = Math.min(1f, h2DelayTicks / (float) Math.max(1, h2MaxDelay.getValue().intValue()));

        Render2D.drawRect(event.getContext(), x, y, w, 2, 0xB41E1E24);
        Render2D.drawRect(event.getContext(), x, y, w * progress, 2, ColorUtil.applyAlpha(0xFF00B4FF, 230));
    }

    // =====================================================================
    // Heypixel2 —— NoXZ / Alink：悬浮收包 → 落地疾跑释放 + 攻击爆发
    // =====================================================================

    private void h2Receive(Packet<?> packet, PacketEvent event) {
        if (packet instanceof GameStateChangeS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
            resetH2();
            return;
        }

        if (packet instanceof PlayerPositionLookS2CPacket) {
            if (h2Suspending) releaseH2();
            resetH2Suspension();
            h2FlagCooldown = 2;
            return;
        }

        if (h2FlagCooldown > 0) return;

        if (h2Suspending) {
            if (packet instanceof EntityS2CPacket move && mc.world != null && move.getEntity(mc.world) == mc.player) {
                return;
            }
            if (h2Allowed(packet)) return;
            event.setCancelled(true);
            h2Queue.add(packet);
            return;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == mc.player.getId()) {
            Entity current = getEntity();
            LivingEntity target = current instanceof LivingEntity living ? living : null;
            if (target == null && h2RequireAura.getValue()) {
                resetH2();
                return;
            }

            Vec3d vel = velocity.getVelocity();
            double total = vel.length();
            if (vel.y > 0) {
                storeKb(vel);
                if (!mc.player.isOnGround()) {
                    h2EnterSuspension();
                    event.setCancelled(true);
                } else if (target != null && h2InReach(target)) {
                    h2Target = target;
                    h2AttacksRemaining = h2AttackCount(vel);
                } else {
                    h2EnterSuspension();
                    event.setCancelled(true);
                }
            } else if (total > 0.01 && h2Suspending) {
                storeKb(vel);
            }
        }
    }

    private void h2Tick() {
        if (h2AttackCooldown > 0) h2AttackCooldown--;
        if (h2FlagCooldown > 0) h2FlagCooldown--;

        if (mc.player.isDead() || !mc.player.isAlive()) {
            if (h2Suspending) releaseH2();
            resetH2();
            return;
        }

        if (h2Suspending) {
            h2DelayTicks++;
            if (h2DelayTicks >= h2MaxDelay.getValue().intValue()) {
                releaseH2();
                resetH2Suspension();
                return;
            }

            if (mc.player.isOnGround()) {
                LivingEntity target = h2CurrentTarget();
                boolean canAttack = target != null && h2InReach(target);
                if (canAttack && mc.player.isSprinting()) {
                    releaseH2();
                    h2Target = target;
                    h2AttacksRemaining = h2AttackCountFromStored();
                    resetH2Suspension();
                } else if (!canAttack) {
                    releaseH2();
                    resetH2Suspension();
                }
            }
            return;
        }

        if (h2AttacksRemaining > 0 && h2Target != null) {
            h2DoAttack();
        }
    }

    private void h2DoAttack() {
        if (h2Target == null || !h2Target.isAlive() || mc.player == null || mc.interactionManager == null) {
            h2AttacksRemaining = 0;
            h2Target = null;
            return;
        }
        if (h2AttackCooldown > 0) return;
        if (!h2InReach(h2Target)) {
            h2AttacksRemaining = 0;
            h2Target = null;
            return;
        }
        if (h2SprintCheck.getValue() && !mc.player.isSprinting()) return;

        boolean wasSprinting = mc.player.isSprinting();
        if (wasSprinting) mc.player.setSprinting(false);
        mc.interactionManager.attackEntity(mc.player, h2Target);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (wasSprinting) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocity(velocity.x * 0.6, velocity.y, velocity.z * 0.6);
        }

        h2AttacksRemaining--;
        h2AttackCooldown = 2;
    }

    private LivingEntity h2CurrentTarget() {
        Entity entity = getEntity();
        return entity instanceof LivingEntity living && h2InReach(living) ? living : null;
    }

    private boolean h2InReach(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d closest = RotationUtil.getNearestPointBB(entity.getBoundingBox());
        return eye.distanceTo(closest) <= 3.7;
    }

    private int h2AttackCount(Vec3d velocity) {
        if (!h2AutoCount.getValue()) return h2AttackAmount.getValue().intValue();
        double magnitude = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        if (magnitude < 1000) return 0;
        if (magnitude < 2000) return 3;
        if (magnitude < 10000) return 4;
        return 5;
    }

    private int h2AttackCountFromStored() {
        if (!h2AutoCount.getValue()) return h2AttackAmount.getValue().intValue();
        double magnitude = Math.sqrt(h2KbX * h2KbX + h2KbY * h2KbY);
        if (magnitude < 1000) return 0;
        if (magnitude < 2000) return 3;
        if (magnitude < 10000) return 4;
        return 5;
    }

    private boolean h2Allowed(Packet<?> packet) {
        return packet instanceof EntityVelocityUpdateS2CPacket
                || packet instanceof HealthUpdateS2CPacket
                || packet instanceof GameStateChangeS2CPacket
                || packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof PlayerRespawnS2CPacket
                || packet instanceof PlaySoundS2CPacket
                || packet instanceof ChatMessageS2CPacket
                || packet instanceof GameMessageS2CPacket
                || packet instanceof TitleS2CPacket
                || packet instanceof SubtitleS2CPacket
                || packet instanceof PlayerListS2CPacket
                || packet instanceof DisconnectS2CPacket
                || packet instanceof CloseScreenS2CPacket
                || packet instanceof EntityDamageS2CPacket
                || (packet instanceof EntityAnimationS2CPacket anim && anim.getEntityId() != mc.player.getId());
    }

    private void storeKb(Vec3d velocity) {
        h2KbX = velocity.x;
        h2KbY = velocity.y;
        h2KbZ = velocity.z;
    }

    private void h2EnterSuspension() {
        h2Suspending = true;
        h2DelayTicks = 0;
    }

    private void resetH2Suspension() {
        h2Suspending = false;
        h2DelayTicks = 0;
    }

    private void releaseH2() {
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

    private void resetH2() {
        releaseH2();
        resetH2Suspension();
        h2FlagCooldown = 0;
        h2Target = null;
        h2AttacksRemaining = 0;
        h2AttackCooldown = 0;
        h2KbX = h2KbY = h2KbZ = 0;
    }

    private Entity getEntity() {
        Aura aura = getModule(Aura.class);
        HitResult hitResult = mc.crosshairTarget;
        Entity entity = null;

        if (aura.isEnabled() && aura.getTarget() != null) {
            entity = aura.getTarget();
        } else {
            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                entity = ((EntityHitResult) hitResult).getEntity();
            }
        }
        return entity;
    }
}
