package com.thelads.core.client.hud;

/**
 * Totem Pop Counter (Lunar's Totem Counter, Feather's Totem): how many totems of undying each player near you has used, from the entity
 * event the server already sends every client when a totem saves someone (id 35; the client plays its animation from it). The 1.20.1
 * and 26.3 packet listener mixins report pops and deaths (id 3); nothing is sent. Minecraft 1.8.9 has no totems.
 */
public final class TotemPops {
    static final int MAX = 16;
    private static final int[] ids = new int[MAX], counts = new int[MAX];
    private static final String[] names = new String[MAX];
    private static final long[] times = new long[MAX];
    private static int size;
    /** Counts every change of the list, so the widget rebuilds its text only then. */
    public static int changes;

    private TotemPops() {}

    /** A totem saved entity {@code id} (named {@code name}): its count, after this pop. */
    public static int pop(int id, String name, long millis) {
        int i = find(id);
        if (i < 0) {
            if (size == MAX) remove(oldest());
            i = size++;
            ids[i] = id;
            counts[i] = 0;
        }
        names[i] = name;
        times[i] = millis;
        changes++;
        return ++counts[i];
    }

    /** Entity {@code id} died: its count starts over. */
    public static void died(int id) {
        int i = find(id);
        if (i >= 0) remove(i);
    }

    public static void clear() {
        size = 0;
        changes++;
    }

    public static int size() {
        return size;
    }

    public static String name(int i) {
        return names[i];
    }

    public static int count(int i) {
        return counts[i];
    }

    public static long time(int i) {
        return times[i];
    }

    /** Forgets the players whose last pop is older than {@code keepMillis}. */
    public static void expire(long millis, long keepMillis) {
        for (int i = size - 1; i >= 0; i--) if (millis - times[i] > keepMillis) remove(i);
    }

    private static int find(int id) {
        for (int i = 0; i < size; i++) if (ids[i] == id) return i;
        return -1;
    }

    private static int oldest() {
        int at = 0;
        for (int i = 1; i < size; i++) if (times[i] < times[at]) at = i;
        return at;
    }

    private static void remove(int i) {
        changes++;
        size--;
        ids[i] = ids[size];
        counts[i] = counts[size];
        names[i] = names[size];
        times[i] = times[size];
        names[size] = null;
    }
}
