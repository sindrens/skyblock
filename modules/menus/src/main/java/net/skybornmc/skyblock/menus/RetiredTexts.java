package net.skybornmc.skyblock.menus;

import java.util.Map;

/**
 * Old bundled texts of {@code messages/navigation.yml} that became wrong (Messages#retire): a live text that still equals
 * its old text word for word is upgraded on start; an owner-edited text is kept.
 */
final class RetiredTexts {

    private RetiredTexts() {
    }

    static Map<String, String> all() {
        return Map.of(
                // GUI-4c: the hub Top button opens the Top screen (islands, this week, richest players, top skills).
                "hub.buttons.top.lore", "<c:text>The best islands:\n<c:text>level and value.",
                // GUI-4c: Bedrock players read help pages in a popup, there is no hover on touch screens.
                "help.topic-info", "<c:text>Hover the pages to read them.\n<c:text>Take me there brings you to the feature.");
    }
}
