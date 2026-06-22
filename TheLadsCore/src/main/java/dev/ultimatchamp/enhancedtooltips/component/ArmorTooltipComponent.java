/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.world.entity.EquipmentSlot$Type
 *  net.minecraft.world.entity.ai.attributes.Attributes
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.component.ItemAttributeModifiers
 *  net.minecraft.world.item.component.ItemAttributeModifiers$Entry
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.ModelViewerTooltipComponent;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.NotNull;

public record ArmorTooltipComponent(ItemStack stack) implements EnhancedTooltipsTooltipComponent
{
    @Override
    public int height() {
        Optional<ItemAttributeModifiers.Entry> opt;
        ItemAttributeModifiers c;
        int height = 0;
        if (ModelViewerTooltipComponent.getEquipmentSlot(this.stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR && (c = (ItemAttributeModifiers)this.stack.get(DataComponents.ATTRIBUTE_MODIFIERS)) != null && (opt = c.modifiers().stream().filter(i -> i.attribute().is(Attributes.ARMOR)).findAny()).isPresent() && opt.get().modifier().amount() > 0.0) {
            height = 9;
        }
        return height;
    }

    public int getWidth(@NotNull Font textRenderer) {
        ItemAttributeModifiers c;
        int width = 0;
        if (ModelViewerTooltipComponent.getEquipmentSlot(this.stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR && (c = (ItemAttributeModifiers)this.stack.get(DataComponents.ATTRIBUTE_MODIFIERS)) != null) {
            Optional<ItemAttributeModifiers.Entry> opt = c.modifiers().stream().filter(i -> i.attribute().is(Attributes.ARMOR)).findAny();
            if (opt.isEmpty() || opt.get().modifier().amount() < 0.0) {
                return 0;
            }
            int prot = (int)opt.get().modifier().amount();
            width += prot / 2 * 9;
        }
        return width;
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        ItemAttributeModifiers c;
        if (ModelViewerTooltipComponent.getEquipmentSlot(this.stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR && (c = (ItemAttributeModifiers)this.stack.get(DataComponents.ATTRIBUTE_MODIFIERS)) != null) {
            Optional<ItemAttributeModifiers.Entry> opt = c.modifiers().stream().filter(i -> i.attribute().is(Attributes.ARMOR)).findAny();
            if (opt.isEmpty()) {
                return;
            }
            int prot = (int)opt.get().modifier().amount();
            for (int j = 0; j < prot / 2; ++j) {
                Identifier identifier = Identifier.withDefaultNamespace("hud/armor_full");
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, x + j * 9, y, 9, 9);
            }
            if (prot % 2 == 1) {
                Identifier identifier = Identifier.withDefaultNamespace("hud/armor_half");
                int n = x + prot / 2 * 9;
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, n, y, 9, 9);
            }
        }
    }
}

