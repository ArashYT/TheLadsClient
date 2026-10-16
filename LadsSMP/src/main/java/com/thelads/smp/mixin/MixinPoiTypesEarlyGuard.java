package com.thelads.smp.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/**
 * Custom Nether Portals hooks PoiTypes.hasPoi/forState and reads its horizontal portal block through an Architectury
 * RegistrySupplier, which throws "Registry Object not present" until that block is registered. Alex's Mobs queries POIs
 * from its own initializer, so when it initializes first the game crashes at startup. Until the block exists the
 * answer can only be vanilla's, so return that before the Custom Nether Portals hook runs.
 */
@Mixin(PoiTypes.class)
public class MixinPoiTypesEarlyGuard {
    @Shadow @Final private static Map<BlockState, Holder<PoiType>> TYPE_BY_STATE;

    @Unique private static final Identifier LADSSMP$PORTAL = Identifier.fromNamespaceAndPath("customnetherportals", "horizontal_nether_portal");
    @Unique private static boolean ladssmp$waiting = FabricLoader.getInstance().isModLoaded("customnetherportals");

    @Unique
    private static boolean ladssmp$portalMissing() {
        if (ladssmp$waiting && BuiltInRegistries.BLOCK.containsKey(LADSSMP$PORTAL)) ladssmp$waiting = false;
        return ladssmp$waiting;
    }

    @Inject(method = "hasPoi", at = @At("HEAD"), cancellable = true, order = 100)
    private static void ladssmp$hasPoiBeforePortalBlock(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (ladssmp$portalMissing()) cir.setReturnValue(TYPE_BY_STATE.containsKey(state));
    }

    @Inject(method = "forState", at = @At("HEAD"), cancellable = true, order = 100)
    private static void ladssmp$forStateBeforePortalBlock(BlockState state, CallbackInfoReturnable<Optional<Holder<PoiType>>> cir) {
        if (ladssmp$portalMissing()) cir.setReturnValue(Optional.ofNullable(TYPE_BY_STATE.get(state)));
    }
}
