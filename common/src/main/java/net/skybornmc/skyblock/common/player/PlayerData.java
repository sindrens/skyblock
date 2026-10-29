package net.skybornmc.skyblock.common.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cached data of one player (table {@code players}). Mutate only on the main thread; every setter marks the data
 * dirty so the autosave writes it. The save itself works on an immutable {@link Snapshot}.
 */
public final class PlayerData {

    /** Immutable copy handed to the database thread. */
    public record Snapshot(UUID uuid, String name, long firstSeen, long lastSeen, long playtimeMillis, boolean bedrock,
                           Long discordId, Map<String, String> settings) {
    }

    private final UUID uuid;
    private volatile String name;
    private final long firstSeen;
    private volatile long lastSeen;
    private volatile long playtimeMillis;
    private volatile boolean bedrock;
    private volatile Long discordId;
    private final Map<String, String> settings;
    private volatile boolean dirty;
    private volatile long sessionStart;
    private final boolean isNew;

    PlayerData(Snapshot s, boolean isNew) {
        this.uuid = s.uuid();
        this.name = s.name();
        this.firstSeen = s.firstSeen();
        this.lastSeen = s.lastSeen();
        this.playtimeMillis = s.playtimeMillis();
        this.bedrock = s.bedrock();
        this.discordId = s.discordId();
        this.settings = Collections.synchronizedMap(new LinkedHashMap<>(s.settings()));
        this.isNew = isNew;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** First join ever (UTC millis). */
    public long firstSeen() {
        return firstSeen;
    }

    /** Last join/quit (UTC millis). */
    public long lastSeen() {
        return lastSeen;
    }

    /** {@code true} if this is the player's first join. */
    public boolean isNew() {
        return isNew;
    }

    public boolean bedrock() {
        return bedrock;
    }

    public Long discordId() {
        return discordId;
    }

    /** Total playtime including the current session. */
    public long playtimeMillis() {
        long session = sessionStart > 0 ? System.currentTimeMillis() - sessionStart : 0;
        return playtimeMillis + session;
    }

    /** A setting value (used by {@code /settings}, Phase 1). */
    public String setting(String key, String fallback) {
        return settings.getOrDefault(key, fallback);
    }

    /** Sets (or with {@code null} removes) a setting and marks the data dirty. */
    public void setSetting(String key, String value) {
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
        dirty = true;
    }

    public void discordId(Long id) {
        if (!Objects.equals(discordId, id)) {
            discordId = id;
            dirty = true;
        }
    }

    void name(String newName) {
        if (!Objects.equals(name, newName)) {
            name = newName;
            dirty = true;
        }
    }

    void bedrock(boolean value) {
        if (bedrock != value) {
            bedrock = value;
            dirty = true;
        }
    }

    void startSession(long now) {
        sessionStart = now;
        lastSeen = now;
        dirty = true;
    }

    /** Ends the session: adds the session to playtime and sets last seen. */
    void endSession(long now) {
        if (sessionStart > 0) {
            playtimeMillis += Math.max(0, now - sessionStart);
            sessionStart = 0;
        }
        lastSeen = now;
        dirty = true;
    }

    /** Folds the running session into playtime without ending it (for autosaves). */
    void checkpoint(long now) {
        if (sessionStart > 0) {
            playtimeMillis += Math.max(0, now - sessionStart);
            sessionStart = now;
            lastSeen = now;
            dirty = true;
        }
    }

    boolean dirty() {
        return dirty;
    }

    void markDirty() {
        dirty = true;
    }

    void clean() {
        dirty = false;
    }

    public Snapshot snapshot() {
        Map<String, String> copy;
        synchronized (settings) {
            copy = Map.copyOf(settings);
        }
        return new Snapshot(uuid, name, firstSeen, lastSeen, playtimeMillis, bedrock, discordId, copy);
    }
}
