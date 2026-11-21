package com.thelads.core.v1_8_9.feature.packs.mixin;

import com.thelads.core.perf.FastPackScreen;
import java.util.List;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One log line per pack screen open for the fast pack screen (the icons decode when first drawn). */
@Mixin(GuiScreenResourcePacks.class)
public abstract class GuiScreenResourcePacksLogMixin189 {
    @Shadow private List<ResourcePackListEntry> availableResourcePacks;
    @Shadow private List<ResourcePackListEntry> selectedResourcePacks;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void lads$log(CallbackInfo ci) {
        if (!FastPackScreen.enabled()) return;
        int n = 0;
        for (ResourcePackListEntry e : availableResourcePacks) if (e instanceof ResourcePackListEntryFound) n++;
        for (ResourcePackListEntry e : selectedResourcePacks) if (e instanceof ResourcePackListEntryFound) n++;
        LogManager.getLogger("TheLadsCore").info(FastPackScreen.openLine(n));
    }
}
