package net.skybornmc.skyblock.common.pack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The generated pack-ids.yml parses and is consistent with the zips next to it. */
class PackIdsTest {

    static final Path DIR = Path.of("../plugin/src/main/resources/pack");
    static PackIds ids;

    @BeforeAll
    static void load() throws IOException {
        try (FileReader r = new FileReader(DIR.resolve("pack-ids.yml").toFile(), StandardCharsets.UTF_8)) {
            ids = PackIds.parse(r);
        }
    }

    @Test
    void zipsExistAndNamesCarryTheHash() {
        assertEquals(2, ids.zips().size());
        ids.zips().forEach((variant, z) -> {
            assertTrue(Files.isRegularFile(DIR.resolve(z.file())), z.file());
            assertEquals(40, z.sha1().length());
            assertTrue(z.file().startsWith("phoenix-java-" + ids.version() + "-"), z.file());
            assertTrue(z.file().endsWith(z.sha1().substring(0, 8) + ".zip"), z.file());
        });
    }

    @Test
    void everyMenuClassHasAKnownBackground() {
        assertTrue(ids.menu().classes().size() >= 150);
        ids.menu().classes().forEach((cls, bg) -> assertTrue(ids.menu().backgrounds().containsKey(bg), cls + " -> " + bg));
        ids.menu().backgrounds().values().forEach(b -> {
            assertEquals(1, b.glyph().length());
            assertTrue(PrivateUse.isPrivateUse(b.glyph().charAt(0)));
        });
    }

    @Test
    void badgesHaveDefaultAndStaffAndCaps() {
        for (String g : new String[]{"default", "immortal", "owner", "helper"}) {
            PackIds.Badge b = ids.badges().get(g);
            assertNotNull(b, g);
            assertFalse(b.capLeft().isEmpty());
            assertFalse(b.capRight().isEmpty());
        }
        assertEquals(3, ids.badges().get("immortal").colors().size());
        assertEquals("default", ids.badge("no-such-group").group());
        assertEquals("", ids.letterGap());
    }

    @Test
    void spacesAddUp() {
        assertEquals("", ids.space(-1));
        assertEquals("", ids.space(-9));
        assertEquals("", ids.space(8));
        assertEquals("", ids.space(0));
        assertEquals(4, ids.space(-177).length()); // 128 + 32 + 16 + 1
    }

    @Test
    void cratesHaveSplitModelsAndIconsListKeys() {
        assertEquals(7, ids.crates().size());
        ids.crates().forEach((id, c) -> {
            assertEquals("crate/block/" + id + "_lid", c.lid());
            assertTrue(ids.icons().contains("crate/key/" + id), id);
        });
        assertTrue(ids.icons().contains("menu/blank"));
        assertTrue(ids.sounds().contains("phoenix:ui.click"));
    }
}
