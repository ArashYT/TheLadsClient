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
        installForgePickupEvent();
        LOGGER.info("Clumps: Lads' own XP orb merging is active in singleplayer and LAN worlds");
    }

    /**
     * Under Forge (Connector) other mods listen to Forge's PlayerXpEvent.PickupXp; Lads' pickup replaces the vanilla method that
     * posts it, so it posts the event itself (reflection, no Forge compile dependency). A cancelled event means the pickup is vetoed.
     */
    private static void installForgePickupEvent() {
        if (!ModPresence1201.onForge()) return;
        try {
            Class<?> eventType = Class.forName("net.minecraftforge.event.entity.player.PlayerXpEvent$PickupXp");
            java.lang.reflect.Constructor<?> constructor = eventType.getConstructor(Player.class, ExperienceOrb.class);
            Object bus = Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            java.lang.reflect.Method post = bus.getClass().getMethod("post", Class.forName("net.minecraftforge.eventbus.api.Event"));
            post.setAccessible(true);
            pickupEvent = (player, orb) -> {
                try {
                    return Boolean.TRUE.equals(post.invoke(bus, constructor.newInstance(player, orb)));
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    return false;
                }
            };
            LOGGER.info("Clumps: Forge's XP pickup event is posted for orbs Lads picks up");
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            LOGGER.warn("Clumps: Forge's XP pickup event is not reachable; mods listening to it will not see Lads' pickups", unavailable);
        }
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
