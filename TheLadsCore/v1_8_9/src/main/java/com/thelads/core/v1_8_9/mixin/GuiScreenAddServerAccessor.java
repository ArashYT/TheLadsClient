package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA (AddServerProbe189): the Add Server screen's two fields. */
@Mixin(GuiScreenAddServer.class)
public interface GuiScreenAddServerAccessor {
    @Accessor GuiTextField getServerNameField();
    @Accessor GuiTextField getServerIPField();
}
