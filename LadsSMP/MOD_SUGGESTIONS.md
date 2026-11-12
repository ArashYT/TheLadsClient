# The Lads SMP Reborn — Mod Suggestions Report (Minecraft 26.3, Fabric 0.19.5)

> [!IMPORTANT]
> **Target Environment**: Minecraft `26.3` (Java 25) | Fabric Loader `0.19.5` | Fabric API `0.163.0+26.3`  
> **Audit Cross-Check**: Every suggestion below was verified against [`MOD_INVENTORY.md`](file:///C:/Users/Administrator/Documents/.aihub-wt/aihub-tb58b5dd1-agy/LadsSMP/MOD_INVENTORY.md) (256 reference jars / 225 active jars), [`MOD_DECISIONS.md`](file:///C:/Users/Administrator/Documents/.aihub-wt/aihub-tb58b5dd1-agy/LadsSMP/MOD_DECISIONS.md), [`MOD_OVERLAPS.md`](file:///C:/Users/Administrator/Documents/.aihub-wt/aihub-tb58b5dd1-agy/LadsSMP/MOD_OVERLAPS.md), and the `AGENTS.md` Anti-Ban & SMP Safety rules:
> - **No Overlap**: None of these mods duplicate our existing 225 active mods or the 13 features consolidated into `ladssmp-1.0.0.jar`.
> - **Zero New Dependency Bloat**: Almost every recommendation uses library jars **already installed** in `lads-smp-26.3-dev` (`puzzleslib`, `forgeconfigapiport`, `cloth-config`, `yet_another_config_lib_v3`, `collective`, `player_animation_library`, `supermartijn642corelib`). Where a small new library is needed (`addonslib`, `cicada`, `configurable`), its exact `26.3` jar is listed.
> - **SMP & Anti-Ban Safe**: No griefing items, no dupes, no OP gear progression skips, and no cheaty client automation/ESP/freecam.

---

## Part 1: 12 Fun Mods (Content, Mobs, Building, Adventure & Social)

### 1. Emotecraft
- **Links**: [Modrinth](https://modrinth.com/mod/emotecraft) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/emotecraft)
- **Exact 26.3 File & Version**: `emotecraft-for-MC26.3-3.5.0-b.build.179.jar` (Version `3.5.0-b.build.179`, MC `26.3`)
- **Side**: Both (`Client` required, `Server` optional — installing on both syncs emotes across all SMP players)
- **Dependencies**: `player_animation_library` (**Already Installed**: `PlayerAnimationLibMerged-1.2.8+mc.26.3.jar`), `fabric-api`
- **Why It Fits**: Essential social fun for a friends SMP alongside `Simple Voice Chat` and `Camerapture`. Players can perform built-in or custom `.emotecraft` / Bedrock animation emotes during group photos and hangout sessions.
- **Overlap Risk**: **None**. Reuses the `player_animation_library` dependency already loaded for `combat_roll`.

### 2. PatPat
- **Links**: [Modrinth](https://modrinth.com/mod/patpat) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/patpat)
- **Exact 26.3 File & Version**: `PatPat-1.3.2+26.3+fabric.jar` (Version `1.3.2+26.3+fabric`, MC `26.3`)
- **Side**: Both (`Client` required, `Server` optional — installing on server broadcasts pat animations, sounds, and hearts to nearby players)
- **Dependencies**: `fabric-api` (**Already Installed**; optional config via `cloth-config` / `yet_another_config_lib_v3`)
- **Why It Fits**: Lets players shift-right-click to pet any mob (`Alex's Mobs`, `Naturalist`, `Lovely Snails`, `Friends&Foes`) or even other players with a cute squish animation and heart particles.
- **Overlap Risk**: **None**. Purely social/visual interaction.

### 3. Straw Statues
- **Links**: [Modrinth](https://modrinth.com/mod/straw-statues) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/straw-statues)
- **Exact 26.3 File & Version**: `strawstatues-v26.3.0-mc26.3.x+fabric.jar` (Version `26.3.0`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `puzzleslib`, `forgeconfigapiport`, `fabric-api` (**All 3 Already Installed**; integrates automatically with `statuemenus` embedded in `armorstatues-v26.3.0-mc26.3.x+fabric.jar`)
- **Why It Fits**: Written by Fuzs as the companion mod to `Armor Statues` (which we already ship). Allows crafting poseable player-skin statues of the Lads for town halls, monuments, and shops using the exact same GUI as `Armor Statues`.
- **Overlap Risk**: **None**. Directly extends `Armor Statues` (`armorstatues`) without duplicating armor stands.

### 4. Macaw's Biomes O' Plenty
- **Links**: [Modrinth](https://modrinth.com/mod/macaws-biomes-o-plenty) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/macaws-biomes-o-plenty)
- **Exact 26.3 File & Version**: `mcwbiomesoplenty-fabric-26.3-1.6.jar` (Version `26.3-1.6`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `biomesoplenty`, `terrablender`, `glitchcore` (**All Already Installed**), plus `addonslib-fabric-26.3-1.0.jar` ([AddonsLib on Modrinth](https://modrinth.com/mod/addonslib), Version `26.3-1.0`)
- **Why It Fits**: We already ship `Biomes O' Plenty` (`26.3.0.0.15`) and **13 Macaw's mods** (`mcwbridges`, `mcwdoors`, `mcwfences`, `mcwfurnitures`, `mcwroofs`, `mcwstairs`, `mcwtrpdoors`, `mcwwindows`, etc.), but currently players cannot craft Macaw furniture/roofs/bridges out of Biomes O' Plenty wood types. This bridges the gap seamlessly.
- **Overlap Risk**: **None**. Pure compatibility bridge between two core mod suites already in the pack.

### 5. Rustic Delight
- **Links**: [Modrinth](https://modrinth.com/mod/rustic-delight) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/rustic-delight)
- **Exact 26.3 File & Version**: `rusticdelight-fabric-26.3-1.7.0.jar` (Version `1.7.0`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `farmersdelight`, `cloth-config`, `fabric-api` (**All 3 Already Installed**)
- **Why It Fits**: Since `regs-more-foods` was removed during the audit (invalid NeoForge MCreator jar), `Farmer's Delight` is our primary cooking system. `Rustic Delight` is a high-quality, native Fabric expansion adding Coffee, Bell Peppers, Cotton, Cooking Oil, Calzones, and placeable Pancakes—all compatible with `Cooking for Blockheads` and `Storage Delight`.
- **Overlap Risk**: **None**. Complements `farmersdelight` and replaces the void left by removing `regs_more_foods`.

### 6. Display Delight (Fabric)
- **Links**: [Modrinth](https://modrinth.com/mod/display-delight-fabric) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/display-delight-fabric)
- **Exact 26.3 File & Version**: `display-delight-1.8.5-mc26.3.jar` (Version `1.8.5-mc26.3`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `farmersdelight`, `fabric-api` (**All Already Installed**)
- **Why It Fits**: We already ship `Items Displayed` (`items_displayed`) for placing ingots/tools on tables, and `Farmer's Delight` for cooking. `Display Delight` allows placing every `Farmer's Delight` meal and bowl as a 3D decorative food plate on tables, taverns, and restaurants.
- **Overlap Risk**: **None**. `Items Displayed` handles vanilla items/tools/gems, while `Display Delight` specifically handles `Farmer's Delight` plated meals.

### 7. Friends&Foes — Flowery Mooblooms & Beekeeper Hut
- **Links**:
  - [Flowery Mooblooms (Modrinth)](https://modrinth.com/mod/friends-and-foes-flowery-mooblooms-fabric) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/friends-and-foes-flowery-mooblooms-fabric)
  - [Beekeeper Hut (Modrinth)](https://modrinth.com/mod/friends-and-foes-beekeeper-hut-fabric) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/friends-and-foes-beekeeper-hut-fabric)
- **Exact 26.3 Files & Versions**:
  - `flowerymooblooms-fabric-3.0.3+mc26.3.jar` (Version `fabric-3.0.3+mc26.3`, MC `26.3`)
  - `beekeeperhut-fabric-3.0.4+mc26.3.jar` (Version `fabric-3.0.4+mc26.3`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `friendsandfoes`, `fabric-api` (**Both Already Installed**: `friendsandfoes-fabric-5.0.2+mc26.3.jar`)
- **Why It Fits**: Official lightweight add-ons by Faboslav (author of `Friends&Foes`, `Variants&Ventures`, and `Structurify`, all in our pack) adding flower-specific Moobloom variants and a cozy Beekeeper Hut for the Beekeeper villager.
- **Overlap Risk**: **Low**. Uses existing `Friends&Foes` systems; `Structurify` can manage Beekeeper Hut spacing if desired.

### 8. Reconnectible Chains
- **Links**: [Modrinth](https://modrinth.com/mod/reconnectible-chains) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/reconnectible-chains)
- **Exact 26.3 File & Version**: `connectiblechains-2.5.0+26.3-fabric.jar` (Version `2.5.0+26.3-fabric`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `yet_another_config_lib_v3`, `fabric-api` (**Both Already Installed**)
- **Why It Fits**: Lets builders hang catenary chains between fences, walls, and posts using vanilla Iron Chains—amazing for suspension bridges, docks, tavern chandeliers, and medieval streetlamps alongside `Beautify` and `Macaw's Bridges/Fences`.
- **Overlap Risk**: **None**. `Beautify` adds hanging rope/trellis blocks, whereas `Reconnectible Chains` creates dynamic point-to-point hanging chain physics between fence posts.

### 9. Blåhaj — Replushed
- **Links**: [Modrinth](https://modrinth.com/mod/blaha-replushed) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/blahaj-replushed)
- **Exact 26.3 File & Version**: `blahaj-replushed-4.0.0+26.3.jar` (Version `4.0.0+26.3`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: Adds craftable, cuddle-held, and placeable Blåhaj shark plushies (and bread/whale plushies) that fit the cozy aesthetic of the pack (`femboymod`, `beautify`, `farmhouse_decorations`). Players can hold them in both hands or place them on beds/shelves.
- **Overlap Risk**: **None**. Purely decorative/collectible plushies.

### 10. Spyglass Astronomy
- **Links**: [Modrinth](https://modrinth.com/mod/spyglass-astronomy) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/spyglass-astronomy)
- **Exact 26.3 File & Version**: `spyglass_astronomy-1.0.27-mc26.3.jar` (Version `1.0.27-mc26.3`, MC `26.3`)
- **Side**: Client-Only (`Client` required, exclude from dedicated server)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: Replaces the static night sky with procedural stars, orbiting planets, and shooting stars, and lets players draw and name constellations when looking through a vanilla Spyglass.
- **Overlap Risk**: **None**. Works in the Overworld night sky (`better_end_sky` only touches The End dimension).

### 11. Kaffee's Dual Ride
- **Links**: [Modrinth](https://modrinth.com/mod/kaffees_dual_ride) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/kaffees-dual-ride)
- **Exact 26.3 File & Version**: `Kaffee's_Dual_Ride-1.2.0.jar` (Version `1.2.0`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: Allows two players to ride on a single horse, donkey, or mule together (just like camels). Super fun for co-op exploration with friends early- and mid-game.
- **Overlap Risk**: **None**.

### 12. Chalk (Fabric)
- **Links**: [Modrinth](https://modrinth.com/mod/chalk) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/chalk-fabric)
- **Exact 26.3 File & Version**: `chalk-3.2.0+26.3.jar` (Version `3.2.0+26.3`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: With `Alex's Caves` and `Dungeons and Taverns` adding sprawling cave systems and 184-room mansions, `Chalk` lets adventurers draw directional arrows, symbols, and colored markings on dungeon walls (including glowing markings with Glow Ink) so nobody gets lost.
- **Overlap Risk**: **None**.

---

## Part 2: 12 Quality-of-Life (QoL) Mods

### 1. Explorer's Compass
- **Links**: [Modrinth](https://modrinth.com/mod/explorers-compass) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/explorers-compass)
- **Exact 26.3 File & Version**: `ExplorersCompass-26.3-2.5.2-fabric.jar` (Version `26.3-2.5.2-fabric`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: We already ship `Nature's Compass` (`NaturesCompass-26.3-2.5.1-fabric.jar` by the same author, Chaosyr) for locating biomes, and we have **18 structure mods** (`Dungeons and Taverns`, `Better Archeology`, `Moog's` suite, `Towns and Towers`, etc.). `Explorer's Compass` is the natural counterpart for locating specific structures without needing `/locate`.
- **Overlap Risk**: **None**. `Nature's Compass` locates biomes; `Explorer's Compass` locates structures.

### 2. Easy Magic
- **Links**: [Modrinth](https://modrinth.com/mod/easy-magic) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/easy-magic)
- **Exact 26.3 File & Version**: `easymagic-v26.3.1-mc26.3.x+fabric.jar` (Version `26.3.1`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `puzzleslib`, `forgeconfigapiport`, `fabric-api` (**All 3 Already Installed**)
- **Why It Fits**: We already ship Fuzs's `Easy Anvils` (`easyanvils-v26.3.1`) and `Visual Workbench` (`visualworkbench-v26.3.0`). `Easy Magic` completes the Fuzs workstation trio: items and lapis stay inside the Enchanting Table, float visually around it, and allow cheap re-rolling without breaking bookshelves.
- **Overlap Risk**: **Replaces `stickyenchantinglapis-26.3.0-1.6.jar`**. If you add `Easy Magic`, remove `stickyenchantinglapis-26.3.0-1.6.jar` since `Easy Magic` includes persistent lapis plus 3D item rendering and rerolls.

### 3. Easy Shulker Boxes
- **Links**: [Modrinth](https://modrinth.com/mod/easy-shulker-boxes) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/easy-shulker-boxes)
- **Exact 26.3 File & Version**: `easyshulkerboxes-v26.3.4-mc26.3.x+fabric.jar` (Version `26.3.4`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `puzzleslib`, `forgeconfigapiport`, `fabric-api` (**All 3 Already Installed**; embeds `iteminteractions`)
- **Why It Fits**: Turns Shulker Boxes and Ender Chests into vanilla-Bundle-style interactive containers right inside your inventory—hover and scroll/right-click to insert or extract items without placing the box on the ground, with colorful visual contents tooltips.
- **Overlap Risk**: **None**. `Smart Backpacks` handles dedicated backpack items; `Easy Shulker Boxes` enhances vanilla Shulker Boxes and Bundles. (Do not pair with `ShulkerBoxTooltip` as `Easy Shulker Boxes` already provides full visual tooltips.)

### 4. Ping Wheel
- **Links**: [Modrinth](https://modrinth.com/mod/ping-wheel) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ping-wheel)
- **Exact 26.3 File & Version**: `Ping-Wheel-1.12.2-fabric-26.3.jar` (Version `1.12.2`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: Lets players middle-click (or press a keybind) to ping a block, chest, ore vein, or mob in 3D space with distance markers for teammates—indispensable when exploring `Alex's Caves` or fighting bosses over `Simple Voice Chat`.
- **Overlap Risk**: **None**. Safe raycast ping from the player's own line of sight (no wallhack/ESP).

### 5. Trade Cycling
- **Links**: [Modrinth](https://modrinth.com/mod/trade-cycling) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/trade-cycling)
- **Exact 26.3 File & Version**: `trade-cycling-fabric-1.0.23+26.3.jar` (Version `fabric-1.0.23+26.3`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: Adds a "Cycle Trades" button (and keybind) to the Villager trading screen for un-traded level-1 villagers so players don't have to break and replace Lecterns 200 times.
- **Overlap Risk**: **None**. Complements `Trading Post` (`tradingpost-v26.3.0`), which aggregates nearby villagers into one UI while `Trade Cycling` refreshes an individual novice villager's offers.

### 6. Leave My Bars Alone
- **Links**: [Modrinth](https://modrinth.com/mod/leave-my-bars-alone) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/leave-my-bars-alone)
- **Exact 26.3 File & Version**: `leavemybarsalone-v26.3.0-mc26.3.x+fabric.jar` (Version `26.3.0`, MC `26.3`)
- **Side**: Client-Only (`Client` required, exclude from dedicated server)
- **Dependencies**: `puzzleslib`, `forgeconfigapiport`, `fabric-api` (**All 3 Already Installed**)
- **Why It Fits**: Keeps your Hunger bar and XP bar visible while riding horses or modded mounts (`Alex's Mobs`, `Naturalist`) and lets you still trigger a horse jump without hiding your XP bar.
- **Overlap Risk**: **None**. Checks cleanly alongside `Animated Hearts` and `Immersive Hotbar`.

### 7. Horse Expert
- **Links**: [Modrinth](https://modrinth.com/mod/horse-expert) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/horse-expert)
- **Exact 26.3 File & Version**: `horseexpert-v26.3.1-mc26.3.x+fabric.jar` (Version `26.3.1`, MC `26.3`)
- **Side**: Both (`Client & Server`)
- **Dependencies**: `puzzleslib`, `forgeconfigapiport`, `fabric-api` (**All 3 Already Installed**)
- **Why It Fits**: Crafting a Monocle (or equipping it in `Trinkets Updated`, which we already ship) displays exact Speed, Jump Height, and Health stats when looking at horses, donkeys, mules, and llamas—making horse breeding rewarding without always plastering F3 debug numbers on screen.
- **Overlap Risk**: **None**. Integrates with `trinkets_updated` (`trinkets-4.2.0+26.3.jar`).

### 8. FastQuit
- **Links**: [Modrinth](https://modrinth.com/mod/fastquit) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fastquit)
- **Exact 26.3 File & Version**: `fastquit-3.1.5+mc26.2.jar` (Version `3.1.5+mc26.2`, tagged & verified for MC `26.2` & `26.3` on Modrinth)
- **Side**: Client-Only (`Client` required, exclude from dedicated server)
- **Dependencies**: `cloth-config`, `fabric-api` (**Both Already Installed**)
- **Why It Fits**: With 225 mods and large worldgen data packs, saving a singleplayer test world on exit takes several seconds. `FastQuit` returns you to the Title Screen immediately while the integrated server finishes saving safely in the background.
- **Overlap Risk**: **None**.

### 9. ClickThrough Plus
- **Links**: [Modrinth](https://modrinth.com/mod/clickthrough-plus) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/clickthrough-plus)
- **Exact 26.3 File & Version**: `clickthrough-plus-3.6.5+26.3-pre-3-fabric.jar` (Version `3.6.5+26.3-pre-3-fabric`, tagged & verified for MC `26.3` release on Modrinth)
- **Side**: Client-Only (`Client` required, exclude from dedicated server)
- **Dependencies**: `fabric-api` (**Already Installed**)
- **Why It Fits**: Lets you right-click through Signs, Item Frames, and Banners attached to Chests, Barrels, and `Storage Delight` cabinets to open the container behind them (Shift-right-click still interacts with the sign/frame). Note: Sends a normal interaction packet on the visible block directly behind the sign—safe on our SMP (if bundled into the global Lads Client, gate behind `HypixelSafetyManager` on external servers).
- **Overlap Risk**: **None**. `Cut Through` (`cutthrough-v26.3.0`) only handles *left-clicking* (swinging weapons) through tall grass, whereas `ClickThrough Plus` handles *right-clicking* through signs/item frames onto chests. Also complements `Fast Item Frames` (`fastitemframes-v26.3.0`).

### 10. Followers Teleport Too
- **Links**: [Modrinth](https://modrinth.com/mod/followers-teleport-too) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/followers-teleport-too)
- **Exact 26.3 File & Version**: `followersteleporttoo-26.3.0-3.2.jar` (Version `26.3.0-3.2-fabric+forge+neo`, MC `26.3`)
- **Side**: Server-Side / Both (`Client` optional, `Server` required)
- **Dependencies**: `collective` (**Already Installed**: `collective-26.3.0-8.53.jar`)
- **Why It Fits**: Because we standardized on `Waystones` (`waystones-fabric-26.3-26.3.0.1.jar`) for long-distance travel across unloaded chunks, tamed dogs, cats, parrots, and modded followers normally get left behind in unloaded chunks when a player warps. This mod teleports non-sitting followers with you when using Waystones or `/tp`.
- **Overlap Risk**: **None**. Uses the `collective` library already in the pack.

### 11. Healing Campfire
- **Links**: [Modrinth](https://modrinth.com/mod/healing-campfire) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/healing-campfire)
- **Exact 26.3 File & Version**: `healingcampfire-26.3.0-7.1.jar` (Version `26.3.0-7.1-fabric+forge+neo`, MC `26.3`)
- **Side**: Server-Side / Both (`Client` optional, `Server` required)
- **Dependencies**: `collective` (**Already Installed**: `collective-26.3.0-8.53.jar`)
- **Why It Fits**: Gives lit Campfires and Soul Campfires a cozy passive Regeneration aura for nearby players and pets—pairs wonderfully with `SimplyTents` (`simplytents-3.5-26.3.fabric.jar`) and `Snowy Tents` when camping during long expeditions.
- **Overlap Risk**: **None**.

### 12. Neruina — Ticking Entity Fixer
- **Links**: [Modrinth](https://modrinth.com/mod/neruina) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/neruina)
- **Exact 26.3 File & Version**: `neruina-3.3.5+26.3-fabric.jar` (Version `3.3.5`, MC `26.3`)
- **Side**: Both (`Server` required for dedicated server protection; `Client` optional for singleplayer)
- **Dependencies**: `configurable-3.5.3+26.3-fabric.jar` ([Configurable on Modrinth](https://modrinth.com/mod/configurable), Version `3.5.3`), `fabric-api`
- **Why It Fits**: Essential server-stability QoL for a 225-mod pack on a fresh MC version (`26.3`). If any modded mob, block entity, or item entity throws an uncaught exception during `tick()`, `Neruina` catches it, freezes/kills only the offending entity, logs the exact stack trace + coordinates in chat for admins, and prevents the entire SMP server from crashing into a boot-loop.
- **Overlap Risk**: **None**.

---

## Summary Master Table (24 Verified 26.3 Mods)

| # | Category | Mod Name | Exact 26.3 Jar Filename | Version | Side | Required Dependencies | Overlap Risk |
| :---: | :---: | :--- | :--- | :---: | :---: | :--- | :--- |
| 1 | **Fun** | [Emotecraft](https://modrinth.com/mod/emotecraft) | `emotecraft-for-MC26.3-3.5.0-b.build.179.jar` | `3.5.0-b.build.179` | Both | `player_animation_library` *(installed)* | None |
| 2 | **Fun** | [PatPat](https://modrinth.com/mod/patpat) | `PatPat-1.3.2+26.3+fabric.jar` | `1.3.2+26.3+fabric` | Both | `fabric-api` *(installed)* | None |
| 3 | **Fun** | [Straw Statues](https://modrinth.com/mod/straw-statues) | `strawstatues-v26.3.0-mc26.3.x+fabric.jar` | `26.3.0` | Both | `puzzleslib`, `forgeconfigapiport`, `statuemenus` *(installed)* | None (Extends `armorstatues`) |
| 4 | **Fun** | [Macaw's Biomes O' Plenty](https://modrinth.com/mod/macaws-biomes-o-plenty) | `mcwbiomesoplenty-fabric-26.3-1.6.jar` | `26.3-1.6` | Both | `biomesoplenty` *(installed)* + `addonslib-fabric-26.3-1.0.jar` | None (Bridges BoP + Macaw's) |
| 5 | **Fun** | [Rustic Delight](https://modrinth.com/mod/rustic-delight) | `rusticdelight-fabric-26.3-1.7.0.jar` | `1.7.0` | Both | `farmersdelight`, `cloth-config` *(installed)* | None (Replaces removed `regs_more_foods`) |
| 6 | **Fun** | [Display Delight (Fabric)](https://modrinth.com/mod/display-delight-fabric) | `display-delight-1.8.5-mc26.3.jar` | `1.8.5-mc26.3` | Both | `farmersdelight` *(installed)* | None (3D plates for FD meals) |
| 7 | **Fun** | [Friends&Foes: Flowery Mooblooms](https://modrinth.com/mod/friends-and-foes-flowery-mooblooms-fabric) & [Beekeeper Hut](https://modrinth.com/mod/friends-and-foes-beekeeper-hut-fabric) | `flowerymooblooms-fabric-3.0.3+mc26.3.jar` + `beekeeperhut-fabric-3.0.4+mc26.3.jar` | `3.0.3` / `3.0.4` | Both | `friendsandfoes` *(installed)* | Low (Official F&F add-ons) |
| 8 | **Fun** | [Reconnectible Chains](https://modrinth.com/mod/reconnectible-chains) | `connectiblechains-2.5.0+26.3-fabric.jar` | `2.5.0+26.3-fabric` | Both | `yet_another_config_lib_v3` *(installed)* | None |
| 9 | **Fun** | [Blåhaj — Replushed](https://modrinth.com/mod/blaha-replushed) | `blahaj-replushed-4.0.0+26.3.jar` | `4.0.0+26.3` | Both | `fabric-api` *(installed)* | None |
| 10 | **Fun** | [Spyglass Astronomy](https://modrinth.com/mod/spyglass-astronomy) | `spyglass_astronomy-1.0.27-mc26.3.jar` | `1.0.27-mc26.3` | Client | `fabric-api` *(installed)* | None (Overworld sky only) |
| 11 | **Fun** | [Kaffee's Dual Ride](https://modrinth.com/mod/kaffees_dual_ride) | `Kaffee's_Dual_Ride-1.2.0.jar` | `1.2.0` | Both | `fabric-api` *(installed)* | None |
| 12 | **Fun** | [Chalk (Fabric)](https://modrinth.com/mod/chalk) | `chalk-3.2.0+26.3.jar` | `3.2.0+26.3` | Both | `fabric-api` *(installed)* | None |
| 13 | **QoL** | [Explorer's Compass](https://modrinth.com/mod/explorers-compass) | `ExplorersCompass-26.3-2.5.2-fabric.jar` | `26.3-2.5.2-fabric` | Both | `fabric-api` *(installed)* | None (Pairs with `naturescompass`) |
| 14 | **QoL** | [Easy Magic](https://modrinth.com/mod/easy-magic) | `easymagic-v26.3.1-mc26.3.x+fabric.jar` | `26.3.1` | Both | `puzzleslib`, `forgeconfigapiport` *(installed)* | Supersedes `stickyenchantinglapis` |
| 15 | **QoL** | [Easy Shulker Boxes](https://modrinth.com/mod/easy-shulker-boxes) | `easyshulkerboxes-v26.3.4-mc26.3.x+fabric.jar` | `26.3.4` | Both | `puzzleslib`, `forgeconfigapiport` *(installed)* | None |
| 16 | **QoL** | [Ping Wheel](https://modrinth.com/mod/ping-wheel) | `Ping-Wheel-1.12.2-fabric-26.3.jar` | `1.12.2` | Both | `fabric-api` *(installed)* | None |
| 17 | **QoL** | [Trade Cycling](https://modrinth.com/mod/trade-cycling) | `trade-cycling-fabric-1.0.23+26.3.jar` | `1.0.23+26.3` | Both | `fabric-api` *(installed)* | None (Complements `tradingpost`) |
| 18 | **QoL** | [Leave My Bars Alone](https://modrinth.com/mod/leave-my-bars-alone) | `leavemybarsalone-v26.3.0-mc26.3.x+fabric.jar` | `26.3.0` | Client | `puzzleslib`, `forgeconfigapiport` *(installed)* | None |
| 19 | **QoL** | [Horse Expert](https://modrinth.com/mod/horse-expert) | `horseexpert-v26.3.1-mc26.3.x+fabric.jar` | `26.3.1` | Both | `puzzleslib`, `forgeconfigapiport` *(installed)* | None (Integrates with `trinkets_updated`) |
| 20 | **QoL** | [FastQuit](https://modrinth.com/mod/fastquit) | `fastquit-3.1.5+mc26.2.jar` | `3.1.5+mc26.2` *(26.3)* | Client | `cloth-config` *(installed)* | None |
| 21 | **QoL** | [ClickThrough Plus](https://modrinth.com/mod/clickthrough-plus) | `clickthrough-plus-3.6.5+26.3-pre-3-fabric.jar` | `3.6.5+26.3` | Client | `fabric-api` *(installed)* | None (`cutthrough` is attack-only) |
| 22 | **QoL** | [Followers Teleport Too](https://modrinth.com/mod/followers-teleport-too) | `followersteleporttoo-26.3.0-3.2.jar` | `26.3.0-3.2` | Server/Both | `collective` *(installed)* | None (Pairs with `waystones`) |
| 23 | **QoL** | [Healing Campfire](https://modrinth.com/mod/healing-campfire) | `healingcampfire-26.3.0-7.1.jar` | `26.3.0-7.1` | Server/Both | `collective` *(installed)* | None (Pairs with `simplytents`) |
| 24 | **QoL** | [Neruina — Ticking Entity Fixer](https://modrinth.com/mod/neruina) | `neruina-3.3.5+26.3-fabric.jar` | `3.3.5` | Both | `configurable-3.5.3+26.3-fabric.jar` | None |
