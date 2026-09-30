package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_21_1.gui.LadsSettingsScreen121;
import com.thelads.core.v1_21_1.gui.PauseMultiplayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The 26.x pause menu: Lads theme and logo, a 1-2 column grid, extras behind "Essential & extras...", Lads/Multiplayer/Replays rows. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Unique private final java.util.List<AbstractWidget> ladsExtras=new java.util.ArrayList<>();
    @Unique private Button ladsExtrasButton;
    @Unique private boolean ladsLayoutReady;

    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Unique private void ladsLayout() {
        if(!((PauseScreen)(Object)this).showsPauseMenu())return;
        boolean changed=!ladsLayoutReady;
        for(var child:java.util.List.copyOf(children()))if(child instanceof AbstractWidget widget
            &&(widget.getClass().getName().startsWith("gg.essential.")
                || widget instanceof Button&&widget!=ladsExtrasButton&&widget.getWidth()<=30)) {
            if(!ladsExtras.contains(widget))ladsExtras.add(widget);widget.visible=false;removeWidget(widget);changed=true;
        }
        if(!changed)return;
        ladsLayoutReady=true;
        if(!ladsExtras.isEmpty()&&ladsExtrasButton==null)ladsExtrasButton=addRenderableWidget(Button.builder(Component.literal("Essential & extras..."),b->minecraft.setScreen(new com.thelads.core.v1_21_1.gui.TitleExtrasScreen121(this,ladsExtras))).bounds(0,0,204,20).build());
        var widgets=children().stream().filter(c->c instanceof Button).map(c->(Button)c)
            .sorted(java.util.Comparator.comparingInt(this::ladsOrder)).toList();
        int columns=width<380?1:2;
        int total=Math.min(width-32,360),gap=6,cw=(total-gap*(columns-1))/columns;
        int rows=(widgets.size()+columns-1)/columns;
        int top=Math.max(62,Math.min(height/3,height-rows*29-12));
        int rowHeight=Math.max(17,Math.min(27,(height-top-10)/Math.max(1,rows)-3));
        for(int i=0;i<widgets.size();i++) {
            var widget=widgets.get(i);widget.setX((width-total)/2+i%columns*(cw+gap));widget.setY(top+i/columns*(rowHeight+3));widget.setWidth(cw);widget.setHeight(rowHeight);
        }
    }
    @Unique private int ladsOrder(Button button){
        String text=button.getMessage().getString();
        String[] keys={"menu.returnToGame","gui.advancements","gui.stats","menu.options","menu.worldOptions","menu.returnToMenu","menu.disconnect"};
        for(int i=0;i<keys.length;i++)if(text.equals(Component.translatable(keys[i]).getString()))return i;
        if(text.equals(Component.translatable("menu.multiplayer").getString()))return 3;
        if(text.equals("Lads Client"))return 7;return 8;
    }
    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",at=@At("HEAD"),require=1)
    private void ladsPauseLayout(GuiGraphics g,int mx,int my,float dt,CallbackInfo ci){
        if(!((PauseScreen)(Object)this).showsPauseMenu())return;
        com.thelads.core.v1_21_1.gui.EssentialActions.suppressOverlay(this);
        ladsLayout();
    }
    /** 1.21.1 Screen.render draws the background (world blur, menu texture) itself; the Lads theme goes on top of it, under the buttons. */
    @Inject(method="renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",at=@At("TAIL"),require=1)
    private void ladsPauseTheme(GuiGraphics g,int mx,int my,float dt,CallbackInfo ci){
        if(!((PauseScreen)(Object)this).showsPauseMenu())return;
        g.fill(0,0,width,height,0xB8100B10);
        g.fill(0,0,width,2,0xFFCF1535);
        var adapter=new com.thelads.core.v1_21_1.adapter.GuiGraphicsLadsAdapter(g,font);
        com.thelads.core.client.title.TitleScreenTheme.renderLogo(adapter,width/2,12,Math.min(64,height/6));
    }

    @Inject(method="init",at=@At("TAIL"),require=1)
    private void ladsRemoveReportButtons(CallbackInfo ci){
        ladsLayoutReady=false;ladsExtras.clear();ladsExtrasButton=null;
        for(var child:java.util.List.copyOf(children()))if(child instanceof AbstractWidget widget){
            String text=widget.getMessage().getString();
            // The "Game Menu" title, and any mod caption (Flashback's), would sit at vanilla grid positions under the Lads grid.
            if(widget instanceof net.minecraft.client.gui.components.AbstractStringWidget){removeWidget(widget);continue;}
            if(text.equals(Component.translatable("menu.sendFeedback").getString())
                ||text.equals(Component.translatable("menu.reportBugs").getString())
                ||text.equals(Component.translatable("menu.playerReporting").getString()))removeWidget(widget);
        }
    }

    @Inject(method = "createPauseMenu()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"),
        require = 1)
    private void ladsAddPauseButtons(CallbackInfo ci, @Local GridLayout.RowHelper rows) {
        // Let vanilla lay out and register the extra rows with all original buttons (Flashback's rows follow them).
        // This method is not called by PauseScreen(false), which intentionally has no menu.
        rows.addChild(Button.builder(Component.literal("Lads Client"),
            button -> minecraft.setScreen(new LadsSettingsScreen121(this))).width(204).build(), 2);
        rows.addChild(Button.builder(Component.translatable("menu.multiplayer"),
            button -> PauseMultiplayer.open(this)).width(204).build(), 2);
        if(com.thelads.core.v1_21_1.gui.FlashbackScreens.available())
            rows.addChild(Button.builder(Component.literal("Replays"),
                button -> com.thelads.core.v1_21_1.gui.FlashbackScreens.open(this)).width(204).build(),2);
        LoggerFactory.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
    }
}
