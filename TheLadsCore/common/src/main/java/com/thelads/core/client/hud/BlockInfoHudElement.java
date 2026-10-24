package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Block Info (Badlion's Block Info, Tool Stats): the block you look at, the tool it needs and whether what you hold harvests it, your
 * digging progress and the light on its face (HudExtras.block*, written by each version's tick). All of it is what vanilla shows in
 * F3 or the crack, so it is visual only.
 */
public class BlockInfoHudElement extends TextHudElement {
    private final StringBuilder out = new StringBuilder();
    private String text = "", name, tool;
    private int harvest = -2, light = -1, sky = -1, percent = -1, options = -1;

    public BlockInfoHudElement() {
        super(80);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        boolean shown = HudExtras.blockShown;
        if (!shown && !editor) return "";
        String n = shown ? HudExtras.blockName : "Diamond Ore", t = shown ? HudExtras.blockTool : "Iron Pickaxe";
        int h = shown ? HudExtras.blockHarvest : 1, l = shown ? HudExtras.blockLight : 0, s = shown ? HudExtras.skyLight : 0;
        int p = shown ? Math.round(HudExtras.blockProgress * 100) : 40;
        int o = (optBool("Show Tool", true) ? 1 : 0) | (optBool("Show Harvestability", true) ? 2 : 0)
            | (optBool("Show Light Level", true) ? 4 : 0) | (optBool("Show Mining Progress", true) ? 8 : 0);
        if (!n.equals(name) || !t.equals(tool) || h != harvest || l != light || s != sky || p != percent || o != options) {
            out.setLength(0);
            out.append(n);
            if ((o & 1) != 0) out.append("\nTool: ").append(t);
            if ((o & 2) != 0 && h >= 0) out.append(h == 1 ? " (can harvest)" : " (can't harvest)");
            if ((o & 8) != 0 && p > 0) out.append("\nMining: ").append(p).append('%');
            if ((o & 4) != 0) out.append("\nLight: ").append(l).append(" (sky ").append(s).append(')');
            text = out.toString();
            name = n;
            tool = t;
            harvest = h;
            light = l;
            sky = s;
            percent = p;
            options = o;
        }
        return text;
    }
}
