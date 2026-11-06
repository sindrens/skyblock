package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.icon.IconCatalog;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ICONS-FINAL section 6 (IconKeysUsedTest): every icon key the code names ({@code "nav/back"}, {@code "feature/home"})
 * exists in resourcepack/icons.json, as a custom icon or a plain vanilla key. A typo would show a barrier or the wrong item.
 * Only strings whose prefix is an icon group of the catalog are checked (other "a/b" strings are paths, ids and so on).
 */
class IconKeysUsedTest {

    private static final Pattern KEY = Pattern.compile("\"([a-z_]+/[a-z0-9_]+)\"");

    @Test
    void everyIconKeyInTheCodeIsInTheCatalog() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        IconCatalog catalog = IconCatalog.load(Files.readString(root.resolve("resourcepack/icons.json")));
        Set<String> groups = new HashSet<>();
        catalog.all().forEach(i -> groups.add(i.key().substring(0, i.key().indexOf('/'))));
        catalog.vanillaKeys().keySet().forEach(k -> groups.add(k.substring(0, k.indexOf('/'))));
        assertTrue(groups.contains("nav"), "catalog loaded: " + groups);
        List<String> missing = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        dirs.add(root.resolve("common/src/main/java"));
        try (Stream<Path> modules = Files.list(root.resolve("modules"))) {
            modules.map(m -> m.resolve("src/main/java")).filter(Files::isDirectory).forEach(dirs::add);
        }
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = KEY.matcher(Files.readString(f));
                    while (m.find()) {
                        String key = m.group(1);
                        // "item/crate_key_" + id: a prefix that the code completes, not a key of its own
                        if (key.endsWith("_")) {
                            continue;
                        }
                        if (groups.contains(key.substring(0, key.indexOf('/'))) && !catalog.has(key)) {
                            missing.add(root.relativize(f) + ": " + key);
                        }
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "icon keys missing from resourcepack/icons.json:\n" + String.join("\n", missing));
    }
}
