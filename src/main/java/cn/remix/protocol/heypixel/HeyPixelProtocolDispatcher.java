package cn.remix.protocol.heypixel;

import java.util.Set;

public final class HeyPixelProtocolDispatcher {
    private static final Set<Integer> JSON_PACKET_IDS = Set.of(113, 115, 116, 118, 119);
    private final ProtocolRuleCache rules;

    public HeyPixelProtocolDispatcher(ProtocolRuleCache rules) {
        this.rules = rules;
    }

    public DispatchResult dispatch(byte[] wire) {
        S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
        int packetId = wrapped.packetId();
        byte[] payload = wrapped.payload();
        return switch (packetId) {
            case 101 -> {
                S2CPacketDecoders.Id101Challenge challenge = S2CPacketDecoders.decodeId101(payload);
                rules.setChallenge(challenge);
                yield new DispatchResult(packetId, Kind.CHALLENGE, payload.length, challenge);
            }
            case 111 -> {
                var records = S2CPacketDecoders.decodeId111(payload);
                rules.replaceRules(records);
                yield new DispatchResult(packetId, Kind.RULES, payload.length, records);
            }
            case 112 -> {
                S2CPacketDecoders.Id112Update update = S2CPacketDecoders.decodeId112(payload);
                rules.applyUpdate(update);
                yield new DispatchResult(packetId, Kind.UPDATE, payload.length, update);
            }
            case 114 -> {
                String token = S2CPacketDecoders.decodeId114(payload);
                rules.setSyncToken(token);
                yield new DispatchResult(packetId, Kind.SYNC_TOKEN, payload.length, token);
            }
            case 117, 120 -> new DispatchResult(packetId, Kind.OPAQUE, payload.length, payload);
            default -> {
                if (JSON_PACKET_IDS.contains(packetId)) {
                    S2CPacketDecoders.JsonPayload json = S2CPacketDecoders.decodeJsonPacket(packetId, payload);
                    rules.putJsonState(packetId, json.json());
                    yield new DispatchResult(packetId, Kind.JSON, payload.length, json);
                }
                yield new DispatchResult(packetId, Kind.UNIMPLEMENTED, payload.length, payload);
            }
        };
    }

    public enum Kind {
        CHALLENGE,
        RULES,
        UPDATE,
        SYNC_TOKEN,
        JSON,
        OPAQUE,
        UNIMPLEMENTED
    }

    public record DispatchResult(int packetId, Kind kind, int payloadLength, Object value) {
    }
}
