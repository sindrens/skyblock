package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.frame.FrameMetrics;
import net.skybornmc.skyblock.common.frame.PlaqueFit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI-FONT section 10 (PlaqueTitleAuditTest): every plaque noun a menu asks for ({@code plaqueKey()}: framework
 * {@code plaque.*}, the shop's {@code menu/buy/sell.short} and {@code categories.<tab>.short}) is a clean all-caps noun the
 * cartoon lettering can draw and fits on a plaque. A broken one would leave Java players with a blank plaque.
 */
class PlaqueTitleAuditTest {

    private static final File ROOT = new File("..").getAbsoluteFile();

    private static YamlConfiguration yaml(String path) {
        return YamlConfiguration.loadConfiguration(new File(ROOT, path));
    }

    @Test
    void everyPlaqueNounIsCleanAndFits() {
        List<String> problems = new ArrayList<>();
        FrameMetrics metrics = FrameMetrics.bundled(getClass().getClassLoader(), problems::add).orElseThrow();
        Map<String, String> nouns = new LinkedHashMap<>();
        YamlConfiguration menus = yaml("plugin/src/main/resources/messages/menus.yml");
        ConfigurationSection plaque = menus.getConfigurationSection("plaque");
        assertTrue(plaque != null && !plaque.getKeys(false).isEmpty(), "menus.yml plaque section");
        plaque.getKeys(false).forEach(k -> nouns.put("menus.yml plaque." + k, plaque.getString(k)));
        YamlConfiguration shop = yaml("modules/shop/src/main/resources/messages/shop.yml");
        for (String key : List.of("menu.short", "buy.short", "sell.short")) {
            nouns.put("shop.yml " + key, shop.getString(key));
        }
        ConfigurationSection cats = shop.getConfigurationSection("categories");
        assertTrue(cats != null, "shop.yml categories");
        cats.getKeys(false).forEach(k -> nouns.put("shop.yml categories." + k + ".short", cats.getString(k + ".short")));
        for (Map.Entry<String, String> e : nouns.entrySet()) {
            String raw = e.getValue();
            String clean = PlaqueFit.sanitize(raw);
            if (raw == null || clean.isEmpty()) {
                problems.add(e.getKey() + ": '" + raw + "' is not a plaque noun (A-Z, 0-9, " + PlaqueFit.SYMBOLS + ", at most 16 letters)");
            } else if (!clean.equals(raw)) {
                problems.add(e.getKey() + ": write it as '" + clean + "'");
            } else if (PlaqueFit.fit(metrics, clean).isEmpty()) {
                problems.add(e.getKey() + ": '" + raw + "' is wider than the large plaque");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
