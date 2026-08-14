package cn.remix.module.impl.player;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.util.Util;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.Vec3d;

public class LightningTracker extends Module {

    public LightningTracker() {
        super("LightningTracker", Category.Player);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof EntitySpawnS2CPacket packet) {
            if (packet.getEntityType() == EntityType.LIGHTNING_BOLT) {
                int x = (int) packet.getX();
                int y = (int) packet.getY();
                int z = (int) packet.getZ();

                double distance = mc.player.getEntityPos().distanceTo(new Vec3d(x, y, z));
                Util.log("Lightning struck at " + x + ", " + y + ", " + z + " (" + (int) distance + " blocks away)");
            }
        }
    }
}