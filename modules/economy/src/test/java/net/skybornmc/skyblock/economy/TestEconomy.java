package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.profile.ProfileService;

import java.io.File;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/** The real economy service on a test database, for tests in other packages (banks). */
public final class TestEconomy {

    private TestEconomy() {
    }

    public static EconomyService create(Database db, File dir, LongSupplier clock, ProfileService profiles) {
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir, Logger.getLogger("test"));
        EconomyStore store = new EconomyStore(db, () -> 0, clock);
        store.owners(profiles::ownerOf);
        EconomyConfig config = new EconomyConfig();
        return new EconomyServiceImpl(store, new BalanceCache(), audit, () -> config, uuid -> null, c -> "$", () -> 0,
                Logger.getLogger("test"), () -> profiles);
    }
}
