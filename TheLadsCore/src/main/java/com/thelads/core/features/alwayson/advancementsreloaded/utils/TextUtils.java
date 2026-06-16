package com.thelads.core.features.alwayson.advancementsreloaded.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class TextUtils {
    private TextUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String toString(FormattedCharSequence charSequence) {
        if (charSequence == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        charSequence.accept((index, style, codepoint) -> {
            builder.appendCodePoint(codepoint);
            return true;
        });
        return builder.toString();
    }

    public static String toString(Component component) {
        if (component == null) {
            return "";
        }
        return TextUtils.toString(component.getVisualOrderText());
    }
}
