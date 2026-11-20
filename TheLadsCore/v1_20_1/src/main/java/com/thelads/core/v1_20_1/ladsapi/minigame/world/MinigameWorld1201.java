package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The 3D/world half of the minigame API on 1.20.1: camera, fx, waypoint, telegraph, ghost. The state machines are the common layer's;
 * this installs the entity lookup and the reduce-flashes hook, registers the world draws, and only then declares the caps. Nothing here
 * runs per frame while no state is active: every renderer starts with an emptiness check on its common-layer state.
 */
public final class MinigameWorld1201 {
    private static boolean registered;

    private MinigameWorld1201() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Hooks.entities = MinigameWorld1201::position;
        Hooks.reduceFlashes = () -> Minecraft.getInstance().options.screenEffectScale().get() <= 0;
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long now = Minigame.now();
            WaypointRender1201.drawBeams(context, now);
            TelegraphRender1201.draw(context, now);
            GhostRender1201.draw(context, now);
        });
        LadsApiClient.capability(Caps.CAMERA);
        LadsApiClient.capability(Caps.FX);
        LadsApiClient.capability(Caps.WAYPOINT);
        LadsApiClient.capability(Caps.TELEGRAPH);
        LadsApiClient.capability(Caps.GHOST);
    }

    /** A loaded entity's interpolated feet position (the client's own entity list only: nothing is loaded or learned), else null. */
    static double[] position(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || id == null) return null;
        float partial = mc.getFrameTime();
        for (Entity e : level.entitiesForRendering()) {
            if (id.equals(e.getUUID())) {
                Vec3 p = e.getPosition(partial);
                return new double[] {p.x, p.y, p.z};
            }
        }
        return null;
    }
}
