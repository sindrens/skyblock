package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterService;
import net.skybornmc.skyblock.common.booster.BoosterType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** {@link BoosterService} of the boosters module. */
final class BoosterServiceImpl implements BoosterService {

    private final BoostersModule m;

    BoosterServiceImpl(BoostersModule module) {
        this.m = module;
    }

    @Override
    public boolean valid(BoosterType type, int percent, int minutes, boolean global) {
        return type != null && m.settings.valid(percent, minutes, global) && (!global || m.settings.globalEnabled);
    }

    @Override
    public Optional<ItemStack> createPersonal(BoosterType type, int percent, int minutes, UUID profileKey, String origin) {
        if (!valid(type, percent, minutes, false)) {
            return Optional.empty();
        }
        // RX39: personal boosters are always bound to the profile that earned them.
        return Optional.of(m.items().create(type, percent, minutes, false, profileKey, true, origin));
    }

    @Override
    public Optional<ItemStack> createGlobal(BoosterType type, int percent, int minutes, UUID profileKey, boolean bound, String origin) {
        if (!valid(type, percent, minutes, true)) {
            return Optional.empty();
        }
        return Optional.of(m.items().create(type, percent, minutes, true, profileKey, bound || m.settings.globalAlwaysBound, origin));
    }

    @Override
    public long bank(Connection c, UUID profileKey, BoosterType type, int percent, long millis, long maxBankedMillis, String source)
            throws SQLException {
        long max = maxBankedMillis > 0 ? maxBankedMillis : m.settings.bankMaxMinutes * BoosterRules.MINUTE;
        String src = source == null || source.isBlank() ? "other" : source.length() > 32 ? source.substring(0, 32) : source;
        return BoosterStore.bank(c, profileKey, type, Math.max(1, percent), millis, max, src, System.currentTimeMillis());
    }

    @Override
    public void banked(UUID profileKey, BoosterType type, int percent, long millis, long bankedAfter, String source, Actor actor) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", type.id());
        d.put("profile", profileKey.toString());
        d.put("added_ms", millis);
        d.put("banked_ms", bankedAfter);
        d.put("source", source);
        UUID owner = m.profiles().ownerOf(profileKey);
        m.audit("bank_add", actor == null ? Actor.SYSTEM : actor, owner, null, (long) percent, d);
        m.core.scheduler().ensureMain("boosters:banked", () -> m.personal().reloadBanks(profileKey));
    }

    @Override
    public void hold(String owner, Predicate<Player> hold) {
        if (owner != null && hold != null) {
            m.personal().hold(owner, hold);
        }
    }

    @Override
    public void release(String owner) {
        if (owner != null) {
            m.personal().release(owner);
        }
    }
}
