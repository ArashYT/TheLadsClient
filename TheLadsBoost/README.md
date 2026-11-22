# lads-boost

Java agent that speeds up client startup: Fabric Knot (MC 26.3, 1.20.1) and 1.8.9 Forge on RetroFuturaBootstrap
(`net.minecraft.launchwrapper.LaunchClassLoader`). The launcher adds it (`Services/StartupBoost.cs`) after
`EnsureSecurityJvmArguments`, only if `boost/lads-boost.jar` matches `StartupBoost.JarSha256`; user `-javaagent` stays blocked.

* **A. Post-mixin class cache.** Rewrites `KnotClassDelegate.getPostMixinClassByteArray(String,boolean)`
  (renamed to `lads$orig$...`, wrapper under the old name). A hit returns the bytes stored by the previous run;
  a miss runs the original and stores the non-null result.
  On RFB: `runTransformers` is wrapped the same way (hit returns cached bytes; miss marks the class) and the
  `runRfbTransformers` call in `findClass` goes through `lads$rfb`, which skips it on a hit and stores its result on a miss,
  so the cached bytes are the final bytes after both transformer chains.
* **B. Preload.** Records the order classes pass through the hook. Next launch, when the first class starting with
  `lads.boost.preloadTrigger` is seen, daemon thread(s) call `Class.forName(name, false, knotLoader)` for the classes
  recorded after the trigger last time (no static initializers run). Waits for the eager first class (max 20 s) and, with
  `preloadSafeSupers`, only preloads classes whose supertypes are JDK classes, already loaded, or themselves safe.
* **C. Late Mixin configs.** Mods may add Mixin configs while the game starts (ReplayMod on 1.20.1: `mixins.core.replaymod.json`,
  "mod (unknown)"). Mixin prepares a new config on its next transform and throws `MixinTargetAlreadyLoadedException` if a
  target is already loaded. So a hit is served only while `Mixins.getUnvisitedCount()` is 0 (else the original path runs
  and prepares it; counted as `mixinPending`), the order file records the config count (`MIXINCFG n`), and preload waits
  (max 20 s, `mixinWaitMs`) until an original transform finished with that many configs, or the count held settled for
  200 ms. Without Mixin on the class path the gate is off. Real Mixin removes a config from `Mixins.getConfigs()` once it
  is selected, so on a real game `MIXINCFG` is 0 and only the settle check matters (1.20.1 warm, 2026-11-22:
  `mixinPending=766 hits=14475`, no `MixinTargetAlreadyLoadedException`).

Everything in a hook is exception-safe: any Throwable -> one `[LadsBoost]` line, boost disables for the rest of the run,
the original path is used.

## Kill switches

`-Dlads.boost=false`; a file named `off` in the boost dir; launcher setting `StartupBoost=false`; env `LADS_BOOST=0`.

## Run

```
-javaagent:C:\path\lads-boost.jar -Dlads.boost.dir=C:\path\boostdir
```
ASM 9.x (`org.objectweb.asm`) must be on the app class path (Fabric Loader brings it). Not bundled.
Build: `powershell -File build.ps1 [-Jdk <jdk21+>] [-Asm <asm-9.x.jar>]` -> `build/lads-boost.jar` (class files for Java 17: 1.20.1 runs on Java 17).
Self-test: `powershell -File selftest/run.ps1` (plain JVM, fake KnotClassDelegate, plus patch+verify of the real class).

## Flags (system properties)

| flag | default | meaning |
|---|---|---|
| `lads.boost` | true | `false` = agent does nothing |
| `lads.boost.cache` | true | use/write the class cache |
| `lads.boost.dir` | `<gameDir>/.lads-boost` | data dir: `cache.idx`, `cache-<millis>.dat`, `order.txt`, `pending`, `debug.log` |
| `lads.boost.gameDir` | `fabric.gameDir`, else `--gameDir` in `sun.java.command`, else cwd | game dir |
| `lads.boost.mods` | `<gameDir>/mods` | mods dir (walked recursively); `fabric.modsFolder` and `fabric.addMods` are also hashed |
| `lads.boost.salt` | empty | extra key input; change to force a rebuild |
| `lads.boost.prepare` | eager | `eager`: the first transformable class after transformers are initialised always runs the original path (Mixin prepares configs at the normal moment), then cache. `lazy`: serve from cache at once |
| `lads.boost.flushAfterSec` | 90 | daemon timer writes the cache/order once after N s (shutdown hook also writes; game is often killed) |
| `lads.boost.preload` | true | record order + preload thread |
| `lads.boost.preloadThreads` | min(4, cores/2) | preload threads |
| `lads.boost.preloadTrigger` | `net.minecraft.client.main.Main` | class-name prefix that starts preload |
| `lads.boost.preloadCachedOnly` | true | only preload names present in the validated cache |
| `lads.boost.preloadSafeSupers` | true | only preload classes whose supertypes are safe (see B) |
| `lads.boost.debug` | false | per-class HIT/MISS lines in `debug.log` |

