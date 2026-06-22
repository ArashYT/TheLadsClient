/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  traben.entity_model_features.EMFAnimationApi
 */
package dev.ultimatchamp.enhancedtooltips.compat;

import traben.entity_model_features.EMFAnimationApi;

public class EMFCompat {
    public static final String KEY = "enhancedtooltips entity do not touch";

    public static void registerVanillaModelCondition() {
        try {
            EMFAnimationApi.registerVanillaModelCondition(entity -> entity.etf$getCustomName().getString().contains(KEY));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

