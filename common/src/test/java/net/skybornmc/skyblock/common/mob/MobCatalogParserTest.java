package net.skybornmc.skyblock.common.mob;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobCatalogParserTest {

    private static final Set<String> ITEMS = Set.of("flesh", "bone", "gem");

    private static MobCatalogParser.Result parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return MobCatalogParser.parse(y, ITEMS, Set.of("zombie", "slime")::contains, problems::add);
    }

    /** A mob written for M4.8 reads exactly as before: no new problems and no M8.1 stats. */
    @Test
    void oldMobsAreUnchanged() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalogParser.Result r = parse("""
                mobs:
                  walker: {type: zombie, health: 20, damage: 2, xp: 5, crystal-chance: 0.26, drops: [{item: flesh, min: 3, max: 5}]}
                """, problems);
        assertTrue(problems.isEmpty(), problems.toString());
        MobDef m = r.mobs().get("walker");
        assertSame(MobStats.NONE, m.stats());
        assertEquals(new MobDef("walker", "zombie", 20, 2, 0, 1, 0, 5, 0, 0.26, 1,
                List.of(new MobDef.Drop("flesh", 3, 5, 1.0)), false, false), m);
        assertTrue(r.dropTables().isEmpty());
        assertTrue(r.defaults().isEmpty());
    }

    @Test
    void readsEveryNewStat() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalogParser.Result r = parse("""
                drop-tables:
                  - id: bog
                    drops: [{item: gem, min: 1, max: 2, chance: 0.5}]
                ability-defaults:
                  slam: {cooldown: 6, chance: 0.9, radius: 5}
                mobs:
                  brute:
                    type: slime
                    armor: 25
                    knockback-resistance: 0.5
                    attack-cooldown: 20
                    follow-range: 24
                    elite-chance: 0.02
                    drop-table: bog
                    drops: [{item: flesh, amount: 2}]
                    abilities:
                      - {id: slam, damage: 2}
                      - {id: curse, cooldown: 12, chance: 0.3, params: {effect: wither, seconds: 4}}
                      - enrage
                """, problems);
        assertTrue(problems.isEmpty(), problems.toString());
        MobDef m = r.mobs().get("brute");
        MobStats s = m.stats();
        assertEquals(25, s.armor());
        assertEquals(0.5, s.knockbackResistance());
        assertEquals(20, s.attackCooldownTicks());
        assertEquals(24, s.followRange());
        assertEquals(0.02, s.eliteChance());
        assertEquals("bog", s.dropTable());
        assertEquals(List.of(new MobDef.Drop("gem", 1, 2, 0.5), new MobDef.Drop("flesh", 2, 2, 1.0)), m.drops(),
                "table rolls first, then the inline drops");
        assertEquals(3, s.abilities().size());

        AbilitySpec slam = r.resolve(s.ability(AbilityId.SLAM));
        assertEquals(6, slam.cooldown(), "from ability-defaults");
        assertEquals(120, slam.cooldownTicks());
        assertEquals(0.9, slam.rollChance());
        assertEquals(5, slam.num("radius"), "from ability-defaults");
        assertEquals(2, slam.num("damage"), "own value wins");
        assertEquals(1.0, slam.num("knockback"), "code default");

        AbilitySpec curse = r.resolve(s.ability(AbilityId.CURSE));
        assertEquals(240, curse.cooldownTicks());
        assertEquals(0.3, curse.rollChance());
        assertEquals("wither", curse.text("effect"));
        assertEquals(4, curse.num("seconds"));
        assertEquals(5, curse.num("radius"), "code default");

        AbilitySpec enrage = r.resolve(s.ability(AbilityId.ENRAGE));
        assertEquals(0, enrage.cooldownTicks(), "enrage is health-triggered");
        assertEquals(0.3, enrage.num("below"));
    }

    /** Armor above 60 is cut to 60; other out-of-range stats fall back to "as before". */
    @Test
    void statClamps() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalogParser.Result r = parse("""
                mobs:
                  tank: {type: zombie, armor: 95}
                  weak: {type: zombie, armor: -5, knockback-resistance: 2, attack-cooldown: 999, follow-range: 500, elite-chance: 1.5}
                  words: {type: zombie, armor: lots}
                """, problems);
        assertEquals(60, r.mobs().get("tank").stats().armor());
        MobStats weak = r.mobs().get("weak").stats();
        assertEquals(0, weak.armor());
        assertTrue(weak.vanillaKnockback());
        assertEquals(0, weak.attackCooldownTicks());
        assertEquals(0, weak.followRange());
        assertEquals(0, weak.eliteChance());
        assertEquals(0, r.mobs().get("words").stats().armor());
        // tank armor, weak: armor, knockback, cooldown, follow, elite; words armor
        assertEquals(7, problems.size(), String.join("\n", problems));
        // The record clamps too (code that builds stats directly).
        MobStats direct = new MobStats(500, 7, -3, -1, null, " ", 3);
        assertEquals(MobStats.MAX_ARMOR, direct.armor());
        assertEquals(1, direct.knockbackResistance());
        assertEquals(0, direct.attackCooldownTicks());
        assertNull(direct.dropTable());
        assertEquals(1, direct.eliteChance());
    }

    @Test
    void badAbilitiesAreReportedAndSkipped() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalogParser.Result r = parse("""
                mobs:
                  odd:
                    type: zombie
                    abilities:
                      - {id: fireball}
                      - {id: slam, cooldown: 0.1, chance: 3, radius: 99, colour: red}
                      - {id: slam}
                      - {id: ring, kind: ice}
                      - {id: curse, effect: poison, level: 2}
                      - 42
                      - pull
                      - leap
                      - charge
                      - volley
                """, problems);
        MobStats s = r.mobs().get("odd").stats();
        List<AbilityId> ids = s.abilities().stream().map(AbilitySpec::id).toList();
        assertEquals(List.of(AbilityId.SLAM, AbilityId.RING, AbilityId.CURSE, AbilityId.PULL, AbilityId.LEAP, AbilityId.CHARGE), ids,
                "unknown and duplicate ids skipped, at most six");
        AbilitySpec slam = s.ability(AbilityId.SLAM);
        assertEquals(AbilityId.SLAM.defaultCooldownSeconds(), slam.cooldown(), "bad cooldown: default");
        assertEquals(AbilityId.SLAM.defaultChance(), slam.rollChance(), "bad chance: default");
        assertEquals(4, slam.num("radius"), "out of range: default");
        assertEquals("fire", s.ability(AbilityId.RING).text("kind"));
        assertEquals("slowness", s.ability(AbilityId.CURSE).text("effect"));
        assertEquals(2, s.ability(AbilityId.CURSE).num("level"));
        // fireball, cooldown, chance, radius, colour, duplicate slam, ring kind, curse effect, 42, volley (7th)
        assertEquals(10, problems.size(), String.join("\n", problems));
    }

    @Test
    void badDropTablesAndDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalogParser.Result r = parse("""
                drop-tables:
                  - id: good
                    drops: [{item: bone, min: 1, max: 1}, {item: nope}, 7]
                  - id: good
                    drops: [{item: bone}]
                  - id: Bad Id
                  - just a string
                  - id: empty
                ability-defaults:
                  slam: {chance: 2}
                  laser: {cooldown: 5}
                mobs:
                  m1: {type: zombie, drop-table: missing, drops: [{item: flesh}]}
                  m2: {type: zombie, drop-table: good}
                """, problems);
        assertEquals(List.of(new MobDef.Drop("bone", 1, 1, 1.0)), r.dropTables().get("good"));
        assertEquals(List.of(), r.dropTables().get("empty"));
        assertEquals(List.of(new MobDef.Drop("flesh", 1, 1, 1.0)), r.mobs().get("m1").drops());
        assertNull(r.mobs().get("m1").stats().dropTable());
        assertEquals("good", r.mobs().get("m2").stats().dropTable());
        assertEquals(AbilityId.SLAM.defaultChance(), r.resolve(AbilitySpec.of(AbilityId.SLAM)).rollChance());
        // nope, 7, duplicate good, Bad Id, string, empty table, slam chance, laser, m1 table
        assertEquals(9, problems.size(), String.join("\n", problems));
    }

    @Test
    void dropTablesMustBeAList() throws Exception {
        List<String> problems = new ArrayList<>();
        MobCatalogParser.Result r = parse("""
                drop-tables:
                  good: {drops: []}
                mobs: {}
                """, problems);
        assertTrue(r.dropTables().isEmpty());
        assertEquals(1, problems.size(), problems.toString());
    }

    @Test
    void codeDefaultsAreInsideTheirRanges() {
        for (AbilityId id : AbilityId.values()) {
            assertEquals(id, AbilityId.of(id.id().toUpperCase()));
            for (AbilityId.Param p : id.params().values()) {
                if (!p.text()) {
                    assertTrue(p.def() >= p.min() && p.def() <= p.max(), id + "." + p.key());
                }
            }
            assertTrue(id.healthTriggered() || id.defaultCooldownSeconds() >= AbilityId.MIN_COOLDOWN, id.id());
        }
        assertNull(AbilityId.of("nope"));
        assertEquals(Map.of(), AbilitySpec.of(AbilityId.SLAM).numbers());
    }
}
