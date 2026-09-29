# Native Clumps for integrated worlds

Lads adapts the Minecraft 26.2 Fabric behavior of [Clumps 26.2.1](https://modrinth.com/mod/clumps/version/dEMopoOJ), Modrinth project `Wnxd13zP`, version `dEMopoOJ`. The [author's 26.2 source](https://github.com/jaredlll08/Clumps/tree/6c4d4e4bee4c0d9f7cff5de802508e27914aadca) matches the inspected pinned orb mixin. Its source property is `mod_version=26.2`; `.1` identifies the published build. The MIT license, attribution, revision, complete modified source and rebuild instructions are included under `META-INF/lads-sources/clumps`.

## Behavior and controls

The upstream 26.2.1 artifact declares no user configuration options. The Lads Clumps switch controls the complete engine; it does not invent merge-radius or XP-multiplier preferences.

| Reference behavior | Native implementation |
|---|---|
| Merge mixed XP values | Actual `ExperienceOrb.scanForMerges`, eligibility and merge hooks retain vanilla search radius and combine different values without vanilla's group-ID restriction. |
| Award-time merging | Actual `ExperienceOrb.award` uses the reference's 1×1×1 search volume and inserts each original award denomination into the first eligible orb. |
| Immediate collection | A real server player's touch clears the XP pickup delay and collects all original units once. Removed entities cannot award XP again. |
| Mending and events | The original denomination/count map is retained. Each original unit runs repair separately, using vanilla 26.2's equivalent Fabric repair calculation. Relocated value/repair callbacks and pickup veto are supported. |
| Lifetime | Merging retains the younger orb's age; award-time merging resets age to zero. Vanilla movement, attraction, dimensions and despawn rules remain active. |
| World saves | The upstream `clumpedMap` NBT format is read and written, preserving original denominations and values larger than vanilla's short `Value` field. |
| Live master switch | Disabling stops new native merges. Already merged entities consume one original unit per vanilla pickup delay until exhausted, preserving XP instead of multiplying or dropping it. |

Already stacked vanilla orbs preserve their complete count when adopted. The aggregate vanilla count is normalized to one because its value is already summed. Merges exceeding the positive integer XP limit remain separate; malformed extension data falls back to the vanilla saved value without failing entity loading.

The native engine owns **singleplayer and hosted LAN worlds only**. XP entities on a remote multiplayer server are authoritative server state, so that server must supply its own Clumps behavior. A separately installed `clumps` mod disables all native ownership and retains its existing configuration path. The relocated API is a Lads extension point, not binary compatibility with mods compiled against upstream `com.blamejared.clumps` classes.

## Verification boundary

`NativeClumpsProbe` performs 21 assertions through transformed Minecraft classes on the actual integrated server: mixed-value scanning, award-time merging, immediate pickup, duplicate prevention, raw vanilla-stack adoption, persisted values, malformed data, disable-time consumption, actual enchanted-item Mending, events, veto and entity cleanup.

It runs only with `-Dthelads.verifyIntegrations=true`, the exact `artifacts/verification/26.2-title` game directory and repository marker, the existing save folder `saves/Client QA 26_2` whose actual `level.dat` name is `Client QA 26.2`, and one local server player. It uses an empty nearby test volume, restores the player's equipment/XP/score/delay and module preferences, and removes only its own spawned XP entities. No remote connection or profile service is used.

The actual integrated-server run passed **21 checks, 0 failures** in the combined [`native-final-checkpoint/world-pass.log`](../artifacts/verification/native-final-checkpoint/world-pass.log). The preserved Core JAR has SHA-256 `52D16AD48F6776945F790288D79496EA2B05AFC66E4A78F7462A79D2C0395DB5`. The first attempted run stopped before mutation because its guard expected the wrong display name; the verified run uses the exact folder/name pair read from the actual `level.dat`, as documented above. The production external Clumps pin was removed after the successful server run. This establishes integrated-world behavior and cleanup, not remote-server XP control.
