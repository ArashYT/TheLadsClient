package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge.DefenseEnchants;
import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayList;
import java.util.List;

public class ProtectionHudElement extends TextHudElement {
    private String cachedText = "";
    private int lastProt = -1, lastFire = -1, lastBlast = -1, lastProj = -1, lastFf = -1;
    private int lastDisplay = -1;
    private boolean lastIncFf = true;

    public ProtectionHudElement() {
        super(60);
        this.x = 5;
        this.y = 270;
        this.width = 75;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        DefenseEnchants def = g.getGame().getDefenseEnchants();
        int prot = def.protection();
        int fire = def.fireProtection();
        int blast = def.blastProtection();
        int proj = def.projectileProtection();
        int ff = def.featherFalling();

        int display = optCycle("Display", 0); // 0 = Summary, 1 = All defensive, 2 = Protection only
        boolean includeFf = optBool("Include Feather Falling", true);
        boolean hideZero = optBool("Hide when zero", false);

        if (editor && prot == 0 && fire == 0 && blast == 0 && proj == 0 && ff == 0) {
            prot = 16;
            ff = 4;
        }

        if (hideZero && prot == 0 && fire == 0 && blast == 0 && proj == 0 && ff == 0) {
            return "";
        }

        if (prot == lastProt && fire == lastFire && blast == lastBlast && proj == lastProj && ff == lastFf
                && display == lastDisplay && includeFf == lastIncFf) {
            return cachedText;
        }

        lastProt = prot;
        lastFire = fire;
        lastBlast = blast;
        lastProj = proj;
        lastFf = ff;
        lastDisplay = display;
        lastIncFf = includeFf;

        if (display == 2) {
            return cachedText = "Prot: " + prot;
        } else if (display == 1) {
            String s = "P:" + prot + " F:" + fire + " B:" + blast + " PR:" + proj;
            if (includeFf) s += " FF:" + ff;
            return cachedText = s;
        } else {
            List<String> list = new ArrayList<>();
            if (prot > 0 || (fire == 0 && blast == 0 && proj == 0)) list.add("Prot: " + prot);
            if (fire > 0) list.add("Fire: " + fire);
            if (blast > 0) list.add("Blast: " + blast);
            if (proj > 0) list.add("Proj: " + proj);
            if (includeFf && ff > 0) list.add("FF: " + ff);
            return cachedText = String.join(" · ", list);
        }
    }
}
