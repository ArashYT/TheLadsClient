/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 */
package dev.tr7zw.notenoughanimations;

import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import net.fabricmc.api.ClientModInitializer;

public class NEAnimationsMod
extends NEAnimationsLoader
implements ClientModInitializer {
    public void onInitializeClient() {
        this.onEnable();
    }
}

