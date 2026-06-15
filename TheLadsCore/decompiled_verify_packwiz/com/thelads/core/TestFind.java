/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.player.AbstractClientPlayer
 */
package com.thelads.core;

import java.lang.reflect.Method;
import net.minecraft.client.player.AbstractClientPlayer;

public class TestFind {
    public static void run() {
        Method[] methods;
        for (Method m : methods = AbstractClientPlayer.class.getDeclaredMethods()) {
            if (!m.getName().equals("getSkin")) continue;
            System.out.println("Return type: " + m.getReturnType().getName());
        }
    }
}

