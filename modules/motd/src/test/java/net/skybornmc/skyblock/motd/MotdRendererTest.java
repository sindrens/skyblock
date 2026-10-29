package net.skybornmc.skyblock.motd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotdRendererTest {

    private final MotdRenderer renderer = new MotdRenderer();

    @Test
    void phaseCyclesFromMinusOneTowardsOne() {
        assertEquals(-1.0, MotdRenderer.phaseAt(0, 6000, 12), 1e-9);
        assertEquals(0.0, MotdRenderer.phaseAt(3000, 6000, 12), 1e-9);
        assertTrue(MotdRenderer.phaseAt(5999, 6000, 12) < 1.0);
        assertEquals(-1.0, MotdRenderer.phaseAt(6000, 6000, 12), 1e-9, "wraps around");
    }

    @Test
    void boldTextIsWider() {
        MiniMessage mm = MiniMessage.miniMessage();
        int normal = MotdRenderer.width(mm.deserialize("PHOENIX"));
        int bold = MotdRenderer.width(mm.deserialize("<bold>PHOENIX</bold>"));
        assertEquals(normal + 7, bold);
    }

    @Test
    void centeringPadsShortLinesOnly() {
        Component shortLine = renderer.line("<red>Hi", 0, true);
        String plain = PlainTextComponentSerializer.plainText().serialize(shortLine);
        assertTrue(plain.startsWith("    "), "short line is padded");
        assertEquals("Hi", plain.strip());

        Component uncentered = renderer.line("<red>Hi", 0, false);
        assertEquals("Hi", PlainTextComponentSerializer.plainText().serialize(uncentered));
    }

    @Test
    void bundledMotdsParseAndFitOnOneLine() {
        var stream = Objects.requireNonNull(getClass().getResourceAsStream("/motd.yml"));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        ConfigurationSection motds = config.getConfigurationSection("motds");
        assertEquals(4, motds.getKeys(false).size());
        for (String id : motds.getKeys(false)) {
            for (String key : new String[]{"line1", "line2"}) {
                Component line = renderer.line(motds.getString(id + "." + key), 0.5, false);
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                assertFalse(plain.contains("<") || plain.contains("{phase}"), id + "." + key + " has unparsed tags: " + plain);
                assertTrue(MotdRenderer.width(line) <= MotdRenderer.LINE_WIDTH,
                        id + "." + key + " is too wide (" + MotdRenderer.width(line) + "px): " + plain);
            }
        }
        for (String id : config.getStringList("active")) {
            assertTrue(motds.contains(id), "active MOTD exists: " + id);
        }
    }
}
