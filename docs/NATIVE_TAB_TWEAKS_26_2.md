# Native player list and ping rendering

Lads adapts the complete TabTweaks **1.5.11** renderer from [source revision faa19c704c3c967e1cf0f0355791f9d90d47a1c5](https://codeberg.org/MicrocontrollersDev/TabTweaks/src/commit/faa19c704c3c967e1cf0f0355791f9d90d47a1c5). Its `gradle.properties` declares 1.5.11, matching the [pinned Modrinth artifact qs9sqprR](https://modrinth.com/mod/tabtweaks/version/qs9sqprR) for Minecraft 26.2. License: LGPL-3.0-only.

## One renderer, two Lads sections

TabList controls layout and appearance: player limits, players per column, header/footer visibility, player/NPC head visibility, expanded hats, objectives, three independent text shadows, overall text shadow, scale, both offsets, boss-bar displacement and four ARGB panel colors. Existing Size, X Offset, Y Offset, Background and Text Shadow preferences remain supported; the scalar ranges now cover the complete upstream editor.

PingView controls that same renderer's ping section: numbers or vanilla bars, complete ping hiding, independent text shadow, half-size text, false-latency hiding, six configurable latency colors, plus the existing Static color mode. Latency thresholds are 75, 145, 200, 300 and 400 ms; the reference's negative-latency fallback is retained. False values are at most 1 ms or at least 999 ms.

Each catalog switch controls its section. Both off restores vanilla behavior. A separately installed `tabtweaks` mod keeps ownership; the Lads hooks pass through and the existing external settings page remains available.

## Persistence and source

Preferences live in the existing Lads module/profile JSON. On first native initialization, an existing `config/tabtweaks.json` is read into these options; that upstream file is unchanged. `config/lads-tabtweaks-import.json` records completion so later native preferences are preserved. Module enabled states are retained. Colors retain alpha and can also follow the Lads global color.

The complete modified renderer/module source, original LGPL text, GPL text, source revision and rebuilding/relinking instructions are included under `META-INF/lads-sources/tabtweaks`. The port does not redistribute upstream icon or localization assets.

## Corrections and verification

The reference incorrectly continued drawing numeric ping after invoking vanilla bars when numbers were disabled. The port returns immediately. Its header-shadow hook targeted a Component call used by player names; the port targets the real FormattedCharSequence header call and keeps three independent shadows. Matrix changes now restore in `finally`, and empty boss overlays clear the previous frame's displacement.

Three common tests cover profile precision, invalid scalar limits and ARGB persistence. The opt-in world probe executes transformed vanilla PlayerTabOverlay rendering with isolated mock PlayerInfo records and local default skins: no player records are installed into a connection, and no profile, skin or server network request is made. It checks real columns, player limits, text/geometry colors, shadows, visibility, objectives, scaling, ping thresholds, fake values, hats, boss state and legacy option import. The combined Minecraft 26.2 world run passed **42 checks, 0 failures**, recorded in [`tab-tweaks-checkpoint/world-pass.log`](../artifacts/verification/tab-tweaks-checkpoint/world-pass.log), alongside 226 native checks and 61 GPU checks. The production TabTweaks pin was removed after this result; separately installed upstream copies still keep ownership.
