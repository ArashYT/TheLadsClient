package com.thelads.core.v26_2.feature;
import com.thelads.core.v26_2.mixin.ModelPartAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.PlayerSkin;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
/** Pixel-extruded outer layers for the native preview. Each voxel samples its own skin pixel. */
public final class PreviewSkinLayers {
    public static boolean enabled(){
        var module=com.thelads.core.config.ModuleManager.getInstance().getModule("SkinLayers");
        return module==null||!(module.getOption("3D Preview") instanceof com.thelads.core.config.BoolOption option)||option.get();
    }
    public static void toggle(){
        var module=com.thelads.core.config.ModuleManager.getInstance().getModule("SkinLayers");
        if(module!=null&&module.getOption("3D Preview") instanceof com.thelads.core.config.BoolOption option){option.set(!option.get());com.thelads.core.config.ConfigManager.save();}
    }
    private static final Map<String,CompletableFuture<BufferedImage>> IMAGES=new LinkedHashMap<>();
    private static final Map<ModelPart,List<ModelPart.Cube>> ORIGINAL=new WeakHashMap<>();
    private static final Map<ModelPart,String> APPLIED=new WeakHashMap<>();
    private static final String[][] PARTS={{"head","hat"},{"body","jacket"},{"left_arm","left_sleeve"},{"right_arm","right_sleeve"},{"left_leg","left_pants"},{"right_leg","right_pants"}};
    private PreviewSkinLayers(){}
    public static void apply(Model.Simple model,PlayerSkin skin){
        if(skin==null)return;
        String key=skin.body().texturePath().toString();
        BufferedImage image=null;
        if(enabled()){
            if(!IMAGES.containsKey(key)){
                if(IMAGES.size()>=12)IMAGES.remove(IMAGES.keySet().iterator().next());
                // Dynamic textures already contain the normalized 64x64 local image.
                var texture=Minecraft.getInstance().getTextureManager().getTexture(skin.body().texturePath());
                if(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture dynamic && dynamic.getPixels()!=null){
                    var pixels=dynamic.getPixels();BufferedImage loaded=new BufferedImage(pixels.getWidth(),pixels.getHeight(),BufferedImage.TYPE_INT_ARGB);
                    loaded.setRGB(0,0,pixels.getWidth(),pixels.getHeight(),pixels.getPixels(),0,pixels.getWidth());IMAGES.put(key,CompletableFuture.completedFuture(loaded));
                }else IMAGES.put(key,CompletableFuture.supplyAsync(()->{
                    try{
                        byte[] bytes;
                        if(skin.body() instanceof ClientAsset.DownloadedTexture downloaded)bytes=LocalSkins.fetch(downloaded.url());
                        else try(var stream=Minecraft.getInstance().getResourceManager().open(skin.body().texturePath())){bytes=stream.readNBytes(1024*1024+1);}
                        LocalSkins.validate(bytes);return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    }catch(Exception e){return null;}
                }));
            }
            image=IMAGES.get(key).getNow(null);
        }
        for(var names:PARTS){
            var parent=model.root().getChild(names[0]);if(!parent.hasChild(names[1]))continue;
            ModelPart part=parent.getChild(names[1]);var access=(ModelPartAccessor)(Object)part;
            var original=ORIGINAL.computeIfAbsent(part,unused->List.copyOf(access.ladsCubes()));
            String desired=enabled()&&image!=null?key:"flat";
            if(desired.equals(APPLIED.get(part)))continue;
            if(desired.equals("flat"))access.ladsCubes(original);
            else {
                List<ModelPart.Cube> voxels=new ArrayList<>();
                for(var cube:original)for(var face:cube.polygons)extrude(face,image,voxels);
                access.ladsCubes(voxels);
            }
            APPLIED.put(part,desired);
        }
    }
    private static void extrude(ModelPart.Polygon face,BufferedImage skin,List<ModelPart.Cube> cubes){
        var vertices=face.vertices();if(vertices.length!=4)return;
        var a=vertices[0];ModelPart.Vertex u=null,v=null;
        for(var b:vertices){if(Math.abs(b.v()-a.v())<.00001&&Math.abs(b.u()-a.u())>.00001)u=b;if(Math.abs(b.u()-a.u())<.00001&&Math.abs(b.v()-a.v())>.00001)v=b;}
        if(u==null||v==null)return;
        int minU=64,minV=64,maxU=0,maxV=0;
        for(var b:vertices){minU=Math.min(minU,Math.round(b.u()*64));minV=Math.min(minV,Math.round(b.v()*64));maxU=Math.max(maxU,Math.round(b.u()*64));maxV=Math.max(maxV,Math.round(b.v()*64));}
        for(int py=Math.max(0,minV);py<Math.min(skin.getHeight(),maxV);py++)for(int px=Math.max(0,minU);px<Math.min(skin.getWidth(),maxU);px++){
            if((skin.getRGB(px,py)>>>24)<16)continue;
            float[] lo={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE},hi={-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
            for(int dx=0;dx<=1;dx++)for(int dy=0;dy<=1;dy++){
                float su=((px+dx)/64f-a.u())/(u.u()-a.u()),sv=((py+dy)/64f-a.v())/(v.v()-a.v());
                float[] point={a.x()+su*(u.x()-a.x())+sv*(v.x()-a.x()),a.y()+su*(u.y()-a.y())+sv*(v.y()-a.y()),a.z()+su*(u.z()-a.z())+sv*(v.z()-a.z())};
                for(int axis=0;axis<3;axis++){float n=face.normal().get(axis);lo[axis]=Math.min(lo[axis],point[axis]+Math.min(0,n*.35f));hi[axis]=Math.max(hi[axis],point[axis]+Math.max(0,n*.35f));}
            }
            var cube=new ModelPart.Cube(0,0,lo[0],lo[1],lo[2],hi[0]-lo[0],hi[1]-lo[1],hi[2]-lo[2],0,0,0,false,64,64,EnumSet.allOf(Direction.class));
            for(var polygon:cube.polygons){var points=polygon.vertices();for(int i=0;i<points.length;i++)points[i]=points[i].remap((px+.5f)/64,(py+.5f)/64);}
            cubes.add(cube);
        }
    }
}
