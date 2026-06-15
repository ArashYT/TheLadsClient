/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.client.hud;

import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class Test2 {
    public static void main(String[] args) {
        for (Method m : GuiGraphicsExtractor.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}

