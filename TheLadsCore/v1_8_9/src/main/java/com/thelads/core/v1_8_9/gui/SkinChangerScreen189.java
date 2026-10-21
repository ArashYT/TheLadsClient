package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.util.FileDialogs;
import com.thelads.core.v1_8_9.feature.LocalSkins189;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Skin changer: a username, UUID, skin URL or PNG file becomes this client's own skin (LocalSkins189), as SkinChangerScreen1201. */
public final class SkinChangerScreen189 extends GuiScreen {
    private static final int LOAD = 1, ADD_FILE = 2, MODEL = 3, ACCOUNT = 4, DONE = 199;
    private static final String HINT = "Username, UUID or URL";
    private final GuiScreen parent;
    private GuiTextField source;
    private SkinPreview189 preview;
    private boolean slim, busy;
    private String status = "Skins are applied locally to your client.";

    public SkinChangerScreen189(GuiScreen parent) {
        this.parent = parent;
        slim = LocalSkins189.current() != null && LocalSkins189.current().slim;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        preview = new SkinPreview189(Math.max(8, width / 2 - 200), 60, 110, Math.max(70, Math.min(220, height - 140)),
            () -> LocalSkins189.current() == null ? LocalSkins189.accountSkin() : LocalSkins189.current().texture,
            () -> LocalSkins189.current() == null ? LocalSkins189.accountSlim() : LocalSkins189.current().slim);
        int x = Math.max(128, width / 2 - 60), w = Math.min(245, width - x - 12);
        String typed = source == null ? "" : source.getText();
        source = new GuiTextField(0, fontRendererObj, x, 57, w, 20);
        source.setMaxStringLength(2048);
        source.setText(typed);
        buttonList.add(new SkinButton189(LOAD, x, 85, w, 20, "Load skin"));
        buttonList.add(new SkinButton189(ADD_FILE, x, 113, w, 20, "Add File"));
        buttonList.add(new SkinButton189(MODEL, x, 141, w, 20, modelLabel()));
        buttonList.add(new SkinButton189(ACCOUNT, x, 169, w, 20, "Use account skin"));
        buttonList.add(new SkinButton189(DONE, width / 2 - 60, height - 28, 120, 20, "Done"));
    }

    private String modelLabel() {
        return "Model: " + (slim ? "Slim" : "Classic");
    }

    @Override
    public void onGuiClosed() {
        preview.release();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        source.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case LOAD:
                load(source.getText());
                break;
            case ADD_FILE:
                if (busy) return;
                busy = true;
                status = "Choose a skin PNG in the file dialog";
                FileDialogs.openFile("Add skin PNG", "Skins (PNG)", "png").whenComplete((file, error) -> mc.addScheduledTask(() -> {
                    busy = false;
                    if (error != null) status = "File dialog failed";
                    else if (file != null) load(file.toString());
                    else status = "Selection canceled";
                }));
                break;
            case MODEL:
                if (busy) return;
                slim = !slim;
                LocalSkins189.setModel(slim);
                button.displayString = modelLabel();
                break;
            case ACCOUNT:
                LocalSkins189.reset();
                status = "Using account skin";
                break;
            default:
                mc.displayGuiScreen(parent);
        }
    }

    /** Loads what was typed (or chosen); the answer shows in the status line. */
    void load(String input) {
        if (busy || input.trim().isEmpty()) return;
        busy = true;
        status = "Loading skin...";
        LocalSkins189.load(input, slim, true).whenComplete((message, error) -> mc.addScheduledTask(() -> {
            busy = false;
            if (error == null && LocalSkins189.current() != null) {
                slim = LocalSkins189.current().slim;
                GuiButton button = button("Model: ");
                if (button != null) button.displayString = modelLabel();
            }
            status = error == null ? message : "Could not load skin: " + (error.getMessage() != null ? error.getMessage() : error.toString());
        }));
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (source.isFocused() && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)) {
            load(source.getText());
        } else if (!source.textboxKeyTyped(typed, key)) {
            super.keyTyped(typed, key);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        source.mouseClicked(mouseX, mouseY, button);
        preview.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        preview.mouseDragged(mouseX);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        preview.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF100B10);
        preview.draw(mc);
        source.drawTextBox();
        if (source.getText().isEmpty() && !source.isFocused()) drawString(fontRendererObj, HINT, source.xPosition + 4, source.yPosition + 6, 0x808080);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, "Skin changer", width / 2, 18, -1);
        drawCenteredString(fontRendererObj, status, width / 2, height - 46, 0xFFCFB5BF);
    }

    /** QA: the status line, the box the player types in, and a button by its label. */
    public String status() {
        return status;
    }

    public GuiTextField source() {
        return source;
    }

    public GuiButton button(String label) {
        for (GuiButton button : buttonList) if (button.displayString.startsWith(label)) return button;
        return null;
    }
}
