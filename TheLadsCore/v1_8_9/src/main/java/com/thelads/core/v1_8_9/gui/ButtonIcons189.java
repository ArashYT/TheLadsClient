package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.title.ButtonIcons;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

/** The icon of a 1.8.9 button from its drawn text (ButtonIcons): English labels, and vanilla's translated into the game's language. */
public final class ButtonIcons189 {
    private static final Function<String, String> TRANSLATE = I18n::format;
    private ButtonIcons189() {}

    public static String of(String text) {
        return ButtonIcons.ofText(text, Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode(), TRANSLATE);
    }

    /** The icon of a "Caption: value" option button's text too (allocates the caption: ask once per text, not per frame). */
    public static String captioned(String text) {
        return ButtonIcons.ofCaptioned(text, Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode(), TRANSLATE);
    }
}
