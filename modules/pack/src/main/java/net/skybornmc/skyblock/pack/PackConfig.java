package net.skybornmc.skyblock.pack;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Typed settings of {@code pack.yml}. Texts that players see on a kick are in {@code messages/pack.yml}. */
public final class PackConfig extends ModuleConfig {

    /** Which bundled zip is sent. */
    public enum Variant { SAFE, SHINE }

    @Setting(desc = "Send the Phoenix resource pack to Java players. Off = nobody gets it (menus and badges fall back to plain text).")
    public boolean enabled = true;

    @Setting(desc = "safe = badges shine as still sparkles; shine = adds the moving-shine shader (26.3 clients only). Switch with /pack reload.")
    public Variant variant = Variant.SAFE;

    @Setting(desc = "Full download URL of the zip when it is hosted elsewhere (its SHA-1 must match the bundled pack). Empty = this server hosts it.")
    public String url = "";

    @Setting(path = "host.bind", desc = "Address the built-in pack web server listens on. Empty = all addresses.", restart = true)
    public String hostBind = "";

    @Setting(path = "host.port", desc = "TCP port of the built-in pack web server. Open it in the firewall.", min = 1, max = 65535, restart = true)
    public int hostPort = 25580;

    @Setting(path = "host.public-host", desc = "Host name (or IP) players download the pack from, used to build the URL when 'url' is empty.")
    public String hostPublicHost = "play.skybornmc.net";

    @Setting(desc = "Prompt shown on the client's resource pack screen (MiniMessage).")
    public String prompt = "<gradient:#ff9a3c:#ff3d2e><bold>Phoenix Skyblock</bold></gradient> <gray>needs its resource pack for menus, badges and sounds.";

    @Setting(desc = "The client must accept the pack to join. A player who declines is kicked with the 'kick.declined' message.")
    public boolean required = true;

    @Setting(path = "kick-on-fail", desc = "Kick a player whose pack download or loading failed (they are asked to rejoin). Off = they play with the text fallback.")
    public boolean kickOnFail = true;

    @Setting(path = "timeout-seconds", desc = "Seconds to wait for the client to load the pack before counting it as failed.", min = 5, max = 600)
    public int timeoutSeconds = 60;

    @Override
    public String fileName() {
        return "pack.yml";
    }

    @Override
    public String header() {
        return "Phoenix resource pack. Reload with /pack reload (resends the pack to everyone online).";
    }
}
