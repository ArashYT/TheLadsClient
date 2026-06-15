package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SharedSkinPrematureCloseChallengerTest {

    @Test
    public void testSharedSkinNotPrematurelyClosed() {
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

            Identifier sharedSkin = Identifier.fromNamespaceAndPath("thelads", "shared_skin");
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            CosmeticsBackend.setActiveSkin(player1, sharedSkin);
            CosmeticsBackend.setActiveSkin(player2, sharedSkin);

            verify(mockTex, never()).close();

            CosmeticsBackend.clearActiveSkin(player1);

            verify(mockTex, never()).close();
            
            org.junit.jupiter.api.Assertions.assertEquals(sharedSkin, CosmeticsBackend.getActiveSkin(player2));
            
            CosmeticsBackend.clearActiveSkin(player2);
            
            verify(mockTex, times(1)).close();
        }
    }
}
