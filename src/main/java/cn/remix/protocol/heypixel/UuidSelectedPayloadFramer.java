package cn.remix.protocol.heypixel;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UuidSelectedPayloadFramer {
    public byte[] framePacket(int packetId, byte[] payload, UUID localUuid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        writeVarInt(out, packetId);
        writeVarInt(out, payload.length + 1);
        out.writeBytes(applyLayout(payload, selectLayout(localUuid)));
        return out.toByteArray();
    }

    public int selectLayout(UUID localUuid) {
        return Math.floorMod(localUuid.getMostSignificantBits() ^ localUuid.getLeastSignificantBits(), 8);
    }

    public byte[] applyLayout(byte[] input, int layout) {
        byte[] bytes = input.clone();
        return switch (layout) {
            case 0 -> bytes;
            case 1 -> reversed(bytes);
            case 2 -> evenThenOdd(bytes);
            case 3 -> rotateLeft(bytes, bytes.length % 5 + 1);
            case 4 -> xor(bytes, (byte) 0xa5);
            case 5 -> reverseFourByteBlocks(bytes);
            case 6 -> swapNibbles(bytes);
            case 7 -> reverseBits(bytes);
            default -> throw new IllegalArgumentException("Unknown UUID payload layout: " + layout);
        };
    }

    public static void writeVarInt(ByteArrayOutputStream out, int value) {
        int current = value;
        do {
            int next = current & 0x7f;
            current >>>= 7;
            if (current != 0) next |= 0x80;
            out.write(next);
        } while (current != 0);
    }

    private static byte[] reversed(byte[] bytes) {
        byte[] out = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) out[i] = bytes[bytes.length - 1 - i];
        return out;
    }

    private static byte[] evenThenOdd(byte[] bytes) {
        byte[] out = new byte[bytes.length];
        int cursor = 0;
        for (int i = 0; i < bytes.length; i += 2) out[cursor++] = bytes[i];
        for (int i = 1; i < bytes.length; i += 2) out[cursor++] = bytes[i];
        return out;
    }

    private static byte[] rotateLeft(byte[] bytes, int distance) {
        if (bytes.length == 0) return bytes;
        byte[] out = new byte[bytes.length];
        int shift = Math.floorMod(distance, bytes.length);
        for (int i = 0; i < bytes.length; i++) out[i] = bytes[(i + shift) % bytes.length];
        return out;
    }

    private static byte[] xor(byte[] bytes, byte key) {
        byte[] out = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) out[i] = (byte) (bytes[i] ^ key);
        return out;
    }

    private static byte[] reverseFourByteBlocks(byte[] bytes) {
        List<byte[]> blocks = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += 4) {
            int length = Math.min(4, bytes.length - offset);
            byte[] block = new byte[length];
            System.arraycopy(bytes, offset, block, 0, length);
            blocks.add(block);
        }
        byte[] out = new byte[bytes.length];
        int cursor = 0;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            byte[] block = blocks.get(i);
            System.arraycopy(block, 0, out, cursor, block.length);
            cursor += block.length;
        }
        return out;
    }

    private static byte[] swapNibbles(byte[] bytes) {
        byte[] out = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i] = (byte) ((value << 4) | (value >>> 4));
        }
        return out;
    }

    private static byte[] reverseBits(byte[] bytes) {
        byte[] out = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) (Integer.reverse(bytes[i] & 0xff) >>> 24);
        }
        return out;
    }
}
