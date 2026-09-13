package cn.remix.protocol.heypixel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class C2SPacketEncoders {
    private C2SPacketEncoders() {
    }

    public static byte[] encodeId2(long writerTime, long packetLong) {
        return frameMessagePack(2, writer -> writer.packLong(writerTime).packLong(packetLong));
    }

    public static byte[] encodeId3(long writerTime, int stateA, int stateB) {
        return frameMessagePack(3, writer -> writer.packLong(writerTime).packInt(stateA).packInt(stateB));
    }

    public static byte[] encodeId4(long writerTime, List<String> values) {
        List<String> stableValues = List.copyOf(values);
        return frameMessagePack(4, writer -> writer.packLong(writerTime)
            .packArrayHeader(stableValues.size())
            .packValue(stableValues));
    }

    public static byte[] encodeId5(Id5UseBlock packet) {
        return frameMessagePack(5, writer -> {
            writer.packLong(packet.writerTime());
            writer.packDouble(packet.playerX());
            writer.packDouble(packet.playerY());
            writer.packDouble(packet.playerZ());
            writer.packInt(packet.directionOrdinal());
            writer.packInt(packet.hitTypeOrdinal());
            writer.packDouble(packet.hitX());
            writer.packDouble(packet.hitY());
            writer.packDouble(packet.hitZ());
            writer.packDouble(packet.blockX());
            writer.packDouble(packet.blockY());
            writer.packDouble(packet.blockZ());
            writer.packBoolean(packet.inside());
            writer.packFloat(packet.yaw());
            writer.packFloat(packet.pitch());
            writer.packBoolean(packet.mainHand());
        });
    }

    public static byte[] encodeId6(Id6AttackEntity packet) {
        return frameMessagePack(6, writer -> {
            writer.packLong(packet.writerTime());
            writer.packValue(packet.targetUuid());
            writer.packInt(packet.hitTypeOrdinal());
            writer.packDouble(packet.hitX());
            writer.packDouble(packet.hitY());
            writer.packDouble(packet.hitZ());
            writer.packInt(packet.playerPoseOrdinal());
            writer.packDouble(packet.playerX());
            writer.packDouble(packet.playerY());
            writer.packDouble(packet.playerZ());
            writer.packFloat(packet.playerYaw());
            writer.packFloat(packet.playerPitch());
            writer.packInt(packet.targetPoseOrdinal());
            writer.packDouble(packet.targetX());
            writer.packDouble(packet.targetY());
            writer.packDouble(packet.targetZ());
            writer.packFloat(packet.targetYaw());
            writer.packFloat(packet.targetPitch());
        });
    }

    public static byte[] encodeId7(long writerTime, long field00, String field01) {
        return frameMessagePack(7, writer -> writer.packLong(writerTime)
            .packLong(field00)
            .packString(field01));
    }

    public static byte[] encodeId8Json(String json) {
        return frameRawPayload(8, utf8(json));
    }

    public static byte[] encodeId9Json(String json) {
        return frameRawPayload(9, utf8(json));
    }

    public static byte[] encodeId10Json(String json) {
        return frameRawPayload(10, utf8(json));
    }

    public static byte[] encodeId108(long writerTime, String field00, String field01) {
        return frameMessagePack(108, writer -> {
            writer.packLong(writerTime).packString(field00);
            if (field01 != null) writer.packString(field01);
        });
    }

    public static byte[] encodeId109(long writerTime, String field00, String field01, Integer field02) {
        return frameMessagePack(109, writer -> {
            writer.packLong(writerTime).packString(field00).packString(field01);
            if (field02 != null) writer.packInt(field02);
        });
    }

    public static byte[] frameMessagePack(int packetId, Consumer<HeyPixelMsgpackWriter> payloadWriter) {
        HeyPixelMsgpackWriter writer = new HeyPixelMsgpackWriter();
        payloadWriter.accept(writer);
        return frameRawPayload(packetId, writer.toByteArray());
    }

    public static byte[] frameRawPayload(int packetId, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        UuidSelectedPayloadFramer.writeVarInt(out, packetId);
        UuidSelectedPayloadFramer.writeVarInt(out, payload.length + 1);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] utf8(String value) {
        return Objects.requireNonNull(value, "json").getBytes(StandardCharsets.UTF_8);
    }

    public record Id5UseBlock(
        long writerTime,
        double playerX,
        double playerY,
        double playerZ,
        int directionOrdinal,
        int hitTypeOrdinal,
        double hitX,
        double hitY,
        double hitZ,
        double blockX,
        double blockY,
        double blockZ,
        boolean inside,
        float yaw,
        float pitch,
        boolean mainHand
    ) {
    }

    public record Id6AttackEntity(
        long writerTime,
        String targetUuid,
        int hitTypeOrdinal,
        double hitX,
        double hitY,
        double hitZ,
        int playerPoseOrdinal,
        double playerX,
        double playerY,
        double playerZ,
        float playerYaw,
        float playerPitch,
        int targetPoseOrdinal,
        double targetX,
        double targetY,
        double targetZ,
        float targetYaw,
        float targetPitch
    ) {
        public Id6AttackEntity {
            Objects.requireNonNull(targetUuid, "targetUuid");
        }
    }
}
