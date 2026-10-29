package net.skybornmc.skyblock.chat;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Typed settings of {@code chat.yml}: display formats (data, editable later in /staff → Display), chat protection,
 * mentions, item showing and the tab list.
 */
public final class ChatConfig extends ModuleConfig {

    // ---------------------------------------------------------------- formats (MiniMessage + parts)

    @Setting(path = "format.chat", desc = "Chat line. Parts: <title> <tag> <rank> <name> <message> (empty parts disappear). Style tags allowed.")
    public String chatFormat = "<title><rank><name> <c:muted>»</c:muted> <c:text><message>";

    @Setting(path = "format.tab", desc = "Tab list entry. Parts: <rank> <name> <title> <tag> <afk>.")
    public String tabFormat = "<rank><name><title><tag><afk>";

    @Setting(path = "format.title-part", desc = "How a title looks when a player has one. <value> = the title. Empty = never shown.")
    public String titlePart = "<value> ";

    @Setting(path = "format.tab-title-part", desc = "Title in the tab list (after the name). <value> = the title.")
    public String tabTitlePart = " <value>";

    @Setting(path = "format.tag-part", desc = "Tag (from /tags) in chat. <value> = the tag. Default: tags only in the tab list.")
    public String tagPart = "";

    @Setting(path = "format.tab-tag-part", desc = "Tag in the tab list. <value> = the tag.")
    public String tabTagPart = " <value>";

    @Setting(path = "format.afk-part", desc = "AFK marker in the tab list and nametag.")
    public String afkPart = " <c:muted>[AFK]</c:muted>";

    @Setting(path = "format.nametag", desc = "Show the rank prefix above heads (scoreboard teams).")
    public boolean nametagPrefix = true;

    // ---------------------------------------------------------------- protection

    @Setting(path = "protection.rate-limit.messages", desc = "Chat messages allowed per window.", min = 1, max = 50)
    public int rateMessages = 4;

    @Setting(path = "protection.rate-limit.seconds", desc = "Rate-limit window (seconds).", min = 1, max = 120)
    public int rateSeconds = 5;

    @Setting(path = "protection.duplicate-seconds", desc = "Block the same message again within this many seconds. 0 = off.", min = 0, max = 600)
    public int duplicateSeconds = 30;

    @Setting(path = "protection.caps.min-letters", desc = "Anti-caps only checks messages with at least this many letters.", min = 1, max = 100)
    public int capsMinLetters = 6;

    @Setting(path = "protection.caps.ratio", desc = "Lowercase messages with more than this share of capital letters (0.7 = 70%).", min = 0.1, max = 1.0)
    public double capsRatio = 0.7;

    @Setting(path = "protection.links.block", desc = "Block links to other websites (bypass: skyblock.chat.links).")
    public boolean blockLinks = true;

    @Setting(path = "protection.links.allowed", desc = "Domains that are always allowed (subdomains too).")
    public List<String> allowedDomains = List.of("skybornmc.net", "youtube.com", "youtu.be", "imgur.com", "minecraft.wiki");

    @Setting(path = "protection.block-ips", desc = "Block IP addresses (server adverts).")
    public boolean blockIps = true;

    @Setting(path = "protection.block-phone-numbers", desc = "Block phone numbers (personal info).")
    public boolean blockPhoneNumbers = true;

    @Setting(path = "protection.words.replace", desc = "Words replaced with *** (whole words, case-insensitive).")
    public List<String> replaceWords = List.of();

    @Setting(path = "protection.words.block", desc = "Words that block the whole message and alert staff (also catches 1→i, 3→e, 0→o, a.b.c).")
    public List<String> blockWords = List.of();

    @Setting(path = "protection.new-player-seconds", desc = "New players can chat after this many seconds on their first day. 0 = off.", min = 0, max = 3600)
    public int newPlayerSeconds = 30;

    // ---------------------------------------------------------------- extras

    @Setting(path = "mentions.enabled", desc = "Highlight your name (or @name) in chat and play a sound.")
    public boolean mentions = true;

    @Setting(path = "item-show.enabled", desc = "[item] or [i] in chat shows the held item with its tooltip.")
    public boolean itemShow = true;

    @Setting(path = "item-show.cooldown-seconds", desc = "Cooldown between [item] uses.", min = 0, max = 600)
    public int itemShowCooldown = 10;

    // ---------------------------------------------------------------- tab list

    @Setting(path = "tab.update-ticks", desc = "How often the tab header/footer and names are refreshed (ticks).", min = 10, max = 200)
    public int tabUpdateTicks = 40;

    @Setting(path = "tab.season", desc = "Season text in the tab header until the seasons feature provides it.")
    public String season = "Season 1 · Preseason";

    @Override
    public String fileName() {
        return "chat.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock chat, tab list and nametags.
                Formats are MiniMessage with style tags (<c:brand>, <grad:phoenix> ...). Rank prefixes come from LuckPerms
                (& codes like Essentials), name colors from the rank's 'color' meta. Texts: messages/chat.yml.
                Reload: /skyblock reload chat""";
    }

    ChatFilter.Rules rules() {
        return new ChatFilter.Rules(capsMinLetters, capsRatio, blockLinks, allowedDomains, blockIps, blockPhoneNumbers,
                replaceWords, blockWords);
    }
}
