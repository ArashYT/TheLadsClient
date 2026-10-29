# Proposed privacy page additions (ladsclient-site/public/privacy.html, section 3 table)

ladsclient-site is outside this repository, so the rows are proposed here. Insert into section 3 table:

```html
<tr>
<td><strong>8. Cosmetics &amp; Emotes (<code>bot.arashyt.ca/cosmetics/*</code>)</strong></td>
<td>Your Minecraft UUID and username, equipped cosmetic slots (cape, hat, glasses, emote), and an ephemeral hash of the server host:port you are playing on. Your Minecraft access token is <strong>never</strong> sent to us: authentication uses standard Mojang <code>hasJoined</code> verification.</td>
<td>To display your equipped cosmetics and sync emotes with other Lads Client players on the same server. Only active while Cosmetics is enabled in-game.</td>
<td><strong>Art. 6(1)(b) Service Delivery</strong>. Permanent entitlements and currently equipped items are stored by UUID in <code>cosmetics.db</code>. Room presence and live emote relays are kept in memory only while connected and discarded on disconnect.</td>
</tr>
<tr>
<td><strong>9. Friends, Social &amp; P2P Signaling (<code>bot.arashyt.ca/social/*</code>)</strong></td>
<td>Your Minecraft UUID and username, friends list, incoming/outgoing friend requests, user blocks, friend nicknames, and offline direct messages. For P2P sessions, WebRTC signaling and ICE candidate IP addresses are exchanged directly between participants. Per-user monthly relay transfer bytes are tracked for Cloudflare TURN quota management.</td>
<td>To provide in-game friends management, online presence, direct messaging, and peer-to-peer multiplayer connectivity.</td>
<td><strong>Art. 6(1)(b) Service Delivery</strong>. Friends graph, blocks, and settings are stored in <code>social.db</code> until removed or unfriended. Offline DMs are automatically deleted after delivery or purged after 7 days. Relay byte counters reset monthly. Active presence respects in-game 'Appear Offline' and 'Server: Hidden' privacy settings.</td>
</tr>
<tr>
<td><strong>10. Crash Reporting &amp; Diagnostics (<code>bot.arashyt.ca/crash</code>)</strong></td>
<td>Crash stack traces, client version, operating system, and hardware summary submitted during client or JVM crashes.</td>
<td>To identify stability bugs, diagnose mod incompatibilities, and triage crashes in #crash-reports.</td>
<td><strong>Art. 6(1)(f) Legitimate Interests</strong>. Stored reports are retained for at most 30 days with a strict 200 MB directory cap. Minecraft usernames in crash reports are treated as unverified.</td>
</tr>
<tr>
<td><strong>11. HUD Sharing &amp; Anonymous Metrics (<code>bot.arashyt.ca/share</code>, <code>/usage</code>)</strong></td>
<td>Custom HUD configuration payloads (base64 JSON) and anonymous client heartbeat pings with a random installation UUID.</td>
<td>To enable sharing HUD layout codes among players and track general client version adoption.</td>
<td><strong>Art. 6(1)(a) Consent / Art. 6(1)(f)</strong>. Share codes store configuration payloads only; creator IP addresses and IP hashes are <strong>never</strong> stored. Usage heartbeats store no IP addresses or personal identifiers.</td>
</tr>
```
