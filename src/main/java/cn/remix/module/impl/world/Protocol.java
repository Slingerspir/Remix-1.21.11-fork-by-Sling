package cn.remix.module.impl.world;

import cn.remix.Client;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.event.impl.TickEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.StringValue;
import cn.remix.protocol.heypixel.HeyPixelId1;
import cn.remix.protocol.heypixel.HeyPixelProtocolRuntime;
import cn.remix.protocol.heypixel.RawPayload;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Protocol —— HeyPixel 协议移植（观测/解析 S2C，可选应答）。
 * 原始字节收发见 cn.remix.protocol.heypixel.{ProtocolPayloads, RawPayload}。
 */
public final class Protocol extends Module {
    private final StringValue hosts = new StringValue("Hosts", "pc.bjdmc.net,*.bjdmc.net");
    private final BoolValue traceLogger = new BoolValue("Trace Logger", false);
    private final BoolValue observeOnly = new BoolValue("Observe Only", true);
    private final BoolValue allowLiveSend = new BoolValue("Allow Live Send", false);
    private final BoolValue strictProviderGate = new BoolValue("Strict Provider Gate", true);

    private static final Path CONFIG_DIR = Path.of(Client.name, "protocol");

    private final HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(mc, CONFIG_DIR);
    private final ConcurrentLinkedQueue<Inbound> queue = new ConcurrentLinkedQueue<>();

    private record Inbound(Identifier channel, byte[] data) {
    }

    public Protocol() {
        super("Protocol", Category.World);
    }

    @Override
    public void onEnable() {
        HeyPixelId1.install(runtime, mc, CONFIG_DIR);
        updateRuntimeSettings();
        runtime.start();
    }

    @Override
    public void onDisable() {
        runtime.stop();
        queue.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        Inbound inbound;
        while ((inbound = queue.poll()) != null) {
            runtime.handle(inbound.channel(), inbound.data());
        }
        updateRuntimeSettings();
        runtime.tick();
        setSuffix(runtime.isActiveForCurrentServer() ? "HeyPixel" : "Idle");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        // 注意：Received 事件在 netty 线程触发，这里只做入队，主线程 tick 再处理
        if (event.getType() != PacketEvent.Type.Received) return;
        if (!(event.getPacket() instanceof CustomPayloadS2CPacket packet)) return;
        if (!(packet.payload() instanceof RawPayload raw)) return;
        queue.add(new Inbound(raw.id().id(), raw.data()));
    }

    public HeyPixelProtocolRuntime getRuntime() {
        return runtime;
    }

    private void updateRuntimeSettings() {
        runtime.configure(
                hosts.getValue(),
                traceLogger.getValue(),
                observeOnly.getValue(),
                allowLiveSend.getValue(),
                strictProviderGate.getValue()
        );
    }
}
