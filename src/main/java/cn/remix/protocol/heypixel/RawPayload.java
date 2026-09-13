package cn.remix.protocol.heypixel;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * 承载原始字节的自定义负载：用于在 1.21+ 收发未知频道的裸数据
 * （原版 UnknownCustomPayload 会丢弃 payload 字节）。
 */
public record RawPayload(CustomPayload.Id<RawPayload> id, byte[] data) implements CustomPayload {

    public static CustomPayload.Id<RawPayload> id(String value) {
        Identifier identifier = Objects.requireNonNull(Identifier.tryParse(value), "invalid channel: " + value);
        return new CustomPayload.Id<>(identifier);
    }

    public static PacketCodec<RegistryByteBuf, RawPayload> codec(CustomPayload.Id<RawPayload> id) {
        return PacketCodec.of(
                (value, buf) -> buf.writeBytes(value.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new RawPayload(id, bytes);
                });
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return id;
    }

    public int length() {
        return data == null ? 0 : data.length;
    }
}
