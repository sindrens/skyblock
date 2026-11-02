package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B2: staff switch-offs survive a restart, switching on again clears them, the catalog hides them. */
class ShopOverridesTest {

    @TempDir
    Path dir;

    private Database db;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "shop-overrides";
        dbc.poolSize = 2;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("shop-overrides");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("shop", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void switchOffSurvivesARestartAndSwitchOnClearsIt() throws Exception {
        ShopOverrides o = new ShopOverrides(db);
        o.load().join();
        o.set("hopper", false, "Price exploit", "console", 1).join();
        assertEquals("Price exploit", o.get("hopper").reason());
        ShopOverrides after = new ShopOverrides(db);
        after.load().join();
        assertEquals(Set.of("hopper"), after.disabled(), "read back from shop_overrides");
        after.set("hopper", true, "Fixed", "console", 2).join();
        assertNull(after.get("hopper"));
        ShopOverrides again = new ShopOverrides(db);
        again.load().join();
        assertTrue(again.disabled().isEmpty());
    }

    @Test
    void theCatalogHidesSwitchedOffItems() throws Exception {
        ShopCatalog c = ShopCatalogTest.catalog().withDisabled(Set.of("hopper", "no_such_item"));
        assertFalse(c.item("hopper").orElseThrow().enabled());
        assertEquals(2_500, c.item("hopper").orElseThrow().price(), "price kept");
        assertEquals(2_500, c.worth("hopper").orElseThrow(), "block worth kept");
        assertTrue(c.item("dirt").orElseThrow().enabled());
        assertEquals(ShopCatalogTest.catalog().size(), c.size());
    }
}
