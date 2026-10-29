package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /tags} and {@code /titles}: one menu, two tabs (owner decision). Owned ones can be equipped (one title + one
 * tag), titles not earned yet show locked with how to earn them, secret titles stay "???" until earned. The head at
 * the top previews your chat and tab line with what you have equipped.
 */
final class CosmeticsMenu extends PagedMenu<Cosmetic> {

    static final int SLOT_TITLES_TAB = 2;
    static final int SLOT_PREVIEW = 4;
    static final int SLOT_TAGS_TAB = 6;
    static final int SLOT_UNEQUIP = 47;
    static final int SLOT_EDIT = 51;

    private final CosmeticsModule module;
    private Cosmetics.Kind kind;
    private boolean wasLoaded;

    CosmeticsMenu(CosmeticsModule module, Cosmetics.Kind kind) {
        this.module = module;
        this.kind = kind;
    }

    private String tab() {
        return kind == Cosmetics.Kind.TITLE ? "titles" : "tags";
    }

    @Override
    protected List<Cosmetic> entries() {
        Set<String> owned = module.service().owned(viewer.getUniqueId(), kind);
        List<Cosmetic> list = new ArrayList<>(module.service().registered(kind));
        list.sort(Comparator.<Cosmetic, Boolean>comparing(c -> !owned.contains(c.id()))
                .thenComparing(Comparator.comparing(Cosmetic::rarity).reversed())
                .thenComparing(Cosmetic::id));
        return list;
    }

