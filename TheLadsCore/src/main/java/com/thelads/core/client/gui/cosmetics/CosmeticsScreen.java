package com.thelads.core.client.gui.cosmetics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.io.File;

public class CosmeticsScreen extends Screen {
    private final Screen parent;
    
    private EditBox usernameField;
    private EditBox urlField;
    
    private Button fetchUsernameBtn;
    private Button fetchUrlBtn;
    private Button uploadBtn;
    private boolean fetching = false;
    private String statusMessage = "";
    
    private boolean isCapeTarget = false; // State selector
    private Identifier previewTexture;
    private boolean isClosed = false;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Skin & Cape Manager"));
        this.parent = parent;
    }

    private boolean isTextureInUse(Identifier texture) {
        if (texture == null || this.minecraft == null || this.minecraft.player == null) return false;
        java.util.UUID uuid = this.minecraft.player.getUUID();
        return texture.equals(CosmeticsBackend.getActiveSkin(uuid)) || texture.equals(CosmeticsBackend.getActiveCape(uuid));
    }

    private void updatePreviewTexture(Identifier newTexture) {
        if (this.previewTexture != null && !isTextureInUse(this.previewTexture)) {
            this.minecraft.getTextureManager().release(this.previewTexture);
        }
        this.previewTexture = newTexture;
    }

    private void setFetching(boolean f) {
        this.fetching = f;
        if (this.fetchUsernameBtn != null) this.fetchUsernameBtn.active = !f;
        if (this.fetchUrlBtn != null) this.fetchUrlBtn.active = !f;
        if (this.uploadBtn != null) this.uploadBtn.active = !f;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Target Selector Button (Skin / Cape)
        this.addRenderableWidget(Button.builder(Component.literal("Target: " + (this.isCapeTarget ? "Cape" : "Skin")), (btn) -> {
            this.isCapeTarget = !this.isCapeTarget;
            btn.setMessage(Component.literal("Target: " + (this.isCapeTarget ? "Cape" : "Skin")));
        }).bounds(centerX - 180, centerY - 90, 160, 20).build());

        // Text fields with adjacent Fetch buttons
        this.usernameField = new EditBox(this.font, centerX - 180, centerY - 50, 115, 20, Component.literal("Username"));
        this.usernameField.setMaxLength(16);
        this.addRenderableWidget(this.usernameField);

        this.fetchUsernameBtn = Button.builder(Component.literal("Fetch"), (btn) -> {
            String username = this.usernameField.getValue();
            if (!username.isEmpty()) {
                this.setFetching(true);
                this.statusMessage = "Fetching...";
                CosmeticsBackend.fetchByUsername(username, this.isCapeTarget).whenComplete((texture, err) -> {
                    this.minecraft.execute(() -> {
                        if (this.isClosed) {
                            if (texture != null) {
                                this.minecraft.getTextureManager().release(texture);
                            }
                            return;
                        }
                        this.setFetching(false);
                        if (err != null) {
                            Throwable cause = (err instanceof java.util.concurrent.CompletionException && err.getCause() != null) ? err.getCause() : err;
                            this.statusMessage = "Error: " + cause.getMessage();
                            return;
                        } else if (texture != null) {
                            this.updatePreviewTexture(texture);
                            this.statusMessage = "Success!";
                        } else {
                            this.statusMessage = "Failed to fetch.";
                        }
                    });
                });
            }
        }).bounds(centerX - 60, centerY - 50, 40, 20).build();
        this.addRenderableWidget(this.fetchUsernameBtn);

        this.urlField = new EditBox(this.font, centerX - 180, centerY - 10, 115, 20, Component.literal("Direct URL"));
        this.urlField.setMaxLength(256);
        this.addRenderableWidget(this.urlField);

        this.fetchUrlBtn = Button.builder(Component.literal("Fetch"), (btn) -> {
            String url = this.urlField.getValue();
            if (!url.isEmpty()) {
                this.setFetching(true);
                this.statusMessage = "Fetching...";
                CosmeticsBackend.fetchByUrl(url).whenComplete((texture, err) -> {
                    this.minecraft.execute(() -> {
                        if (this.isClosed) {
                            if (texture != null) {
                                this.minecraft.getTextureManager().release(texture);
                            }
                            return;
                        }
                        this.setFetching(false);
                        if (err != null) {
                            Throwable cause = (err instanceof java.util.concurrent.CompletionException && err.getCause() != null) ? err.getCause() : err;
                            this.statusMessage = "Error: " + cause.getMessage();
                            return;
                        } else if (texture != null) {
                            this.updatePreviewTexture(texture);
                            this.statusMessage = "Success!";
                        } else {
                            this.statusMessage = "Failed to fetch.";
                        }
                    });
                });
            }
        }).bounds(centerX - 60, centerY - 10, 40, 20).build();
        this.addRenderableWidget(this.fetchUrlBtn);

        // Upload and Back Buttons
        this.uploadBtn = Button.builder(Component.literal("Upload File"), (btn) -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog("Select Image", null, null, "Image Files", false);
            if (path != null) {
                this.setFetching(true);
                this.statusMessage = "Reading file...";
                CosmeticsBackend.fetchByFile(new File(path)).whenComplete((texture, err) -> {
                    this.minecraft.execute(() -> {
                        if (this.isClosed) {
                            if (texture != null) {
                                this.minecraft.getTextureManager().release(texture);
                            }
                            return;
                        }
                        this.setFetching(false);
                        if (err != null) {
                            Throwable cause = (err instanceof java.util.concurrent.CompletionException && err.getCause() != null) ? err.getCause() : err;
                            this.statusMessage = "Error: " + cause.getMessage();
                            return;
                        } else if (texture != null) {
                            this.updatePreviewTexture(texture);
                            this.statusMessage = "Success!";
                        } else {
                            this.statusMessage = "Failed to read file.";
                        }
                    });
                });
            }
        }).bounds(centerX - 180, centerY + 30, 160, 20).build();
        this.addRenderableWidget(this.uploadBtn);

        this.addRenderableWidget(Button.builder(Component.literal("Back"), (btn) -> {
            this.onClose();
        }).bounds(centerX - 180, centerY + 70, 160, 20).build());

        // Preview Area Buttons (Apply, Equip, Reset)
        int previewX = centerX + 20;
        int previewBtnY = centerY + 65;

        this.addRenderableWidget(Button.builder(Component.literal("Apply"), (btn) -> {
            if (this.previewTexture != null && this.minecraft != null && this.minecraft.player != null) {
                if (this.isCapeTarget) {
                    CosmeticsBackend.setActiveCape(this.minecraft.player.getUUID(), this.previewTexture);
                } else {
                    CosmeticsBackend.setActiveSkin(this.minecraft.player.getUUID(), this.previewTexture);
                }
            }
        }).bounds(previewX + 5, previewBtnY, 48, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Equip"), (btn) -> {
            if (this.previewTexture != null && this.minecraft != null && this.minecraft.player != null) {
                java.util.UUID uuid = this.minecraft.player.getUUID();
                if (this.isCapeTarget) {
                    CosmeticsBackend.setActiveCape(uuid, this.previewTexture);
                } else {
                    CosmeticsBackend.setActiveSkin(uuid, this.previewTexture);
                }
                CosmeticsBackend.persist();
                this.statusMessage = "Equipped!";
            } else {
                this.statusMessage = "Equip failed.";
            }
        }).bounds(previewX + 56, previewBtnY, 48, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), (btn) -> {
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
        if (this.previewTexture != null && !isTextureInUse(this.previewTexture)) {
            this.minecraft.getTextureManager().release(this.previewTexture);
        }
        this.previewTexture = null;
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void removed() {
        this.isClosed = true;
        if (this.previewTexture != null && !isTextureInUse(this.previewTexture)) {
            this.minecraft.getTextureManager().release(this.previewTexture);
        }
        this.previewTexture = null;
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Render dark background
        g.fill(0, 0, this.width, this.height, 0xDD101020);
        
        // Draw title
        g.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Field Labels
        g.text(this.font, "Fetch by Username:", centerX - 180, centerY - 65, 0xAAAAAA, false);
        g.text(this.font, "Fetch by Direct URL:", centerX - 180, centerY - 25, 0xAAAAAA, false);
        g.text(this.font, "Upload Local File:", centerX - 180, centerY + 15, 0xAAAAAA, false);
        
        // Preview Panel Background
        g.fill(centerX + 20, centerY - 80, centerX + 180, centerY + 90, 0x55000000);
        g.fill(centerX + 20, centerY - 80, centerX + 180, centerY - 79, 0xFF6C63FF); // Top accent
        
        if (this.previewTexture != null) {
            int w = 128;
            int h = this.isCapeTarget ? 64 : 128;
            g.blit(RenderPipelines.GUI_TEXTURED, this.previewTexture, centerX + 100 - (w/2), centerY - 10 - (h/2), 0f, 0f, w, h, w, h);
        } else {
            g.centeredText(this.font, Component.literal("Preview Placeholder"), centerX + 100, centerY - 65, 0xFFFFFF);
            g.centeredText(this.font, Component.literal("(Skin/Cape Model)"), centerX + 100, centerY, 0xAAAAAA);
        }
        
        if (!this.statusMessage.isEmpty()) {
            g.centeredText(this.font, Component.literal(this.statusMessage), centerX - 100, centerY + 55, 0xFFFF55);
        }
        
        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}
