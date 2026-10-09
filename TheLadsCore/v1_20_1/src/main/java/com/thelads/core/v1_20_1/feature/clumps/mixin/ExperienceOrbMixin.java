// Adapted from Clumps 26.2.1 (Lads 26.3 copy), ported to Minecraft 1.20.1 by The Lads, Copyright (c) 2021 Jaredlll08, MIT.
// Source 6c4d4e4bee4c0d9f7cff5de802508e27914aadca; corresponding source in META-INF/lads-sources/clumps.
package com.thelads.core.v1_20_1.feature.clumps.mixin;

import com.thelads.core.v1_20_1.feature.clumps.ClumpedOrb;
import com.thelads.core.v1_20_1.feature.clumps.NativeClumps1201;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1's orb keeps its XP in a private int value and a stack count, joins only equal values (and only orbs whose ids share a
 * group of 40) and has no setter; a Clumps orb instead keeps the original denominations and sets value to their sum with a
 * count of one. Every hook is a no-op away from the integrated server (see NativeClumps1201.owns).
 */
@Mixin(value = ExperienceOrb.class, priority = 1001)
public abstract class ExperienceOrbMixin extends Entity implements ClumpedOrb {
    @Shadow private int count;
    @Shadow private int age;
    @Shadow private int value;
    @Shadow public abstract int getValue();
    @Unique private Map<Integer, Integer> ladsClumps$values;

