/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Lists
 *  com.mojang.datafixers.util.Pair
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.Gui
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.core.Holder
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffectUtil
 *  net.minecraft.world.entity.ai.attributes.Attribute
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier
 *  net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.alchemy.PotionContents
 *  net.minecraft.world.item.component.ItemAttributeModifiers
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.NotNull;

public record PotionEffectTooltipComponent(ItemStack stack) implements EnhancedTooltipsTooltipComponent
{
    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    @Override
    public int height() {
        int height = 0;
        PotionContents c = (PotionContents)this.stack.get(DataComponents.POTION_CONTENTS);
        if (c != null) {
            ArrayList effects = new ArrayList();
            @NotNull ArrayList list = Lists.newArrayList();
            c.getAllEffects().forEach(i -> {
                effects.add(((MobEffect)i.getEffect().value()).getDisplayName());
                ((MobEffect)i.getEffect().value()).createModifiers(i.getAmplifier(), (attribute, modifier) -> list.add(new Pair(attribute, modifier)));
            });
            height += effects.size() * 10;
            if (effects.isEmpty()) {
                height += 10;
            }
            if (!list.isEmpty()) {
                height += 10;
            }
            height += list.size() * 10;
        }
        return height;
    }

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    public int getWidth(@NotNull Font textRenderer) {
        int width = 0;
        PotionContents c = (PotionContents)this.stack.get(DataComponents.POTION_CONTENTS);
        if (c != null) {
            List<Pair<Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier>> list = Lists.newArrayList();
            boolean isEmpty = true;
            for (MobEffectInstance effect : c.getAllEffects()) {
                isEmpty = false;
                width = Math.max(12 + textRenderer.width((FormattedText)this.getEffectText(effect, list::add)), width);
            }
            if (isEmpty) {
                width = Math.max(textRenderer.width((FormattedText)Component.translatable((String)"effect.none").withStyle(ChatFormatting.GRAY)), width);
            }
            if (!list.isEmpty()) {
                width = Math.max(textRenderer.width((FormattedText)Component.translatable((String)"potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE)), width);
            }
            for (Pair<Holder<Attribute>, AttributeModifier> pair : list) {
                Component modifierText = PotionEffectTooltipComponent.getModifierText(pair);
                if (modifierText == null) continue;
                width = Math.max(3 + textRenderer.width((FormattedText)modifierText), width);
            }
        }
        return width;
    }

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        PotionContents c = (PotionContents)this.stack.get(DataComponents.POTION_CONTENTS);
        if (c == null) {
            return;
        }
        Objects.requireNonNull(textRenderer);
        int lineY = y - (9 + 1);
        List<Pair<Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier>> list = Lists.newArrayList();
        boolean isEmpty = true;
        for (MobEffectInstance effect : c.getAllEffects()) {
            isEmpty = false;
            Objects.requireNonNull(textRenderer);
            lineY += 9 + 1;
            Identifier effectTexture = Hud.getMobEffectSprite((Holder)effect.getEffect());
            Objects.requireNonNull(textRenderer);
            Objects.requireNonNull(textRenderer);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, effectTexture, x, lineY - 1, 9, 9);
            Component component = this.getEffectText(effect, list::add);
            Objects.requireNonNull(textRenderer);
            TooltipHelper.renderText(context, textRenderer, component, x + 9 + 3, lineY, ((MobEffect)effect.getEffect().value()).getColor() | 0xFF000000, true);
        }
        if (isEmpty) {
            Objects.requireNonNull(textRenderer);
            TooltipHelper.renderText(context, textRenderer, (Component)Component.translatable((String)"effect.none").withStyle(ChatFormatting.GRAY), x, lineY += 9 + 1, -1, true);
        }
        if (!list.isEmpty()) {
            Objects.requireNonNull(textRenderer);
            TooltipHelper.renderText(context, textRenderer, (Component)Component.translatable((String)"potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE), x, lineY += 9 + 1, -1, true);
        }
        for (Pair<Holder<Attribute>, AttributeModifier> pair : list) {
            Objects.requireNonNull(textRenderer);
            lineY += 9 + 1;
            Component modifierText = PotionEffectTooltipComponent.getModifierText(pair);
            if (modifierText == null) continue;
            TooltipHelper.renderText(context, textRenderer, modifierText, x + 3, lineY, -1, true);
        }
    }

    /*
     * Issues handling annotations - annotations may be inaccurate
     */
    private Component getEffectText(MobEffectInstance effect, Consumer<Pair<Holder<@NotNull Attribute>, AttributeModifier>> list) {
        MutableComponent effectText;
        @NotNull Holder registryEntry = effect.getEffect();
        int amplifier = effect.getAmplifier();
        ((MobEffect)registryEntry.value()).createModifiers(amplifier, (attribute, modifier) -> list.accept(new Pair(attribute, modifier)));
        MutableComponent name = Component.translatable((String)((MobEffect)effect.getEffect().value()).getDescriptionId());
        MutableComponent mutableComponent = effectText = amplifier > 0 ? Component.translatable((String)"potion.withAmplifier", (Object[])new Object[]{name, Component.translatable((String)("potion.potency." + amplifier))}) : name;
        if (!effect.endsWithin(20)) {
            float durationMultiplier = ((Float)this.stack.getOrDefault(DataComponents.POTION_DURATION_SCALE, (Object)Float.valueOf(1.0f))).floatValue();
            effectText = Component.translatable((String)"potion.withDuration", (Object[])new Object[]{effectText, MobEffectUtil.formatDuration((MobEffectInstance)effect, (float)durationMultiplier, (float)20.0f)});
        }
        return effectText;
    }

    private static Component getModifierText(Pair<Holder<@NotNull Attribute>, AttributeModifier> pair) {
        AttributeModifier modifier = (AttributeModifier)pair.getSecond();
        double value = modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL ? modifier.amount() : modifier.amount() * 100.0;
        MutableComponent modifierText = modifier.amount() > 0.0 ? Component.translatable((String)("attribute.modifier.plus." + modifier.operation().id()), (Object[])new Object[]{ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value), Component.translatable((String)((Attribute)((Holder)pair.getFirst()).value()).getDescriptionId())}).withStyle(ChatFormatting.BLUE) : (modifier.amount() < 0.0 ? Component.translatable((String)("attribute.modifier.take." + modifier.operation().id()), (Object[])new Object[]{ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(value *= -1.0), Component.translatable((String)((Attribute)((Holder)pair.getFirst()).value()).getDescriptionId())}).withStyle(ChatFormatting.RED) : null);
        return modifierText;
    }
}

