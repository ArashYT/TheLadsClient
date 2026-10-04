package com.thelads.core.v1_8_9.mixin;

import java.util.Arrays;
import java.util.Collection;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The survival and creative inventories stay centred while the player has potion effects: vanilla moves guiLeft 80 pixels or so to
 * the right to make room for the effect list. The list is drawn where vanilla draws it, 124 pixels left of the inventory, and
 * where the window has no room for that (a narrow window, a large GUI scale) as a column of 32-pixel icons with the name and time
 * in a tooltip, or not at all when even that does not fit. It never covers the inventory.
 */
@Mixin(InventoryEffectRenderer.class)
public abstract class InventoryEffectRendererMixin extends GuiContainer {
    private static final int FULL = 124, COMPACT = 34, BOX = 32; // pixels left of the inventory the full list and the icon column need, and the icon box

    @Shadow private boolean hasActivePotionEffects;

    private InventoryEffectRendererMixin() { super(null); }

    @Inject(method = "updateActivePotionEffects", at = @At("RETURN"), require = 1)
    private void ladsCentred(CallbackInfo ci) {
        guiLeft = (width - xSize) / 2;
    }

    /** Vanilla's list when it fits; otherwise ladsCompact draws it. */
    @Inject(method = "drawActivePotionEffects", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsFits(CallbackInfo ci) {
        if (guiLeft < FULL) ci.cancel();
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), require = 1)
    private void ladsCompact(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!hasActivePotionEffects || guiLeft >= FULL || guiLeft < COMPACT) return;
        Collection<PotionEffect> effects = mc.thePlayer.getActivePotionEffects();
        int x = guiLeft - COMPACT, y = guiTop, step = effects.size() > 5 ? 132 / (effects.size() - 1) : 33;
        PotionEffect hovered = null;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        mc.getTextureManager().bindTexture(inventoryBackground);
        for (PotionEffect effect : effects) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (!potion.shouldRender(effect)) continue;
            drawTexturedModalRect(x, y, 0, 166, BOX - 4, BOX); // vanilla's box: its left part with the icon, and its right edge
            drawTexturedModalRect(x + BOX - 4, y, 116, 166, 4, BOX);
            if (potion.hasStatusIcon()) {
                int icon = potion.getStatusIconIndex();
                drawTexturedModalRect(x + 6, y + 7, icon % 8 * 18, 198 + icon / 8 * 18, 18, 18);
            }
            if (mouseX >= x && mouseX < x + BOX && mouseY >= y && mouseY < y + BOX) hovered = effect;
            y += step;
        }
        if (hovered == null) return;
        String name = I18n.format(Potion.potionTypes[hovered.getPotionID()].getName());
        if (hovered.getAmplifier() >= 1 && hovered.getAmplifier() <= 3) name += " " + I18n.format("enchantment.level." + (hovered.getAmplifier() + 1));
        drawHoveringText(Arrays.asList(name, Potion.getDurationString(hovered)), mouseX, mouseY);
    }
}
