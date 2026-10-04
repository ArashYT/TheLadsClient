package com.thelads.core.client;

/**
 * Item Physics: how a dropped item rests, tumbles and floats (a transform recipe every adapter replays through
 * {@link OldAnimations.Sink}), and the small sums behind the singleplayer rules. Written from ItemPhysic's public feature list
 * and how dropped items behave in game; no mod source.
 */
public final class ItemPhysics {
    /** A model this thin (vanilla's own flat-item test) lies on a face; anything thicker rests like a block. */
    public static final float FLAT = 0.0625f;
    /** Gap under a resting item: no z-fighting with the ground, too small to see. */
    static final float LIFT = 0.01f;
    /** A floating item is drawn this far above the surface, and only while it floats less than SURFACE deep (a sinking one goes on). */
    static final float FLOAT = 0.02f, SURFACE = 0.3f;
    /** Degrees of tumble per block travelled in the air. */
    static final float SPIN = 40;
    private static final float DEG = (float) Math.PI / 180;
    /** Item ids (the path, e.g. "white_wool") that burn although they are neither fuel nor a flammable block. */
    private static final String[] BURNS = {"paper", "book", "feather", "string", "leather", "wool", "carpet", "map"};
    /** Light things that float without burning. */
    private static final String[] FLOATS = {"ice", "snow", "snowball", "sponge", "kelp", "lily_pad", "waterlily"};

    private ItemPhysics() {}

    /** Gives a dropped item entity its tumble (the adapters' item entity mixins). */
    public interface Holder {
        Tumble lads$tumble();
    }

    /** One dropped item's yaw and tumble on the client, stepped every client tick and interpolated per frame. */
    public static final class Tumble {
        private final float seed;
        private float yaw, pitch, yawO, pitchO, afloat, afloatO;
        private int age;
        /** Resting turn: 180 for a flat item (it lies on either face), 90 for a block (any side). The renderer sets it. */
        public float rest = 180;

        /** @param seed a stable per-entity angle in degrees: the item's resting yaw */
        public Tumble(float seed) {
            this.seed = yaw = yawO = seed;
        }

        /**
         * One client tick.
         *
         * @param moved how far the item travelled last tick, in blocks
         * @param depth how deep the item's bottom is in water or lava (0: in neither)
         */
        public void tick(double moved, boolean onGround, double depth) {
            boolean inFluid = depth > 0;
            yawO = yaw;
            pitchO = pitch;
            afloatO = afloat;
            float surface = inFluid && !onGround && depth < SURFACE ? (float) depth + FLOAT : 0;
            afloat += (surface - afloat) * 0.3f;                 // drawn on the surface: eases up and back down
            age++;
            float rested = Math.round(pitch / rest) * rest;
            if (inFluid && !onGround) {                       // afloat (or sinking): turn slowly and rock
                yaw += 0.8f;
                settle(rested + 8 * (float) Math.sin(age * 0.12f + seed));
            } else if (onGround) settle(rested);              // landed: ease onto the nearest face, no snap
            else pitch += (float) Math.min(moved, 1) * SPIN;  // airborne: tumble with the speed
            if (pitch >= 360) { pitch -= 360; pitchO -= 360; }
            if (yaw >= 360) { yaw -= 360; yawO -= 360; }
        }

        private void settle(float target) {
            float gap = target - pitch;
            pitch = Math.abs(gap) < 0.5f ? target : pitch + gap * 0.3f;
        }

        public float yaw(float partial) { return yawO + (yaw - yawO) * partial; }

        public float pitch(float partial) { return pitchO + (pitch - pitchO) * partial; }

        /** Extra height for place(): the item floats this deep, and is drawn lying on the surface instead. */
        public float raise(float partial) { return afloatO + (afloat - afloatO) * partial; }
    }

