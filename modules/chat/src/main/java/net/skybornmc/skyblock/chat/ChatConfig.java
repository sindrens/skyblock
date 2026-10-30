package net.skybornmc.skyblock.chat;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;
import java.util.Map;

/**
 * Typed settings of {@code chat.yml}: display formats (data, editable later in /staff → Display), chat protection,
 * mentions, item showing and the tab list.
 */
public final class ChatConfig extends ModuleConfig {

    // ---------------------------------------------------------------- formats (MiniMessage + parts)

    @Setting(path = "format.chat", desc = "Chat line. Parts: <title> <tag> <rank> <name> <message> (empty parts disappear). "
            + "Titles go before the name, tags after it: [MOD] Pouched (Phoenix) » hi")
    public String chatFormat = "<title><rank><name><tag> <c:muted>»</c:muted> <c:text><message>";

    @Setting(path = "format.tab", desc = "Tab list entry. Parts: <rank> <name> <title> <tag> <afk>. Default: [MOD] Pouched (Phoenix)")
    public String tabFormat = "<title><rank><name><tag><afk>";

    @Setting(path = "format.title-part", desc = "How a title looks when a player has one. <value> = the title. Empty = never shown.")
    public String titlePart = "<value> ";

    @Setting(path = "format.tab-title-part", desc = "Title in the tab list (before the rank). <value> = the title.")
    public String tabTitlePart = "<value> ";

    @Setting(path = "format.tag-part", desc = "Tag (from /tags) in chat, after the name. <value> = the tag, already in "
            + "( ) from cosmetics.yml -> tag.format. Empty = no tags in chat.")
    public String tagPart = " <value>";

    @Setting(path = "format.tab-tag-part", desc = "Tag in the tab list, after the name. <value> = the tag.")
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

    /** Owner answer 301: English-only starter list of mild swear words (replaced with ***), for the owner to approve. */
    static final List<String> STARTER_REPLACE = List.of("fuck", "fucks", "fucked", "fucking", "fucker", "motherfucker", "wtf",
            "stfu", "shit", "shits", "shitty", "bullshit", "bitch", "bitches", "asshole", "assholes", "bastard", "dickhead",
            "wanker", "twat", "prick", "cunt", "cunts", "pussy", "slut", "whore");

    /** Owner answer 301: English-only starter list of slurs and threats (message blocked, staff alerted), for approval. */
    static final List<String> STARTER_BLOCK = List.of("nigger", "niggers", "nigga", "niggas", "faggot", "faggots", "fag", "fags",
            "retard", "retards", "retarded", "tranny", "trannies", "kike", "kikes", "spic", "spics", "chink", "chinks",
            "gook", "gooks", "wetback", "wetbacks", "coon", "coons", "paki", "pakis", "dyke", "dykes", "kys");

    @Setting(path = "protection.words.replace", desc = "Words replaced with *** (whole words, case-insensitive). English only "
            + "(owner answer 301); the starter list waits for the owner's approval (docs/OPEN_QUESTIONS.md).")
    public List<String> replaceWords = STARTER_REPLACE;

    @Setting(path = "protection.words.block", desc = "Words that block the whole message and alert staff (also catches 1→i, 3→e, "
            + "0→o, a.b.c). English only (owner answer 301); starter list of slurs and threats, waiting for the owner's approval.")
    public List<String> blockWords = STARTER_BLOCK;

    @Setting(path = "protection.filter-signs-books-anvils", desc = "Owner answer 304: signs, book text and anvil names go through "
            + "the same filter (words replaced, blocked text refused). Warp names and nicknames are checked by their modules.")
    public boolean filterSurfaces = true;

    @Setting(path = "channels.respect-lock", desc = "Locked chat (/mutechat) also stops channel chat such as island chat (/is chat). "
            + "Off: the lock is about public chat only. Rate limit, duplicates and the filter always apply.")
    public boolean channelsRespectLock = false;

    @Setting(path = "channels.respect-slow-mode", desc = "Slow mode (/chat slow) also applies to channel chat such as island chat.")
    public boolean channelsRespectSlow = false;

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

    @Setting(path = "tab.tps-good", desc = "Staff TPS line: at or above this TPS it shows green (messages tab.tps.good).", min = 0, max = 20)
    public double tpsGood = 19.0;

    @Setting(path = "tab.tps-ok", desc = "Staff TPS line: at or above this TPS (and below tps-good) it shows orange; below it red.", min = 0, max = 20)
    public double tpsOk = 15.0;

    @Override
    public String fileName() {
        return "chat.yml";
    }

    /** Version 2 (owner answer 301): the English starter word lists fill the lists that were still empty. */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of(
                "protection.words.replace", ValueChange.of(List.of(), STARTER_REPLACE),
                "protection.words.block", ValueChange.of(List.of(), STARTER_BLOCK)));
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock chat, tab list and nametags.
                Formats are MiniMessage with style tags (<c:brand>, <grad:phoenix> ...). Rank prefixes come from LuckPerms
                (& codes like Essentials), name colors from the rank's 'color' meta. Texts: messages/chat.yml.
                Reload: /skyblock reload chat""";
    }

    /**
     * Formats whose default changed (UX1: titles before, tags after the name). A file that still has the old default
     * gets the new one; a value the owner edited is kept. path → {old default, new default}.
     */
    static final Map<String, String[]> CHANGED_DEFAULTS = Map.of(
            "format.chat", new String[]{"<title><rank><name> <c:muted>»</c:muted> <c:text><message>",
                    "<title><rank><name><tag> <c:muted>»</c:muted> <c:text><message>"},
            "format.tab", new String[]{"<rank><name><title><tag><afk>", "<title><rank><name><tag><afk>"},
            "format.tab-title-part", new String[]{" <value>", "<value> "},
            "format.tag-part", new String[]{"", " <value>"});

    /** The settings to move to their new default: those whose current value is still an old default. */
    static Map<String, String> upgrades(Map<String, String> current) {
        Map<String, String> out = new java.util.TreeMap<>();
        CHANGED_DEFAULTS.forEach((path, values) -> {
            if (values[0].equals(current.get(path))) {
                out.put(path, values[1]);
            }
        });
        return out;
    }

    /** Current values of the settings in {@link #CHANGED_DEFAULTS}. */
    Map<String, String> changedDefaultValues() {
        return Map.of("format.chat", chatFormat, "format.tab", tabFormat, "format.tab-title-part", tabTitlePart,
                "format.tag-part", tagPart);
    }

    ChatFilter.Rules rules() {
        return new ChatFilter.Rules(capsMinLetters, capsRatio, blockLinks, allowedDomains, blockIps, blockPhoneNumbers,
                replaceWords, blockWords);
    }
}
