package cn.remix.protocol.heypixel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;

/**
 * 注册 heypixel 相关频道的原始负载类型。
 * 注册后：入站同 id 的未知负载会被解码为 {@link RawPayload}（拿到原始字节），
 * 出站可用 ClientPlayNetworking 发送 RawPayload。
 */
public final class ProtocolPayloads {

    public static final Identifier MAIN_CHANNEL = Identifier.of("heypixel", "s2cevent");
    public static final Identifier SKIN_CHANNEL = Identifier.of("heypixel", "sync_skins");
    public static final Identifier FORM_CHANNEL = Identifier.of("floodgate", "form");
    public static final Identifier NETEASE_CHANNEL = Identifier.of("floodgate", "netease");

    public static final CustomPayload.Id<RawPayload> MAIN_ID = new CustomPayload.Id<>(MAIN_CHANNEL);
    public static final CustomPayload.Id<RawPayload> SKIN_ID = new CustomPayload.Id<>(SKIN_CHANNEL);
    public static final CustomPayload.Id<RawPayload> FORM_ID = new CustomPayload.Id<>(FORM_CHANNEL);
    public static final CustomPayload.Id<RawPayload> NETEASE_ID = new CustomPayload.Id<>(NETEASE_CHANNEL);

    private static boolean registered;

    private ProtocolPayloads() {}

    /** 幂等；须在建立连接前调用（Client.init）。 */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        registerOne(MAIN_ID);
        registerOne(SKIN_ID);
        registerOne(FORM_ID);
        registerOne(NETEASE_ID);
    }

    private static void registerOne(CustomPayload.Id<RawPayload> id) {
        PacketCodec<RegistryByteBuf, RawPayload> codec = RawPayload.codec(id);
        PayloadTypeRegistry.playS2C().register(id, codec);
        PayloadTypeRegistry.playC2S().register(id, codec);
    }
}
