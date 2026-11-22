package org.spongepowered.asm.mixin;

import java.util.LinkedHashSet;
import java.util.Set;

/** FAKE with the two members the agent calls (sponge-mixin 0.8). One config, visited before the game starts. Test only. */
public final class Mixins {
    static final Set<Object> CONFIGS = new LinkedHashSet<>(Set.of("base.mixins.json"));
    static int visited = 1;

    public static Set<Object> getConfigs() { synchronized (CONFIGS) { return new LinkedHashSet<>(CONFIGS); } }
    public static int getUnvisitedCount() { synchronized (CONFIGS) { return CONFIGS.size() - visited; } }

    /** a mod adding a config while the game starts (Mixins.addConfiguration) */
    public static void add(String c) { synchronized (CONFIGS) { CONFIGS.add(c); } }
    /** what the real transformer does on its next transform: select (visit) and prepare every new config */
    public static void visitAll() { synchronized (CONFIGS) { visited = CONFIGS.size(); } }
}
