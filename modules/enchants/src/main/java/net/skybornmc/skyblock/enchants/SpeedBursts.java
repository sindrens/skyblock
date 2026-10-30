package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ember Momentum and Phoenix Haste (ENCHANTS §6.1): a temporary {@code block_break_speed} modifier on the player (works on
 * Bedrock through Geyser, no client mods). Every burst stays inside the speed family cap (it only fills what the tool's
 * own speed family leaves below the cap) and switches off while the player breaks
 * {@code gathering.max-blocks-per-second} (6) or more blocks a second, the hard cap of the balance model. Main thread.
 */
final class SpeedBursts {

    static final String MOMENTUM = "ember_momentum";
    static final String HASTE = "phoenix_haste";
    private static final NamespacedKey KEY = ItemKeys.key("enchant_burst");

    private static final class State {
        BurstMath.Momentum momentum = BurstMath.Momentum.NONE;
        BurstMath.Haste haste = BurstMath.Haste.NONE;
        final BurstMath.Rate rate = new BurstMath.Rate(16);
        int momentumLevel;
        int hasteLevel;
        double toolFamily;
        double applied;
    }

    private final EnchantsModule module;
    private final ProcBar bar;
    private final Map<UUID, State> states = new HashMap<>();
    private Scheduler.Task task;

    SpeedBursts(EnchantsModule module, ProcBar bar) {
        this.module = module;
        this.bar = bar;
    }

    void start() {
        if (task == null) {
            task = module.core.scheduler().repeat("enchants-bursts", 10L, 10L, this::tick);
        }
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                apply(p, e.getValue(), 0);
            }
        }
        states.clear();
    }

    /** One manual break in The Depths with a custom pickaxe/axe (area blocks are not manual breaks). */
    void action(Player player, ToolInfo tool, Map<String, Integer> enchants, long now) {
        int momentum = enchants.getOrDefault(MOMENTUM, 0);
        int haste = enchants.getOrDefault(HASTE, 0);
        State s = states.get(player.getUniqueId());
        if (s == null) {
            if (momentum <= 0 && haste <= 0) {
                return; // nothing to track for players without burst enchants
            }
            s = new State();
            states.put(player.getUniqueId(), s);
        }
        s.rate.add(now);
        s.momentumLevel = momentum;
        s.hasteLevel = haste;
        BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
        s.toolFamily = bonus == null ? 0 : bonus.capped(BonusFamily.SPEED, BonusContext.ofTool(tool));
        if (momentum > 0) {
            int before = BurstMath.stacks(s.momentum, now, idleMs(momentum));
            s.momentum = BurstMath.momentum(s.momentum, now, (int) value(MOMENTUM, "actions", momentum), idleMs(momentum),
                    (int) value(MOMENTUM, "max-stacks", momentum));
            if (s.momentum.stacks() > before) {
                bar.show(player, "proc.momentum", module.config().soundGatherProc, Msg.p("enchant", module.text().displayName(MOMENTUM, momentum)),
                        Msg.p("stacks", s.momentum.stacks()), Msg.p("max", (int) value(MOMENTUM, "max-stacks", momentum)));
            }
        } else {
            s.momentum = BurstMath.Momentum.NONE;
        }
        if (haste > 0) {
            boolean wasActive = BurstMath.active(s.haste, now);
            s.haste = BurstMath.haste(s.haste, now, ThreadLocalRandom.current().nextDouble(), value(HASTE, "chance", haste),
                    Math.round(value(HASTE, "seconds", haste) * 1000), Math.round(value(HASTE, "cooldown", haste) * 1000));
            if (!wasActive && BurstMath.active(s.haste, now)) {
                bar.show(player, "proc.haste", module.config().soundGatherProc, Msg.p("enchant", module.text().displayName(HASTE, haste)),
                        Msg.p("percent", Format.number(Math.round(value(HASTE, "percent", haste)))));
            }
        }
        update(player, s, now);
    }

    private void tick() {
        if (states.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            State s = e.getValue();
            update(p, s, now);
            if (s.applied <= 0 && BurstMath.stacks(s.momentum, now, idleMs(Math.max(1, s.momentumLevel))) == 0
                    && !BurstMath.active(s.haste, now) && now >= s.haste.readyAt()) {
                it.remove(); // idle: nothing to keep
            }
        }
    }

    private void update(Player player, State s, long now) {
        double wanted = 0;
        if (s.momentumLevel > 0) {
            wanted = BurstMath.stacks(s.momentum, now, idleMs(s.momentumLevel)) * value(MOMENTUM, "percent", s.momentumLevel);
        }
        if (s.hasteLevel > 0 && BurstMath.active(s.haste, now)) {
            wanted = Math.max(wanted, value(HASTE, "percent", s.hasteLevel));
        }
        BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
        double cap = bonus == null ? 0 : bonus.cap(BonusFamily.SPEED);
        apply(player, s, BurstMath.allowed(wanted, s.toolFamily, cap, s.rate.lastSecond(now), module.config().maxBlocksPerSecond));
    }

    private void apply(Player player, State s, double percent) {
        if (Math.abs(s.applied - percent) < 1e-9) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(KEY);
        if (percent > 0) {
            attribute.addTransientModifier(new AttributeModifier(KEY, percent / 100.0, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
        }
        s.applied = percent;
    }

    /** Quit: the modifier is transient, but it is removed anyway so nothing lingers on a reconnect within the session. */
    void forget(Player player) {
        State s = states.remove(player.getUniqueId());
        if (s != null) {
            apply(player, s, 0);
        }
    }

    private long idleMs(int level) {
        return Math.round(value(MOMENTUM, "idle-seconds", level) * 1000);
    }

    private double value(String id, String key, int level) {
        double v = module.registry().get(id).map(d -> d.value(key, level)).orElse(Double.NaN);
        return Double.isFinite(v) ? v : 0;
    }
}
