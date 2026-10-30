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
