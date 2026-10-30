package net.skybornmc.skyblock.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Owner answer 304: signs, anvil names and book text go through the chat filter too. Replaced words become ***; a blocked
 * text (slur, link, IP, phone number) is refused: the sign or book edit is cancelled, the anvil shows no result. Staff
 * with {@code skyblock.chat.bypass} are not filtered. Every refusal is audited ({@code text_filtered}).
 */
final class TextSurfaces implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Core core;
    private final ModuleMessages msg;
    private final Supplier<ChatConfig> config;

    TextSurfaces(Core core, ModuleMessages msg, Supplier<ChatConfig> config) {
        this.core = core;
        this.msg = msg;
        this.config = config;
    }

    /** Pure: the filtered lines, or {@code null} when one of them is blocked. */
    static List<String> filterLines(List<String> lines, ChatFilter.Rules rules, boolean linkBypass) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                out.add(line);
                continue;
            }
            ChatFilter.Result result = ChatFilter.check(line, rules, linkBypass);
            if (result.blocked() != null) {
                return null;
            }
            // Shouting is fine on signs and in books: only words are replaced, the case is kept.
            out.add(result.changes().contains("word") ? starOnly(line, result.text()) : line);
        }
        return out;
    }

    /** Keeps the original letters except where the filter put stars (the caps rule lowers whole lines). */
    static String starOnly(String original, String filtered) {
        if (original.length() != filtered.length()) {
            return filtered;
        }
        StringBuilder b = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            b.append(filtered.charAt(i) == '*' && original.charAt(i) != '*' ? '*' : original.charAt(i));
        }
        return b.toString();
    }

    private boolean exempt(Player player) {
        return !config.get().filterSurfaces || player.hasPermission(ChatModule.PERM_BYPASS);
    }

    private boolean links(Player player) {
        return player.hasPermission(ChatModule.PERM_LINKS);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Component line : event.lines()) {
            lines.add(line == null ? "" : PLAIN.serialize(line));
        }
        List<String> filtered = filterLines(lines, config.get().rules(), links(player));
        if (filtered == null) {
            event.setCancelled(true);
            refused(player, "sign", String.join(" | ", lines));
            return;
        }
        for (int i = 0; i < filtered.size(); i++) {
            if (!filtered.get(i).equals(lines.get(i))) {
                event.line(i, Component.text(filtered.get(i)));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        BookMeta meta = event.getNewBookMeta();
        List<String> pages = new ArrayList<>();
        for (Component page : meta.pages()) {
            pages.add(PLAIN.serialize(page));
        }
        String title = meta.hasTitle() ? PLAIN.serialize(meta.title()) : null;
        List<String> all = new ArrayList<>(pages);
        if (title != null) {
            all.add(title);
        }
        List<String> filtered = filterLines(all, config.get().rules(), links(player));
        if (filtered == null) {
            event.setCancelled(true);
            refused(player, "book", title == null ? "" : title);
            return;
        }
        if (filtered.equals(all)) {
            return;
        }
        List<Component> newPages = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            newPages.add(Component.text(filtered.get(i)));
        }
        meta.pages(newPages);
        if (title != null) {
            meta.title(Component.text(filtered.get(filtered.size() - 1)));
        }
        event.setNewBookMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || exempt(player)) {
            return;
        }
        String rename = event.getView().getRenameText();
        ItemStack result = event.getResult();
        if (rename == null || rename.isBlank() || result == null || result.isEmpty()) {
            return;
        }
        List<String> filtered = filterLines(List.of(rename), config.get().rules(), links(player));
        if (filtered == null) {
            event.setResult(null);
            return;
        }
        if (!filtered.getFirst().equals(rename)) {
            ItemStack copy = result.clone();
            copy.editMeta(meta -> meta.displayName(Component.text(filtered.getFirst())));
            event.setResult(copy);
        }
    }

    private void refused(Player player, String where, String text) {
        msg.send(player, "blocked.surface");
        core.sounds().play(player, "error");
        core.audit().logPlayer(AuditCategory.CHAT, "text_filtered", Actor.player(player), player.getUniqueId(), player.getName(),
                null, player.getLocation(), Map.of("where", where, "text", text.length() > 200 ? text.substring(0, 200) : text));
    }
}
