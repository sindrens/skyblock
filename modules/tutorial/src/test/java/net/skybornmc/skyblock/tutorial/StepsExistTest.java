package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 4 Nov C §4 ("always up to date"): every tutorial step points at something that exists in the code base: an NPC
 * role with a name (messages/npcs.yml), a menu class ({@code <Name>Menu} → its {@link Menu#idOf} id), an audit action
 * another module writes, and a command some module registers. Removing a feature fails the build here, so the tour
 * never sends a new player to something that is gone (like the Depths).
 */
class StepsExistTest {

    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final Pattern MENU_CLASS = Pattern.compile("class (\\w+Menu)\\b");
    private static final Pattern COMMAND = Pattern.compile("CommandSpec\\.command\\(\"([a-z0-9_-]+)\"\\)");
    private static final Pattern ALIASES = Pattern.compile("\\.aliases\\(([^)]*)\\)");

    private static String sources() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(ROOT.resolve("modules"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java") && p.toString().contains("src/main/java")).toList()) {
                all.append(Files.readString(f)).append('\n');
            }
        }
        return all.toString();
    }

    @Test
    void everyStepPointsAtSomethingThatExists() throws IOException {
        Catalog catalog = CatalogTest.bundled(new ArrayList<>());
        String code = sources();
        YamlConfiguration npcs = YamlConfiguration.loadConfiguration(ROOT.resolve("modules/npcs/src/main/resources/messages/npcs.yml").toFile());
        Set<String> menus = new HashSet<>();
        Matcher mm = MENU_CLASS.matcher(code);
        while (mm.find()) {
            menus.add(Menu.idOf(mm.group(1)));
        }
        Set<String> commands = new HashSet<>();
        Matcher cm = COMMAND.matcher(code);
        while (cm.find()) {
            commands.add(cm.group(1));
        }
        Matcher am = ALIASES.matcher(code);
        while (am.find()) {
            for (String a : am.group(1).split(",")) {
                commands.add(a.strip().replace("\"", ""));
            }
        }
        List<String> missing = new ArrayList<>();
        for (Catalog.Chapter c : catalog.chapters()) {
            for (Catalog.Step s : c.steps()) {
                switch (s.type()) {
                    case NPC -> {
                        if (!npcs.isString("roles." + s.target() + ".name")) {
                            missing.add(s.key() + ": NPC role " + s.target());
                        }
                    }
                    case MENU -> {
                        for (String id : s.target().split("\\|")) {
                            if (!menus.contains(id.strip())) {
                                missing.add(s.key() + ": menu " + id);
                            }
                        }
                    }
                    case AUDIT -> {
                        String[] parts = s.target().split(":", 3);
                        for (String action : parts[1].split("\\|")) {
                            if (!code.contains("\"" + action.strip() + "\"")) {
                                missing.add(s.key() + ": audit action " + action);
                            }
                        }
                    }
                    default -> { }
                }
                if (!s.command().isEmpty()) {
                    String root = s.command().split("\\s+", 2)[0];
                    if (!commands.contains(root)) {
                        missing.add(s.key() + ": command /" + root);
                    }
                }
            }
        }
        for (String role : catalog.idleRoles().keySet()) {
            if (!npcs.isString("roles." + role + ".name")) {
                missing.add("idle: NPC role " + role);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void bookBarsArePure() {
        assertEquals("■■□□", TutorialBook.squares(2, 4));
        assertEquals("●●··", TutorialBook.dots(2, 4));
        assertEquals("□□□", TutorialBook.squares(0, 3));
        assertEquals("▰▰▰▰▰▱▱▱▱▱", TutorialBook.bar(50));
        assertEquals("▰▰▰▰▰▰▰▰▰▰", TutorialBook.bar(120));
    }
}
