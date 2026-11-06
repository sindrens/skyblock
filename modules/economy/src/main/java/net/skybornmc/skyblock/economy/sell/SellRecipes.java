package net.skybornmc.skyblock.economy.sell;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server's recipes for {@link SellDerive} (ECON-SELL-SHOP-AH A2): shaped, shapeless, furnace-like (smelting,
 * blasting, smoking, campfire) and stonecutting, with plain material choices only (exact items, smithing and special
 * recipes are skipped). Main thread; read once per catalog load (about 1,500 recipes, under 50 ms).
 */
final class SellRecipes {

    private SellRecipes() {
    }

    static List<SellDerive.Recipe> fromServer() {
        List<SellDerive.Recipe> out = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r;
            try {
                r = it.next();
            } catch (RuntimeException e) {
                continue; // a broken data-pack recipe never stops the price list
            }
            ItemStack result = r.getResult();
            if (result == null || result.getType().isAir() || result.hasItemMeta() && result.getItemMeta().hasEnchants()) {
                continue;
            }
            String output = key(result.getType());
            int count = Math.max(1, result.getAmount());
            List<List<String>> inputs = new ArrayList<>();
            SellDerive.Kind kind;
            if (r instanceof ShapedRecipe shaped) {
                Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
                boolean ok = true;
                for (String row : shaped.getShape()) {
                    for (char ch : row.toCharArray()) {
                        RecipeChoice c = choices.get(ch);
                        if (c == null) {
                            continue; // empty cell
                        }
                        List<String> alts = alternatives(c);
                        if (alts.isEmpty()) {
                            ok = false;
                        }
                        inputs.add(alts);
                    }
                }
                if (!ok) {
                    continue;
                }
                kind = SellDerive.Kind.SHAPED;
            } else if (r instanceof ShapelessRecipe shapeless) {
                boolean ok = true;
                for (RecipeChoice c : shapeless.getChoiceList()) {
                    List<String> alts = alternatives(c);
                    if (alts.isEmpty()) {
                        ok = false;
                    }
                    inputs.add(alts);
                }
                if (!ok) {
                    continue;
                }
                kind = SellDerive.Kind.SHAPELESS;
            } else if (r instanceof CookingRecipe<?> cooking) {
                List<String> alts = alternatives(cooking.getInputChoice());
                if (alts.isEmpty()) {
                    continue;
                }
                inputs.add(alts);
                kind = SellDerive.Kind.SMELTING;
            } else if (r instanceof StonecuttingRecipe cutting) {
                List<String> alts = alternatives(cutting.getInputChoice());
                if (alts.isEmpty()) {
                    continue;
                }
                inputs.add(alts);
                kind = SellDerive.Kind.STONECUTTING;
            } else {
                continue; // smithing, transmute and special recipes are never derived
            }
            if (!inputs.isEmpty()) {
                out.add(new SellDerive.Recipe(output, count, inputs, kind));
            }
        }
        return out;
    }

    /** Material keys of a choice (tags give every member); empty for exact-item or unknown choices. */
    static List<String> alternatives(RecipeChoice choice) {
        List<String> out = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice m) {
            for (Material mat : m.getChoices()) {
                if (mat != null && !mat.isAir()) {
                    out.add(key(mat));
                }
            }
        }
        return out;
    }

    static String key(Material m) {
        return m.getKey().getKey().toLowerCase(Locale.ROOT);
    }
}
