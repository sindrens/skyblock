package net.skybornmc.skyblock.crates;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a finished open is shown (M10.3). The open is already committed when this runs: keys taken, reward rolled, saved
 * and handed out ({@link CrateOpener}). Everything here is only the show: the B40 spinner for "Open 1" in a menu, the
 * in-world build-up and reward float at a pedestal, or the result at once (bulk opens, Fast crate opening). The result
 * message and the global ★ line come exactly once, when the show reveals the reward, or right away when the player
 * closes the menu or leaves.
 */
final class CrateShow {

    /** The /settings switch that skips every animation. */
    static final String SETTING_FAST = "fast-crate-opening";

    /** Where an open was started. */
    enum Kind { NONE, MENU, PEDESTAL }

    /** Where an open was started: a menu (spinner), a pedestal (in-world show) or elsewhere (result at once). */
    record Source(Kind kind, Menu menu, Pedestals.Pedestal pedestal) {

        static Source none() {
            return new Source(Kind.NONE, null, null);
        }

        static Source menu(Menu menu) {
            return new Source(Kind.MENU, menu, null);
        }

        static Source pedestal(Pedestals.Pedestal pedestal) {
            return new Source(Kind.PEDESTAL, null, pedestal);
        }
    }

    /** What a single open shows. Pure. */
    enum Mode { SPINNER, WORLD, INSTANT }

    /**
     * Picks the show of a committed open: only a single open animates (bulk opens show their summary), the Fast crate
     * opening switch skips the spinner; at a pedestal the world show always runs (fast = no build-up, the reward still
     * floats up), unless the crate yard is off.
     */
    static Mode mode(int grants, Kind source, boolean fast, boolean spinnerEnabled, boolean worldAvailable) {
        if (grants != 1) {
            return Mode.INSTANT;
        }
        return switch (source) {
            case MENU -> spinnerEnabled && !fast ? Mode.SPINNER : Mode.INSTANT;
            case PEDESTAL -> worldAvailable ? Mode.WORLD : Mode.INSTANT;
            case NONE -> Mode.INSTANT;
        };
    }

    /** Reveals exactly once (end of the spin, skip, close, quit). Pure. */
    static final class Reveal {
        private boolean done;

        /** True the first time only. */
        boolean claim() {
            if (done) {
                return false;
            }
            done = true;
            return true;
        }

        boolean done() {
            return done;
        }
    }

    private final CratesModule m;
    /** Players whose spinner or build-up is running (no second open until it is revealed). */
    private final Set<UUID> animating = ConcurrentHashMap.newKeySet();

    CrateShow(CratesModule module) {
        this.m = module;
    }

    boolean animating(UUID player) {
        return animating.contains(player);
    }

    void started(UUID player) {
        animating.add(player);
    }

    void ended(UUID player) {
        animating.remove(player);
    }

    void clear() {
        animating.clear();
    }

    /** True when the player turned on Fast crate opening. */
    boolean fast(Player player) {
        return m.core.services().optional(PlayerSettings.class).map(s -> s.get(player, SETTING_FAST)).orElse(m.settings.fastDefault);
    }

    /**
     * Shows a committed open (main thread). {@code online} is null when the opener left: nothing to show, but a ★ win is
     * still told in chat.
     */
    void present(Player online, String name, OpenPlan.Plan plan, Source source) {
        if (online == null) {
            broadcast(name, plan);
            return;
        }
        Mode mode = mode(plan.grants().size(), source.kind(), fast(online), m.settings.spinnerEnabled,
                m.settings.physicalEnabled && m.yard() != null);
        switch (mode) {
            case SPINNER -> {
                started(online.getUniqueId());
                SpinnerMenu spinner = new SpinnerMenu(m, plan);
                if (source.menu() != null && source.menu().isOpen() && source.menu().viewer() == online) {
                    source.menu().openChild(spinner);
                } else {
                    m.core.menus().open(online, spinner);
                }
            }
            case WORLD -> m.yard().play(online, plan, source.pedestal(), fast(online));
            case INSTANT -> revealed(online, online.getName(), plan);
        }
    }

