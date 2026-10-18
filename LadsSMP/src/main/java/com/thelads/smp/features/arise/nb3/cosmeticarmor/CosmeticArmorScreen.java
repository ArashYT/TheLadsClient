package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Interactive Cosmetic Armor GUI allowing players to equip decorative armor pieces
 * and toggle visibility for Head, Chest, Legs, and Feet slots.
 */
public class CosmeticArmorScreen extends Screen {
    private final Screen parent;
    private final CosmeticArmorData data;

    public CosmeticArmorScreen(Screen parent) {
        super(Component.literal("Cosmetic Armor"));
        this.parent = parent;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        this.data = (player != null) ? CosmeticArmorFeature.getOrCreate(player.getUUID()) : new CosmeticArmorData();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 50;

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] slotNames = {"Head / Helmet", "Chest / Elytra", "Legs / Leggings", "Feet / Boots"};

        for (int i = 0; i < slots.length; i++) {
            final EquipmentSlot slot = slots[i];
            int y = startY + (i * 30);

            // Slot interaction button
            this.addRenderableWidget(Button.builder(Component.literal(slotNames[i] + ": " + getSlotLabel(slot)), btn -> {
                handleSlotClick(slot);
                btn.setMessage(Component.literal(slotNames[slot.ordinal()] + ": " + getSlotLabel(slot)));
            }).bounds(centerX - 100, y, 140, 20).build());

            // Eye visibility toggle button
            this.addRenderableWidget(Button.builder(Component.literal(data.isVisible(slot) ? "👁 Shown" : "Ø Hidden"), btn -> {
                boolean vis = data.toggleVisibility(slot);
                btn.setMessage(Component.literal(vis ? "👁 Shown" : "Ø Hidden"));
                syncToServer();
            }).bounds(centerX + 45, y, 65, 20).tooltip(Tooltip.create(Component.literal("Toggle slot visibility"))).build());
        }

        // Done button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            this.onClose();
        }).bounds(centerX - 50, startY + 135, 100, 20).build());
    }

    private String getSlotLabel(EquipmentSlot slot) {
        ItemStack s = data.get(slot);
        return s.isEmpty() ? "[Empty]" : s.getHoverName().getString();
    }

    private void handleSlotClick(EquipmentSlot slot) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();

        if (held.isEmpty()) {
            // Unequip to mainhand if holding nothing
            ItemStack cosmetic = data.get(slot);
            if (!cosmetic.isEmpty()) {
                mc.player.getInventory().add(cosmetic.copy());
                data.set(slot, ItemStack.EMPTY);
            }
        } else {
            // Swap held item with cosmetic slot
            ItemStack old = data.get(slot);
            ItemStack toEquip = held.copy();
            toEquip.setCount(1);
            data.set(slot, toEquip);
            held.shrink(1);
            if (!old.isEmpty()) {
                mc.player.getInventory().add(old);
            }
        }
        syncToServer();
    }

    private void syncToServer() {
        if (ClientPlayNetworking.canSend(UpdateCosmeticArmorPayload.TYPE)) {
            ClientPlayNetworking.send(new UpdateCosmeticArmorPayload(
                    data.get(EquipmentSlot.HEAD),
                    data.get(EquipmentSlot.CHEST),
                    data.get(EquipmentSlot.LEGS),
                    data.get(EquipmentSlot.FEET),
                    data.isVisible(EquipmentSlot.HEAD),
                    data.isVisible(EquipmentSlot.CHEST),
                    data.isVisible(EquipmentSlot.LEGS),
                    data.isVisible(EquipmentSlot.FEET)
            ));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, Component.literal("Cosmetic Armor Equipment").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.width / 2, 22, 0xFFFFFF);
        guiGraphics.centeredText(this.font, Component.literal("Click a slot with armor in hand to equip, or empty hand to unequip.").withStyle(ChatFormatting.GRAY), this.width / 2, 35, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        syncToServer();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
