package com.thelads.core.v1_8_9.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;

public class ResourcePackFolderEntry189 extends ResourcePackListEntry {
    private final String folderName;

    public ResourcePackFolderEntry189(GuiScreenResourcePacks screen, String folderName) {
        super(screen);
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }

    @Override
    protected int func_183019_a() {
        return 1;
    }

    @Override
    protected String func_148311_a() {
        return "Folder (click to open)";
    }

    @Override
    protected String func_148312_b() {
        return folderName;
    }

    @Override
    protected void func_148313_c() {
        // Handled in drawEntry
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected) {
        boolean hovered = mouseX >= x && mouseX <= x + listWidth && mouseY >= y && mouseY <= y + slotHeight;
        int iconBg = hovered ? 0x55FFAA00 : 0x33FFAA00;
        Gui.drawRect(x, y, x + 32, y + 32, iconBg);
        this.mc.fontRendererObj.drawStringWithShadow("§6DIR", x + 8, y + 12, 0xFFFFAA);
        this.mc.fontRendererObj.drawStringWithShadow("§e[Folder] §f" + folderName, x + 36, y + 4, 0xFFFFFF);
        this.mc.fontRendererObj.drawStringWithShadow("Folder - Click to view packs", x + 36, y + 16, 0xAAAAAA);
    }

    @Override
    public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
        if (this.resourcePacksGUI instanceof FolderNav189) {
            ((FolderNav189) this.resourcePacksGUI).ladsCd(folderName);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {}

    @Override
    public void setSelected(int p_178011_1_, int p_178011_2_, int p_178011_3_) {}
}
