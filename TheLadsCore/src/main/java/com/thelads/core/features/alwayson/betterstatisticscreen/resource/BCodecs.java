package com.thelads.core.features.alwayson.betterstatisticscreen.resource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.net.URI;

public final class BCodecs {
    public static final Codec<URI> URI = Codec.STRING.<URI>flatXmap(uri -> {
        try {
            return DataResult.success(java.net.URI.create(uri));
        }
        catch (Exception e) {
            return DataResult.error(() -> String.valueOf(e.getClass()) + ": " + e.getMessage());
        }
    }, uri -> {
        try {
            return DataResult.success(uri.toString());
        }
        catch (Exception e) {
            return DataResult.error(() -> String.valueOf(e.getClass()) + ": " + e.getMessage());
        }
    });
}
