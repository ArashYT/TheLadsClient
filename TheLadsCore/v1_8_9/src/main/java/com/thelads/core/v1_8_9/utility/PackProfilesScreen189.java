package com.thelads.core.v1_8_9.utility;

import com.thelads.core.client.pack.PackProfiles;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/**
 * Pack Profiles on 1.8.9: pick a saved profile (the button cycles through them) and Apply it (its packs load now, as Done would), or
 * name the packs selected on the pack screen now and Save them. Done goes back to the pack screen.
 */
public final class PackProfilesScreen189 extends GuiScreen {
    private static final String VERSION = "1.8.9";
    private static final int PICK = 0, APPLY = 1, DELETE = 2, SAVE = 3, DONE = 4;
    /** The resource pack screen (GuiScreenResourcePacksMixin): its pending Selected packs, and applying a profile there. */
    public interface Host {
        List<String> ladsSelectedPacks();
        void ladsApplyPacks(List<String> names);
    }

    private final GuiScreen parent;
    private final Host host;
    private List<String> names;
    private int shown;
    private GuiTextField name;
    private String status = "";

    public PackProfilesScreen189(GuiScreen parent, Host host) {
        this.parent = parent;
        this.host = host;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        names = PackProfiles.names(VERSION);
        if (shown >= names.size()) shown = 0;
        int x = width / 2 - 100, y = height / 2 - 56;
        buttonList.clear();
        GuiButton pick = new GuiButton(PICK, x, y, 200, 20, names.isEmpty() ? "No profiles yet" : "Profile: " + names.get(shown));
        pick.enabled = names.size() > 1;
        GuiButton apply = new GuiButton(APPLY, x, y + 24, 98, 20, "Apply"), delete = new GuiButton(DELETE, x + 102, y + 24, 98, 20, "Delete");
        apply.enabled = delete.enabled = !names.isEmpty();
        buttonList.add(pick);
        buttonList.add(apply);
        buttonList.add(delete);
        buttonList.add(new GuiButton(SAVE, x, y + 84, 200, 20, "Save Selected Packs"));
        buttonList.add(new GuiButton(DONE, x, height - 28, 200, 20, I18n.format("gui.done")));
        String typed = name == null ? "" : name.getText();
        name = new GuiTextField(5, fontRendererObj, x, y + 60, 200, 20);
        name.setMaxStringLength(PackProfiles.MAX_NAME);
        name.setText(typed);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == PICK) {
            shown = (shown + 1) % names.size();
            initGui();
        } else if (button.id == APPLY) {
            host.ladsApplyPacks(PackProfiles.get(VERSION, names.get(shown)));
        } else if (button.id == DELETE) {
            PackProfiles.delete(VERSION, names.get(shown));
            status = "Deleted " + names.get(shown);
            shown = 0;
            initGui();
        } else if (button.id == SAVE) {
            String typed = name.getText().trim();
            List<String> packs = host.ladsSelectedPacks();
            if (!PackProfiles.save(VERSION, typed, packs)) {
                status = "Type a name first";
                return;
            }
            status = "Saved " + typed + " (" + packs.size() + " packs)";
            shown = Math.max(0, PackProfiles.names(VERSION).indexOf(typed));
            name.setText("");
            initGui();
        } else if (button.id == DONE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (name.textboxKeyTyped(typedChar, keyCode)) return;
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        name.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void updateScreen() {
        name.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Pack Profiles", width / 2, height / 2 - 80, 0xFFFFFF);
        name.drawTextBox();
        if (name.getText().isEmpty() && !name.isFocused()) drawString(fontRendererObj, "§8Profile name", width / 2 - 96, height / 2 + 10, 0x888888);
        drawCenteredString(fontRendererObj, status, width / 2, height / 2 + 54, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
