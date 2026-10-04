package com.thelads.core.v26_2.feature;

import com.mojang.authlib.GameProfile;
import com.thelads.core.modules.KillBannerModule;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.slf4j.LoggerFactory;

/**
 * Drives the real packet handlers with synthetic QA packets about client-only stand-ins (a zombie, a dragon and a remote
 * player that only this client knows); never claims a real kill.
 */
final class NativeKillBannerProbe {
    private static final int FIRST_ID = Integer.MAX_VALUE - 64;
    private NativeKillBannerProbe() {}

    static int run() {
        Minecraft minecraft = Minecraft.getInstance();
        KillBannerModule module = (KillBannerModule) NativeQualityOfLife.module("KillBanner");
        boolean enabledBefore = module.isEnabled(), soundBefore = module.sound.get();
        boolean playersBefore = module.players.get(), mobsBefore = module.mobs.get(), bossesBefore = module.bosses.get();
        int randomBefore = module.randomize.getIndex();
        long modifiedBefore = module.getLastModified();
        var level = minecraft.level;
        int passed = 0, next = FIRST_ID;
        try {
            module.setEnabled(true);
            module.sound.set(false);
            module.randomize.setIndex(KillBannerModule.RANDOM_OFF);
            module.players.set(true);
            module.mobs.set(true);
            module.bosses.set(true);
            NativeKillBanner.reset();
            NativeKillBanner.bindCurrent();

            Entity zombie = spawn(EntityTypes.ZOMBIE, next++);
            death(zombie);
            require(NativeKillBanner.timeline().age(System.nanoTime()) < 0, "a death the player had no part in is not a kill"); passed++;
            NativeKillBanner.attacked(zombie);
            death(zombie);
            require(NativeKillBanner.timeline().age(System.nanoTime()) >= 0 && NativeKillBanner.timeline().sequence() == 1,
                "the banner starts inside the death packet's handler (same frame)"); passed++;
            death(zombie);
            require(NativeKillBanner.timeline().sequence() == 1, "a repeated death event is one kill"); passed++;

            Entity other = spawn(EntityTypes.ZOMBIE, next++);
            NativeKillBanner.attacked(other);
            minecraft.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(other.getId(),
                level.damageSources().generic().typeHolder(), zombie.getId(), zombie.getId(), Optional.empty()));
            death(other);
            require(NativeKillBanner.timeline().sequence() == 1, "someone else's last hit takes the credit"); passed++;

            Entity mine = spawn(EntityTypes.ZOMBIE, next++);
            minecraft.getConnection().handleDamageEvent(new ClientboundDamageEventPacket(mine.getId(),
                level.damageSources().generic().typeHolder(), minecraft.player.getId(), minecraft.player.getId(), Optional.empty()));
            long before = System.nanoTime();
            death(mine);
            require(NativeKillBanner.timeline().sequence() == 2 && NativeKillBanner.timeline().age(System.nanoTime()) <= (System.nanoTime() - before) / 1e9,
                "a projectile-style hit (damage event naming the player) credits the kill; the animation restarts on it"); passed++;

            module.mobs.set(false);
            Entity skipped = spawn(EntityTypes.ZOMBIE, next++);
            NativeKillBanner.attacked(skipped);
            death(skipped);
            require(NativeKillBanner.timeline().sequence() == 2, "Mobs unticked: no banner for a mob"); passed++;

            Entity boss = spawn(EntityTypes.WITHER, next++);
            NativeKillBanner.attacked(boss);
            death(boss);
            require(NativeKillBanner.timeline().sequence() == 3, "a boss kill counts while Bosses is ticked"); passed++;

            RemotePlayer victim = new RemotePlayer(level, new GameProfile(UUID.randomUUID(), "LadsQAVictim"));
            victim.setId(next++);
            victim.setPos(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ() + 2);
            level.addEntity(victim);
            String me = minecraft.player.getGameProfile().name();
            NativeKillBanner.attacked(victim);
            chat("[MVP+] LadsQAVictim: gg " + me);
            chat("You were killed by LadsQAVictim.");
            require(NativeKillBanner.timeline().sequence() == 3, "player chat and the player's own death are not kills"); passed++;
            chat("LadsQAVictim was killed by " + me + ". FINAL KILL!");
            require(NativeKillBanner.timeline().sequence() == 4, "a plugin server's kill message is a player kill (Hypixel style)"); passed++;
            death(victim);
            require(NativeKillBanner.timeline().sequence() == 4, "the same kill's death event does not count twice"); passed++;

            require(minecraft.getResourceManager().getResource(Identifier.fromNamespaceAndPath("theladscore", "textures/gui/base_kill_banner.png")).isPresent(), "base_kill_banner resource loads"); passed++;
            passed += assets();
            LoggerFactory.getLogger("TheLadsCore").info("Lads kill banner probe END: {} passed, 0 failed (synthetic packets about client-only stand-ins; no real kill claimed)", passed);
            return passed;
        } finally {
            for (int id = FIRST_ID; id < next; id++) level.removeEntity(id, Entity.RemovalReason.DISCARDED);
            NativeKillBanner.reset();
            module.sound.set(soundBefore);
            module.players.set(playersBefore);
            module.mobs.set(mobsBefore);
            module.bosses.set(bossesBefore);
            module.randomize.setIndex(randomBefore);
            module.setEnabled(enabledBefore);
            module.setLastModified(modifiedBefore);
            if (passed > 0) NativeKillBannerPreview.schedule();
        }
    }

    /** Every skin's strips decode, and each kill count's sound is registered with its sample in the resources (shared copies included). */
    static int assets() {
        Minecraft minecraft = Minecraft.getInstance();
        int passed = 0;
        for (var style : com.thelads.core.client.killbanner.KillBannerStyle.values()) {
            for (int index = 1; index <= 5; index++) {
                var strip = style.strip(index);
                if (strip != null) { require(strip.frame(strip.introEnd).length == strip.width * strip.height * 4, style.id + " frames for " + index + " kills decode"); passed++; }
                Identifier sound = Identifier.fromNamespaceAndPath("theladscore", style.id + "_kill_" + Math.min(index, style.soundCount));
                var event = minecraft.getSoundManager().getSoundEvent(sound);
                require(event != null, sound + " registered"); passed++;
                Identifier sample = event.getSound(net.minecraft.util.RandomSource.create()).getPath();
                require(minecraft.getResourceManager().getResource(sample).isPresent(), sound + " sample " + sample + " available"); passed++;
            }
        }
        return passed;
    }

    private static Entity spawn(EntityType<?> type, int id) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = type.create(minecraft.level, EntitySpawnReason.LOAD);
        entity.setId(id);
        entity.setPos(minecraft.player.getX() + 2, minecraft.player.getY(), minecraft.player.getZ());
        minecraft.level.addEntity(entity);
        return entity;
    }

    private static void death(Entity entity) {
        Minecraft.getInstance().getConnection().handleEntityEvent(new ClientboundEntityEventPacket(entity, EntityEvent.DEATH));
    }

    private static void chat(String text) {
        Minecraft.getInstance().getConnection().handleSystemChat(new ClientboundSystemChatPacket(Component.literal(text), false));
    }

    private static void require(boolean value, String name) {
        if (!value) throw new IllegalStateException(name);
    }
}
