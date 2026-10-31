package net.skybornmc.skyblock.menus;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared state and text helpers of the menus module: the core, its messages ({@code messages/navigation.yml}), the
 * current hub layout, help book and settings registry. Menus read them on every build, so a reload applies instantly.
 */
public final class Nav {

    /** Messages module of this module's texts (the framework owns {@code messages/menus.yml}). */
    public static final String TEXTS = "navigation";

    final Core core;
    final ModuleMessages msg;
    final SettingsRegistry settings;
    volatile ButtonLayout hub = new ButtonLayout(List.of(), java.util.Map.of());
    volatile ButtonLayout you = new ButtonLayout(List.of(), java.util.Map.of());
    volatile HubLayout staff = new HubLayout(List.of());
    volatile WarpLayout travel = new WarpLayout(List.of());
    volatile HelpBook help = HelpBook.empty();
    /** Test server: show unbuilt hub/You buttons in the "Coming soon" look ({@code navigation.yml → hub.show-unbuilt}). */
    volatile boolean showUnbuilt;
    /** {@code /menu → Top} (GUI-4c): seconds a loaded player board (richest, top skills) is reused. */
    volatile int topCacheSeconds = 30;
    /** The loaded player boards of {@code /menu → Top}, shared by every viewer. */
    final TopCache<List<net.skybornmc.skyblock.common.leaderboard.TopLine>> tops = new TopCache<>(System::currentTimeMillis);

    Nav(Core core, SettingsRegistry settings) {
        this.core = core;
        this.msg = core.messages(TEXTS);
        this.settings = settings;
    }

    /**
     * Placeholder replaced before parsing, so it also works inside click/hover arguments. Only for safe values
     * (command paths, topic ids); quotes and tag brackets are stripped.
     */
    static TagResolver pre(String name, Object value) {
        String safe = String.valueOf(value).replace("'", "").replace("<", "").replace(">", "").replace("\\", "");
        return TagResolver.resolver(name, net.kyori.adventure.text.minimessage.tag.Tag.preProcessParsed(safe));
    }

    /** Parses a free text (help.yml) for a viewer: style tags, placeholders, no italics. */
    Component parse(Audience viewer, String text, TagResolver... placeholders) {
        return core.messages().parse(viewer, text, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    /** A title crumb from a free text: colors stripped, so it reads like every other title. */
    Component crumb(Audience viewer, String text) {
        return Component.text(PlainTextComponentSerializer.plainText().serialize(parse(viewer, text)));
    }

    List<Component> parse(Audience viewer, List<String> lines, TagResolver... placeholders) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(parse(viewer, line, placeholders));
        }
        return out;
    }

    Component text(Player viewer, String key, TagResolver... placeholders) {
        return core.menus().text(viewer, TEXTS, key, placeholders);
    }

    List<Component> lines(Player viewer, String key, TagResolver... placeholders) {
        return core.menus().lines(viewer, TEXTS, key, placeholders);
    }

    ItemStack item(Material icon, Component name, List<Component> lore) {
        return core.menus().item(icon, name, lore);
    }

    /** The item of a parsed icon id ({@link ButtonLayout}, {@link WarpLayout}); {@code fallback} if it is not an item. */
    static Material material(String id, Material fallback) {
        Material m = Material.matchMaterial(id == null ? "" : id);
        return m == null || !m.isItem() ? fallback : m;
    }

    /** Text of {@code key}, or {@code fallback-key} with the prettified {@code id} when an owner-added button has no texts yet. */
    Component textOr(Player viewer, String key, String fallbackKey, String id) {
        if (core.messages().raw(TEXTS, key) == null) {
            return text(viewer, fallbackKey, pre("name", TravelMenu.pretty(id)));
        }
        return text(viewer, key);
    }

    /**
     * Your head (GUI_DESIGN §7.1): name, rank, money, crystals and playtime. {@code key} = the message section
     * ({@code hub.head} with a click line, {@code you.head} without).
     */
    ItemStack head(Player viewer, String key) {
        var economy = core.services().optional(net.skybornmc.skyblock.common.economy.EconomyService.class);
        String money = economy.flatMap(e -> {
            var v = e.cached(viewer.getUniqueId(), net.skybornmc.skyblock.common.economy.Currency.MONEY);
            return v.isPresent() ? java.util.Optional.of(e.format(net.skybornmc.skyblock.common.economy.Currency.MONEY, v.getAsLong()))
                    : java.util.Optional.empty();
        }).orElse("-");
        String crystals = economy.flatMap(e -> {
            var v = e.cached(viewer.getUniqueId(), net.skybornmc.skyblock.common.economy.Currency.CRYSTALS);
            return v.isPresent() ? java.util.Optional.of(e.format(net.skybornmc.skyblock.common.economy.Currency.CRYSTALS, v.getAsLong()))
                    : java.util.Optional.empty();
        }).orElse("-");
        long playtime = core.players().get(viewer.getUniqueId()).map(d -> d.playtimeMillis()).orElse(0L);
        ItemStack head = item(Material.PLAYER_HEAD, text(viewer, key + ".name"), lines(viewer, key + ".lore",
                net.skybornmc.skyblock.common.text.Msg.p("money", money),
                net.skybornmc.skyblock.common.text.Msg.p("crystals", crystals),
                net.skybornmc.skyblock.common.text.Msg.p("playtime",
                        net.skybornmc.skyblock.common.util.Format.duration(java.time.Duration.ofMillis(playtime)))));
        head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        return head;
    }

    /** The locked "Coming soon" look: real icon, gray name with the suffix, the description, then a locked line. */
    ItemStack comingSoon(Player viewer, Material icon, Component name, List<Component> description) {
        String plain = PlainTextComponentSerializer.plainText().serialize(name);
        List<Component> lore = new ArrayList<>(description);
        lore.addAll(lines(viewer, "coming-soon.lore"));
        return item(icon, text(viewer, "coming-soon.name", net.skybornmc.skyblock.common.text.Msg.p("name", plain)), lore);
    }
}
