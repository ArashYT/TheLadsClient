package com.thelads.smp.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only driver (-Dladsqa.world=1): at the title screen creates a NEW singleplayer world with default settings (like a player pressing
 * "Create New World"), then flies the player DIST blocks out in each direction in the Overworld, the Nether and every other dimension,
 * while a watchdog records any server-thread stall. Report: ladsqa/world.txt. Options: -Dladsqa.seed=, -Dladsqa.worldtype=<text in the
 * preset name, e.g. Superflat>, -Dladsqa.dist=1500, -Dladsqa.speed=1.5 (blocks per tick), -Dladsqa.dwell=100 (seconds), -Dladsqa.halt=true.
 */
public class WorldDriver implements ClientModInitializer {
    static final boolean ON = Boolean.getBoolean("ladsqa.world");
    static final int DIST = Integer.getInteger("ladsqa.dist", 1500);
    static final double SPEED = Double.parseDouble(System.getProperty("ladsqa.speed", "1.5"));
    static final int DWELL = Integer.getInteger("ladsqa.dwell", 100);
    static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("ladsqa");

    int tick, titleTicks, createTicks, state; // 0 title wait, 1 open create screen, 2 wait screen, 3 creating, 4 flying, 5 done
    String lastScreen = "";
    volatile boolean watchRun = true;
    int fails;

    record Leg(ResourceKey<Level> dim, int tx, int tz) { }
    final List<Leg> legs = new ArrayList<>();
    int legIdx = -1, legTick, dwellTicks;
    double px, pz, y = 250, tx, tz;
    ResourceKey<Level> curDim;
    long joinedAt;
    boolean inDwell, testedBlocks;
    long legStart;
    BlockPos testPos;