    protected ExperienceOrbMixin(EntityType<?> type, Level level) { super(type, level); }

    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("HEAD"), cancellable = true)
    private void ladsClumps$eligible(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
        if (!NativeClumps1201.owns(level())) return;
        if (NativeClumps1201.enabled(level()))
            cir.setReturnValue(other.isAlive() && !is(other) && other.level() == level()
                    && ladsClumps$total(this) + ladsClumps$total(other) <= Integer.MAX_VALUE);
        else if (ladsClumps$managed() || ((ClumpedOrb) other).ladsClumps$managed()) cir.setReturnValue(false);
    }

    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void ladsClumps$eligibleAward(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (!NativeClumps1201.owns(orb.level())) return;
        if (NativeClumps1201.enabled(orb.level())) cir.setReturnValue(orb.isAlive() && value >= 0 && ladsClumps$total(orb) + value <= Integer.MAX_VALUE);
        else if (((ClumpedOrb) orb).ladsClumps$managed()) cir.setReturnValue(false);
    }

    @Inject(method = "merge", at = @At("HEAD"), cancellable = true)
    private void ladsClumps$onMerge(ExperienceOrb other, CallbackInfo ci) {
        if (!NativeClumps1201.owns(level())) return;
        if (!NativeClumps1201.enabled(level())) {
            if (ladsClumps$managed() || ((ClumpedOrb) other).ladsClumps$managed()) ci.cancel();
            return;
        }
        ci.cancel();
        if (!other.isAlive() || is(other) || ladsClumps$total(this) + ladsClumps$total(other) > Integer.MAX_VALUE) return;
        Map<Integer, Integer> merged = new HashMap<>(ladsClumps$values());
        ((ClumpedOrb) other).ladsClumps$values().forEach((value, amount) -> merged.merge(value, amount, Math::addExact));
        ladsClumps$values(merged);
        age = Math.min(age, ((ExperienceOrbAccess) other).ladsClumps$age());
        other.discard();
    }

    @Inject(method = "tryMergeToExisting", at = @At("HEAD"), cancellable = true)
    private static void ladsClumps$award(ServerLevel level, Vec3 position, int value, CallbackInfoReturnable<Boolean> cir) {
        if (!NativeClumps1201.enabled(level)) return;
        List<ExperienceOrb> nearby = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), AABB.ofSize(position, 1, 1, 1),
                orb -> orb.isAlive() && value >= 0 && ladsClumps$total(orb) + value <= Integer.MAX_VALUE);
        if (nearby.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        ExperienceOrb orb = nearby.get(0);
        Map<Integer, Integer> values = new HashMap<>(((ClumpedOrb) orb).ladsClumps$values());
        values.merge(value, 1, Math::addExact);
        ((ClumpedOrb) orb).ladsClumps$values(values);
        ((ExperienceOrbAccess) orb).ladsClumps$age(0);
        cir.setReturnValue(true);
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void ladsClumps$pickup(Player rawPlayer, CallbackInfo ci) {
        if (!(rawPlayer instanceof ServerPlayer player) || !NativeClumps1201.owns(level())
                || (!NativeClumps1201.enabled(level()) && !ladsClumps$managed())) return;
        ci.cancel();
        if (!isAlive() || player.isSpectator() || NativeClumps1201.pickupEvent.test(player, (ExperienceOrb) (Object) this)) return;
        boolean instant = NativeClumps1201.enabled(level());
        if (!instant && player.takeXpDelay != 0) return;
        player.takeXpDelay = instant ? 0 : 2;
        player.take(this, 1);
        long remainingXp = 0;
        Map<Integer, Integer> remaining = new HashMap<>(ladsClumps$values());
        for (Map.Entry<Integer, Integer> entry : ladsClumps$values().entrySet()) {
            int value = NativeClumps1201.value(player, entry.getKey());
            int amount = instant ? entry.getValue() : 1;
            for (int i = 0; i < amount; i++) {
                int left = NativeClumps1201.repair(player, value);
                // Fabric Clumps uses ratio 1; vanilla's Mending calculation is the same, run per original orb.
                if (left == value && value > 0) left = ladsClumps$mend(player, value, value);
                remainingXp += Math.max(0, left);
            }
            if (amount == entry.getValue()) remaining.remove(entry.getKey());
            else remaining.put(entry.getKey(), entry.getValue() - amount);
            if (!instant) break;
        }
        if (remainingXp > 0) player.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, remainingXp));
        if (remaining.isEmpty()) discard(); else ladsClumps$values(remaining);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ladsClumps$save(CompoundTag tag, CallbackInfo ci) {
        if (!NativeClumps1201.owns(level()) || !ladsClumps$managed()) return;
        CompoundTag map = new CompoundTag();
        ladsClumps$values.forEach((value, amount) -> map.putInt(value.toString(), amount));
        tag.put("clumpedMap", map);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ladsClumps$load(CompoundTag tag, CallbackInfo ci) {
        if (!NativeClumps1201.owns(level())) return;
        ladsClumps$values = null;
        if (!tag.contains("clumpedMap", Tag.TAG_COMPOUND)) return;
        CompoundTag map = tag.getCompound("clumpedMap");
        Map<Integer, Integer> values = new HashMap<>();
        try {
            for (String key : map.getAllKeys()) {
                if (!map.contains(key, Tag.TAG_ANY_NUMERIC)) throw new IllegalArgumentException("Invalid XP count");
                int denomination = Integer.parseInt(key), amount = map.getInt(key);
                if (denomination < 0 || amount <= 0) throw new IllegalArgumentException("Invalid XP count");
                values.put(denomination, amount);
            }
            if (values.isEmpty()) throw new IllegalArgumentException("Empty XP map");
            ladsClumps$values(values);
        } catch (RuntimeException invalid) {
            // Vanilla aggregate Value remains a safe fallback for malformed extension data.
            ladsClumps$values = null;
            count = 1;
        }
    }

    @Override public boolean ladsClumps$managed() { return ladsClumps$values != null; }

    @Override
    public Map<Integer, Integer> ladsClumps$values() {
        if (ladsClumps$values == null) {
            Map<Integer, Integer> values = new HashMap<>();
            values.put(Math.max(0, getValue()), Math.max(1, count));
            ladsClumps$values(values);
        }
        return Collections.unmodifiableMap(ladsClumps$values);
    }

    @Override
    public void ladsClumps$values(Map<Integer, Integer> values) {
        long total = 0;
        for (Map.Entry<Integer, Integer> entry : values.entrySet()) {
            if (entry.getKey() < 0 || entry.getValue() <= 0) throw new IllegalArgumentException("Invalid XP count");
            total = Math.addExact(total, (long) entry.getKey() * entry.getValue());
        }
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("XP total cannot fit one orb");
        ladsClumps$values = new HashMap<>(values);
        value = (int) total;
        // Value is already aggregated: a vanilla fallback must never multiply it by the original count.
        count = 1;
    }

    @Unique
    private static long ladsClumps$total(Entity entity) {
        ExperienceOrb orb = (ExperienceOrb) entity;
        return (long) Math.max(0, orb.getValue()) * Math.max(1, ((ExperienceOrbAccess) orb).ladsClumps$count());
    }

    /**
     * Vanilla's Mending step for one original orb of {@code unitValue}: repairs a random damaged Mending item, 2 durability per
     * XP, and returns the XP left over. Vanilla's own repairPlayerItems prices repairs by the orb's current value, which on a
     * clumped orb is the whole sum, so the per-orb value is passed in.
     */
    @Unique
    private static int ladsClumps$mend(Player player, int unitValue, int xp) {
        Map.Entry<EquipmentSlot, ItemStack> entry = EnchantmentHelper.getRandomItemWith(Enchantments.MENDING, player, ItemStack::isDamaged);
        if (entry == null) return xp;
        ItemStack stack = entry.getValue();
        int repaired = Math.min(unitValue * 2, stack.getDamageValue());
        stack.setDamageValue(stack.getDamageValue() - repaired);
        int left = xp - repaired / 2;
        return left > 0 ? ladsClumps$mend(player, unitValue, left) : 0;
    }
}
