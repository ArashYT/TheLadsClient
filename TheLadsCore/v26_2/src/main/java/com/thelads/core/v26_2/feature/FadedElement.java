package com.thelads.core.v26_2.feature;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import com.mojang.blaze3d.pipeline.RenderPipeline;
public record FadedElement(GuiElementRenderState element,float alpha) implements GuiElementRenderState {
    public RenderPipeline pipeline(){return element.pipeline();}
    public TextureSetup textureSetup(){return element.textureSetup();}
    public ScreenRectangle scissorArea(){return element.scissorArea();}
    public ScreenRectangle bounds(){return element.bounds();}
    public void buildVertices(VertexConsumer out){element.buildVertices(new VertexConsumer(){
        public VertexConsumer addVertex(float x,float y,float z){out.addVertex(x,y,z);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){out.setColor(r,g,b,Math.round(a*alpha));return this;}
        public VertexConsumer setColor(int c){out.setColor(NativeAutohide.tint(c,alpha));return this;}
        public VertexConsumer setUv(float u,float v){out.setUv(u,v);return this;}
        public VertexConsumer setUv1(int u,int v){out.setUv1(u,v);return this;}
        public VertexConsumer setUv2(int u,int v){out.setUv2(u,v);return this;}
        public VertexConsumer setNormal(float x,float y,float z){out.setNormal(x,y,z);return this;}
        public VertexConsumer setLineWidth(float value){out.setLineWidth(value);return this;}
    });}
}