    static void log(String s) {
        String line = String.format("+%6d ms  %s%n", FrameWatch.ms(System.nanoTime()), s);
        System.out.print("[LadsQA] " + line);
        try { Files.createDirectories(DIR); Files.writeString(DIR.resolve("world.txt"), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ignored) { }
    }

    @Override
    public void onInitializeClient() {
        if (!ON) return;
        try { Files.createDirectories(DIR); Files.writeString(DIR.resolve("world.txt"), ""); } catch (Exception ignored) { }
        log("client init (ladsqa world driver on) dist=" + DIST + " speed=" + SPEED);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientTickEvents.START_CLIENT_TICK.register(this::move);
    }

    /** Creative flight like a player's: the client player keeps a constant velocity toward the target (no teleports, so chunks load as they would in play). */
    void move(Minecraft mc) {
        if (state != 4 || inDwell || mc.player == null || mc.level == null || !mc.level.dimension().equals(curDim)) return;
        if (!mc.player.getAbilities().mayfly) return;
        mc.player.getAbilities().flying = true;
        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ(), d = Math.hypot(dx, dz);
        double vy = Math.max(-1, Math.min(1, (y - mc.player.getY()) * 0.2));
        if (d < SPEED * 1.5) { mc.player.setDeltaMovement(0, vy, 0); return; }
        mc.player.setDeltaMovement(dx / d * SPEED, vy, dz / d * SPEED);
    }

    void tick(Minecraft mc) {
        tick++;
        if (shotAt != 0 && tick >= shotAt) { shotAt = 0; shot(mc, shotWhy); }
        String sc = mc.gui.screen() == null ? "null" : mc.gui.screen().getClass().getSimpleName();
        String shown = sc + (mc.gui.overlay() == null ? "" : "+overlay");
        if (!shown.equals(lastScreen)) { lastScreen = shown; FrameWatch.lastScreen = shown; log("screen " + shown); }
        switch (state) {
            case 0 -> {
                if (mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null) {
                    if (++titleTicks == 1) log("TITLE reached (overlay gone)");
                    if (titleTicks > 60) state = 1;
                }
            }
            case 1 -> { log("opening CreateWorldScreen"); CreateWorldScreen.openFresh(mc, () -> { }); state = 2; }
            case 2 -> {
                if (mc.gui.screen() instanceof CreateWorldScreen cws) {
                    if (++createTicks == 1) {
                        WorldCreationUiState ui = cws.getUiState();
                        String seed = System.getProperty("ladsqa.seed", "");
                        if (!seed.isEmpty()) ui.setSeed(seed);
                        String wt = System.getProperty("ladsqa.worldtype", "");
                        if (!wt.isEmpty()) {
                            var all = new ArrayList<>(ui.getNormalPresetList()); all.addAll(ui.getAltPresetList());
                            for (var e : all) if (e.describePreset().getString().toLowerCase().contains(wt.toLowerCase())) { ui.setWorldType(e); break; }
                        }
                        ui.setName("LadsQA-" + System.currentTimeMillis() / 1000);
                        log("CreateWorldScreen ready: name=" + ui.getName() + " seed='" + ui.getSeed() + "' type=" + ui.getWorldType().describePreset().getString() + " mode=" + ui.getGameMode());
                    }
                    if (createTicks == 30) {
                        try {
                            Method m = CreateWorldScreen.class.getDeclaredMethod("onCreate"); m.setAccessible(true);
                            log("CLICK Create New World"); m.invoke(cws); state = 3;
                        } catch (Exception e) { log("FAIL onCreate " + e); state = 5; }
                    }
                }
            }
            case 3 -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.gui.screen() == null) {
                    if (joinedAt == 0) { joinedAt = System.nanoTime(); log("JOINED: player in world, no screen: " + mc.player.blockPosition()); }
                    if (tick % 20 == 0) ackWatch(mc);
                    if (Boolean.getBoolean("ladsqa.dimtest")) { dimTest(mc); }
                    else if (Boolean.getBoolean("ladsqa.stuckonly")) {
                        // no flight: after 12 s test blocks and mobs at spawn for DWELL seconds, then finish
                        if (!dwellStarted && System.nanoTime() - joinedAt > 12_000_000_000L) {
                            dwellStarted = true; var srv = mc.getSingleplayerServer();
                            srv.execute(() -> cmd(srv, "gamemode creative @a")); startServerWatch(srv);
                            curDim = Level.OVERWORLD; state = 4; legIdx = -1; inDwell = true; dwellTicks = 0;
                        } else if (!dwellStarted && System.nanoTime() - joinedAt > 120_000_000_000L) finish(mc);
                    } else if (tick % 20 == 0 && System.nanoTime() - joinedAt > 8_000_000_000L) startFlight(mc);
                }
            }
            case 4 -> fly(mc);
            default -> { }
        }
    }

    int dimStep; long dimAt; 
    /** Teleport like a portal trip: into the Nether, into the End, back to the Overworld; each time wait until the client is there and the world ticks quietly. */
    void dimTest(Minecraft mc) {
        var srv = mc.getSingleplayerServer(); long now = System.nanoTime();
        if (dimStep == 0) {
            if (now - joinedAt < 12_000_000_000L) return;
            startServerWatch(srv); srv.execute(() -> cmd(srv, "gamemode creative @a")); dimAt = now; dimStep = 1; log("DIMTEST begin (server stall tracking on)"); return;
        }
        String[] dims = {"minecraft:the_nether", "minecraft:the_end", "minecraft:overworld", "minecraft:the_nether", "minecraft:overworld"};
        int[] ys = {100, 80, 150, 100, 150};
        int i = (dimStep - 1) / 2;
        if (i >= dims.length) { finish(mc); return; }
        if ((dimStep & 1) == 1) { // issue the teleport
            if (now - dimAt < 8_000_000_000L) return;
            String d = dims[i]; int y = ys[i]; int x = 10 + 8 * i; // a different, not yet generated spot each time
            log("DIMTEST tp into " + d + " at (" + x + "," + y + "," + x + ")");
            srv.execute(() -> cmd(srv, "execute in " + d + " run tp @a " + x + " " + y + " " + x));
            dimAt = now; dimStep++;
        } else { // wait until the client is in the dimension, then 20 s of play-like time
            String d = dims[i];
            if (!mc.level.dimension().identifier().toString().equals(d)) { if (now - dimAt > 180_000_000_000L) { log("FAIL DIMTEST client never arrived in " + d); fails++; dimStep++; dimAt = now; } return; }
            if (dimAt != 0 && !dimArrived) { dimArrived = true; log("DIMTEST client arrived in " + d + " after " + (now - dimAt) / 1_000_000L + " ms; chunks under player=" + (mc.level.getChunk((int) Math.floor(mc.player.getX()) >> 4, (int) Math.floor(mc.player.getZ()) >> 4, ChunkStatus.FULL, false) != null)); dimAt = now; }
            if (now - dimAt > 25_000_000_000L) { report(mc, srv, "dimtest-" + d); shot(mc, "dim-" + d); dimStep++; dimAt = now; dimArrived = false; }
        }
    }
    boolean dimArrived, homeDone;

    int lastUnacked = -1, lastClientChunks = -1; long unackedSince;

    /** Every second after the join: is the server holding chunk batches back because none is acknowledged any more? */
    void ackWatch(Minecraft mc) {
        try {
            var srv = mc.getSingleplayerServer();
            if (srv == null || srv.getPlayerList().getPlayers().isEmpty()) return;
            var sp = srv.getPlayerList().getPlayers().get(0);
            var cs = sp.connection.chunkSender;
            int unacked = (int) field(cs, "unacknowledgedBatches"), max = (int) field(cs, "maxUnacknowledgedBatches");
            int cli = mc.level.getChunkSource().getLoadedChunksCount();
            long now = System.nanoTime();
            if (unacked < max || unacked != lastUnacked || cli != lastClientChunks) { lastUnacked = unacked; lastClientChunks = cli; unackedSince = now; }
            else if (now - unackedSince > 15_000_000_000L && !ackStuckLogged) {
                ackStuckLogged = true; fails++;
                log("FAIL ACKSTUCK: server holds " + unacked + "/" + max + " unacknowledged chunk batches for 15 s, client has " + cli + " chunks and nothing queued; " + diag(mc, sp));
                shot(mc, "ackstuck");
            }
        } catch (Throwable t) { log("ackWatch fail " + t); }
    }
    boolean ackStuckLogged, dwellStarted;
    int shotAt; String shotWhy = "";
    static volatile long maxStallMs, maxStallAt;

    void startFlight(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        log("start flight; dims=" + srv.levelKeys());
        report(mc, srv, "at-spawn");
        shot(mc, "spawn");
        List<ResourceKey<Level>> dims = new ArrayList<>(srv.levelKeys());
        dims.sort((a, b) -> Boolean.compare(a != Level.OVERWORLD, b != Level.OVERWORLD));
        for (ResourceKey<Level> k : dims) {
            int d = k == Level.OVERWORLD ? DIST : (k == Level.NETHER ? DIST / 2 : DIST / 3);
            // four straight legs through the origin: +x out, across to -x, then +z and -z
            legs.add(new Leg(k, d, 0)); legs.add(new Leg(k, -d, 0)); legs.add(new Leg(k, 0, d)); legs.add(new Leg(k, 0, -d));
        }
        int maxLegs = Integer.getInteger("ladsqa.maxlegs", 0);
        if (maxLegs > 0) while (legs.size() > maxLegs) legs.remove(legs.size() - 1);
        srv.execute(() -> cmd(srv, "gamemode creative @a"));
        startServerWatch(srv);
        state = 4; legIdx = -1; nextLeg(mc);
    }

    void cmd(IntegratedServer srv, String c) {
        try { srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack().withSuppressedOutput(), c); } catch (Throwable t) { log("cmd fail " + c + ": " + t); }
    }

    void nextLeg(Minecraft mc) {
        legIdx++;
        if (legIdx >= legs.size() && Boolean.getBoolean("ladsqa.backhome") && !homeDone) { homeDone = true; log("== back home: Overworld again"); legs.add(new Leg(Level.OVERWORLD, 300, 0)); legs.add(new Leg(Level.OVERWORLD, 0, 300)); }
        if (legIdx >= legs.size()) { finish(mc); return; }
        Leg l = legs.get(legIdx);
        IntegratedServer srv = mc.getSingleplayerServer();
        if (l.dim != curDim) {
            curDim = l.dim; px = 0; pz = 0; y = l.dim == Level.NETHER ? 100 : (l.dim == Level.OVERWORLD ? 250 : 120);
            String dn = dimName(); int yy = (int) y;
            log("== dimension " + dn + " (tp 0," + yy + ",0)");
            srv.execute(() -> cmd(srv, "execute in " + dn + " run tp @a 0 " + yy + " 0"));
        }
        tx = l.tx; tz = l.tz; legTick = 0; lastProgressAt = System.nanoTime(); lastProgX = px; lastProgZ = pz; legStart = System.nanoTime();
        log("-- leg " + legIdx + "/" + legs.size() + " " + dimName() + " to (" + (int) tx + "," + (int) tz + ") from (" + (int) px + "," + (int) pz + ")");
    }

    String dimName() { return curDim.identifier().toString(); }
    static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    long lastProgressAt; double lastProgX, lastProgZ;

    void fly(Minecraft mc) {
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null || mc.level == null) { if (tick % 100 == 0) log("WARN no server/player/level in flight"); return; }
        if (inDwell) { dwell(mc, srv); return; }
        if (!mc.level.dimension().equals(curDim)) { if (tick % 100 == 0) log("waiting for client to enter " + dimName() + " (in " + mc.level.dimension().identifier() + ")"); return; }
        legTick++;
        px = mc.player.getX(); pz = mc.player.getZ();
        double d = Math.hypot(tx - px, tz - pz);
        if (d <= SPEED * 1.6) {
            report(mc, srv, "LEG-END after " + (System.nanoTime() - legStart) / 1_000_000_000L + " s");
            mc.player.setXRot(55f); shotAt = tick + 30; shotWhy = "leg" + legIdx + "-looking-down";
            boolean overworldDone = curDim == Level.OVERWORLD && (legIdx + 1 >= legs.size() || legs.get(legIdx + 1).dim != Level.OVERWORLD);
            if (overworldDone && !testedBlocks) { inDwell = true; dwellTicks = 0; return; }
            nextLeg(mc);
            return;
        }
        long now = System.nanoTime();
        if (Math.hypot(px - lastProgX, pz - lastProgZ) > 8) { lastProgX = px; lastProgZ = pz; lastProgressAt = now; }
        else if (now - lastProgressAt > Long.getLong("ladsqa.stuckms", 90000L) * 1_000_000L) {
            log("FAIL STUCK: player did not move 8 blocks in " + Long.getLong("ladsqa.stuckms", 90000L) / 1000 + " s at (" + (int) px + "," + (int) pz + "), chunk loaded under player: " + (mc.level.getChunk((int) Math.floor(px) >> 4, (int) Math.floor(pz) >> 4, ChunkStatus.FULL, false) != null));
            report(mc, srv, "STUCK"); shot(mc, "stuck"); fails++; lastProgressAt = now; nextLeg(mc); return;
        }
        if (legTick % 100 == 0) report(mc, srv, "fly");
    }

    /** Standing at spawn in the Overworld: break and place a block, spawn mobs and check that they tick, for DWELL seconds. */
    void dwell(Minecraft mc, IntegratedServer srv) {
        dwellTicks++;
        if (dwellTicks == 1) {
            log("== dwell " + DWELL + " s at spawn: block break/place + mob tick test");
            // no teleport: the player hovers where the last leg ended, blocks are tested right below, mobs right next to the player
            curDim = Level.OVERWORLD;
        }
        if (dwellTicks == 80) {
            testPos = mc.player.blockPosition().below(2);
            BlockPos p = testPos;
            srv.execute(() -> cmd(srv, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " minecraft:stone"));
        }
        if (dwellTicks == 120) {
            boolean before = !mc.level.getBlockState(testPos).isAir();
            boolean ok = mc.gameMode.startDestroyBlock(testPos, net.minecraft.core.Direction.UP);
            log("BREAK client sees block=" + before + " startDestroyBlock()=" + ok + " at " + testPos);
        }
        if (dwellTicks == 160) {
            boolean cliAir = mc.level.getBlockState(testPos).isAir();
            ServerLevel sl = srv.getLevel(Level.OVERWORLD);
            boolean srvAir = sl != null && sl.getBlockState(testPos).isAir();
            log("BREAK result clientAir=" + cliAir + " serverAir=" + srvAir + (cliAir && srvAir ? " OK" : " FAIL"));
            if (!(cliAir && srvAir)) fails++;
            BlockPos p = testPos;
            srv.execute(() -> cmd(srv, "item replace entity @p weapon.mainhand with minecraft:cobblestone 8"));
        }
        if (dwellTicks == 180) {
            var against = testPos.below();
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(against).add(0, 0.5, 0), net.minecraft.core.Direction.UP, against, false);
            log("PLACE useItemOn with " + mc.player.getMainHandItem() + " against " + against + " -> " + mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        }
        if (dwellTicks == 200) {
            ServerLevel sl = srv.getLevel(Level.OVERWORLD);
            boolean placed = sl != null && sl.getBlockState(testPos).is(Blocks.COBBLESTONE);
            boolean cli = mc.level.getBlockState(testPos).is(Blocks.COBBLESTONE);
            log("PLACE (useItemOn) result: server cobblestone=" + placed + " client cobblestone=" + cli + (placed && cli ? " OK" : " FAIL"));
            if (!(placed && cli)) fails++;
            srv.execute(() -> {
                cmd(srv, "difficulty normal");
                cmd(srv, "execute in minecraft:overworld as @p at @s run summon minecraft:zombie ~3 ~ ~3 {PersistenceRequired:1b,NoGravity:1b}");
                cmd(srv, "execute in minecraft:overworld as @p at @s run summon minecraft:cow ~-3 ~ ~3 {PersistenceRequired:1b,NoGravity:1b}");
            });
        }
        if (dwellTicks == 220 || dwellTicks == 220 + 160) {
            final int n = dwellTicks;
            srv.execute(() -> {
                ServerLevel sl = srv.getLevel(Level.OVERWORLD);
                if (sl == null) return;
                StringBuilder sb = new StringBuilder(); int adv = 0, mobs = 0;
                for (var e : sl.getAllEntities()) {
                    if (e instanceof Mob m && (m.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE || m.getType() == net.minecraft.world.entity.EntityTypes.COW)) {
                        mobs++; sb.append(m.getType().toShortString()).append("(age ").append(m.tickCount).append(") "); if (m.tickCount > 100) adv++;
                    }
                }
                boolean second = n != 220;
                log("MOBS tick check " + (second ? "#2 (8 s later)" : "#1") + ": " + sb + (second ? (adv > 0 ? " OK (age advancing)" : " FAIL (not ticking)") : ""));
                if (second && adv == 0) fails++;
            });
        }
        if (dwellTicks % 400 == 0) report(mc, srv, "dwell");
        if (dwellTicks >= DWELL * 20) { inDwell = false; testedBlocks = true; nextLeg(mc); }
    }

    void report(Minecraft mc, IntegratedServer srv, String tag) {
        try {
            int cx = (int) Math.floor(px) >> 4, cz = (int) Math.floor(pz) >> 4;
            int cli = mc.level.getChunkSource().getLoadedChunksCount();
            boolean here = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) != null;
            ServerLevel sl = srv.getLevel(curDim == null ? Level.OVERWORLD : curDim);
            int sld = sl == null ? -1 : sl.getChunkSource().getLoadedChunksCount();
            String cdim = mc.level.dimension().identifier().toString();
            var sp = srv.getPlayerList().getPlayers().isEmpty() ? null : srv.getPlayerList().getPlayers().get(0);
            // the player's REAL position: client side and server side
            int rcx = (int) Math.floor(mc.player.getX()) >> 4, rcz = (int) Math.floor(mc.player.getZ()) >> 4;
            boolean realHere = mc.level.getChunk(rcx, rcz, ChunkStatus.FULL, false) != null;
            String diag = diag(mc, sp);
            log(String.format("%s want=(%d,%d) clientPlayer=(%.0f,%.0f,%.0f) srvPlayer=%s mode=%s clientDim=%s srvTick=%d mspt=%.1f clientChunks=%d clientHasChunkUnderPlayer=%s srvChunks=%d fps=%d", tag, (int) px, (int) pz,
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(), sp == null ? "none" : String.format("(%.0f,%.0f,%.0f)", sp.getX(), sp.getY(), sp.getZ()),
                    sp == null ? "?" : sp.gameMode(), cdim, srv.getTickCount(), srv.getAverageTickTimeNanos() / 1e6, cli, realHere, sld, mc.getFps()) + " | " + diag);
        } catch (Throwable t) { log("report fail " + t); }
    }

    static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { var f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    /** Chunk-delivery state of both sides: is the client connected and handling packets, what chunk rate did it ask for, what does the server's sender hold back. */
    static Object cc0(Minecraft mc) throws Exception { return mc.getConnection().getConnection().getPacketListener(); }

    static String chanState(net.minecraft.network.Connection c) {
        try {
            var ch = (io.netty.channel.Channel) field(c, "channel");
            String extra = "";
            try { extra += ",autoRead=" + ch.config().isAutoRead(); } catch (Throwable ignored) { }
            try { extra += ",inboundBuf=" + ((java.util.Collection<?>) field(ch, "inboundBuffer")).size(); } catch (Throwable ignored) { }
            try { extra += ",state=" + field(ch, "state"); } catch (Throwable ignored) { }
            try {
                var fc = ch.pipeline().get(io.netty.handler.flow.FlowControlHandler.class);
                if (fc != null) { Object q = field(fc, "queue"); extra += ",flowQueue=" + (q == null ? "null" : String.valueOf(q.getClass().getMethod("size").invoke(q))) + ",flowShouldConsume=" + field(fc, "shouldConsume"); }
            } catch (Throwable t) { extra += ",flowFail=" + t; }
            try {
                var bd = ch.pipeline().get("bundler");
                if (bd != null) { Object cb = field(bd, "currentBundler"); extra += ",bundlerOpen=" + (cb != null); if (cb != null) extra += "(" + cb.getClass().getName() + ")"; }
            } catch (Throwable t) { extra += ",bundlerFail=" + t; }
            try { extra += ",pendingActions=" + ((java.util.Collection<?>) field(c, "pendingActions")).size(); } catch (Throwable ignored) { }
            try { extra += ",antiXrayHeld=" + ((java.util.Collection<?>) field(c, "antiXray$isActionReady")).size(); } catch (Throwable ignored) { }
            try { extra += ",received=" + field(c, "receivedPackets") + ",sent=" + field(c, "sentPackets"); } catch (Throwable ignored) { }
            return "active=" + ch.isActive() + ",open=" + ch.isOpen() + ",writable=" + ch.isWritable() + extra + ",cls=" + ch.getClass().getSimpleName();
        } catch (Throwable t) { return "?" + t; }
    }

    String diag(Minecraft mc, net.minecraft.server.level.ServerPlayer sp) {
        StringBuilder sb = new StringBuilder("diag:");
        try {
            var cpl = mc.getConnection();
            var cc = cpl.getConnection();
            try {
                var pl = cc0(mc);
                sb.append(" connListener=").append(pl == null ? "null" : pl.getClass().getName()).append(pl == mc.getConnection() ? "(same as mc.getConnection)" : "(DIFFERENT from mc.getConnection " + mc.getConnection().getClass().getName() + ")");
            } catch (Throwable t) { sb.append(" listenerFail=").append(t); }
            sb.append(" clientConnected=").append(cc.isConnected()).append(" ch(").append(chanState(cc)).append(")");
            sb.append(" clientQueuedPackets=").append(((java.util.Queue<?>) field(mc.packetProcessor(), "packetsToBeHandled")).size());
            sb.append(" clientDesiredChunksPerTick=").append(((net.minecraft.client.multiplayer.ChunkBatchSizeCalculator) field(cpl, "chunkBatchSizeCalculator")).getDesiredChunksPerTick());
        } catch (Throwable t) { sb.append(" clientDiagFail=").append(t); }
        try {
            if (sp != null) {
                try { var sc = (net.minecraft.network.Connection) field(sp.connection, "connection"); sb.append(" | srvConn(").append(chanState(sc)).append(")"); } catch (Throwable t) { sb.append(" srvConnFail=").append(t); }
                var cs = sp.connection.chunkSender;
                sb.append(" | srvDesired=").append(field(cs, "desiredChunksPerTick")).append(" quota=").append(field(cs, "batchQuota")).append(" unacked=").append(field(cs, "unacknowledgedBatches"))
                    .append("/").append(field(cs, "maxUnacknowledgedBatches")).append(" pending=").append(((java.util.Collection<?>) field(cs, "pendingChunks")).size());
            }
        } catch (Throwable t) { sb.append(" srvDiagFail=").append(t); }
        return sb.toString();
    }

    void shot(Minecraft mc, String why) {
        try { net.minecraft.client.Screenshot.grab(mc, false); log("screenshot requested: " + why); } catch (Throwable t) { log("screenshot fail " + t); }
    }

    void startServerWatch(IntegratedServer srv) {
        Thread t = new Thread(() -> {
            int lastTick = srv.getTickCount(); long lastChange = System.nanoTime(); boolean stalled = false; long lastDump = 0;
            while (watchRun && srv.isRunning()) {
                try { Thread.sleep(250); } catch (InterruptedException e) { return; }
                int tc = srv.getTickCount(); long now = System.nanoTime();
                if (tc != lastTick) {
                    long gapMs = (now - lastChange) / 1_000_000L;
                    if (gapMs > maxStallMs) { maxStallMs = gapMs; maxStallAt = FrameWatch.ms(now); }
                    if (gapMs > 1500 && !stalled) log("STALL " + gapMs + " ms (server tick count did not advance)");
                    if (stalled) { log("SRVSTALL ended after " + (now - lastChange) / 1_000_000L + " ms"); stalled = false; }
                    lastTick = tc; lastChange = now; lastDump = 0; continue;
                }
                long stuck = (now - lastChange) / 1_000_000L;
                if (stuck > 3000 && now - lastDump > 2_000_000_000L) {
                    stalled = true; lastDump = now;
                    Thread st = srv.getRunningThread();
                    StringBuilder sb = new StringBuilder("SRVSTALL server tick frozen for " + stuck + " ms; server thread stack:");
                    if (st != null) for (StackTraceElement el : st.getStackTrace()) sb.append("\n      ").append(el);
                    log(sb.toString());
                    for (var e : Thread.getAllStackTraces().entrySet()) {
                        String n = e.getKey().getName();
                        Thread.State s = e.getKey().getState();
                        if ((n.startsWith("Worker-Main") || n.toLowerCase().startsWith("c2me") || n.startsWith("Chunk")) && s != Thread.State.WAITING && s != Thread.State.TIMED_WAITING) {
                            StringBuilder s2 = new StringBuilder("   busy " + n + " " + s);
                            int k = 0; for (StackTraceElement el : e.getValue()) { if (k++ > 14) break; s2.append("\n        ").append(el); }
                            log(s2.toString());
                        }
                    }
                }
            }
        }, "ladsqa-srvwatch");
        t.setDaemon(true); t.start();
    }

    void finish(Minecraft mc) {
        state = 5; watchRun = false;
        log("DONE flight legs=" + legs.size() + " fails=" + fails + " longestServerStall=" + maxStallMs + " ms at +" + maxStallAt);
        try { Files.writeString(DIR.resolve("world-summary.txt"), "DONE fails=" + fails + "\n"); } catch (Exception ignored) { }
        if (Boolean.getBoolean("ladsqa.halt")) mc.stop();
    }
}
