package cn.remix.protocol.heypixel;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Wire-compatible subset of the MessagePack writer used by the HeyPixel packets. */
public final class HeyPixelMsgpackWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public HeyPixelMsgpackWriter packByte(byte value) {
        return packInt(value);
    }

    public HeyPixelMsgpackWriter packInt(int value) {
        if (value >= 0) {
            if (value <= 0x7f) {
                writeByte(value);
            } else if (value <= 0xff) {
                writeByte(0xcc);
                writeByte(value);
            } else if (value <= 0xffff) {
                writeByte(0xcd);
                writeU16(value);
            } else {
                writeByte(0xce);
                writeU32(value & 0xffffffffL);
            }
        } else if (value >= -32) {
            writeByte(value);
        } else if (value >= Byte.MIN_VALUE) {
            writeByte(0xd0);
            writeByte(value);
        } else if (value >= Short.MIN_VALUE) {
            writeByte(0xd1);
            writeU16(value);
        } else {
            writeByte(0xd2);
            writeU32(value & 0xffffffffL);
        }
        return this;
    }

    public HeyPixelMsgpackWriter packLong(long value) {
        if (value >= 0) {
            if (value <= 0x7fL) {
                writeByte((int) value);
            } else if (value <= 0xffL) {
                writeByte(0xcc);
                writeByte((int) value);
            } else if (value <= 0xffffL) {
                writeByte(0xcd);
                writeU16((int) value);
            } else if (value <= 0xffffffffL) {
                writeByte(0xce);
                writeU32(value);
            } else {
                writeByte(0xcf);
                writeI64(value);
            }
        } else if (value >= -32L) {
            writeByte((int) value);
        } else if (value >= Byte.MIN_VALUE) {
            writeByte(0xd0);
            writeByte((int) value);
        } else if (value >= Short.MIN_VALUE) {
            writeByte(0xd1);
            writeU16((int) value);
        } else if (value >= Integer.MIN_VALUE) {
            writeByte(0xd2);
            writeU32(value & 0xffffffffL);
        } else {
            writeByte(0xd3);
            writeI64(value);
        }
        return this;
    }

    public HeyPixelMsgpackWriter packFloat(float value) {
        writeByte(0xca);
        writeU32(Float.floatToRawIntBits(value) & 0xffffffffL);
        return this;
    }

    public HeyPixelMsgpackWriter packDouble(double value) {
        writeByte(0xcb);
        writeI64(Double.doubleToRawLongBits(value));
        return this;
    }

    public HeyPixelMsgpackWriter packBoolean(boolean value) {
        writeByte(value ? 0xc3 : 0xc2);
        return this;
    }

    public HeyPixelMsgpackWriter packNil() {
        writeByte(0xc0);
        return this;
    }

    /**
     * The original direct string writer does not use fixstr for non-empty short
     * strings. It also rewrites canonical UUID text to least|-|most.
     */
    public HeyPixelMsgpackWriter packString(String value) {
        byte[] utf8 = normalizeUuid(Objects.requireNonNull(value, "value"))
            .getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0) {
            writeByte(0xa0);
        } else if (utf8.length <= 0xff) {
            writeByte(0xd9);
            writeByte(utf8.length);
        } else if (utf8.length <= 0xffff) {
            writeByte(0xda);
            writeU16(utf8.length);
        } else {
            writeByte(0xdb);
            writeU32(utf8.length);
        }
        out.writeBytes(utf8);
        return this;
    }

    public HeyPixelMsgpackWriter packArrayHeader(int size) {
        requireContainerSize(size);
        if (size <= 15) {
            writeByte(0x90 | size);
        } else if (size <= 0xffff) {
            writeByte(0xdc);
            writeU16(size);
        } else {
            writeByte(0xdd);
            writeU32(size);
        }
        return this;
    }

    public HeyPixelMsgpackWriter packMapHeader(int size) {
        requireContainerSize(size);
        if (size <= 15) {
            writeByte(0x80 | size);
        } else if (size <= 0xffff) {
            writeByte(0xde);
            writeU16(size);
        } else {
            writeByte(0xdf);
            writeU32(size);
        }
        return this;
    }

    public HeyPixelMsgpackWriter packValue(Object value) {
        if (value == null) return packNil();
        if (value instanceof RawValue raw) {
            out.writeBytes(raw.bytes());
            return this;
        }
        if (value instanceof Boolean bool) return packBoolean(bool);
        if (value instanceof Byte number) return packInt(number.intValue());
        if (value instanceof Short number) return packInt(number.intValue());
        if (value instanceof Integer number) return packInt(number);
        if (value instanceof Long number) return packLong(number);
        if (value instanceof Float number) return packFloat(number);
        if (value instanceof Double number) return packDouble(number);
        if (value instanceof String string) return packCanonicalString(string);
        if (value instanceof byte[] bytes) return packBinary(bytes);
        if (value instanceof Map<?, ?> map) {
            packMapHeader(map.size());
            map.forEach((key, entryValue) -> {
                packValue(key);
                packValue(entryValue);
            });
            return this;
        }
        if (value instanceof Iterable<?> iterable) {
            if (!(iterable instanceof List<?> list)) {
                throw new IllegalArgumentException("Iterable values must have a stable List size/order");
            }
            packArrayHeader(list.size());
            list.forEach(this::packValue);
            return this;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            packArrayHeader(length);
            for (int i = 0; i < length; i++) packValue(Array.get(value, i));
            return this;
        }
        throw new IllegalArgumentException("Unsupported MessagePack value: " + value.getClass().getName());
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    private HeyPixelMsgpackWriter packCanonicalString(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length <= 31) {
            writeByte(0xa0 | utf8.length);
        } else if (utf8.length <= 0xff) {
            writeByte(0xd9);
            writeByte(utf8.length);
        } else if (utf8.length <= 0xffff) {
            writeByte(0xda);
            writeU16(utf8.length);
        } else {
            writeByte(0xdb);
            writeU32(utf8.length);
        }
        out.writeBytes(utf8);
        return this;
    }

    private HeyPixelMsgpackWriter packBinary(byte[] bytes) {
        if (bytes.length <= 0xff) {
            writeByte(0xc4);
            writeByte(bytes.length);
        } else if (bytes.length <= 0xffff) {
            writeByte(0xc5);
            writeU16(bytes.length);
        } else {
            writeByte(0xc6);
            writeU32(bytes.length);
        }
        out.writeBytes(bytes);
        return this;
    }

    private static String normalizeUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.getLeastSignificantBits() + "|-|" + uuid.getMostSignificantBits();
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private static void requireContainerSize(int size) {
        if (size < 0) throw new IllegalArgumentException("negative container size");
    }

    private void writeByte(int value) {
        out.write(value & 0xff);
    }

    private void writeU16(int value) {
        writeByte(value >>> 8);
        writeByte(value);
    }

    private void writeU32(long value) {
        writeByte((int) (value >>> 24));
        writeByte((int) (value >>> 16));
        writeByte((int) (value >>> 8));
        writeByte((int) value);
    }

    private void writeI64(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) writeByte((int) (value >>> shift));
    }

    public record RawValue(byte[] bytes) {
        public RawValue {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
