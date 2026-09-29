# Native shulker previews — Minecraft 26.2

This document retains the verified 17:39 checkpoint below. The dated parity expansion at the end describes the subsequent tooltip, inventory and persistence implementation and its separate verification status.

ShulkerBoxUtils displays the first occupied inventory slot as an item icon above a placed shulker box. Open its Lads module page to adjust Distance (8–64 blocks), Icon Size, Height, Animate and Remember Observed Contents. Disabling the module clears extracted icon geometry immediately; hidden HUD, distance, vanilla block-entity visibility and loaded-chunk rules are respected.

The renderer prepares the actual Minecraft item model during extraction and stores it in that shulker's render state. Submission consumes the stable snapshot with the world's lighting and depth behavior. It does not read the server world on the rendering thread, force chunks to load, or draw unknown contents through walls. Bobbing uses elapsed game time with render interpolation and respects zero screen-effect strength.

## Where contents come from

- **Placed boxes:** the actual `DataComponents.CONTAINER` of the placed item, captured through Minecraft's component application method.
- **Opened multiplayer boxes:** a recent click on the exact loaded shulker is associated with the server-opened shulker menu. A full inventory packet establishes the slot snapshot. Subsequent server slot packets change only their acknowledged slot. Client click predictions cannot alter the preview.
- **Singleplayer:** loaded containers are refreshed on the integrated server's thread at most once per second per visible box, with at most eight outstanding requests. The result is copied back to the client thread. Unopened loot tables are never materialized just to obtain a preview.

Minecraft does not normally send closed shulker inventories to multiplayer clients. A box that has not been placed or opened this session has no preview. Remembered contents are last-observed data: unseen hopper/server changes can make a closed box's icon stale until a new observation. Opening by another viewer invalidates that observation when Minecraft sends its open event. Custom servers that open a shulker menu unrelated to the recently clicked block are outside this vanilla association contract.

The cache contains at most 256 block-entity identities and is cleared on world changes. Block removal/unload invalidates that identity. It is not persisted to disk. Turning Remember Observed Contents off removes a multiplayer observation after its menu closes; singleplayer can still obtain a fresh authoritative snapshot.

## Scope and verification

The bounded first-item world preview is recreated inside Lads. The reference's additional inventory badges, fill indicators, tooltip layouts and saved cache are not part of this implementation. No upstream Shulker Box Utils JAR or `fweigel-util-lib` dependency is required. Reference behavior was inspected from the supplied Shulker Box Utils 1.3.0 by fweigel-dev (metadata license CC-BY-4.0); Lads uses original source.

Required all-target `gradlew.bat build deploy -x test` passed. All **24 shulker runtime checks passed at 17:39:05 on 10 September 2026**, part of the final **108-check** run with 33 upstream JARs plus Core. The probe uses a temporary client-only shulker fixture and synthetic server inventory packets through real transformed handlers; it restores the prior air block, menu and options before returning. This checks the component/render pipeline without claiming a live multiplayer container test. See [`108-checks.log`](../artifacts/verification/native-mods-26.2/108-checks.log), shulker summary at line 589, and [NATIVE_MODULES_26_2.md](NATIVE_MODULES_26_2.md) for the complete evidence and boundaries. Tested Core SHA-256: `24FAD43B579667E47D7FBADDBF80E97E17ED51FC47BF5FFD4F53CD381FE4D09F`.

Optimised Block Entities is deferred: the supplied engine replaces vanilla shulker rendering, which bypasses these integration hooks. Adding that renderer requires a dedicated compatibility implementation and runtime check.

## 10 September 2026 — observed singleplayer behavior

The preceding tested build also passed a real integrated-server visual check in the isolated QA world. A purple shulker at `(0, 100, -3)` received seven diamonds in `container.0` through the server command. Without opening the box, its diamond icon was visible at 17:42:07. Replacing the slot with air removed the icon at 17:42:25, within 1.7 seconds of the command. Captures: [diamond present](../artifacts/development-preview/shulker-diamond.png), [empty box](../artifacts/development-preview/shulker-empty.png). This confirms the live server-thread refresh path, separately from synthetic packet checks.

## 10 September 2026 — expanded reference parity

Inspection of the supplied 1.3.0 classes confirms the following behavior. Original Lads source now implements each row; the earlier evidence above remains attached to its exact build.

| Supplied reference behavior | Expanded Lads implementation |
| --- | --- |
| Inventory badge | Half-size first stored item over the shulker item. A recursion guard prevents nested components from recursively drawing badges. |
| World icon | The existing per-frame world icon has an independent World Icon toggle. |
| Display mode | First Item or Single Item Type, shared by the inventory badge and world icon. Uniformity compares item types, matching the supplied inventory implementation; individual item components remain intact. |
| Fill indicator | Free Slot Bar measures unoccupied slots out of 27, not total items. Empty is a full green bar; full is a minimum-width red bar. |
| Contents preview | A 9×3 native item grid retains sparse slot positions, uses real item models and stack-count decorations, and suppresses the duplicate vanilla contents list only while its own grid is present. The Lads panel also shows occupied slots and total item count. |
| Independent switches | Inventory Badge, World Icon, Free Slot Bar and Contents Preview are independent; Item Counts is an additional Lads option. Module disable restores vanilla behavior. Hidden container components/hidden tooltips do not expose a badge, bar or grid. |
| Persistent world observations | First observed item and uniformity persist, with item components/count preserved. Scopes include world/server, dimension and player identity; filenames use a SHA-256 scope key. Options remain in Lads configuration rather than the reference's separate per-world settings file. |

The live identity cache remains bounded to 256 block entities. The persistent history holds at most 1,024 observations and 2 MiB per scope; entries expire after seven days and oversized item records are omitted. Disk reads/writes use a bounded background executor, atomic file replacement, a ten-second flush interval, world-transition flushes and a short shutdown drain. Files live under the instance's `config/theladscore/shulker-observations`. Invalid, expired or wrong-scope data degrades to an unknown preview. Fresh observations and block changes take precedence over an asynchronous cache load.

Persist Observed Contents requires Remember Observed Contents. Unloading a chunk releases its live block entity, while the last-observed history can survive reload/reconnect. A block change, another viewer opening the box, known empty contents or an explicit forget invalidates history. A stored observation is **last-known data**, not a claim that an unseen closed multiplayer container is unchanged. Singleplayer still uses fresh server-thread snapshots. No unopened loot table is generated for previews.

The reference's separate configuration screen and hotkey are replaced by its integrated Lads module page in RSHIFT. The reference's external utility library and compiled classes are not used. The artwork/layout is Lads-native; original Minecraft item models render the actual contents.

Required all-target build/deploy passed after the expansion. The runtime suite includes 28 world-icon checks and 41 additional tooltip/inventory/cache checks; all passed in both [native world checkpoints](NATIVE_RUNTIME_CHECKPOINT_26_2.md). The actual inventory test found an overload mismatch, now fixed by hooking the shared private item-rendering path. Cache tests use their own temporary QA directory and cover real component round trips, world/dimension/account separation, expiry, malformed/oversized input, bounds and atomic replacement. GUI tests inspect actual item/text/geometry extraction and option restoration.

Root also observed a real integrated-server container without opening it: placing seven diamonds in slot 0 produced the diamond icon at 17:42:07; replacing that slot with air removed the icon at 17:42:25 after the next refresh. The server commands affected only the isolated Creative QA world. Captures: `artifacts/development-preview/shulker-diamond.png` and `shulker-empty.png`. This separately verifies the live singleplayer snapshot path; it remains distinct from multiplayer observation caching.
