# lads-boost (prototype)

Java agent that speeds up Fabric client startup (target: MC 26.3, fabric-loader 0.19.5, JDK 25).

* **A. Post-mixin class cache.** Rewrites `KnotClassDelegate.getPostMixinClassByteArray(String,boolean)`
  (renamed to `lads$orig$...`, wrapper under the old name). A hit returns the bytes stored by the previous run;
  a miss runs the original and stores the non-null result.
* **B. Preload.** Records the order classes pass through the hook. Next launch, when the first class starting with
  `lads.boost.preloadTrigger` is seen, daemon thread(s) call `Class.forName(name, false, knotLoader)` for the classes
  recorded after the trigger last time (no static initializers run).

Everything in a hook is exception-safe: any Throwable -> one `[LadsBoost]` line, boost disables for the rest of the run,
the original path is used.

## Run

```
-javaagent:C:\path\lads-boost.jar -Dlads.boost.dir=C:\path\boostdir
```
ASM 9.x (`org.objectweb.asm`) must be on the app class path (Fabric Loader brings it). Not bundled.
Build: `powershell -File build.ps1 [-Jdk <jdk21+>] [-Asm <asm-9.x.jar>]` -> `build/lads-boost.jar`.
Self-test: `powershell -File selftest/run.ps1` (plain JVM, fake KnotClassDelegate, plus patch+verify of the real class).

## Flags (system properties)

| flag | default | meaning |
|---|---|---|
| `lads.boost.cache` | true | use/write the class cache |
| `lads.boost.dir` | `<gameDir>/.lads-boost` | data dir: `cache.dat`, `cache.idx`, `order.txt`, `debug.log` |
| `lads.boost.gameDir` | `fabric.gameDir`, else `--gameDir` in `sun.java.command`, else cwd | game dir |
| `lads.boost.mods` | `<gameDir>/mods` | mods dir (walked recursively); `fabric.modsFolder` and `fabric.addMods` are also hashed |
| `lads.boost.salt` | empty | extra key input; change to force a rebuild |
| `lads.boost.prepare` | eager | `eager`: the first transformable class after transformers are initialised always runs the original path (Mixin prepares configs at the normal moment), then cache. `lazy`: serve from cache at once |
| `lads.boost.flushAfterSec` | 90 | daemon timer writes the cache/order once after N s (shutdown hook also writes; game is often killed) |
| `lads.boost.preload` | true | record order + preload thread |
| `lads.boost.preloadThreads` | 1 | preload threads |
| `lads.boost.preloadTrigger` | `net.minecraft.client.main.Main` | class-name prefix that starts preload |
| `lads.boost.preloadCachedOnly` | true | only preload names present in the validated cache |
| `lads.boost.debug` | false | per-class HIT/MISS lines in `debug.log` |

## Key (SHA-256, any change -> cache and order ignored and rebuilt)

java.version + java.vm.version; every `java.class.path` entry (path,size,mtime; this includes the fabric-loader jar and
the game jar when on the class path); `fabric.gameJarPath[.client]` if set; every file under the mods dir(s)
(path,size,mtime); files directly in `<gameDir>/config` ending `mixins.properties` or containing `mixin`;
`lads.boost.salt`. Cache file: index has key, data length and CRC32 of the data; truncated/corrupt -> rebuilt.
Files are written to `*.tmp` and atomically renamed.

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
* Memory: new bytes are kept until exit (tens of MB). Cache is read fully at startup.

## Measured notes (cyrax-pc, MC 26.3)
- Cache key hashes mod config file contents (mods rewrite mtimes every launch) and carries `agent=2`.
- Classes whose bytes reference `/generated/` (MixinExtras LocalRefImpl) are not cached (`skippedGenerated`).
- `prepare=lazy` fails (`MixinTargetAlreadyLoadedException`); use eager.
- Preload with 2+ threads and no cache deadlocks (MixinTransformer monitor vs Knot class locks); hence `preloadCachedOnly=true` default.
- Original System.err is captured, since MC replaces it with log4j.
