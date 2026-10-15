# 1.20.1 and Zero SMP QA (harness, 1.8.5)

Two ways to run a real 1.20.1 game in the sandbox, plus one script language to drive it. Everything runs through
`TheLadsLauncher.Verification` (own sandbox, trip-wire on the real `%APPDATA%\.minecraft`), nothing touches `%APPDATA%\.theladsclient`
(Zero SMP's real instance is only read).

```
cd <your worktree>
dotnet build TheLadsLauncher.Verification -c Debug --nologo -v q
$h = "TheLadsLauncher.Verification\bin\Debug\net8.0-windows\TheLadsLauncher.Verification.exe"
```

## 1. Plain Fabric: `--mc1201`

```
& $h --mc1201 . [--script f.txt] [--script-text "wait 40;shot a"] [--remove-mods a,b] [--dir name] [--no-world] [--fresh-world]
                [--no-build|--build] [--core-jar x.jar] [--loader 0.19.5] [--timeout-sec N] [--scenario name]
```

The standard "The Lads Client 1.20.1" profile: Fabric Loader **0.19.5** (the pinned pack's Kotlin, JEI, TLib, Effectual, MRU and OBE need
>= 0.19.5; the launcher profile now uses it too), Java 17, the 50 pinned client mods (jars cached machine-wide, so only the first run
downloads), and the Lads Core built from **this worktree**: `TheLadsCore\v1_20_1` `remapJar` is rebuilt automatically when a Java source
in `v1_20_1\src` or `common\src` is newer than the jar (`--no-build` skips, `--build` forces, `--core-jar` takes a jar). The mixin classes
of the jar are checked before the launch (a missing one would crash Fabric at startup).
It reaches the title screen, photographs it, creates (first run) or opens the superflat peaceful survival QA world
`LadsQA-<dir name>` (cheats on, day, clear weather, no day/weather cycle), photographs it, runs the script, stops the game.
`--dir name` = another game folder (`artifacts\verification\<name>`) for a second concurrent game of the same worktree
(default `1.20.1-title`). `--fresh-world` deletes the folder's QA world first. `--no-world` stops at the title screen (a script still runs there).
`--remove-mods a,b` deletes jars whose file name contains `a` or `b` (case-insensitive) from the sandbox mods, after the pack
installer ran (to test a Lads module replacing a duplicate mod, e.g. `--remove-mods sodium-extra,moreculling`).
The QA pack is the pack as pinned (Faster Iris Shadow Mapper included: its manifest id is `fism`, like its jar).
`LADS_VERIFY_EXCLUDE_MODS=a,b` leaves upstream mods out of a run, to get past a pack defect under diagnosis.
Essential shows an "Account authentication failed" modal over the title screen (offline QA account); harmless.

## 2. Zero SMP (Forge 47.4.10 + Sinytra Connector): `--zero-smp`

```
& $h --zero-smp <worktree>\artifacts\verification\zero [--script f.txt] [--remove-mods a,b] [--no-world] [--fresh-world]
                 [--no-build|--build] [--core-jar x.jar] [--source <instance dir>] [--timeout-sec 900] [--scenario name]
```

The sandbox dir is the launcher data folder; it must be inside `artifacts\verification`. The harness mirrors the owner's instance
(`%APPDATA%\.theladsclient\instances\zero-smp-1.20.1`, read-only: mods, config, defaultconfigs, options.txt and every pack folder; **not**
saves, Distant_Horizons_server_data, screenshots, logs, backups, caches) into `<dir>\instances\zero-smp-1.20.1\minecraft`, replaces
`mods\theladscore.jar` by the worktree's freshly built 1.20.1 Core, deletes `--remove-mods` jars **from that copy only**, forces the QA
options, and starts the game through the launcher's own `ModpackLauncher.PrepareAsync` (Forge installer, libraries and Java come from a
machine-wide cache `The Lads Client Dev\.qa-cache`, seeded once from the owner's launcher, read-only). The pass condition is the Core's
"initialized successfully" line (proves Connector loaded it), the QA probe's title, world and script markers, and a graceful stop.
Needs 7 GB free RAM (6 GB heap). The QA world is `LadsQA-zero-smp` in the copy's own `saves`.

FastQuit (both ways, and every version's `--title`): `LADS_VERIFY_FASTQUIT=run|verify|baseline|crash|cycles` runs the FastQuit probe
**instead of** the auto-world QA (the probe opens its own worlds from the title screen; never both at once). `cycles` = 5x quit then rejoin
the same world, quit then open another, open one while another is loaded; marks checked, every region file read at the end.

## 3. QA rules the harness enforces (every version, not only 1.20.1)

* **Max 6 QA games machine-wide** (owner 2026-10-10; the free-RAM gate is the real limit on this 32 GB PC): the harness claims `The Lads Client Dev\.qa-slot-1..6` (a folder with `owner.txt`, the harness PID;
  stale ones are reaped) and waits for a free slot **and** >= 4 GB free RAM (7 GB Zero SMP), with 25 s between two launches.
  Do not `mkdir` slots yourself; if a wrapper of yours already holds slot N, export `LADS_QA_SLOT=N` (the harness then skips its own claim).
  `LADS_VERIFY_FOCUS=1` runs (need the game in front) are the old exclusive ones and take no slot.
* **Window**: the QA monitor (device name in `The Lads Client Dev\.qa-monitor`, e.g. `\\.\DISPLAY2`; primary when absent), a 2x2 tile of the screen by slot (1 top left, 2 top right, 3 bottom left; 960x540 each), game opened at the tile's
  client size (944x501, so GUI scale 2 is not capped), never activated, never minimized, **High** priority. `LADS_VERIFY_NO_RESIZE=1` keeps the game's own size.
* **Options forced** into `options.txt` on every launch: 120 FPS, vsync off, render/simulation distance 4, GUI scale 2, master volume 0,
  pauseOnLostFocus false, not fullscreen (`LADS_VERIFY_FULLSCREEN=1`: started as the launcher's default "Fullscreen on launch" does,
  `--fullscreen` + `fullscreen:true`, for borderless/capture tests: the game then covers the primary monitor); Essential's Discord activity and Lads DiscordRPC off; `-Dfabric.noGui=true` (needs Fabric
  Loader >= 0.19.5, older loaders are refused: they ignore it and would open the crash window on the owner's screen).

* **Trip-wire**: the real `%APPDATA%\.minecraft` listing must not change during a run. The owner plays on this PC, so a change is only a printed
  warning; `LADS_VERIFY_TRIPWIRE=strict` makes it a failure (use it while the owner is away). Games always run with sandbox folders.
* **Caches**: Minecraft assets/libraries, Java runtimes and the 1.20.1 pack jars are cached in `The Lads Client Dev\.qa-cache` for every
  worktree (downloaded once, under file locks); `LADS_QA_SHARED_ROOT` moves it.

## 4. Probe script (`QaProbe1201`, 1.20.1 Core, inert without the harness flags)

`--script <file>`, `LADS_VERIFY_SCRIPT=<file>`, or `--script-text "step;step"` (`LADS_VERIFY_SCRIPT_TEXT`). One step per line, `#` starts a
comment, double quotes group a token. It starts when the world is READY (or at the title screen with `--no-world`), one step per tick at most,
and the run ends when it finishes (log: `Lads qa script END: N passed, M failed`; M > 0 fails the run).

| Step | Does |
|---|---|
| `wait 40` / `wait 2s` / `wait 500ms` | 40 ticks (20 per second), 2 s, 500 ms |
| `cmd /give @s torch` | chat command as the player (cheats are on); the leading `/` is optional |
| `chat hello` | plain chat message |
| `shot name` | waits 3 ticks, writes `screenshots\name.png` (the main render target: last finished frame, GUI included); name = `[A-Za-z0-9._-]` |
| `screen X` | `none` `title` `pause` `options` `video` `controls` `lads` (Lads settings) `hud` (HUD editor) `inventory` `chat` |
| `key F5` | presses a key binding by its GLFW name (`key.keyboard.<name>`); `F1` toggles hideGui, `F3` the debug screen, `F11` fullscreen (Lads makes it borderless), `escape` closes the screen or pauses |
| `hold forward on` / `hold sneak off` | holds a movement/action key (`forward back left right jump sneak sprint attack use tab`; `hold tab on` keeps the player list open for a shot, `hold tab off` releases it) |
| `click left` | one left/right/middle mouse click on the key mappings (attack, use) |
| `hotbar 3` | selects slot 3 |
| `look <yaw> <pitch>` | turns the player (client side) |
| `module "ParticleBudget" off` | `on` / `off` / `toggle` a Lads module by name (case-insensitive) |
| `option "Fullbright" "Gamma" 0.5` | sets a module option from JSON (`true`, `0.5`, `"text"`; a bare word is taken as a string) |
| `packs vanilla,file/a.zip` | selects exactly these resource pack ids (comma separated, quote ids with spaces) without a reload and logs `Lads qa packs: [...]`; `packs` alone only logs the selected ones. The Pack HUD module follows the change |
| `dump state` / `dump modules [filter]` / `dump all` | logs `Lads qa dump ...` lines: screen, FPS, window, FOV, position, health, gamemode, effects, time; every module's enabled flag, support kind and options |
| `log text` | logs `Lads qa log: text` |
| `ui open fps` (or `colors`, `mods`, `actions`), `ui module <name>`, `ui search <text>`, `ui click <control id>`, `ui ids [prefix]`, `ui key <glfw code>`, `ui type <text>`, `ui scroll <n>`, `ui scale 1` (or `2`) | drives the open Lads settings screen through its real input methods (`ui ids` logs the control ids on screen: `category:HUD`, `card:<module>`, `option:<name>`, `reset`, ...); `ui hud click <id>` and `ui hud ids` do the same in the HUD editor; `ui scale` switches the GUI scale (1 = the roomy layouts, 2 = the compact ones in the 944x501 QA window) |
| `ui mouse <x> <y>`, `ui hover <slot>` | puts the pointer at GUI pixel x, y, or on a container slot (inventory open), so tooltips and hover states render for a shot |
| `press <label>` | presses the visible button with that label (case-insensitive, e.g. `press "Switch Versions"` on the title, `press "Lads Client 26.3"`, `press "Relaunch Now"`) on the open screen, through its own `onPress`; fails the step when there is none |
| `ui pose` | the next frame of a Lads settings screen logs `Lads qa pose ...` lines: the translation of the GuiGraphics pose before the vanilla HUD, after it, and at the screen (all 0 unless an overlay leaked a pose push) |
| `ui silence <ms>` | SignalLoss sees the server silent for that long (0 = the real connection); the toast shows 5 s after the module became eligible |
| `kbcap [skin id,...]` | holds the Kill Banner at the frames the 26.3 capture uses and writes the banner crops to `screenshots\kb172\kb-<skin>-v<variant>-k<kills>-f<frame>.png`, to compare with the 26.3 frames of the same name (`LADS_VERIFY_CAPTURE_KILLBANNER=1`) |
| `quit` | stops the game |

Example (`artifacts\1.8.5\<lane>\test.txt`):

```
cmd /give @s torch 16
cmd /effect give @s minecraft:speed 30 2
hotbar 1
module "ParticleBudget" off
dump modules Particle
screen lads
wait 20
shot lads-settings
screen none
cmd /time set night
wait 10
shot night
```

Where things land: screenshots `artifacts\verification\<dir>\screenshots\` (Zero SMP: `<sandbox>\instances\zero-smp-1.20.1\minecraft\screenshots`) and copied into the
evidence folder `artifacts\verification-1.2.3\<scenario>\<time>-game-<dir>\screenshots\` together with `summary.txt` (command, JVM flags,
result, key log lines), `production-smoke.log` (Fabric) or `latest.log` (Zero SMP) and the script. Exit code 0 = PASS. Read the PNGs.
Probe code for lanes that need more: `TheLadsCore\v1_20_1\...\feature\QaProbe1201.java` (`QaProbe1201.active()`, `windowActive()`:
QA windows are never focused, so Lads input rules that need `isWindowActive()` can use `QaProbe1201.windowActive()`).

Version switch (1.9.0): every harness start writes `lads_versions.json` into the game folder as the launcher does (`VersionSwitch.Prepare`; the
Zero SMP start through `ModpackLauncher.PrepareAsync`, current = "Zero SMP"). `--mc1201 . --no-world --script-text "wait 40;press \"Switch Versions\";wait 20;shot versions;press \"Lads Client 26.3\";wait 20;shot confirm;press \"Relaunch Now\""`
photographs the in-game list and confirmation and leaves `lads_next_version.json` in the game folder; 26.x: `LADS_VERIFY_AUTO_WORLD=1 LADS_VERIFY_CAPTURE_TITLE=1
LADS_VERIFY_VERSION_SWITCH=1`; 1.8.9: `LADS_VERIFY_AUTO_WORLD=1 LADS_VERIFY_189_ONLY=title`. Zero SMP "Auto-join server": `LADS_VERIFY_JOIN=127.0.0.1:1`
(local addresses only) starts the pack with the quick-play argument the launcher builds; the game must log `Connecting to 127.0.0.1, 1` and the probe
photographs the connect/failed screen (`qa-join`; needs a script step, e.g. `--script-text "wait 20"`, to be active).

Environment variables behind the options: `LADS_VERIFY_SCRIPT`, `LADS_VERIFY_SCRIPT_TEXT`, `LADS_VERIFY_REMOVE_MODS`, `LADS_VERIFY_CORE_JAR`,
`LADS_VERIFY_BUILD`, `LADS_VERIFY_NO_BUILD`, `LADS_VERIFY_FRESH_WORLD`, `LADS_VERIFY_LOADER`, `LADS_VERIFY_TIMEOUT_SEC`, `LADS_VERIFY_SCENARIO`,
`LADS_VERIFY_NO_RESIZE`, `LADS_QA_SLOT`, `LADS_QA_SHARED_ROOT` (default `$HOME\Desktop\The Lads Client Dev`).
