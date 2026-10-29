package net.skybornmc.skyblock.cosmetics;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.text.LegacyColors;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tag items (owner decision): tags won from crates or bought in the store arrive as a unique item. Right-click →
 * confirm → the tag is added to your account and the item is used up. Extra copies can be traded and sold on the
 * auction house. Unique id per item, so the dupe scanner protects them.
 */
final class TagItems implements Listener {

    static final String ITEM_ID = "tag_item";
    static final NamespacedKey TAG_KEY = new NamespacedKey(ItemKeys.NAMESPACE, "cosmetic_tag");

    private final CosmeticsModule module;

    TagItems(CosmeticsModule module) {
        this.module = module;
    }

    void register() {
        if (module.core().items().get(ITEM_ID).isPresent()) {
            return;
        }
        module.core().items().register(ItemDefinition.builder(ITEM_ID, Material.NAME_TAG, ItemType.COSMETIC, Rarity.EPIC)
                .name(module.raw("item.name"))
                .description(module.raw("item.description"))
                .action(module.raw("item.action-input"), module.raw("item.action-text"))
                .unique()
                .glint()
                .renderer(new ItemDefinition.Renderer() {
                    @Override
                    public String name(ItemStack stack, ItemDefinition definition) {
                        return tagOf(stack).flatMap(id -> module.registry().get(Cosmetics.Kind.TAG, id))
                                .map(tag -> LoreBuilder.fill(module.raw("item.name-with-tag"), Map.of("tag", module.service().displayRaw(tag, TagItems::mini))))
                                .orElse(definition.name());
                    }

                    @Override
                    public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                        tagOf(stack).flatMap(id -> module.registry().get(Cosmetics.Kind.TAG, id)).ifPresent(tag -> {
                            lore.line(LoreBuilder.fill(module.raw("item.preview"), Map.of("tag", module.service().displayRaw(tag, TagItems::mini))));
                            lore.line(LoreBuilder.fill(module.raw("item.rarity"),
                                    Map.of("rarity", "<rarity:" + tag.rarity().name().toLowerCase(Locale.ROOT) + ">"
                                            + tag.rarity().name().charAt(0) + tag.rarity().name().substring(1).toLowerCase(Locale.ROOT))));
                        });
                    }
                })
                .build());
    }

    private static String mini(String display) {
        return LegacyColors.hasCodes(display) && display.indexOf('<') < 0 ? LegacyColors.toMiniMessage(display) : display;
    }

    static Optional<String> tagOf(ItemStack stack) {
        if (stack == null || !ItemRegistry.is(stack, ITEM_ID) || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(TAG_KEY, PersistentDataType.STRING));
    }

    /** A new unique tag item for {@code tagId}. */
    ItemStack create(String tagId, String source) {
        return module.core().items().create(ITEM_ID, 1, source, pdc -> pdc.set(TAG_KEY, PersistentDataType.STRING, tagId));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack hand = event.getItem();
        Optional<String> tagId = tagOf(hand);
        if (tagId.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<Cosmetic> tag = module.registry().get(Cosmetics.Kind.TAG, tagId.get());
        if (tag.isEmpty()) {
            module.msg().send(player, "redeem.unknown");
            return;
        }
        if (module.service().owns(player.getUniqueId(), Cosmetics.Kind.TAG, tag.get().id())) {
            module.msg().send(player, "redeem.owned", Msg.c("display", module.service().display(player, tag.get())));
            return;
        }
        String itemUuid = ItemRegistry.uuidOf(hand).map(Object::toString).orElse("");
        module.core().menus().open(player, new ConfirmMenu(p -> module.text(p, "redeem.crumb"), hand.clone(),
                p -> module.lines(p, "redeem.details", Msg.c("display", module.service().display(p, tag.get()))),
                p -> redeem(p, tag.get(), itemUuid),
                p -> { }));
    }

    /** Uses up the exact item that was confirmed (same unique id, still in the main hand), then grants the tag. */
    private void redeem(Player player, Cosmetic tag, String itemUuid) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (itemUuid.isEmpty() || !itemUuid.equals(ItemRegistry.uuidOf(hand).map(Object::toString).orElse(""))
                || !tagOf(hand).map(tag.id()::equals).orElse(false)) {
            module.msg().send(player, "redeem.moved");
            return;
        }
        ItemStack taken = hand.clone();
        player.getInventory().setItemInMainHand(null);
        module.service().grant(player.getUniqueId(), Cosmetics.Kind.TAG, tag.id(), "item:" + itemUuid, Actor.player(player))
                .whenComplete((added, error) -> module.core().plugin().getServer().getScheduler().runTask(module.core().plugin(), () -> {
                    if (error != null || !Boolean.TRUE.equals(added)) {
                        // Nothing was granted: give the item back (inventory, or at the player's feet).
                        player.getInventory().addItem(taken).values()
                                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                        module.msg().send(player, error != null ? "redeem.error" : "redeem.owned",
                                Msg.c("display", module.service().display(player, tag)));
                        return;
                    }
                    module.core().audit().logPlayer(AuditCategory.ITEM, "tag_redeem", Actor.player(player), player.getUniqueId(),
                            player.getName(), null, player.getLocation(), Map.of("tag", tag.id(), "item_uuid", itemUuid));
                }));
    }

    /** Lines for the console/staff when items were handed out. */
    static List<String> ids(CosmeticsModule module) {
        return List.copyOf(module.registry().ids(Cosmetics.Kind.TAG));
    }
}
