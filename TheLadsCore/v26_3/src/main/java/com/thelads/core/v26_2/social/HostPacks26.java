package com.thelads.core.v26_2.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.social.HostOptions;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.social.ui.SocialToasts;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.Pack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Host option "Share my resource packs" (26.3). The host's enabled file packs go to its guests over the game connection
 * (theladscore:host_packs), and the guest applies them as server packs: vanilla takes them away again when it leaves.
 * <pre>
 * guest -> host  HELLO                 only to a server that announced the channel and is LAN or this PC (a friend's world via P2P)
 * host -> guest  OFFER [{sha1,size,name}]   the packs, bottom to top, when the host shares packs
 * guest -> host  WANT [sha1...]        the ones it has not cached; nothing when it has them all
 * host -> guest  CHUNK sha1 offset total bytes   256 KB pieces, each pack at most twice per guest
 * </pre>
 * A public server never gets HELLO (it is not LAN or local) and the host only answers while it hosts.
 */
public final class HostPacks26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-HostPacks");
    static final int HELLO = 0, OFFER = 1, WANT = 2, CHUNK = 3;
    private static final int CHUNK_SIZE = 256 * 1024, MAX_PACKS = 16, MAX_SENDS = 2;
    private static final long MAX_PACK = 128L * 1024 * 1024, MAX_TOTAL = 384L * 1024 * 1024;
    private static final String FOLDER = "lads-host-packs";

    public record Payload(int kind, String text, long offset, long total, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("theladscore", "host_packs"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeUtf(p.text, 32767);
                    buf.writeVarLong(p.offset);
                    buf.writeVarLong(p.total);
                    buf.writeByteArray(p.bytes);
                },
                buf -> new Payload(buf.readByte(), buf.readUtf(32767), buf.readVarLong(), buf.readVarLong(), buf.readByteArray(CHUNK_SIZE)));

        static Payload of(int kind, String text) {
            return new Payload(kind, text, 0, 0, new byte[0]);
        }

        @Override public Type<Payload> type() { return TYPE; }
    }

    /** A pack ready to send: its zip on disk (a folder pack is zipped first). */
    record Prepared(String id, String name, Path file, String sha1, long size) {}

    private static final ExecutorService WORKER = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Lads-HostPacks");
        t.setDaemon(true);
        return t;
    });

    // Host side, per guest
    private static final Map<UUID, List<Prepared>> offers = new ConcurrentHashMap<>();
    private static final Map<String, Integer> sends = new ConcurrentHashMap<>();
    /** Folder packs zipped this run: id -> zip (rebuilt when the folder is newer). */
    private static final Map<String, Prepared> zipped = new ConcurrentHashMap<>();

    // Guest side, this connection
    private static volatile boolean started;
    private static volatile List<JsonObject> offered = List.of();
    private static final Map<String, long[]> receiving = new ConcurrentHashMap<>(); // sha1 -> {received, total}

    private HostPacks26() {}

    public static void init() {
        try {
            PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
        } catch (RuntimeException e) {
            LOGGER.warn("Host packs channel not registered: {}", e.toString());
            return;
        }
        // Our integrated server: announced to the players who join it (guests), so they know this host can share packs
        ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, ctx) -> onHost(payload, ctx.player()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> offers.remove(handler.getPlayer().getUUID()));
        ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(Payload.TYPE.id())) client.execute(HostPacks26::hello);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(HostPacks26::hello));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            started = false;
            offered = List.of();
            receiving.clear();
        });
    }

    // ---- Guest ----

    /** A friend's world (LAN or through P2P on this PC) that can share packs: ask once per connection. */
    private static void hello() {
        Minecraft mc = Minecraft.getInstance();
        if (started || mc.isLocalServer() || !ClientPlayNetworking.canSend(Payload.TYPE) || !friendsWorld(mc.getCurrentServer())) return;
        started = true;
        ClientPlayNetworking.registerReceiver(Payload.TYPE, (payload, ctx) -> onGuest(payload));
        ClientPlayNetworking.send(Payload.of(HELLO, ""));
    }

    /** LAN, or an address on this PC or the home network: never a public server. */
    static boolean friendsWorld(ServerData server) {
        if (server == null) return false;
        if (server.isLan()) return true;
        String host = server.ip == null ? "" : server.ip.trim();
        if (host.startsWith("[")) host = host.substring(1, Math.max(1, host.indexOf(']')));
        else if (host.indexOf(':') == host.lastIndexOf(':') && host.contains(":")) host = host.substring(0, host.indexOf(':'));
        if (host.equalsIgnoreCase("localhost")) return true;
        if (!host.matches("[0-9.]+|[0-9a-fA-F:]+")) return false; // a name: we don't resolve it here
        try {
            InetAddress a = InetAddress.getByName(host);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress();
        } catch (IOException e) {
            return false;
        }
    }

    private static Path cache() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
    }

    private static void onGuest(Payload p) {
        if (p.kind() == OFFER) onOffer(p.text());
        else if (p.kind() == CHUNK) {
            byte[] bytes = p.bytes();
            WORKER.execute(() -> onChunk(p.text(), p.offset(), p.total(), bytes));
        }
    }

    private static void onOffer(String json) {
        List<JsonObject> list = new ArrayList<>();
        long total = 0;
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                String sha1 = o.get("sha1").getAsString();
                long size = o.get("size").getAsLong();
                if (!sha1.matches("[0-9a-f]{40}") || size <= 0 || size > MAX_PACK) continue;
                if (list.size() >= MAX_PACKS || (total += size) > MAX_TOTAL) break;
                list.add(o);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Host packs: bad offer: {}", e.toString());
            return;
        }
        if (list.isEmpty()) return;
        offered = List.copyOf(list);
        JsonArray want = new JsonArray();
        for (JsonObject o : list) {
            String sha1 = o.get("sha1").getAsString();
            Path file = cache().resolve(sha1 + ".zip");
            try {
                if (Files.isRegularFile(file) && Files.size(file) == o.get("size").getAsLong()) {
                    Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
                    continue;
                }
            } catch (IOException ignored) {
            }
            receiving.put(sha1, new long[]{0, o.get("size").getAsLong()});
            want.add(sha1);
        }
        if (want.isEmpty()) {
            apply();
            return;
        }
        SocialToasts.showAction("Resource packs", "Getting the host's resource packs...", null, null);
        ClientPlayNetworking.send(Payload.of(WANT, want.toString()));
    }

    private static synchronized void onChunk(String sha1, long offset, long total, byte[] bytes) {
        long[] state = receiving.get(sha1);
        if (state == null || total != state[1] || offset < 0 || offset + bytes.length > total) return;
        Path part = cache().resolve(sha1 + ".zip.part");
        try {
            Files.createDirectories(part.getParent());
            try (RandomAccessFile f = new RandomAccessFile(part.toFile(), "rw")) {
                f.seek(offset);
                f.write(bytes);
            }
            state[0] += bytes.length;
            if (state[0] < total) return;
            receiving.remove(sha1);
            if (!sha1.equals(sha1(part))) {
                Files.deleteIfExists(part);
                LOGGER.warn("Host packs: {} did not match its hash, dropped", sha1);
                return;
            }
            Files.move(part, cache().resolve(sha1 + ".zip"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            receiving.remove(sha1);
            LOGGER.warn("Host packs: could not save {}: {}", sha1, e.toString());
            return;
        }
        if (receiving.isEmpty()) apply();
    }

    /** All the offered packs that arrived, bottom to top, as server packs; vanilla drops them when we leave. */
    private static void apply() {
        Minecraft mc = Minecraft.getInstance();
        List<JsonObject> list = offered;
        mc.execute(() -> {
            if (!started) return; // left meanwhile
            int n = 0;
            for (JsonObject o : list) {
                String sha1 = o.get("sha1").getAsString();
                Path file = cache().resolve(sha1 + ".zip");
                if (!Files.isRegularFile(file)) continue;
                mc.getDownloadedPackSource().pushLocalPack(UUID.nameUUIDFromBytes(("lads-host-pack:" + sha1).getBytes(StandardCharsets.UTF_8)), file);
                n++;
            }
            if (n == 0) return;
            mc.getDownloadedPackSource().allowServerPacks();
            SocialToasts.showAction("Resource packs", "Using the host's " + n + (n == 1 ? " pack" : " packs") + " while you play here", null, null);
            LOGGER.info("Host packs: applied {} pack(s)", n);
        });
        WORKER.execute(HostPacks26::pruneCache);
    }

    /** Cached packs not used for 30 days go. */
    private static void pruneCache() {
        long old = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
        try (Stream<Path> files = Files.list(cache())) {
            for (Path f : files.toList()) if (Files.getLastModifiedTime(f).toMillis() < old) Files.deleteIfExists(f);
        } catch (IOException ignored) {
        }
    }

    // ---- Host ----

    private static void onHost(Payload p, ServerPlayer player) {
        SocialState s = SocialState.get();
        if (s == null || !s.hostSession().isHosting() || isOwner(player)) return;
        HostOptions options = s.hostOptions();
        if (options == null || !options.sharePacks) return;
        if (p.kind() == HELLO) offer(player, options);
        else if (p.kind() == WANT) send(player, p.text());
    }

    private static boolean isOwner(ServerPlayer player) {
        var server = player.level().getServer();
        var owner = server == null ? null : server.getSingleplayerProfile();
        return owner != null && owner.id().equals(player.getUUID());
    }

    private static void offer(ServerPlayer player, HostOptions options) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> selected(mc, options), mc)
                .thenApplyAsync(HostPacks26::prepare, WORKER)
                .thenAccept(packs -> {
                    if (packs.isEmpty() || player.hasDisconnected()) return;
                    offers.put(player.getUUID(), packs);
                    JsonArray array = new JsonArray();
                    for (Prepared pack : packs) {
                        JsonObject o = new JsonObject();
                        o.addProperty("sha1", pack.sha1());
                        o.addProperty("size", pack.size());
                        o.addProperty("name", pack.name().length() > 64 ? pack.name().substring(0, 64) : pack.name());
                        array.add(o);
                    }
                    ServerPlayNetworking.send(player, Payload.of(OFFER, array.toString()));
                })
                .exceptionally(e -> {
                    LOGGER.warn("Host packs: could not offer packs: {}", e.toString());
                    return null;
                });
    }

    /** The host's enabled file packs (bottom to top) the options share: id -> {name, path}. */
    private static Map<String, String[]> selected(Minecraft mc, HostOptions options) {
        Map<String, String[]> out = new LinkedHashMap<>();
        Path dir = mc.getResourcePackDirectory();
        for (Pack pack : mc.getResourcePackRepository().getSelectedPacks()) {
            String id = pack.location().id();
            if (!id.startsWith("file/")) continue;
            if (!options.shareAllPacks && !options.sharedPacks.contains(id)) continue;
            Path path = dir.resolve(id.substring(5)).normalize();
            if (!path.startsWith(dir)) continue;
            out.put(id, new String[]{pack.getTitle().getString(), path.toString()});
        }
        return out;
    }

    private static List<Prepared> prepare(Map<String, String[]> packs) {
        List<Prepared> out = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, String[]> e : packs.entrySet()) {
            try {
                Prepared p = prepare(e.getKey(), e.getValue()[0], Path.of(e.getValue()[1]));
                if (p == null || p.size() > MAX_PACK) continue;
                if (out.size() >= MAX_PACKS || (total += p.size()) > MAX_TOTAL) break;
                out.add(p);
            } catch (IOException ex) {
                LOGGER.warn("Host packs: skipped {}: {}", e.getKey(), ex.toString());
            }
        }
        return out;
    }

    private static Prepared prepare(String id, String name, Path path) throws IOException {
        if (Files.isRegularFile(path)) return new Prepared(id, name, path, sha1(path), Files.size(path));
        if (!Files.isDirectory(path)) return null;
        long newest = newest(path);
        Prepared done = zipped.get(id);
        if (done != null && Files.isRegularFile(done.file()) && Files.getLastModifiedTime(done.file()).toMillis() >= newest) return done;
        Path zip = Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER).resolve("out")
                .resolve(Integer.toHexString(id.hashCode()) + ".zip");
        Files.createDirectories(zip.getParent());
        Path tmp = zip.resolveSibling(zip.getFileName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream z = new ZipOutputStream(os); Stream<Path> files = Files.walk(path)) {
            for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                z.putNextEntry(new ZipEntry(path.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, z);
                z.closeEntry();
            }
        }
        Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
        Prepared p = new Prepared(id, name, zip, sha1(zip), Files.size(zip));
        zipped.put(id, p);
        return p;
    }

    private static long newest(Path dir) throws IOException {
        long newest = 0;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.toList()) newest = Math.max(newest, Files.getLastModifiedTime(f).toMillis());
        }
        return newest;
    }

    /** The packs this guest asked for, from what it was offered only, each at most MAX_SENDS times; paced so play goes on. */
    private static void send(ServerPlayer player, String json) {
        List<Prepared> offer = offers.get(player.getUUID());
        if (offer == null) return;
        List<Prepared> wanted = new ArrayList<>();
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                String sha1 = e.getAsString();
                for (Prepared p : offer) {
                    if (!p.sha1().equals(sha1) || wanted.contains(p)) continue;
                    if (sends.merge(player.getUUID() + "/" + sha1, 1, Integer::sum) <= MAX_SENDS) wanted.add(p);
                }
            }
        } catch (RuntimeException e) {
            return;
        }
        if (wanted.isEmpty()) return;
        WORKER.execute(() -> {
            byte[] buf = new byte[CHUNK_SIZE];
            for (Prepared p : wanted) {
                try (InputStream in = Files.newInputStream(p.file())) {
                    long offset = 0;
                    int n;
                    while ((n = in.readNBytes(buf, 0, buf.length)) > 0) {
                        if (player.hasDisconnected()) return;
                        ServerPlayNetworking.send(player, new Payload(CHUNK, p.sha1(), offset, p.size(), java.util.Arrays.copyOf(buf, n)));
                        offset += n;
                        Thread.sleep(25);
                    }
                } catch (IOException | InterruptedException e) {
                    LOGGER.warn("Host packs: sending {} stopped: {}", p.name(), e.toString());
                    return;
                }
            }
            LOGGER.info("Host packs: sent {} pack(s) to {}", wanted.size(), player.getGameProfile().name());
        });
    }

    static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder hex = new StringBuilder(40);
            for (byte b : md.digest()) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Pack id -> name of the host's enabled file packs (Host screen list). */
    static Map<String, String> shareable() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Pack pack : Minecraft.getInstance().getResourcePackRepository().getSelectedPacks()) {
            String id = pack.location().id();
            if (id.startsWith("file/")) out.put(id, pack.getTitle().getString());
        }
        return out;
    }
}
