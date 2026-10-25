package com.thelads.core.v1_8_9.qol.mixin;

import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiRepair.class)
public interface GuiRepairAccessor189 {
    @Accessor("nameField")
    GuiTextField lads$getNameField();
}
