/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
 *  net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
 *  net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.util.FormattedCharSequence
 *  net.minecraft.world.item.ItemStack
 *  org.joml.Vector2ic
 */
package dev.ultimatchamp.enhancedtooltips.tooltip;

import dev.ultimatchamp.enhancedtooltips.EnhancedTooltips;
import dev.ultimatchamp.enhancedtooltips.component.TooltipBackgroundComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.mixin.accessors.ClientTextTooltipAccessor;
import dev.ultimatchamp.enhancedtooltips.util.EnhancedTooltipsTextVisitor;
import dev.ultimatchamp.enhancedtooltips.util.MatricesUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;

public class EnhancedTooltipsDrawer {
    private static final int EDGE_SPACING = 32;
    private static final int PAGE_SPACING = 12;
    private static long startTime = -1L;
    private static ItemStack lastStack = ItemStack.EMPTY;

    private static int getMaxHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight() - 64;
    }

    private static int getMaxWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - 32;
    }

    public static void drawTooltip(GuiGraphicsExtractor context, Font textRenderer, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack currentStack) {
        if (components == null || components.isEmpty() || currentStack.isEmpty()) {
            startTime = -1L;
            lastStack = ItemStack.EMPTY;
        }
        if (components == null || components.isEmpty()) {
            return;
        }
        if (!currentStack.isEmpty()) {
            if (lastStack.isEmpty() || !ItemStack.matches((ItemStack)lastStack, (ItemStack)currentStack)) {
                startTime = System.nanoTime();
                lastStack = currentStack.copy();
            } else if (startTime == -1L) {
                startTime = System.nanoTime();
            }
        }
        TooltipBackgroundComponent backgroundComponent = EnhancedTooltipsDrawer.getBackgroundComponent(components);
        if (components.size() > 1 && components.get(1).getWidth(textRenderer) == 0) {
            components.remove(1);
        }
        if (EnhancedTooltipsConfig.load().general.removeAllSpacing) {
            components.removeIf(component -> component.getHeight(textRenderer) == 0 || component.getWidth(textRenderer) == 0);
        }
        MatricesUtil matrices = new MatricesUtil((Object)context.pose());
        ArrayList<TooltipPage> pageList = new ArrayList<TooltipPage>();
        float scale = 1.0f;
        int maxWidth = (int)((float)EnhancedTooltipsDrawer.getMaxWidth() / scale);
        int totalWidth = 0;
        int pageHeight = -2;
        int maxHeight = (int)((float)EnhancedTooltipsDrawer.getMaxHeight() / scale);
        int spacing = components.size() > 1 ? 4 : 0;
        pageHeight += spacing;
        TooltipPage page = new TooltipPage();
        for (ClientTooltipComponent tooltipComponent : components) {
            int width = tooltipComponent.getWidth(textRenderer);
            int height = tooltipComponent.getHeight(textRenderer);
            if (width > maxWidth) {
                List<ClientTooltipComponent> wrappedComponents = EnhancedTooltipsDrawer.wrapComponent(tooltipComponent, textRenderer, maxWidth);
                Iterator<ClientTooltipComponent> iterator = wrappedComponents.iterator();
                while (iterator.hasNext()) {
                    ClientTooltipComponent wrappedComponent = iterator.next();
                    int wrappedWidth = wrappedComponent.getWidth(textRenderer);
                    int wrappedHeight = wrappedComponent.getHeight(textRenderer);
                    if (pageHeight + wrappedHeight > maxHeight) {
                        pageList.add(page);
                        totalWidth += page.width;
                        page = new TooltipPage();
                        pageHeight = -2;
                    }
                    page.components.add(wrappedComponent);
                    page.height = pageHeight += wrappedHeight;
                    page.width = Math.max(page.width, wrappedWidth);
                }
                continue;
            }
            if (pageHeight + height > maxHeight) {
                pageList.add(page);
                totalWidth += page.width;
                page = new TooltipPage();
                pageHeight = -2;
            }
            page.components.add(tooltipComponent);
            page.height = pageHeight += height;
            page.width = Math.max(page.width, width);
        }
        if (!page.components.isEmpty()) {
            pageList.add(page);
            totalWidth += page.width;
        }
        int scaledOffset = (int)(12.0f * scale) - 12;
        Vector2ic vector2ic = positioner.positionTooltip(context.guiWidth(), context.guiHeight(), x + scaledOffset, y - scaledOffset, (int)((float)totalWidth * scale), (int)((float)((TooltipPage)pageList.getFirst()).height * scale));
        int n = vector2ic.x();
        int o = vector2ic.y();
        for (TooltipPage tooltipPage : pageList) {
            tooltipPage.x = n;
            tooltipPage.y = pageList.size() > 1 ? o - 32 : o - 6;
            n += tooltipPage.width + 12;
        }
        matrices.pushMatrix();
        if (!currentStack.isEmpty() && EnhancedTooltipsConfig.load().popUpAnimation.enabled) {
            matrices.trans(x, y, 0.0f);
            float sec = EnhancedTooltipsConfig.load().popUpAnimation.time * 1000.0f;
            float elapsedTime = (float)(System.nanoTime() - startTime) / 1000000.0f / sec;
            float pop = 1.0f;
            if (elapsedTime < 0.5f) {
                pop = 1.0f + Math.abs((float)Math.sin((double)elapsedTime * Math.PI * 2.0)) * (EnhancedTooltipsConfig.load().popUpAnimation.magnitude / 10.0f * scale);
            }
            matrices.scal(pop, pop, 1.0f);
            matrices.trans(-x, -y, 0.0f);
        }
        matrices.scal(scale, scale, 1.0f);
        for (TooltipPage p : pageList) {
            if (pageList.getFirst() == p) {
                p.x = (int)((float)p.x / scale);
            }
            p.y = (int)((float)p.y / scale);
            if (backgroundComponent == null) {
                TooltipRenderUtil.extractTooltipBackground((GuiGraphicsExtractor)context, (int)p.x, (int)p.y, (int)p.width, (int)p.height, (Identifier)Identifier.withDefaultNamespace("tooltip/background"));
                continue;
            }
            try {
                backgroundComponent.render(context, p.x, p.y, p.width, p.height, 400, pageList.indexOf(p));
            }
            catch (Exception e) {
                EnhancedTooltips.LOGGER.error("[{}]", (Object)"EnhancedTooltips", (Object)e);
            }
        }
        matrices.trans(0.0f, 0.0f, 400.0f);
        for (TooltipPage p : pageList) {
            int cx = p.x;
            int cy = p.y;
            for (ClientTooltipComponent component2 : p.components) {
                try {
                    component2.extractText(context, textRenderer, cx, cy);
                    component2.extractImage(textRenderer, cx, cy, p.width, p.height, context);
                    cy += component2.getHeight(textRenderer);
                    if (p != pageList.getFirst() || component2 != p.components.getFirst() || components.size() <= 1) continue;
                    cy += spacing;
                }
                catch (Exception e) {
                    EnhancedTooltips.LOGGER.error("[{}]", (Object)"EnhancedTooltips", (Object)e);
                }
            }
        }
        matrices.popMatrix();
    }

    private static TooltipBackgroundComponent getBackgroundComponent(List<ClientTooltipComponent> components) {
        for (ClientTooltipComponent component : components) {
            if (!(component instanceof TooltipBackgroundComponent)) continue;
            TooltipBackgroundComponent bgComponent = (TooltipBackgroundComponent)component;
            return bgComponent;
        }
        return null;
    }

    private static List<ClientTooltipComponent> wrapComponent(ClientTooltipComponent component, Font textRenderer, int maxWidth) {
        ArrayList<ClientTooltipComponent> wrappedComponents = new ArrayList<ClientTooltipComponent>();
        if (component instanceof ClientTextTooltipAccessor) {
            ClientTextTooltipAccessor orderedTextTooltipComponent = (ClientTextTooltipAccessor)component;
            Component text = EnhancedTooltipsTextVisitor.get(orderedTextTooltipComponent.getText());
            List<FormattedCharSequence> lines = textRenderer.split((FormattedText)text, maxWidth);
            for (FormattedCharSequence line : lines) {
                wrappedComponents.add(ClientTooltipComponent.create(line));
            }
        } else {
            wrappedComponents.add(component);
        }
        return wrappedComponents;
    }

    private static class TooltipPage {
        private int x;
        private int y;
        private int width;
        private int height;
        private final List<ClientTooltipComponent> components;

        private TooltipPage() {
            this(0, 0, 0, 0, new ArrayList<ClientTooltipComponent>());
        }

        private TooltipPage(int x, int y, int width, int height, List<ClientTooltipComponent> components) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.components = components;
        }
    }
}

