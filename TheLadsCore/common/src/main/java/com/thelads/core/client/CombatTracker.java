package com.thelads.core.client;

public final class CombatTracker {
    private static final CombatTracker INSTANCE = new CombatTracker();
    public static CombatTracker get() { return INSTANCE; }

    private static final long CONFIRM_NANOS = 1_000_000_000L;
    private static final long RESET_NANOS = 2_000_000_000L;

    private int targetId = -1;
    private long attackedAt;
    private int combo;
    private long lastHitTime;
    private int hitsLanded;
    private int hitsTaken;
    private int airSwings;
    private int gapplesUsed;

    private CombatTracker() {}

    public synchronized void onAirSwing() {
        airSwings++;
    }

    public synchronized void onSwing() {
        airSwings++;
    }

    public synchronized void onAttack(int entityId, long now) {
        targetId = entityId;
        attackedAt = now;
    }

    public synchronized int onHurt(int entityId, boolean self, long now) {
        if (self) {
            hitsTaken++;
            combo = 0;
            return 0;
        }
        if (entityId == targetId && now - attackedAt <= CONFIRM_NANOS) {
            targetId = -1;
            combo = (combo > 0 && now - lastHitTime <= RESET_NANOS) ? combo + 1 : 1;
            lastHitTime = now;
            hitsLanded++;
            return combo;
        }
        return 0;
    }

    public synchronized void onGapple() {
        gapplesUsed++;
    }

    public synchronized int getCombo() {
        if (combo > 0 && System.nanoTime() - lastHitTime > RESET_NANOS) {
            combo = 0;
        }
        return combo;
    }

    public synchronized int getHitsLanded() { return hitsLanded; }
    public synchronized int getHitsTaken() { return hitsTaken; }
    public synchronized int getAirSwings() { return airSwings; }
    public synchronized int getSwings() { return hitsLanded + airSwings; }
    public synchronized int getGapplesUsed() { return gapplesUsed; }

    public synchronized float getAccuracy() {
        int total = hitsLanded + airSwings;
        return total > 0 ? ((float) hitsLanded * 100.0f) / total : 0.0f;
    }

    public synchronized void reset() {
        targetId = -1;
        combo = 0;
        hitsLanded = 0;
        hitsTaken = 0;
        airSwings = 0;
        gapplesUsed = 0;
    }
}
