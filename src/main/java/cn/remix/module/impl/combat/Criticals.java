package cn.remix.module.impl.combat;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.MotionEvent;
import cn.remix.event.impl.MoveInputEvent;
import cn.remix.event.impl.PacketEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import injection.accessor.ClientPlayerEntityAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class Criticals extends Module {
    private final ModeValue mode = new ModeValue("Mode", "Packet", "No Ground", "NCP", "Strict", "Sentinel", "Packet", "Heypixel");
    private final BoolValue autoJump = new BoolValue("Auto Jump", true, () -> mode.is("Heypixel"));
    private final BoolValue skipTicks = new BoolValue("SkipTicks", true, () -> mode.is("Heypixel"));
    private final NumberValue critRange = new NumberValue("Critical Range", 3.0f, 1, 3.2f, 0.1f, () -> mode.is("Heypixel"));

    private boolean armedSkipTick;

    public Criticals() {
        super("Criticals", Category.Combat);
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.player == null || !mode.is("Heypixel")) return;
        if (!autoJump.getValue() || mc.world == null) return;
        if (mc.options.jumpKey.isPressed()) return;

        Aura aura = getModule(Aura.class);
        if (aura == null || !aura.isEnabled() || aura.getTarget() == null) return;

        if (mc.player.isOnGround() && mc.player.distanceTo(aura.getTarget()) <= 3.0f) {
            event.setJumping(true);
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.player == null) return;

        if (mode.is("Heypixel")) {
            if (!(event.getEntity() instanceof LivingEntity living)) return;
            if (cantCrit(living)) {
                armedSkipTick = false;
                return;
            }

            if (mc.player.getVelocity().y < 0.0 && !mc.player.isOnGround()
                    && mc.player.distanceTo(living) <= critRange.getValue()) {
                if (skipTicks.getValue() && !armedSkipTick) {
                    armedSkipTick = true;
                }
                cancelSprint();
            } else {
                armedSkipTick = false;
            }
            return;
        }

        switch (mode.getValue()) {
            case "NCP" -> {
                sendCritPacket(0.000000271875, false);
                sendCritPacket(0, false);
            }

            case "Packet" -> {
                sendCritPacket(0.0625, false);
                sendCritPacket(0, false);
            }

            case "Strict" -> {
                sendCritPacket(0.062600301692775, false);
                sendCritPacket(0.07260029960661, false);
                sendCritPacket(0., false);
                sendCritPacket(0., false);
            }

            case "Sentinel" -> {
                if (!mc.player.isOnGround()) {
                    sendCritPacket(-0.000001, true);
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null || !mode.is("Heypixel") || !skipTicks.getValue()) return;
        if (event.getType() != PacketEvent.Type.Send) return;
        if (!armedSkipTick) return;
        if (event.getPacket() instanceof PlayerMoveC2SPacket) {
            event.setCancelled();
            armedSkipTick = false;
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        setSuffix(mode.getValue());
        event.setOnGround(false);
    }

    private boolean cantCrit(LivingEntity target) {
        return mc.player.isClimbing()
                || mc.player.isTouchingWater()
                || mc.player.isInLava()
                || mc.player.hasVehicle()
                || hasHeadBlock()
                || target.hurtTime > 10
                || target.getHealth() <= 0.0f;
    }

    private boolean hasHeadBlock() {
        if (mc.player == null || mc.world == null) return false;
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0, 0.5, 0)).iterator().hasNext();
    }

    private void cancelSprint() {
        if (mc.player == null) return;
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }
    }

    private void sendCritPacket(double offset, boolean full) {
        if (mc.player == null) return;

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean h = mc.player.horizontalCollision;
        if (!full) {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + offset, z, false, h));
        } else {
            PacketUtil.sendPacket(new PlayerMoveC2SPacket.Full(x, mc.player.getY() + offset, z, ((ClientPlayerEntityAccessor) mc.player).getLastYaw(), ((ClientPlayerEntityAccessor) mc.player).getLastPitch(), false, h));
        }
    }
}
