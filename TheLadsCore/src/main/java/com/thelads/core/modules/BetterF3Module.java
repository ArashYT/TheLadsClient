package com.thelads.core.modules;

import com.thelads.core.config.Module;
import java.util.ArrayList;
import java.util.List;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;

public class BetterF3Module extends Module {
    public final CycleOption fpsUpdateRate = new CycleOption("FPS Update", 1, "Instant", "Fast", "Normal", "Slow");
    public final BoolOption showXYZ = new BoolOption("Show XYZ", true);
    public final BoolOption showJava = new BoolOption("Show Java", false);
    public final BoolOption showMemory = new BoolOption("Show Memory", true);
    public final BoolOption showTargetBlock = new BoolOption("Show Target Block", true);
    public final ColorOption leftTextColor = new ColorOption("Left Color", false, 0xFFE0E0E0);
    public final ColorOption rightTextColor = new ColorOption("Right Color", false, 0xFFE0E0E0);
    public final CycleOption position = new CycleOption("Position", 0, "Top", "Bottom");

    public BetterF3Module() {
        super("BetterF3", "Customizable debug screen");
        addOption(fpsUpdateRate);
        addOption(showXYZ);
        addOption(showJava);
        addOption(showMemory);
        addOption(showTargetBlock);
        addOption(leftTextColor);
        addOption(rightTextColor);
        addOption(position);
    }

    public List<String> filterLeftText(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> mutableList = new ArrayList<>(list);
        if (!isEnabled()) return mutableList;
        
        if (!showXYZ.get()) {
            mutableList.removeIf(line -> line != null && (line.startsWith("XYZ:") || line.startsWith("Block:") || line.startsWith("Chunk:")));
        }
        if (!showTargetBlock.get()) {
            mutableList.removeIf(line -> line != null && (line.startsWith("Targeted Block:") || line.startsWith("Targeted Fluid:")));
        }
        return mutableList;
    }

    public List<String> filterRightText(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> mutableList = new ArrayList<>(list);
        if (!isEnabled()) return mutableList;
        
        if (!showJava.get()) {
            mutableList.removeIf(line -> line != null && line.startsWith("Java:"));
        }
        if (!showMemory.get()) {
            mutableList.removeIf(line -> line != null && line.startsWith("Mem:"));
            mutableList.removeIf(line -> line != null && line.startsWith("Allocated:"));
        }
        return mutableList;
    }
}
