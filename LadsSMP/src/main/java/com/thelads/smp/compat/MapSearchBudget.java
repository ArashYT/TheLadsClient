package com.thelads.smp.compat;

/**
 * Time budget for explorer-map structure searches, which run on the server thread. Dungeons & Taverns charts asked for structures
 * that took 25-48 s to search for even at radius 16 (Lone Citadel, Illager Manor); past the budget the map stays blank, as when
 * vanilla finds nothing. A search that found its target past the budget skips the biome preview, which generates chunks too
 * (the map keeps its marker and fills in as players explore).
 */
public final class MapSearchBudget {
    public static final long BUDGET_MS = 3000;
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

    /** Thrown out of the search when the budget is spent; caught where the search started. */
    public static final class Timeout extends RuntimeException {
        public Timeout() { super(null, null, false, false); }
    }

    private MapSearchBudget() { }

    public static void start() { DEADLINE.set(System.currentTimeMillis() + BUDGET_MS); }

    public static void clear() { DEADLINE.remove(); }

    public static boolean spent() {
        Long deadline = DEADLINE.get();
        return deadline != null && System.currentTimeMillis() > deadline;
    }

    public static void check() {
        if (spent()) throw new Timeout();
    }
}
