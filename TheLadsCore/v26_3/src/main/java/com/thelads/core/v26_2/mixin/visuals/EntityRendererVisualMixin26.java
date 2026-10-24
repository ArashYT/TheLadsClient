package com.thelads.core.v26_2.mixin.visuals;

import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.modules.LootBeamsModule;
import com.thelads.core.v26_2.feature.PvpVisuals26;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x draws from render states, not entities: every renderer's extraction ends in this base method (subclasses call it first), so
 * the state gets what Mob Size (the mob's kind) and Loot Beams / Item Despawn (a dropped item's rarity and client age) need.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererVisualMixin26 {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("RETURN"))
    private void ladsVisual(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ItemEntity item = entity instanceof ItemEntity dropped ? dropped : null; // rarity and age only while their modules are on
        ((PvpVisuals26.Visual) state).lads$visual(PvpVisuals26.kind(entity), item != null && LootBeamsModule.active() ? item.getItem().getRarity().ordinal() : 0,
            item != null && ItemDespawnModule.active() ? item.getAge() : 0);
    }
}
