package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Staff: every title (or tag) with a Create button; click one to edit it. Changes are saved to tags-titles.yml. */
final class EditorListMenu extends PagedMenu<Cosmetic> {

    static final int SLOT_CREATE = 49 + 2; // 51, next to the fixed Help button

    private final CosmeticsModule module;
    private final Cosmetics.Kind kind;

    EditorListMenu(CosmeticsModule module, Cosmetics.Kind kind) {
        this.module = module;
        this.kind = kind;
    }

    private String tab() {
        return kind == Cosmetics.Kind.TITLE ? "titles" : "tags";
    }

    @Override
    protected List<Cosmetic> entries() {
        return module.service().registered(kind);
    }

    @Override
    protected MenuButton entry(Cosmetic cosmetic) {
        List<Component> lore = new ArrayList<>();
        lore.add(module.text(viewer, "editor.id", Msg.p("id", cosmetic.id())));
        lore.add(module.text(viewer, "menu.rarity", Msg.c("rarity",
                module.text(viewer, "rarity." + cosmetic.rarity().name().toLowerCase(Locale.ROOT)))));
        if (!cosmetic.source().isBlank()) {
            lore.add(module.text(viewer, "editor.source", Msg.c("source", module.core().messages().parse(viewer, cosmetic.source()))));
        }
        lore.addAll(module.lines(viewer, "editor.action-edit"));
        return MenuButton.of(module.core().menus().item(kind == Cosmetics.Kind.TITLE ? Material.NAME_TAG : Material.ARMOR_STAND,
                module.service().display(viewer, cosmetic), lore), click -> openChild(new EditorEntryMenu(module, kind, cosmetic.id())));
    }

    @Override
    protected void decorate() {
        set(SLOT_CREATE, module.core().menus().item(Material.LIME_DYE, module.text(viewer, "editor.create-" + tab() + ".name"),
                module.lines(viewer, "editor.create-" + tab() + ".lore")), click -> createStep1());
    }

    /** Create: ask for the id, then the display text. */
    private void createStep1() {
        module.core().menus().prompt(viewer, CosmeticsModule.ID, "editor.prompt-id", 64, input -> {
            String id = input.strip().toLowerCase(Locale.ROOT);
            if (!Cosmetic.validId(id)) {
                module.msg().send(viewer, "editor.invalid-id");
                module.core().menus().open(viewer, this);
                return;
            }
            if (module.registry().get(kind, id).isPresent()) {
                module.msg().send(viewer, "editor.exists", Msg.p("id", id));
                module.core().menus().open(viewer, this);
                return;
            }
            module.core().menus().prompt(viewer, CosmeticsModule.ID, "editor.prompt-display", 200, display -> {
                Cosmetic created = new Cosmetic(kind, id, display.strip(), Rarity.COMMON, "", false, true);
                if (module.save(viewer, created, "create")) {
                    openChild(new EditorEntryMenu(module, kind, id));
                } else {
                    module.core().menus().open(viewer, this);
                }
            }, () -> module.core().menus().open(viewer, this));
        }, () -> module.core().menus().open(viewer, this));
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.text(viewer, "menu.title"), module.text(viewer, "editor.crumb-" + tab()));
    }

    @Override
    public String messageModule() {
        return CosmeticsModule.ID;
    }

    @Override
    protected String emptyKey() {
        return "editor.empty";
    }

    @Override
    public String infoKey() {
        return "editor.info";
    }

    @Override
    public String helpTopic() {
        return "tags";
    }
}
