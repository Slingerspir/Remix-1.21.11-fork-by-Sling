package cn.remix.protocol.heypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProtocolTraceLogger {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Path directory;
    private volatile boolean enabled;

    public ProtocolTraceLogger(Path directory) {
        this.directory = directory;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void log(String event, String channel, Integer packetId, Map<String, ?> details) {
        if (!enabled) return;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("timestamp", Instant.now().toString());
        value.put("event", event);
        value.put("channel", channel);
        value.put("packetId", packetId);
        value.put("details", redact(details));
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("protocol-" + java.time.LocalDate.now() + ".jsonl");
            Files.writeString(file, GSON.toJson(value) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static Map<String, ?> redact(Map<String, ?> details) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            String key = entry.getKey();
            if (key.toLowerCase(java.util.Locale.ROOT).contains("token")
                || key.toLowerCase(java.util.Locale.ROOT).contains("secret")) {
                safe.put(key, "<redacted>");
            } else {
                safe.put(key, entry.getValue());
            }
        }
        return safe;
    }
}
