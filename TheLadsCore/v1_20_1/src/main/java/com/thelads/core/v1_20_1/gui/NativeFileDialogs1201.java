package com.thelads.core.v1_20_1.gui;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The game's own dialog (tinyfd, bundled with Minecraft 1.20.1), which FileDialogs opens off Windows (VanillaGameBridge1201.gameFileDialog).
 * It blocks its caller until the player picks, so it runs on its own thread; the result completes on that thread, and callers hop
 * back to the game thread.
 */
public final class NativeFileDialogs1201 {
    private static final AtomicBoolean OPEN = new AtomicBoolean();

    private NativeFileDialogs1201() {}

    public static CompletableFuture<Path> choose(boolean folder, String title, String filterName, String... extensions) {
        if (!OPEN.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A file dialog is already open"));
        }
        var result = new CompletableFuture<Path>();
        Thread thread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer patterns = null;
                if (extensions.length > 0) {
                    patterns = stack.mallocPointer(extensions.length);
                    for (String extension : extensions) patterns.put(stack.UTF8("*." + extension));
                    patterns.flip();
                }
                String picked = folder
                    ? TinyFileDialogs.tinyfd_selectFolderDialog(title, "") // LWJGL refuses a null default path
                    : TinyFileDialogs.tinyfd_openFileDialog(title, (CharSequence) null, patterns, extensions.length > 0 ? filterName : null, false);
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
