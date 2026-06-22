/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Lists
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.Gui
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.util.Tuple
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 *  net.minecraft.world.effect.MobEffectUtil
 *  net.minecraft.world.food.FoodProperties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.component.Consumable
 *  net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect
 *  net.minecraft.world.item.consume_effects.ConsumeEffect
 *  org.jetbrains.annotations.NotNull
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.ultimatchamp.enhancedtooltips.mixin;

import com.google.common.collect.Lists;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.mixin.accessors.InventoryAccessor;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipHelper;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipItemStackCache;
import dev.ultimatchamp.enhancedtooltips.util.BadgesUtils;
import dev.ultimatchamp.enhancedtooltips.util.EnhancedTooltipsTextVisitor;
import dev.ultimatchamp.enhancedtooltips.util.MatricesUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Hud.class})
public abstract class GuiMixin {
    @Unique
    private long enhancedTooltips$tiltStartTime = 0L;
    @Unique
    private int enhancedTooltips$lastTiltSlot = -1;
    @Unique
    private float enhancedTooltips$tiltDirection = 0.0f;
    @Unique
    private static final int enhancedTooltips$SPACING = 4;
    @Shadow
    private ItemStack lastToolHighlight;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private int toolHighlightTimer;

    @Shadow
    public abstract Font getFont();

