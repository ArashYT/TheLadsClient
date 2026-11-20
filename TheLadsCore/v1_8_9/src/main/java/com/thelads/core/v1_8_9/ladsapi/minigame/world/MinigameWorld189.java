package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The 3D/world half of the minigame API on 1.8.9: camera, fx, waypoint, telegraph, ghost (PROTOCOL.md sections 11, 12, 14, 15, 17).
 * The state machines are the common layer's; this installs the entity lookup, hooks the render (EntityRenderer mixin for the camera
 * shot and the fx shader, Forge's RenderWorldLastEvent for decals, beams, markers and ghosts, its overlay events for the screen
 * fx), calls {@code Minigame.tick()} once per client tick, and only then declares the caps. Everything is visual: no packets, no
 * input. Nothing runs per frame while no state is active: every renderer starts with an emptiness check on its common-layer state.
 */
public final class MinigameWorld189 {
    private static float partial;

    private MinigameWorld189() {}

    public static void register() {
        Hooks.entities = MinigameWorld189::position;
        MinecraftForge.EVENT_BUS.register(new MinigameWorld189());
        LadsApiClient.capability(Caps.CAMERA);
        LadsApiClient.capability(Caps.FX);
        LadsApiClient.capability(Caps.WAYPOINT);
        LadsApiClient.capability(Caps.TELEGRAPH);
        LadsApiClient.capability(Caps.GHOST);
    }

    /** A loaded entity's interpolated feet position (the client's own entity list only: nothing is loaded or learned), else null. */
    @SuppressWarnings("unchecked")
    static double[] position(UUID id) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || id == null) return null;
        for (Entity e : (List<Entity>) mc.theWorld.loadedEntityList) {
            if (id.equals(e.getUniqueID())) {
                return new double[] {e.prevPosX + (e.posX - e.prevPosX) * partial, e.prevPosY + (e.posY - e.prevPosY) * partial,
                    e.prevPosZ + (e.posZ - e.prevPosZ) * partial};
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minigame.tick();
        GhostRender189.tick(Minecraft.getMinecraft(), Minigame.now());
    }

    @SubscribeEvent
    public void onWorldLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        partial = e.partialTicks;
        long now = Minigame.now();
        net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
        double vx = rm.viewerPosX, vy = rm.viewerPosY, vz = rm.viewerPosZ;
        GhostRender189.render(mc, now, e.partialTicks, vx, vy, vz);
        TelegraphRender189.render(now, vx, vy, vz);
        WaypointRender189.world(mc, now, vx, vy, vz);
    }

    /** With the HUD hidden by a shot everything but the overlays goes: they are drawn here and the rest cancelled. */
    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre e) {
        if (e.type != ElementType.ALL || !Minigame.camera.hidesHud(Minigame.now())) return;
        WorldOverlay189.draw(Minecraft.getMinecraft(), false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post e) {
        if (e.type == ElementType.ALL) WorldOverlay189.draw(Minecraft.getMinecraft(), true);
    }
}
