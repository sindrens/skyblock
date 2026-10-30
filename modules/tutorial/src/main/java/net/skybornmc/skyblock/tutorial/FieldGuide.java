package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The Phoenix Field Guide (TUTORIAL.md §6): a written book with the core loop in short pages, read on Java and Bedrock.
 * Title, author and pages come from {@code messages/tutorial.yml → book} (one list entry per page; {@code <br>} = new line).
 */
final class FieldGuide {

    private FieldGuide() {
    }

    static ItemStack book(TutorialModule module, Player viewer) {
        ItemStack stack = new ItemStack(Material.WRITTEN_BOOK);
        if (!(stack.getItemMeta() instanceof BookMeta meta)) {
            return stack;
        }
        var msg = module.msg();
        String title = PlainTextComponentSerializer.plainText().serialize(msg.component(viewer, "book.title"));
        meta.setTitle(title.length() > 32 ? title.substring(0, 32) : title);
        meta.setAuthor(PlainTextComponentSerializer.plainText().serialize(msg.component(viewer, "book.author")));
        String raw = module.core().messages().raw(TutorialModule.ID, "book.pages");
        List<Component> pages = new ArrayList<>();
        if (raw != null) {
            for (String page : raw.split("<newline>", -1)) {
                pages.add(module.core().messages().parse(viewer, page));
            }
        }
        if (!pages.isEmpty()) {
            meta.addPages(pages.toArray(Component[]::new));
        }
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        stack.setItemMeta(meta);
        return stack;
    }
}
