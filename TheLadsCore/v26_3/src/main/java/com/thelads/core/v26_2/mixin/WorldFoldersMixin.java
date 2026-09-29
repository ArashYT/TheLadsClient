package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.gui.WorldSourcesScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class WorldFoldersMixin extends Screen {
    @Shadow @Final private HeaderAndFooterLayout layout;
    @Shadow @Final protected Screen lastScreen;
    @Unique private Button ladsFolders;
    protected WorldFoldersMixin(Component title){super(title);}
    @Inject(method="init",at=@At("TAIL"),require=1)
    private void ladsFolders(CallbackInfo ci){
        layout.setHeaderHeight(94);
        ladsFolders=addRenderableWidget(Button.builder(Component.literal("World folders: Global / Version / Instances"),
            b->minecraft.setScreenAndShow(new WorldSourcesScreen(lastScreen))).bounds(width/2-155,67,310,20).build());
        repositionElements();
    }
    @Inject(method="repositionElements",at=@At("TAIL"),require=1)
    private void ladsPosition(CallbackInfo ci){if(ladsFolders!=null){ladsFolders.setX(width/2-155);ladsFolders.setY(67);}}
}
