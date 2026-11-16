package com.thelads.smp.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SlabBlock;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * slabForStep(block) walks every slab of the registry when the block is not a step, and the answer "no slab" is not remembered, so
 * building the step recipes asks the same 20,000 non-step blocks again and again (see {@link MixinBitsAndBalanceSlabStepIds}). Remember
 * the "not a step" answer for the current block registry size (a registry that grows can make a block a step).
 */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.common.recipe.EnhancedSlabRecipeLookup", remap = false)
public class MixinBitsAndBalanceNotAStep {
    @Unique private static final Set<Block> ladssmp$notSteps = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    @Unique private static volatile int ladssmp$registrySize = -1;

    @Unique private static Map<Identifier, Block> ladssmp$stepIdToSlab;
    @Unique private static int ladssmp$reverseSize = -1;

    /**
     * The scan that follows asks, for every slab of the registry in order, which step id it would get, and returns the first slab whose id
     * equals this step's id. Build that answer once as a map (same order, first slab wins) and look it up instead.
     */
    @Inject(method = "slabForStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/DefaultedRegistry;iterator()Ljava/util/Iterator;", ordinal = 0), cancellable = true, remap = false, require = 0)
    private static void ladssmp$reverseScan(Block stepBlock, CallbackInfoReturnable<Block> cir) {
        try {
            int size = BuiltInRegistries.BLOCK.size();
            if (ladssmp$stepIdToSlab == null || ladssmp$reverseSize != size) {
                MethodHandle idFor = MethodHandles.publicLookup().findStatic(Class.forName("org.onenonly.bitsandbalance.common.registry.StepDynamicRegistry", true, MixinBitsAndBalanceNotAStep.class.getClassLoader()),
                    "idForSlab", MethodType.methodType(Identifier.class, Identifier.class, String.class));
                Map<Identifier, Block> map = new HashMap<>();
                for (Block block : BuiltInRegistries.BLOCK) {
                    if (!(block instanceof SlabBlock)) continue;
                    Identifier slabId = BuiltInRegistries.BLOCK.getKey(block);
                    if (slabId == null) continue;
                    Identifier expected = (Identifier) idFor.invoke(slabId, "bitsandbalance");
                    map.putIfAbsent(expected, block);
                }
                ladssmp$stepIdToSlab = map;
                ladssmp$reverseSize = size;
            }
            Identifier target = BuiltInRegistries.BLOCK.getKey(stepBlock);
            cir.setReturnValue(target == null ? null : ladssmp$stepIdToSlab.get(target));
        } catch (Throwable t) {
            // anything unexpected: let the original scan run
        }
    }

    @Inject(method = "slabForStep", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$cachedNo(Block stepBlock, CallbackInfoReturnable<Block> cir) {
        int size = BuiltInRegistries.BLOCK.size();
        if (size != ladssmp$registrySize) { ladssmp$notSteps.clear(); ladssmp$registrySize = size; }
        if (stepBlock != null && ladssmp$notSteps.contains(stepBlock)) cir.setReturnValue(null);
    }

    @Inject(method = "slabForStep", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$storeNo(Block stepBlock, CallbackInfoReturnable<Block> cir) {
        if (stepBlock != null && cir.getReturnValue() == null && ladssmp$registrySize == BuiltInRegistries.BLOCK.size()) ladssmp$notSteps.add(stepBlock);
    }
}
