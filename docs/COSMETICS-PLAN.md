# Lads Cosmetics plan (emotes, capes, cloaks, hats, glasses, wearables)

Status 2026-10-17: phase 1 (one free emote, synced to every Lads player) is being built for 1.9.11. Phases 2-4 are the plan.

## How it fits together

| Piece | Where | Job |
|---|---|---|
| Cosmetics service | bot web app (`bot.arashyt.ca/cosmetics/*`, aiohttp, Arash-HP) | Verifies the player owns the Minecraft account (Mojang `join`/`hasJoined`, the same check servers use), keeps who is online on which server, relays emotes in real time over one WebSocket, serves each player's owned + equipped cosmetics. |
| Client (TheLadsCore, all versions) | `client/cosmetics/*` | One background WebSocket while you are on a server. Plays emotes (third person for everyone, first-person arms for you), draws wearables on players who own them. Never sends anything to the Minecraft server, so it is safe on Hypixel and every other server. |
| Shop | ladsclient-site (Cloudflare Worker) + a payment provider | Sells cosmetics; a purchase adds the item to the buyer's Minecraft UUID. |
| Assets | CDN (Cloudflare R2/the site) | Models, textures and emote files, downloaded once, cached on disk, versioned by hash. |

## Phases

1. **Phase 1 (1.9.11): emote engine + sync.** One looping dance ("Groove", free for everyone), emote wheel on B, smooth blend in/out (moving stops the dance and the walk animation takes over without a snap), first-person arms, visible to all Lads players on the same server.
2. **Phase 2: ownership + equip.** `GET /cosmetics/profile/{uuid}` returns owned/equipped items; a Cosmetics screen in the client to equip what you own. Ownership lives in a small database on the service (SQLite to start, the same pattern as share codes).
3. **Phase 3: wearables.** Capes/cloaks (our own cape layer, wavy physics already exist), hats, glasses, back items: low-poly models (made in Blockbench, exported to our own simple JSON format), one texture atlas per item, drawn as an extra model layer attached to the head/body. Animated textures by frame strips.
4. **Phase 4: the shop** (researched 2026-10-18, artifacts/cosmetics/SHOP-RESEARCH.md + SHOP-RESEARCH-REVIEW.md):
   - **Provider: Tebex** (headless API + Tebex.js) as merchant of record: built for Minecraft, handles Canada GST/HST, EU VAT and US tax, and chargebacks. Stripe, Paddle and Lemon Squeezy forbid selling items for a game you don't publish.
   - **Which account gets the item:** the buyer types their Minecraft username (public Mojang lookup -> UUID) at checkout; the in-game "link" button (one-time token from the cosmetics service) confirms it. Microsoft sign-in on the website is not an option: new apps are blocked from the Minecraft profile API without Mojang's approval.
   - Tebex webhook -> service grants the item; refunds/chargebacks revoke it automatically.
   - **Rules:** Mojang's usage guidelines don't explicitly allow selling client cosmetics; Lunar/Badlion/Essential do it and it is tolerated. Show the "Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft." disclaimer on the store, checkout, receipts and the in-game wardrobe; don't copy Mojang's cape textures or call our items official capes.

## Performance rules (all phases)

- Nothing runs when nobody near you has cosmetics: one map lookup per rendered player.
- No allocations per frame; poses are sampled into reused buffers.
- Models are tiny (dozens of cubes), baked once into vertex buffers per item, drawn with the player's own render pass; texture atlases, no per-player textures.
- Only players inside render distance are animated; distant players drop to the static pose (LOD).
- Network: one WebSocket per client, a few bytes per emote change; nothing per frame.
- Every cosmetic is optional per viewer ("Show other players' cosmetics").

## Rules we keep

- Client-side only: other players see cosmetics only if they use Lads Client. Servers never receive anything, so no anti-cheat risk.
- Privacy: while cosmetics are on, the service sees your Minecraft UUID/name and a hash of the server address you are on (to know who can see your emote). Nothing is written to disk in phase 1. The privacy page must say this before release.
- Moderation: only cosmetics we make; no user uploads (avoids offensive content and copyright problems).
- Clean-room: no code or assets copied from Emotecraft, Essential, Figura or other mods.
