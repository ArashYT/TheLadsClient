// Adapted from Clumps 26.2.1, Copyright (c) 2021 Jaredlll08, MIT.
// Source 6c4d4e4bee4c0d9f7cff5de802508e27914aadca; corresponding source in META-INF/lads-sources/clumps.
package com.thelads.core.v26_2.feature.clumps.mixin;

import com.thelads.core.v26_2.feature.clumps.ClumpedOrb;
import com.thelads.core.v26_2.feature.clumps.NativeClumps;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ExperienceOrb.class, priority = 1001)
public abstract class ExperienceOrbMixin extends Entity implements ClumpedOrb {
    @Shadow private int count;
    @Shadow private int age;
    @Shadow public abstract int getValue();
    @Shadow protected abstract void setValue(int value);
    @Shadow protected abstract int repairPlayerItems(ServerPlayer player, int value);
    @Unique private Map<Integer, Integer> ladsClumps$values;
    protected ExperienceOrbMixin(EntityType<?> type, Level level) { super(type, level); }

    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("HEAD"), cancellable = true)
    private void eligible(ExperienceOrb other, CallbackInfoReturnable<Boolean> cir) {
        if (!NativeClumps.owns(level())) return;
        if (NativeClumps.enabled(level()))
            cir.setReturnValue(other.isAlive() && !is(other) && other.level() == level()
                    && ladsClumps$total(this) + ladsClumps$total(other) <= Integer.MAX_VALUE);
        else if (ladsClumps$managed() || ((ClumpedOrb) other).ladsClumps$managed()) cir.setReturnValue(false);
    }
    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void eligible(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (!NativeClumps.owns(orb.level())) return;
        if (NativeClumps.enabled(orb.level())) cir.setReturnValue(orb.isAlive() && value >= 0 && ladsClumps$total(orb) + value <= Integer.MAX_VALUE);
        else if (((ClumpedOrb) orb).ladsClumps$managed()) cir.setReturnValue(false);
    }
    @Inject(method = "merge", at = @At("HEAD"), cancellable = true)
    private void merge(ExperienceOrb other, CallbackInfo ci) {
        if (!NativeClumps.owns(level())) return;
        if (!NativeClumps.enabled(level())) {
            if (ladsClumps$managed() || ((ClumpedOrb) other).ladsClumps$managed()) ci.cancel();
            return;
        }
        ci.cancel();
        if (!other.isAlive() || is(other) || ladsClumps$total(this) + ladsClumps$total(other) > Integer.MAX_VALUE) return;
        var merged = new HashMap<>(ladsClumps$values());
        ((ClumpedOrb) other).ladsClumps$values().forEach((value, amount) -> merged.merge(value, amount, Math::addExact));
        ladsClumps$values(merged);
        age = Math.min(age, ((ExperienceOrbAccess) other).ladsClumps$age());
        other.discard();
    }
    @Inject(method = "tryMergeToExisting", at = @At("HEAD"), cancellable = true)
    private static void award(ServerLevel level, Vec3 position, int value, CallbackInfoReturnable<Boolean> cir) {
        if (!NativeClumps.enabled(level)) return;
        var nearby = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), AABB.ofSize(position,1,1,1),
                orb -> orb.isAlive() && value >= 0 && ladsClumps$total(orb) + value <= Integer.MAX_VALUE);
        if (nearby.isEmpty()) { cir.setReturnValue(false); return; }
        var orb = nearby.getFirst(); var values = new HashMap<>(((ClumpedOrb) orb).ladsClumps$values());
        values.merge(value, 1, Math::addExact); ((ClumpedOrb) orb).ladsClumps$values(values);
        ((ExperienceOrbAccess) orb).ladsClumps$age(0); cir.setReturnValue(true);
    }
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void pickup(Player rawPlayer, CallbackInfo ci) {
        if (!(rawPlayer instanceof ServerPlayer player) || !NativeClumps.owns(level())
                || (!NativeClumps.enabled(level()) && !ladsClumps$managed())) return;
        ci.cancel();
        if (!isAlive() || player.isSpectator() || NativeClumps.pickupEvent.test(player, (ExperienceOrb) (Object) this)) return;
        boolean instant = NativeClumps.enabled(level());
        if (!instant && player.takeXpDelay != 0) return;
        player.takeXpDelay = instant ? 0 : 2;
        player.take(this, 1);
        long remainingXp = 0;
        var remaining = new HashMap<>(ladsClumps$values());
        for (var entry : ladsClumps$values().entrySet()) {
            int value = NativeClumps.value(player, entry.getKey());
            int amount = instant ? entry.getValue() : 1;
            for (int i = 0; i < amount; i++) {
                int left = NativeClumps.repair(player, value);
                // Fabric Clumps uses ratio 1; vanilla 26.2 performs the identical Mending calculation.
                if (left == value && value > 0) left = repairPlayerItems(player, value);
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
    private void save(ValueOutput output, CallbackInfo ci) {
        if (!NativeClumps.owns(level()) || !ladsClumps$managed()) return;
        var map = new CompoundTag(); ladsClumps$values.forEach((value, amount) -> map.putInt(value.toString(), amount));
        output.store("clumpedMap", CompoundTag.CODEC, map);
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void load(ValueInput input, CallbackInfo ci) {
        if (!NativeClumps.owns(level())) return;
        ladsClumps$values = null;
        input.read("clumpedMap", CompoundTag.CODEC).ifPresent(tag -> {
            var values = new HashMap<Integer,Integer>();
            try {
                for (String key : tag.keySet()) {
                    int value = Integer.parseInt(key), amount = tag.getInt(key).orElseThrow();
                    if (value < 0 || amount <= 0) throw new IllegalArgumentException("Invalid XP count");
                    values.put(value, amount);
                }
                ladsClumps$values(values);
            } catch (RuntimeException invalid) {
                // Vanilla aggregate Value remains a safe fallback for malformed extension data.
                ladsClumps$values = null; count = 1;
            }
        });
    }
    @Override public boolean ladsClumps$managed() { return ladsClumps$values != null; }
    @Override public Map<Integer,Integer> ladsClumps$values() {
        if (ladsClumps$values == null) {
            var values = new HashMap<Integer,Integer>(); values.put(Math.max(0,getValue()), Math.max(1,count));
            ladsClumps$values(values);
        }
        return Collections.unmodifiableMap(ladsClumps$values);
    }
    @Override public void ladsClumps$values(Map<Integer,Integer> values) {
        long total = 0;
        for (var entry : values.entrySet()) {
            if (entry.getKey() < 0 || entry.getValue() <= 0) throw new IllegalArgumentException("Invalid XP count");
            total = Math.addExact(total, (long) entry.getKey() * entry.getValue());
        }
        if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("XP total cannot fit one orb");
        ladsClumps$values = new HashMap<>(values); setValue((int) total);
        // Value is already aggregated: a vanilla fallback must never multiply it by the original count.
        count = 1;
    }
    @Unique private static long ladsClumps$total(Entity entity) {
        var orb = (ExperienceOrb) entity;
        return (long) Math.max(0,orb.getValue()) * Math.max(1,((ExperienceOrbAccess) orb).ladsClumps$count());
    }
}
