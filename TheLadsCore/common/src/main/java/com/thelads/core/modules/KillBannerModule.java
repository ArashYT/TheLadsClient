package com.thelads.core.modules;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class KillBannerModule extends Module {
    public static final int BASE = 0, REAVER = 1, ROGUE = 2, CUSTOM = 3;
    public static final int RANDOM_OFF = 0, RANDOM_VARIANT = 1, RANDOM_SKIN = 2, RANDOM_CHOSEN = 3;
    public final DropdownOption bannerStyle = new DropdownOption("Style", REAVER, "Base", "Reaver", "Rogue", "Custom");
    public final DropdownOption reaverVariant = new DropdownOption("Reaver Variant", 0, "Base", "Red", "Black", "White");
    public final DropdownOption rogueVariant = new DropdownOption("Rogue Variant", 0, "Base", "Green", "Red", "Blue");
    /** Custom: the banner of one skin with the sound of another. */
    public final DropdownOption customVisual = new DropdownOption("Custom Banner", REAVER, "Base", "Reaver", "Rogue");
    public final DropdownOption customSound = new DropdownOption("Custom Sound", ROGUE, "Base", "Reaver", "Rogue");
    public final DropdownOption randomize = new DropdownOption("Randomize", RANDOM_OFF, "Off", "Variant", "Skin and variant", "Chosen");
    /** The Chosen randomizer's pool: "reaver:0,rogue:3,..." (skin id and variant index). */
    public final TextOption randomPool = new TextOption("Random Pool", "reaver:0,reaver:1,reaver:2,reaver:3,rogue:0,rogue:1,rogue:2,rogue:3");
    public final BoolOption players = new BoolOption("Players", true);
    public final BoolOption mobs = new BoolOption("Mobs", false);
    public final BoolOption bosses = new BoolOption("Bosses", true);
    // A new name: the old "Headshot Text" (on) stays behind in saved configs, so the label starts hidden for everyone.
    public final BoolOption headshotText = new BoolOption("HEADSHOT Text", false);
    public final ColorOption textColor = new ColorOption("Text Color", false, 0xFFFF0000); // the Base style's label
    public final BoolOption sound = new BoolOption("Sound", true);
    public final SliderOption volume = new SliderOption("Volume", .6, 0, 1, .05);
    public final SliderOption duration = new SliderOption("Duration", 2, 1, 5, .25);
    public final SliderOption size = new SliderOption("Size", 100, 50, 150, 10);
    private final Random random = new Random();
    private Pick last;

    /** What one kill shows and plays: {@code style} null is the Base banner, {@code soundStyle} null the plain chime. */
    public record Pick(KillBannerStyle style, int variant, KillBannerStyle soundStyle) {}

    public KillBannerModule() {
        super("KillBanner", "Valorant Reaver and Rogue kill banners, with their animations and sounds, the moment you kill a player, mob or boss.");
        addOption(bannerStyle);
        addOption(reaverVariant);
        addOption(rogueVariant);
        addOption(customVisual);
        addOption(customSound);
        addOption(randomize);
        addOption(randomPool);
        addOption(players);
        addOption(mobs);
        addOption(bosses);
        addOption(headshotText);
        addOption(textColor);
        addOption(sound);
        addOption(volume);
        addOption(duration);
        addOption(size);
    }

    /** Options the Kill Banner picker draws itself, so the settings list leaves them out. */
    public boolean pickerOption(Option option) {
        return option == bannerStyle || option == reaverVariant || option == rogueVariant || option == customVisual
            || option == customSound || option == randomize || option == randomPool || option == players || option == mobs || option == bosses;
    }

    public boolean counts(KillDetector.Kind kind) {
        return switch (kind) {
            case PLAYER -> players.get();
            case MOB -> mobs.get();
            case BOSS -> bosses.get();
        };
    }

    public static KillBannerStyle skin(int index) {
        return index == REAVER ? KillBannerStyle.REAVER : index == ROGUE ? KillBannerStyle.ROGUE : null;
    }

    public DropdownOption variantOf(KillBannerStyle style) { return style == KillBannerStyle.ROGUE ? rogueVariant : reaverVariant; }

    /** The banner as set, without randomizing. */
    public Pick chosen() {
        int index = bannerStyle.getIndex();
        int visual = index == CUSTOM ? customVisual.getIndex() : index;
        KillBannerStyle style = skin(visual);
        KillBannerStyle soundStyle = index == CUSTOM ? skin(customSound.getIndex()) : style;
        return new Pick(style, style == null ? 0 : variantOf(style).getIndex(), soundStyle);
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
                for (KillBannerStyle style : KillBannerStyle.values())
                    for (int v = 0; v < style.variantNames.length; v++) pool.add(new Pick(style, v, style));
            }
            case RANDOM_CHOSEN -> {
                for (String entry : pool()) {
                    KillBannerStyle style = entry.startsWith("rogue:") ? KillBannerStyle.ROGUE : entry.startsWith("reaver:") ? KillBannerStyle.REAVER : null;
                    if (style == null) continue;
                    try { pool.add(new Pick(style, Integer.parseInt(entry.substring(entry.indexOf(':') + 1)), style)); }
                    catch (NumberFormatException ignored) {}
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
