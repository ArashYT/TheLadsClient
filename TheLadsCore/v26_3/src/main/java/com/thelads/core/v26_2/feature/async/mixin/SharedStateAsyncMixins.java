package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Small shared structures a mob's tick can reach; each mixin guards one entry point. */
final class SharedStateAsyncMixins {
    private SharedStateAsyncMixins() {}

    /** Advancement progress of a player (breeding, kills). */
    @Mixin(SimpleCriterionTrigger.class)
    abstract static class Criteria<T> {
        @WrapMethod(method = "trigger")
        private void lads$lockTrigger(ServerPlayer player, Predicate<T> matcher, Operation<Void> original) {
            AsyncTicking.locked(original, player, matcher);
        }
    }

    /** Loot tables (bartering, gifts, deaths) create their random sequence in a shared map on first use. */
    @Mixin(RandomSequences.class)
    abstract static class LootSequences {
        @WrapMethod(method = "get")
        private RandomSource lads$lockGet(Identifier key, long seed, Operation<RandomSource> original) {
            return AsyncTicking.locked(original, key, seed);
        }
    }

    /** Vibration listeners (sculk sensors) are registered per section and process events statefully. */
    @Mixin(GameEventDispatcher.class)
    abstract static class GameEvents {
        @WrapMethod(method = "post")
        private void lads$lockPost(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, Operation<Void> original) {
            AsyncTicking.locked(original, event, pos, context);
        }
    }

    /** An entity section builds its per-class lists the first time a class is asked for; two workers may ask one section. */
    @Mixin(ClassInstanceMultiMap.class)
    abstract static class SectionLists {
        @WrapMethod(method = "find")
        private <S> Collection<S> lads$syncFind(Class<S> type, Operation<Collection<S>> original) {
            if (!AsyncTicking.onWorker()) return original.call(type);
            synchronized (this) { return original.call(type); }
        }
    }
}
