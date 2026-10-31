package net.skybornmc.skyblock.collection;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /collection} (GUI-1 layout): the pages are tabs in row 0 that switch in place; the entries of the page fill the
 * content area. A found entry shows its item with a glint and "✔ Name"; a locked one is the gray locked look named
 * "??? · how to get it" (the key fact is in the name, so Bedrock players see it without the lore; a tap repeats it in
 * the action bar). 46 = the whole log (found/total, what the rewards are), 47 = this page's milestones. Plain clicks only.
 */
final class CollectionMenu extends PagedMenu<Catalog.Entry> {

    static final int SLOT_SUMMARY = 46;
    static final int SLOT_MILESTONES = 47;

    private final CollectionModule m;
    private String page;

    CollectionMenu(CollectionModule module) {
        this.m = module;
    }

    /** Row-0 slots of {@code n} tabs: up to 4 spaced out around slot 4, more side by side (pure, unit tested). */
    static int[] tabSlots(int n) {
        int[] out = new int[Math.max(0, Math.min(9, n))];
        for (int i = 0; i < out.length; i++) {
            out[i] = out.length <= 4 ? 4 + 2 * i - (out.length - 1) : 4 - out.length / 2 + i;
        }
        return out;
    }

    private Catalog.Page current() {
        List<Catalog.Page> pages = m.catalog().visible();
        if (pages.isEmpty()) {
            return null;
        }
        for (Catalog.Page p : pages) {
            if (p.id().equals(page)) {
                return p;
            }
        }
        page = pages.getFirst().id();
        return pages.getFirst();
    }

    private ProfileLog log() {
        return m.recorder().log(m.profiles().profileKey(viewer));
    }

    private Set<String> found() {
        ProfileLog log = log();
        return log == null ? Set.of() : log.foundIds();
    }

    @Override
    protected List<Catalog.Entry> entries() {
        ProfileLog log = log();
        Catalog.Page p = current();
        return p == null || log == null || !log.loaded() ? List.of() : p.entries();
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, CollectionModule.ID, "menu.title"));
    }

    @Override
    public String messageModule() {
        return CollectionModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info";
    }

    @Override
    public String helpTopic() {
        return "collection";
    }

    @Override
    protected String emptyKey() {
        ProfileLog log = log();
        return log != null && !log.loaded() ? "menu.loading" : "menu.empty";
    }

    @Override
    protected void decorate() {
        List<Catalog.Page> pages = m.catalog().visible();
        Catalog.Page selected = current();
        Set<String> found = found();
        int[] slots = tabSlots(pages.size());
        for (int i = 0; i < slots.length; i++) {
            Catalog.Page p = pages.get(i);
            boolean isSelected = selected != null && p.id().equals(selected.id());
            int n = Catalog.found(p, found);
            int total = p.entries().size();
            set(slots[i], service.tab(viewer, new ItemStack(pageIcon(p.kind())),
                    service.text(viewer, CollectionModule.ID, "menu.tab.name", Msg.c("page", m.pageName(viewer, p.id())),
                            Msg.number("found", n), Msg.number("total", total)),
                    service.lines(viewer, CollectionModule.ID, "menu.tab.lore", Msg.number("found", n), Msg.number("total", total),
                            Msg.number("percent", percent(n, total))),
                    isSelected, () -> {
                        page = p.id();
                        page(0);
                    }));
        }
        set(SLOT_SUMMARY, summary(found, selected == null ? Catalog.PageKind.MINE.id() : selected.id()));
        if (selected != null) {
            set(SLOT_MILESTONES, milestones(selected, found));
        }
    }

    private static Material pageIcon(Catalog.PageKind kind) {
        return switch (kind) {
            case MINE -> Material.IRON_PICKAXE;
            case FOREST -> Material.IRON_AXE;
            case COMBAT -> Material.IRON_SWORD;
            case CRATES -> Material.TRIPWIRE_HOOK;
        };
    }

    static int percent(int found, int total) {
        return total <= 0 ? 0 : (int) (found * 100L / total);
    }

    private ItemStack summary(Set<String> found, String shownPage) {
        Catalog catalog = m.catalog();
        int n = catalog.found(found);
        int total = catalog.total();
        List<Component> lore = new ArrayList<>(service.lines(viewer, CollectionModule.ID, "menu.summary.lore",
                Msg.p("profile", m.profiles().activeName(viewer))));
        lore.addAll(service.lines(viewer, CollectionModule.ID, "menu.summary.rewards",
                Msg.p("points", m.settings.levelPoints == Math.rint(m.settings.levelPoints) ? String.valueOf((long) m.settings.levelPoints)
                        : String.valueOf(m.settings.levelPoints)),
                Msg.c("page-rewards", m.notices().rewards(viewer, Milestones.id(shownPage, Milestones.FULL))),
                Msg.c("log-rewards", m.notices().rewards(viewer, Milestones.ALL))));
        ProfileLog log = log();
        if (log != null && log.hasMilestone(Milestones.ALL)) {
            lore.addAll(service.lines(viewer, CollectionModule.ID, "menu.summary.complete"));
        }
        return service.item(Material.BOOKSHELF, service.text(viewer, CollectionModule.ID, "menu.summary.name", Msg.number("found", n),
                Msg.number("total", total), Msg.number("percent", percent(n, total))), lore);
    }

    private ItemStack milestones(Catalog.Page p, Set<String> found) {
        ProfileLog log = log();
        int reached = 0;
        List<Component> lore = new ArrayList<>();
        for (int pct : m.percents()) {
            boolean done = log != null && log.hasMilestone(Milestones.id(p.id(), pct));
            reached += done ? 1 : 0;
            lore.add(m.msg.component(viewer, done ? "menu.milestones.reached" : "menu.milestones.open", Msg.number("percent", pct),
                    Msg.number("need", (int) Math.ceil(pct * p.entries().size() / 100.0))));
        }
        lore.addAll(service.lines(viewer, CollectionModule.ID, "menu.milestones.lore",
                Msg.c("rewards", m.notices().rewards(viewer, Milestones.id(p.id(), Milestones.FULL)))));
        return service.item(Material.NETHER_STAR, service.text(viewer, CollectionModule.ID, "menu.milestones.name",
                Msg.c("page", m.pageName(viewer, p.id())), Msg.number("reached", reached), Msg.number("total", m.percents().size())), lore);
    }

    @Override
    protected MenuButton entry(Catalog.Entry e) {
        ProfileLog log = log();
        ProfileLog.Found f = log == null ? null : log.found(e.id());
        Component hint = m.hint(viewer, e);
        if (f == null) {
            return service.locked(viewer, service.text(viewer, CollectionModule.ID, "menu.entry.locked", Msg.c("hint", hint)), hint);
        }
        Component name = m.entryName(viewer, e);
        ItemStack item = service.item(new ItemStack(m.entryIcon(e)), service.text(viewer, CollectionModule.ID, "menu.entry.found.name",
                Msg.c("item", name)), service.lines(viewer, CollectionModule.ID, "menu.entry.found.lore", Msg.c("hint", hint),
                Msg.p("ago", Format.duration(java.time.Duration.ofMillis(Math.max(0, System.currentTimeMillis() - f.at())))), Msg.c("source", source(f.source()))));
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return MenuButton.display(item);
    }

    private Component source(String source) {
        String key = "sources." + (source == null ? "other" : source);
        return m.msg.has(key) ? m.msg.component(viewer, key) : m.msg.component(viewer, "sources.other");
    }
}
