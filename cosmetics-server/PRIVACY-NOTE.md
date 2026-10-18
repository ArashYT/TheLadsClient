# Proposed privacy page addition (ladsclient-site/public/privacy.html, section 3 table)

ladsclient-site is outside this repository, so the row is proposed here. Insert after row 7:

```html
<tr>
<td><strong>8. Cosmetics &amp; Emotes (<code>bot.arashyt.ca</code>)</strong></td>
<td>Your Minecraft UUID and username, a one-way hash (SHA-256) of the address of the server you are playing on, and which emote you are playing. Your IP address is seen transiently for connection limits. Your Minecraft access token is <strong>never</strong> sent to us: the client proves your account to Mojang, and we only ask Mojang to confirm it, as any Minecraft server does.</td>
<td>So other Lads Client players on the same server can see your emotes (and, later, cosmetics), and you can see theirs. Only sent while the Cosmetics module is on and you are in a multiplayer world; nothing is sent in singleplayer or menus, and nothing ever goes through the Minecraft server you play on.</td>
<td><strong>Art. 6(1)(b) Service Delivery</strong> (an in-game feature you can switch off in the Cosmetics module). Kept in memory only while you are connected; <strong>never written to disk</strong>, gone when you disconnect or the server restarts.</td>
</tr>
```
