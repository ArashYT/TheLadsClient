package com.thelads.core.features.alwayson.advancementsreloaded;

import java.util.Optional;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;

public class ReloadedDisplayInfo extends DisplayInfo {
    private float x;
    private float y;

    public ReloadedDisplayInfo(ItemStackTemplate icon, Component title, Component description, Optional<ClientAsset.ResourceTexture> background, AdvancementType type, boolean showToast, boolean announceChat, boolean hidden) {
        super(icon, title, description, background, type, showToast, announceChat, hidden);
    }

    public static ReloadedDisplayInfo cast(DisplayInfo display) {
        ReloadedDisplayInfo ard = new ReloadedDisplayInfo(display.getIcon(), display.getTitle(), display.getDescription(), display.getBackground(), display.getType(), display.shouldShowToast(), display.shouldAnnounceChat(), display.isHidden());
        ard.x = display.getX();
        ard.y = display.getY();
        return ard;
    }

    @Override
    public float getX() {
        return this.x;
    }

    @Override
    public float getY() {
        return this.y;
    }
}
