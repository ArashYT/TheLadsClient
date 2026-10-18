package com.thelads.smp.features.arise.nb1.client;

import com.thelads.smp.features.arise.nb1.AriseNB1Config;
import com.thelads.smp.features.arise.nb1.SaveClipboardPayload;
import com.thelads.smp.features.arise.nb1.UsefulClipboardFeature;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Interactive Client UI for Useful Clipboard.
 * Features an editable multi-line text area, character counter,
 * and direct one-click buttons to Copy to / Paste from the OS system clipboard.
 */
public class ClipboardScreen extends Screen {
    private final InteractionHand hand;
    private MultiLineEditBox editBox;
    private String initialText = "";

    public ClipboardScreen(InteractionHand hand) {
        super(Component.literal("Clipboard Notes"));
        this.hand = hand;

        if (Minecraft.getInstance().player != null) {
            ItemStack stack = Minecraft.getInstance().player.getItemInHand(hand);
            if (stack.is(UsefulClipboardFeature.CLIPBOARD)) {
                CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                CompoundTag tag = data.copyTag();
                this.initialText = tag.getStringOr("ClipboardText", "");
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        int boxWidth = Math.min(320, this.width - 40);
        int boxHeight = this.height - 80;
        int boxX = (this.width - boxWidth) / 2;
        int boxY = 30;

        this.editBox = MultiLineEditBox.builder()
                .setX(boxX)
                .setY(boxY)
                .setPlaceholder(Component.literal("Type or paste notes here...").withStyle(ChatFormatting.DARK_GRAY))
                .build(this.font, boxWidth, boxHeight, Component.literal("Clipboard Notes"));

        this.editBox.setCharacterLimit(AriseNB1Config.get().usefulClipboard.maxCharacters);
        this.editBox.setValue(this.initialText);
        this.addRenderableWidget(this.editBox);

        int btnWidth = 90;
        int btnGap = 8;
        int totalBtnWidth = btnWidth * 3 + btnGap * 2;
        int btnStartX = (this.width - totalBtnWidth) / 2;
        int btnY = this.height - 35;

        // Button 1: Copy to OS Clipboard
        this.addRenderableWidget(Button.builder(Component.literal("Copy (OS)"), btn -> {
            if (this.minecraft != null && this.editBox != null) {
                this.minecraft.keyboardHandler.setClipboard(this.editBox.getValue());
            }
        }).bounds(btnStartX, btnY, btnWidth, 20).build());

        // Button 2: Paste from OS Clipboard
        this.addRenderableWidget(Button.builder(Component.literal("Paste (OS)"), btn -> {
            if (this.minecraft != null && this.editBox != null) {
                String clip = this.minecraft.keyboardHandler.getClipboard();
                if (clip != null) {
                    this.editBox.setValue(clip);
                }
            }
        }).bounds(btnStartX + btnWidth + btnGap, btnY, btnWidth, 20).build());

        // Button 3: Done / Save
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            saveAndClose();
        }).bounds(btnStartX + (btnWidth + btnGap) * 2, btnY, btnWidth, 20).build());
    }

    private void saveAndClose() {
        if (this.editBox != null) {
            String text = this.editBox.getValue();
            ClientPlayNetworking.send(new SaveClipboardPayload(text));
        }
        this.onClose();
    }
}
