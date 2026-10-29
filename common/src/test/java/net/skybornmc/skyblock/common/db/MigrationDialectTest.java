package net.skybornmc.skyblock.common.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationDialectTest {

    @Test
    void picksTheVariantOfTheRunningDatabase() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        List<Migrations.Migration> h2 = Migrations.find("core", loader, "h2");
        List<Migrations.Migration> maria = Migrations.find("core", loader, "mariadb");
        assertEquals("V002__audit_log.h2.sql", h2.get(1).file());
        assertEquals("V002__audit_log.mariadb.sql", maria.get(1).file());
        assertTrue(maria.get(1).sql().contains("PARTITION BY RANGE"));
    }

    @Test
    void variantFileNamesMatch() {
        assertTrue(Migrations.FILE.matcher("V002__audit_log.mariadb.sql").matches());
        assertTrue(Migrations.FILE.matcher("V002__audit_log.h2.sql").matches());
        assertTrue(Migrations.FILE.matcher("V001__players.sql").matches());
    }
}
