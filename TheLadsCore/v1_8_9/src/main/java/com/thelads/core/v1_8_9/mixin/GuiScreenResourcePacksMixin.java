package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.pack.PackAssetMask;
import com.thelads.core.client.pack.PackFilterHelper;
import com.thelads.core.client.pack.PackFolderHelper;
import com.thelads.core.client.pack.PackMenuSettings;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.FolderNav189;
import com.thelads.core.v1_8_9.gui.ResourcePackBackEntry189;
import com.thelads.core.v1_8_9.gui.ResourcePackFolderEntry189;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreenResourcePacks.class)
public abstract class GuiScreenResourcePacksMixin extends GuiScreen implements FolderNav189 {
    @Shadow @Final private GuiScreen parentScreen;
    @Shadow private List<ResourcePackListEntry> availableResourcePacks;
    @Shadow private List<ResourcePackListEntry> selectedResourcePacks;
    @Shadow private boolean changed;

    @Unique private GuiTextField ladsSearchField;
    @Unique private String ladsCurrentFolder = "";
    @Unique private final List<ResourcePackListEntry> ladsMasterSelected = new ArrayList<>();
    @Unique private final List<ResourcePackListEntry> ladsMasterAllAvailable = new ArrayList<>();
    @Unique private final List<ResourcePackListEntry> ladsLastSelectedSnapshot = new ArrayList<>();
    @Unique private String ladsCachedSubfoldersDir = null;
    @Unique private List<Path> ladsCachedSubfolders = Collections.emptyList();

