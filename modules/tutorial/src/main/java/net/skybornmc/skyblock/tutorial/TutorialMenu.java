package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The tutorial as a chest menu: Bedrock's {@code /tutorial} (books can't be clicked there) and the pages of Rowan's
 * {@code /start} (owner 4 Nov 2026: "/start has pages and shows what's next"). Row 1: the chapter and its reward. Row 2:
 * its steps (✔ / ➤ / •, a click takes you there). Row 4: Island, Farm and Mine plus the book ({@code /start}) or every
 * chapter ({@code /tutorial}). Plain vanilla items, Bedrock-safe.
 */
final class TutorialMenu extends Menu {

    static final int SLOT_CHAPTER = 4;
    static final int[] STEP_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    static final int[] CHAPTER_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    static final int SLOT_ISLAND = 29;
    static final int SLOT_FARM = 31;
    static final int SLOT_MINE = 33;
    static final int SLOT_BOOK = 40;

    private final TutorialModule m;
    private final boolean start;

    TutorialMenu(TutorialModule m, boolean start) {
        this.m = m;
        this.start = start;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, m.msg().component(viewer, start ? "menu.start-title" : "menu.title",
                Msg.mm("guide", guideName())));
    }

    @Override
    public String messageModule() {
        return TutorialModule.ID;
    }

    @Override
    public String menuId() {
        return start ? "start" : "tutorial";
    }

    private String guideName() {
        String raw = m.core().messages().raw("npcs", "roles.guide.name");
        return raw == null || raw.isBlank() ? "Rowan" : raw;
    }

    @Override
    protected void build() {
        navBar();
        Player p = viewer;
        Optional<Progress> pr = m.progress(p);
        if (pr.isEmpty()) {
            setLoading(SLOT_CHAPTER);
            return;
        }
        Catalog catalog = m.catalog();
        Predicate<Catalog.Step> available = m.availability();
        Optional<Catalog.Chapter> chapter = pr.get().current(catalog);
        if (chapter.isEmpty()) {
            set(SLOT_CHAPTER, service.item(Material.NETHER_STAR, line(pr.get().skipped() ? "menu.skipped.name" : "menu.finished.name"),
                    List.of(line(pr.get().skipped() ? "menu.skipped.lore" : "menu.finished.lore"))));
        } else {
            Catalog.Chapter c = chapter.get();
            int done = pr.get().doneCount(c, available);
            List<Component> lore = new ArrayList<>();
            lore.add(line("menu.chapter.subtitle", Msg.p("number", catalog.index(c) + 1), Msg.p("total", catalog.chapters().size())));
            lore.add(Component.empty());
            lore.add(line("menu.chapter.progress", Msg.p("bar", TutorialBook.dots(done, c.steps().size())),
                    Msg.p("done", done), Msg.p("total", c.steps().size())));
            lore.add(Component.empty());
            lore.add(line("menu.chapter.reward-head"));
            lore.add(line("menu.chapter.reward", Msg.p("rewards", m.rewardText(c))));
            set(SLOT_CHAPTER, service.item(material(c.icon(), Material.BOOK), line("menu.chapter.name", Msg.p("chapter", c.name())), lore));
            Optional<Catalog.Step> next = pr.get().next(catalog, available);
            int i = 0;
            for (Catalog.Step s : c.steps()) {
                if (i >= STEP_SLOTS.length) {
                    break;
                }
                stepButton(STEP_SLOTS[i++], pr.get(), s, next.map(n -> n.key().equals(s.key())).orElse(false), available);
            }
        }
        if (start) {
            place(SLOT_ISLAND, Material.GRASS_BLOCK, "island");
            place(SLOT_FARM, Material.WHEAT, "farm");
            place(SLOT_MINE, Material.IRON_PICKAXE, "mine");
            set(SLOT_BOOK, service.item(Material.WRITABLE_BOOK, line("menu.book.name"),
                    List.of(line("menu.book.lore"), Component.empty(), line("menu.click", Msg.p("action", plain("menu.book.action"))))),
                    click -> {
                        click.player().closeInventory();
                        m.open(click.player());
                    });
        } else {
            chapters(pr.get(), catalog);
        }
    }

    private void stepButton(int slot, Progress pr, Catalog.Step s, boolean isNext, Predicate<Catalog.Step> available) {
        boolean open = pr.open(s, available);
        String state = !open ? "done" : isNext ? "next" : "open";
        List<Component> lore = new ArrayList<>();
        lore.add(line("menu.step.state-" + state));
        if (!s.hint().isEmpty()) {
            lore.add(Component.empty());
            lore.add(line("menu.step.hint", Msg.p("hint", s.hint())));
        }
        if (open && s.amount() > 1) {
            lore.add(line("menu.step.count", Msg.p("count", pr.counter(s)), Msg.p("amount", s.amount())));
        }
        String command = m.commandOf(s);
        boolean clickable = open && !command.isEmpty() && m.commandExists(command);
        if (clickable) {
            lore.add(Component.empty());
            lore.add(line("menu.click", Msg.p("action", plain("menu.step.action"))));
        }
        Material icon = !open ? Material.LIME_DYE : material(s.icon(), Material.PAPER);
        Component name = line("menu.step.name-" + state, Msg.p("step", s.text()));
        if (clickable) {
            set(slot, service.item(icon, name, lore), click -> {
                click.player().closeInventory();
                click.player().performCommand(command);
            });
        } else {
            set(slot, service.item(icon, name, lore));
        }
    }

    private void place(int slot, Material icon, String id) {
        Optional<Places.Place> place = m.places().get(id);
        if (place.isEmpty() || !m.commandExists(place.get().command())) {
            return;
        }
        String command = place.get().command();
        set(slot, service.item(icon, line("menu.place." + id + ".name"),
                List.of(line("menu.place." + id + ".lore"), Component.empty(), line("menu.click", Msg.p("action", plain("menu.place.action"))))),
                click -> {
                    click.player().closeInventory();
                    click.player().performCommand(command);
                });
    }

    private void chapters(Progress pr, Catalog catalog) {
        int i = 0;
        boolean blocked = false;
        for (Catalog.Chapter c : catalog.chapters()) {
            if (i >= CHAPTER_SLOTS.length) {
                break;
            }
            String state;
            if (pr.chapterDone(c)) {
                state = "done";
            } else if (!blocked) {
                state = "current";
                blocked = true;
            } else {
                state = "locked";
            }
            Material icon = state.equals("locked") ? Material.GRAY_DYE : material(c.icon(), Material.BOOK);
            set(CHAPTER_SLOTS[i++], service.item(icon, line("menu.chapters.name-" + state, Msg.p("chapter", c.name())),
                    List.of(line("menu.chapters.state-" + state), Component.empty(), line("menu.chapter.reward-head"),
                            line("menu.chapter.reward", Msg.p("rewards", m.rewardText(c))))));
        }
    }

    private Component line(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        return m.msg().component(viewer, key, tags);
    }

    private String plain(String key) {
        return TutorialModule.plain(line(key));
    }

    private static Material material(String id, Material fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(id.strip().toUpperCase(Locale.ROOT));
        return mat == null || !mat.isItem() ? fallback : mat;
    }
}
