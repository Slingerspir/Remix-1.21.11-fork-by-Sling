package cn.remix.protocol.heypixel;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ID1 应答的“真正”实现：签名(HMAC-SHA256) + 变换(AES-256-GCM) + 各 subtype 证据。
 * 密钥来自 &lt;configDir&gt;/protocol-session.key（Base64），与 ProtocolSessionProvider 同一份。
 */
public final class HeyPixelId1 {

    private HeyPixelId1() {
    }

    /** 把 Id1 构建器与输入映射接进运行时。可重复调用。 */
    public static void install(HeyPixelProtocolRuntime runtime, MinecraftClient mc, Path configDirectory) {
        KeyStore keys = new KeyStore(configDirectory);
        Id1PacketBuilder builder = new Id1PacketBuilder(
                new KeyedSignatureProvider(keys),
                new AesGcmCryptoTransform(keys),
                Id1PacketBuilder.EvidenceSampler.preserveOrder(),
                new ChallengeAttackValueProvider());
        runtime.configureId1(builder, (challenge, session) -> buildInput(mc, session, challenge));
    }

    private static HeyPixelProtocolRuntime.Id1BuildInput buildInput(
            MinecraftClient mc, ProtocolSessionSnapshot session, S2CPacketDecoders.Id101Challenge challenge) {
        Id1PacketBuilder.Id1Subtype subtype = subtypeOf(challenge.subtypeName());
        UUID localUuid = parseUuid(session.entityId(), mc);
        Id1PacketBuilder.Context context = new Id1PacketBuilder.Context(localUuid, System.currentTimeMillis());
        Object payload = switch (subtype) {
            case SPRINT -> sprintEnvironment(mc, session);
            case SNEAK -> sneakEvidence(mc);
            case SWIM -> swimEvidence(mc, session);
            case ATTACK -> null; // writeAttack 只用 challengeValue，不需要 subtypePayload
        };
        return new HeyPixelProtocolRuntime.Id1BuildInput(subtype, context, payload);
    }

    private static Id1PacketBuilder.Id1Subtype subtypeOf(String name) {
        if (name == null) return Id1PacketBuilder.Id1Subtype.SPRINT;
        for (Id1PacketBuilder.Id1Subtype value : Id1PacketBuilder.Id1Subtype.values()) {
            if (value.name().equalsIgnoreCase(name.trim())) return value;
        }
        return Id1PacketBuilder.Id1Subtype.SPRINT;
    }

    private static UUID parseUuid(String text, MinecraftClient mc) {
        if (text != null && !text.isBlank()) {
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return mc.player != null ? mc.player.getUuid() : UUID.randomUUID();
    }

    // ---------------- subtype 证据 ----------------

    private static Id1PacketBuilder.SprintEnvironment sprintEnvironment(MinecraftClient mc, ProtocolSessionSnapshot session) {
        List<Id1PacketBuilder.ModEvidence> mods = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mods.size() >= 64) break;
            String path = mod.getOrigin().getPaths().isEmpty() ? "" : mod.getOrigin().getPaths().get(0).toString();
            mods.add(new Id1PacketBuilder.ModEvidence(mod.getMetadata().getName(), path));
        }

        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("arch", System.getProperty("os.arch", "unknown"));
        cpu.put("cores", Runtime.getRuntime().availableProcessors());

        Map<String, Object> system = new LinkedHashMap<>();
        system.put("os", System.getProperty("os.name", "unknown"));
        system.put("version", System.getProperty("os.version", "unknown"));
        system.put("user", System.getProperty("user.name", "unknown"));

