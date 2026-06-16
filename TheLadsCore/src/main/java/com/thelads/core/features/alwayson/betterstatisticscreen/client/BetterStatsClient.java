package com.thelads.core.features.alwayson.betterstatisticscreen.client;

import com.thelads.core.features.alwayson.betterstatisticscreen.BetterStats;

public class BetterStatsClient extends BetterStats {
    private static BetterStatsClient INSTANCE;

    public BetterStatsClient() {
        super();
        INSTANCE = this;
    }

    public static void init() {
        if (INSTANCE == null) {
            new BetterStatsClient();
        }
    }
}
