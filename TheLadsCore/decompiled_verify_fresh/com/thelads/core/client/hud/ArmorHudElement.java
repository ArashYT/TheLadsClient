/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.item.ItemStack
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorHudElement
extends HudElement {
    public ArmorHudElement() {
        this.x = 5;
        this.y = 85;
        this.width = 80;
        this.height = 30;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        boolean attach = this.optBool("Attach to hotbar", true);
        int durabilityMode = this.optCycle("Durability", 1);
        if (attach) {
            int gw = mc.getWindow().getGuiScaledWidth();
            int gh = mc.getWindow().getGuiScaledHeight();
            this.width = 80;
            this.height = 30;
            this.x = gw / 2 - this.width / 2;
            this.y = gh - 22 - this.height;
        }
        this.drawBackground(g);
        if (mc.player != null) {
            EquipmentSlot[] slots;
            boolean shadow = HudSettings.getInstance().isTextShadow();
            int currentX = this.x + 4;
            for (EquipmentSlot slot : slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = mc.player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    g.item(stack, currentX, this.y + 2);
                    g.itemDecorations(mc.font, stack, currentX, this.y + 2);
                    if (durabilityMode != 0 && stack.isDamageableItem()) {
                        int maxD = stack.getMaxDamage();
                        int rem = maxD - stack.getDamageValue();
                        String s = durabilityMode == 2 && maxD > 0 ? rem * 100 / maxD + "%" : String.valueOf(rem);
                        g.text(mc.font, s, currentX + 9 - mc.font.width(s) / 2, this.y + 20, this.resolveColor(), shadow);
                    }
                }
                currentX += 18;
            }
        }
    }
}

