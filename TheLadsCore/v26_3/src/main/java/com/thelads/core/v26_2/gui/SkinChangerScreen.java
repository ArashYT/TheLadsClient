package com.thelads.core.v26_2.gui;
import com.thelads.core.v26_2.feature.LocalSkins;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class SkinChangerScreen extends Screen {
    private final Screen parent;private EditBox source;private boolean slim,busy;private String status="Skins are applied locally to your client.";
    public SkinChangerScreen(Screen parent){super(Component.literal("Skin changer"));this.parent=parent;slim=LocalSkins.current()!=null&&LocalSkins.current().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;}
    @Override protected void init(){
        var original=minecraft.getSkinManager().createLookup(minecraft.getGameProfile(),false);
        var preview=new PlayerSkinWidget(110,Math.max(70,Math.min(220,height-140)),minecraft.getEntityModels(),()->LocalSkins.current()==null?original.get():LocalSkins.current());
        preview.setX(Math.max(8,width/2-200));preview.setY(60);addRenderableWidget(preview);
        int x=Math.max(128,width/2-60),w=Math.min(245,width-x-12);
        source=new EditBox(font,x,57,w,20,Component.literal("Username, UUID or skin URL"));source.setMaxLength(2048);source.setHint(Component.literal("Username, UUID or URL"));addRenderableWidget(source);
        addRenderableWidget(new LadsButton(x,85,w,20,Component.literal("Load skin"),b->load(source.getValue())));
        addRenderableWidget(new LadsButton(x,113,w,20,Component.literal("Add File"),b->{if(busy)return;busy=true;status="Choose a skin PNG in the file dialog";NativeFileDialogs.choose(false).whenComplete((file,error)->minecraft.execute(()->{busy=false;if(error!=null)status="File dialog failed";else if(file!=null)load(file.toString());else status="Selection canceled";}));}));
        addRenderableWidget(new LadsButton(x,141,w,20,Component.literal("Model: "+(slim?"Slim":"Classic")),b->{if(busy)return;slim=!slim;LocalSkins.setModel(slim);b.setMessage(Component.literal("Model: "+(slim?"Slim":"Classic")));}));
        addRenderableWidget(new LadsButton(x,169,w,20,Component.literal("Use account skin"),b->{LocalSkins.reset();status="Using account skin";}));
        addRenderableWidget(new LadsButton(width/2-60,height-28,120,20,Component.literal("Done"),b->onClose()));
    }
    private void load(String input){if(busy||input.isBlank())return;busy=true;status="Loading skin...";LocalSkins.load(input,slim,true).whenComplete((message,error)->minecraft.execute(()->{busy=false;status=error==null?message:"Could not load skin: "+error.getMessage();}));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){g.fill(0,0,width,height,0xFF100B10);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){super.extractRenderState(g,mx,my,dt);g.centeredText(font,title,width/2,18,-1);g.centeredText(font,Component.literal(status),width/2,height-46,0xFFCFB5BF);}
    @Override public void onClose(){minecraft.setScreenAndShow(parent);}
}
