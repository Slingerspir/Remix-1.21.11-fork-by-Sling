package cn.remix.protocol.heypixel;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minimal reader for the token subset used by the recovered HeyPixel packets. */
public final class HeyPixelMsgpackReader {
    private final byte[] input;
    private int offset;

    public HeyPixelMsgpackReader(byte[] input) {
        this.input = input.clone();
    }

    public boolean hasRemaining() {
        return offset < input.length;
    }

    public int remaining() {
        return input.length - offset;
    }

    public long readLong() {
        int token = u8();
        if (token <= 0x7f) return token;
        if (token >= 0xe0) return (byte) token;
        return switch (token) {
            case 0xcc -> u8();
            case 0xcd -> u16();
            case 0xce -> u32();
            case 0xcf, 0xd3 -> i64();
            case 0xd0 -> (byte) u8();
            case 0xd1 -> (short) u16();
            case 0xd2 -> (int) u32();
            default -> throw tokenError("integer", token);
        };
    }

    public int readInt() {
        long value = readLong();
        if (value < Integer.MIN_VALUE || value > 0xffffffffL) {
            throw new IllegalArgumentException("integer does not fit 32 bits: " + value);
        }
        return (int) value;
    }

    public String readString() {
        int token = u8();
        int length;
        if ((token & 0xe0) == 0xa0) {
            length = token & 0x1f;
        } else {
            length = switch (token) {
                case 0xd9 -> u8();
                case 0xda -> u16();
                case 0xdb -> Math.toIntExact(u32());
                default -> throw tokenError("string", token);
            };
        }
        require(length);
        String value = new String(input, offset, length, StandardCharsets.UTF_8);
        offset += length;
        return value;
    }

    public UUID readUuid() {
        String value = readString();
        int separator = value.indexOf("|-|");
        if (separator >= 0) {
            long least = Long.parseLong(value.substring(0, separator));
            long most = Long.parseLong(value.substring(separator + 3));
            return new UUID(most, least);
        }
        return UUID.fromString(value);
    }

    public int readArrayHeader() {
        int token = u8();
        if ((token & 0xf0) == 0x90) return token & 0x0f;
        return switch (token) {
            case 0xdc -> u16();
            case 0xdd -> Math.toIntExact(u32());
            default -> throw tokenError("array", token);
        };
    }

    private int u8() {
        require(1);
        return input[offset++] & 0xff;
    }

    private int u16() {
        return (u8() << 8) | u8();
    }

    private long u32() {
        return ((long) u8() << 24) | ((long) u8() << 16) | ((long) u8() << 8) | u8();
    }

    private long i64() {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | u8();
        return value;
    }

    private void require(int count) {
        if (count < 0 || offset + count > input.length) {
            throw new IllegalArgumentException("truncated MessagePack at " + offset + " need=" + count);
        }
    }

    private IllegalArgumentException tokenError(String expected, int token) {
        return new IllegalArgumentException("expected " + expected + " token at " + (offset - 1)
            + " but found 0x" + Integer.toHexString(token));
    }
}
