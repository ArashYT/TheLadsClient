package com.thelads.core.client.hypixel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel MVP++ chat emotes expansion for players without MVP++ rank.
 * Shortcode to Unicode/text mappings based on official Hypixel MVP++ emotes:
 * Sources: https://hypixel.net/threads/2395666/ & https://hypixel.net/threads/5149301/
 */
public final class ChatEmotes {
    private static final Map<String, String> EMOTES = new LinkedHashMap<>();
    private static final Pattern HEART_PATTERN = Pattern.compile("(?<=\\s|^)<3(?=\\s|$)");
    private static final Pattern WAVE_PATTERN = Pattern.compile("(?<=\\s|^)o/(?=\\s|$)");

    static {
        EMOTES.put(":shrug:", "¯\\_(ツ)_/¯");
        EMOTES.put(":tableflip:", "(╯°□°）╯︵ ┻━┻");
        EMOTES.put(":flip:", "(╯°□°）╯︵ ┻━┻");
        EMOTES.put(":yes:", "✔");
        EMOTES.put(":no:", "✖");
        EMOTES.put(":heart:", "❤");
        EMOTES.put(":star:", "✮");
        EMOTES.put(":totem:", "◎_◎");
        EMOTES.put(":typing:", "✎...");
        EMOTES.put(":maths:", "√(π+x)=L");
        EMOTES.put(":snail:", "@'-'");
        EMOTES.put(":thinking:", "(0.o?)");
        EMOTES.put(":gimme:", "༼つ ◕_◕ ༽つ");
        EMOTES.put(":wizard:", "('-')⊃━☆ﾟ.*･｡ﾟ");
        EMOTES.put(":pvp:", "⚔");
        EMOTES.put(":peace:", "✌");
        EMOTES.put(":oof:", "OOF");
        EMOTES.put(":puffer:", "<('O')>");
        EMOTES.put(":snow:", "☃");
        EMOTES.put(":dog:", "(ᵔᴥᵔ)");
        EMOTES.put(":sloth:", "( ⬩ ⊝ ⬩ )");
        EMOTES.put(":dab:", "<o/");
        EMOTES.put(":cat:", "= ＾● ⋏ ●＾ =");
        EMOTES.put(":yey:", "ヽ (◕◡◕) ﾉ");
        EMOTES.put(":java:", "☕");
        EMOTES.put(":arrow:", "➜");
        EMOTES.put(":123:", "123");
        EMOTES.put(":cute:", "(✿ᴖ‿ᴖ)");
        EMOTES.put(":dj:", "ヽ(⌐■_■)ノ♬");
    }

    private ChatEmotes() {}

    /**
     * Replaces MVP++ style emote shortcodes with their Unicode/text representations.
     * Case-insensitive for shortcodes (e.g. :shrug: and :SHRUG: are both replaced).
     */
    public static String replaceEmotes(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (Map.Entry<String, String> entry : EMOTES.entrySet()) {
            String code = entry.getKey();
            if (result.toLowerCase(java.util.Locale.ROOT).contains(code)) {
                result = Pattern.compile(Pattern.quote(code), Pattern.CASE_INSENSITIVE)
                    .matcher(result).replaceAll(Matcher.quoteReplacement(entry.getValue()));
            }
        }
        if (result.contains("<3")) {
            result = HEART_PATTERN.matcher(result).replaceAll("❤");
        }
        if (result.contains("o/")) {
            result = WAVE_PATTERN.matcher(result).replaceAll("( ﾟ◡ﾟ)/");
        }
        return result;
    }

    public static Map<String, String> getEmotes() {
        return Collections.unmodifiableMap(EMOTES);
    }
}
