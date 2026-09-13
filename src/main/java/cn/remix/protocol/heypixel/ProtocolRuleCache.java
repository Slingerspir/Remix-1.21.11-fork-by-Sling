package cn.remix.protocol.heypixel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProtocolRuleCache {
    private volatile S2CPacketDecoders.Id101Challenge challenge;
    private volatile String syncToken;
    private volatile int updateOperation;
    private final Map<String, S2CPacketDecoders.Id111Record> rules = new LinkedHashMap<>();
    private final Map<Integer, String> jsonState = new LinkedHashMap<>();
    private List<String> updateEntries = List.of();

    public synchronized void reset() {
        challenge = null;
        syncToken = null;
        updateOperation = 0;
        rules.clear();
        jsonState.clear();
        updateEntries = List.of();
    }

    public void setChallenge(S2CPacketDecoders.Id101Challenge challenge) {
        this.challenge = challenge;
    }

    public Optional<S2CPacketDecoders.Id101Challenge> challenge() {
        return Optional.ofNullable(challenge);
    }

    public synchronized void replaceRules(List<S2CPacketDecoders.Id111Record> records) {
        rules.clear();
        for (S2CPacketDecoders.Id111Record record : records) rules.put(record.key(), record);
    }

    public synchronized Map<String, S2CPacketDecoders.Id111Record> rules() {
        return Map.copyOf(rules);
    }

    public synchronized void applyUpdate(S2CPacketDecoders.Id112Update update) {
        updateOperation = update.operation();
        updateEntries = List.copyOf(update.jsonEntries());
        if (update.operation() == 0) rules.clear();
    }

    public int updateOperation() {
        return updateOperation;
    }

    public synchronized List<String> updateEntries() {
        return updateEntries;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
    }

    public Optional<String> syncToken() {
        return Optional.ofNullable(syncToken);
    }

    public synchronized void putJsonState(int packetId, String json) {
        jsonState.put(packetId, json);
    }

    public synchronized Map<Integer, String> jsonState() {
        return Map.copyOf(jsonState);
    }
}
