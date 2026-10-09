package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.v1_20_1.gui.Widgets1201;
import com.thelads.core.v1_20_1.gui.WorldSourcesScreen1201;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select World: the "World folders" button under the search field (Global / This Instance / Other Instance / custom saves). */
@Mixin(SelectWorldScreen.class)
public abstract class WorldFoldersMixin extends Screen {
    @Shadow @Final protected Screen lastScreen;
    @Shadow private WorldSelectionList list;
    @Unique private Button ladsFolders;

    protected WorldFoldersMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsFolders(CallbackInfo ci) {
        // The list starts 24 pixels lower than vanilla's, under the button.
        list.updateSize(width, height, 72, height - 64);
        ladsFolders = addRenderableWidget(Button.builder(Component.literal("World folders: Global / This Instance / Other Instance"),
            b -> minecraft.setScreen(new WorldSourcesScreen1201(lastScreen))).bounds(width / 2 - 155, 46, 310, 20).build());
    }
}
