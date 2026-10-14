package com.thelads.core.v1_20_1.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.Map;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Synchronizes DispenserBlock.registerBehavior and wraps DISPENSER_REGISTRY so parallel Forge 1.20.1
 * FMLCommonSetupEvent workers (Supplementaries, Quark, Create, Friends&Foes) cannot corrupt the backing
 * Object2ObjectOpenHashMap.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockSynchronizedMixin {
    @Shadow
    @Final
    @Mutable
    private static Map<Item, DispenseItemBehavior> DISPENSER_REGISTRY;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void lads$wrapDispenserRegistry(CallbackInfo ci) {
        if (DISPENSER_REGISTRY != null) {
            DISPENSER_REGISTRY = Collections.synchronizedMap(DISPENSER_REGISTRY);
        }
    }

    @WrapMethod(method = "registerBehavior")
    private static void lads$registerBehaviorSynchronized(
            ItemLike item, DispenseItemBehavior behavior, Operation<Void> original) {
        synchronized (DISPENSER_REGISTRY) {
            original.call(item, behavior);
        }
    }
}
