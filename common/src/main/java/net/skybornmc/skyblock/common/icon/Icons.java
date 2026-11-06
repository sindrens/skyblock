package net.skybornmc.skyblock.common.icon;

import net.skybornmc.skyblock.common.pack.PackService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Menu icons (I7 pack v2, I9 section 7): every icon is its real vanilla base item; viewers whose pack status is
 * {@link PackService.Status#LOADED} also get the icon key as {@code custom_model_data} string, which the pack's item
 * definition turns into our own picture. Everyone else (Bedrock, declined, failed, loading, host down, pack module off)
 * sees the plain base item. Never sets {@code item_model} (I9 7.3). Reached through {@code core.menus().icons()}.
 */
public final class Icons {

    private final Supplier<PackService> packs;
    private final Logger log;
    private final Supplier<IconCatalog> loader;
    private volatile IconCatalog catalog;
    private final Map<String, Material> materials = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /**
     * @param loader reads the catalogue (on construction and every {@link #reload()})
     * @param packs  the pack service, may return {@code null} (no service = nobody gets models)
     */
    public Icons(Supplier<IconCatalog> loader, Supplier<PackService> packs, Logger log) {
        this.loader = loader;
        this.packs = packs == null ? () -> null : packs;
        this.log = log;
        reload();
    }

    /** Reloads the catalogue (classpath) and forgets cached materials and warnings. */
    public void reload() {
        IconCatalog loaded = loader.get();
        catalog = loaded == null ? IconCatalog.empty() : loaded;
        materials.clear();
        warned.clear();
    }

    public IconCatalog catalog() {
        return catalog;
    }

    public boolean has(String key) {
        return catalog.has(key);
    }

    /**
     * The vanilla item of {@code key}: the base item of a custom icon, or the item of a plain vanilla key;
     * {@link Material#BARRIER} for an unknown key or an id that is not an item.
     */
    public Material base(String key) {
        return materials.computeIfAbsent(key == null ? "" : key, k -> {
            String id = catalog.get(k).map(IconDef::base).or(() -> catalog.vanilla(k)).orElse(null);
            if (id == null) {
                return Material.BARRIER;
            }
            Material m = Material.matchMaterial(id);
            if (m == null || !m.isItem()) {
                warnOnce("base:" + k, "icon '" + k + "' has item '" + id + "', which is not an item; showing a barrier");
                return Material.BARRIER;
            }
            return m;
        });
    }

    /**
     * The icon for {@code viewer}: the base item, plus the model string when the viewer has the pack loaded and the key
     * is a custom icon. A plain vanilla key is the real item for everyone, never tagged. An unknown key logs once and
     * returns a plain barrier (never a missing-model cube).
     */
    public ItemStack item(Player viewer, String key) {
        boolean known = has(key);
        if (!known) {
            warnOnce("key:" + key, "unknown icon key '" + key + "' (not in icons/icons.json); showing a barrier");
            return new ItemStack(Material.BARRIER);
        }
        ItemStack item = new ItemStack(base(key));
        if (shouldTag(status(viewer), catalog.isCustom(key))) {
            item.editMeta(meta -> {
                CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
                cmd.setStrings(List.of(key));
                meta.setCustomModelDataComponent(cmd);
            });
        }
        return item;
    }

    /** True when {@code viewer}'s client has the pack loaded. */
    public boolean viewerGetsModel(Player viewer) {
        PackService service = packs.get();
        return service != null && service.hasPack(viewer);
    }

    private PackService.Status status(Player viewer) {
        PackService service = packs.get();
        if (service == null || viewer == null) {
            return null;
        }
        return service.status(viewer.getUniqueId());
    }

    /** Pure: only {@link PackService.Status#LOADED} viewers get models. */
    public static boolean viewerGetsModel(PackService.Status status) {
        return status == PackService.Status.LOADED;
    }

    /** Pure: the model string is set only for a known key and a LOADED viewer ({@code null} status = no service). */
    public static boolean shouldTag(PackService.Status status, boolean known) {
        return known && viewerGetsModel(status);
    }

    private void warnOnce(String id, String message) {
        if (warned.add(id) && log != null) {
            log.warning("Icons: " + message);
        }
    }
}
