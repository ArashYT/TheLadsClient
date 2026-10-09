package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** FastQuitProbe189 (QA): the pause menu's own Save and Quit button, pressed as a click would. */
@Mixin(GuiScreen.class)
public interface GuiScreenFastQuitAccessor {
    @Accessor("buttonList") List<GuiButton> lads$buttons();
    @Invoker("actionPerformed") void lads$press(GuiButton button) throws java.io.IOException;
}
