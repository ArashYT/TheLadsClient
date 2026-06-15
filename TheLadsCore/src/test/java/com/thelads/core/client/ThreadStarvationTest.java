package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class ThreadStarvationTest {
    
    @Test
    public void testFetchByUsernameDoesNotUseBlockingSend() throws Exception {
        Path path = Paths.get("src/main/java/com/thelads/core/client/cosmetics/backend/CosmeticsBackend.java");
        String content = Files.readString(path);
        
        assertFalse(content.contains("HTTP_CLIENT.send("), 
            "fetchByUsername still uses the synchronous HTTP_CLIENT.send() method! This blocks ForkJoinPool.commonPool() threads, failing to fix the Slowloris thread starvation vulnerability.");
    }
    
    @Test
    public void testJoinIsNotCalledOnForkJoinPool() throws Exception {
        Path path = Paths.get("src/main/java/com/thelads/core/client/cosmetics/backend/CosmeticsBackend.java");
        String content = Files.readString(path);
        
        assertFalse(content.contains("sendAsync(textureReq, HttpResponse.BodyHandlers.ofByteArray()).join()"), 
            "Calling .join() on an async future inside fetchByUsername defeats the purpose and still blocks the ForkJoinPool worker thread.");
    }

    @Test
    public void testRegisterTextureDoesNotBlockAsyncThread() throws Exception {
        Path path = Paths.get("src/main/java/com/thelads/core/client/cosmetics/backend/CosmeticsBackend.java");
        String content = Files.readString(path);
        
        // registerTexture calls future.join()
        assertFalse(content.contains("return future.join();"), 
            "registerTexture calls future.join() while executing on the HttpClient's thread pool, which blocks the networking threads while waiting for the main render thread. This should return a CompletableFuture instead of blocking.");
    }
}