    /** The reward is shown now: the result message (opener, if online) and the global ★ line. Call once per open. */
    void revealed(Player online, String name, OpenPlan.Plan plan) {
        if (online != null) {
            ended(online.getUniqueId());
            if (online.isOnline()) {
                m.opener().feedback(online, plan);
            }
        }
        broadcast(name, plan);
    }

    // ================================================================== global ★ line (RX12)

    /** True when a top win of this crate goes to global chat. Pure. */
    static boolean broadcasts(List<String> crates, String crateId) {
        return crates != null && crates.stream().anyMatch(c -> c != null && c.trim().equalsIgnoreCase(crateId));
    }

    /**
     * One chat line to everyone online for a top-band win of a listed crate (bulk: the first top reward + how many more).
     * Java sees the item when hovering the reward; Bedrock gets the same line as plain text.
     */
    void broadcast(String name, OpenPlan.Plan plan) {
        if (!broadcasts(m.settings.broadcastCrates, plan.crate().id())) {
            return;
        }
        List<OpenPlan.Grant> tops = plan.grants().stream().filter(OpenPlan.Grant::top).toList();
        if (tops.isEmpty()) {
            return;
        }
        OpenPlan.Grant first = tops.getFirst();
        String key = tops.size() == 1 ? "broadcast.top" : "broadcast.top-many";
        String player = name == null ? "?" : name;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Component reward = m.opener().grantName(viewer, plan.crate(), first);
            if (!m.bedrock(viewer)) {
                reward = reward.hoverEvent(hoverItem(viewer, plan.crate(), first, reward).asHoverEvent());
            }
            m.msg.send(viewer, key, Msg.p("player", player), Msg.mm("crate", m.crateMini(plan.crate())), Msg.c("reward", reward),
                    Msg.number("more", tops.size() - 1));
        }
    }

    /** The item shown when hovering a won reward: its icon, name, crate and chance. */
    ItemStack hoverItem(Player viewer, CrateCatalog.Crate crate, OpenPlan.Grant grant, Component name) {
        Material icon = grant.fallback() ? Material.PRISMARINE_CRYSTALS : m.icon(grant.reward());
        List<Component> lore = m.core.menus().lines(viewer, CratesModule.ID, "broadcast.hover", Msg.mm("crate", m.crateMini(crate)),
                Msg.p("chance", CrateMath.percent(crate.percent(grant.reward()))));
        return m.core.menus().item(new ItemStack(icon), name, lore);
    }

    /** A display item of a grant (spinner middle slot, the reward float): its icon and won name. */
    ItemStack grantItem(Player viewer, CrateCatalog.Crate crate, OpenPlan.Grant grant) {
        Material icon = grant.fallback() ? Material.PRISMARINE_CRYSTALS : m.icon(grant.reward());
        List<Component> lore = grant.fallback()
                ? m.core.menus().lines(viewer, CratesModule.ID, "spinner.fallback-lore",
                Msg.p("amount", m.format(Currency.CRYSTALS, grant.amount())))
                : List.of();
        return m.core.menus().item(new ItemStack(icon), m.opener().grantName(viewer, crate, grant), lore);
    }

    // ================================================================== sounds

    /**
     * A sound setting: {@code namespace:sound volume pitch} (e.g. {@code minecraft:block.note_block.hat 0.35 1.6}), or
     * empty when it is not one (then it is a sounds.yml key). Pure.
     */
    static Optional<Sound> sound(String spec) {
        if (spec == null || spec.isBlank() || !spec.contains(":")) {
            return Optional.empty();
        }
        String[] parts = spec.trim().split("\\s+");
        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
            if (volume < 0 || volume > 10 || pitch < 0.5f || pitch > 2f) {
                return Optional.empty();
            }
            return Optional.of(Sound.sound(Key.key(parts[0]), Sound.Source.MASTER, volume, pitch));
        } catch (InvalidKeyException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Plays a sound setting to one player (a raw sound, else a sounds.yml key). */
    void play(Player player, String spec) {
        Optional<Sound> raw = sound(spec);
        if (raw.isPresent()) {
            player.playSound(raw.get(), Sound.Emitter.self());
        } else if (spec != null && !spec.isBlank()) {
            m.core.sounds().play(player, spec.trim());
        }
    }
}
