package com.thelads.core.v1_20_1.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;

public class FolderPackEntry1201 extends TransferableSelectionList.PackEntry {
    private final String folderName;
    private final boolean isBack;
    private final FolderNav1201 nav;

    public FolderPackEntry1201(Minecraft minecraft, TransferableSelectionList parent, String folderName, boolean isBack, FolderNav1201 nav) {
        super(minecraft, parent, new FolderModelEntry(folderName, isBack));
        this.folderName = folderName;
        this.isBack = isBack;
        this.nav = nav;
    }

    @Override
    public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
        boolean hovered = mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height;
        int iconBg = isBack ? (hovered ? 0x5500AAFF : 0x3300AAFF) : (hovered ? 0x55FFAA00 : 0x33FFAA00);
        graphics.fill(left, top, left + 32, top + 32, iconBg);
        if (isBack) {
            graphics.drawString(this.minecraft.font, "§b..", left + 12, top + 12, 0xFFFFFF);
            graphics.drawString(this.minecraft.font, "§b.. (Up)", left + 36, top + 4, 0xFFFFFF);
            graphics.drawString(this.minecraft.font, "§7Go up to parent folder", left + 36, top + 16, 0xAAAAAA);
        } else {
            graphics.drawString(this.minecraft.font, "§6DIR", left + 8, top + 12, 0xFFFFAA);
            graphics.drawString(this.minecraft.font, "§e[Folder] §f" + folderName, left + 36, top + 4, 0xFFFFFF);
            graphics.drawString(this.minecraft.font, "§7Folder - Click to view packs", left + 36, top + 16, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && nav != null) {
            if (isBack) {
                nav.ladsCdUp();
            } else {
                nav.ladsCd(folderName);
            }
            return true;
        }
        return false;
    }

    private static class FolderModelEntry implements PackSelectionModel.Entry {
        private final String name;
        private final boolean isBack;

        public FolderModelEntry(String name, boolean isBack) {
            this.name = name;
            this.isBack = isBack;
        }

        @Override public ResourceLocation getIconTexture() { return new ResourceLocation("theladscore", "textures/gui/folder.png"); }
        @Override public PackCompatibility getCompatibility() { return PackCompatibility.COMPATIBLE; }
        @Override public String getId() { return "folder/" + name; }
        @Override public Component getTitle() { return Component.literal(isBack ? ".." : name); }
        @Override public Component getDescription() { return Component.empty(); }
        @Override public Component getExtendedDescription() { return Component.empty(); }
        @Override public PackSource getPackSource() { return PackSource.DEFAULT; }
        @Override public boolean isFixedPosition() { return false; }
        @Override public boolean isRequired() { return false; }
        @Override public void select() {}
        @Override public void unselect() {}
        @Override public void moveUp() {}
        @Override public void moveDown() {}
        @Override public boolean isSelected() { return false; }
        @Override public boolean canSelect() { return false; }
        @Override public boolean canUnselect() { return false; }
        @Override public boolean canMoveUp() { return false; }
        @Override public boolean canMoveDown() { return false; }
    }
}
