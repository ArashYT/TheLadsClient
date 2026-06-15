package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import com.thelads.core.client.cosmetics.backend.TextureRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MemoryLeakChallengerTest {

    @Test
    public void testUnassignedFetchesLeakMemory() throws Exception {
        try (MockedStatic<Minecraft> mockedMinecraft = mockStatic(Minecraft.class)) {
            Minecraft mockMc = mock(Minecraft.class);
            TextureManager mockTm = mock(TextureManager.class);
            AbstractTexture mockTex = mock(AbstractTexture.class);

            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mockMc);
            when(mockMc.getTextureManager()).thenReturn(mockTm);
            when(mockTm.getTexture(any())).thenReturn(mockTex);

            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(mockMc).execute(any(Runnable.class));

            Identifier dummyId = Identifier.fromNamespaceAndPath("thelads", "dummy");
            
            TextureRegistrar mockRegistrar = data -> CompletableFuture.completedFuture(dummyId);
            CosmeticsBackend.setRegistrar(mockRegistrar);

            java.lang.reflect.Method registerMethod = CosmeticsBackend.class.getDeclaredMethod("registerTexture", byte[].class);
            registerMethod.setAccessible(true);
            
            Identifier id = ((CompletableFuture<Identifier>) registerMethod.invoke(null, new byte[0])).join();

            java.lang.reflect.Field unassignedField = CosmeticsBackend.class.getDeclaredField("unassigned");
            unassignedField.setAccessible(true);
            java.util.Map<Identifier, Long> unassigned = (java.util.Map<Identifier, Long>) unassignedField.get(null);
            
            unassigned.put(id, System.currentTimeMillis() - 61000);

            CosmeticsBackend.setActiveSkin(UUID.randomUUID(), null);

            verify(mockTex, times(1)).close();
            verify(mockTm, times(1)).release(id);
        }
    }
}
