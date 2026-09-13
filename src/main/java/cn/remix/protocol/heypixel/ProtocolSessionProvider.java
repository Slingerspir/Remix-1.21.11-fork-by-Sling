package cn.remix.protocol.heypixel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ProtocolSessionProvider {
    public static final String SNAPSHOT_NAME = "protocol-session.json";
    public static final String KEY_NAME = "protocol-session.key";

    private final Path snapshotPath;
    private final Path keyPath;
    private final Clock clock;

    public ProtocolSessionProvider(Path configDirectory) {
        this(configDirectory.resolve(SNAPSHOT_NAME), configDirectory.resolve(KEY_NAME), Clock.systemUTC());
    }

    ProtocolSessionProvider(Path snapshotPath, Path keyPath, Clock clock) {
        this.snapshotPath = snapshotPath;
        this.keyPath = keyPath;
        this.clock = clock;
    }

    public Optional<ProtocolSessionSnapshot> loadValid(String expectedHost) {
        try {
            ProtocolSessionSnapshot snapshot = parse(Files.readString(snapshotPath, StandardCharsets.UTF_8));
            validate(snapshot, expectedHost);
            return Optional.of(snapshot);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public ProtocolSessionSnapshot loadRequired(String expectedHost) throws IOException {
        ProtocolSessionSnapshot snapshot = parse(Files.readString(snapshotPath, StandardCharsets.UTF_8));
        validate(snapshot, expectedHost);
        return snapshot;
    }

    ProtocolSessionSnapshot parse(String json) {
        JsonObject value = JsonParser.parseString(json).getAsJsonObject();
        return new ProtocolSessionSnapshot(
            requiredString(value, "roleName"),
            requiredString(value, "serverAddress"),
            requiredInt(value, "serverPort"),
            requiredInt(value, "userId"),
            requiredString(value, "userTokenHash"),
            requiredString(value, "entityId"),
            requiredString(value, "sdkUid"),
            requiredString(value, "sessionId"),
            requiredString(value, "deviceId"),
            requiredString(value, "gameId"),
            requiredString(value, "launcherVersion"),
            Instant.parse(requiredString(value, "createdAt")),
            Instant.parse(requiredString(value, "expiresAt")),
            requiredString(value, "signature")
        );
    }

    void validate(ProtocolSessionSnapshot snapshot, String expectedHost) {
        if (snapshot.roleName().isBlank() || snapshot.serverAddress().isBlank()
            || snapshot.serverPort() < 1 || snapshot.serverPort() > 65535
            || snapshot.userTokenHash().isBlank()) {
            throw new IllegalArgumentException("protocol session is incomplete");
        }
        if (snapshot.isExpired(clock.instant())) {
            throw new IllegalArgumentException("protocol session is expired");
        }
        if (expectedHost != null && !expectedHost.isBlank()
            && !normalizeHost(snapshot.serverAddress()).equals(normalizeHost(expectedHost))) {
            throw new IllegalArgumentException("protocol session host mismatch");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(Files.readString(keyPath, StandardCharsets.US_ASCII).trim());
        } catch (Exception error) {
            throw new IllegalArgumentException("protocol session key is unavailable", error);
        }
        byte[] expected = hmac(key, canonical(snapshot));
        byte[] actual;
        try {
            actual = Base64.getDecoder().decode(snapshot.signature());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("protocol session signature is malformed", error);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("protocol session signature mismatch");
        }
    }

    public static String canonical(ProtocolSessionSnapshot snapshot) {
        return String.join("\n",
            snapshot.roleName(),
            normalizeHost(snapshot.serverAddress()),
            Integer.toString(snapshot.serverPort()),
            Integer.toString(snapshot.userId()),
            snapshot.userTokenHash(),
            snapshot.entityId(),
            snapshot.sdkUid(),
            snapshot.sessionId(),
            snapshot.deviceId(),
            snapshot.gameId(),
            snapshot.launcherVersion(),
            Long.toString(snapshot.createdAt().toEpochMilli()),
            Long.toString(snapshot.expiresAt().toEpochMilli())
        );
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("HmacSHA256 is unavailable", error);
        }
    }

    private static String requiredString(JsonObject value, String key) {
        if (!value.has(key) || value.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing protocol session field: " + key);
        }
        return value.get(key).getAsString();
    }

    private static int requiredInt(JsonObject value, String key) {
        if (!value.has(key) || value.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing protocol session field: " + key);
        }
        return value.get(key).getAsInt();
    }

    static String normalizeHost(String value) {
        String host = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("[")) {
            int closing = host.indexOf(']');
            return closing > 0 ? host.substring(1, closing) : host;
        }
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            String port = host.substring(colon + 1);
            if (port.chars().allMatch(Character::isDigit)) host = host.substring(0, colon);
        }
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }
}
