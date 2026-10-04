package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/** Async: parallel entity ticking on the integrated server (singleplayer and hosted LAN worlds). Minecraft 26.x only. */
public final class AsyncModule extends Module {
    public static final String NAME = "Async";
    private static final String[] THREADS = {"Auto", "2", "3", "4", "6", "8", "12", "16"};
    private final SliderOption minEntities = addOption(new SliderOption("Min entities", 300, 100, 3000, 100));
    private final DropdownOption threads = addOption(new DropdownOption("Threads", 0, THREADS));

    public AsyncModule() {
        super(NAME, "Experimental. Ticks mobs and items that are far apart on several CPU cores in singleplayer and LAN worlds you host. "
            + "Players, riders, projectiles, bosses and raids stay on the main thread. If anything goes wrong it switches back "
            + "to normal ticking until you rejoin.");
        setEnabled(true);
    }

    /** Fewer ticking entities than this in a dimension: normal ticking (threads would cost more than they save). */
    public int minEntities() { return (int) minEntities.getValue(); }

    /** Worker threads; Auto leaves one core for the render thread. Below 2 there is nothing to run in parallel. */
    public int threads(int cores) {
        int index = threads.getIndex();
        return index == 0 ? Math.max(1, cores - 1) : Integer.parseInt(THREADS[index]);
    }
}
