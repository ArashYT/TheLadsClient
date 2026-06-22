/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.util.FormattedCharSequence
 *  net.minecraft.util.FormattedCharSink
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.jetbrains.annotations.NotNull;

public class EnhancedTooltipsTextVisitor
implements FormattedCharSink {
    private final MutableComponent text = Component.empty();

    public boolean accept(int index, @NotNull Style style, int codePoint) {
        String car = new String(Character.toChars(codePoint));
        this.text.append((Component)Component.literal((String)car).setStyle(style));
        return true;
    }

    public Component getText() {
        return this.text;
    }

    public static Component get(FormattedCharSequence text) {
        EnhancedTooltipsTextVisitor visitor = new EnhancedTooltipsTextVisitor();
        text.accept((FormattedCharSink)visitor);
        return visitor.getText();
    }
}

