package com.thelads.core.client.social.modsync;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Where a missing jar comes from. SITE: straight from Modrinth's or CurseForge's own CDN, no question asked (the hash check still
 * applies). P2P: from the host's PC over the link, only once the guest said they trust that host's mods.
 */
public final class ModSyncSource {
    public enum Kind { SITE, P2P }

    /** The launcher's list (ModpackPatchEngine.AllowedModDownloadHosts), split by site. */
    private static final Set<String> MODRINTH_HOSTS = Set.of("cdn.modrinth.com"),
            CURSEFORGE_HOSTS = Set.of("edge.forgecdn.net", "mediafilez.forgecdn.net");

    /** The jars to fetch, split by where they come from. */
    public record Plan(List<ModSyncEntry> site, List<ModSyncEntry> p2p) {
        public boolean isEmpty() {
            return site.isEmpty() && p2p.isEmpty();
        }

        /** Jars from the host's PC are code no site ever saw: the guest is asked once per host. */
        public boolean needsTrust() {
            return !p2p.isEmpty();
        }

        public long bytes() {
            long total = 0;
            for (ModSyncEntry m : site) total += m.size();
            for (ModSyncEntry m : p2p) total += m.size();
            return total;
        }
    }

    private ModSyncSource() {}

    public static Kind choose(ModSyncEntry entry) {
        return siteUri(entry) != null ? Kind.SITE : Kind.P2P;
    }

    public static Plan plan(List<ModSyncEntry> missing) {
        List<ModSyncEntry> site = new ArrayList<>(), p2p = new ArrayList<>();
        for (ModSyncEntry m : missing) (choose(m) == Kind.SITE ? site : p2p).add(m);
        return new Plan(List.copyOf(site), List.copyOf(p2p));
    }

    /**
     * The entry's download address when its source's own CDN serves it: https, default port, no user, query or fragment. Null for
     * anything else (the host's list is untrusted: an address elsewhere is never fetched).
     */
    public static URI siteUri(ModSyncEntry entry) {
        if (entry.url().isEmpty()) return null;
        URI uri;
        try {
            uri = new URI(entry.url());
        } catch (URISyntaxException e) {
            return null;
        }
        return allowed(entry.source(), uri) ? uri : null;
    }

    /** Also checked for every redirect a download follows. */
    public static boolean allowed(String source, URI uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null) return false;
        if (uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
        Set<String> hosts = ModSyncEntry.MODRINTH.equals(source) ? MODRINTH_HOSTS
                : ModSyncEntry.CURSEFORGE.equals(source) ? CURSEFORGE_HOSTS : Set.of();
        return hosts.contains(uri.getHost());
    }
}
