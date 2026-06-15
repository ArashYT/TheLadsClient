package com.thelads.core.client.cosmetics.backend;

import net.minecraft.resources.Identifier;
import java.util.concurrent.CompletableFuture;

public interface TextureRegistrar {
    CompletableFuture<Identifier> register(byte[] data);
}
