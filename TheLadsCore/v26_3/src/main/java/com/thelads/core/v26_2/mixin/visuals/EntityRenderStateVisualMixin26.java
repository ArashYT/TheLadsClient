package com.thelads.core.v26_2.mixin.visuals;

import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** What EntityRendererVisualMixin26 extracts for Mob Size, Loot Beams and Item Despawn, kept on the entity's render state. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateVisualMixin26 implements PvpVisuals26.Visual {
    @Unique private int ladsKind, ladsRarity, ladsAge;

    @Override public int lads$kind() { return ladsKind; }
    @Override public int lads$rarity() { return ladsRarity; }
    @Override public int lads$age() { return ladsAge; }
    @Override public void lads$visual(int kind, int rarity, int age) { ladsKind = kind; ladsRarity = rarity; ladsAge = age; }
}
