package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.pack.PackFilterHelper;
import com.thelads.core.client.pack.PackFolderHelper;
import com.thelads.core.client.pack.PackMenuSettings;
import com.thelads.core.v26_2.gui.FolderNav26;
import com.thelads.core.v26_2.gui.FolderPackEntry26;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

@Mixin(PackSelectionScreen.class)
public abstract class PackFilterMixin extends Screen implements FolderNav26 {
    @Shadow private EditBox search;
    @Shadow protected abstract void updateFilteredEntries(String value);
    @Shadow private Path packDir;
    @Shadow private TransferableSelectionList availablePackList;
    @Shadow private TransferableSelectionList selectedPackList;
    @Shadow @Final private static Component OPEN_PACK_FOLDER_TITLE;

    @Unique private int ladsFilter;
    @Unique private boolean ladsSort;
    @Unique private String ladsCurrentSubfolder = "";
    @Unique private String ladsCachedSubfoldersDir = null;
    @Unique private List<Path> ladsCachedSubfolders = java.util.Collections.emptyList();

    protected PackFilterMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void ladsFilters(CallbackInfo ci) {
        ladsCachedSubfoldersDir = null;
        addRenderableWidget(Button.builder(Component.literal(ladsFilterLabel()), b -> {
            ladsFilter = (ladsFilter + 1) % 3;
            b.setMessage(Component.literal(ladsFilterLabel()));
            updateFilteredEntries(search.getValue());
        }).bounds(6, 5, 112, 20).build());

        addRenderableWidget(Button.builder(Component.literal(ladsSort ? "Sort: name" : "Sort: default"), b -> {
            ladsSort = !ladsSort;
            b.setMessage(Component.literal(ladsSort ? "Sort: name" : "Sort: default"));
            updateFilteredEntries(search.getValue());
        }).bounds(width - 118, 5, 112, 20).build());
    }

    @Unique
    private String ladsFilterLabel() {
        return switch (ladsFilter) {
            case 1 -> "Version: compatible";
            case 2 -> "Version: incompatible";
            default -> "Version: all";
        };
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;"))
    private Button.Builder ladsRedirectButtonBuilder(Component message, Button.OnPress original) {
        if (message.equals(OPEN_PACK_FOLDER_TITLE)) {
            return Button.builder(message, b -> {
                Path target = (ladsCurrentSubfolder != null && !ladsCurrentSubfolder.isEmpty())
                    ? this.packDir.resolve(ladsCurrentSubfolder)
                    : this.packDir;
                com.mojang.blaze3d.Blaze3D.openPath(target);
            });
        }
        return Button.builder(message, original);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (PackMenuSettings.isTransparent()) {
            if (this.minecraft.level == null) {
                this.extractPanorama(graphics, partialTick);
            }
            return;
        }
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.availablePackList != null) {
            String label = "Folder: /" + ladsCurrentSubfolder;
            graphics.text(this.minecraft.font, label, this.availablePackList.getX() + 2, this.availablePackList.getY() - 11, 0xFFFF55);
        }
    }

    @Inject(method = "filterEntries", at = @At("HEAD"), cancellable = true)
    private void ladsFilterEntries(String query, Stream<PackSelectionModel.Entry> stream, TransferableSelectionList list, PackSelectionModel.EntryBase focused, CallbackInfo ci) {
        if (list == null) return;
        ci.cancel();

        boolean isAvailable = (list == this.availablePackList);
        boolean searching = query != null && !query.isBlank();

        Stream<PackSelectionModel.Entry> filtered = stream.filter(e -> {
            if (!e.isSelected() && ladsFilter != 0
                    && (e.getCompatibility() == net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE) != (ladsFilter == 1)) {
                return false;
            }
            if (isAvailable) {
                String id = e.getId();
                if (id.startsWith("file/")) {
                    String relPath = id.substring(5);
                    if (!PackFolderHelper.isInsideFolder(relPath, ladsCurrentSubfolder, searching)) {
                        return false;
                    }
                } else {
                    if (!ladsCurrentSubfolder.isEmpty()) {
                        return false;
                    }
                }
            }
            if (searching) {
                return PackFilterHelper.matches(query, e.getTitle().getString(), e.getDescription().getString())
                    || e.getId().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT));
            }
            return true;
        });

        if (ladsSort && isAvailable) {
            filtered = filtered.sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getTitle().getString(), b.getTitle().getString()));
        }

        list.updateList(filtered, focused);

        if (isAvailable) {
            int insertIndex = 1;
            if (!ladsCurrentSubfolder.isEmpty()) {
                FolderPackEntry26 back = new FolderPackEntry26(list, this.minecraft, "..", true, this);
                back.setX(list.getRowLeft());
                back.setWidth(list.getRowWidth());
                back.setHeight(36);
                ((AbstractSelectionListAccessor) list).ladsGetChildren().add(insertIndex++, back);
            }

            if (!ladsCurrentSubfolder.equals(ladsCachedSubfoldersDir)) {
                Path currentDir = ladsCurrentSubfolder.isEmpty() ? this.packDir : this.packDir.resolve(ladsCurrentSubfolder);
                ladsCachedSubfolders = PackFolderHelper.findSubfolders(currentDir);
                ladsCachedSubfoldersDir = ladsCurrentSubfolder;
            }
            for (Path sub : ladsCachedSubfolders) {
                String folderName = sub.getFileName().toString();
                if (!searching || folderName.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) {
                    FolderPackEntry26 folderEntry = new FolderPackEntry26(list, this.minecraft, folderName, false, this);
                    folderEntry.setX(list.getRowLeft());
                    folderEntry.setWidth(list.getRowWidth());
                    folderEntry.setHeight(36);
                    ((AbstractSelectionListAccessor) list).ladsGetChildren().add(insertIndex++, folderEntry);
                }
            }
            ((AbstractSelectionListAccessor) list).ladsInvokeRepositionEntries();
        }
    }

    @Override
    public void ladsCd(String folderName) {
        if (folderName == null || folderName.isEmpty()) return;
        this.ladsCurrentSubfolder = this.ladsCurrentSubfolder.isEmpty() ? folderName : this.ladsCurrentSubfolder + "/" + folderName;
        updateFilteredEntries(this.search.getValue());
    }

    @Override
    public void ladsCdUp() {
        String parent = PackFolderHelper.getParentRelativePath(this.ladsCurrentSubfolder);
        this.ladsCurrentSubfolder = (parent == null) ? "" : parent;
        updateFilteredEntries(this.search.getValue());
    }
}
