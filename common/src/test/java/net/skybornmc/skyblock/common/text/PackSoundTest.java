package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RP6: pack viewers hear phoenix:ui.* sounds, everyone else the vanilla ones. */
class PackSoundTest {

    private static Sound s(String id) {
        return Sound.sound(Key.key(id), Sound.Source.MASTER, 1f, 1f);
    }

    @Test
    void packViewerGetsPackSoundOthersVanilla() {
        Map<String, Sound> normal = Map.of("click", s("minecraft:ui.button.click"), "money", s("minecraft:block.amethyst_block.chime"));
        Map<String, Sound> pack = Map.of("click", s("phoenix:ui.click"));
        assertEquals("phoenix:ui.click", Sounds.pick(normal, pack, "click", true).name().asString());
        assertEquals("minecraft:ui.button.click", Sounds.pick(normal, pack, "click", false).name().asString());
        assertEquals("minecraft:block.amethyst_block.chime", Sounds.pick(normal, pack, "money", true).name().asString(), "no pack variant: vanilla");
        assertNull(Sounds.pick(normal, pack, "nope", true));
    }

    @Test
    void soundsYmlPackSectionIsParsedAndOldFilesStillWork() throws Exception {
        File f = File.createTempFile("sounds", ".yml");
        f.deleteOnExit();
        Files.writeString(f.toPath(), "click: \"minecraft:ui.button.click 0.4 1.2\"\npack-sound:\n  click: \"phoenix:ui.click 0.3 1.1\"\n");
        Sounds sounds = new Sounds(Logger.getAnonymousLogger());
        List<String> problems = sounds.load(f);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(List.of("click"), List.copyOf(sounds.keys()), "pack entries are not normal keys");

        Files.writeString(f.toPath(), "click: \"minecraft:ui.button.click 0.4 1.2\"\n");
        assertTrue(sounds.load(f).isEmpty(), "an old file without pack-sound still loads (built-in pack defaults)");
    }
}