    /**
     * Entity origin to the frame the item model is drawn in, for one copy of a dropped stack (vanilla draws 1 to 5 by count):
     * lifted so the model rests on the ground at any tumble, turned to its yaw, tumbled about its own X axis and centred. A flat
     * item lies on its face with its copies stacked like sheets; a block's copies lie scattered beside it, never below it.
     *
     * @param raise Tumble.raise: drawn this much higher (afloat)
     * @param cx  the model's centre and half height and depth, in the frame it is drawn in (with its ground display transform)
     * @param seed a stable number for this stack, so its copies keep their places
     */
    public static void place(OldAnimations.Sink out, float yaw, float pitch, float raise, float cx, float cy, float cz, float halfY, float halfZ,
                             int copy, int copies, int seed) {
        boolean flat = halfZ * 2 <= FLAT, sheets = flat && copies > 1;
        float spacing = halfZ * 2 * 1.5f, stack = flat ? spacing * (copies - 1) / 2 : 0, turn = (flat ? 90 : 0) + pitch;
        float spread = sheets ? 0.075f : 0; // the sheets' sideways jitter, which tips downwards mid-tumble
        out.translate(0, Math.abs((float) Math.cos(turn * DEG)) * (halfY + spread) + Math.abs((float) Math.sin(turn * DEG)) * (halfZ + stack) + LIFT + raise, 0);
        out.rotate(yaw, 0, 1, 0);
        if (!flat && copy > 0) out.translate(jitter(seed, copy, 0) * 0.15f, (jitter(seed, copy, 1) + 1) * 0.05f, jitter(seed, copy, 2) * 0.15f);
        out.rotate(turn, 1, 0, 0);
        if (sheets) out.translate(copy > 0 ? jitter(seed, copy, 0) * spread : 0, copy > 0 ? jitter(seed, copy, 1) * spread : 0, copy * spacing - stack);
        out.translate(-cx, -cy, -cz);
    }

    /** -1..1, the same for the same arguments. */
    static float jitter(int seed, int copy, int axis) {
        int h = seed * 31 + copy * 7919 + axis * 104729;
        h ^= h >>> 16;
        h *= 0x45d9f3b;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 32767.5f - 1;
    }

    /** Burns in fire and lava: furnace fuel, flammable blocks and a few light materials. Everything else survives them. */
    public static boolean burns(String id, boolean fuel, boolean flammableBlock) {
        return fuel || flammableBlock || named(id, BURNS);
    }

    /** Floats up in water: whatever burns, and ice, snow and a few plants. Everything else sinks. */
    public static boolean floats(String id, boolean fuel, boolean flammableBlock) {
        return burns(id, fuel, flammableBlock) || named(id, FLOATS);
    }

    /** The path is one of the names, or starts or ends with one as a whole word ("white_wool", "leather_boots"). */
    private static boolean named(String path, String[] names) {
        for (String name : names) if (path.equals(name) || path.endsWith("_" + name) || path.startsWith(name + "_")) return true;
        return false;
    }

    /**
     * Vertical speed after a tick under water (or lava, for an item that does not burn), before any gravity: a floating item rises to
     * the surface at up to 0.06 blocks a tick, a heavy one sinks at 0.06. Vanilla 26.x lifts every item very slowly; 1.8.9 lets
     * every item fall through.
     */
    public static double buoyancy(double vy, boolean floats) {
        return floats ? Math.min(vy * 0.9 + 0.01, 0.06) : vy * 0.9 - 0.006;
    }

    /** Ticks an item lies before it despawns, for the slider's minutes (vanilla: 5 minutes, 6000 ticks). */
    public static int despawnTicks(double minutes) {
        return (int) Math.round(minutes * 1200);
    }

    /** Hold the drop key to charge a throw; release to throw. */
    public static final class Charge {
        /** Ticks to full power, and the throw speed at full power as a multiple of vanilla's. */
        static final int FULL = 20;
        static final float MAX = 2.5f;
        private int held = -1;

        /** One client tick: the speed multiple to throw with now (1 for a tap), or 0 while charging or idle. */
        public float tick(boolean down, boolean clicked) {
            if (down) {
                held++;
                return 0;
            }
            float power = held >= 0 || clicked ? 1 + (MAX - 1) * Math.min(Math.max(held, 0), FULL) / FULL : 0;
            held = -1;
            return power;
        }

        public void reset() { held = -1; }

        /** 0 to 1 for the bar by the crosshair; nothing during a tap's first ticks. */
        public float shown() { return held < 3 ? 0 : Math.min(1, held / (float) FULL); }
    }
}
