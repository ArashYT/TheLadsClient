package com.thelads.core.client;

import java.util.function.LongSupplier;

/** Small client-only policies, independent of Minecraft and wall-clock changes. */
public final class ClientTools {
    private ClientTools() {}
    public static final Stopwatch STOPWATCH = new Stopwatch(System::nanoTime);
    public static String portal(String dimension, int x, int z) {
        return switch (dimension) {
            case "minecraft:overworld" -> "Nether: " + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8);
            case "minecraft:the_nether" -> "Overworld: " + (long)x * 8 + ", " + (long)z * 8;
            default -> "";
        };
    }
    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public static final class Stopwatch {
        private final LongSupplier clock;
        private long accumulated, started;
        private boolean running;
        public Stopwatch(LongSupplier clock) { this.clock = clock; }
        public void toggle() {
            if (running) accumulated += Math.max(0, clock.getAsLong() - started);
            else started = clock.getAsLong();
            running = !running;
        }
        public void reset() { accumulated = 0; started = clock.getAsLong(); }
        public long millis() { return (accumulated + (running ? Math.max(0, clock.getAsLong() - started) : 0)) / 1_000_000; }
        public boolean running() { return running; }
    }
    /** Warn once per transition; recovery rearms it, cooldown prevents equip/unequip spam. */
    public static final class Warning {
        private boolean previous;
        private long nextAllowed = Long.MIN_VALUE;
        public boolean update(boolean condition, long nowMillis, long cooldownMillis) {
            boolean fire = condition && !previous && nowMillis >= nextAllowed;
            previous = condition;
            if (fire) nextAllowed = nowMillis + cooldownMillis;
            return fire;
        }
        public void reset() { previous = false; nextAllowed = Long.MIN_VALUE; }
    }
    public static final class ParticleBudget {
        private long tick = Long.MIN_VALUE;
        private int used;
        public boolean allow(long currentTick, double distanceSquared, double radius, int limit) {
            if (tick != currentTick) { tick = currentTick; used = 0; }
            if (distanceSquared > radius * radius) return false;
            return used++ < limit;
        }
        public void reset() { tick = Long.MIN_VALUE; used = 0; }
    }
}
