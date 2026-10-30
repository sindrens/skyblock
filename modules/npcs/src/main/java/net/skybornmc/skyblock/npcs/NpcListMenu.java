package net.skybornmc.skyblock.npcs;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Staff list of every NPC, hologram and leaderboard from the builds ({@code /skyblock npcs}, staff hub). Click one to
 * teleport in front of it. Read-only otherwise; positions live in the build metadata.
 */
final class NpcListMenu extends PagedMenu<Placed> {

    private final NpcsModule module;

    NpcListMenu(NpcsModule module) {
        this.module = module;
    }

    @Override
    public String messageModule() {
        return NpcsModule.ID;
    }

    @Override
    public String infoKey() {
        return "admin.menu.info";
    }

    @Override
    public String helpTopic() {
        return "npcs";
    }

    @Override
    protected List<Placed> entries() {
        Placer placer = module.placer();
        return placer == null ? List.of() : new ArrayList<>(placer.all());
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(service.text(viewer, NpcsModule.ID, "admin.menu.crumb"));
    }

    @Override
    protected String emptyKey() {
        return "admin.menu.empty";
    }

    @Override
    protected MenuButton entry(Placed p) {
        Material icon = switch (p.kind) {
            case NPC -> Material.ARMOR_STAND;
            case HOLOGRAM -> Material.OAK_SIGN;
            case BOARD -> Material.GOLD_BLOCK;
        };
        Location at = p.where(Bukkit.getWorld(p.world));
        String kind = p.kind.name().toLowerCase(Locale.ROOT);
        String name = p.npc != null ? module.npcNameRaw(p.pointId(), p.npc.role()) : NpcsModule.pretty(p.pointId());
        var item = service.item(icon, viewer, NpcsModule.ID, "admin.menu.entry",
                Msg.mm("name", name), Msg.p("id", p.id), Msg.p("kind", module.plain("admin.kind." + kind)),
                Msg.p("role", p.npc != null ? p.npc.role() : "-"), Msg.p("world", p.world),
                Msg.number("x", at.getBlockX()), Msg.number("y", at.getBlockY()), Msg.number("z", at.getBlockZ()),
                Msg.p("state", module.plain(p.spawned() ? "admin.state.spawned" : "admin.state.unloaded")));
        return MenuButton.of(item, click -> {
            click.player().closeInventory();
            module.teleport(click.player(), p);
        });
    }
}
