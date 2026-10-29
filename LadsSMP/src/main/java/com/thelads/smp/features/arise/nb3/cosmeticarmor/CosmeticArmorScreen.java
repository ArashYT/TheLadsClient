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
            final int index = i; // wire slot index: 0 head .. 3 feet
            int y = startY + (i * 30);

            // Slot interaction button (the server moves the item and syncs back; labels refresh on that sync)
            this.addRenderableWidget(Button.builder(Component.literal(slotNames[i] + ": " + getSlotLabel(slot)),
                    btn -> sendAction(index, true, data.isVisible(slot))
            ).bounds(centerX - 100, y, 140, 20).build());

            // Eye visibility toggle button
            this.addRenderableWidget(Button.builder(Component.literal(data.isVisible(slot) ? "👁 Shown" : "Ø Hidden"),
                    btn -> sendAction(index, false, !data.isVisible(slot))
            ).bounds(centerX + 45, y, 65, 20).tooltip(Tooltip.create(Component.literal("Toggle slot visibility"))).build());
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

    /** Called when the server's sync for the local player arrives. */
    public void refresh() {
        this.rebuildWidgets();
    }

    private static void sendAction(int index, boolean swap, boolean visible) {
        if (ClientPlayNetworking.canSend(UpdateCosmeticArmorPayload.TYPE)) {
            ClientPlayNetworking.send(new UpdateCosmeticArmorPayload(index, swap, visible));
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
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
