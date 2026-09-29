// Clumps 26.2.1 integration, adapted under the MIT license from Jaredlll08.
package com.thelads.core.v26_2.feature.clumps;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.clumps.api.events.*;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class NativeClumps {
    private static volatile boolean active, enabled;
    public static volatile BiPredicate<Player, ExperienceOrb> pickupEvent = (player, orb) -> false;
    public static void initialize() {
        if (active || FabricLoader.getInstance().isModLoaded("clumps")) return;
        active = true; refresh();
        ModuleSupport.registerBuiltIn("Clumps");
        ClientTickEvents.END_CLIENT_TICK.register(mc -> refresh());
        NativeClumpsProbe.initialize();
    }
    public static void refresh() { enabled = active && NativeQualityOfLife.enabled("Clumps"); }
    public static boolean active() { return active; }
    public static boolean owns(Level level) { return active && level instanceof ServerLevel server && !server.getServer().isDedicatedServer(); }
    public static boolean enabled(Level level) { return enabled && owns(level); }
    public static int value(Player player, int amount) {
        var event = new ValueEvent(player, amount); ClumpsEvents.VALUE_EVENT.invoker().handle(event);
        return Math.max(0, event.getValue());
    }
    public static int repair(Player player, int amount) {
        var event = new RepairEvent(player, amount); ClumpsEvents.REPAIR_EVENT.invoker().handle(event);
        return Math.max(0, event.getValue());
    }
}
