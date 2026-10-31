package net.skybornmc.skyblock.production;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.3: the bundled crop tiers (WORLD §5.1) parse cleanly and match the plan. */
class CropCatalogTest {

    private static CropCatalog defaults() {
        List<String> problems = new ArrayList<>();
        CropCatalog c = CropCatalog.parse(new FarmingSettings().crops, problems::add);
        assertEquals(List.of(), problems);
        return c;
    }

    @Test
    void bundledTiersFollowThePlan() {
        CropCatalog c = defaults();
        assertEquals(13, c.crops().size());
        assertEquals(List.of(0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58), c.tierLevels());
        assertEquals(1.0, c.byBlock("wheat").xp());
        assertEquals(58.0, c.byBlock("pitcher_crop").xp());
        assertEquals(c.byBlock("carrots").tier(), c.byBlock("potatoes").tier());
        assertEquals(CropCatalog.Kind.FRUIT, c.byBlock("melon").kind());
        assertEquals(CropCatalog.Kind.STACK, c.byBlock("cactus").kind());
        assertEquals(CropCatalog.Kind.AGE, c.byBlock("sweet_berry_bush").kind());
    }

    @Test
    void plantingGatesUseTheBlockTheSeedPlaces() {
        CropCatalog c = defaults();
        assertEquals(0, c.plantLevel("wheat"));
        assertEquals(5, c.plantLevel("carrots"));
        assertEquals(20, c.plantLevel("melon_stem"), "melon seeds place a stem");
        assertEquals(0, c.plantLevel("melon"), "placing a melon block is building, not planting");
        assertEquals(50, c.plantLevel("torchflower_crop"));
        assertEquals(0, c.plantLevel("torchflower"));
        assertNull(c.byPlant("stone"));
    }

    @Test
    void bundledNamesAreRealMaterials() {
        for (CropCatalog.Crop crop : defaults().crops()) {
            Material block = Material.matchMaterial(crop.block());
            Material plant = Material.matchMaterial(crop.plant());
            Material item = Material.matchMaterial(crop.item());
            assertNotNull(block, crop.id() + " block");
            assertNotNull(plant, crop.id() + " plant");
            assertNotNull(item, crop.id() + " item");
        }
    }

    @Test
    void brokenLinesAreReportedAndSkipped() {
        List<String> problems = new ArrayList<>();
        CropCatalog c = CropCatalog.parse(List.of(
                "wheat: tier=1 level=0 xp=1 block=wheat item=wheat",
                "wheat: tier=2 level=5 xp=1 block=carrots item=carrot",
                "nothing",
                "melon: tier=5 level=20 xp=6 block=melon item=melon_slice kind=fruit",
                "cane: tier=4 level=x xp=4 block=sugar_cane item=sugar_cane kind=pile",
                "copy: tier=1 level=0 xp=1 block=wheat item=wheat"), problems::add);
        assertEquals(List.of("wheat", "cane"), c.crops().stream().map(CropCatalog.Crop::id).toList());
        assertEquals(CropCatalog.Kind.AGE, c.byBlock("sugar_cane").kind(), "unknown kind falls back to age");
        assertEquals(0, c.byBlock("sugar_cane").level(), "a bad level falls back to 0");
        assertEquals(6, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("plant=")));
        assertFalse(problems.isEmpty());
    }
}
