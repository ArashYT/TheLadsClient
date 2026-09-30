package com.thelads.core.v1_21_11.gui;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
/** TinyFD uses Windows Explorer dialogs on Windows. No render-thread blocking. */
public final class NativeFileDialogs {
    private static final AtomicBoolean OPEN=new AtomicBoolean();
    private NativeFileDialogs(){}
    public static CompletableFuture<Path> choose(boolean folder){return choose(folder,folder?"Add world save folder":"Add skin PNG");}
    public static CompletableFuture<Path> choose(boolean folder,String title){
        if(!OPEN.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("A file dialog is already open"));
        return CompletableFuture.supplyAsync(()->{
            try{
                String selected=folder?TinyFileDialogs.tinyfd_selectFolderDialog(title,null)
                    :TinyFileDialogs.tinyfd_openFileDialog(title,null,(org.lwjgl.PointerBuffer)null,"PNG skin",false);
                return selected==null?null:Path.of(selected);
            }finally{OPEN.set(false);}
        });
    }
}
