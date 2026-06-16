/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Localization {
    public static String translationKey(String path) {
        return "clientsort." + path;
    }

    public static String translationKey(String domain, String path) {
        return domain + ".clientsort." + path;
    }

    public static MutableComponent localized(String path, Object ... args) {
        return Component.translatable((String)Localization.translationKey(path), (Object[])args);
    }

    public static MutableComponent localized(String domain, String path, Object ... args) {
        return Component.translatable((String)Localization.translationKey(domain, path), (Object[])args);
    }
}
