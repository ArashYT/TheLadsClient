package com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.thelads.core.features.alwayson.betterstatisticscreen.BetterStats;
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto.credits.CreditsSection;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BetterStatsRestAPI {
    public static CompletableFuture<List<CreditsSection>> fetchBuiltInCreditsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream is = BetterStats.class.getResourceAsStream("/betterstats.credits.json")) {
                if (is == null) {
                    throw new IllegalStateException("Local resource betterstats.credits.json not found!");
                }
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                    Pair<List<CreditsSection>, ?> pair = CreditsSection.CODEC.listOf()
                            .decode(JsonOps.INSTANCE, json.get("sections"))
                            .getOrThrow();
                    return pair.getFirst();
                }
            } catch (Exception e) {
                BetterStats.LOGGER.error("Failed to parse local credits", e);
                return Collections.emptyList();
            }
        });
    }
}
