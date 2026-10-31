package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Staff: edit one title/tag. Display text and "how to get it" via chat input, rarity from a pick list, secret and
 * season flags toggle, tags can be handed out as a tag item, and delete asks to confirm. Every change is saved and logged.
 */
final class EditorEntryMenu extends Menu {

    static final int SLOT_PREVIEW = 4;
    static final int SLOT_DISPLAY = 20;
    static final int SLOT_RARITY = 21;
    static final int SLOT_SOURCE = 22;
    static final int SLOT_SECRET = 23;
    static final int SLOT_SEASON = 24;
    static final int SLOT_ITEM = 30;
    static final int SLOT_DELETE = 32;

    private final CosmeticsModule module;
    private final Cosmetics.Kind kind;
    private final String id;

    EditorEntryMenu(CosmeticsModule module, Cosmetics.Kind kind, String id) {
        this.module = module;
        this.kind = kind;
        this.id = id;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.text(viewer, "menu.title"), Component.text(id));
    }

    @Override
    public String messageModule() {
        return CosmeticsModule.ID;
    }

    @Override
    public String infoKey() {
        return "editor.entry-info";
    }

    @Override
    public String helpTopic() {
        return "tags";
    }

    private Optional<Cosmetic> current() {
        return module.registry().get(kind, id);
    }

    @Override
    protected void build() {
        frame();
        Optional<Cosmetic> found = current();
        if (found.isEmpty()) {
            set(22, service.item(Material.COBWEB, module.text(viewer, "editor.gone.name"), module.lines(viewer, "editor.gone.lore")));
            return;
        }
        Cosmetic c = found.get();
        set(SLOT_PREVIEW, service.item(kind == Cosmetics.Kind.TITLE ? Material.NAME_TAG : Material.ARMOR_STAND,
                module.service().display(viewer, c), module.lines(viewer, "editor.preview-lore", Msg.p("id", c.id()))));

        set(SLOT_DISPLAY, service.item(Material.OAK_SIGN, module.text(viewer, "editor.display.name"),
                module.lines(viewer, "editor.display.lore", Msg.p("raw", c.display()), Msg.c("shown", module.service().display(viewer, c)))),
                click -> service.prompt(viewer, CosmeticsModule.ID, "editor.prompt-display", 200,
                        input -> saveAndReopen(c.withDisplay(input.strip()), "display"), () -> service.open(viewer, this)));

        Component rarity = module.text(viewer, "rarity." + c.rarity().name().toLowerCase(Locale.ROOT));
        set(SLOT_RARITY, service.item(Material.AMETHYST_SHARD, module.text(viewer, "editor.rarity.name", Msg.c("rarity", rarity)),
                module.lines(viewer, "editor.rarity.lore", Msg.c("rarity", rarity))), click -> pickRarity(c));

        set(SLOT_SOURCE, service.item(Material.WRITABLE_BOOK, module.text(viewer, "editor.source-item.name"),
                module.lines(viewer, "editor.source-item.lore", Msg.c("source",
                        c.source().isBlank() ? module.text(viewer, "editor.none") : module.core().messages().parse(viewer, c.source())))),
                click -> service.prompt(viewer, CosmeticsModule.ID, "editor.prompt-source", 200,
                        input -> saveAndReopen(c.withSource(input.strip().equalsIgnoreCase("none") ? "" : input.strip()), "source"),
                        () -> service.open(viewer, this)));

        if (kind == Cosmetics.Kind.TITLE) {
            set(SLOT_SECRET, service.toggle(viewer, CosmeticsModule.ID, "editor.secret", c.secret(),
                    value -> module.save(viewer, c.withSecret(value), "secret")));
        }
        set(SLOT_SEASON, service.toggle(viewer, CosmeticsModule.ID, "editor.season", c.seasonPersistent(),
                value -> module.save(viewer, c.withSeasonPersistent(value), "season-persistent")));

        if (kind == Cosmetics.Kind.TAG && viewer.hasPermission(CosmeticsModule.PERM_ITEM)) {
            set(SLOT_ITEM, service.item(Material.NAME_TAG, module.text(viewer, "editor.give-item.name"),
                    module.lines(viewer, "editor.give-item.lore")), click -> module.giveTagItems(viewer, viewer, c.id(), 1));
        }

        set(SLOT_DELETE, service.item(Material.LAVA_BUCKET, module.text(viewer, "editor.delete.name"),
                module.lines(viewer, "editor.delete.lore")), click -> openChild(new ConfirmMenu(
                p -> module.text(p, "editor.delete.crumb"),
                service.item(Material.LAVA_BUCKET, module.service().display(viewer, c), List.of()),
                p -> module.lines(p, "editor.delete.details"),
                p -> {
                    module.delete(p, kind, c.id());
                    back();
                },
                p -> { })));
    }

    /** A pick list of the rarities (GUI-4b/G9: no forward-only cycling); the pick is saved and logged. */
    private void pickRarity(Cosmetic c) {
        List<PickListMenu.Option<Rarity>> options = new ArrayList<>();
        for (Rarity r : Rarity.values()) {
            options.add(PickListMenu.Option.of(r, Material.AMETHYST_SHARD, module.text(viewer, "rarity." + r.name().toLowerCase(Locale.ROOT)),
                    r == c.rarity()));
        }
        openChild(new PickListMenu<>(List.of(Component.text(id), module.text(viewer, "editor.rarity.crumb")), options,
                r -> module.save(viewer, c.withRarity(r), "rarity")));
    }

    private void saveAndReopen(Cosmetic updated, String field) {
        module.save(viewer, updated, field);
        service.open(viewer, this);
    }
}
