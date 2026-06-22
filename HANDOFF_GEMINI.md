# HANDOFF — Lads Client mod-recreation project (for Gemini as Project Manager)

You are the **MANAGER** of a mod-recreation project for **"The Lads Client"** (Fabric, Minecraft
**26.1.2**, Java **25**, **Mojang/official mappings** — NOT Yarn). A local AI worker (Ollama) is
your laborer. Your job: drive the worker and the decompiler to recreate desktop mods into the
TheLadsCore mod, then **REVIEW, FIX, COMPILE, REGISTER, and VERIFY** each one until it actually
runs in-game without crashing. The worker and decompilers produce broken code — you are expected to
fix their mistakes, not just accept them. The user is not in a rush; **prioritize correctness**.

**The user's hard rule: the local AI must do as much of the labor as possible. You (Gemini) are the
manager who reviews, fixes, and guarantees correctness. Do NOT hand-write whole mods from scratch
when the worker or the decompiler can produce the bulk.**

---

## 0. ENVIRONMENT / GROUND TRUTH

- **Working dir:** `C:\Users\Arash\Desktop\Lads Client`
- **Mod source root:** `TheLadsCore\src\main\java\com\thelads\core\`
- **Mods to recreate live in:** `C:\Users\Arash\Desktop\Mods To Recreate In Lads\` (extracted folders + `.jar`)
- **Shell:** Windows. PowerShell for native cmds; a Git-Bash-style `bash` is also available (POSIX).
- **Git branch:** `feat/integrate-32-mods` (commit here; do not touch `main`).
- **MC version is 26.1.2** (`TheLadsCore/gradle.properties` -> `minecraft_version=26.1.2`).
- **Mapped MC jar (use for `javap` signature checks):**
  `C:\Users\Arash\.gradle\caches\fabric-loom\26.1.2\minecraft-merged.jar`

### Compile / build / deploy commands
- **Compile only (fast triage):**
  `cd TheLadsCore && ./gradlew.bat compileJava --console=plain`  → look for `BUILD SUCCESSFUL`.
- **Build + deploy to the live game + all CF instances:**
  `.\Build-LadsClient.ps1` (compiles, re-embeds the 75-entry access widener, copies
  `TheLadsCore-1.41.0.jar` into `C:\The Lads Client\mods\` and the CurseForge instances).
- **The live jar `C:\The Lads Client\mods\TheLadsCore-1.41.0.jar` is LOCKED while MC runs — the user
  must close MC before you build.**

### In-game verification
- You **cannot launch the game yourself** — the launcher exits immediately when started
  programmatically and needs the user's Microsoft-auth flow. **Ask the USER to launch The Lads
  Client normally and reach the main menu**, then read the log:
  `C:\The Lads Client\logs\latest.log`.
- **Healthy boot =** exactly **ONE** `Reloading ResourceManager`, reaches
  `Game took N seconds to start`, and **no error/exception lines that reference your mod's mixin
  package**. Two resource reloads = something threw during reload (rollback) = investigate.
- Many `Error loading class: ...` WARN lines are normal soft mixin-target probes for *other* mods —
  ignore unless the class is one of YOURS.

---

## 1. CURRENT STATE (already done — DO NOT redo or clobber)

Committed on `feat/integrate-32-mods`:
- `1816d06e` **Baseline** — recovered the previously-uncommitted integration work (the "4 working
  mods": obe2621010, shulkerboxutils130, disablenarrator/GameNarratorMixin,
  SignalLoss/ConnectionSignalLossMixin) + `features/auto/*` + cleanup of a corrupted git index.
- `776db22d` **farblockentityrendering** — done, compiles, registered, **verified booting clean**.
- `fbdd1fe4` **ModernAdvancementsScreen** — full 61-file source-port 26.2→26.1.2, compiles,
  registered, deployed, **verified booting clean** ("Loaded Modern Advancements", no mixin errors).
  - Final functional eyeball still worth doing: press **L** in a world to open the new advancement
    screen.

Decided/closed:
- **Kerria → SKIPPED.** Its mixins optimize the pre-1.21.5 direct-OpenGL texture-upload path
  (`NativeImage.upload`, `GlStateManager._texSubImage2D`, `SpriteContents$AnimatedTexture`) which
  Mojang **deleted** in 26.x (replaced by the `blaze3d` GpuTexture/CommandEncoder abstraction). The
  worker was tried (per user request) and produced no-op stubs that failed to compile → auto-
  quarantined to `recreate_quarantine/kerria1301211fabric`. Do not revive unless the author ships a
  native 26.x build.
- **retromod → SKIP** (NeoForge loader-compat shim, not portable as Fabric mixins).
- **decentscreenshot → SKIP** (redundant with built-in GalleryScreen).

---

## 2. YOUR REMAINING WORK (in priority order)

### 2A. animatium — DO THIS. It's the main remaining job.
- Folder: `C:\Users\Arash\Desktop\Mods To Recreate In Lads\animatium-3.2+26.1.2-fabric`
- **`depends: minecraft >=26.1 <=26.1.2` → it targets OUR EXACT version.** This is the big deal:
  **expect very few or zero API deltas**, unlike MAS (which was 26.2). A decompile→relocate→compile
  pass may nearly work out of the box.
- **Scale:** ~175 top-level classes, **~130 mixins** (organized under `mixins/v1/...`: entity,
  gui, rendering, general, accessor). It's a "legacy/old-MC-feel" visuals mod (old sky/fog/clouds,
  old inventory rendering, view bobbing, sneaking animation, item animations, disable modern combat
  particles/sounds, etc.). Lots of small mixins, not deep logic.
- **Dependencies to verify before relocation:**
  - `mixinextras` `>=0.5.0` (the mixins use `@WrapOperation`, `@ModifyExpressionValue`, etc.).
    MixinExtras ships transitively with modern Fabric Loader, so it should already be on the
    compile classpath — confirm by compiling one mixin that uses it.
  - Config uses a config lib + ModMenu (`config/category/*`, `ModMenuIntegration`). YACL
    (`yet_another_config_lib_v3`) is already loaded in the instance at runtime; confirm it's a
    compile dependency or descope the config UI (keep the config DATA class, drop the ModMenu screen
    if it won't compile).
- **CRITICAL — its `animatium.mixins.json` has `"injectors": { "defaultRequire": 1 }`.** That means
  any mixin whose target is missing will HARD-CRASH the game. When you register these into
  `theladscore.mixins.json`, the project's config uses `defaultRequire: 0` (safe no-op) — good — but
  do **not** copy a `require=1`. Keep the project default. Still, verify the riskier targets exist
  with `javap` (see MAS notes below for the technique).

### 2B. Resourcify — almost certainly DESCOPE; confirm with the user first.
- A standalone **`Resourcify (26.1-fabric)-1.8.3.jar` is ALREADY deployed and loading** in
  `C:\The Lads Client\mods\` (it appears in the boot log's resource list). It already works.
- The folder you were given is the **26.2** build (`depends: minecraft >=26.2 <26.3`) which won't
  even load on 26.1.2.
- **Recommendation: do not recreate Resourcify** — it's redundant duplication of a large pack-
  browser UI mod that already functions standalone. Ask the user to confirm descope (they may want
  it *shaded* into TheLadsCore anyway; only then port it).

---

## 3. HOW TO USE THE LOCAL AI WORKER (maximize this)

There are **two** local-AI tools. Use the right one per mod size.

### 3.1 The harness: `recreate_local.py` (best for SMALL mods, few mixins)
- **What it does:** talks to Ollama directly, forces a strict `=== FILE: X.java ===/=== END ===`
  output format, writes each file into `mixin/auto/<id>/` with the correct `package` line,
  **compile-gates**, and **quarantines** failures to `recreate_quarantine/<id>/` (+ a
  `<id>.compile.log`). It only feeds the worker the mod's **mixin class NAMES + description** as
  hints — not source.
- **Run it:**
  ```powershell
  $env:PYTHONUTF8 = '1'
  # ensure ollama is up first:  Invoke-WebRequest http://127.0.0.1:11434/api/tags  (expect 200)
  #   if down:  Start-Process ollama 'serve'
  python recreate_local.py 'animatium-3.2+26.1.2-fabric'
  ```
- **Model:** default `qwen-coder-14b-stable:latest`. Override with `$env:RECREATE_MODEL='...'`.
  Available: `qwen-coder-14b-stable`, `qwen-coder-14b-fast`, `qwen2.5-coder:14b`,
  `codestral:latest`, etc. (list via the `/api/tags` endpoint).
- **Known worker failure modes (you WILL fix these):** invents non-existent class names
  (cannot-find-symbol on imports/`@Mixin`), slips back to Yarn names despite being told Mojang,
  leaves empty no-op method bodies, fabricates imports. For a 130-mixin mod the harness alone will
  NOT succeed — use it only as a first-pass idea generator / for the simplest accessor mixins.

### 3.2 The decompile→relocate→fix pipeline (best for BIG mods like animatium — THIS is how MAS was done)
This is the highest-leverage path and still "the tools do the bulk; you fix." Steps:

1. **Decompile the original jar with Vineflower** (cleaner control flow than CFR — CFR mangles
   complex methods into `** GOTO lbl-1000` garbage):
   ```bash
   VF="/c/Users/Arash/.gradle/caches/modules-2/files-2.1/org.vineflower/vineflower/1.11.1/77767cdbc76d116c6d5e1565ec387e531085b9f4/vineflower-1.11.1.jar"
   rm -rf /tmp/anim && mkdir /tmp/anim
   java -jar "$VF" "/c/Users/Arash/Desktop/Mods To Recreate In Lads/animatium-3.2+26.1.2-fabric.jar" /tmp/anim
   ```
   (CFR fallback for single broken files: `TheLadsCore/cfr.jar`.)
   **The mod jars decompile to NAMED Mojang mappings** — same family as our project — so this is
   tractable, not intermediary `class_xxxx` soup.

2. **Relocate into the project with a package rewrite** (bash sed loop). For animatium the original
   base package is `org.visuals.legacy.animatium`. Put non-mixin classes under
   `com.thelads.core.features.auto.animatium` and mixins under
   `com.thelads.core.mixin.auto.animatium3226112fabric` (the `recreate_local.py` `mid` convention =
   the folder name lowercased with non-alphanumerics stripped). Rewrite the **mixin** sub-package
   FIRST (more specific), then the general package:
   ```bash
   SRC="/tmp/anim/org/visuals/legacy/animatium"
   FEAT=".../features/auto/animatium" ; MIX=".../mixin/auto/animatium3226112fabric"
   # for each .java:  if path contains /mixins/  -> MIX (flatten? NO: animatium mixins use
   #   sub-packages like v1/entity/... — PRESERVE the subtree under MIX), else -> FEAT
   sed -e 's/org\.visuals\.legacy\.animatium\.mixins/com.thelads.core.mixin.auto.animatium3226112fabric/g' \
       -e 's/org\.visuals\.legacy\.animatium/com.thelads.core.features.auto.animatium/g'
   ```
   NOTE: unlike MAS (flat mixin package), **animatium's mixins live in nested packages**
   (`v1/entity/...`, `v1/gui/...`). Preserve that subtree and register them with their dotted
   sub-path in `theladscore.mixins.json` (e.g. `auto.animatium3226112fabric.v1.entity.MixinHumanoidModel`).

3. **Compile-triage loop:** `./gradlew.bat compileJava --console=plain 2>&1 | grep error:`
   Group by file, fix in batches. Because animatium is the SAME version, most errors will be
   decompiler artifacts, not API deltas:
   - **jspecify `@NonNull` type-annotation artifacts** ("not expected here"): strip them —
     `find <dirs> -name '*.java' -print0 | xargs -0 sed -i -e 's/@NonNull //g' -e '/^\s*@NonNull\s*$/d' -e '/^import org\.jspecify\.annotations\.NonNull;$/d'`
   - **mixin self-casts**: `(Foo)this` → `(Foo)(Object)this`.
   - **raw generics**: `(List)x` → `(List<String>)x`.
   - **Vineflower-mangled record `switch`** (rare): rewrite by hand from the record accessors.
   - If a target method/class genuinely differs, verify with
     `javap -cp <26.1.2 merged jar> <class>` and adapt (this is how the MAS `Gui`↔`Minecraft`
     `setScreen` delta was found).

4. **Where the local AI fits in this pipeline (maximize per the user):** the decompiler does the
   bulk translation; for the remaining mechanical compile fixes you may **delegate batches to the
   worker** via a focused prompt (give it the exact error + the offending method and ask for a
   corrected method), then review. Aider also works against Ollama here (see memory
   `local_ai_setup.md`); OpenCode does NOT (tool-calling unreliable on local models). Reserve your
   own hand-edits for the tricky few. Always state when you fall back to hand-fixing and why.

---

## 4. REGISTER + WIRE (every mod, after it compiles)

1. **Register every mixin** in `TheLadsCore/src/main/resources/theladscore.mixins.json`:
   - common/server mixins → the top-level `"mixins"` array;
   - client mixins → the `"client"` array.
   - Use the dotted path relative to base package `com.thelads.core.mixin`, e.g.
     `auto.animatium3226112fabric.v1.gui.MixinInGameHud`. **Unregistered = inactive.**
   - The project file already has `"injectors": { "defaultRequire": 0 }` and
     `"overwrites": { "requireAnnotations": true }` — so `@Overwrite` needs `@author`/`@reason`.
2. **Wire entrypoints** — shaded mods must NOT be Fabric entrypoints in `fabric.mod.json`. Instead
   call them from TheLadsCore's existing aggregators (this is the established pattern):
   - main `ModInitializer.onInitialize()` →
     `TheLadsCore/src/main/java/com/thelads/core/TheLadsCore.java` (add
     `new <pkg>.Animatium...().onInitialize();` before the closing brace of `onInitialize()`).
   - client `ClientModInitializer.onInitializeClient()` →
     `.../client/TheLadsCoreClient.java` (add the `...onInitializeClient();` call).
3. **Copy assets** (`assets/<modid>/...` lang + textures) into
   `TheLadsCore/src/main/resources/assets/<modid>/` or the screen shows raw translation keys.

### CRITICAL PITFALL (causes black screen / boot rollback)
Any **duck-interface or non-mixin class that is referenced directly** by other code must **NOT** live
in package `com.thelads.core.mixin.*` (it triggers `IllegalClassLoadError` → resource-reload failure
→ black screen). Put such classes under `com.thelads.core.features.*`. (This is why all non-mixin
ported classes go under `features/auto/<mod>`.)

---

## 5. COMMIT STRATEGY (decided with the user)
- Baseline already committed. **Commit each mod separately** once it **compiles + is registered**.
- Leave a mod **uncommitted only if it still needs the in-game boot test**; commit after the user
  confirms a clean boot (or commit on compile+register and note "in-game test pending" in the
  message, as was done for MAS — your call, but be explicit).
- Line-ending `LF→CRLF` warnings from git on these files are harmless.
- Commit message footer:  `Co-Authored-By: <your model> <noreply@...>` style is fine; keep messages
  factual about what was ported and which deltas were fixed.

---

## 6. PER-MOD CHECKLIST (run this loop for animatium, then any others)
1. Decompile with Vineflower (§3.2) — or `recreate_local.py` for tiny mods (§3.1).
2. Relocate with package rewrite; non-mixin → `features/auto/<id>`, mixins → `mixin/auto/<id>` (keep
   sub-packages).
3. `./gradlew.bat compileJava` → fix in batches until `BUILD SUCCESSFUL`. Use `javap` on the 26.1.2
   merged jar to resolve any real API mismatch. Delegate mechanical fix batches to the local worker
   where practical.
4. Register all mixins in `theladscore.mixins.json`; verify each mixin's `@Mixin` target +
   `method=` exists in 26.1.2 (`javap`) so it actually applies.
5. Wire entrypoints into `TheLadsCore.java` / `TheLadsCoreClient.java`; copy assets.
6. `./gradlew.bat compileJava` again (full) → `BUILD SUCCESSFUL`.
7. Commit.
8. `.\Build-LadsClient.ps1` (ask user to close MC first) → ask user to launch → read
   `C:\The Lads Client\logs\latest.log` → confirm ONE resource reload, "Game took N seconds to
   start", no errors mentioning your mixin package. Fix anything that fails; re-deploy.

---

## 7. MEMORY (read these — persistent project notes for this repo)
`C:\Users\Arash\.claude\projects\C--Users-Arash-Desktop-Lads-Client\memory\` — `MEMORY.md` is the
index. Especially:
- `theladscore_integration_pitfalls.md` — the 5 recurring crash classes (JIJ, entrypoints, access
  wideners, mixin-package interfaces, duplicate standalones).
- `local_ai_setup.md` — Ollama/Aider details; OpenCode unreliable.
- `theladscore_uncommitted_baseline.md` — git history context.
- `launcher_headless_quirk.md` — why you can't auto-launch; build is automatable, user must launch.
- `feedback_maximize_local_ai.md` — the user wants the worker to do the bulk; you manage/fix.

## 8. ONE-LINE SUMMARY FOR THE USER
"farble + ModernAdvancementsScreen are done and verified booting clean. Kerria/retromod/
decentscreenshot are skipped (with reasons). Next: **animatium** (targets our exact 26.1.2, ~175
classes/130 mixins — decompile with Vineflower, relocate, fix the few artifacts, register, wire,
verify). **Resourcify** already works as a standalone jar — recommend descope unless you want it
shaded."
