package net.skybornmc.skyblock.common.db;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Starts a throw-away H2 (MySQL mode) database for tests. */
public final class H2 {

    private H2() {
    }

    public static Database start(Path dir) throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "test";
        config.poolSize = 2;
        Database db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        return db;
    }
}
