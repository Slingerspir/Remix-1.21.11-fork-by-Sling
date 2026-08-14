package injection;

import cn.remix.event.impl.PacketEvent;
import cn.remix.ui.screen.impl.proxy.ProxyScreen;
import cn.remix.util.IMinecraft;
import cn.remix.util.network.PacketUtil;
import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class MixinClientConnection implements IMinecraft {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void send(Packet<?> packet, CallbackInfo ci) {
        if (mc.player == null || mc.world == null) return;
        if (PacketUtil.getPackets().remove(packet)) return;

        PacketEvent event = new PacketEvent(packet, PacketEvent.Type.Send);
        instance.getEventManager().call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Mixin(targets = "net.minecraft.network.ClientConnection$1")
    public static class Proxy {
        @Inject(method = "initChannel", at = @At("TAIL"), remap = false)
        private void onInitChannel(Channel channel, CallbackInfo ci) {
            if (ProxyScreen.getProxy() != null) {
                channel.pipeline().addFirst("proxy", ProxyScreen.getProxy().getHandler());
            }
        }
    }
}