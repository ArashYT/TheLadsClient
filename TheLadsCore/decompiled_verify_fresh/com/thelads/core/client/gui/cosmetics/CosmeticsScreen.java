/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  org.lwjgl.util.tinyfd.TinyFileDialogs
 */
package com.thelads.core.client.gui.cosmetics;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class CosmeticsScreen
extends Screen {
    private final Screen parent;
    private EditBox usernameField;
    private EditBox urlField;
    private Button fetchUsernameBtn;
    private Button fetchUrlBtn;
    private Button uploadBtn;
    private boolean fetching = false;
    private String statusMessage = "";
    private boolean isCapeTarget = false;
    private Identifier previewTexture;
    private boolean isClosed = false;

    public CosmeticsScreen(Screen parent) {
        super((Component)Component.literal((String)"Skin & Cape Manager"));
        this.parent = parent;
    }

    private boolean isTextureInUse(Identifier texture) {
        if (texture == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        UUID uuid = this.minecraft.player.getUUID();
        return texture.equals(CosmeticsBackend.getActiveSkin(uuid)) || texture.equals(CosmeticsBackend.getActiveCape(uuid));
    }

    private void updatePreviewTexture(Identifier newTexture) {
        if (this.previewTexture != null && !this.isTextureInUse(this.previewTexture)) {
            this.minecraft.getTextureManager().release(this.previewTexture);
        }
        this.previewTexture = newTexture;
    }

    private void setFetching(boolean f) {
        this.fetching = f;
        if (this.fetchUsernameBtn != null) {
            boolean bl = this.fetchUsernameBtn.active = !f;
        }
        if (this.fetchUrlBtn != null) {
            boolean bl = this.fetchUrlBtn.active = !f;
        }
        if (this.uploadBtn != null) {
            this.uploadBtn.active = !f;
        }
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)("Target: " + (this.isCapeTarget ? "Cape" : "Skin"))), btn -> {
            this.isCapeTarget = !this.isCapeTarget;
            btn.setMessage((Component)Component.literal((String)("Target: " + (this.isCapeTarget ? "Cape" : "Skin"))));
        }).bounds(centerX - 180, centerY - 90, 160, 20).build());
        this.usernameField = new EditBox(this.font, centerX - 180, centerY - 50, 115, 20, (Component)Component.literal((String)"Username"));
        this.usernameField.setMaxLength(16);
        this.addRenderableWidget(this.usernameField);
        this.fetchUsernameBtn = Button.builder((Component)Component.literal((String)"Fetch"), btn -> {
            String username = this.usernameField.getValue();
            if (!username.isEmpty()) {
                this.setFetching(true);
                this.statusMessage = "Fetching...";
                CosmeticsBackend.fetchByUsername(username, this.isCapeTarget).whenComplete((texture, err) -> this.minecraft.execute(() -> {
                    if (this.isClosed) {
                        if (texture != null) {
                            this.minecraft.getTextureManager().release(texture);
                        }
                        return;
                    }
                    this.setFetching(false);
                    if (err != null) {
                        Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                        this.statusMessage = "Error: " + cause.getMessage();
                        return;
                    }
                    if (texture != null) {
                        this.updatePreviewTexture((Identifier)texture);
                        this.statusMessage = "Success!";
                    } else {
                        this.statusMessage = "Failed to fetch.";
                    }
                }));
            }
        }).bounds(centerX - 60, centerY - 50, 40, 20).build();
        this.addRenderableWidget(this.fetchUsernameBtn);
        this.urlField = new EditBox(this.font, centerX - 180, centerY - 10, 115, 20, (Component)Component.literal((String)"Direct URL"));
        this.urlField.setMaxLength(256);
        this.addRenderableWidget(this.urlField);
        this.fetchUrlBtn = Button.builder((Component)Component.literal((String)"Fetch"), btn -> {
            String url = this.urlField.getValue();
            if (!url.isEmpty()) {
                this.setFetching(true);
                this.statusMessage = "Fetching...";
                CosmeticsBackend.fetchByUrl(url).whenComplete((texture, err) -> this.minecraft.execute(() -> {
                    if (this.isClosed) {
                        if (texture != null) {
                            this.minecraft.getTextureManager().release(texture);
                        }
                        return;
                    }
                    this.setFetching(false);
                    if (err != null) {
                        Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                        this.statusMessage = "Error: " + cause.getMessage();
                        return;
                    }
                    if (texture != null) {
                        this.updatePreviewTexture((Identifier)texture);
                        this.statusMessage = "Success!";
                    } else {
                        this.statusMessage = "Failed to fetch.";
                    }
                }));
            }
        }).bounds(centerX - 60, centerY - 10, 40, 20).build();
        this.addRenderableWidget(this.fetchUrlBtn);
        this.uploadBtn = Button.builder((Component)Component.literal((String)"Upload File"), btn -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog((CharSequence)"Select Image", null, null, (CharSequence)"Image Files", (boolean)false);
            if (path != null) {
                this.setFetching(true);
                this.statusMessage = "Reading file...";
                CosmeticsBackend.fetchByFile(new File(path)).whenComplete((texture, err) -> this.minecraft.execute(() -> {
                    if (this.isClosed) {
                        if (texture != null) {
                            this.minecraft.getTextureManager().release(texture);
                        }
                        return;
                    }
                    this.setFetching(false);
                    if (err != null) {
                        Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                        this.statusMessage = "Error: " + cause.getMessage();
                        return;
                    }
                    if (texture != null) {
                        this.updatePreviewTexture((Identifier)texture);
                        this.statusMessage = "Success!";
                    } else {
                        this.statusMessage = "Failed to read file.";
                    }
                }));
            }
        }).bounds(centerX - 180, centerY + 30, 160, 20).build();
        this.addRenderableWidget(this.uploadBtn);
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Back"), btn -> this.onClose()).bounds(centerX - 180, centerY + 70, 160, 20).build());
        int previewX = centerX + 20;
        int previewBtnY = centerY + 65;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Apply"), btn -> {
            if (this.previewTexture != null && this.minecraft != null && this.minecraft.player != null) {
                if (this.isCapeTarget) {
                    CosmeticsBackend.setActiveCape(this.minecraft.player.getUUID(), this.previewTexture);
                } else {
                    CosmeticsBackend.setActiveSkin(this.minecraft.player.getUUID(), this.previewTexture);
                }
            }
        }).bounds(previewX + 5, previewBtnY, 48, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Equip"), btn -> {}).bounds(previewX + 56, previewBtnY, 48, 20).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Reset"), btn -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                if (this.isCapeTarget) {
                    CosmeticsBackend.clearActiveCape(this.minecraft.player.getUUID());
                } else {
                    CosmeticsBackend.clearActiveSkin(this.minecraft.player.getUUID());
                }
            }
        }).bounds(previewX + 107, previewBtnY, 48, 20).build());
    }

    @Override
    public void onClose() {
        if (this.previewTexture != null && !this.isTextureInUse(this.previewTexture)) {
            this.minecraft.getTextureManager().release(this.previewTexture);
        }
        this.previewTexture = null;
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.isClosed = true;
        if (this.previewTexture != null && !this.isTextureInUse(this.previewTexture)) {
            this.minecraft.getTextureManager().release(this.previewTexture);
        }
        this.previewTexture = null;
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, -586149856);
        g.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        g.text(this.font, "Fetch by Username:", centerX - 180, centerY - 65, 0xAAAAAA, false);
        g.text(this.font, "Fetch by Direct URL:", centerX - 180, centerY - 25, 0xAAAAAA, false);
        g.text(this.font, "Upload Local File:", centerX - 180, centerY + 15, 0xAAAAAA, false);
        g.fill(centerX + 20, centerY - 80, centerX + 180, centerY + 90, 0x55000000);
        g.fill(centerX + 20, centerY - 80, centerX + 180, centerY - 79, -9673729);
        if (this.previewTexture != null) {
            int w = 128;
            int h = this.isCapeTarget ? 64 : 128;
            g.blit(RenderPipelines.GUI_TEXTURED, this.previewTexture, centerX + 100 - w / 2, centerY - 10 - h / 2, 0.0f, 0.0f, w, h, w, h);
        } else {
            g.centeredText(this.font, (Component)Component.literal((String)"Preview Placeholder"), centerX + 100, centerY - 65, 0xFFFFFF);
            g.centeredText(this.font, (Component)Component.literal((String)"(Skin/Cape Model)"), centerX + 100, centerY, 0xAAAAAA);
        }
        if (!this.statusMessage.isEmpty()) {
            g.centeredText(this.font, (Component)Component.literal((String)this.statusMessage), centerX - 100, centerY + 55, 0xFFFF55);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}

