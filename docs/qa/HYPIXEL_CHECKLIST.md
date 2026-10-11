# Hypixel test checklist (1.8.9 and 26.3)

Hypixel's rules: [Allowed Modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962). In short, a mod is allowed only if it is
performance, cosmetic, a HUD that shows nothing you couldn't already see, or brightness/gamma. Not allowed: minimaps, other players'
health/armor, player distance or range, changing your perspective (seeing around or over blocks), anything that automates an action
("macros, auto-sprint, aim assists", chat macros), and anything that changes how the client talks to the server. A client is allowed
only if every feature in it is, or can be fully turned off. Lads blocks some modules on Hypixel by hostname
(`common/.../client/HypixelSafetyManager.java`): Minimap, XaeroMinimap, XaeroWorldMap, AutoReconnect, MouseTweaks, ReachDisplay,
Jade, ShulkerBoxUtils.

Use an alt account where possible. Note version, game mode and what happened for anything odd, and attach `logs/latest.log`.

## Before release: rule risks found in the code

| # | Risk | Rule | Suggested fix |
|---|---|---|---|
| 1 | Toggle Sprint has an "Always" sprint mode | "auto-sprint" is named as disallowed | Block the Always mode on Hypixel (keep press-to-toggle) |
| 2 | Bundled Xaero's World Map (26.3, M key) and its waypoints/radar have no Hypixel gate; only the Lads modules are blocked | Minimaps not allowed | Disable the Xaero mods' keybinds/rendering on Hypixel, or confirm Xaero's own fair-play mode kicks in there |
| 3 | Hypixel is detected only when the address ends in `hypixel.net`/`hypixel.io`; a raw IP or alias skips every block | — | Also match Hypixel's IP ranges / the server brand it sends |
| 4 | A blocked module is hidden via `isEnabled()` but never turned off (`onDisable` not called); anything set once at enable time may stay active | — | Call the disable path when joining Hypixel |
| 5 | ViaFabricPlus (26.3) can change the protocol version | "alter the way your client communicates with our server" | Force ViaFabricPlus to native on Hypixel |
| 6 | IgnorePacketErrors (on by default) keeps you connected on packets it can't read | same rule, arguably | Turn it off on Hypixel |
| 7 | Crosshair "Highlight Players/Hostiles" | grey area (extra info) | Off on Hypixel, or leave off by default |
| 8 | Discord RPC shares the server address by default | privacy, not a rule | Off by default |

## Every session

- [ ] Join `mc.hypixel.net` (1.8.9 and 26.3). The connection is not refused; no "outdated client" or protocol errors.
- [ ] Open Lads settings in a lobby: Minimap, Xaero, AutoReconnect, MouseTweaks, ReachDisplay, Jade, ShulkerBoxUtils show **Blocked on Hypixel**.
- [ ] 26.3: press M (world map), check waypoints and the radar. Note anything that still shows (risk 2).
- [ ] 26.3: ViaFabricPlus version is native (risk 5).
- [ ] Join by a raw IP or another address (risk 3): note which blocked modules come back.
- [ ] Turn on Zoom, Fullbright, Keystrokes/CPS, ArmorHUD, Potion Effects, 1.7 Animations, Item Physics, Toggle Sprint (toggle mode only). Play a few
  games: no Watchdog warnings, no kicks, no visual glitches.
- [ ] Note FPS in a busy lobby and mid-game (F3 or the FPS HUD).
- [ ] Leave and rejoin; switch lobbies 3 times; `/hub` from a game. No freezes, no "Signal lost" false alarms, chat history kept.
- [ ] Essential: friends list, cosmetics and screenshots work; no extra chat spam.

## Per game mode (play at least one full game each)

| Game | Check |
|---|---|
| Bed Wars (solo + 4v4) | Item shop and upgrade GUIs (MouseTweaks blocked = vanilla behavior), scoreboard HUD, kill messages and KillBanner, bed-break titles, TNT/fireball visuals |
| SkyWars | Chest GUIs, kit selector, void/fall, nametags at distance |
| Duels (Sumo, Classic, UHC, Bridge) | Hit registration feel with 1.7 Animations on and off, keystrokes/CPS, armor HUD, Bridge goal titles |
| Zombies (Arcade) | Many entities: FPS, EntityCulling (no zombies vanishing), weapon/perk GUIs, round titles |
| Murder Mystery | Nametags never show through walls, bow/knife animations |
| The newest minigame | Everything in "Every session"; this is where Lunar reportedly had a bug, so record anything odd |
| SkyBlock | Menus, NPCs, large inventories, Item Physics on dropped items, chat-heavy areas |
| Housing / Build Battle | Custom resource packs load, title/subtitle display |
| Party / guild chat | Chat Heads, timestamps, long histories, links |

## Report

For each problem: version (1.8.9 / 26.3), game mode, which Lads modules were on, what happened, and `latest.log`. Post it in
`#bug-reports`.
