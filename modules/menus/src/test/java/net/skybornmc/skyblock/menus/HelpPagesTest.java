package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** GUI-4: no help topic has more pages than the topic screen shows (they used to be cut silently after 7). */
class HelpPagesTest {

    @Test
    void everyPageFitsOnTheTopicScreen() {
        List<String> tooLong = new ArrayList<>();
        for (String file : List.of("src/main/resources/help.yml", "../../server/plugins/PhoenixSkyblock/help.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(file));
            var topics = yaml.getConfigurationSection("topics");
            if (topics == null) {
                continue;
            }
            for (String topic : topics.getKeys(false)) {
                int pages = yaml.getMapList("topics." + topic + ".pages").size();
                if (pages > HelpTopicMenu.MAX_PAGES) {
                    tooLong.add(file + " " + topic + ": " + pages);
                }
            }
        }
        assertEquals(List.of(), tooLong);
    }
}
