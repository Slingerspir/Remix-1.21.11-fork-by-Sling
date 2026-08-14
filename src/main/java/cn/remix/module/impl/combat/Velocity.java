package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.player.RotationUtil;
import injection.accessor.EntityVelocityUpdateS2CPacketAccessor;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

@Getter
public class Velocity extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Normal", "Normal", "Packet", "Reduce");
    private final NumberValue horizontal = new NumberValue("Horizontal", 0, 0, 100, 1, () -> mode.is("Packet"));
    private final NumberValue vertical = new NumberValue("Vertical", 0, 0, 100, 1, () -> mode.is("Packet"));
    private LivingEntity attackTarget = null;
    private boolean jump = false;
    private boolean attacking;
    private int reduceTicks;
    private int resetTicks;

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
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;

        if (jump) {
            event.setJumping(true);
            jump = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;
        setSuffix(mode.getValue());
        Packet<?> packet = event.getPacket();
        if (event.getType() == PacketEvent.Type.Received) {
            if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
                if (velocity.getEntityId() == mc.player.getId()) {
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