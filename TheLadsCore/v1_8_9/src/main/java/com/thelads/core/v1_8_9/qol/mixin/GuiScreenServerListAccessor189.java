package com.thelads.core.v1_8_9.qol.mixin;

import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Quick Reconnect: Direct Connect's address field, pinged once typing stops. */
@Mixin(GuiScreenServerList.class)
public interface GuiScreenServerListAccessor189 {
    @Accessor("field_146302_g") GuiTextField lads$address();
}
