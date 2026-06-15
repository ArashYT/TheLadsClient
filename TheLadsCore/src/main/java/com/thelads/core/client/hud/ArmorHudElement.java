package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorHudElement extends HudElement {
    public ArmorHudElement() {
        this.x = 5;
        this.y = 85;
        this.width = 80;
        this.height = 30;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();

        // Option-driven: "Attach to hotbar" follows the hotbar every frame;
        // "Durability" is 0=Off, 1=Number, 2=Percent.
        boolean attach = optBool("Attach to hotbar", true);
        int durabilityMode = optCycle("Durability", 1);

        if (attach) {
            int gw = mc.getWindow().getGuiScaledWidth();
            int gh = mc.getWindow().getGuiScaledHeight();
            this.width = 80;
            this.height = 30;
            this.x = gw / 2 - this.width / 2;     // centred over the hotbar
            this.y = gh - 22 - this.height;       // sit directly above the hotbar
        }

        drawBackground(g);
        if (mc.player != null) {
            boolean shadow = HudSettings.getInstance().isTextShadow();
            int currentX = x + 4;
            EquipmentSlot[] slots = new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            for (EquipmentSlot slot : slots) {
                ItemStack stack = mc.player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    g.item(stack, currentX, y + 2);
                    g.itemDecorations(mc.font, stack, currentX, y + 2);
                    if (durabilityMode != 0 && stack.isDamageableItem()) {
                        int maxD = stack.getMaxDamage();
                        int rem = maxD - stack.getDamageValue();
                        String s = (durabilityMode == 2 && maxD > 0)
                            ? (rem * 100 / maxD) + "%"
                            : String.valueOf(rem);
                        g.text(mc.font, s, currentX + 9 - mc.font.width(s) / 2, y + 20, resolveColor(), shadow);
                    }
                }
                currentX += 18;
            }
        }
    }
}
