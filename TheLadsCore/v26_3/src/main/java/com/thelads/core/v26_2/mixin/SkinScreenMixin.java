package com.thelads.core.v26_2.mixin;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;
import com.thelads.core.v26_2.gui.LadsButton;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(SkinCustomizationScreen.class)
public abstract class SkinScreenMixin extends OptionsSubScreen {
    protected SkinScreenMixin(Screen parent,Options options,Component title){super(parent,options,title);}
    @Override protected void init(){
        int left=Math.max(12,width/2-180),top=55,previewWidth=Math.min(150,width/3);
        var preview=new PlayerSkinWidget(previewWidth,Math.max(80,height-100),minecraft.getEntityModels(),minecraft.getSkinManager().createLookup(minecraft.getGameProfile(),false));
        preview.setX(left);preview.setY(top);addRenderableWidget(preview);
        int x=left+previewWidth+20,w=Math.max(90,Math.min(195,width-x-12));
        int row=0,step=Math.max(20,Math.min(27,(height-100)/8));
        for(var part:PlayerModelPart.values()){
            addRenderableWidget(new LadsButton(x,top+row++*step,w,20,part.getName().copy().append(options.isModelPartEnabled(part)?": ON":": OFF"),button->{
                options.setModelPart(part,!options.isModelPartEnabled(part));
                button.setMessage(part.getName().copy().append(options.isModelPartEnabled(part)?": ON":": OFF"));
            }));
        }
        addRenderableWidget(new LadsButton(x,top+row*step,w,20,Component.literal("Main hand: "+options.mainHand().get().getSerializedName()),button->{
            options.mainHand().set(options.mainHand().get()==net.minecraft.world.entity.HumanoidArm.RIGHT?net.minecraft.world.entity.HumanoidArm.LEFT:net.minecraft.world.entity.HumanoidArm.RIGHT);
            button.setMessage(Component.literal("Main hand: "+options.mainHand().get().getSerializedName()));
        }));
        addRenderableWidget(new LadsButton(width/2-55,height-29,110,20,Component.literal("Done"),button->{options.save();onClose();}));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xFF100B10);g.fill(0,0,width,2,0xFFCF1535);
        int left=Math.max(12,width/2-180);g.fill(left-4,49,left+Math.min(150,width/3)+4,height-40,0xFF24151D);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,Component.literal("THE LADS / SKIN CUSTOMIZATION"),width/2,15,0xFFFFFFFF);
        g.centeredText(font,Component.literal("Drag your skin preview to rotate"),width/2,32,0xFFB7A5AF);
    }
}
