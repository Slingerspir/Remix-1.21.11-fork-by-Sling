package cn.remix.protocol.heypixel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * HeyPixel 协议运行时（1.21.11 / Yarn 适配版）。
 * 只负责网关、解码分发与可选的应答发送；原始字节收发交给 {@link ProtocolPayloads}/{@link RawPayload}。
 */
public final class HeyPixelProtocolRuntime {

    public static final Identifier MAIN_CHANNEL = ProtocolPayloads.MAIN_CHANNEL;
    public static final Identifier SKIN_CHANNEL = ProtocolPayloads.SKIN_CHANNEL;
    public static final Identifier FORM_CHANNEL = ProtocolPayloads.FORM_CHANNEL;
    public static final Identifier NETEASE_CHANNEL = ProtocolPayloads.NETEASE_CHANNEL;

    private final MinecraftClient minecraft;
    private final ProtocolSessionProvider sessions;
    private final ProtocolTraceLogger trace;
    private final ProtocolRuleCache rules = new ProtocolRuleCache();
    private final HeyPixelProtocolDispatcher dispatcher = new HeyPixelProtocolDispatcher(rules);
    private volatile boolean enabled;
    private volatile boolean observeOnly = true;
    private volatile boolean allowLiveSend;
    private volatile boolean strictProviderGate = true;
    private volatile String enabledHosts = "pc.bjdmc.net,*.bjdmc.net";
    private volatile String lastHost = "";
    private volatile String deliveredSyncToken;
    private volatile Id1PacketBuilder id1Builder;
    private volatile BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> id1Input;

    public HeyPixelProtocolRuntime(MinecraftClient minecraft, Path configDirectory) {
        this.minecraft = minecraft;
        this.sessions = new ProtocolSessionProvider(configDirectory);
        this.trace = new ProtocolTraceLogger(configDirectory.resolve("protocol-trace"));
    }

    public void start() {
        enabled = true;
        deliveredSyncToken = null;
        trace.log("runtime-start", null, null, Map.of("observeOnly", observeOnly));
    }

    public void stop() {
        if (!enabled) return;
        trace.log("runtime-stop", null, null, Map.of());
        enabled = false;
        lastHost = "";
        deliveredSyncToken = null;
        rules.reset();
    }

    public void configure(String hosts, boolean traceEnabled, boolean observeOnly,
                          boolean allowLiveSend, boolean strictProviderGate) {
        this.enabledHosts = hosts == null ? "" : hosts;
        this.trace.setEnabled(traceEnabled);
        this.observeOnly = observeOnly;
        this.allowLiveSend = allowLiveSend;
        this.strictProviderGate = strictProviderGate;
    }

    public void configureId1(
            Id1PacketBuilder builder,
            BiFunction<S2CPacketDecoders.Id101Challenge, ProtocolSessionSnapshot, Id1BuildInput> input) {
        this.id1Builder = builder;
        this.id1Input = input;
    }

    public void tick() {
        if (!enabled) return;
        String host = currentHost();
        if (!host.equals(lastHost)) {
            trace.log("server-change", null, null, Map.of("host", host, "target", matchesTargetHost(host)));
            lastHost = host;
            deliveredSyncToken = null;
            rules.reset();
        }
        if (!matchesTargetHost(host) || minecraft.world == null || minecraft.player == null) return;
        rules.syncToken().ifPresent(token -> {
            if (!token.equals(deliveredSyncToken)) {
                deliveredSyncToken = token;
                trace.log("sync-token-ready", MAIN_CHANNEL.toString(), 114, Map.of("length", token.length()));
            }
        });
    }

    /** 处理一条入站自定义负载（已在主线程调用）。 */
    public void handle(Identifier channel, byte[] bytes) {
        if (!enabled || channel == null || bytes == null) return;
        if (!matchesTargetHost(currentHost())) return;
        if (!isSupportedChannel(channel)) return;
        if (!MAIN_CHANNEL.equals(channel)) {
            trace.log("s2c-channel", channel.toString(), null, Map.of("length", bytes.length));
            return;
        }
        handleMainChannel(bytes);
    }

