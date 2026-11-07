package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsTest {

    @Test
    void parsesEveryKind() {
        assertEquals(new Rewards.Reward(Rewards.Kind.CURRENCY, Currency.MONEY, null, 500), Rewards.parse("money 500").orElseThrow());
        assertEquals(Currency.MINING_SHARDS, Rewards.parse("mining_shards 100").orElseThrow().currency());
        assertEquals(new Rewards.Reward(Rewards.Kind.KEY, null, "ember", 1), Rewards.parse("key Ember").orElseThrow());
        assertEquals(new Rewards.Reward(Rewards.Kind.KEY, null, "cinder", 2), Rewards.parse("key cinder 2").orElseThrow());
        assertEquals(new Rewards.Reward(Rewards.Kind.TAG, null, "trailblazer", 1), Rewards.parse("tag trailblazer").orElseThrow());
    }

    @Test
    void refusesCreditsNegativesAndJunk() {
        for (String bad : List.of("credits 5", "credits_earned 5", "money -1", "money", "money 1 2", "key", "key ember 0",
                "gems 10", "", "fly 1")) {
            assertTrue(Rewards.parse(bad).isEmpty(), bad);
        }
        List<String> warnings = new ArrayList<>();
        assertEquals(1, Rewards.parse(List.of("money 1", "credits 9"), warnings::add).size());
        assertEquals(1, warnings.size());
    }

    @Test
    void aTitleIsAReward() {
        Rewards.Reward r = Rewards.parse("title Kindled").orElseThrow();
        assertEquals(Rewards.Kind.TITLE, r.kind());
        assertEquals("kindled", r.id());
        assertTrue(Rewards.parse("title").isEmpty());
    }

    @Test
    void newBundledChaptersAreAddedToALiveFileOnce() {
        org.bukkit.configuration.file.YamlConfiguration bundled = new org.bukkit.configuration.file.YamlConfiguration();
        bundled.set("chapters.welcome.name", "W");
        bundled.set("chapters.kindle.name", "K");
        org.bukkit.configuration.file.YamlConfiguration live = new org.bukkit.configuration.file.YamlConfiguration();
        live.set("chapters.welcome.name", "Staff name");
        assertEquals(java.util.List.of("kindle"), TutorialModule.addNewChapters(live, bundled));
        assertEquals("Staff name", live.getString("chapters.welcome.name"), "a live chapter is never changed");
        assertEquals("K", live.getString("chapters.kindle.name"));
        live.set("chapters.kindle", null); // staff removed it
        assertTrue(TutorialModule.addNewChapters(live, bundled).isEmpty(), "a removed chapter does not come back");
    }
}
