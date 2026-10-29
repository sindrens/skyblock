package net.skybornmc.skyblock.common.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Modules publish and consume services by interface, so they never touch each other's classes.
 *
 * <pre>{@code
 * core.services().register("economy", EconomyService.class, new SqlEconomy(...));   // in EconomyModule#enable
 * EconomyService eco = core.services().get(EconomyService.class);                   // in a dependent module
 * }</pre>
 *
 * Every registration records its owner (module id). {@link #unregisterAll(String)} is called when that module disables,
 * so no one keeps calling a disabled module. Consumers should look services up when they need them (or depend on the
 * owning module) instead of caching a reference forever.
 */
public final class ServiceRegistry {

    private record Entry(String owner, Object service) {
    }

    private final Map<Class<?>, Entry> services = new ConcurrentHashMap<>();
    private final Logger log;

    public ServiceRegistry(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Publishes {@code service} under {@code type}.
     *
     * @throws IllegalStateException if another module already provides this type
     */
    public <T> void register(String owner, Class<T> type, T service) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(service, "service");
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException(service.getClass().getName() + " does not implement " + type.getName());
        }
        Entry previous = services.putIfAbsent(type, new Entry(owner, service));
        if (previous != null) {
            throw new IllegalStateException("Service " + type.getSimpleName() + " is already provided by '" + previous.owner()
                    + "' (tried: '" + owner + "')");
        }
    }

    /** The service, or an exception that names the missing type (use when your module depends on the provider). */
    public <T> T get(Class<T> type) {
        return optional(type).orElseThrow(() -> new IllegalStateException(
                "No service " + type.getSimpleName() + " registered. Is the providing module enabled?"));
    }

    /** The service if some enabled module provides it (use for soft integrations). */
    public <T> Optional<T> optional(Class<T> type) {
        Entry entry = services.get(type);
        return entry == null ? Optional.empty() : Optional.of(type.cast(entry.service()));
    }

    public boolean has(Class<?> type) {
        return services.containsKey(type);
    }

    /** Removes one service if {@code owner} provides it. */
    public void unregister(String owner, Class<?> type) {
        services.computeIfPresent(type, (t, e) -> e.owner().equals(owner) ? null : e);
    }

    /** Removes every service of a module (call when it disables). */
    public void unregisterAll(String owner) {
        services.entrySet().removeIf(e -> {
            boolean match = e.getValue().owner().equals(owner);
            if (match) {
                log.fine("Service " + e.getKey().getSimpleName() + " of '" + owner + "' unregistered.");
            }
            return match;
        });
    }

    /** Registered service types with their owners (for /skyblock modules or debugging). */
    public Map<String, String> describe() {
        Map<String, String> out = new java.util.TreeMap<>();
        services.forEach((type, entry) -> out.put(type.getSimpleName(), entry.owner()));
        return out;
    }

    public List<Class<?>> types() {
        return List.copyOf(services.keySet());
    }
}