    public boolean sendBusinessPacket(byte[] wire, int packetId, String trigger) {
        String host = currentHost();
        if (!enabled || !matchesTargetHost(host) || wire == null) return false;
        if (observeOnly || !allowLiveSend) {
            trace.log("c2s-blocked", MAIN_CHANNEL.toString(), packetId,
                    Map.of("reason", observeOnly ? "observe-only" : "live-send-disabled", "trigger", trigger));
            return false;
        }
        if (strictProviderGate && sessions.loadValid(host).isEmpty()) {
            trace.log("c2s-blocked", MAIN_CHANNEL.toString(), packetId,
                    Map.of("reason", "session-provider-unavailable", "trigger", trigger));
            return false;
        }
        if (minecraft.getNetworkHandler() == null) return false;
        ClientPlayNetworking.send(new RawPayload(ProtocolPayloads.MAIN_ID, wire));
        trace.log("c2s-send", MAIN_CHANNEL.toString(), packetId,
                Map.of("length", wire.length, "trigger", trigger));
        return true;
    }

    public boolean isActiveForCurrentServer() {
        return enabled && matchesTargetHost(currentHost());
    }

    public ProtocolRuleCache rules() {
        return rules;
    }

    private void handleMainChannel(byte[] wire) {
        try {
            HeyPixelProtocolDispatcher.DispatchResult result = dispatcher.dispatch(wire);
            trace.log("s2c-dispatch", MAIN_CHANNEL.toString(), result.packetId(),
                    Map.of("length", result.payloadLength(), "kind", result.kind().name()));
            if (result.kind() == HeyPixelProtocolDispatcher.Kind.CHALLENGE) {
                handleChallenge((S2CPacketDecoders.Id101Challenge) result.value());
            }
        } catch (RuntimeException error) {
            trace.log("s2c-error", MAIN_CHANNEL.toString(), null,
                    Map.of("error", error.getClass().getSimpleName(), "message", String.valueOf(error.getMessage())));
        }
    }

    private void handleChallenge(S2CPacketDecoders.Id101Challenge challenge) {
        rules.setChallenge(challenge);
        trace.log("id101-challenge", MAIN_CHANNEL.toString(), 101,
                Map.of("subtype", challenge.subtypeName(), "packetLong", challenge.packetLong()));
        if (observeOnly || !allowLiveSend) return;
        String host = currentHost();
        Optional<ProtocolSessionSnapshot> session = sessions.loadValid(host);
        if (session.isEmpty() || id1Builder == null || id1Input == null) {
            trace.log("id1-provider-blocked", MAIN_CHANNEL.toString(), 1,
                    Map.of("session", session.isPresent(), "builder", id1Builder != null,
                            "input", id1Input != null, "strict", strictProviderGate));
            return;
        }
        try {
            Id1BuildInput input = id1Input.apply(challenge, session.get());
            Id1PacketBuilder.Challenge request = new Id1PacketBuilder.Challenge(
                    challenge.packetUuid(), challenge.packetLong(), input.subtype(), challenge.challengeValue());
            byte[] wire = id1Builder.buildPacket(request, input.context(), input.subtypePayload());
            sendBusinessPacket(wire, 1, "S2C_ID101");
        } catch (RuntimeException error) {
            trace.log("id1-build-error", MAIN_CHANNEL.toString(), 1,
                    Map.of("error", error.getClass().getSimpleName(), "message", String.valueOf(error.getMessage())));
        }
    }

    private boolean isSupportedChannel(Identifier channel) {
        return MAIN_CHANNEL.equals(channel) || SKIN_CHANNEL.equals(channel)
                || FORM_CHANNEL.equals(channel) || NETEASE_CHANNEL.equals(channel);
    }

    private boolean matchesTargetHost(String host) {
        if (host.isBlank()) return false;
        String normalized = ProtocolSessionProvider.normalizeHost(host);
        return Arrays.stream(enabledHosts.split("[,;\\s]+"))
                .map(ProtocolSessionProvider::normalizeHost)
                .filter(value -> !value.isBlank())
                .anyMatch(pattern -> pattern.startsWith("*.")
                        ? normalized.equals(pattern.substring(2)) || normalized.endsWith(pattern.substring(1))
                        : normalized.equals(pattern));
    }

    private String currentHost() {
        ServerInfo server = minecraft.getCurrentServerEntry();
        return server == null ? "" : ProtocolSessionProvider.normalizeHost(server.address);
    }

    public record Id1BuildInput(
            Id1PacketBuilder.Id1Subtype subtype,
            Id1PacketBuilder.Context context,
            Object subtypePayload
    ) {
    }
}
