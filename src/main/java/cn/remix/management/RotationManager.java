package cn.remix.management;

import cn.remix.event.base.annotation.EventPriority;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.*;
import cn.remix.management.movement.MovementCorrection;
import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.player.AntiLava;
import cn.remix.module.impl.player.Derp;
import cn.remix.module.impl.world.Scaffold;
import cn.remix.util.IMinecraft;
import cn.remix.util.Util;
import cn.remix.util.player.MovementUtil;
import cn.remix.util.player.RotationUtil;
import injection.accessor.PlayerMoveC2SPacketAccessor;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * RotationManager
 * @author DSJ
 */
public class RotationManager implements IMinecraft {
    public static float[] currentRotations;
    public static float[] targetRotations;
    public static float[] lastRotations;

    public static MovementCorrection correctMovement;
    private static double rotationSpeed;
    private static boolean enabled;

    public RotationManager() {
        instance.getEventManager().register(this);
    }

    public static void setRotations(float[] rotations, double rotationSpeed, MovementCorrection correctMovement) {
        RotationManager.targetRotations = rotations;
        RotationManager.rotationSpeed = rotationSpeed;
        RotationManager.correctMovement = correctMovement;

        enabled = true;
    }

    @EventTarget
    @EventPriority(999)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.player == null) return;

        Aura aura = instance.getModuleManager().getModule(Aura.class);
        Scaffold scaffold = instance.getModuleManager().getModule(Scaffold.class);
        AntiLava antiLava = instance.getModuleManager().getModule(AntiLava.class);
        Derp derp = instance.getModuleManager().getModule(Derp.class);

        if (derp.isEnabled() && derp.getPriority().getValue() && derp.getRotations() != null) {
            setRotations(derp.getRotations(), 180, MovementCorrection.None);
        } else if (antiLava.isEnabled() && antiLava.getRotations() != null) {
            setRotations(antiLava.getRotations(), 180, antiLava.getMovementFix().getValue() ? MovementCorrection.Silent : MovementCorrection.None);
        } else if (scaffold.isEnabled() && scaffold.isCanRotation() && scaffold.getRotations() != null) {
            setRotations(scaffold.getRotations(), scaffold.getRotationSpeed(), scaffold.getMovementFix().getValue() ? MovementCorrection.Silent : MovementCorrection.None);
        } else if (aura.isEnabled() && aura.getTarget() != null && aura.getRotations() != null) {
            setRotations(aura.getRotations(), aura.getRotationSpeed().getValue(), aura.getMovementFixMode().is("None") ? MovementCorrection.None : (aura.getMovementFixMode().is("Silent") ? MovementCorrection.Silent : MovementCorrection.Strict));
        } else if (derp.isEnabled() && derp.getRotations() != null) {
            setRotations(derp.getRotations(), 180, MovementCorrection.None);
        } else {
            enabled = false;
        }

        lastRotations = currentRotations;
        currentRotations = RotationUtil.getSmoothRotation(lastRotations, targetRotations, rotationSpeed + Math.random());
        mc.gameRenderer.updateCrosshairTarget(1.0f);
    }

    @EventTarget
    @EventPriority(0)
    public void onPacket(PacketEvent packetEvent) {
        if (packetEvent.getPacket() instanceof PlayerMoveC2SPacket movePacket) {
            float yaw = movePacket.getYaw(0);
            if (yaw < 360 && yaw > -360) {
                ((PlayerMoveC2SPacketAccessor) movePacket).setYaw(yaw + 720);
            }
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onLook(LookEvent e) {
        if (mc.player == null) return;

        if (canRotation()) {
            e.setRotation(currentRotations);
            e.setLastRotation(lastRotations);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onStrafe(StrafeEvent e) {
        if (mc.player == null) return;

        if (canRotation() && correctMovement != MovementCorrection.None) {
            e.setYaw(currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onJump(JumpEvent e) {
        if (mc.player == null) return;

        if (canRotation() && correctMovement != MovementCorrection.None) {
            e.setYaw(currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;

        if (e.isPre()) {
            if (!enabled || currentRotations == null || lastRotations == null || targetRotations == null) {
                currentRotations = targetRotations = lastRotations = new float[]{mc.player.getYaw(), mc.player.getPitch()};
            }

            if (canRotation()) {
                e.setYaw(currentRotations[0]);
                e.setPitch(currentRotations[1]);
            }
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onMoveInput(MoveInputEvent e) {
        if (canRotation() && correctMovement == MovementCorrection.Silent) {
            MovementUtil.fixMovement(e, currentRotations[0]);
        }
    }

    @EventTarget
    @EventPriority(999)
    public void onRotation(RenderRotationEvent e) {
        if (mc.player == null) return;

        if (canRotation()) {
            e.setRotation(currentRotations);
            e.setLastRotation(lastRotations);
        }
    }

    private boolean canRotation() {
        return enabled && currentRotations != null && lastRotations != null && targetRotations != null;
    }
}
