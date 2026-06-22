/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
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
 *  net.minecraft.world.food.FoodProperties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.component.Consumable
 *  net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect
 *  net.minecraft.world.item.consume_effects.ConsumeEffect
 *  org.jetbrains.annotations.NotNull
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.EnhancedTooltipsTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipHelper;
import java.util.Objects;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import org.jetbrains.annotations.NotNull;

public class FoodTooltipComponent
implements EnhancedTooltipsTooltipComponent {
    private final ItemStack stack;
    private final EnhancedTooltipsConfig config;

    public FoodTooltipComponent(ItemStack stack) {
        this.stack = stack;
        this.config = EnhancedTooltipsConfig.load();
    }

    public Consumable getConsumableComponent() {
        return (Consumable)this.stack.get(DataComponents.CONSUMABLE);
    }

    public static FoodProperties getFoodComponent(ItemStack stack) {
        return (FoodProperties)stack.getItem().components().get(DataComponents.FOOD);
    }

    public int getHunger() {
        FoodProperties foodComponent = FoodTooltipComponent.getFoodComponent(this.stack);
        int hunger = 0;
        Consumable consumableComponent = this.getConsumableComponent();
        if (foodComponent != null && consumableComponent != null) {
            hunger = foodComponent.nutrition();
        }
        return hunger;
    }

    public int getSaturation() {
        FoodProperties foodComponent = FoodTooltipComponent.getFoodComponent(this.stack);
        int saturation = 0;
        int hunger = this.getHunger();
        if (foodComponent != null) {
            saturation = (int)((double)foodComponent.saturation() / ((double)hunger * 2.0) * 100.0);
        }
        return saturation;
    }

    @Override
    public int height() {
        int height = 0;
        FoodProperties foodComponent = FoodTooltipComponent.getFoodComponent(this.stack);
        Consumable consumableComponent = this.getConsumableComponent();
        if (foodComponent != null && consumableComponent != null) {
            if (this.config.foodAndDrinks.hungerTooltip) {
                height += 10;
            }
            if (this.config.foodAndDrinks.saturationTooltip) {
                height += 10;
            }
            if (this.config.foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.OFF) {
                return height;
            }
            for (ConsumeEffect entry : consumableComponent.onConsumeEffects()) {
                if (!(entry instanceof ApplyStatusEffectsConsumeEffect)) continue;
                ApplyStatusEffectsConsumeEffect applyEffectsConsumeEffect = (ApplyStatusEffectsConsumeEffect)entry;
                for (MobEffectInstance ignored : applyEffectsConsumeEffect.effects()) {
                    Objects.requireNonNull(Minecraft.getInstance().font);
                    height += 9 + 1;
                }
            }
        }
        return height;
    }

    public int getWidth(@NotNull Font textRenderer) {
        int foodWidth = 0;
        int effectsWidth = 0;
        FoodProperties foodComponent = FoodTooltipComponent.getFoodComponent(this.stack);
        int hunger = this.getHunger();
        int hungerLine = 0;
        if (this.config.foodAndDrinks.hungerTooltip) {
            float f = textRenderer.width((FormattedText)Component.translatable((String)"enhancedtooltips.tooltip.hunger")) + 1;
            Objects.requireNonNull(textRenderer);
            hungerLine = (int)(f + (float)(9 - 2) * ((float)hunger / 2.0f));
        }
        int saturationLine = 0;
        if (this.config.foodAndDrinks.saturationTooltip) {
            saturationLine = textRenderer.width((FormattedText)Component.translatable((String)"enhancedtooltips.tooltip.saturation"));
        }
        foodWidth = Math.max(hungerLine, saturationLine);
        if (this.config.foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.OFF) {
            return foodWidth;
        }
        if (foodComponent == null) {
            return 0;
        }
        Consumable consumableComponent = this.getConsumableComponent();
        if (consumableComponent == null) {
            return 0;
        }
        for (ConsumeEffect entry : consumableComponent.onConsumeEffects()) {
            if (!(entry instanceof ApplyStatusEffectsConsumeEffect)) continue;
            ApplyStatusEffectsConsumeEffect applyEffectsConsumeEffect = (ApplyStatusEffectsConsumeEffect)entry;
            for (MobEffectInstance statusEffect : applyEffectsConsumeEffect.effects()) {
                int amplifier = statusEffect.getAmplifier();
                float probability = applyEffectsConsumeEffect.probability();
                MutableComponent text = Component.translatable((String)statusEffect.getDescriptionId()).append((Component)Component.translatable((String)("potion.potency." + amplifier))).append("  (99:99)");
                if (probability < 1.0f) {
                    text.append(" [100%]");
                }
                effectsWidth = Math.max(effectsWidth, textRenderer.width((FormattedText)text));
            }
        }
        if (effectsWidth != 0 && this.config.foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.WITH_ICONS) {
            Objects.requireNonNull(textRenderer);
            effectsWidth += 9 + 3;
        }
        return Math.max(foodWidth, effectsWidth);
    }

    @Override
    public void drawImage(@NotNull Font textRenderer, int x, int y, int width, int height, @NotNull GuiGraphicsExtractor context) {
        FoodProperties foodComponent = FoodTooltipComponent.getFoodComponent(this.stack);
        int hunger = this.getHunger();
        int saturation = this.getSaturation();
        Consumable consumableComponent = this.getConsumableComponent();
        if (consumableComponent == null) {
            return;
        }
        if (foodComponent == null) {
            return;
        }
        MutableComponent hungerText = Component.translatable((String)"enhancedtooltips.tooltip.hunger");
        MutableComponent saturationText = Component.translatable((String)"enhancedtooltips.tooltip.saturation", (Object[])new Object[]{saturation});
        int lineY = y;
        if (this.config.foodAndDrinks.hungerTooltip) {
            TooltipHelper.renderText(context, textRenderer, (Component)hungerText, x, lineY, -1, true);
            Identifier fullHunger = Identifier.fromNamespaceAndPath("minecraft", "hud/food_full");
            Identifier halfHunger = Identifier.fromNamespaceAndPath("minecraft", "hud/food_half");
            float fullHungers = (float)hunger / 2.0f;
            boolean hasHalfHunger = hunger % 2 != 0;
            int hungerWidth = textRenderer.width((FormattedText)hungerText) + 1;
            for (int i = 0; i < (int)fullHungers; ++i) {
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, fullHunger, 9, 9, 0, 0, x + hungerWidth, lineY, 9, 9);
                Objects.requireNonNull(textRenderer);
                hungerWidth += 9 - 2;
            }
            if (hasHalfHunger) {
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                Objects.requireNonNull(textRenderer);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, halfHunger, 9, 9, 0, 0, x + hungerWidth, lineY, 9, 9);
            }
            Objects.requireNonNull(textRenderer);
            lineY += 9 + 1;
        }
        if (this.config.foodAndDrinks.saturationTooltip) {
            TooltipHelper.renderText(context, textRenderer, (Component)saturationText, x + 2, lineY, -16711681, true);
            Objects.requireNonNull(textRenderer);
            lineY += 9 + 1;
        }
        if (this.config.foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.OFF) {
            return;
        }
        for (ConsumeEffect entry : consumableComponent.onConsumeEffects()) {
            if (!(entry instanceof ApplyStatusEffectsConsumeEffect)) continue;
            ApplyStatusEffectsConsumeEffect applyEffectsConsumeEffect = (ApplyStatusEffectsConsumeEffect)entry;
            for (MobEffectInstance statusEffect : applyEffectsConsumeEffect.effects()) {
                int c = ((MobEffect)statusEffect.getEffect().value()).getColor() | 0xFF000000;
                int amplifier = statusEffect.getAmplifier();
                Identifier effectTexture = Hud.getMobEffectSprite((Holder)statusEffect.getEffect());
                MutableComponent effectText = amplifier > 0 ? Component.translatable((String)"potion.withAmplifier", (Object[])new Object[]{Component.translatable((String)statusEffect.getDescriptionId()), Component.translatable((String)("potion.potency." + amplifier))}) : Component.translatable((String)statusEffect.getDescriptionId());
                float probability = applyEffectsConsumeEffect.probability();
                effectText = probability >= 1.0f ? effectText.append(" (").append(MobEffectUtil.formatDuration((MobEffectInstance)statusEffect, (float)1.0f, (float)20.0f)).append(")") : effectText.append(" (").append(MobEffectUtil.formatDuration((MobEffectInstance)statusEffect, (float)1.0f, (float)20.0f)).append(")").append(" [").append(Math.round(probability * 100.0f) + "%").append("]");
                if (this.config.foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.WITH_ICONS) {
                    Objects.requireNonNull(textRenderer);
                    Objects.requireNonNull(textRenderer);
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, effectTexture, x, lineY - 1, 9, 9);
                    Objects.requireNonNull(textRenderer);
                    TooltipHelper.renderText(context, textRenderer, (Component)effectText, x + 9 + 3, lineY, c, true);
                } else {
                    TooltipHelper.renderText(context, textRenderer, (Component)effectText, x, lineY, c, true);
                }
                Objects.requireNonNull(textRenderer);
                lineY += 9 + 1;
            }
        }
    }
}

