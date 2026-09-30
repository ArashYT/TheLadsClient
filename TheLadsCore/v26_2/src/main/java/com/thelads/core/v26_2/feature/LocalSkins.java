package com.thelads.core.v26_2.feature;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
/** Local skin library. Network and disk work never runs on the render thread. */
public final class LocalSkins {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static volatile PlayerSkin selected;
    private static boolean initialized;
    private static volatile int revision;
    private static final Object STATE=new Object();
    public static PlayerSkin current(){return selected;}
    public static void initialize(){
        if(initialized)return;initialized=true;
        Path file=folder().resolve("skin.png");
        if(Files.isRegularFile(file))load(file.toString(),readSlim(),false);
    }
    private static boolean readSlim(){try{return Files.readString(folder().resolve("model.txt")).equals("slim");}catch(Exception e){return false;}}
    private static Path folder(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/lads-skin");}
    public static CompletableFuture<String> load(String input,boolean slim,boolean persist){
        var result=new CompletableFuture<String>();final int request; synchronized(STATE){request=++revision;}
        CompletableFuture.runAsync(()->{
            try{
                byte[] data;boolean model=slim;String source=input.strip();
                if(source.startsWith("https://")||source.startsWith("http://"))data=fetch(source);
                else if(source.matches("[A-Za-z0-9_]{1,16}")||source.matches("[a-fA-F0-9-]{32,36}")){
                    String uuid=source.replace("-","");
                    if(!uuid.matches("[a-fA-F0-9]{32}")){
                        var profile=JsonParser.parseString(new String(fetch("https://api.mojang.com/users/profiles/minecraft/"+source),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        uuid=profile.get("id").getAsString();
                    }
                    var profile=JsonParser.parseString(new String(fetch("https://sessionserver.mojang.com/session/minecraft/profile/"+uuid),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    com.google.gson.JsonObject skin=null;
                    for(var entry:profile.getAsJsonArray("properties")){
                        var property=entry.getAsJsonObject();if(!property.get("name").getAsString().equals("textures"))continue;
                        var textures=JsonParser.parseString(new String(Base64.getDecoder().decode(property.get("value").getAsString()),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("textures");
                        if(textures.has("SKIN"))skin=textures.getAsJsonObject("SKIN");
                    }
                    if(skin==null)throw new java.io.IOException("This profile has no custom skin");
                    model=skin.has("metadata")&&skin.getAsJsonObject("metadata").has("model")&&skin.getAsJsonObject("metadata").get("model").getAsString().equals("slim");
                    data=fetch(skin.get("url").getAsString());
                }else{
                    Path file=Path.of(source);if(Files.size(file)>1024*1024)throw new java.io.IOException("Skin file exceeds 1 MB");data=Files.readAllBytes(file);
                }
                validate(data);
                NativeImage image=com.thelads.core.v26_2.mixin.SkinTextureAccessor.ladsNormalizeSkin(NativeImage.read(data),"Lads local skin");
                boolean finalModel=model;
                try {
                    synchronized(STATE){
                        if(request!=revision){image.close();result.complete("A newer skin was selected");return;}
                        if(persist){
                            Files.createDirectories(folder());Path temporary=Files.createTempFile(folder(),"skin-",".png");
                            try {image.writeToFile(temporary);Files.move(temporary,folder().resolve("skin.png"),StandardCopyOption.REPLACE_EXISTING);}
                            finally{Files.deleteIfExists(temporary);}
                            Files.writeString(folder().resolve("model.txt"),model?"slim":"wide");
                        }
                    }
                } catch(Throwable failure){image.close();throw failure;}
                Minecraft.getInstance().execute(()->{
                    if(request!=revision){image.close();result.complete("A newer skin was selected");return;}
                    try{
                        var mc=Minecraft.getInstance();Identifier id=Identifier.fromNamespaceAndPath("theladscore","local_skin/"+request);
                        mc.getTextureManager().register(id,new DynamicTexture(()->"Lads custom skin",image));
                        PlayerSkin old=selected;
                        selected=new PlayerSkin(new ClientAsset.ResourceTexture(id,id),null,null,finalModel?PlayerModelType.SLIM:PlayerModelType.WIDE,false);
                        if(old!=null)mc.getTextureManager().release(old.body().texturePath());
                        result.complete("Skin applied locally");
                    }catch(Throwable e){image.close();result.completeExceptionally(e);}
                });
            }catch(Throwable e){result.completeExceptionally(e);}
        });
        return result;
    }
    public static byte[] fetch(String url)throws Exception{
        URI uri=URI.create(url);
        if(!List.of("https","http").contains(uri.getScheme()))throw new java.io.IOException("Use an HTTP(S) image URL");
        var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("User-Agent","LadsClient/1.3.3").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()){
            if(response.statusCode()!=200)throw new java.io.IOException("Server returned "+response.statusCode());
            byte[] bytes=body.readNBytes(1024*1024+1);if(bytes.length>1024*1024)throw new java.io.IOException("Response exceeds 1 MB");return bytes;
        }
    }
    public static void validate(byte[] bytes)throws java.io.IOException{
        if(bytes.length<24||java.nio.ByteBuffer.wrap(bytes).getLong()!=0x89504E470D0A1A0AL)throw new java.io.IOException("Select a PNG skin");
        var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);
        if(w!=64||(h!=64&&h!=32))throw new java.io.IOException("Skin must be 64 x 64 or 64 x 32 pixels");
    }
    public static void setModel(boolean slim){
        final int request;
        synchronized(STATE){
            if(selected==null)return;
            request=revision;
            selected=new PlayerSkin(selected.body(),selected.cape(),selected.elytra(),slim?PlayerModelType.SLIM:PlayerModelType.WIDE,selected.secure());
        }
        CompletableFuture.runAsync(()->{
            synchronized(STATE){
                if(request!=revision||selected==null||selected.model()!=(slim?PlayerModelType.SLIM:PlayerModelType.WIDE))return;
                try{Files.createDirectories(folder());Files.writeString(folder().resolve("model.txt"),slim?"slim":"wide");}
                catch(java.io.IOException failure){org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Could not save skin model",failure);}
            }
        });
    }
    public static void reset(){
        PlayerSkin old=selected;
        synchronized(STATE){selected=null;revision++;try{Files.deleteIfExists(folder().resolve("skin.png"));}catch(java.io.IOException e){org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Could not reset saved local skin",e);}}
        if(old!=null)Minecraft.getInstance().getTextureManager().release(old.body().texturePath());
    }
}
