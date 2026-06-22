/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.ultimatchamp.enhancedtooltips.mixin;

import dev.ultimatchamp.enhancedtooltips.mixin.accessors.ClientTextTooltipAccessor;
import dev.ultimatchamp.enhancedtooltips.tooltip.EnhancedTooltipsDrawer;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipComponentManager;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipHelper;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipItemStackCache;
import dev.ultimatchamp.enhancedtooltips.util.EnhancedTooltipsTextVisitor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GuiGraphicsExtractor.class})
public class DrawContextMixin {
    @Inject(method={"tooltip"}, at={@At(value="HEAD")}, cancellable=true)
    private void enhancedTooltips$drawTooltip(Font textRenderer, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, @Nullable Identifier texture, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedTooltips").isEnabled() &&
            !com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedToolbars").isEnabled()) {
            return;
        }
        Component cachedName;
        ClientTextTooltipAccessor ordered;
        Component name;
        ClientTooltipComponent clientTooltipComponent;
        boolean isEmpty;
        if (components == null || components.isEmpty()) {
            return;
        }
        ArrayList<ClientTooltipComponent> tooltipComponents = new ArrayList<ClientTooltipComponent>(components);
        ItemStack cacheItemStack = TooltipItemStackCache.getItemStack();
        boolean bl = isEmpty = cacheItemStack == null || cacheItemStack.isEmpty();
        if (isEmpty) {
            cacheItemStack = ItemStack.EMPTY;
        }
        if (!isEmpty && (clientTooltipComponent = components.getFirst()) instanceof ClientTextTooltipAccessor && !(name = EnhancedTooltipsTextVisitor.get((ordered = (ClientTextTooltipAccessor)clientTooltipComponent).getText())).equals((Object)(cachedName = EnhancedTooltipsTextVisitor.get(TooltipHelper.getDisplayName(cacheItemStack).getVisualOrderText())))) {
            cacheItemStack = ItemStack.EMPTY;
        }
        TooltipItemStackCache.saveItemStack(ItemStack.EMPTY);
        TooltipComponentManager.invoke(tooltipComponents, cacheItemStack);
        EnhancedTooltipsDrawer.drawTooltip((GuiGraphicsExtractor)((Object)this), textRenderer, tooltipComponents, x, y, positioner, cacheItemStack);
        ci.cancel();
    }
}

