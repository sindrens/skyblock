package net.skybornmc.skyblock.profiles;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures and applies a player's per-profile state (owner PR4): inventory with armor and offhand, ender chest, XP,
 * health, food, effects and location. Main thread only.
 */
final class StateCodec {

    private StateCodec() {
    }

    /**
     * Owner 4 Nov 2026: per-profile values that other modules keep in the player's persistent data (the /farm and /mine
     * tier counters): saved and loaded with the profile, removed for a fresh profile (tier 1). From
     * {@code state.persistent-keys} (profiles.yml); set on enable and reload.
     */
    private static volatile List<org.bukkit.NamespacedKey> persistentKeys = List.of();

    static void persistentKeys(List<String> keys) {
        List<org.bukkit.NamespacedKey> parsed = new ArrayList<>();
        for (String key : keys) {
            org.bukkit.NamespacedKey k = key == null ? null : org.bukkit.NamespacedKey.fromString(key.strip().toLowerCase(java.util.Locale.ROOT));
            if (k != null && !parsed.contains(k)) {
                parsed.add(k);
            }
        }
        persistentKeys = List.copyOf(parsed);
    }

    static StoredState capture(Player player) {
        byte[] inventory = ItemStack.serializeItemsAsBytes(nonNull(player.getInventory().getContents()));
        byte[] ender = ItemStack.serializeItemsAsBytes(nonNull(player.getEnderChest().getContents()));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("level", player.getLevel());
        yaml.set("exp", (double) player.getExp());
        yaml.set("total-exp", player.getTotalExperience());
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", (double) player.getSaturation());
        yaml.set("exhaustion", (double) player.getExhaustion());
        yaml.set("fire-ticks", player.getFireTicks());
        yaml.set("air", player.getRemainingAir());
        yaml.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        for (org.bukkit.NamespacedKey key : persistentKeys) {
            Long value = player.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.LONG);
            if (value != null) {
                yaml.set("persistent." + key.getNamespace() + "." + key.getKey(), value);
            }
        }
        Location l = player.getLocation();
        if (l.getWorld() != null) {
            yaml.set("location.world", l.getWorld().getName());
            yaml.set("location.x", l.getX());
            yaml.set("location.y", l.getY());
            yaml.set("location.z", l.getZ());
            yaml.set("location.yaw", (double) l.getYaw());
            yaml.set("location.pitch", (double) l.getPitch());
        }
        return new StoredState(inventory, ender, yaml.saveToString());
    }

    /** Replaces the player's state. A fresh state (never played) starts empty with full health and food. */
    static void apply(Player player, StoredState state) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getEnderChest().clear();
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        for (org.bukkit.NamespacedKey key : persistentKeys) {
            player.getPersistentDataContainer().remove(key); // a fresh profile starts at tier 1
        }
        double maxHealth = maxHealth(player);
        if (state == null || state.fresh()) {
            player.setLevel(0);
            player.setExp(0);
            player.setTotalExperience(0);
            player.setHealth(maxHealth);
            player.setFoodLevel(20);
            player.setSaturation(5);
            player.setExhaustion(0);
            player.setFireTicks(0);
            player.setRemainingAir(player.getMaximumAir());
            player.updateInventory();
            return;
        }
        if (state.inventory() != null) {
            fill(player.getInventory().getSize(), ItemStack.deserializeItemsFromBytes(state.inventory()), player.getInventory()::setItem);
        }
        if (state.enderChest() != null) {
            fill(player.getEnderChest().getSize(), ItemStack.deserializeItemsFromBytes(state.enderChest()), player.getEnderChest()::setItem);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (state.data() != null) {
            try {
                yaml.loadFromString(state.data());
            } catch (InvalidConfigurationException e) {
                // keep defaults below; the items were restored already
            }
        }
        player.setLevel(Math.max(0, yaml.getInt("level", 0)));
        player.setExp((float) Math.clamp(yaml.getDouble("exp", 0), 0.0, 0.9999));
        player.setTotalExperience(Math.max(0, yaml.getInt("total-exp", 0)));
        double health = yaml.getDouble("health", maxHealth);
        player.setHealth(Math.clamp(health <= 0 ? maxHealth : health, 0.5, maxHealth));
        player.setFoodLevel(Math.clamp(yaml.getInt("food", 20), 0, 20));
        player.setSaturation((float) Math.clamp(yaml.getDouble("saturation", 5), 0.0, 20.0));
        player.setExhaustion((float) Math.clamp(yaml.getDouble("exhaustion", 0), 0.0, 4.0));
        player.setFireTicks(Math.max(0, yaml.getInt("fire-ticks", 0)));
        player.setRemainingAir(Math.clamp(yaml.getInt("air", player.getMaximumAir()), 0, player.getMaximumAir()));
        for (org.bukkit.NamespacedKey key : persistentKeys) {
            String path = "persistent." + key.getNamespace() + "." + key.getKey();
            if (yaml.contains(path)) {
                player.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.LONG, yaml.getLong(path));
            }
        }
        List<?> effects = yaml.getList("effects", List.of());
        for (Object o : effects) {
            if (o instanceof PotionEffect effect) {
                player.addPotionEffect(effect);
            }
        }
        player.updateInventory();
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private interface SlotSetter {
        void set(int slot, ItemStack item);
    }

    private static void fill(int size, ItemStack[] items, SlotSetter setter) {
        for (int i = 0; i < size && i < items.length; i++) {
            ItemStack item = items[i];
            setter.set(i, item == null || item.isEmpty() ? null : item);
        }
    }

    private static ItemStack[] nonNull(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] == null ? ItemStack.empty() : items[i];
        }
        return out;
    }
}
