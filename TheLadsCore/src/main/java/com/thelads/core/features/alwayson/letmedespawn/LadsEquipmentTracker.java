package com.thelads.core.features.alwayson.letmedespawn;

import net.minecraft.world.entity.EquipmentSlot;

public interface LadsEquipmentTracker {
    boolean lads$isSlotPicked(EquipmentSlot slot);
    void lads$markSlotAsPicked(EquipmentSlot slot, boolean picked);
    boolean lads$hasAnyPickedEquipment();
    void lads$setPersistenceRequired(boolean value);
}
