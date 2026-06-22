# Lads Client — backlog

## Native mod recreations (from "Mods To Recreate In Lads")
Workflow per mod: study behavior -> write precise AI_TASK.txt -> run RunAITask.bat
(7b-fast, background) -> register mixin in theladscore.mixins.json -> compileJava ->
leave UNCOMMITTED for in-game test -> commit when verified.

| Mod | Difficulty | Status |
|---|---|---|
| disablenarrator | Easy | ✅ DRAFTED (GameNarratorMixin), compiles, needs in-game test |
| SignalLoss | Easy | ✅ DRAFTED (ConnectionSignalLossMixin), compiles, needs in-game test |
| decentscreenshot | SKIP | redundant — client already has GalleryScreen |
| farblockentityrendering | Easy-Med | TODO |
| shulkerboxutils | Medium | TODO |
| retromod | Medium (study behavior) | TODO |
| obe | Medium (study behavior) | TODO |
| Kerria | Medium (study behavior) | TODO |
| SignalLoss | Easy | TODO |
| animatium | HARD (animations) | needs frontier model + heavy in-game work |
| Resourcify | HARD (pack browser UI) | needs frontier model + heavy in-game work |
| ModernAdvancementsScreen | HARD (overlaps existing AdvancementsReloaded) | reconsider whether needed |

**Reality:** the local 7B/14B can do the Easy/Medium rows one at a time with a precise
spec (each needs in-game verification since most are rendering/mixin features). The HARD
rows are multi-day, in-game-only, and beyond a local model — they need a frontier model
or manual work.

## Other
- F3 text-shadow/background render hook: DRAFTED uncommitted (DebugScreenOverlayMixin), needs in-game test.
- MC 26.2 migration: BLOCKED upstream; weekly auto-recheck scheduled (lads-26-2-migration-recheck).
