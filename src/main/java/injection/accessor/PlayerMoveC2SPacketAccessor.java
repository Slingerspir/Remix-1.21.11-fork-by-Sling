package injection.accessor;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerMoveC2SPacket.class)
public interface PlayerMoveC2SPacketAccessor {
    @Mutable
    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Mutable
    @Accessor("y")
    void setY(double y);

    @Mutable
    @Accessor("yaw")
    void setYaw(float yaw);
}