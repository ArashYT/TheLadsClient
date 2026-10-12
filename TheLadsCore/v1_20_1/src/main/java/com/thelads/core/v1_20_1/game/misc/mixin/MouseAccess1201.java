package com.thelads.core.v1_20_1.game.misc.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA only (MiscQa1201): drives what a real mouse would, so a script can scroll in a container screen. */
@Mixin(MouseHandler.class)
public interface MouseAccess1201 {
    @Accessor("xpos") void lads$xpos(double x);
    @Accessor("ypos") void lads$ypos(double y);
    @Invoker("onScroll") void lads$onScroll(long window, double xOffset, double yOffset);
    /** A button press or release as GLFW reports it (ItemsQa1201: Mouse 4 and 5 in inventories). */
    @Invoker("onPress") void lads$onPress(long window, int button, int action, int mods);
    /** The button being pressed while its press is handled (QuarkBackButtonMixin). */
    @Accessor("activeButton") int lads$activeButton();
}
