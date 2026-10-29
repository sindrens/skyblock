package net.skybornmc.skyblock.logs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Picks the category of a log search: "All" plus every category the viewer may see. Click a category to search only
 * that one (click it again in the hub to change it); the choice returns to the hub.
 */
final class LogCategoryMenu extends PagedMenu<Optional<AuditCategory>> {

    private final LogsModule module;
    private final LogFilter filter;

    LogCategoryMenu(LogsModule module, LogFilter filter) {
        this.module = module;
        this.filter = filter;
    }

    @Override
    public String messageModule() {
        return LogsModule.ID;
    }

    @Override
    public String infoKey() {
        return "category-menu.info";
    }

    @Override
    public String helpTopic() {
        return "logs";
    }

    @Override
    protected List<Optional<AuditCategory>> entries() {
        List<Optional<AuditCategory>> out = new ArrayList<>();
        out.add(Optional.empty());
        module.allowed(viewer).forEach(c -> out.add(Optional.of(c)));
        return out;
    }

    @Override
    protected MenuButton entry(Optional<AuditCategory> value) {
        boolean selected = value.isEmpty() ? filter.categories.isEmpty()
                : filter.categories.size() == 1 && filter.categories.contains(value.get());
        Material icon = value.map(module.render::icon).orElse(Material.HOPPER);
        Component name = value.isEmpty()
                ? module.render.text(viewer, "category-menu.all.name")
                : module.render.text(viewer, "category-menu.entry.name", Msg.c("category", module.render.category(viewer, value.get())));
        List<Component> lore = new ArrayList<>(value.isEmpty()
                ? service.lines(viewer, LogsModule.ID, "category-menu.all.lore")
                : service.lines(viewer, LogsModule.ID, "category-menu.descriptions." + value.get().key()));
        lore.addAll(service.lines(viewer, LogsModule.ID, selected ? "category-menu.selected" : "category-menu.click"));
        ItemStack item = service.item(icon, name, lore);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.of(item, click -> {
            filter.categories.clear();
            value.ifPresent(filter.categories::add);
            service.sound(viewer, "click");
            back();
        });
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.render.text(viewer, "title.logs"), module.render.text(viewer, "title.category"));
    }
}
