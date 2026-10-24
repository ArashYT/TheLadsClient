package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Unique private GridLayout.RowHelper ladsPauseRows;
    @Unique private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> ladsExtras=new java.util.ArrayList<>();
    @Unique private Button ladsExtrasButton;
    @Unique private Button ladsFullscreenButton;
    @Unique private boolean ladsLayoutReady;

    @Unique private void ladsLayout() {
        if(!((PauseScreen)(Object)this).showsPauseMenu())return;
        boolean changed=!ladsLayoutReady;

        for(var child:java.util.List.copyOf(children()))if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget
            &&widget!=ladsFullscreenButton) {
            if(widget.getClass().getName().startsWith("gg.essential.")) {
                widget.visible=false;removeWidget(widget);changed=true;
            } else if(widget instanceof Button&&widget!=ladsExtrasButton&&widget.getWidth()<=30) {
                if(!ladsExtras.contains(widget))ladsExtras.add(widget);widget.visible=false;removeWidget(widget);changed=true;
            }
        }

        if(!changed)return;
        ladsLayoutReady=true;
        if(!ladsExtras.isEmpty()&&ladsExtrasButton==null)ladsExtrasButton=addRenderableWidget(Button.builder(Component.literal("Extras..."),b->minecraft.setScreenAndShow(new com.thelads.core.v26_2.gui.TitleExtrasScreen26(this,ladsExtras))).bounds(0,0,204,20).build());
        var buttons=children().stream().filter(c->c instanceof Button&&c!=ladsFullscreenButton).map(c->(Button)c).toList();
        int bottom=height-32;
        int top=Math.max(62,12+Math.min(64,height/6)+18);
        var slots=buttons.stream().map(this::ladsSlot).toList();
        var boxes=com.thelads.core.client.title.PauseMenuLayout.arrange(slots,width,top,bottom);
        for(int i=0;i<buttons.size();i++) {
            var widget=buttons.get(i);var box=boxes.get(i);
            widget.setX(box.x());widget.setY(box.y());widget.setWidth(box.width());widget.setHeight(box.height());
            com.thelads.core.client.title.ButtonLift.enable(widget,com.thelads.core.client.title.PauseMenuLayout.icon(slots.get(i)));
        }
    }

    @Unique private com.thelads.core.client.title.PauseMenuLayout.Slot ladsSlot(Button button){
        String text=button.getMessage().getString();
        if(button==ladsExtrasButton)return com.thelads.core.client.title.PauseMenuLayout.Slot.EXTRAS;
        if(text.equals("Lads Client"))return com.thelads.core.client.title.PauseMenuLayout.Slot.LADS;
        if(text.equals("Screenshots"))return com.thelads.core.client.title.PauseMenuLayout.Slot.SCREENSHOTS;
        if(text.equals("Cosmetics"))return com.thelads.core.client.title.PauseMenuLayout.Slot.COSMETICS;
        if(text.equals("Replays"))return com.thelads.core.client.title.PauseMenuLayout.Slot.REPLAYS;
        if(text.contains("Host")||text.contains("Invite"))return com.thelads.core.client.title.PauseMenuLayout.Slot.WORLD;
        String[][] keys={{"menu.returnToGame","BACK"},{"gui.advancements","ADVANCEMENTS"},{"gui.stats","STATS"},{"menu.options","OPTIONS"},
            {"menu.multiplayer","MULTIPLAYER"},{"menu.multiplayerOptions.button","WORLD"},{"options.worldOptions.button","WORLD"},{"menu.worldOptions","WORLD"},{"menu.shareToLan","WORLD"},{"menu.returnToMenu","QUIT"},{"menu.disconnect","QUIT"}};
        for(String[] key:keys)if(text.equals(Component.translatable(key[0]).getString()))
            return com.thelads.core.client.title.PauseMenuLayout.Slot.valueOf(key[1]);
        return com.thelads.core.client.title.PauseMenuLayout.Slot.OTHER;
    }

    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsPauseTheme(net.minecraft.client.gui.GuiGraphicsExtractor g,int mx,int my,float dt,CallbackInfo ci){
        if(!((PauseScreen)(Object)this).showsPauseMenu())return;
        ladsLayout();
        g.fill(0,0,width,height,0xB8100B10);
        g.fill(0,0,width,2,0xFFCF1535);
        var adapter=new com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter(g,font);
        com.thelads.core.client.title.TitleScreenTheme.renderLogo(adapter,width/2,12,Math.min(64,height/6));
        g.fill(16,height-29,width-16,height-28,0x2944202A);
        com.thelads.core.client.title.TitleScreenTheme.renderAccount(adapter,height,minecraft.getUser().getName(),Math.min(170,width-32));
        if (!com.thelads.core.client.ConfirmDisconnectHelper.isConfirming(System.currentTimeMillis())) {
            for (var child : children()) {
                if (child instanceof Button b && b.getMessage().getString().contains("Confirm Disconnect?")) {
                    b.setMessage(Component.translatable(minecraft != null && !minecraft.isLocalServer() ? "menu.disconnect" : "menu.returnToMenu"));
                }
            }
        }
        super.extractRenderState(g,mx,my,dt);
        ci.cancel();
    }

    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Inject(method="init",at=@At("TAIL"),require=1)
    private void ladsRemoveReportButtons(CallbackInfo ci){
        ladsLayoutReady=false;ladsExtras.clear();ladsExtrasButton=null;ladsFullscreenButton=null;
        if(((PauseScreen)(Object)this).showsPauseMenu())
            ladsFullscreenButton=addRenderableWidget(new com.thelads.core.v26_2.gui.CompactButton26(width-26,6,20,20,Component.translatable("options.fullscreen"),
                ()->minecraft.options.fullscreen().get()?"windowed":"fullscreen",com.thelads.core.v26_2.gui.CompactButton26::toggleFullscreen));
        for(var child:java.util.List.copyOf(children()))if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget){
            String text=widget.getMessage().getString();
            if(widget instanceof net.minecraft.client.gui.components.StringWidget){removeWidget(widget);continue;}
            if(text.equals(Component.translatable("menu.sendFeedback").getString())
                ||text.equals(Component.translatable("menu.reportBugs").getString())
                ||text.equals(Component.translatable("menu.playerReporting").getString()))removeWidget(widget);
        }
        for(var child:java.util.List.copyOf(children()))if(child instanceof Button btn && ladsSlot(btn)==com.thelads.core.client.title.PauseMenuLayout.Slot.QUIT){
            removeWidget(btn);
            addRenderableWidget(Button.builder(btn.getMessage(), b -> {
                boolean isMultiplayer = minecraft != null && !minecraft.isLocalServer();
                com.thelads.core.config.Module mod = com.thelads.core.config.ModuleManager.getInstance().getModule("ConfirmDisconnect");
                boolean enabled = mod instanceof com.thelads.core.modules.ConfirmDisconnectModule cdm && cdm.isEnabled();
                long timeout = mod instanceof com.thelads.core.modules.ConfirmDisconnectModule cdm ? cdm.getTimeoutMillis() : 3000L;
                if (!com.thelads.core.client.ConfirmDisconnectHelper.onClickDisconnect(System.currentTimeMillis(), timeout, isMultiplayer, enabled)) {
                    b.setMessage(Component.literal("\u00a7cConfirm Disconnect?"));
                    return;
                }
                btn.onPress(null);
            }).bounds(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight()).build());
        }
    }

    @Redirect(method = "createPauseMenu()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout;createRowHelper(I)Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"),
        require = 1)
    private GridLayout.RowHelper ladsCapturePauseRows(GridLayout grid, int columns) {
        ladsPauseRows = grid.createRowHelper(columns);
        return ladsPauseRows;
    }

    @Inject(method = "createPauseMenu()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"),
        require = 1)
    private void ladsAddPauseButton(CallbackInfo ci) {
        ladsPauseRows.addChild(Button.builder(Component.literal("Lads Client"),
            button -> minecraft.gui.setScreen(new LadsSettingsScreen26(this))).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.translatable("menu.multiplayer"),
            button -> com.thelads.core.v26_2.gui.PauseMultiplayer.open(this)).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.literal("Screenshots"),
            button -> com.thelads.core.v26_2.feature.screenshots.ScreenshotViewer.openFromMenu(this)).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.literal("Cosmetics"),
            button -> minecraft.setScreenAndShow(new com.thelads.core.v26_2.cosmetics.anim.CosmeticsScreen26(this))).width(204).build(), 2);
        if(com.thelads.core.v26_2.gui.FlashbackScreens.available())
            ladsPauseRows.addChild(Button.builder(Component.literal("Replays"),
                button -> com.thelads.core.v26_2.gui.FlashbackScreens.open(this)).width(204).build(),2);
        ladsPauseRows = null;
        LoggerFactory.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
    }
}