    @Inject(method = "initGui", at = @At("RETURN"))
    private void ladsOnInitGui(CallbackInfo ci) {
        ladsSearchField = new GuiTextField(999, fontRendererObj, width / 2 - 90, 8, 180, 16);
        ladsSearchField.setMaxStringLength(50);
        ladsSearchField.setCanLoseFocus(true);
        ladsCachedSubfoldersDir = null;

        if (ladsMasterSelected.isEmpty() && selectedResourcePacks != null) {
            ladsMasterSelected.addAll(selectedResourcePacks);
        }
        ladsMasterAllAvailable.clear();
        if (availableResourcePacks != null) {
            ladsMasterAllAvailable.addAll(availableResourcePacks);
        }
        ladsApplyFilter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (ladsSearchField != null && ladsSearchField.isFocused()) {
            if (keyCode == 1) { // Esc closes
                mc.displayGuiScreen(parentScreen);
                return;
            }
            if (ladsSearchField.textboxKeyTyped(typedChar, keyCode)) {
                ladsApplyFilter();
            }
            return; // Consume event to prevent triggering other hotkeys
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (ladsSearchField != null) {
            ladsSearchField.updateCursorCounter();
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void ladsOnMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (ladsSearchField != null) {
            ladsSearchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (selectedResourcePacks != null && !selectedResourcePacks.equals(ladsLastSelectedSnapshot)) {
            ladsSyncMasterSelected();
            ladsApplyFilter();
        }
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void ladsDrawScreenHead(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (PackMenuSettings.isTransparent() && mc.theWorld == null) {
            TitleScreenTheme.Layout layout = TitleScreenTheme.layout(width, height, 0, false);
            TitleScreenTheme.renderBackground(new GuiLadsAdapter(fontRendererObj, width, height), layout,
                mc.getSession().getUsername(), "1.8.9", false, (System.currentTimeMillis() % 1000000L) / 1000.0, false);
        }
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreenResourcePacks;drawCenteredString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"), require = 0)
    private void ladsSkipCenterTitle(GuiScreenResourcePacks instance, FontRenderer fontRenderer, String text, int x, int y, int color) {
        // Suppress default center title overlapping the search bar
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void ladsDrawScreenReturn(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (ladsSearchField != null) {
            ladsSearchField.drawTextBox();
            if (ladsSearchField.getText().isEmpty() && !ladsSearchField.isFocused()) {
                drawString(fontRendererObj, "§8Search packs...", width / 2 - 84, 12, 0x888888);
            }
        }
        String folderLabel = "§7Folder: §f/" + ladsCurrentFolder;
        drawString(fontRendererObj, folderLabel, width / 2 - 200, 12, 0xFFFFFF);
    }

    @Redirect(method = "actionPerformed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/ResourcePackRepository;getDirResourcepacks()Ljava/io/File;"))
    private File ladsOpenSubfolder(ResourcePackRepository repo) {
        File root = repo.getDirResourcepacks();
        if (ladsCurrentFolder != null && !ladsCurrentFolder.isEmpty()) {
            File sub = new File(root, ladsCurrentFolder);
            if (!sub.exists()) sub.mkdirs();
            return sub;
        }
        return root;
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void ladsBeforeDone(GuiButton button, CallbackInfo ci) {
        if (button.id == 1 && selectedResourcePacks != null) {
            selectedResourcePacks.clear();
            selectedResourcePacks.addAll(ladsMasterSelected);
            if (this.changed && mc != null && mc.getResourcePackRepository() != null) {
                ResourcePackRepository repo = mc.getResourcePackRepository();
                File rootDir = repo.getDirResourcepacks();
                List<ResourcePackRepository.Entry> oldEntries = repo.getRepositoryEntries();
                List<String> oldIds = new ArrayList<>(oldEntries.size());
                for (ResourcePackRepository.Entry e : oldEntries) {
                    oldIds.add(e.getResourcePackName());
                }
                List<String> newIds = new ArrayList<>(selectedResourcePacks.size());
                for (int i = selectedResourcePacks.size() - 1; i >= 0; i--) {
                    ResourcePackListEntry le = selectedResourcePacks.get(i);
                    if (le instanceof ResourcePackListEntryFound) {
                        newIds.add(((ResourcePackListEntryFound) le).func_148318_i().getResourcePackName());
                    }
                }
                int diffMask = PackAssetMask.computeDiffMask(oldIds, newIds, id -> {
                    if (rootDir == null || id == null || id.isEmpty()) return PackAssetMask.ALL;
                    return PackAssetMask.getMask(new File(rootDir, id));
                });
                PackAssetMask.beginPackSwitchReload(diffMask);
            }
        }
    }

    @Inject(method = "actionPerformed", at = @At("RETURN"))
    private void ladsAfterActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 1) {
            PackAssetMask.clearPendingIfNotStarted();
        }
    }

    @Override
    public void ladsCd(String folderName) {
        if (ladsCurrentFolder == null || ladsCurrentFolder.isEmpty()) {
            ladsCurrentFolder = folderName;
        } else {
            ladsCurrentFolder = ladsCurrentFolder + "/" + folderName;
        }
        ladsApplyFilter();
    }

    @Override
    public void ladsCdUp() {
        ladsCurrentFolder = PackFolderHelper.getParentRelativePath(ladsCurrentFolder);
        if (ladsCurrentFolder == null) {
            ladsCurrentFolder = "";
        }
        ladsApplyFilter();
    }

    @Unique
    private void ladsSyncMasterSelected() {
        if (selectedResourcePacks == null) return;
        String query = ladsSearchField == null ? "" : ladsSearchField.getText().trim();
        if (query.isEmpty()) {
            ladsMasterSelected.clear();
            ladsMasterSelected.addAll(selectedResourcePacks);
        } else {
            // Newly selected entries
            for (ResourcePackListEntry entry : selectedResourcePacks) {
                if (!ladsMasterSelected.contains(entry)) {
                    ladsMasterSelected.add(0, entry);
                }
            }
            // Entries deselected while query active
            for (ResourcePackListEntry master : new ArrayList<>(ladsMasterSelected)) {
                if (!selectedResourcePacks.contains(master)) {
                    String name = getEntryName(master);
                    String desc = getEntryDesc(master);
                    if (PackFilterHelper.matches(query, name, desc)) {
                        ladsMasterSelected.remove(master);
                    }
                }
            }
            // Sync relative ordering of visible items into ladsMasterSelected
            List<ResourcePackListEntry> visible = new ArrayList<>();
            for (ResourcePackListEntry entry : selectedResourcePacks) {
                if (ladsMasterSelected.contains(entry)) {
                    visible.add(entry);
                }
            }
            int vIdx = 0;
            for (int i = 0; i < ladsMasterSelected.size() && vIdx < visible.size(); i++) {
                ResourcePackListEntry current = ladsMasterSelected.get(i);
                if (visible.contains(current)) {
                    ladsMasterSelected.set(i, visible.get(vIdx++));
                }
            }
        }
    }

    @Unique
    private void ladsApplyFilter() {
        if (selectedResourcePacks == null || availableResourcePacks == null) return;
        String query = ladsSearchField == null ? "" : ladsSearchField.getText().trim();

        // 1. Filter selected list while preserving master order
        selectedResourcePacks.clear();
        Set<String> selectedNames = new HashSet<>(Math.max(16, ladsMasterSelected.size() * 2));
        for (ResourcePackListEntry entry : ladsMasterSelected) {
            String name = getEntryName(entry);
            if (!name.isEmpty()) {
                selectedNames.add(name);
            }
            String desc = getEntryDesc(entry);
            if (PackFilterHelper.matches(query, name, desc)) {
                selectedResourcePacks.add(entry);
            }
        }

        // 2. Filter available list
        availableResourcePacks.clear();
        if (query.isEmpty()) {
            if (!ladsCurrentFolder.isEmpty()) {
                availableResourcePacks.add(new ResourcePackBackEntry189((GuiScreenResourcePacks) (Object) this));
            }
            if (!ladsCurrentFolder.equals(ladsCachedSubfoldersDir)) {
                File rootDir = mc.getResourcePackRepository().getDirResourcepacks();
                File currentDir = ladsCurrentFolder.isEmpty() ? rootDir : new File(rootDir, ladsCurrentFolder);
                ladsCachedSubfolders = PackFolderHelper.findSubfolders(currentDir.toPath());
                ladsCachedSubfoldersDir = ladsCurrentFolder;
            }
            for (Path sub : ladsCachedSubfolders) {
                availableResourcePacks.add(new ResourcePackFolderEntry189((GuiScreenResourcePacks) (Object) this, sub.getFileName().toString()));
            }
            for (ResourcePackListEntry entry : ladsMasterAllAvailable) {
                String relPath = getEntryRelativePath(entry);
                if (!relPath.isEmpty() && selectedNames.contains(relPath)) continue;
                if (PackFolderHelper.isInsideFolder(relPath, ladsCurrentFolder, false)) {
                    availableResourcePacks.add(entry);
                }
            }
        } else {
            for (ResourcePackListEntry entry : ladsMasterAllAvailable) {
                String relPath = getEntryRelativePath(entry);
                if (!relPath.isEmpty() && selectedNames.contains(relPath)) continue;
                if (PackFolderHelper.isInsideFolder(relPath, ladsCurrentFolder, true)) {
                    String name = getEntryName(entry);
                    String desc = getEntryDesc(entry);
                    if (PackFilterHelper.matches(query, name, desc)) {
                        availableResourcePacks.add(entry);
                    }
                }
            }
        }

        ladsLastSelectedSnapshot.clear();
        ladsLastSelectedSnapshot.addAll(selectedResourcePacks);
    }

    @Unique
    private String getEntryName(ResourcePackListEntry entry) {
        if (entry instanceof ResourcePackListEntryFound) {
            return ((ResourcePackListEntryFound) entry).func_148318_i().getResourcePackName();
        }
        return "";
    }

    @Unique
    private String getEntryDesc(ResourcePackListEntry entry) {
        if (entry instanceof ResourcePackListEntryFound) {
            return ((ResourcePackListEntryFound) entry).func_148318_i().getTexturePackDescription();
        }
        return "";
    }

    @Unique
    private String getEntryRelativePath(ResourcePackListEntry entry) {
        return getEntryName(entry);
    }
}
