package cn.remix.module.impl.move;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.NumberValue;
import cn.remix.util.network.PacketUtil;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;

public final class ECFly extends Module {
    private final NumberValue flySpeed = new NumberValue("Speed", 1.0f, 0.1f, 5.0f, 0.1f);
    private final NumberValue fuckEC = new NumberValue("FUCK EC", 200, 10, 5000, 50);

    public ECFly() {
        super("ECFly", Category.Move);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        mc.player.getAbilities().flying = true;
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;

        if (!mc.player.isCreative() && !mc.player.isSpectator()) {
            mc.player.getAbilities().flying = false;
        }

        PlayerAbilities abilities = new PlayerAbilities();
        abilities.flying = false;
        abilities.allowFlying = mc.player.getAbilities().allowFlying;
        PacketUtil.sendPacketNoEvent(new UpdatePlayerAbilitiesC2SPacket(abilities));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        PlayerAbilities creative = new PlayerAbilities();
        creative.flying = true;
        creative.allowFlying = true;
        creative.creativeMode = true;
        creative.invulnerable = true;

        PlayerAbilities op = new PlayerAbilities();
        op.flying = true;
        op.allowFlying = true;
        op.creativeMode = true;
        op.invulnerable = true;
        op.setWalkSpeed(0.1f);
        op.setFlySpeed(0.05f);

        int burst = fuckEC.getValue().intValue();
        for (int i = 0; i < burst; i++) {
            PacketUtil.sendPacketNoEvent(new UpdatePlayerAbilitiesC2SPacket(creative));
            PacketUtil.sendPacketNoEvent(new UpdatePlayerAbilitiesC2SPacket(op));
        }

        mc.player.getAbilities().flying = true;

        double yMotion = 0.0;
        if (mc.options.jumpKey.isPressed()) {
            yMotion = flySpeed.getValue();
        } else if (mc.options.sneakKey.isPressed()) {
            yMotion = -flySpeed.getValue();
        }

        var motion = mc.player.getVelocity();
        mc.player.setVelocity(motion.x, yMotion, motion.z);
    }
}