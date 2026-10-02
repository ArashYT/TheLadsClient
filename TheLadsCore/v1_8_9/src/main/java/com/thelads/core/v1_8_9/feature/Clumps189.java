package com.thelads.core.v1_8_9.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Clumps on 1.8.9, as 26.x NativeClumps: in singleplayer and hosted LAN worlds (the integrated server runs in this JVM; remote
 * servers control their own XP) a new XP orb joins an orb within half a block, orbs that drift that close merge once a second,
 * and a player collects every orb touched in a tick instead of one per 2 ticks. 1.8.9 has no Mending, so an orb is only its XP
 * value; a clump stays at most 32767, the short 1.8.9 saves and sends an orb's value as.
 */
public final class Clumps189 {
    /** Read on the server thread, refreshed on the client tick (26.x: NativeClumps.refresh). */
    private static volatile boolean enabled;

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) enabled = Options189.enabled("Clumps");
    }

    /** World.spawnEntityInWorld: the orb is not added. Orbs loaded with their chunk are already in it and always load. */
    @SubscribeEvent
    public void spawn(EntityJoinWorldEvent event) {
        if (!enabled || event.world.isRemote || !(event.entity instanceof EntityXPOrb) || event.entity.addedToChunk) return;
        EntityXPOrb orb = (EntityXPOrb) event.entity;
        for (EntityXPOrb other : nearby(event.world, orb)) {
            if (!fits(other, orb)) continue;
            other.xpValue += orb.xpValue;
            other.xpOrbAge = 0;
            resend(other);
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event) {
        World world = event.world;
        if (!enabled || event.phase != TickEvent.Phase.END || world.isRemote || world.getTotalWorldTime() % 20 != 0) return;
        List<EntityXPOrb> orbs = new ArrayList<EntityXPOrb>();
        for (Entity entity : world.loadedEntityList) if (entity instanceof EntityXPOrb) orbs.add((EntityXPOrb) entity);
        for (EntityXPOrb orb : orbs) {
            if (orb.isDead) continue;
            int value = orb.xpValue;
            for (EntityXPOrb other : nearby(world, orb)) {
                if (!fits(orb, other)) continue;
                orb.xpValue += other.xpValue;
                orb.xpOrbAge = Math.min(orb.xpOrbAge, other.xpOrbAge);
                other.setDead();
            }
            if (orb.xpValue != value) resend(orb);
        }
    }

    /** EntityXPOrb.onCollideWithPlayer without the 2-tick cooldown it sets: the next orb is collected in the same tick. */
    @SubscribeEvent
    public void pickup(PlayerPickupXpEvent event) {
        EntityPlayer player = event.entityPlayer;
        EntityXPOrb orb = event.orb;
        if (!enabled || player.worldObj.isRemote || orb.isDead) return;
        event.setCanceled(true);
        player.worldObj.playSoundAtEntity(player, "random.orb", 0.1f, 0.5f * ((orb.worldObj.rand.nextFloat() - orb.worldObj.rand.nextFloat()) * 0.7f + 1.8f));
        player.onItemPickup(orb, 1);
        player.addExperience(orb.xpValue);
        orb.setDead();
    }

    /** 1.8.9 sends an orb's value only when it spawns on a client: spawn it again there, so it shows the clump's size. */
    private static void resend(EntityXPOrb orb) {
        if (!(orb.worldObj instanceof WorldServer)) return;
        EntityTracker tracker = ((WorldServer) orb.worldObj).getEntityTracker();
        tracker.untrackEntity(orb);
        tracker.trackEntity(orb);
    }

    private static List<EntityXPOrb> nearby(World world, EntityXPOrb orb) {
        return world.getEntitiesWithinAABB(EntityXPOrb.class, orb.getEntityBoundingBox().expand(0.5, 0.5, 0.5));
    }

    private static boolean fits(EntityXPOrb into, EntityXPOrb orb) {
        return into != orb && !into.isDead && !orb.isDead && into.xpValue >= 0 && orb.xpValue >= 0 && into.xpValue + orb.xpValue <= Short.MAX_VALUE;
    }
}