    @Inject(method={"extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V")}, cancellable=true)
    private void enhancedTooltips$renderHeldItemTooltipBackground(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedTooltips").isEnabled() &&
            !com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedToolbars").isEnabled()) {
            return;
        }
        float alpha;
        int currentSlot;
        List<Component> tooltip;
        EnhancedTooltipsConfig config = EnhancedTooltipsConfig.load();
        if (config.heldItemTooltip.mode == EnhancedTooltipsConfig.HeldItemTooltipMode.OFF) {
            return;
        }
        if (config.heldItemTooltip.mode == EnhancedTooltipsConfig.HeldItemTooltipMode.ON) {
            tooltip = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, this.lastToolHighlight));
            TooltipItemStackCache.saveItemStack(ItemStack.EMPTY);
            if (tooltip.isEmpty()) {
                return;
            }
        } else {
            tooltip = Lists.newArrayList((Component[])new Component[]{TooltipHelper.getDisplayName(this.lastToolHighlight)});
        }
        Font textRenderer = this.getFont();
        int n = currentSlot = this.minecraft.player != null ? ((InventoryAccessor)this.minecraft.player.getInventory()).getSelected() : this.enhancedTooltips$lastTiltSlot;
        if (currentSlot != this.enhancedTooltips$lastTiltSlot) {
            int delta = currentSlot - this.enhancedTooltips$lastTiltSlot;
            if (this.enhancedTooltips$lastTiltSlot == 8 && currentSlot == 0) {
                delta = 1;
            } else if (this.enhancedTooltips$lastTiltSlot == 0 && currentSlot == 8) {
                delta = -1;
            }
            this.enhancedTooltips$tiltDirection = Math.signum(delta);
            this.enhancedTooltips$tiltStartTime = System.currentTimeMillis();
            this.enhancedTooltips$lastTiltSlot = currentSlot;
        }
        Tuple<@NotNull Component, @NotNull Integer> badgeText = BadgesUtils.getBadgeText(this.lastToolHighlight);
        if (config.general.itemBadges && !((Component)badgeText.getA()).toFlatList().isEmpty()) {
            MutableComponent name = ((Component)tooltip.getFirst()).copy();
            MutableComponent badge = ((Component)badgeText.getA()).copy().withColor(((Integer)badgeText.getB()).intValue());
            if (!config.heldItemTooltip.hideItemName) {
                MutableComponent combined = name.append((Component)Component.literal((String)" (").withColor(-4539718)).append((Component)badge).append((Component)Component.literal((String)")").withColor(-4539718));
                tooltip.set(0, combined);
            } else {
                tooltip.set(0, badge);
            }
        } else if (config.heldItemTooltip.hideItemName) {
            tooltip.removeFirst();
        }
        tooltip.removeIf(c -> c.getString().equals(Component.translatable((String)"item.sophisticatedcore.storage.tooltip.press_for_contents", (Object[])new Object[]{Component.translatable((String)"item.sophisticatedcore.storage.tooltip.shift")}).getString()));
        if (config.general.rarityTooltip) {
            tooltip.add(Math.min(1, tooltip.size()), TooltipHelper.getRarityName(this.lastToolHighlight));
        }
        if (config.heldItemTooltip.mode == EnhancedTooltipsConfig.HeldItemTooltipMode.ON) {
            this.enhancedTooltips$addFoodTooltip(tooltip::add);
        }
        if (this.minecraft.options.advancedItemTooltips) {
            tooltip.remove(Component.translatable((String)"item.durability", (Object[])new Object[]{this.lastToolHighlight.getMaxDamage() - this.lastToolHighlight.getDamageValue(), this.lastToolHighlight.getMaxDamage()}));
            tooltip.remove(Component.literal(BuiltInRegistries.ITEM.getKey(this.lastToolHighlight.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.remove(Component.translatable((String)"item.components", (Object[])new Object[]{this.lastToolHighlight.getComponents().size()}).withStyle(ChatFormatting.DARK_GRAY));
        }
        if ((!config.durability.durabilityTooltip.equals((Object)EnhancedTooltipsConfig.DurabilityTooltipMode.OFF) || config.durability.durabilityBar) && this.lastToolHighlight.isDamageableItem()) {
            tooltip.add(Component.translatable((String)"enhancedtooltips.tooltip.durability").append(this.enhancedTooltips$getDurabilityText()));
        }
        float scale = config.heldItemTooltip.scaleFactor;
        float maxWidth = (float)context.guiWidth() / (2.0f * scale);
        for (Component component : new ArrayList<>(tooltip)) {
            if (!((float)textRenderer.width((FormattedText)component) > maxWidth)) continue;
            ArrayList wrapped = new ArrayList();
            textRenderer.split((FormattedText)component, (int)maxWidth).forEach(line -> wrapped.add(EnhancedTooltipsTextVisitor.get(line)));
            tooltip.addAll(tooltip.indexOf(component), wrapped);
            tooltip.remove(component);
        }
        AtomicInteger cutOff = new AtomicInteger();
        tooltip.removeIf(text -> {
            if (tooltip.indexOf(text) > config.heldItemTooltip.maxLines) {
                cutOff.incrementAndGet();
                return true;
            }
            return textRenderer.width((FormattedText)text) == 0;
        });
        if (cutOff.get() > 0) {
            tooltip.add(Component.literal((String)("(+" + cutOff.get() + " more...)")).withColor(-4539718).withStyle(s -> s.withItalic(Boolean.valueOf(true))));
        }
        int width = tooltip.stream().mapToInt(arg_0 -> ((Font)textRenderer).width(arg_0)).max().orElse(0);
        float x = ((float)context.guiWidth() - (float)width * scale) / 2.0f;
        int yShift = 0;
        float y = context.guiHeight() - Math.max(yShift, 59);
        Objects.requireNonNull(textRenderer);
        y -= (9.0f + 2.0f) * (float)tooltip.size() * scale - 12.0f + 2.0f;
        if (this.minecraft.player.getArmorValue() > 0 && this.minecraft.gameMode != null && this.minecraft.gameMode.canHurtPlayer()) {
            y -= 8.0f;
        }
        if ((alpha = (float)(this.toolHighlightTimer * 256) / 10.0f) > 255.0f) {
            alpha = 255.0f;
        }
        MatricesUtil matrices = new MatricesUtil((Object)context.pose());
        if (!tooltip.isEmpty()) {
            matrices.pushMatrix();
            this.enhancedTooltips$drawTextWithBackground(textRenderer, tooltip, (int)x, (int)y, width, context, (int)alpha, scale);
            matrices.popMatrix();
        }
        ci.cancel();
    }

    @Unique
    public void enhancedTooltips$addFoodTooltip(Consumer<Component> list) {
        Consumable consumableComponent;
        FoodProperties foodComponent = this.enhancedTooltips$getFoodComponent();
        if (foodComponent == null) {
            return;
        }
        int hunger = this.enhancedTooltips$getHunger();
        int saturation = this.enhancedTooltips$getSaturation();
        MutableComponent hungerText = Component.translatable((String)"enhancedtooltips.tooltip.hunger").append(" " + hunger + " ").append((Component)Component.translatable((String)"effect.minecraft.hunger"));
        MutableComponent saturationText = Component.translatable((String)"enhancedtooltips.tooltip.saturation", (Object[])new Object[]{saturation}).withColor(-16711681);
        if (EnhancedTooltipsConfig.load().foodAndDrinks.hungerTooltip) {
            list.accept((Component)hungerText);
        }
        if (EnhancedTooltipsConfig.load().foodAndDrinks.saturationTooltip) {
            list.accept((Component)saturationText);
        }
        if ((consumableComponent = this.enhancedTooltips$getConsumableComponent()) == null) {
            return;
        }
        List<net.minecraft.world.item.consume_effects.ConsumeEffect> effects = consumableComponent.onConsumeEffects();
        for (net.minecraft.world.item.consume_effects.ConsumeEffect entry : effects) {
            if (EnhancedTooltipsConfig.load().foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.OFF) break;
            if (!(entry instanceof ApplyStatusEffectsConsumeEffect)) continue;
            ApplyStatusEffectsConsumeEffect applyEffectsConsumeEffect = (ApplyStatusEffectsConsumeEffect)entry;
            for (MobEffectInstance statusEffect : applyEffectsConsumeEffect.effects()) {
                int c = ((MobEffect)statusEffect.getEffect().value()).getColor();
                int amplifier = statusEffect.getAmplifier();
                MutableComponent name = amplifier > 0 ? Component.translatable((String)"potion.withAmplifier", (Object[])new Object[]{Component.translatable((String)statusEffect.getDescriptionId()), Component.translatable((String)("potion.potency." + amplifier))}) : Component.translatable((String)statusEffect.getDescriptionId());
                MutableComponent effectText = Component.literal((String)"\u25c8 ").append((Component)name).append(" (").append(MobEffectUtil.formatDuration((MobEffectInstance)statusEffect, (float)1.0f, (float)20.0f)).append(")").withColor(c);
                float probability = applyEffectsConsumeEffect.probability();
                if (!(probability >= 1.0f)) {
                    effectText = effectText.append(" [").append(Math.round(probability * 100.0f) + "%").append("]");
                }
                list.accept((Component)effectText);
            }
        }
    }

    @Unique
    public FoodProperties enhancedTooltips$getFoodComponent() {
        return (FoodProperties)this.lastToolHighlight.getItem().components().get(DataComponents.FOOD);
    }

    @Unique
    public Consumable enhancedTooltips$getConsumableComponent() {
        return (Consumable)this.lastToolHighlight.get(DataComponents.CONSUMABLE);
    }

    @Unique
    public int enhancedTooltips$getHunger() {
        FoodProperties foodComponent = this.enhancedTooltips$getFoodComponent();
        int hunger = 0;
        Consumable consumableComponent = this.enhancedTooltips$getConsumableComponent();
        if (foodComponent != null && consumableComponent != null) {
            hunger = foodComponent.nutrition();
        }
        return hunger;
    }

    @Unique
    public int enhancedTooltips$getSaturation() {
        FoodProperties foodComponent = this.enhancedTooltips$getFoodComponent();
        int saturation = 0;
        int hunger = this.enhancedTooltips$getHunger();
        if (foodComponent != null) {
            saturation = (int)((double)foodComponent.saturation() / ((double)hunger * 2.0) * 100.0);
        }
        return saturation;
    }

    @Unique
    private Component enhancedTooltips$getDurabilityText() {
        int remaining = this.lastToolHighlight.getMaxDamage() - this.lastToolHighlight.getDamageValue();
        if (remaining <= 0) {
            return Component.empty();
        }
        return switch (EnhancedTooltipsConfig.load().durability.durabilityTooltip) {
            case EnhancedTooltipsConfig.DurabilityTooltipMode.VALUE -> Component.literal((String)" ").append((Component)Component.literal((String)String.valueOf(remaining)).setStyle(Style.EMPTY.withColor(this.lastToolHighlight.getBarColor()))).append((Component)Component.literal((String)" / ").setStyle(Style.EMPTY.withColor(-4539718))).append((Component)Component.literal((String)String.valueOf(this.lastToolHighlight.getMaxDamage())).setStyle(Style.EMPTY.withColor(-16711936)));
            case EnhancedTooltipsConfig.DurabilityTooltipMode.PERCENTAGE -> {
                int percent = remaining * 100 / this.lastToolHighlight.getMaxDamage();
                yield Component.literal((String)(" " + percent + "%")).setStyle(Style.EMPTY.withColor(this.lastToolHighlight.getBarColor()));
            }
            default -> {
                int i;
                int filledCount = remaining * 10 / this.lastToolHighlight.getMaxDamage();
                MutableComponent durabilityBar = Component.literal((String)" ");
                for (i = 0; i < filledCount; ++i) {
                    durabilityBar.append((Component)Component.literal((String)"=").setStyle(Style.EMPTY.withColor(this.lastToolHighlight.getBarColor())));
                }
                for (i = filledCount; i < 10; ++i) {
                    durabilityBar.append((Component)Component.literal((String)"=").setStyle(Style.EMPTY.withColor(-4539718)));
                }
                yield durabilityBar;
            }
        };
    }

    @Unique
    private void enhancedTooltips$drawTextWithBackground(Font textRenderer, List<Component> lines, int x, int y, int width, GuiGraphicsExtractor context, int alpha, float scale) {
        int bgAlpha = 128 * alpha / 255 << 24;
        float tilt = this.enhancedTooltips$getTilt();
        MatricesUtil matrices = new MatricesUtil((Object)context.pose());
        matrices.pushMatrix();
        float f = (float)x + (float)width * scale / 2.0f;
        float f2 = y;
        Objects.requireNonNull(textRenderer);
        matrices.trans(f, f2 + (float)(9 * lines.size()) * scale / 2.0f, 0.0f);
        matrices.scal(scale, scale, 0.0f);
        context.pose().rotate((float)Math.toRadians(tilt));
        float f3 = -((float)x / scale + (float)width / 2.0f);
        float f4 = (float)y / scale;
        Objects.requireNonNull(textRenderer);
        matrices.trans(f3, -(f4 + (float)(9 * lines.size()) / 2.0f), 0.0f);
        if (EnhancedTooltipsConfig.load().heldItemTooltip.showBackground) {
            int n = (int)((float)x / scale - 4.0f + 1.0f);
            int n2 = (int)((float)y / scale - 2.0f);
            int n3 = (int)((float)x / scale + (float)width + 4.0f - 1.0f);
            float f5 = (float)y / scale;
            Objects.requireNonNull(textRenderer);
            context.fill(n, n2, n3, (int)(f5 + (9.0f + 2.0f) * (float)lines.size() - 2.0f), bgAlpha);
        }
        int textY = (int)((float)y / scale);
        for (Component line : lines) {
            int color = (line.getStyle().getColor() != null ? line.getStyle().getColor().getValue() : 0xFFFFFF) | alpha << 24;
            TooltipHelper.renderText(context, textRenderer, (Component)line.copy().withColor(color), (int)(((float)context.guiWidth() / scale - (float)textRenderer.width((FormattedText)line)) / 2.0f), textY, color, true);
            Objects.requireNonNull(textRenderer);
            textY += 9 + 2;
        }
        int frameAlpha = 112 * alpha / 255 << 24;
        if (EnhancedTooltipsConfig.load().heldItemTooltip.showBackground) {
            int n = (int)((float)x / scale - 4.0f);
            int n4 = (int)((float)y / scale - 2.0f);
            Objects.requireNonNull(textRenderer);
            BadgesUtils.drawFrame(context, n, n4, width + 8, (9 + 2) * lines.size() + 2, 400, frameAlpha);
        }
        matrices.popMatrix();
    }

    @Unique
    private float enhancedTooltips$getTilt() {
        float duration;
        if (!EnhancedTooltipsConfig.load().heldItemTooltip.tiltAnimation || this.enhancedTooltips$tiltDirection == 0.0f) {
            return 0.0f;
        }
        long elapsed = System.currentTimeMillis() - this.enhancedTooltips$tiltStartTime;
        if ((float)elapsed > (duration = (float)EnhancedTooltipsConfig.load().heldItemTooltip.tiltDuration)) {
            return 0.0f;
        }
        float eased = (float)Math.pow(1.0f - (float)elapsed / duration, EnhancedTooltipsConfig.load().heldItemTooltip.tiltEasing);
        return this.enhancedTooltips$tiltDirection * EnhancedTooltipsConfig.load().heldItemTooltip.tiltMagnitude * eased;
    }
}

