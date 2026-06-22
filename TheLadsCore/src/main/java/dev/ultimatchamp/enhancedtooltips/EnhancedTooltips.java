/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EquipmentSlot$Type
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.MapItem
 *  net.minecraft.world.item.MobBucketItem
 *  net.minecraft.world.item.SmithingTemplateItem
 *  net.minecraft.world.item.SpawnEggItem
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package dev.ultimatchamp.enhancedtooltips;

import dev.ultimatchamp.enhancedtooltips.compat.EMFCompat;
import dev.ultimatchamp.enhancedtooltips.compat.SophisticatedBackpacksCompat;
import dev.ultimatchamp.enhancedtooltips.component.ArmorTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.BannerPatternTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.DurabilityTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.FoodTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.HeaderTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.MapTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.ModelViewerTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.PaintingTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.PotionEffectTooltipComponent;
import dev.ultimatchamp.enhancedtooltips.component.TooltipBackgroundComponent;
import dev.ultimatchamp.enhancedtooltips.component.TooltipBorderColorComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.mixin.accessors.ClientTextTooltipAccessor;
import dev.ultimatchamp.enhancedtooltips.mixin.accessors.HangingEntityItemTypeAccessor;
import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipComponentManager;
import dev.ultimatchamp.enhancedtooltips.util.BadgesUtils;
import dev.ultimatchamp.enhancedtooltips.util.EnhancedTooltipsTextVisitor;
import dev.ultimatchamp.enhancedtooltips.util.ItemGroupsUtils;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SpawnEggItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnhancedTooltips {
    public static final String MOD_ID = "enhancedtooltips";
    public static final String MOD_NAME = "EnhancedTooltips";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"EnhancedTooltips");

    public static void init() {
        EnhancedTooltipsConfig.load();
        if (BadgesUtils.getMods().containsKey("entity_model_features")) {
            EMFCompat.registerVanillaModelCondition();
        }
        TooltipComponentManager.register((list, stack) -> {
            HangingEntityItemTypeAccessor decorationItem;
            Item patt0$temp;
            if (list.isEmpty()) {
                return;
            }
            ArrayList copy = new ArrayList(list);
            ArrayList advanced = new ArrayList();
            if (Minecraft.getInstance().options.advancedItemTooltips) {
                list.removeIf(component -> {
                    if (!(component instanceof ClientTextTooltipAccessor)) return false;
                    ClientTextTooltipAccessor c = (ClientTextTooltipAccessor)component;
                    if (EnhancedTooltipsConfig.load().durability.durabilityTooltip.equals((Object)EnhancedTooltipsConfig.DurabilityTooltipMode.OFF)) {
                        if (!EnhancedTooltipsConfig.load().durability.durabilityBar) return false;
                    }
                    if (!EnhancedTooltipsTextVisitor.get(c.getText()).getString().contains(stack.getMaxDamage() - stack.getDamageValue() + " / " + stack.getMaxDamage())) return false;
                    return true;
                });
                copy.forEach(component -> {
                    if (!(component instanceof ClientTextTooltipAccessor)) {
                        return;
                    }
                    ClientTextTooltipAccessor c = (ClientTextTooltipAccessor)component;
                    if (EnhancedTooltipsTextVisitor.get(c.getText()).getString().contains(stack.getMaxDamage() - stack.getDamageValue() + " / " + stack.getMaxDamage()) || EnhancedTooltipsTextVisitor.get(c.getText()).getString().contains(Component.literal((String)BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY).getString()) || EnhancedTooltipsTextVisitor.get(c.getText()).getString().contains(Component.translatable((String)"item.components", (Object[])new Object[]{stack.getComponents().size()}).withStyle(ChatFormatting.DARK_GRAY).getString())) {
                        advanced.add(component);
                        list.remove(component);
                    }
                });
            }
            if (stack.isEmpty()) {
                list.add(new TooltipBackgroundComponent());
                return;
            }
            list.removeIf(c -> {
                ClientTextTooltipAccessor component;
                return c instanceof ClientTextTooltipAccessor && EnhancedTooltipsTextVisitor.get((component = (ClientTextTooltipAccessor)c).getText()).getString().equals(stack.getHoverName().getString()) && (!BadgesUtils.getMods().containsKey("sophisticatedcore") || !SophisticatedBackpacksCompat.containsBackpackTooltip(list));
            });
            list.addFirst(new HeaderTooltipComponent(stack));
            if (EnhancedTooltipsConfig.load().general.itemBadges) {
                list.removeIf(component -> {
                    if (component instanceof ClientTextTooltipAccessor) {
                        ClientTextTooltipAccessor textComponent = (ClientTextTooltipAccessor)component;
                        Component text = EnhancedTooltipsTextVisitor.get(textComponent.getText());
                        for (String group : ItemGroupsUtils.ITEM_GROUP_KEYS) {
                            if (!text.getString().equals(EnhancedTooltipsTextVisitor.get(Component.translatable((String)group).getVisualOrderText()).getString())) continue;
                            return true;
                        }
                    }
                    return false;
                });
            }
            if (FoodTooltipComponent.getFoodComponent(stack) != null) {
                list.add(1, new FoodTooltipComponent(stack));
            }
            if (stack.get(DataComponents.POTION_CONTENTS) != null && EnhancedTooltipsConfig.load().foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.WITH_ICONS) {
                list.add(1, new PotionEffectTooltipComponent(stack));
            }
            if (ModelViewerTooltipComponent.getEquipmentSlot(stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR || ModelViewerTooltipComponent.getEquipmentSlot(stack).getType() == EquipmentSlot.Type.ANIMAL_ARMOR || stack.getItem() instanceof MobBucketItem || stack.getItem() instanceof SpawnEggItem || stack.getItem() instanceof SmithingTemplateItem && stack.getItem().toString().contains("armor_trim")) {
                list.add(new ModelViewerTooltipComponent(stack));
            } else {
                list.add(new TooltipBorderColorComponent(stack));
            }
            if (stack.getItem() instanceof MapItem && EnhancedTooltipsConfig.load().mapTooltip.enabled) {
                list.add(new MapTooltipComponent(stack));
            }
            if ((patt0$temp = stack.getItem()) instanceof HangingEntityItemTypeAccessor && (decorationItem = (HangingEntityItemTypeAccessor)patt0$temp).get() == net.minecraft.world.entity.EntityTypes.PAINTING && EnhancedTooltipsConfig.load().paintingTooltip.enabled) {
                list.add(new PaintingTooltipComponent(stack));
            }
            if (stack.get(DataComponents.PROVIDES_BANNER_PATTERNS) != null && EnhancedTooltipsConfig.load().bannerPatternTooltip.enabled) {
                list.add(new BannerPatternTooltipComponent(stack));
            }
            if (ModelViewerTooltipComponent.getEquipmentSlot(stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR && EnhancedTooltipsConfig.load().armorIconTooltip.enabled) {
                list.add(new ArmorTooltipComponent(stack));
            }
            if (stack.isDamageableItem()) {
                list.add(new DurabilityTooltipComponent(stack));
            }
            list.addAll(advanced);
        });
    }
}

