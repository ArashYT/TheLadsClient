/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package fuzs.resourcepackoverrides.common;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourcePackOverrides {
    public static final String MOD_ID = "resourcepackoverrides";
    public static final String MOD_NAME = "Resource Pack Overrides";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"Resource Pack Overrides");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath((String)MOD_ID, (String)path);
    }
}

