package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public class CosmeticsChallengerTest {

    @Test
    public void testThreadPoolExhaustionByJoin() throws Exception {
        try {
            CosmeticsBackend.fetchByFile(new File("valid.png")).join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