    @Override
    protected MenuButton entry(Cosmetic cosmetic) {
        CosmeticsService service = module.service();
        boolean owns = service.owns(viewer.getUniqueId(), kind, cosmetic.id());
        boolean equipped = cosmetic.id().equals(service.equippedId(viewer.getUniqueId(), kind));
        String rarityKey = "rarity." + cosmetic.rarity().name().toLowerCase(java.util.Locale.ROOT);
        if (!owns) {
            boolean hidden = cosmetic.secret();
            Component name = hidden ? module.text(viewer, "menu.secret-name") : service.display(viewer, cosmetic);
            List<Component> lore = new ArrayList<>();
            lore.add(module.text(viewer, "menu.rarity", Msg.c("rarity", module.text(viewer, rarityKey))));
            lore.add(Component.empty());
            lore.addAll(hidden ? module.lines(viewer, "menu.secret-lore")
                    : module.lines(viewer, kind == Cosmetics.Kind.TITLE ? "menu.locked-title" : "menu.locked-tag",
                    Msg.c("source", module.core().messages().parse(viewer, cosmetic.source().isBlank()
                            ? module.raw(kind == Cosmetics.Kind.TITLE ? "menu.default-title-source" : "menu.default-tag-source")
                            : cosmetic.source()))));
            return MenuButton.display(service().item(Material.LIGHT_GRAY_DYE, module.text(viewer, "menu.locked-name",
                    Msg.p("name", PlainTextComponentSerializer.plainText().serialize(name))), lore));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(module.text(viewer, "menu.rarity", Msg.c("rarity", module.text(viewer, rarityKey))));
        if (!cosmetic.source().isBlank()) {
            lore.add(module.text(viewer, "menu.earned", Msg.c("source", module.core().messages().parse(viewer, cosmetic.source()))));
        }
        lore.addAll(module.lines(viewer, equipped ? "menu.status-equipped" : "menu.status-owned"));
        lore.addAll(module.lines(viewer, equipped ? "menu.action-unequip" : "menu.action-equip"));
        ItemStack item = service().item(kind == Cosmetics.Kind.TITLE ? Material.NAME_TAG : Material.ARMOR_STAND,
                service.display(viewer, cosmetic), lore);
        if (equipped) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return MenuButton.of(item, click -> {
            service.equip(viewer.getUniqueId(), kind, equipped ? null : cosmetic.id());
            module.core().sounds().play(viewer, equipped ? "click" : "success");
            module.msg().send(viewer, equipped ? "equip.removed-" + tab() : "equip.set-" + tab(),
                    Msg.c("display", service.display(viewer, cosmetic)));
            refresh();
        });
    }

    private net.skybornmc.skyblock.common.menu.MenuService service() {
        return module.core().menus();
    }

    @Override
    protected void decorate() {
        CosmeticsService service = module.service();
        set(SLOT_TITLES_TAB, tabItem(Cosmetics.Kind.TITLE, Material.NAME_TAG), click -> switchTo(Cosmetics.Kind.TITLE));
        set(SLOT_TAGS_TAB, tabItem(Cosmetics.Kind.TAG, Material.ARMOR_STAND), click -> switchTo(Cosmetics.Kind.TAG));

        ItemStack head = service().item(Material.PLAYER_HEAD, module.text(viewer, "menu.preview.name"),
                List.of(module.text(viewer, "menu.preview.chat-label"),
                        module.preview().chatLine(viewer, module.text(viewer, "menu.preview.message"), Map.of()),
                        Component.empty(),
                        module.text(viewer, "menu.preview.tab-label"),
                        module.preview().tabLine(viewer, Map.of())));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        set(SLOT_PREVIEW, head);

        service.equipped(viewer.getUniqueId(), kind).ifPresent(current -> set(SLOT_UNEQUIP,
                service().item(Material.BARRIER, module.text(viewer, "menu.unequip-" + tab() + ".name"),
                        module.lines(viewer, "menu.unequip-" + tab() + ".lore", Msg.c("display", service.display(viewer, current)))),
                click -> {
                    service.equip(viewer.getUniqueId(), kind, null);
                    module.core().sounds().play(viewer, "click");
                    refresh();
                }));

        if (viewer.hasPermission(CosmeticsModule.PERM_EDIT)) {
            set(SLOT_EDIT, service().item(Material.WRITABLE_BOOK, module.text(viewer, "menu.edit.name"),
                    module.lines(viewer, "menu.edit.lore")), click -> openChild(new EditorListMenu(module, kind)));
        }
        wasLoaded = service.loaded(viewer.getUniqueId());
        if (!wasLoaded) {
            set(SLOT_EMPTY, service().item(Material.CLOCK, viewer, "loading"));
        }
    }

    @Override
    public int tickInterval() {
        return 10;
    }

    /** Owned titles/tags load right after join: refresh once they arrive. */
    @Override
    protected void tick() {
        boolean loaded = module.service().loaded(viewer.getUniqueId());
        if (loaded && !wasLoaded) {
            wasLoaded = true;
            refresh();
        }
    }

    private ItemStack tabItem(Cosmetics.Kind tabKind, Material icon) {
        String key = tabKind == Cosmetics.Kind.TITLE ? "titles" : "tags";
        int owned = module.service().owned(viewer.getUniqueId(), tabKind).size();
        int total = module.service().registered(tabKind).size();
        List<Component> lore = new ArrayList<>(module.lines(viewer, "menu.tab-" + key + ".lore",
                Msg.number("owned", owned), Msg.number("total", total)));
        lore.addAll(module.lines(viewer, tabKind == kind ? "menu.tab-selected" : "menu.tab-action"));
        ItemStack item = service().item(icon, module.text(viewer, "menu.tab-" + key + ".name"), lore);
        if (tabKind == kind) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private void switchTo(Cosmetics.Kind next) {
        if (next == kind) {
            return;
        }
        kind = next;
        page(0);
        module.core().menus().open(viewer, this); // re-open so the title shows the tab
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(module.text(viewer, "menu.title"), module.text(viewer, "menu.tab-" + tab() + ".crumb"));
    }

    @Override
    public String messageModule() {
        return CosmeticsModule.ID;
    }

    @Override
    protected String emptyKey() {
        return "menu.empty";
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "tags";
    }
}
