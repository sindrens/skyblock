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
    volatile HubLayout hub = new HubLayout(List.of());
    volatile HubLayout staff = new HubLayout(List.of());
    volatile WarpLayout warps = new WarpLayout(List.of());
    volatile HelpBook help = HelpBook.empty();

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

    /** The locked "Coming soon" look: real icon, gray name with the suffix, the description, then a locked line. */
    ItemStack comingSoon(Player viewer, Material icon, Component name, List<Component> description) {
        String plain = PlainTextComponentSerializer.plainText().serialize(name);
        List<Component> lore = new ArrayList<>(description);
        lore.addAll(lines(viewer, "coming-soon.lore"));
        return item(icon, text(viewer, "coming-soon.name", net.skybornmc.skyblock.common.text.Msg.p("name", plain)), lore);
    }
}
