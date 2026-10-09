// Clumps 26.2.1 integration (Lads 26.3 copy), ported to Minecraft 1.20.1; adapted under the MIT license from Jaredlll08.
package com.thelads.core.v1_20_1.feature.clumps;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.feature.clumps.api.events.ClumpsEvents;
import com.thelads.core.v1_20_1.feature.clumps.api.events.RepairEvent;
import com.thelads.core.v1_20_1.feature.clumps.api.events.ValueEvent;
import com.thelads.core.v1_20_1.perf.PerfOptions1201;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native Lads Clumps for 1.20.1: the engine merges XP orbs on the integrated server (singleplayer and LAN host) only; a remote
 * server owns its own orbs. While a real Clumps jar is installed (plain Fabric jar, or the Forge jar under Connector) the Lads
 * module is a card for that jar and the mixins stand down (see {@link ClumpsMixinPlugin1201}).
 */
public final class NativeClumps1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Clumps");
    private static volatile boolean active, enabled;
    public static volatile BiPredicate<Player, ExperienceOrb> pickupEvent = (player, orb) -> false;

    private NativeClumps1201() {}

    /**
     * Settles who owns XP merging and registers the Lads "Clumps" module accordingly. Call it from the performance registration
     * AFTER ExternalModSettings1201.register(), which registers Clumps as external from a Fabric-only check.
     */
    public static void register() {
        if (ModPresence1201.loaded("clumps")) {
            ModuleSupport.registerExternal("Clumps", "Clumps", "clumps", true);
            LOGGER.info("Clumps: the Clumps jar is installed ({}), Lads stands down", ModPresence1201.where("clumps"));
            return;
        }
        if (active) return;
        active = true;
        refresh();
        ModuleSupport.registerBuiltIn("Clumps");
        ClientTickEvents.END_CLIENT_TICK.register(client -> refresh());
        NativeClumpsProbe1201.initialize();
        LOGGER.info("Clumps: Lads' own XP orb merging is active in singleplayer and LAN worlds");
    }

    public static void refresh() { enabled = active && PerfOptions1201.enabled("Clumps"); }
    public static boolean active() { return active; }

    /** True on the integrated server's levels only; a dedicated server and the client world are never ours. */
    public static boolean owns(Level level) { return active && level instanceof ServerLevel server && !server.getServer().isDedicatedServer(); }
    public static boolean enabled(Level level) { return enabled && owns(level); }

    public static int value(Player player, int amount) {
        ValueEvent event = new ValueEvent(player, amount);
        ClumpsEvents.VALUE_EVENT.invoker().handle(event);
        return Math.max(0, event.getValue());
    }

    public static int repair(Player player, int amount) {
        RepairEvent event = new RepairEvent(player, amount);
        ClumpsEvents.REPAIR_EVENT.invoker().handle(event);
        return Math.max(0, event.getValue());
    }
}
