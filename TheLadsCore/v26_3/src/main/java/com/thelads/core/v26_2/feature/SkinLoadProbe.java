package com.thelads.core.v26_2.feature;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import java.nio.file.*;
import java.util.concurrent.*;
/** Uses the production loader and real TextureManager, isolated from the user's profiles. */
final class SkinLoadProbe {
    private static CompletableFuture<Void> result;
    private static Path fixture;
    private static long started;
    static boolean tick() throws Exception {
        if(!Boolean.getBoolean("thelads.verify133"))return true;
        if(result==null){
            var mc=Minecraft.getInstance();var root=NativeWorldVerification.checkedGameDirectory(mc.gameDirectory.toPath());
            if(LocalSkins.current()!=null)throw new IllegalStateException("Skin QA requires the isolated account skin as its starting state");
            fixture=root.resolve(".lads-qa-skin.png");
            byte[] bytes;
            try(var stream=mc.getResourceManager().open(Identifier.withDefaultNamespace("textures/entity/player/wide/kai.png"))){bytes=stream.readAllBytes();}
            Files.write(fixture,bytes);started=System.nanoTime();
            var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
            socket.setSoTimeout(20_000);
            Thread server=new Thread(()->{
                try(socket;var client=socket.accept()){
                    var reader=new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));
                    for(String line;(line=reader.readLine())!=null&&!line.isEmpty();){}
                    var out=client.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(bytes);out.flush();
                }catch(Exception failure){org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Skin QA fixture server ended",failure);}
            },"Lads skin QA HTTP fixture");server.setDaemon(true);server.start();
            String url="http://"+socket.getInetAddress().getHostAddress()+":"+socket.getLocalPort()+"/skin.png";
            result=LocalSkins.load(fixture.toString(),false,true).thenCompose(message->{
                require(LocalSkins.current()!=null&&LocalSkins.current().model()==PlayerModelType.WIDE,"file skin and classic model");
                require(Files.isRegularFile(root.resolve("config/lads-skin/skin.png")),"file skin persisted");
                return LocalSkins.load(url,true,false);
            }).thenAccept(message->{
                require(LocalSkins.current()!=null&&LocalSkins.current().model()==PlayerModelType.SLIM,"HTTP skin and slim model");
                var texture=mc.getTextureManager().getTexture(LocalSkins.current().body().texturePath());
                require(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture,"skin uploaded to the real texture manager");
                org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads skin loader probe END: 4 passed, 0 failed; real file, HTTP, persistence and texture upload");
            }).thenCompose(unused->{
                if(!Boolean.getBoolean("thelads.verifySkinNetwork"))return CompletableFuture.completedFuture(null);
                return CompletableFuture.supplyAsync(()->{
                    try{return com.google.gson.JsonParser.parseString(new String(LocalSkins.fetch("https://api.mojang.com/users/profiles/minecraft/Notch"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();}
                    catch(Exception failure){throw new CompletionException(failure);}
                }).thenCompose(uuid->LocalSkins.load("Notch",false,false).thenCompose(message->{
                    int usernamePixels=pixelHash();
                    return LocalSkins.load(uuid,false,false).thenAccept(uuidMessage->{
                        require(pixelHash()==usernamePixels,"username and UUID resolve the same uploaded skin");
                        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads skin profile probe END: 2 passed, 0 failed; live Mojang username and UUID loaders");
                    });
                })).thenCompose(done->LocalSkins.load(fixture.toString(),true,false)).thenAccept(done->{});
            }).whenComplete((unused,error)->{try{socket.close();}catch(Exception ignored){}});
        }
        if(System.nanoTime()-started>90_000_000_000L&&!result.isDone())throw new IllegalStateException("Skin loader QA timed out");
        if(!result.isDone())return false;
        result.join();return true;
    }
    private static int pixelHash(){
        var texture=(net.minecraft.client.renderer.texture.DynamicTexture)Minecraft.getInstance().getTextureManager().getTexture(LocalSkins.current().body().texturePath());
        return java.util.Arrays.hashCode(texture.getPixels().getPixels());
    }
    static void close(){if(fixture!=null){LocalSkins.reset();try{Files.deleteIfExists(fixture);}catch(Exception failure){throw new IllegalStateException(failure);}}}
    private static void require(boolean valid,String name){if(!valid)throw new IllegalStateException(name);}
}
