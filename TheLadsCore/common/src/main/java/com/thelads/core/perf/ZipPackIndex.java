package com.thelads.core.perf;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fast in-memory index for resource pack zips (QuickPack parity).
 * Vanilla FileResourcePack iterates all zip entries on every call to getResourceDomains()
 * and invokes native ZipFile lookup on every hasResourceName() check.
 * This index builds in a single pass and provides O(1) domain set retrieval and entry queries.
 */
public final class ZipPackIndex {
    private final Set<String> domains;
    private final Set<String> entries;

    public ZipPackIndex(Set<String> domains, Set<String> entries) {
        this.domains = Collections.unmodifiableSet(new HashSet<>(domains));
        this.entries = Collections.unmodifiableSet(new HashSet<>(entries));
    }

    public static ZipPackIndex build(ZipFile zip) {
        Set<String> domains = new HashSet<>();
        Set<String> entries = new HashSet<>(Math.max(16, zip.size()));
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry entry = en.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory()) {
                entries.add(name);
            }
            if (name.startsWith("assets/")) {
                int firstSlash = name.indexOf('/');
                int secondSlash = name.indexOf('/', firstSlash + 1);
                if (secondSlash > firstSlash + 1) {
                    String domain = name.substring(firstSlash + 1, secondSlash);
                    if (domain.equals(domain.toLowerCase(Locale.ROOT))) {
                        domains.add(domain);
                    }
                }
            }
        }
        return new ZipPackIndex(domains, entries);
    }

    public static ZipPackIndex fromEntries(Iterable<String> entryNames) {
        Set<String> domains = new HashSet<>();
        Set<String> entries = new HashSet<>();
        for (String name : entryNames) {
            entries.add(name);
            if (name.startsWith("assets/")) {
                int firstSlash = name.indexOf('/');
                int secondSlash = name.indexOf('/', firstSlash + 1);
                if (secondSlash > firstSlash + 1) {
                    String domain = name.substring(firstSlash + 1, secondSlash);
                    if (domain.equals(domain.toLowerCase(Locale.ROOT))) {
                        domains.add(domain);
                    }
                }
            }
        }
        return new ZipPackIndex(domains, entries);
    }

    public Set<String> domains() {
        return domains;
    }

    public boolean hasEntry(String name) {
        return entries.contains(name);
    }

    public int entryCount() {
        return entries.size();
    }
}