        List<String> interfaces = new ArrayList<>();
        try {
            var nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements() && interfaces.size() < 16) {
                interfaces.add(nics.nextElement().getName());
            }
        } catch (Exception ignored) {
        }

        List<String> disks = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (disks.size() >= 8) break;
                disks.add(root.getAbsolutePath() + " free=" + root.getFreeSpace());
            }
        }

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("roleName", session.roleName());
        account.put("userId", session.userId());
        account.put("deviceId", session.deviceId());
        account.put("sessionId", session.sessionId());
        account.put("userTokenHash", session.userTokenHash());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("java.version", System.getProperty("java.version", ""));
        props.put("java.vendor", System.getProperty("java.vendor", ""));
        props.put("os.arch", System.getProperty("os.arch", ""));
        props.put("user.language", System.getProperty("user.language", ""));

        List<String> jars = discoveredJars();
        String gameDir = FabricLoader.getInstance().getGameDir().toString();

        return new Id1PacketBuilder.SprintEnvironment(
                mods,
                gameDir,
                System.getProperty("java.home", ""),
                cpu, system, interfaces, disks, account, props, jars);
    }

    private static List<String> discoveredJars() {
        List<String> jars = new ArrayList<>();
        try {
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
            if (Files.isDirectory(mods)) {
                try (var stream = Files.list(mods)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".jar")).limit(64)
                            .forEach(p -> jars.add(p.getFileName().toString()));
                }
            }
        } catch (Exception ignored) {
        }
        return jars;
    }

    private static Id1PacketBuilder.SneakEvidence sneakEvidence(MinecraftClient mc) {
        int state = 0;
        List<String> values = new ArrayList<>();
        if (mc.player != null) {
            if (mc.player.isSprinting()) state |= 1;
            if (mc.player.isSneaking()) state |= 2;
            if (mc.player.isSwimming()) state |= 4;
            values.add("sprint=" + mc.player.isSprinting());
            values.add("sneak=" + mc.player.isSneaking());
            values.add("swim=" + mc.player.isSwimming());
            values.add("onGround=" + mc.player.isOnGround());
            values.add("pos=" + Math.round(mc.player.getX()) + "," + Math.round(mc.player.getY()) + "," + Math.round(mc.player.getZ()));
            values.add("dim=" + (mc.world != null ? mc.world.getRegistryKey().getValue() : "?"));
        }
        return new Id1PacketBuilder.SneakEvidence(state, values);
    }

    private static Id1PacketBuilder.SwimEvidence swimEvidence(MinecraftClient mc, ProtocolSessionSnapshot session) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("roleName", session.roleName());
        values.put("deviceId", session.deviceId());
        values.put("sessionId", session.sessionId());
        values.put("sdkUid", session.sdkUid());
        values.put("gameId", session.gameId());
        values.put("launcher", session.launcherVersion());
        values.put("userId", String.valueOf(session.userId()));
        if (mc.player != null) {
            values.put("uuid", mc.player.getUuid().toString());
        }
        if (mc.getNetworkHandler() != null && mc.player != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) values.put("latency", String.valueOf(entry.getLatency()));
        }
        return new Id1PacketBuilder.SwimEvidence(values.size(), values);
    }

    // ---------------- 密钥 ----------------

    private static final class KeyStore {
        private final Path keyPath;
        private volatile byte[] cached;

        KeyStore(Path configDirectory) {
            this.keyPath = configDirectory.resolve(ProtocolSessionProvider.KEY_NAME);
        }

        Optional<byte[]> key() {
            byte[] local = cached;
            if (local != null) return Optional.of(local);
            try {
                if (!Files.isRegularFile(keyPath)) return Optional.empty();
                byte[] decoded = Base64.getDecoder().decode(Files.readString(keyPath, StandardCharsets.US_ASCII).trim());
                if (decoded.length == 0) return Optional.empty();
                cached = decoded;
                return Optional.of(decoded);
            } catch (Exception error) {
                return Optional.empty();
            }
        }
    }

    /** HMAC-SHA256 签名；路径摘要用规范化路径的 SHA-256。 */
    private static final class KeyedSignatureProvider implements Id1PacketBuilder.Id1SignatureProvider {
        private final KeyStore keys;

        KeyedSignatureProvider(KeyStore keys) {
            this.keys = keys;
        }

        @Override
        public boolean available() {
            return keys.key().isPresent();
        }

        @Override
        public String digestPathLike(String path) {
            String normalized = path == null ? "" : path.replace('\\', '/');
            return sha256Hex(normalized.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String signString(String value) {
            byte[] key = keys.key().orElseThrow(() -> new IllegalStateException("session key unavailable"));
            return Base64.getEncoder().encodeToString(hmac(key, value == null ? "" : value));
        }
    }

    /** AES-256-GCM；输出 = 12 字节随机 IV || 密文+tag；密钥 = SHA-256(keyMaterial)。 */
    private static final class AesGcmCryptoTransform implements Id1PacketBuilder.Id1CryptoTransform {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final KeyStore keys;

        AesGcmCryptoTransform(KeyStore keys) {
            this.keys = keys;
        }

        @Override
        public boolean available() {
            return keys.key().isPresent();
        }

        @Override
        public byte[] transform(byte[] preCrypto) {
            byte[] key = keys.key().orElseThrow(() -> new IllegalStateException("session key unavailable"));
            try {
                byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(key);
                byte[] iv = new byte[12];
                RANDOM.nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(preCrypto);
                byte[] out = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, out, 0, iv.length);
                System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
                return out;
            } catch (Exception error) {
                throw new IllegalStateException("ID1 crypto transform failed", error);
            }
        }
    }

    /** ATTACK：尽量从 challengeValue 解析出数值；否则原样返回字符串。 */
    private static final class ChallengeAttackValueProvider implements Id1PacketBuilder.AttackValueProvider {
        @Override
        public Object derive(String challengeValue) {
            if (challengeValue == null) return 0L;
            String value = challengeValue.trim();
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
            }
            try {
                return Long.parseLong(value, 16);
            } catch (NumberFormatException ignored) {
            }
            return Objects.requireNonNullElse(value, "");
        }
    }

    // ---------------- 工具 ----------------

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("HMAC failed", error);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 failed", error);
        }
    }
}
