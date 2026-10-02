// Adapted from NBT Autocomplete 2.1 for Minecraft 1.21.11 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.nbtac.mixin.fields;

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(EntitySelector.class)
public interface EntitySelectorFields
{
	@Accessor("playerName") @Nullable String nbtac$getPlayerName();
	@Accessor("entityUUID") @Nullable UUID nbtac$getEntityUUID();
	@Accessor("type") EntityTypeTest<Entity, ?> nbtac$getType();
}
