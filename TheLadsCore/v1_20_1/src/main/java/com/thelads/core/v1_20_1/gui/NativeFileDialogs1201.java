package com.thelads.core.v1_20_1.gui;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The platform's native Explorer dialog (tinyfd, bundled with Minecraft 1.20.1). It blocks its caller until the player picks,
 * so it runs on its own thread; the result completes on that thread, and callers hop back to the game thread.
 */
public final class NativeFileDialogs1201 {
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private NativeFileDialogs1201() {}

    public static CompletableFuture<Path> choose(boolean folder) {
        return choose(folder, folder ? "Add world save folder" : "Add skin PNG");
    }

    public static CompletableFuture<Path> choose(boolean folder, String title) {
        if (!OPEN.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A file dialog is already open"));
        }
        var result = new CompletableFuture<Path>();
        Thread thread = new Thread(() -> {
            try {
                String picked = folder
                    ? TinyFileDialogs.tinyfd_selectFolderDialog(title, (CharSequence) null)
                    : TinyFileDialogs.tinyfd_openFileDialog(title, (CharSequence) null, null, (CharSequence) null, false);
                result.complete(picked == null ? null : Path.of(picked));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                OPEN.set(false);
            }
        }, "Lads file dialog");
        thread.setDaemon(true);
        thread.start();
        return result;
    }
}
