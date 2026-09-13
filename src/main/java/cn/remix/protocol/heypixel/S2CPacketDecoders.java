package cn.remix.protocol.heypixel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.InflaterInputStream;

public final class S2CPacketDecoders {
    private S2CPacketDecoders() {
    }

    public static WrappedPacket decodeWrapper(byte[] wire) {
        if (wire.length < 4) throw new IllegalArgumentException("S2C wrapper is shorter than int32 id");
        int id = (wire[0] & 0xff) << 24 | (wire[1] & 0xff) << 16
            | (wire[2] & 0xff) << 8 | wire[3] & 0xff;
        return new WrappedPacket(id, Arrays.copyOfRange(wire, 4, wire.length));
    }

    public static Id101Challenge decodeId101(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        Id101Challenge result = new Id101Challenge(
            reader.readUuid(),
            reader.readLong(),
            reader.readString(),
            reader.readString()
        );
        requireFullyConsumed(101, reader);
        return result;
    }

    public static List<Id111Record> decodeId111(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        int count = reader.readArrayHeader();
        List<Id111Record> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new Id111Record(
                reader.readString(),
                reader.readInt(),
                reader.readInt(),
                reader.readString(),
                reader.readString(),
                reader.readString(),
                reader.readString()
            ));
        }
        requireFullyConsumed(111, reader);
        return List.copyOf(result);
    }

    public static Id112Update decodeId112(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        int operation = reader.readInt();
        List<String> entries = new ArrayList<>();
        while (reader.hasRemaining()) entries.add(reader.readString());
        return new Id112Update(operation, List.copyOf(entries));
    }

    public static String decodeId114(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        String token = reader.readString();
        requireFullyConsumed(114, reader);
        return token;
    }

    public static JsonPayload decodeJsonPacket(int packetId, byte[] payload) {
        if (packetId != 113 && packetId != 115 && packetId != 116
            && packetId != 118 && packetId != 119) {
            throw new IllegalArgumentException("packet " + packetId + " is not a recovered JSON decoder");
        }
        return inflateOrPlainJson(packetId, payload);
    }

    private static JsonPayload inflateOrPlainJson(int packetId, byte[] payload) {
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(payload));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            inflater.transferTo(out);
            return new JsonPayload(packetId, new String(out.toByteArray(), StandardCharsets.UTF_8), true);
        } catch (Exception ignored) {
            return new JsonPayload(packetId, new String(payload, StandardCharsets.UTF_8), false);
        }
    }

    private static void requireFullyConsumed(int packetId, HeyPixelMsgpackReader reader) {
        if (reader.hasRemaining()) {
            throw new IllegalArgumentException("S2C ID" + packetId + " left " + reader.remaining() + " unread bytes");
        }
    }

    public record WrappedPacket(int packetId, byte[] payload) {
        public WrappedPacket {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    public record Id101Challenge(UUID packetUuid, long packetLong, String subtypeName, String challengeValue) {
    }

    public record Id111Record(
        String key,
        int field00,
        int field01,
        String field02,
        String field03,
        String field04,
        String field05
    ) {
    }

    public record Id112Update(int operation, List<String> jsonEntries) {
        public Id112Update {
            jsonEntries = List.copyOf(jsonEntries);
        }
    }

    public record JsonPayload(int packetId, String json, boolean zlibCompressed) {
    }
}
