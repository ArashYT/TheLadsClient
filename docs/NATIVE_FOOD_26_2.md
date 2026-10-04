# Built-in food overlays (the AppleSkin module)

Since 1.7.0 the AppleSkin catalog entry is Lads' own code on 26.2, 26.3 and 1.8.9, written from the mod's public feature list and the game's food rules. It contains no AppleSkin code, textures or config format. The 1.6.0 adaptation and its license notice were removed; 26.x listens on AppleSkin's server channels again with Lads' own receiver (see below). 1.21.11 is frozen and still ships the upstream jar.

## Behavior and settings (module "AppleSkin", option names unchanged from 1.6.0)

- **Show Food Values**: while you hold a food you can eat, the hunger icons it would fill pulse on the hunger bar. Harmful food (rotten flesh, spider eyes, raw chicken, pufferfish) pulses in the hunger effect's green icons. **Offhand Food** (26.x) also previews an edible off-hand food when the main hand has none.
- **Show Saturation**: current saturation is drawn as gold outlines on the hunger icons. A part-filled icon shows its right side, like the game's half shank. **Show Saturation Overlay** pulses the saturation the held food would add.
- **Show Exhaustion**: exhaustion (0 to 4) is drawn as a pale band behind the hunger bar, growing from the right.
- **Show Health Overlay**: the hearts the held food would give back pulse on the health bar. The estimate covers natural regeneration from the new hunger and saturation, following the game's tick rules (26.x fast healing at a full bar, 1.8.9's 1 health per 3 exhaustion), plus the food's own Regeneration (golden apples). It is hidden in Peaceful, where health regenerates anyway.
- **Vanilla Animations**: previews follow the bars' shake (hunger at empty saturation, hearts at low health, the Regeneration bounce). They take the positions the game actually drew; off, they stay steady.
- **Overlay Opacity**: the pulse's peak opacity.
- **Food Tooltips** and **Tooltips Always Visible** (off: hold Shift). 26.x tooltips show the game's own hunger icons and points, then gold saturation outlines and points, as inline sprites in a plain text line, so any tooltip renderer shows them. 1.8.9 tooltips are text only and show "Food: +4 hunger, +2.4 saturation". When EnhancedTooltips already prints that line, it is not repeated.
- **F3**: a food line with hunger, saturation and exhaustion. On 26.x, enable it in the debug options screen as "theladscore:food".
- With the module off, nothing is drawn. It never changes hunger, health, items or packets.

## What the client can know

Singleplayer worlds, and LAN worlds you host, run an integrated server in the same game. The overlays read that server's own copy of your player, so saturation, exhaustion and the natural-regeneration game rule are exact.

On 26.x, a server that runs AppleSkin's server side sends your exact saturation, exhaustion and its natural-regeneration game rule on AppleSkin's channels (`appleskin:saturation` and `appleskin:exhaustion`, one big-endian float each; `appleskin:natural_regeneration`, one boolean byte). Lads registers its own receiver for them (`AppleSkinPayload`, decoded and range-checked by common `AppleSkinSync`, unit-tested in `AppleSkinSyncTest`) and uses those values there; they are forgotten on every join. A loaded AppleSkin jar keeps the channels and the job.

On other servers the game sends hunger and saturation, but saturation only arrives with a health or hunger change. Exhaustion is never sent. There, saturation is the last value received, the exhaustion band and F3 exhaustion ("?") are not shown, and health estimates assume no exhaustion and natural regeneration on. 1.8.9 has no server channel (1.6.0's 1.8.9 code had none either).

## Compatibility

A manually installed AppleSkin jar keeps the job: on 26.x the module then stands down and the card links to that mod's settings. The managed installer removes only Lads' own pinned copies.

## Verification

- `common`: `FoodPreviewTest` covers eating caps, icon fill, the pulse, both regeneration rule sets, Regeneration effect healing and the F3 line.
- 26.x: `FoodProbe` (in the native feature probe, marker "Lads food probe END") checks real tooltip lines, hidden components, options, off-hand selection, harmful food and the integrated-server read. `LADS_VERIFY_CAPTURE_HUDINFO=1` photographs the previews, tooltips and crosshair styles (`screenshots/hudinfo-*.png`). `LADS_VERIFY_APPLESKIN_SYNC=1` (`AppleSkinSyncCapture`, request file `.lads-qa-appleskin-sync`, marker "Lads AppleSkin sync capture END") reads the QA world as a remote server, injects saturation 3.5, exhaustion 2 and natural regeneration off as AppleSkin payloads through the registered codec and the client's own connection, checks the values, saves `screenshots/appleskin-sync-1.png`, and checks that a short or impossible payload is ignored and a join forgets the values.
- 1.8.9: the self-test (`Probe150e`) checks the integrated-server read, Regeneration and harmful-food detection, and saves `lads-qa/screenshots/150-appleskin.png` and `170-food-*`, `170-tooltip-*` and `170-crosshair-*` shots.
