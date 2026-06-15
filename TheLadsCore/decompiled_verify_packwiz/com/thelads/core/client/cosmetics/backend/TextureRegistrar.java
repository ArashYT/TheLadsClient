/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.client.cosmetics.backend;

import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.Identifier;

public interface TextureRegistrar {
    public CompletableFuture<Identifier> register(byte[] var1);
}

