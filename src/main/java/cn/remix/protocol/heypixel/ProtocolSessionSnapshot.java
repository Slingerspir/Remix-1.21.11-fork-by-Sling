package cn.remix.protocol.heypixel;

import java.time.Instant;

public record ProtocolSessionSnapshot(
    String roleName,
    String serverAddress,
    int serverPort,
    int userId,
    String userTokenHash,
    String entityId,
    String sdkUid,
    String sessionId,
    String deviceId,
    String gameId,
    String launcherVersion,
    Instant createdAt,
    Instant expiresAt,
    String signature
) {
    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
