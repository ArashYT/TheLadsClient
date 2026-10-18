package com.thelads.smp.features.arise.nb3.targetdummy;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import com.thelads.smp.features.arise.nb3.AriseNb3Mod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * MmmMmmMmmMmm Target Dummy Entity:
 * Absorbs attacks without dying, displays floating damage numbers & rolling DPS,
 * animates wobble recoil, and scares nearby animals when equipped with a carved pumpkin.
 */
public class TargetDummyEntity extends ArmorStand {
    private final List<DamageRecord> damageHistory = new ArrayList<>();
    private float totalDamageDealt = 0.0f;
    private long lastHitTime = 0;

    public record DamageRecord(long timestampMs, float amount) {}

    public TargetDummyEntity(EntityType<? extends TargetDummyEntity> entityType, Level level) {
        super(entityType, level);
        this.setShowArms(true);
        this.setNoBasePlate(false);
    }

    public static EntityType<TargetDummyEntity> createEntityType() {
        return EntityType.Builder.of(TargetDummyEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                .sized(0.5f, 1.975f)
                .eyeHeight(1.6f)
                .clientTrackingRange(10)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(AriseNb3Mod.MOD_ID, "target_dummy")));
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (!AriseNb3Config.get().targetDummy.enabled) {
            return super.hurtServer(serverLevel, damageSource, amount);
        }

        // Shift + empty hand to dismantle and pick up dummy
        if (damageSource.getEntity() instanceof ServerPlayer player && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            this.kill(serverLevel, player);
            this.spawnAtLocation(serverLevel, new ItemStack(AriseNb3Mod.TARGET_DUMMY_ITEM));
            return true;
        }

        if (amount <= 0.0f) return false;

        long now = System.currentTimeMillis();
        this.lastHitTime = now;
        this.totalDamageDealt += amount;
        this.damageHistory.add(new DamageRecord(now, amount));
        pruneOldRecords(now);

        float currentDPS = calculateDPS(now);

        // Notify attacker with damage numbers & DPS
        if (damageSource.getEntity() instanceof ServerPlayer player && AriseNb3Config.get().targetDummy.damageNumbers) {
            String dpsText = String.format(java.util.Locale.ROOT, "%.1f", currentDPS);
            String dmgText = String.format(java.util.Locale.ROOT, "%.1f", amount);
            player.sendOverlayMessage(Component.literal("Hit: " + dmgText + " dmg | DPS: " + dpsText)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }

        // Scarecrow effect: if wearing carved pumpkin, scare nearby animals
        if (getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
            scareNearbyAnimals(serverLevel);
        }

        // Trigger hurt animation
        this.setRemainingFireTicks(0);
        this.setHealth(20.0f);
        return true;
    }

    private void pruneOldRecords(long now) {
        long cutoff = now - 5000L; // 5-second window
        Iterator<DamageRecord> it = damageHistory.iterator();
        while (it.hasNext()) {
            if (it.next().timestampMs() < cutoff) {
                it.remove();
            }
        }
    }

    public float calculateDPS(long now) {
        pruneOldRecords(now);
        if (damageHistory.isEmpty()) return 0.0f;
        float sum = 0.0f;
        long oldest = now;
        for (DamageRecord r : damageHistory) {
            sum += r.amount();
            if (r.timestampMs() < oldest) oldest = r.timestampMs();
        }
        double durationSeconds = Math.max(1.0, (now - oldest) / 1000.0);
        return (float) (sum / durationSeconds);
    }

    private void scareNearbyAnimals(ServerLevel level) {
        int radius = AriseNb3Config.get().targetDummy.scarecrowRadius;
        AABB box = this.getBoundingBox().inflate(radius);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box);
        for (Animal animal : animals) {
            animal.getNavigation().moveTo(
                    animal.getX() + (animal.getX() - this.getX()),
                    animal.getY(),
                    animal.getZ() + (animal.getZ() - this.getZ()),
                    1.25
            );
        }
    }

    public float getTotalDamageDealt() {
        return totalDamageDealt;
    }
}
