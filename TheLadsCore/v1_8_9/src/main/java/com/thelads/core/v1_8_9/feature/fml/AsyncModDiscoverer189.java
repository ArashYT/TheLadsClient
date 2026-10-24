package com.thelads.core.v1_8_9.feature.fml;

import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.thelads.core.perf.AsyncModScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Executes FML mod exploration across candidate jars in parallel on multi-core systems,
 * with synchronized ASMDataTable recording and deterministic container ordering (PolyPatcher parity).
 */
public final class AsyncModDiscoverer189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private AsyncModDiscoverer189() {}

    private static final class SynchronizedASMTable extends ASMDataTable {
        private final ASMDataTable delegate;

        SynchronizedASMTable(ASMDataTable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void addASMData(ModCandidate candidate, String annotation, String targetClass, String memberName, Map<String, Object> values) {
            synchronized (delegate) {
                delegate.addASMData(candidate, annotation, targetClass, memberName, values);
            }
        }

        @Override
        public void addContainer(ModContainer container) {
            synchronized (delegate) {
                delegate.addContainer(container);
            }
        }

        @Override
        public void registerPackage(ModCandidate candidate, String pkg) {
            synchronized (delegate) {
                delegate.registerPackage(candidate, pkg);
            }
        }

        @Override
        public Set<ASMDataTable.ASMData> getAll(String annotation) {
            synchronized (delegate) {
                return delegate.getAll(annotation);
            }
        }

        @Override
        public SetMultimap<String, ASMDataTable.ASMData> getAnnotationsFor(ModContainer container) {
            synchronized (delegate) {
                return delegate.getAnnotationsFor(container);
            }
        }

        @Override
        public Set<ModCandidate> getCandidatesFor(String pkg) {
            synchronized (delegate) {
                return delegate.getCandidatesFor(pkg);
            }
        }
    }

    private static final class CandidateResult {
        final ModCandidate candidate;
        final List<ModContainer> mods;

        CandidateResult(ModCandidate candidate, List<ModContainer> mods) {
            this.candidate = candidate;
            this.mods = mods;
        }
    }

    public static List<ModContainer> identifyMods(List<ModCandidate> candidates, ASMDataTable dataTable, List<File> nonModLibs) {
        if (candidates == null || candidates.isEmpty()) {
            return Lists.newArrayList();
        }

        long start = System.nanoTime();
        int cores = Runtime.getRuntime().availableProcessors();
        SynchronizedASMTable syncTable = new SynchronizedASMTable(dataTable);

        List<CandidateResult> results = AsyncModScanner.mapParallel(candidates, candidate -> {
            try {
                List<ModContainer> explored = candidate.explore(syncTable);
                return new CandidateResult(candidate, explored != null ? explored : Lists.newArrayList());
            } catch (Throwable t) {
                LOGGER.error("Async FML scan: failed exploring candidate " + candidate.getModContainer(), t);
                return new CandidateResult(candidate, Lists.newArrayList());
            }
        });

        List<ModContainer> mods = Lists.newArrayList();
        for (CandidateResult res : results) {
            if (res.mods.isEmpty()) {
                if (!res.candidate.isClasspath()) {
                    nonModLibs.add(res.candidate.getModContainer());
                    AsyncModScanner.markNonMod(res.candidate.getModContainer());
                }
            } else {
                mods.addAll(res.mods);
            }
        }

        long ms = (System.nanoTime() - start) / 1_000_000L;
        LOGGER.info("Async FML mod scan: identified {} mods from {} candidates in {} ms (using {} cores)",
            mods.size(), candidates.size(), ms, cores);
        return mods;
    }
}
