package com.thelads.core.modules;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.IntTextOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class KillBannerModule extends Module {
    public static final int BASE = 0, REAVER = 1, ROGUE = 2, CUSTOM = 3;
    public static final int RANDOM_OFF = 0, RANDOM_VARIANT = 1, RANDOM_SKIN = 2, RANDOM_CHOSEN = 3;

    private static String[] createStyleChoices() {
        KillBannerStyle[] styles = KillBannerStyle.values();
        String[] choices = new String[styles.length + 1];
        choices[0] = "Base";
        choices[1] = "Reaver";
        choices[2] = "Rogue";
        choices[3] = "Custom";
        for (int i = 3; i < styles.length; i++) {
            choices[i + 1] = styles[i].displayName;
        }
        return choices;
    }

    private static String[] createCustomChoices() {
        KillBannerStyle[] styles = KillBannerStyle.values();
        String[] choices = new String[styles.length];
        choices[0] = "Base";
        choices[1] = "Reaver";
        choices[2] = "Rogue";
        for (int i = 3; i < styles.length; i++) {
            choices[i] = styles[i].displayName;
        }
        return choices;
    }

    public static final int HEADSHOTS_HEAD_HITS = 0, HEADSHOTS_EVERY_KILL = 1;
    public final DropdownOption bannerStyle = new DropdownOption("Style", REAVER, createStyleChoices());
    public final DropdownOption reaverVariant = new DropdownOption("Reaver Variant", 0, "Base", "Red", "Black", "White");
    public final DropdownOption rogueVariant = new DropdownOption("Rogue Variant", 0, "Base", "Green", "Red", "Blue");
    /** Custom: the banner of one skin with the sound of another. */
    public final DropdownOption customVisual = new DropdownOption("Custom Banner", REAVER, createCustomChoices());
    public final DropdownOption customSound = new DropdownOption("Custom Sound", ROGUE, createCustomChoices());
    public final DropdownOption randomize = new DropdownOption("Randomize", RANDOM_OFF, "Off", "Variant", "Skin and variant", "Chosen");
    /** The Chosen randomizer's pool: "reaver:0,rogue:3,..." (skin id and variant index). */
    public final TextOption randomPool = new TextOption("Random Pool", "reaver:0,reaver:1,reaver:2,reaver:3,rogue:0,rogue:1,rogue:2,rogue:3");
    public final TextOption skinVariants = new TextOption("Skin Variants", "");
    public final BoolOption players = new BoolOption("Players", true);
    public final BoolOption mobs = new BoolOption("Mobs", false);
    public final BoolOption bosses = new BoolOption("Bosses", true);
    /** Headshots: "Head hits" (an arrow in the head that kills, or the crosshair on the head at the last hit) or every kill. */
    public final DropdownOption headshots = new DropdownOption("Headshots", HEADSHOTS_HEAD_HITS, "Head hits", "Every kill");
    /** The HEADSHOT banner on a headshot kill: the skin's box under the banner (a new name, so it starts on for everyone). */
    public final BoolOption headshotBanner = new BoolOption("Headshot Banner", true);
    /** The kill mark: the red X on the emblem and its red strobe as the kill lands. */
    public final BoolOption killMark = new BoolOption("Kill Mark", true);
    public final ColorOption textColor = new ColorOption("Text Color", false, 0xFFFF0000); // the Base style's label
    public final BoolOption sound = new BoolOption("Sound", true);
    public final SliderOption volume = new SliderOption("Volume", .6, 0, 1, .05);
    public final SliderOption duration = new SliderOption("Duration", 2, 1, 5, .25);
    public final SliderOption size = new SliderOption("Size", 100, 50, 150, 10);
    /** Seconds without a kill before the streak starts again at 1 (1.7.1 and older: a fixed 8 seconds, not a setting). */
    public final IntTextOption streakReset = new IntTextOption("Streak Reset (seconds)", 45, 1, 600);
    /** The streak never runs out: only your death, a new world or leaving the server starts it again. */
    public final BoolOption unlimitedStreak = new BoolOption("Unlimited Streak", false);
    private final Random random = new Random();
    private Pick last;

    /** What one kill shows and plays: {@code style} null is the Base banner, {@code soundStyle} null the plain chime. */
    public record Pick(KillBannerStyle style, int variant, KillBannerStyle soundStyle) {}

    public KillBannerModule() {
        super("KillBanner", "Valorant kill banners with animations, rings, pips and sounds, the moment you kill a player, mob or boss.");
        addOption(bannerStyle);
        addOption(reaverVariant);
        addOption(rogueVariant);
        addOption(customVisual);
        addOption(customSound);
        addOption(randomize);
        addOption(randomPool);
        addOption(skinVariants);
        addOption(players);
        addOption(mobs);
        addOption(bosses);
        addOption(headshots);
        addOption(headshotBanner);
        addOption(killMark);
        addOption(textColor);
        addOption(sound);
        addOption(volume);
        addOption(duration);
        addOption(size);
        addOption(streakReset);
        addOption(unlimitedStreak);
    }

    /** How long a streak waits for its next kill, in nanoseconds; negative: forever (Unlimited Streak). */
    public long streakWindow() {
        return unlimitedStreak.get() ? -1 : streakReset.get() * 1_000_000_000L;
    }

    /** Options the Kill Banner picker draws itself, so the settings list leaves them out (Text Color: only the old Base label used it). */
    public boolean pickerOption(Option option) {
        return option == bannerStyle || option == reaverVariant || option == rogueVariant || option == customVisual
            || option == customSound || option == randomize || option == randomPool || option == skinVariants
            || option == players || option == mobs || option == bosses || option == textColor;
    }

    public boolean counts(KillDetector.Kind kind) {
        return switch (kind) {
            case PLAYER -> players.get();
            case MOB -> mobs.get();
            case BOSS -> bosses.get();
        };
    }

    public static KillBannerStyle skin(int index) {
        if (index == BASE) return KillBannerStyle.DEFAULT;
        if (index == REAVER) return KillBannerStyle.REAVER;
        if (index == ROGUE) return KillBannerStyle.ROGUE;
        if (index == CUSTOM) return null;
        KillBannerStyle[] all = KillBannerStyle.values();
        int styleIdx = index - 1;
        return styleIdx >= 0 && styleIdx < all.length ? all[styleIdx] : KillBannerStyle.DEFAULT;
    }

    public static KillBannerStyle visualOrSoundSkin(int index) {
        KillBannerStyle[] all = KillBannerStyle.values();
        return index >= 0 && index < all.length ? all[index] : KillBannerStyle.DEFAULT;
    }

    public static int styleIndexOf(KillBannerStyle style) {
        if (style == KillBannerStyle.DEFAULT) return BASE;
        if (style == KillBannerStyle.REAVER) return REAVER;
        if (style == KillBannerStyle.ROGUE) return ROGUE;
        KillBannerStyle[] all = KillBannerStyle.values();
        for (int i = 3; i < all.length; i++) {
            if (all[i] == style) return i + 1;
        }
        return BASE;
    }

    public DropdownOption variantOf(KillBannerStyle style) {
        return style == KillBannerStyle.ROGUE ? rogueVariant : reaverVariant;
    }

    /** The other skins' variants, read from Skin Variants each time (a reset or a loaded config changes it). */
    private Map<KillBannerStyle, Integer> variants() {
        Map<KillBannerStyle, Integer> variants = new EnumMap<>(KillBannerStyle.class);
        for (String entry : skinVariants.getValue().split(",")) {
            int colon = entry.indexOf(':');
            KillBannerStyle s = colon > 0 ? KillBannerStyle.byId(entry.substring(0, colon)) : null;
            if (s == null) continue;
            try {
                variants.put(s, Math.max(0, Math.min(s.variantNames.length - 1, Integer.parseInt(entry.substring(colon + 1).trim()))));
            } catch (NumberFormatException ignored) {}
        }
        return variants;
    }

    public int getVariant(KillBannerStyle style) {
        if (style == null) return 0;
        if (style == KillBannerStyle.REAVER) return reaverVariant.getIndex();
        if (style == KillBannerStyle.ROGUE) return rogueVariant.getIndex();
        return variants().getOrDefault(style, 0);
    }

    public void setVariant(KillBannerStyle style, int v) {
        if (style == null) return;
        if (style == KillBannerStyle.REAVER) reaverVariant.setIndex(v);
        else if (style == KillBannerStyle.ROGUE) rogueVariant.setIndex(v);
        else {
            Map<KillBannerStyle, Integer> variants = variants();
            variants.put(style, v);
            List<String> list = new ArrayList<>();
            for (Map.Entry<KillBannerStyle, Integer> e : variants.entrySet()) list.add(e.getKey().id + ":" + e.getValue());
            skinVariants.setValue(String.join(",", list));
        }
    }

    /** The banner as set, without randomizing. */
    public Pick chosen() {
        int index = bannerStyle.getIndex();
        KillBannerStyle style = index == CUSTOM ? visualOrSoundSkin(customVisual.getIndex()) : skin(index);
        KillBannerStyle soundStyle = index == CUSTOM ? visualOrSoundSkin(customSound.getIndex()) : style;
        return pick(style, getVariant(style), soundStyle);
    }

    /** A pick with a skin folded into another (an older saved choice) resolved to that skin and the matching variant. */
    public static Pick pick(KillBannerStyle style, int variant, KillBannerStyle soundStyle) {
        if (style != null && style.mergedInto() != null) {
            variant += style.variantOffset();
            if (soundStyle == style) soundStyle = style.mergedInto();
            style = style.mergedInto();
        }
        if (soundStyle != null && soundStyle.mergedInto() != null) soundStyle = soundStyle.mergedInto();
        return new Pick(style, variant, soundStyle);
    }

    /** The banner for the next kill: the chosen one, or a random one (never the same twice in a row when there is a choice). */
    public Pick next() {
        Pick chosen = chosen();
        List<Pick> pool = new ArrayList<>();
        switch (randomize.getIndex()) {
            case RANDOM_VARIANT -> {
                if (chosen.style() != null) for (int v = 0; v < chosen.style().variantNames.length; v++)
                    pool.add(new Pick(chosen.style(), v, bannerStyle.getIndex() == CUSTOM ? chosen.soundStyle() : chosen.style()));
            }
            case RANDOM_SKIN -> {
                for (KillBannerStyle style : KillBannerStyle.shown())
                    for (int v = 0; v < style.variantNames.length; v++) pool.add(new Pick(style, v, style));
            }
            case RANDOM_CHOSEN -> {
                for (String entry : pool()) {
                    int colon = entry.indexOf(':');
                    if (colon <= 0) continue;
                    KillBannerStyle style = KillBannerStyle.byId(entry.substring(0, colon));
                    if (style == null) continue;
                    try {
                        pool.add(pick(style, Integer.parseInt(entry.substring(colon + 1)), style));
                    } catch (NumberFormatException ignored) {}
                }
            }
            default -> {}
        }
        if (pool.size() > 1 && last != null) pool.remove(last);
        last = pool.isEmpty() ? chosen : pool.get(random.nextInt(pool.size()));
        return last;
    }

    public Set<String> pool() {
        Set<String> entries = new LinkedHashSet<>();
        for (String entry : randomPool.getValue().split(",")) if (!entry.isBlank()) entries.add(entry.trim());
        return entries;
    }

    public void togglePool(KillBannerStyle style, int variant) {
        Set<String> entries = pool();
        String key = style.id + ":" + variant;
        if (!entries.remove(key)) entries.add(key);
        randomPool.setValue(String.join(",", entries));
    }
}
