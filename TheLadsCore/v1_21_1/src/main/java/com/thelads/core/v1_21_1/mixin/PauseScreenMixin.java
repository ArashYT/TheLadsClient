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

/** The 26.x pause menu: Lads theme and logo, a 1-2 column grid, Essential's actions in a row above the account name, other extras behind "Extras...", Lads/Multiplayer/Replays rows. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Unique private final java.util.List<AbstractWidget> ladsExtras=new java.util.ArrayList<>();
    @Unique private final java.util.List<com.thelads.core.v1_21_1.gui.EssentialActions.Action> ladsEssentialActions=new java.util.ArrayList<>();
    @Unique private java.util.List<AbstractWidget> ladsEssentialRow=new java.util.ArrayList<>();
    @Unique private final java.util.List<AbstractWidget> ladsEssentialPending=new java.util.ArrayList<>();
    @Unique private long ladsInitNanos;
    @Unique private Button ladsExtrasButton;
    @Unique private Button ladsFullscreenButton;
    @Unique private boolean ladsLayoutReady;

    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Unique private void ladsLayout() {
        if(!((PauseScreen)(Object)this).showsPauseMenu())return;
        boolean changed=!ladsLayoutReady;
        // Essential binds its buttons a moment after the screen opens; the row is rebuilt once they are ready.
        if(!ladsEssentialPending.isEmpty()&&System.nanoTime()-ladsInitNanos<5_000_000_000L
            &&ladsEssentialPending.removeIf(w->com.thelads.core.v1_21_1.gui.EssentialRow121.collect(this,w,ladsEssentialActions)))changed=true;
        for(var child:java.util.List.copyOf(children()))if(child instanceof AbstractWidget widget
            &&widget!=ladsFullscreenButton&&!ladsEssentialRow.contains(widget)) {
            if(widget.getClass().getName().startsWith("gg.essential.")) {
                // Essential's actions get their own row above the account name; its proxies stay hidden.
                widget.visible=false;removeWidget(widget);changed=true;
                if(!com.thelads.core.v1_21_1.gui.EssentialRow121.collect(this,widget,ladsEssentialActions)&&!ladsEssentialPending.contains(widget))ladsEssentialPending.add(widget);
            } else if(widget instanceof Button&&widget!=ladsExtrasButton&&widget.getWidth()<=30) {
                if(!ladsExtras.contains(widget))ladsExtras.add(widget);widget.visible=false;removeWidget(widget);changed=true;
            }
        }
        if(!changed)return;
        ladsLayoutReady=true;
        if(!ladsExtras.isEmpty()&&ladsExtrasButton==null)ladsExtrasButton=addRenderableWidget(Button.builder(Component.literal("Extras..."),b->minecraft.setScreen(new com.thelads.core.v1_21_1.gui.TitleExtrasScreen121(this,ladsExtras))).bounds(0,0,204,20).build());
        ladsEssentialRow.forEach(w->removeWidget(w));
        ladsEssentialRow=com.thelads.core.v1_21_1.gui.EssentialRow121.place(w->addRenderableWidget(w),ladsEssentialActions,height,width-32);
        var buttons=children().stream().filter(c->c instanceof Button&&c!=ladsFullscreenButton&&!ladsEssentialRow.contains(c)).map(c->(Button)c).toList();
        // In groups: Back to Game, then Advancements/Statistics, Options/Lads Client, Multiplayer/world options, Replays/Extras, Save and Quit apart.
        int bottom=height-32-(ladsEssentialActions.isEmpty()?0:com.thelads.core.client.title.TitleScreenTheme.ROW_SPACE);
        int top=Math.max(62,12+Math.min(64,height/6)+18);
        var boxes=com.thelads.core.client.title.PauseMenuLayout.arrange(buttons.stream().map(this::ladsSlot).toList(),width,top,bottom);
        for(int i=0;i<buttons.size();i++) {
            var widget=buttons.get(i);var box=boxes.get(i);
            widget.setX(box.x());widget.setY(box.y());widget.setWidth(box.width());widget.setHeight(box.height());
            com.thelads.core.client.title.ButtonLift.enable(widget);
        }
    }
    @Unique private com.thelads.core.client.title.PauseMenuLayout.Slot ladsSlot(Button button){
        String text=button.getMessage().getString();
        if(button==ladsExtrasButton)return com.thelads.core.client.title.PauseMenuLayout.Slot.EXTRAS;
        if(text.equals("Lads Client"))return com.thelads.core.client.title.PauseMenuLayout.Slot.LADS;
        if(text.equals("Replays"))return com.thelads.core.client.title.PauseMenuLayout.Slot.REPLAYS;
        String[][] keys={{"menu.returnToGame","BACK"},{"gui.advancements","ADVANCEMENTS"},{"gui.stats","STATS"},{"menu.options","OPTIONS"},
            {"menu.multiplayer","MULTIPLAYER"},{"menu.worldOptions","WORLD"},{"menu.shareToLan","WORLD"},{"menu.returnToMenu","QUIT"},{"menu.disconnect","QUIT"}};
        for(String[] key:keys)if(text.equals(Component.translatable(key[0]).getString()))
            return com.thelads.core.client.title.PauseMenuLayout.Slot.valueOf(key[1]);
        return com.thelads.core.client.title.PauseMenuLayout.Slot.OTHER;
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
        g.fill(16,height-29,width-16,height-28,0x2944202A);
        com.thelads.core.client.title.TitleScreenTheme.renderAccount(adapter,height,minecraft.getUser().getName(),Math.min(170,width-32));
    }

    @Inject(method="init",at=@At("TAIL"),require=1)
    private void ladsRemoveReportButtons(CallbackInfo ci){
        ladsLayoutReady=false;ladsExtras.clear();ladsExtrasButton=null;
        ladsEssentialActions.clear();ladsEssentialRow=new java.util.ArrayList<>();ladsFullscreenButton=null;
        ladsEssentialPending.clear();ladsInitNanos=System.nanoTime();
        if(((PauseScreen)(Object)this).showsPauseMenu())
            ladsFullscreenButton=addRenderableWidget(new com.thelads.core.v1_21_1.gui.CompactButton121(width-26,6,20,20,Component.translatable("options.fullscreen"),
                ()->minecraft.options.fullscreen().get()?"windowed":"fullscreen",com.thelads.core.v1_21_1.gui.CompactButton121::toggleFullscreen));
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
