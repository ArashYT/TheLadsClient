package com.thelads.core.v1_20_1.gui;

import com.thelads.core.v1_20_1.feature.LocalSkins1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Skin changer: a username, UUID, skin URL or PNG file becomes this client's own skin (LocalSkins1201). */
public final class SkinChangerScreen1201 extends Screen {
    private final Screen parent;
    private EditBox source;
    private boolean slim, busy;
    private String status = "Skins are applied locally to your client.";

    public SkinChangerScreen1201(Screen parent) {
        super(Component.literal("Skin changer"));
        this.parent = parent;
        slim = LocalSkins1201.current() != null && LocalSkins1201.current().slim();
    }

    /** The signed-in account's own skin texture (a default skin for offline accounts) and model. */
    static ResourceLocation accountSkin() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.getSkinManager().getInsecureSkinLocation(mc.getUser().getGameProfile());
    }

    static boolean accountSlim() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var profile = mc.getUser().getGameProfile();
        var textures = mc.getSkinManager().getInsecureSkinInformation(profile);
        var skin = textures.get(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN);
        if (skin != null) return "slim".equals(skin.getMetadata("model"));
        return "slim".equals(net.minecraft.client.resources.DefaultPlayerSkin.getSkinModelName(profile.getId()));
    }

    @Override
    protected void init() {
        var preview = new PlayerPreview1201(110, Math.max(70, Math.min(220, height - 140)),
            () -> LocalSkins1201.current() == null ? accountSkin() : LocalSkins1201.current().texture(),
            () -> LocalSkins1201.current() == null ? accountSlim() : LocalSkins1201.current().slim());
        preview.setX(Math.max(8, width / 2 - 200));
        preview.setY(60);
        addRenderableWidget(preview);
        int x = Math.max(128, width / 2 - 60), w = Math.min(245, width - x - 12);
        source = new EditBox(font, x, 57, w, 20, Component.literal("Username, UUID or skin URL"));
        source.setMaxLength(2048);
        source.setHint(Component.literal("Username, UUID or URL"));
        addRenderableWidget(source);
        addRenderableWidget(new LadsButton1201(x, 85, w, 20, Component.literal("Load skin"), b -> load(source.getValue())));
        addRenderableWidget(new LadsButton1201(x, 113, w, 20, Component.literal("Add File"), b -> {
            if (busy) return;
            busy = true;
            status = "Choose a skin PNG in the file dialog";
            com.thelads.core.client.util.FileDialogs.openFile("Add skin PNG", "Skins (PNG)", "png").whenComplete((file, error) -> minecraft.execute(() -> {
                busy = false;
                if (error != null) status = "File dialog failed";
                else if (file != null) load(file.toString());
                else status = "Selection canceled";
            }));
        }));
        addRenderableWidget(new LadsButton1201(x, 141, w, 20, Component.literal("Model: " + (slim ? "Slim" : "Classic")), b -> {
            if (busy) return;
            slim = !slim;
            LocalSkins1201.setModel(slim);
            b.setMessage(Component.literal("Model: " + (slim ? "Slim" : "Classic")));
        }));
        addRenderableWidget(new LadsButton1201(x, 169, w, 20, Component.literal("Use account skin"), b -> {
            LocalSkins1201.reset();
            status = "Using account skin";
        }));
        addRenderableWidget(new LadsButton1201(width / 2 - 60, height - 28, 120, 20, Component.literal("Done"), b -> onClose()));
    }

    @Override
    public void tick() {
        if (source != null) source.tick();
    }

    private void load(String input) {
        if (busy || input.isBlank()) return;
        busy = true;
        status = "Loading skin...";
        LocalSkins1201.load(input, slim, true).whenComplete((message, error) -> minecraft.execute(() -> {
            busy = false;
            status = error == null ? message : "Could not load skin: " + error.getMessage();
        }));
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xFF100B10);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        renderBackground(g);
        super.render(g, mx, my, dt);
        g.drawCenteredString(font, title, width / 2, 18, -1);
        g.drawCenteredString(font, Component.literal(status), width / 2, height - 46, 0xFFCFB5BF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
