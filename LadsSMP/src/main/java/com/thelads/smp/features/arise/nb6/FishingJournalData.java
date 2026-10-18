package com.thelads.smp.features.arise.nb6;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;

/**
 * Persisted player record tracking discovered fish species, catch counts, and first catch timestamps.
 */
public final class FishingJournalData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int TOTAL_SPECIES = 20;

    private UUID playerId;
    private boolean receivedStartingJournal = false;
    private final Set<String> caughtFish = new LinkedHashSet<>();
    private final Map<String, Integer> catchCounts = new LinkedHashMap<>();
    private final Map<String, Long> firstCaughtTimestamps = new LinkedHashMap<>();

    public FishingJournalData() {}

    public FishingJournalData(UUID playerId) {
        this.playerId = playerId;
    }

    public boolean hasReceivedStartingJournal() {
        return receivedStartingJournal;
    }

    public void setReceivedStartingJournal(boolean received) {
        this.receivedStartingJournal = received;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public synchronized boolean recordCatch(String fishId) {
        if (fishId == null || fishId.isEmpty()) {
            return false;
        }
        catchCounts.put(fishId, catchCounts.getOrDefault(fishId, 0) + 1);
        boolean isFirstCatch = !caughtFish.contains(fishId);
        if (isFirstCatch) {
            caughtFish.add(fishId);
            firstCaughtTimestamps.put(fishId, System.currentTimeMillis());
        }
        return isFirstCatch;
    }

    public synchronized boolean hasCaught(String fishId) {
        return caughtFish.contains(fishId);
    }

    public synchronized int getCatchCount(String fishId) {
        return catchCounts.getOrDefault(fishId, 0);
    }

    public synchronized long getFirstCaughtTimestamp(String fishId) {
        return firstCaughtTimestamps.getOrDefault(fishId, 0L);
    }

    public synchronized Set<String> getCaughtFish() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(caughtFish));
    }

    public synchronized int getDiscoveredCount() {
        return caughtFish.size();
    }

    public synchronized float getCompletionPercentage() {
        return Math.min(100.0f, (caughtFish.size() / (float) TOTAL_SPECIES) * 100.0f);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static FishingJournalData fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new FishingJournalData();
        }
        return GSON.fromJson(json, FishingJournalData.class);
    }
}
