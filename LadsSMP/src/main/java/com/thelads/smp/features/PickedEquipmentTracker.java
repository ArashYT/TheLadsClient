package com.thelads.smp.features;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * Interface mixed into Mob for 'letmedespawn' to track equipment slots that were picked up or dispensed.
 */
public interface PickedEquipmentTracker {
    void ladssmp$markSlotPicked(EquipmentSlot slot);
    boolean ladssmp$isSlotPicked(EquipmentSlot slot);
    boolean ladssmp$hasPickedEquipment();
}
