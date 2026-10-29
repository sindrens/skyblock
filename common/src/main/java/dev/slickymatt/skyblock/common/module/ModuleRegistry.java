package dev.slickymatt.skyblock.common.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers modules, enables them in dependency order and isolates failures:
 * a module that fails to enable (or whose dependency failed) is skipped instead of
 * taking the whole server down.
 */
public final class ModuleRegistry {

    public enum State { REGISTERED, ENABLED, FAILED, DISABLED }

    private final Logger logger;
    private final Map<String, SkyblockModule> modules = new LinkedHashMap<>();
    private final Map<String, State> states = new LinkedHashMap<>();
    private final List<SkyblockModule> enableOrder = new ArrayList<>();

    public ModuleRegistry(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(SkyblockModule module) {
        Objects.requireNonNull(module, "module");
        String id = module.id();
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid module id: " + id);
        }
        if (modules.putIfAbsent(id, module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + id);
        }
        states.put(id, State.REGISTERED);
    }

    /** Enables every registered module in dependency order. */
    public void enableAll() {
        for (SkyblockModule module : resolveOrder()) {
            String missing = firstUnavailableDependency(module);
            if (missing != null) {
                states.put(module.id(), State.FAILED);
                logger.severe("Module '" + module.id() + "' skipped: dependency '" + missing + "' is not enabled.");
                continue;
            }
            try {
                module.enable();
                states.put(module.id(), State.ENABLED);
                enableOrder.add(module);
                logger.info("Module '" + module.id() + "' enabled.");
            } catch (Exception e) {
                states.put(module.id(), State.FAILED);
                logger.log(Level.SEVERE, "Module '" + module.id() + "' failed to enable.", e);
            }
        }
    }

    /** Disables enabled modules in reverse enable order. */
    public void disableAll() {
        List<SkyblockModule> reversed = new ArrayList<>(enableOrder);
        Collections.reverse(reversed);
        for (SkyblockModule module : reversed) {
            try {
                module.disable();
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Module '" + module.id() + "' threw while disabling.", e);
            }
            states.put(module.id(), State.DISABLED);
        }
        enableOrder.clear();
    }

    /** Reloads every enabled module. Returns the ids that failed. */
    public List<String> reloadAll() {
        List<String> failed = new ArrayList<>();
        for (SkyblockModule module : enableOrder) {
            try {
                module.reload();
            } catch (Exception e) {
                failed.add(module.id());
                logger.log(Level.SEVERE, "Module '" + module.id() + "' failed to reload.", e);
            }
        }
        return failed;
    }

    public State state(String id) {
        return states.get(id);
    }

    public Map<String, State> states() {
        return Collections.unmodifiableMap(states);
    }

    private String firstUnavailableDependency(SkyblockModule module) {
        for (String dep : module.dependencies()) {
            if (states.get(dep) != State.ENABLED) {
                return dep;
            }
        }
        return null;
    }

    /** Topological sort; throws on unknown dependencies or cycles so mistakes surface at startup. */
    List<SkyblockModule> resolveOrder() {
        List<SkyblockModule> order = new ArrayList<>();
        Set<String> done = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String id : modules.keySet()) {
            visit(id, done, visiting, order);
        }
        return order;
    }

    private void visit(String id, Set<String> done, Set<String> visiting, List<SkyblockModule> order) {
        if (done.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalStateException("Module dependency cycle involving '" + id + "'");
        }
        SkyblockModule module = modules.get(id);
        for (String dep : module.dependencies()) {
            if (!modules.containsKey(dep)) {
                throw new IllegalStateException("Module '" + id + "' depends on unknown module '" + dep + "'");
            }
            visit(dep, done, visiting, order);
        }
        visiting.remove(id);
        done.add(id);
        order.add(module);
    }
}
