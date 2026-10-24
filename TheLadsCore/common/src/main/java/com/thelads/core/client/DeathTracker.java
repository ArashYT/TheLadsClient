package com.thelads.core.client;

public final class DeathTracker {
    private static final DeathTracker INSTANCE = new DeathTracker();
    public static DeathTracker get() { return INSTANCE; }

    private int deathCount;
    private int lastX, lastY, lastZ;
    private String lastDimension = "";
    private String lastCause = "";
    private boolean hasDied;

    private DeathTracker() {}

    public synchronized void onDeath(double x, double y, double z, String dimension, String cause) {
        deathCount++;
        lastX = (int) Math.floor(x);
        lastY = (int) Math.floor(y);
        lastZ = (int) Math.floor(z);
        lastDimension = dimension != null ? dimension : "";
        lastCause = cause != null ? cause : "Unknown";
        hasDied = true;
    }

    public synchronized int getDeathCount() { return deathCount; }
    public synchronized int getLastX() { return lastX; }
    public synchronized int getLastY() { return lastY; }
    public synchronized int getLastZ() { return lastZ; }
    public synchronized String getLastDimension() { return lastDimension; }
    public synchronized String getLastCause() { return lastCause; }
    public synchronized boolean hasDied() { return hasDied; }

    public synchronized void reset() {
        deathCount = 0;
        hasDied = false;
        lastCause = "";
    }
}
