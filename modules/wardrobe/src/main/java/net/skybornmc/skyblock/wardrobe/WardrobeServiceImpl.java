package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.ArmorSlots;
import net.skybornmc.skyblock.common.wardrobe.SetEntryView;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import net.skybornmc.skyblock.common.wardrobe.WornPiece;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The published {@link WardrobeService}. */
final class WardrobeServiceImpl implements WardrobeService {

    private final WardrobeModule module;

    WardrobeServiceImpl(WardrobeModule module) {
        this.module = module;
    }

    @Override
    public Map<String, Integer> wornPieces(Player player) {
        Session s = module.equipment().sessionOrNull(player.getUniqueId());
        return s == null ? Map.of() : s.bonuses.pieces();
    }

    private Session byProfile(UUID profileKey) {
        for (Session s : module.equipment().sessions()) {
            if (s.loaded && profileKey.equals(s.profile)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Set<String> ownedSets(UUID profileKey) {
        Session s = byProfile(profileKey);
        return s == null ? Set.of() : Set.copyOf(s.ownedIds());
    }

    @Override
    public int setLevel(UUID profileKey, String setId) {
        Session s = byProfile(profileKey);
        if (s == null || setId == null) {
            return 0;
        }
        return s.containing(setId).stream().mapToInt(WardrobeStore.Entry::level).max().orElse(0);
    }

    @Override
    public void setXp(Player player, String action, double amount) {
        Leveling leveling = module.leveling();
        if (leveling != null) {
            leveling.api(player, action, amount);
        }
    }

    @Override
    public double levelMultiplier(int level) {
        return module.bonusMultiplier(level);
    }

    @Override
    public int enchantSlots(UUID profileKey, String setId, ArmorPiece piece) {
        return module.config().armorSlots().openSlots(piece, setLevel(profileKey, setId));
    }

    // ------------------------------------------------------------------ armor enchants (E5)

    @Override
    public ArmorSlots armorSlots() {
        return module.config().armorSlots();
    }

    /** The loaded, not-stripped session of an online player, or null. */
    private Session ready(Player player) {
        Session s = player == null ? null : module.equipment().sessionOrNull(player.getUniqueId());
        return s == null || !s.loaded || s.stripped ? null : s;
    }

    /** The set level the enchant rules use: the entry's level, or the max level while set levels are off. */
    int enchantLevel(WardrobeStore.Entry e) {
        return module.config().levelsEnabled ? Math.max(0, e.level()) : module.config().maxLevel;
    }

    private SetEntryView view(Session s, WardrobeStore.Entry e) {
        SetDef look = module.registry().get(e.look()).orElse(null);
        Set<ArmorPiece> worn = EnumSet.noneOf(ArmorPiece.class);
        for (WardrobeStore.Equipped q : s.equipped.values()) {
            if (q.entryId().equals(e.entryId())) {
                worn.add(q.piece());
            }
        }
        return new SetEntryView(e.entryId(), e.setId(), e.mergedSetId(), e.look(), module.text().entryName(module.registry(), e),
                look == null ? Rarity.COMMON : look.rarity(), enchantLevel(e), s.enchants(e.entryId()), worn);
    }

    @Override
    public List<SetEntryView> entries(Player player) {
        Session s = ready(player);
        if (s == null) {
            return List.of();
        }
        List<SetEntryView> out = new ArrayList<>();
        for (WardrobeStore.Entry e : s.entries) {
            if (module.registry().get(e.look()).isPresent()) {
                out.add(view(s, e));
            }
        }
        return out;
    }

    @Override
    public Optional<SetEntryView> entry(Player player, UUID entryId) {
        Session s = ready(player);
        if (s == null || entryId == null) {
            return Optional.empty();
        }
        return s.entry(entryId).filter(e -> module.registry().get(e.look()).isPresent()).map(e -> view(s, e));
    }

    @Override
    public ItemStack pieceIcon(Player player, UUID entryId, ArmorPiece piece) {
        Session s = ready(player);
        if (s == null || entryId == null || piece == null) {
            return null;
        }
        Optional<WardrobeStore.Entry> e = s.entry(entryId);
        Optional<SetDef> look = e.flatMap(x -> module.registry().get(x.look()));
        if (e.isEmpty() || look.isEmpty()) {
            return null;
        }
        return module.items().pieceIcon(look.get(), e.get(), piece, s.enchants(entryId, piece));
    }

    @Override
    public List<WornPiece> wornEnchants(Player player) {
        Session s = ready(player);
        if (s == null || s.equipped.isEmpty()) {
            return List.of();
        }
        List<WornPiece> out = new ArrayList<>(4);
        for (WardrobeStore.Equipped q : s.equipped.values()) {
            List<ToolPerk> list = s.enchants(q.entryId(), q.piece());
            if (list.isEmpty()) {
                continue;
            }
            s.entry(q.entryId()).ifPresent(e -> out.add(new WornPiece(q.piece(), e.entryId(), enchantLevel(e), list)));
        }
        return out;
    }

    @Override
    public boolean inWarzone(Player player) {
        Session s = player == null ? null : module.equipment().sessionOrNull(player.getUniqueId());
        if (s != null && s.loaded) {
            return s.warzone;
        }
        return player != null && module.config().warzoneWorld(player.getWorld().getName());
    }

    @Override
    public double warzoneFactor(BonusFamily family) {
        Double f = family == null ? null : module.config().normalization().factors().get(family);
        return f == null || !(f > 0) ? 0 : f;
    }

    @Override
    public boolean busy(Player player) {
        Session s = player == null ? null : module.equipment().sessionOrNull(player.getUniqueId());
        return s != null && (s.busy || s.stripped || !s.loaded);
    }

    @Override
    public Optional<List<ToolPerk>> lockPieceEnchants(Connection connection, UUID profileKey, UUID entryId, ArmorPiece piece) throws SQLException {
        return module.store().lockPiece(connection, profileKey, entryId, piece);
    }

    @Override
    public void savePieceEnchants(Connection connection, UUID entryId, ArmorPiece piece, List<ToolPerk> enchants) throws SQLException {
        module.store().savePiece(connection, entryId, piece, enchants);
    }

    @Override
    public void pieceEnchantsChanged(Player player, UUID entryId, ArmorPiece piece, List<ToolPerk> enchants) {
        Session s = player == null ? null : module.equipment().sessionOrNull(player.getUniqueId());
        if (s == null || !s.loaded || s.entry(entryId).isEmpty()) {
            if (player != null && player.isOnline()) {
                module.equipment().load(player);
            }
            return;
        }
        s.putEnchants(entryId, piece, enchants);
        module.equipment().rerender(player, s, entryId::equals);
    }

    @Override
    public boolean isSetPiece(ItemStack stack) {
        return WardrobeItems.isPiece(stack);
    }

    @Override
    public CompletableFuture<ItemStack> createToken(String setId, String origin, UUID owner, Actor actor) {
        return module.shop().createToken(setId, origin, owner, actor);
    }

    @Override
    public void openStaffView(Player staff, UUID target, String targetName, Menu parent) {
        module.staff().open(staff, target, targetName, parent);
    }
}
