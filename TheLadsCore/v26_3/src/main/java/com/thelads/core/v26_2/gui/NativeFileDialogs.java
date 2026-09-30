package com.thelads.core.v26_2.gui;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.sdl.*;
import org.lwjgl.system.MemoryUtil;
/** SDL opens the platform's native asynchronous Explorer dialog on Windows. */
public final class NativeFileDialogs {
    private static final AtomicBoolean OPEN=new AtomicBoolean();
    private record Retired(SDL_DialogFileCallback callback,long after) {}
    private static final java.util.concurrent.ConcurrentLinkedQueue<Retired> RETIRED=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static boolean registered;
    private NativeFileDialogs(){}
    public static CompletableFuture<Path> choose(boolean folder){
        if(!OPEN.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("A file dialog is already open"));
        var result=new CompletableFuture<Path>();
        var mc=net.minecraft.client.Minecraft.getInstance();
        mc.execute(()->{
            if(!registered){registered=true;net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
                for(Retired entry;(entry=RETIRED.peek())!=null&&entry.after()<System.nanoTime();){RETIRED.poll();entry.callback().free();}
            });}
            SDL_DialogFileCallback[] holder=new SDL_DialogFileCallback[1];
            holder[0]=SDL_DialogFileCallback.create((data,files,filter)->{
                try{
                    if(files==0)result.completeExceptionally(new IllegalStateException(SDLError.SDL_GetError()));
                    else {long name=MemoryUtil.memGetAddress(files);result.complete(name==0?null:Path.of(MemoryUtil.memUTF8(name)));}
                }catch(Exception e){result.completeExceptionally(e);}
                finally{OPEN.set(false);RETIRED.add(new Retired(holder[0],System.nanoTime()+1_000_000_000L));}
            });
            try{
                if(folder)SDLDialog.SDL_ShowOpenFolderDialog(holder[0],0,mc.getWindow().handle(),(CharSequence)null,false);
                else SDLDialog.SDL_ShowOpenFileDialog(holder[0],0,mc.getWindow().handle(),null,(CharSequence)null,false);
            }catch(Throwable e){holder[0].free();OPEN.set(false);result.completeExceptionally(e);}
        });
        return result;
    }
}
