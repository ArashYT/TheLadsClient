package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.EntityCulling189;
import com.thelads.core.v1_8_9.feature.NametagCache189;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Entity Culling: the culling thread's verdict on each entity (EntityCulling189.Cullable). Nametag per-entity cache (NametagCache189). */
@Mixin(Entity.class)
public abstract class EntityCullMixin implements EntityCulling189.Cullable, NametagCache189 {
    private int ladsCullGen, ladsSeen;
    private int ladsNameTick = -1;
    private String ladsFormattedName;
    private String ladsRenamed;
    private int ladsNickGen = -1;

    @Override public int ladsCullGen() { return ladsCullGen; }
    @Override public void ladsCullGen(int gen) { ladsCullGen = gen; }
    @Override public int ladsSeen() { return ladsSeen; }
    @Override public void ladsSeen(int frame) { ladsSeen = frame; }

    @Override public int ladsNameTick() { return ladsNameTick; }
    @Override public void ladsNameTick(int tick) { ladsNameTick = tick; }
    @Override public String ladsFormattedName() { return ladsFormattedName; }
    @Override public void ladsFormattedName(String name) { ladsFormattedName = name; }
    @Override public String ladsRenamed() { return ladsRenamed; }
    @Override public void ladsRenamed(String renamed) { ladsRenamed = renamed; }
    @Override public int ladsNickGen() { return ladsNickGen; }
    @Override public void ladsNickGen(int gen) { ladsNickGen = gen; }
}
