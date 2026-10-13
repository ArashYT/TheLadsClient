package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.pack.PackFilterHelper;
import com.thelads.core.client.pack.PackFolderHelper;
import com.thelads.core.client.pack.PackMenuSettings;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.gui.FolderNav1201;
import com.thelads.core.v1_20_1.gui.FolderPackEntry1201;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen implements FolderNav1201 {
    @Shadow private TransferableSelectionList availablePackList;
    @Shadow private TransferableSelectionList selectedPackList;
    @Shadow @Final private PackSelectionModel model;
    @Shadow @Final private Path packDir;
    @Shadow private void populateLists() {}

    @Unique private EditBox ladsSearch;
    @Unique private String ladsCurrentFolder = "";

    protected PackSelectionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void ladsInit(CallbackInfo ci) {
        if (PackMenuSettings.isTransparent()) {
            if (availablePackList != null) {
                availablePackList.setRenderBackground(false);
                availablePackList.setRenderTopAndBottom(false);
            }
            if (selectedPackList != null) {
                selectedPackList.setRenderBackground(false);
                selectedPackList.setRenderTopAndBottom(false);
            }
        }

        ladsSearch = new EditBox(font, width / 2 - 100, 8, 200, 16, Component.literal("Search"));
        ladsSearch.setHint(Component.literal("Search packs..."));
        ladsSearch.setResponder(query -> populateLists());
        addRenderableWidget(ladsSearch);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/packs/PackSelectionScreen;renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void ladsPackBackground(PackSelectionScreen screen, GuiGraphics graphics) {
        if (PackMenuSettings.isTransparent()) {
            if (minecraft.level == null) {
                TitleScreenTheme.Layout layout = TitleScreenTheme.layout(width, height, 0, false);
                var adapter = new GuiGraphicsLadsAdapter1201(graphics, font);
                TitleScreenTheme.renderBackground(adapter, layout, minecraft.getUser().getName(), "1.20.1", false,
                    (System.currentTimeMillis() % 1000000L) / 1000.0, false);
            }
            return;
        }
        screen.renderDirtBackground(graphics);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ladsRenderReturn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        graphics.drawString(font, "§7Folder: §f/" + ladsCurrentFolder, width / 2 - 200, 12, 0xFFFFFF);
    }

    @Inject(method = "updateList", at = @At("HEAD"), cancellable = true)
    private void ladsUpdateList(TransferableSelectionList list, Stream<PackSelectionModel.Entry> entries, CallbackInfo ci) {
        list.children().clear();
        String query = ladsSearch == null ? "" : ladsSearch.getValue().trim();

        if (list == selectedPackList) {
            entries.forEach(entry -> {
                if (PackFilterHelper.matches(query, entry.getTitle().getString(), entry.getDescription().getString())) {
                    list.children().add(new TransferableSelectionList.PackEntry(minecraft, list, entry));
                }
            });
            ci.cancel();
            return;
        }

        if (list == availablePackList) {
            if (query.isEmpty()) {
                if (!ladsCurrentFolder.isEmpty()) {
                    list.children().add(new FolderPackEntry1201(minecraft, list, "..", true, this));
                }
                Path currentPath = ladsCurrentFolder.isEmpty() ? packDir : packDir.resolve(ladsCurrentFolder);
                List<Path> subfolders = PackFolderHelper.findSubfolders(currentPath);
                for (Path sub : subfolders) {
                    list.children().add(new FolderPackEntry1201(minecraft, list, sub.getFileName().toString(), false, this));
                }
                entries.forEach(entry -> {
                    String rel = ladsGetRelPath(entry);
                    if (PackFolderHelper.isInsideFolder(rel, ladsCurrentFolder, false)) {
                        list.children().add(new TransferableSelectionList.PackEntry(minecraft, list, entry));
                    }
                });
            } else {
                entries.forEach(entry -> {
                    String rel = ladsGetRelPath(entry);
                    if (PackFolderHelper.isInsideFolder(rel, ladsCurrentFolder, true)) {
                        if (PackFilterHelper.matches(query, entry.getTitle().getString(), entry.getDescription().getString())) {
                            list.children().add(new TransferableSelectionList.PackEntry(minecraft, list, entry));
                        }
                    }
                });
            }
            ci.cancel();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ladsSearch != null && ladsSearch.isFocused()) {
            if (keyCode == 256) { // Esc closes
                onClose();
                return true;
            }
            if (ladsSearch.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (ladsSearch != null && ladsSearch.isFocused()) {
            if (ladsSearch.charTyped(codePoint, modifiers)) {
                return true;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Redirect(method = "method_29670", at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;toUri()Ljava/net/URI;"))
    private URI ladsOpenSubfolderUri(Path path) {
        if (ladsCurrentFolder != null && !ladsCurrentFolder.isEmpty()) {
            Path sub = path.resolve(ladsCurrentFolder);
            try {
                if (!Files.exists(sub)) Files.createDirectories(sub);
            } catch (Exception ignored) {}
            return sub.toUri();
        }
        return path.toUri();
    }

    @Override
    public void ladsCd(String folder) {
        if (ladsCurrentFolder == null || ladsCurrentFolder.isEmpty()) {
            ladsCurrentFolder = folder;
        } else {
            ladsCurrentFolder = ladsCurrentFolder + "/" + folder;
        }
        populateLists();
    }

    @Override
    public void ladsCdUp() {
        ladsCurrentFolder = PackFolderHelper.getParentRelativePath(ladsCurrentFolder);
        if (ladsCurrentFolder == null) {
            ladsCurrentFolder = "";
        }
        populateLists();
    }

    @Unique
    private String ladsGetRelPath(PackSelectionModel.Entry entry) {
        String id = entry.getId();
        if (id != null && id.startsWith("file/")) {
            return id.substring(5);
        }
        return id != null ? id : "";
    }
}
