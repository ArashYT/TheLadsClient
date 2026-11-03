package com.thelads.core.v1_8_9.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA: a screen's buttons. */
@Mixin(GuiScreen.class)
public interface GuiScreenButtonsAccessor {
    @Accessor("buttonList") List<GuiButton> ladsButtons();
}
