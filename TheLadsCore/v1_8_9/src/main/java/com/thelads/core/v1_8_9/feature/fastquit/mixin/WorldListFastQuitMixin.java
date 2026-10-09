package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import com.thelads.core.client.FastQuit;
import java.io.File;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.world.storage.SaveFormatComparator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world list marks a world saving in the background "Saving..." (drawn, never put in its name: joining passes the shown
 * name on, and 1.8.9 would rename the world to it).
 */
@Mixin(targets = "net.minecraft.client.gui.GuiSelectWorld$List")
public abstract class WorldListFastQuitMixin {
    @Shadow(remap = false) @Final GuiSelectWorld this$0;

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void lads$markSaving(int entry, int x, int y, int slotHeight, int mouseX, int mouseY, CallbackInfo ci) {
        List<SaveFormatComparator> worlds = ((GuiSelectWorldAccessor) this$0).lads$worlds();
        if (worlds == null || entry < 0 || entry >= worlds.size()) return;
        Minecraft mc = Minecraft.getMinecraft();
        File dir = new File(new File(mc.mcDataDir, "saves"), worlds.get(entry).getFileName());
        if (!FastQuit.saving(dir.toPath())) return;
        FontRenderer font = mc.fontRendererObj;
        String text = "Saving...";
        font.drawString(text, x + 215 - font.getStringWidth(text), y + 1, 0xFFFF55);
    }
}
