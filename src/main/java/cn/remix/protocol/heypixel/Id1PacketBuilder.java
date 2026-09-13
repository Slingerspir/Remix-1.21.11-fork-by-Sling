package cn.remix.protocol.heypixel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Id1PacketBuilder {
    public static final int PACKET_ID = 1;
    public static final int SHORT_EVIDENCE_LIMIT = 40;

    private final Id1SignatureProvider signatures;
    private final Id1CryptoTransform crypto;
    private final EvidenceSampler sampler;
    private final AttackValueProvider attackValues;
    private final UuidSelectedPayloadFramer framer;

    public Id1PacketBuilder(
        Id1SignatureProvider signatures,
        Id1CryptoTransform crypto,
        EvidenceSampler sampler,
        AttackValueProvider attackValues
    ) {
        this.signatures = Objects.requireNonNull(signatures, "signatures");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.attackValues = Objects.requireNonNull(attackValues, "attackValues");
        this.framer = new UuidSelectedPayloadFramer();
    }

    public byte[] buildPacket(Challenge challenge, Context context, Object subtypePayload) {
        byte[] preCrypto = buildPreCrypto(challenge, context, subtypePayload);
        if (!crypto.available()) {
            throw new IllegalStateException("ID1 crypto is unavailable; refusing to build a live packet");
        }
        byte[] postCrypto = Objects.requireNonNull(crypto.transform(preCrypto), "crypto result");
        return framer.framePacket(PACKET_ID, postCrypto, context.localUuid());
    }

    public byte[] buildPreCrypto(Challenge challenge, Context context, Object subtypePayload) {
        HeyPixelMsgpackWriter writer = new HeyPixelMsgpackWriter();
        writer.packLong(context.writerTime());
        writer.packString(context.localUuid().toString());
        writer.packByte((byte) challenge.subtype().wireId);
        writer.packString(challenge.packetUuid().toString());
        writer.packLong(challenge.packetLong());

        switch (challenge.subtype()) {
            case SPRINT -> writeSprint(writer, requireType(subtypePayload, SprintEnvironment.class));
            case SNEAK -> writeSneak(writer, requireType(subtypePayload, SneakEvidence.class));
            case SWIM -> writeSwim(writer, requireType(subtypePayload, SwimEvidence.class));
            case ATTACK -> writeAttack(writer, challenge.challengeValue());
        }
        return writer.toByteArray();
    }

    private void writeSprint(HeyPixelMsgpackWriter writer, SprintEnvironment environment) {
        requireSignatures();
        writer.packArrayHeader(environment.loadedMods().size());
        for (ModEvidence mod : environment.loadedMods()) {
            writer.packString(mod.moduleName());
            writer.packString(mod.path());
            writer.packString(signatures.digestPathLike(mod.path()));
        }
        writer.packString(environment.gameDirectory());
        writer.packString(environment.javaHome());
        writer.packValue(environment.cpuInfo());
        writer.packValue(environment.computerSystemInfo());
        writer.packValue(environment.networkInterfaces());
        writer.packValue(environment.diskStores());
        writer.packValue(environment.accountTraces());
        writer.packValue(environment.userProperties());

        writer.packArrayHeader(environment.discoveredJars().size());
        for (String jar : environment.discoveredJars()) {
            writer.packString(signatures.signString(jar));
            writer.packString(signatures.signString(signatures.digestPathLike(jar)));
        }
    }

    private void writeSneak(HeyPixelMsgpackWriter writer, SneakEvidence evidence) {
        List<String> values = sampler.sample(new ArrayList<>(evidence.values()), SHORT_EVIDENCE_LIMIT);
        writer.packInt(evidence.stateCode());
        writer.packInt(evidence.values().size());
        writer.packValue(values);
    }

    private void writeSwim(HeyPixelMsgpackWriter writer, SwimEvidence evidence) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(evidence.valuesByKey());
        List<String> keys = sampler.sample(new ArrayList<>(values.keySet()), SHORT_EVIDENCE_LIMIT);
        List<String> samples = keys.stream().map(key -> key + ":" + values.get(key)).toList();
        writer.packInt(evidence.evidenceKeyCount());
        writer.packInt(values.size());
        writer.packValue(samples);
    }

    private void writeAttack(HeyPixelMsgpackWriter writer, String challengeValue) {
        requireSignatures();
        Object derived = Objects.requireNonNull(attackValues.derive(challengeValue), "derived attack value");
        writer.packInt(derived.hashCode());
        writer.packString(signatures.signString(derived.toString()));
    }

    private void requireSignatures() {
        if (!signatures.available()) {
            throw new IllegalStateException("ID1 signatures are unavailable; refusing to construct this subtype");
        }
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Expected " + type.getSimpleName() + " but got "
                + (value == null ? "null" : value.getClass().getName()));
        }
        return type.cast(value);
    }

    public enum Id1Subtype {
        SPRINT(0), SNEAK(1), SWIM(2), ATTACK(3);

        private final int wireId;

        Id1Subtype(int wireId) {
            this.wireId = wireId;
        }

        public int wireId() {
            return wireId;
        }
    }

    public record Challenge(UUID packetUuid, long packetLong, Id1Subtype subtype, String challengeValue) {
        public Challenge {
            Objects.requireNonNull(packetUuid, "packetUuid");
            Objects.requireNonNull(subtype, "subtype");
        }
    }

    public record Context(UUID localUuid, long writerTime) {
        public Context {
            Objects.requireNonNull(localUuid, "localUuid");
        }
    }

    public record ModEvidence(String moduleName, String path) {
        public ModEvidence {
            Objects.requireNonNull(moduleName, "moduleName");
            Objects.requireNonNull(path, "path");
        }
    }

    public record SprintEnvironment(
        List<ModEvidence> loadedMods,
        String gameDirectory,
        String javaHome,
        Object cpuInfo,
        Object computerSystemInfo,
        Object networkInterfaces,
        Object diskStores,
        Object accountTraces,
        Object userProperties,
        List<String> discoveredJars
    ) {
        public SprintEnvironment {
            loadedMods = List.copyOf(loadedMods);
            discoveredJars = List.copyOf(discoveredJars);
        }
    }

    public record SneakEvidence(int stateCode, List<String> values) {
        public SneakEvidence {
            values = List.copyOf(values);
        }
    }

    public record SwimEvidence(int evidenceKeyCount, Map<String, String> valuesByKey) {
        public SwimEvidence {
            valuesByKey = Map.copyOf(valuesByKey);
        }
    }

    public interface Id1SignatureProvider {
        boolean available();
        String digestPathLike(String path);
        String signString(String value);
    }

    public interface Id1CryptoTransform {
        boolean available();
        byte[] transform(byte[] preCrypto);
    }

    public interface EvidenceSampler {
        List<String> sample(List<String> values, int limit);

        static EvidenceSampler preserveOrder() {
            return (values, limit) -> List.copyOf(values.subList(0, Math.min(values.size(), limit)));
        }
    }

    public interface AttackValueProvider {
        Object derive(String challengeValue);
    }
}
