package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The quest book of {@code /tutorial} (Java; Bedrock gets {@link TutorialMenu}): the current chapter with its steps
 * (✔ done, ➤ next with a hint, • open), clickable steps that take the player to the step's place, then the list of all
 * chapters with an overall progress bar. Texts: messages/tutorial.yml {@code book.*}.
 */
final class TutorialBook {

    /** Steps on the first page (under the chapter header) and on the pages after it. */
    static final int STEPS_FIRST_PAGE = 3;
    static final int STEPS_PER_PAGE = 5;
    /** Chapters per page of the "All chapters" list. */
    static final int CHAPTERS_PER_PAGE = 9;

    private TutorialBook() {
    }

    static void open(TutorialModule m, Player p) {
        Optional<Progress> pr = m.progress(p);
        if (pr.isEmpty()) {
            m.msg().send(p, "loading");
            return;
        }
        p.openBook(Book.book(m.msg().component(p, "book.title"), m.msg().component(p, "book.author"), pages(m, p, pr.get())));
        m.showBar(p);
    }

    static List<Component> pages(TutorialModule m, Player p, Progress pr) {
        Catalog catalog = m.catalog();
        Predicate<Catalog.Step> available = m.availability();
        List<Component> pages = new ArrayList<>();
        Optional<Catalog.Chapter> current = pr.current(catalog);
        if (current.isPresent()) {
            Catalog.Chapter c = current.get();
            Optional<Catalog.Step> next = pr.next(catalog, available);
            int done = pr.doneCount(c, available);
            Component head = m.msg().component(p, "book.chapter-head", Msg.p("chapter", c.name()),
                    Msg.p("number", catalog.index(c) + 1), Msg.c("state", m.msg().component(p,
                            done == 0 ? "book.state-new" : "book.state-started")),
                    Msg.p("bar", squares(done, c.steps().size())), Msg.p("done", done), Msg.p("total", c.steps().size()),
                    Msg.p("rewards", m.rewardText(c)));
            List<Catalog.Step> steps = c.steps();
            Component page = head;
            int onPage = 0;
            int limit = STEPS_FIRST_PAGE;
            for (Catalog.Step s : steps) {
                if (onPage >= limit) {
                    pages.add(page);
                    page = Component.empty();
                    onPage = 0;
                    limit = STEPS_PER_PAGE;
                }
                page = page.append(step(m, p, pr, s, next.map(n -> n.key().equals(s.key())).orElse(false), available));
                onPage++;
            }
            pages.add(page.append(m.msg().component(p, "book.see-all")));
        } else {
            pages.add(m.msg().component(p, pr.skipped() ? "book.skipped" : "book.finished"));
        }
        // All chapters
        int percent = (int) Math.round(pr.overall(catalog, available) * 100);
        Component list = m.msg().component(p, "book.all-head", Msg.p("bar", bar(percent)), Msg.p("percent", percent));
        int onPage = 0;
        boolean blocked = false;
        Catalog.Chapter previous = null;
        for (Catalog.Chapter c : catalog.chapters()) {
            if (onPage >= CHAPTERS_PER_PAGE) {
                pages.add(list);
                list = Component.empty();
                onPage = 0;
            }
            String key;
            if (pr.chapterDone(c)) {
                key = "book.chapter-done";
            } else if (!blocked) {
                key = "book.chapter-current";
                blocked = true;
            } else {
                key = "book.chapter-locked";
            }
            Component line = m.msg().component(p, key, Msg.p("chapter", c.name()));
            if (key.equals("book.chapter-locked") && previous != null) {
                line = line.hoverEvent(HoverEvent.showText(m.msg().component(p, "book.locked-hover", Msg.p("chapter", previous.name()))));
            } else {
                line = line.hoverEvent(HoverEvent.showText(m.msg().component(p, "book.reward-hover", Msg.p("rewards", m.rewardText(c)))));
            }
            list = list.append(line);
            previous = c;
            onPage++;
        }
        pages.add(list.append(m.msg().component(p, "book.footer")));
        return pages;
    }

    private static Component step(TutorialModule m, Player p, Progress pr, Catalog.Step s, boolean isNext,
                                  Predicate<Catalog.Step> available) {
        String key;
        if (!pr.open(s, available)) {
            key = "book.step-done";
        } else if (isNext) {
            key = "book.step-next";
        } else {
            key = "book.step-open";
        }
        String count = s.amount() > 1 && pr.open(s, available) ? pr.counter(s) + "/" + s.amount() : "";
        Component line = m.msg().component(p, key, Msg.p("step", s.text()), Msg.p("hint", s.hint()), Msg.p("count", count));
        if (pr.open(s, available)) {
            String command = m.commandOf(s);
            if (!command.isEmpty() && m.commandExists(command)) {
                line = line.clickEvent(ClickEvent.runCommand("/" + command))
                        .hoverEvent(HoverEvent.showText(m.msg().component(p, "book.step-hover", Msg.p("command", "/" + command))));
            }
        }
        return line;
    }

    /** {@code ■■□□} for 2 of 4 done (max 10 squares). Pure. */
    static String squares(int done, int total) {
        int n = Math.min(10, Math.max(0, total));
        int d = total <= 0 ? 0 : (int) Math.round((double) Math.min(done, total) / total * n);
        return "■".repeat(d) + "□".repeat(n - d);
    }

    /** {@code ●●··} for 2 of 4 done (Bedrock-safe symbols only, for the chest menu). Pure. */
    static String dots(int done, int total) {
        return squares(done, total).replace('■', '●').replace('□', '·');
    }

    /** A 10-part bar for a percentage. Pure. */
    static String bar(int percent) {
        int d = Math.max(0, Math.min(10, Math.round(percent / 10f)));
        return "▰".repeat(d) + "▱".repeat(10 - d);
    }
}
