package com.thelads.core.v1_8_9.feature;

/**
 * Per-entity tick-based nametag cache, implemented directly on net.minecraft.entity.Entity
 * via EntityCullMixin to eliminate hashmap allocations and per-frame string formatting/renaming.
 */
public interface NametagCache189 {
    int ladsNameTick();
    void ladsNameTick(int tick);
    String ladsFormattedName();
    void ladsFormattedName(String name);
    String ladsRenamed();
    void ladsRenamed(String renamed);
    int ladsNickGen();
    void ladsNickGen(int gen);
}
