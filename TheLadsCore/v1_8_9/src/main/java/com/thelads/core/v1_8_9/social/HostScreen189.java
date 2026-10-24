package com.thelads.core.v1_8_9.social;

import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Host screen wrapper for the common HostScreenLogic (Task S4) and Multiplayer Options screen on Minecraft 1.8.9.
 */
public final class HostScreen189 extends GuiScreen {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-HostScreen");
    private final GuiScreen parent;
    private HostOptions options;
    private Path worldDir;
    // TODO(S4): private HostScreenLogic logic;

    public HostScreen189(GuiScreen parent) {
        this.parent = parent;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getIntegratedServer() != null) {
            this.worldDir = new File(new File(mc.mcDataDir, "saves"), mc.getIntegratedServer().getFolderName()).toPath();
            this.options = HostOptions.load(this.worldDir);
        } else {
            this.options = new HostOptions();
        }
        // TODO(S4): this.logic = new HostScreenLogic(new HostScreenLogic.Callbacks() { ... });
    }

    public GuiScreen parent() {
        return parent;
    }

    public HostOptions options() {
        return options;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();

        Minecraft mc = Minecraft.getMinecraft();
        boolean isPublic = mc != null && mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null && mc.getIntegratedServer().getPublic();
        boolean canHost = SocialBridge.get().canHost();

        int cy = height / 2;
        if (isPublic) {
            buttonList.add(new GuiButton(101, width / 2 - 100, cy + 40, 200, 20, "Stop Hosting World"));
        } else if (canHost) {
            buttonList.add(new GuiButton(100, width / 2 - 100, cy + 25, 200, 20, "Start Hosting (Friends)"));
            buttonList.add(new GuiButton(102, width / 2 - 100, cy + 50, 200, 20, "Open to LAN"));
        }
        buttonList.add(new GuiButton(103, width / 2 - 100, cy + 75, 200, 20, "Done"));
        // TODO(S4): if (logic != null) logic.init(width, height);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 100) {
            options.scope = HostOptions.Scope.FRIENDS;
            saveOptions();
            SocialBridge.get().openToFriends(options);
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else if (button.id == 101) {
            SocialBridge.get().closeHosting("Host stopped hosting world");
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else if (button.id == 102) {
            options.scope = HostOptions.Scope.LAN;
            saveOptions();
            SocialBridge.get().openToLan(options);
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else if (button.id == 103) {
            saveOptions();
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    private void saveOptions() {
        if (worldDir != null && options != null) {
            try {
                options.save(worldDir);
            } catch (IOException e) {
                LOGGER.warn("Failed to save lads-host.json: {}", e.getMessage());
            }
        }
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        GuiLadsAdapter g = new GuiLadsAdapter(fontRendererObj, width, height);

        // TODO(S4): if (logic != null) { logic.render(g, mx, my); } else { ... }
        int cardW = 340, cardH = 240;
        int left = width / 2 - cardW / 2;
        int top = height / 2 - cardH / 2;
        g.fill(left, top, left + cardW, top + cardH, 0xD0111116);
        g.fill(left, top, left + cardW, top + 2, 0xFFCF1535);

        g.drawCenteredText("Multiplayer Options", width / 2, top + 16, 0xFFFFFFFF, true);

        Minecraft mc = Minecraft.getMinecraft();
        boolean isPublic = mc != null && mc.isIntegratedServerRunning() && mc.getIntegratedServer() != null && mc.getIntegratedServer().getPublic();
        String status = isPublic ? "Status: Hosted (Joinable)" : "Status: Singleplayer (Offline)";
        g.drawCenteredText(status, width / 2, top + 34, isPublic ? 0xFF55FF55 : 0xFFAAAAAA, true);

        int textY = top + 52;
        g.drawText("Max Players: " + options.maxPlayers, left + 40, textY, 0xFFDDDDDD, true);
        g.drawText("Default Mode: " + options.guestGameMode, left + 40, textY + 14, 0xFFDDDDDD, true);
        g.drawText("PvP Allowed: " + (options.pvp ? "ON" : "OFF"), left + 40, textY + 28, 0xFFDDDDDD, true);
        g.drawText("Relay Only: " + (options.relayOnly ? "ON" : "OFF"), left + 40, textY + 42, 0xFFDDDDDD, true);
        g.drawText("Backup On Open: " + (options.backupOnOpen ? "ON" : "OFF"), left + 40, textY + 56, 0xFFDDDDDD, true);

        super.drawScreen(mx, my, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            saveOptions();
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        // TODO(S4): if (logic != null && logic.keyPressed(keyCode, typedChar)) return;
        super.keyTyped(typedChar, keyCode);
    }
}
