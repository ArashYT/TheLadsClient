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
    public static CompletableFuture<Path> choose(boolean folder){return choose(folder,folder?"Add world save folder":"Add skin PNG");}
    public static CompletableFuture<Path> choose(boolean folder,String title){
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
                // Only the properties form takes a title. SDL copies what it needs before returning, like its own open-folder/file helpers.
                int props=SDLProperties.SDL_CreateProperties();
                try{
                    SDLProperties.SDL_SetPointerProperty(props,SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,mc.getWindow().handle());
                    SDLProperties.SDL_SetStringProperty(props,SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING,title);
                    SDLDialog.SDL_ShowFileDialogWithProperties(folder?SDLDialog.SDL_FILEDIALOG_OPENFOLDER:SDLDialog.SDL_FILEDIALOG_OPENFILE,holder[0],0,props);
                }finally{SDLProperties.SDL_DestroyProperties(props);}
            }catch(Throwable e){holder[0].free();OPEN.set(false);result.completeExceptionally(e);}
        });
        return result;
    }
}