## Key (SHA-256, any change -> cache and order ignored and rebuilt)

java.version + java.vm.version; every `java.class.path` entry (path,size,mtime; this includes the fabric-loader jar and
the game jar when on the class path); `fabric.gameJarPath[.client]` if set; every file under the mods dir(s)
(path,size,mtime); files directly in `<gameDir>/config` ending `mixins.properties` or containing `mixin`;
`lads.boost.salt`; agent version (`agent=4`).

## Files (format LBC2)

`cache.idx`: magic, key, name of the data file, its length, then per entry name/offset/length/CRC32. The data file
(`cache-<millis>.dat`) is memory-mapped read-only, not read into the heap. Each entry's CRC is checked when it is served;
a bad entry goes the original path and is rewritten at the next flush. A truncated data file or broken index -> rebuild.
Each flush writes a new data file + index (`*.tmp`, atomic rename); older data files are deleted (or at the next start,
since Windows can't delete a mapped file). `pending` is created at start and removed at the first flush: if it is still
there at the next start (crash/kill before the flush), the cache and order are dropped once.

## Rules the wrapper follows

* Only used when `transformInitialized` is true (same condition as Fabric's transform). Earlier loads: original, not stored.
* Null results (class not found) are never stored. Raw (non-transformable) and transformed classes are both stored.
* Order is recorded only when no valid order file existed (otherwise preload-thread timing would pollute it).
  Delete `order.txt` to re-record.

## Known risks

* A mixin plugin (`IMixinConfigPlugin`) or anything else that decides on runtime state other than the key
  (system properties, options, other mods' presence via code, time, GPU, server state) can give different bytes
  than the cached ones. Cached bytes are replayed blindly. Mixin side effects of `transformClassBytes`
  (generated/synthetic classes, `preApply/postApply`, audit/export, `MixinTargetAlreadyLoaded` bookkeeping) do not
  happen for cached classes. `lazy` mode defers Mixin's config preparation until the first cache miss.
* Classes defined by other loaders or by `Unsafe/defineClass` bypass the hook (unchanged, unseen).
* Preload runs concurrently with the main thread; it only loads (no init), but loading order of superclasses/
  interfaces changes and Mixin transform runs on another thread.
* Memory: new bytes are kept until the flush (tens of MB). The cache itself is mapped, not loaded.
* RFB: `rfb$parent` child-delegated classes never reach `runRfbTransformers` and are not stored.

## Measured notes (cyrax-pc, MC 26.3)
- Cache key hashes mod config file contents (mods rewrite mtimes every launch) and carries `agent=2`.
- Classes whose bytes reference `/generated/` (MixinExtras LocalRefImpl) are not cached (`skippedGenerated`).
- `prepare=lazy` fails (`MixinTargetAlreadyLoadedException`); use eager.
- Preload with 2+ threads and no cache deadlocks (MixinTransformer monitor vs Knot class locks); hence `preloadCachedOnly=true` default.
- Original System.err is captured, since MC replaces it with log4j.

## JDK 25 AOT cache (tested 2026-11-22, not shipped)
- 26.3 on cyrax, title s, 4 timed runs each: base 41.7/47.8/48.0/48.3, `-XX:AOTCache` 38.7/41.2/42.2/44.2, Boost warm
  36.3/36.8/38.6/40.6. AOT is ~5 s faster than base, Boost warm ~9 s; AOT+Boost was not better than Boost alone.
- Cost: the training run (`-XX:AOTMode=record`) reached the title after 263.8 s (6x a normal start), `create` took 238.9 s
  and wrote 242 MB. Recording with the agent attached hung ~10 min at exit and left an empty config (`create` then fails:
  "Must be a valid AOT configuration generated by the current JVM").
- 1.8.9 (RFB) cannot use it: "Archived non-system classes are disabled because the java.system.class.loader property is
  specified" and `create` fails on `RfbSystemClassLoader`.
- With the AppCDS hang behind 048c2aa9f, AOT stays out; Boost alone is faster.
