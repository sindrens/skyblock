package net.skybornmc.skyblock.wardrobe;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.wardrobe.ArmorPiece;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
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
        return s == null ? 0 : s.owned(setId).map(WardrobeStore.Entry::level).orElse(0);
    }

    @Override
    public void setXp(Player player, String activity, double amount) {
        // Set levels arrive with W2 (wardrobe.yml → levels.enabled); until then XP is not counted.
    }

    @Override
    public int enchantSlots(UUID profileKey, String setId, ArmorPiece piece) {
        return setLevel(profileKey, setId) >= module.config().secondSlotLevel ? 2 : 1;
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
