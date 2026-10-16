package com.thelads.smp.features;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * Consolidates 'zombieimprovements': Leader zombie synced attachment, sound events, and damage calculation.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ZombieImprovementsManager {
    public static final String NAMESPACE = "zombieimprovements";

    public static final Identifier AMBIENT_ID = id("entity.zombie.leader.ambient");
    public static final Identifier HURT_ID = id("entity.zombie.leader.hurt");
    public static final Identifier DEATH_ID = id("entity.zombie.leader.death");

    public static final SoundEvent AMBIENT = SoundEvent.createVariableRangeEvent(AMBIENT_ID);
    public static final SoundEvent HURT = SoundEvent.createVariableRangeEvent(HURT_ID);
    public static final SoundEvent DEATH = SoundEvent.createVariableRangeEvent(DEATH_ID);

    private static volatile AttachmentType<Boolean> LEADER;

    private ZombieImprovementsManager() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static AttachmentType<Boolean> getLeaderAttachment() {
        AttachmentType<Boolean> local = LEADER;
        if (local == null) {
            synchronized (ZombieImprovementsManager.class) {
                local = LEADER;
                if (local == null) {
                    local = AttachmentRegistry.create(
                            id("leader"),
                            builder -> builder
                                    .initializer(() -> Boolean.FALSE)
                                    .persistent(Codec.BOOL)
                                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
                    );
                    LEADER = local;
                }
            }
        }
        return local;
    }

    public static void init() {
        getLeaderAttachment();
        Registry.register(BuiltInRegistries.SOUND_EVENT, AMBIENT_ID, AMBIENT);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HURT_ID, HURT);
        Registry.register(BuiltInRegistries.SOUND_EVENT, DEATH_ID, DEATH);
    }

    public static boolean isLeader(Entity entity) {
        if (entity == null) return false;
        return Boolean.TRUE.equals(entity.getAttachedOrSet(getLeaderAttachment(), Boolean.FALSE));
    }

    public static void setLeader(Entity entity) {
        if (entity != null) {
            entity.setAttached(getLeaderAttachment(), Boolean.TRUE);
        }
    }

    /**
     * Evaluates the default leader zombie bonus attack damage formula: r * 1.5 + 3.0
     */
    public static double computeLeaderDamageBonus(double r) {
        return r * 1.5 + 3.0;
    }
}
