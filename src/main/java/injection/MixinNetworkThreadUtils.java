package injection;

import cn.remix.event.impl.PacketEvent;
import cn.remix.util.IMinecraft;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.PacketApplyBatcher;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkThreadUtils.class)
public class MixinNetworkThreadUtils implements IMinecraft {

    @Inject(method = "forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V", at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void forceMainThread(Packet<T> packet, T listener, PacketApplyBatcher batcher, CallbackInfo ci) throws OffThreadException {
        if (!batcher.isOnThread()) {
            PacketEvent event = new PacketEvent(packet, PacketEvent.Type.Received);
            instance.getEventManager().call(event);

            if (event.isCancelled()) {
                ci.cancel();
                throw OffThreadException.INSTANCE;
            }
        }
    }
}