package net.skybornmc.skyblock.essentials;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Open {@code /tpa} and {@code /tpahere} requests. Pure bookkeeping (no Bukkit calls) so it can be unit tested.
 * Main thread only.
 */
final class TpaRequests {

    enum Type {
        /** The requester wants to go to the target ({@code /tpa}). */
        TO_TARGET,
        /** The requester wants the target to come to them ({@code /tpahere}). */
        HERE
    }

    record Request(UUID requester, UUID target, Type type, long createdAt) {
    }

    /** Why a request could not be created. */
    enum Refusal { SELF, ALREADY_SENT, TARGET_FULL }

    private final LongSupplier clock;
    private final Map<UUID, LinkedHashMap<UUID, Request>> byTarget = new LinkedHashMap<>();
    private long timeoutMillis;
    private int maxPending;

    TpaRequests(LongSupplier clock, long timeoutMillis, int maxPending) {
        this.clock = clock;
        configure(timeoutMillis, maxPending);
    }

    void configure(long timeoutMillis, int maxPending) {
        this.timeoutMillis = timeoutMillis;
        this.maxPending = maxPending;
    }

    /** Creates a request, or returns why not. A newer request from the same requester replaces the type. */
    Optional<Refusal> create(UUID requester, UUID target, Type type) {
        if (requester.equals(target)) {
            return Optional.of(Refusal.SELF);
        }
        LinkedHashMap<UUID, Request> pending = byTarget.computeIfAbsent(target, k -> new LinkedHashMap<>());
        Request existing = pending.get(requester);
        if (existing != null && !expired(existing) && existing.type() == type) {
            return Optional.of(Refusal.ALREADY_SENT);
        }
        pending.remove(requester);
        pending.values().removeIf(this::expired);
        if (pending.size() >= maxPending) {
            return Optional.of(Refusal.TARGET_FULL);
        }
        pending.put(requester, new Request(requester, target, type, clock.getAsLong()));
        return Optional.empty();
    }

    /** The request from {@code requester} to {@code target}, or the newest one when {@code requester} is null. */
    Optional<Request> find(UUID target, UUID requester) {
        LinkedHashMap<UUID, Request> pending = byTarget.get(target);
        if (pending == null) {
            return Optional.empty();
        }
        pending.values().removeIf(this::expired);
        if (requester != null) {
            return Optional.ofNullable(pending.get(requester));
        }
        Request newest = null;
        for (Request r : pending.values()) {
            newest = r;
        }
        return Optional.ofNullable(newest);
    }

    /** Requesters with an open request to {@code target} (for tab completion). */
    List<UUID> requesters(UUID target) {
        LinkedHashMap<UUID, Request> pending = byTarget.get(target);
        if (pending == null) {
            return List.of();
        }
        pending.values().removeIf(this::expired);
        return new ArrayList<>(pending.keySet());
    }

    void remove(Request request) {
        LinkedHashMap<UUID, Request> pending = byTarget.get(request.target());
        if (pending != null) {
            pending.remove(request.requester());
            if (pending.isEmpty()) {
                byTarget.remove(request.target());
            }
        }
    }

    /** All open requests sent by {@code requester}. */
    List<Request> sentBy(UUID requester) {
        List<Request> out = new ArrayList<>();
        for (LinkedHashMap<UUID, Request> pending : byTarget.values()) {
            Request r = pending.get(requester);
            if (r != null && !expired(r)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Removes and returns every expired request (so both sides can be told). */
    List<Request> expire() {
        List<Request> out = new ArrayList<>();
        byTarget.values().forEach(pending -> pending.values().removeIf(r -> {
            if (expired(r)) {
                out.add(r);
                return true;
            }
            return false;
        }));
        byTarget.values().removeIf(Map::isEmpty);
        return out;
    }

    /** Drops everything involving {@code player} (on quit). Returns the dropped requests. */
    List<Request> forget(UUID player) {
        List<Request> out = new ArrayList<>();
        LinkedHashMap<UUID, Request> received = byTarget.remove(player);
        if (received != null) {
            out.addAll(received.values());
        }
        byTarget.values().forEach(pending -> {
            Request r = pending.remove(player);
            if (r != null) {
                out.add(r);
            }
        });
        byTarget.values().removeIf(Map::isEmpty);
        return out;
    }

    private boolean expired(Request r) {
        return clock.getAsLong() - r.createdAt() >= timeoutMillis;
    }
}
