package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.EntityCulling189;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Entity Culling: the culling thread's verdict on each entity (EntityCulling189.Cullable). */
@Mixin(Entity.class)
public abstract class EntityCullMixin implements EntityCulling189.Cullable {
    private int ladsCullGen, ladsSeen;

    @Override public int ladsCullGen() { return ladsCullGen; }
    @Override public void ladsCullGen(int gen) { ladsCullGen = gen; }
    @Override public int ladsSeen() { return ladsSeen; }
    @Override public void ladsSeen(int frame) { ladsSeen = frame; }
}
