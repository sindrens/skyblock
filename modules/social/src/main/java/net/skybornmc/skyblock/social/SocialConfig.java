package net.skybornmc.skyblock.social;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/** Typed settings of {@code social.yml}. */
public final class SocialConfig extends ModuleConfig {

    @Setting(path = "dm.max-length", desc = "Longest private message (characters).", min = 16, max = 1024)
    public int dmMaxLength = 256;

    @Setting(path = "dm.rate-limit.messages", desc = "Private messages allowed per window before slowing down.", min = 1, max = 100)
    public int dmRateMessages = 5;

    @Setting(path = "dm.rate-limit.seconds", desc = "Length of the private message rate-limit window (seconds).", min = 1, max = 300)
    public int dmRateSeconds = 5;

    @Setting(path = "dm.contact-filter", desc = "R3-32: refuse links, IP addresses and phone numbers in private messages (the "
            + "same check as chat, chat.yml rules; the swear list is never used for private messages, answer 305).")
    public boolean dmContactFilter = true;

    @Setting(path = "dm.new-player-protection", desc = "R3-32: until an account finishes the tutorial, only staff and players it "
            + "messaged first can send it private messages.")
    public boolean dmNewPlayerProtection = true;

    @Setting(path = "ignore.hide-chat-default", desc = "Owner answer 317: /settings 'Hide ignored chat' starts on, so the public "
            + "chat lines of players you /ignore are hidden for you only. Players can switch it off. Applies after a restart.")
    public boolean hideIgnoredChatDefault = true;

    @Setting(path = "afk.auto-minutes", desc = "Minutes without activity before a player is marked AFK. 0 = never.", min = 0, max = 240)
    public int afkAutoMinutes = 5;

    @Setting(path = "afk.broadcast", desc = "Tell everyone when a player goes AFK or comes back.")
    public boolean afkBroadcast = true;

    @Setting(path = "broadcast.title", desc = "Also show /broadcast messages as a title on screen.")
    public boolean broadcastTitle = true;

    @Setting(path = "links.discord", desc = "Link used by /discord.")
    public String discordUrl = "https://discord.skybornmc.net";

    @Setting(path = "links.store", desc = "Link used by /store.")
    public String storeUrl = "https://store.skybornmc.net";

    @Setting(path = "links.vote", desc = "Link used by /vote.")
    public String voteUrl = "https://skybornmc.net/vote";

    @Setting(path = "links.website", desc = "Link used by /website.")
    public String websiteUrl = "https://skybornmc.net";

    @Override
    public String fileName() {
        return "social.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock social settings (private messages, AFK, broadcasts, links).
                Texts are in messages/social.yml. Reload: /skyblock reload social""";
    }
}
