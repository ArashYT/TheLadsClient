package com.thelads.core.v26_2.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The 3D/world half of the minigame API on 26.3: camera, fx, waypoint, telegraph, ghost. The state machines are the common layer's;
 * this installs the entity lookup and the reduce-flashes hook, registers the world submits, and only then declares the caps. Nothing
 * here runs per frame while no state is active: every renderer starts with an emptiness check on its common-layer state.
 */
public final class MinigameWorld26 {
    private MinigameWorld26() {}

    public static void register() {
        Hooks.entities = MinigameWorld26::position;
        Hooks.reduceFlashes = () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.options.screenEffectScale().get() <= 0;
        };
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            long now = Minigame.now();
            WaypointRender26.submitBeams(context, now);
            TelegraphRender26.submit(context, now);
            GhostRender26.submit(context, now);
        });
        LadsApiClient.capability(Caps.CAMERA);
        LadsApiClient.capability(Caps.FX);
        LadsApiClient.capability(Caps.WAYPOINT);
        LadsApiClient.capability(Caps.TELEGRAPH);
        LadsApiClient.capability(Caps.GHOST);
        LadsApiClient.capability("guiview"); // HUD "Lock View": the camera stands still on a server GUI page and we draw the cursor
    }

    /** A loaded entity's interpolated feet position (the client's own entity list only: nothing is loaded or learned), else null. */
    static double[] position(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || id == null) return null;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Entity e : level.entitiesForRendering()) {
            if (id.equals(e.getUUID())) {
                Vec3 p = e.getPosition(partial);
                return new double[] {p.x, p.y, p.z};
            }
        }
        return null;
    }
}
