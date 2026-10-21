"""
Simulates every command, button, select, modal, prefix command, event and background task in bot.py
against the REAL guild and posts each one's real output into the private #🔒-bot-test channel.

    python discord-bot/tests/simulate_all.py [--state PATH] [--only NAME,NAME]

Safety (why this can run while the 24/7 bot is live):
- REST only (bot.login, no gateway), so it never takes the live bot's gateway session.
- Every HTTP write goes through an allowlist guard: only the test channel and threads created in it can be
  written to. Bans, kicks, timeouts, role/nickname edits, channel creation/deletion, stats renames and DMs
  are recorded as "dry-run: <METHOD path>" and never sent ("verified up to the API call").
- It runs on a temporary COPY of state.json and a temporary RELEASES_DIR, so live state is never written.
- All sends use AllowedMentions.none(): nobody is pinged, not even 🔄 Update Ping.
- The guild cache holds only the test channel (aliased under every channel name the bot looks up) and the
  read-only stats category, so nothing can land in a public channel.
The bot token is read by bot.py's own _bot_token() and is never printed.
"""
import argparse
import asyncio
import json
import os
import re
import shutil
import sys
import tempfile
import time
import traceback
from datetime import datetime, timedelta, timezone
from pathlib import Path
from types import SimpleNamespace

BOT_DIR = Path(__file__).resolve().parent.parent
TEST_CHANNEL_NAME = "🔒-bot-test"
LIVE_STATE = Path(r"\\Arash-HP\Servers\TheLadsBot\state.json")
REPLY_WINDOW = 3.0  # seconds Discord gives an interaction to be acknowledged

parser = argparse.ArgumentParser()
parser.add_argument("--state", help="state.json to copy for the run (default: live Arash-HP copy, else local)")
parser.add_argument("--only", help="comma-separated step names to run (default: all)")
args = parser.parse_args()

# --- sandbox the bot's files BEFORE importing it ---
SANDBOX = Path(tempfile.mkdtemp(prefix="lads-bot-sim-"))
src_state = Path(args.state) if args.state else (LIVE_STATE if LIVE_STATE.exists() else BOT_DIR / "state.json")
shutil.copyfile(src_state, SANDBOX / "state.json")
(SANDBOX / "releases").mkdir()
(SANDBOX / "releases" / "9.9.9.md").write_text(
    "# 9.9.9\n\n## Highlights\n- Simulation harness test notes (not a real release).\n\n"
    "## Added\n- Nothing real.\n\n## Changed\n- Nothing real.\n\n## Fixed\n- Nothing real.\n", encoding="utf-8")
os.environ["STATE_FILE_PATH"] = str(SANDBOX / "state.json")
os.environ["RELEASES_DIR"] = str(SANDBOX / "releases")

sys.path.insert(0, str(BOT_DIR))
import discord  # noqa: E402
from discord import app_commands  # noqa: E402
import bot as B  # noqa: E402

bot = B.bot
DRY: list = []         # dry-run API calls recorded during the current step
RESULTS: list = []     # (name, status, detail)
ALLOWED_CHANNELS: set = set()
TEST: discord.TextChannel = None
GUILD: discord.Guild = None


# ------------------------------------------------------------------ HTTP allowlist guard

def install_guard():
    real_request = bot.http.request

    async def guarded(route, **kwargs):
        method, path, ch = route.method, route.path, route.channel_id
        payload = kwargs.get("json") or {}
        if method == "GET":
            return await real_request(route, **kwargs)
        allowed = ch in ALLOWED_CHANNELS
        if allowed and path == "/channels/{channel_id}":
            # never delete/rename/move the test channel itself (slowmode is the only edit allowed);
            # threads the harness created inside it may be deleted during cleanup
            allowed = ch != TEST.id or (method == "PATCH" and set(payload) <= {"rate_limit_per_user"})
        if allowed:
            result = await real_request(route, **kwargs)
            if method == "POST" and path.endswith("/threads") and isinstance(result, dict):
                ALLOWED_CHANNELS.add(int(result["id"]))
            return result
        shown = route.url.split("/api/v10", 1)[-1]
        extra = f" {json.dumps(payload, ensure_ascii=False)[:120]}" if payload else ""
        DRY.append(f"{method} {shown}{extra}")
        return None

    bot.http.request = guarded


def dry(text):
    DRY.append(text)


# ------------------------------------------------------------------ test doubles

class FakeMember:
    """A member that never touches the API: role/nick/ban/kick/timeout/DM calls are only recorded."""

    def __init__(self, uid, name, roles=(), is_bot=False, perms=None):
        self.id, self.name, self.display_name, self.global_name, self.bot = uid, name, name, name, is_bot
        self.mention = f"<@{uid}>"
        self.roles = list(roles)
        self.joined_at = datetime.now(timezone.utc) - timedelta(days=3)
        self.display_avatar = self.avatar = SimpleNamespace(url="https://cdn.discordapp.com/embed/avatars/0.png")
        self.guild_permissions = perms if perms is not None else discord.Permissions.all()
        self.status = discord.Status.online
        self.guild = GUILD

    def __eq__(self, other):
        return getattr(other, "id", None) == self.id

    def __hash__(self):
        return hash(self.id)

    def __str__(self):
        return self.name

    async def add_roles(self, *roles, reason=None):
        dry(f"PUT member roles {[r.name for r in roles]} -> fake {self.name}")
        self.roles += [r for r in roles if r not in self.roles]

    async def remove_roles(self, *roles, reason=None):
        dry(f"DELETE member roles {[r.name for r in roles]} -> fake {self.name}")
        self.roles = [r for r in self.roles if r not in roles]

    async def edit(self, **kwargs):
        dry(f"PATCH member {self.name} {kwargs}")

    async def ban(self, **kwargs):
        dry(f"PUT /guilds/{{guild}}/bans/{self.id} {kwargs}")

    async def kick(self, **kwargs):
        dry(f"DELETE /guilds/{{guild}}/members/{self.id} {kwargs}")

    async def timeout(self, until, **kwargs):
        dry(f"PATCH /guilds/{{guild}}/members/{self.id} communication_disabled_until={until} {kwargs}")

    async def send(self, *a, **kw):
        dry(f"DM to fake {self.name} (blocked)")

    async def move_to(self, channel, **kw):
        dry(f"move fake {self.name} -> {getattr(channel, 'name', channel)}")


class FakeVoice:
    def __init__(self, name):
        self.id, self.name, self.members, self.category = 4242, name, [], None

    async def delete(self, **kw):
        dry(f"DELETE voice channel {self.name}")


async def post(content=None, embed=None, embeds=None, view=None, ephemeral=False):
    if ephemeral:
        content = "-# 👁 ephemeral reply" + (f"\n{content}" if content else "")
    kwargs = {"content": (content or "")[:2000] or None}
    if embed is not None:
        kwargs["embed"] = embed
    if embeds:
        kwargs["embeds"] = embeds
    if view is not None:
        kwargs["view"] = view
    if not kwargs["content"] and "embed" not in kwargs and "embeds" not in kwargs:
        kwargs["content"] = "(empty reply)"
    return await TEST.send(**kwargs)


class FakeResponse:
    def __init__(self, it):
        self.it, self._done = it, False

    def is_done(self):
        return self._done

    def _ack(self, kind):
        if self._done:
            raise discord.InteractionResponded(self.it)
        self._done = True
        self.it.ack_kind = kind
        elapsed = time.monotonic() - self.it.t0
        if elapsed > REPLY_WINDOW:
            self.it.late = elapsed

    async def send_message(self, content=None, *, embed=None, embeds=None, view=None, ephemeral=False, **kw):
        self._ack("message")
        msg = await post(content, embed, embeds, view, ephemeral)
        self.it.sent.append(msg)
        return msg

    async def defer(self, *, ephemeral=False, thinking=False):
        self._ack("defer")

    async def send_modal(self, modal):
        self._ack("modal")
        self.it.modal = modal
        modal.to_dict()  # must serialise like the real thing
        fields = ", ".join(getattr(c, "label", None) or "?" for c in modal.children)
        self.it.sent.append(await post(f"🧾 *opens modal* **{modal.title}** — fields: {fields}"))

    async def edit_message(self, **kw):
        self._ack("edit")
        kw.pop("delete_after", None)
        await self.it.message.edit(**kw)


class FakeFollowup:
    def __init__(self, it):
        self.it = it

    async def send(self, content=None, *, embed=None, embeds=None, view=None, ephemeral=False, **kw):
        if not self.it.response.is_done():
            raise RuntimeError("followup.send before the interaction was acknowledged (Discord: Unknown Webhook)")
        msg = await post(content, embed, embeds, view, ephemeral)
        self.it.sent.append(msg)
        return msg


class FakeInteraction:
    def __init__(self, user, *, command=None, message=None, data=None, permissions=None):
        self.client, self.guild, self.guild_id = bot, GUILD, GUILD.id
        self.channel, self.channel_id = TEST, TEST.id
        self.user, self.message, self.command = user, message, command
        self.data = data or {}
        self.permissions = self.app_permissions = permissions if permissions is not None else discord.Permissions.all()
        self.id = discord.utils.time_snowflake(datetime.now(timezone.utc))
        self.created_at = datetime.now(timezone.utc)
        self.extras, self.locale = {}, discord.Locale.american_english
        self.response, self.followup = FakeResponse(self), FakeFollowup(self)
        self.t0, self.late, self.sent, self.modal, self.ack_kind = time.monotonic(), None, [], None, None


class FakeMessage:
    """A member's message for on_message / prefix commands; `anchor` is a real message it replies to."""

    def __init__(self, author, content, channel, anchor):
        self.author, self.content, self.channel, self.guild = author, content, channel, GUILD
        self.attachments, self.mentions, self.embeds = [], [], []
        self.id, self._state, self._anchor = anchor.id, bot._connection, anchor
        self.type, self.webhook_id, self.interaction_metadata = discord.MessageType.default, None, None

    def to_message_reference_dict(self):
        return self._anchor.to_message_reference_dict()

    async def delete(self, **kw):
        dry(f"delete fake member message {self.content[:30]!r}")

    async def reply(self, content=None, **kw):
        return await self.channel.send(content, reference=self._anchor, **kw)


# ------------------------------------------------------------------ step runner

async def step(name, fn, *, it=None, expect_reply=True):
    if args.only and name.split()[0] not in args.only.split(","):
        return None
    await asyncio.sleep(1.2)  # keep the channel's 5-msgs/5s bucket from inflating the 3 s timing check
    await TEST.send(f"### ▶ {name}")
    DRY.clear()
    status, detail, value = "PASS", "", None
    if it is not None:
        it.t0 = time.monotonic()  # the 3 s window starts now, not before the harness's own pause/header
    try:
        value = await asyncio.wait_for(fn(), 90)
    except Exception as e:
        status, detail = "FAIL", f"{type(e).__name__}: {e}"
        traceback.print_exc()
        if it is not None and not it.response.is_done():
            try:
                await B.reply_with_error(it, e)
            except Exception:
                pass
    if it is not None and status == "PASS":
        if it.late:
            status, detail = "FAIL", f"first reply after {it.late:.1f}s (> 3 s: Discord shows 'did not respond')"
        elif expect_reply and not it.response.is_done():
            status, detail = "FAIL", "never acknowledged (Discord shows 'The application did not respond')"
    if isinstance(value, str) and status == "PASS":
        detail = value
    if DRY and status == "PASS":
        status = "PASS*"
        detail = (detail + "; " if detail else "") + "verified up to the API call: " + " | ".join(DRY)[:600]
    RESULTS.append((name, status, detail))
    print(f"{status:5} {name}: {detail}", flush=True)
    return value


def cmd(name):
    c = bot.tree.get_command(name)
    assert c is not None, f"/{name} is not registered"
    return c


async def slash(name, user, /, **kwargs):
    """Run a slash command the way the tree would: checks first, then the callback, errors to tree.on_error."""
    c = cmd(name)
    it = FakeInteraction(user, command=c)

    async def run():
        try:
            if not await c._check_can_run(it):
                raise app_commands.CheckFailure("check returned False")
            await c.callback(it, **kwargs)
        except Exception as e:
            await B.on_app_command_error(it, e)  # the real global handler
            raise
        return None

    label = f"/{name} " + " ".join(f"{k}:{getattr(v, 'name', v)}" for k, v in kwargs.items())
    await step(label.strip(), run, it=it)
    return it


async def component(name, user, callback, message=None, data=None, expect_reply=True):
    it = FakeInteraction(user, message=message, data=data or {})
    await step(name, lambda: callback(it), it=it, expect_reply=expect_reply)
    return it


# ------------------------------------------------------------------ guild sandbox

async def build_guild():
    global GUILD, TEST
    GUILD = await bot.fetch_guild(B.GUILD_ID)
    GUILD._member_count = GUILD.approximate_member_count
    channels = await GUILD.fetch_channels()
    TEST = discord.utils.get(channels, name=TEST_CHANNEL_NAME)
    assert TEST is not None, f"#{TEST_CHANNEL_NAME} not found"
    ALLOWED_CHANNELS.add(TEST.id)
    GUILD._channels.clear()
    GUILD._add_channel(TEST)
    raw = await bot.http.get_channel(TEST.id)
    names = ["roles", "faq", "tech-support", "suggestions", "giveaways", "client-setups", "looking-for-group",
             "changelog", "mod-logs", "general", "bug-reports"]
    for i, name in enumerate(names, start=1):
        GUILD._channels[-i] = discord.TextChannel(state=bot._connection, guild=GUILD, data={**raw, "name": name})
    stats = discord.utils.get(channels, name=B.STATS_CATEGORY)
    if stats:
        GUILD._add_channel(stats)
        for c in channels:
            if c.category_id == stats.id:
                GUILD._add_channel(c)
    GUILD._add_member(await GUILD.fetch_member(bot.user.id))
    bot._connection._add_guild(GUILD)


def alias(name):
    return discord.utils.get(GUILD.text_channels, name=name)


def state():
    return B.load_state()


# ------------------------------------------------------------------ the simulation

async def simulate():
    admin = FakeMember(100000000000000001, "harness-admin")
    u1 = FakeMember(100000000000000002, "harness-player")
    u2 = FakeMember(100000000000000003, "harness-friend")
    nobody = FakeMember(100000000000000004, "harness-no-perms")
    role = lambda n: discord.utils.get(GUILD.roles, name=n)  # noqa: E731

    await TEST.send(f"## 🧪 Bot simulation run — {datetime.now():%Y-%m-%d %H:%M}\n"
                    "-# Every reply below is the bot's real output. Writes outside this channel are dry-run only; "
                    "nobody is pinged; state is a temporary copy.")

    # --- startup ---
    async def check_setup_hook():
        persistent = {type(v).__name__ for v in bot.persistent_views}
        need = {"NotificationRolesView", "ColorRolesView", "FAQPanelView", "TicketPanelView", "TicketControlView",
                "SuggestionPanelView", "SuggestionVoteView", "LFGSquadView"}
        assert need <= persistent, f"missing persistent views: {need - persistent}"
        assert B.GiveawayButton in bot._connection._view_store._dynamic_items.values(), "GiveawayButton not registered"
        return f"{len(persistent)} persistent views + dynamic GiveawayButton"
    await step("setup_hook (persistent views)", check_setup_hook)

    async def check_commands():
        cmds = bot.tree.get_commands()
        for c in cmds:
            c.to_dict(bot.tree)
        return f"{len(cmds)} slash commands serialise; prefix: {[c.name for c in bot.commands]}"
    await step("command payloads", check_commands)

    async def fake_sync(*, guild=None):
        return bot.tree.get_commands(guild=guild)
    bot.tree.sync = fake_sync
    for loop in (B.changelog_watcher, B.server_stats_updater, B.giveaway_ender):
        loop.is_running = lambda: True  # on_ready must not start the real loops here; they're run once below
    B.start_web_server = lambda: asyncio.sleep(0)
    await step("on_ready (panels, auto-roles, sync)", bot.on_ready)

    # --- fun & info ---
    await slash("coinflip", u1)
    await slash("roll", u1, sides=20)
    await slash("8ball", u1, question="Will the harness pass?")
    await slash("rps", u1, choice=app_commands.Choice(name="Rock 🪨", value="rock"))
    await slash("rate", u1, subject="The Lads Client")
    await slash("ship", u1, user1=u1, user2=u2)
    await slash("pvp", u1, opponent=u2)
    await slash("meme", u1)
    await slash("cat", u1)
    await slash("dog", u1)
    await slash("download", u1)
    await slash("faq", u1)
    await slash("colors", u1)
    await slash("mcstatus", u1, address="mc.hypixel.net")
    await slash("crashlog", u1, text="Exception in thread main java.lang.OutOfMemoryError: Java heap space")
    await slash("crashlog", u1, text="nothing recognisable here")

    # --- tags ---
    await slash("tag_list", u1)
    await slash("tag", u1, name="java")
    await slash("tag_create", admin, name="harnesstest", content="Harness test tag (deleted right after).")
    await slash("tag", u1, name="harnesstest")
    await slash("tag_delete", admin, name="harnesstest")
    await slash("tag", u1, name="harnesstest")

    # --- levels ---
    await slash("rank", u1)
    await slash("rank", u1, user=admin)
    await slash("leaderboard", u1)

    # --- account linking (fake Discord user, public IGN) ---
    await slash("link", u1, ign="Notch")
    await slash("link", u2, ign="Notch")  # duplicate claim must be refused
    await slash("profile", u1)
    await slash("profile", u2, ign="Notch")
    await slash("whois", u2, ign="Notch")
    await slash("skin", u1)
    await slash("cape", u1, player="Notch")
    await slash("unlink", u1)
    await slash("link", u1, ign="jeb_")
    await slash("unlink_staff", admin, user=u1, reason="harness cleanup")

    async def link_cleanup():
        left = [k for k in state()["linked_accounts"] if k.startswith("10000000000000000")]
        assert not left, f"harness links left in state: {left}"
        return "no harness entries left in linked_accounts"
    await step("linked_accounts cleanup", link_cleanup)

    # --- moderation (fake targets; API calls are dry-run) ---
    await slash("warn", admin, user=u2, reason="harness test warning")
    await slash("warnings", admin, user=u2)
    await slash("clearwarnings", admin, user=u2)
    await slash("ban", admin, user=u2, reason="harness dry-run")
    await slash("unban", admin, user_id=str(bot.user.id), reason="harness dry-run")
    await slash("kick", admin, user=u2, reason="harness dry-run")
    await slash("timeout", admin, user=u2, minutes=5, reason="harness dry-run")
    await slash("untimeout", admin, user=u2)
    for i in range(3):
        await TEST.send(f"purge-me {i + 1}/3")
    await slash("purge", admin, amount=4)  # the 3 messages above + this step's header
    await slash("slowmode", admin, seconds=5)
    await slash("slowmode", admin, seconds=0)
    await slash("lock", admin)
    await slash("unlock", admin)

    async def restore_overwrite():
        await TEST.set_permissions(GUILD.default_role, overwrite=discord.PermissionOverwrite(view_channel=False))
        return "@everyone overwrite back to: deny View Channel only"
    await step("restore #🔒-bot-test permissions", restore_overwrite)

    # permission check + global error handler
    async def no_perm():
        it = FakeInteraction(nobody, command=cmd("ban"), permissions=discord.Permissions.none())
        try:
            await cmd("ban")._check_can_run(it)
        except app_commands.MissingPermissions as e:
            await B.on_app_command_error(it, e)
            return "MissingPermissions -> friendly ephemeral reply"
        raise AssertionError("check did not block a member without Ban Members")
    await step("permission check + tree.on_error", no_perm)

    async def crash_handler():
        it = FakeInteraction(u1, command=cmd("coinflip"))
        await B.on_app_command_error(it, app_commands.CommandInvokeError(cmd("coinflip"), RuntimeError("simulated crash")))
        assert it.response.is_done()
        return "unexpected exception -> logged + friendly ephemeral reply"
    await step("tree.on_error (unexpected error)", crash_handler)

    # --- blacklist + automod ---
    await slash("blacklist_add", admin, word="harnessbadword")
    await slash("blacklist_list", admin)

    async def automod_word():
        anchor = await TEST.send("-# (simulated member message) this has harnessbadword in it")
        await B.on_message(FakeMessage(u1, "this has harnessbadword in it", TEST, anchor))
        assert any("delete fake member message" in d for d in DRY), "blacklisted word not deleted"
    await step("on_message automod: blacklisted word", automod_word)
    await slash("blacklist_remove", admin, word="harnessbadword")

    async def automod_crash():
        anchor = await TEST.send("-# (simulated #bug-reports message) java.lang.OutOfMemoryError: Java heap space")
        await B.on_message(FakeMessage(u1, "my game crashed: java.lang.OutOfMemoryError: Java heap space", alias("bug-reports"), anchor))
    await step("on_message crash-log analyzer", automod_crash)

    # --- sticky ---
    await slash("sticky_set", admin, message="📌 Harness sticky test (cleared right after).")

    async def sticky_repost():
        anchor = await TEST.send("-# (simulated member message to trigger the sticky repost)")
        before = state()["sticky_messages"][str(TEST.id)]["last_msg_id"]
        await B.on_message(FakeMessage(u1, "hello", TEST, anchor))
        after = state()["sticky_messages"][str(TEST.id)]["last_msg_id"]
        assert after != before, "sticky was not reposted"
        return "sticky reposted to the bottom"
    await step("on_message sticky repost", sticky_repost)
    await slash("sticky_clear", admin)

    # --- giveaways ---
    await slash("giveaway_start", admin, prize="Harness test prize (not real)", duration_minutes=1, winners=1)
    gw_id = max(state()["giveaways"], key=int)
    gw_msg = await TEST.fetch_message(int(gw_id))
    view = B.GiveawayView(gw_id, 0)
    await component("button: 🎉 Enter Giveaway", u1, view.children[0].callback, message=gw_msg)

    async def giveaway_dynamic():
        match = re.fullmatch(r"giveaway_enter_(?P<msg_id>\d+)", f"giveaway_enter_{gw_id}")
        item = await B.GiveawayButton.from_custom_id(FakeInteraction(u2), None, match)
        assert item.item.label.endswith("(1)"), item.item.label
        return f"restored after restart with label {item.item.label!r}"
    await step("giveaway button survives restart (DynamicItem)", giveaway_dynamic)
    await slash("giveaway_end", admin, message_id=gw_id)
    await slash("giveaway_end", admin, message_id=gw_id)  # already ended

    await slash("giveaway_start", admin, prize="Harness auto-end prize (not real)", duration_minutes=1, winners=1)

    async def giveaway_auto_end():
        s = state()
        gid = max(s["giveaways"], key=int)
        s["giveaways"][gid]["ends_at"] = (datetime.now(timezone.utc) - timedelta(seconds=5)).isoformat()
        B.save_state(s)
        await B.giveaway_ender.coro()
        assert gid not in state()["giveaways"], "expired giveaway was not ended"
        return "expired giveaway ended by the background task"
    await step("task giveaway_ender", giveaway_auto_end)

    # --- suggestions ---
    it = await slash("suggest", u1)
    modal = it.modal

    async def submit_modal():
        modal.mod_name._value = "Sodium"
        modal.mod_link._value = "https://modrinth.com/mod/sodium"
        modal.what_i_like._value = "Huge FPS boost on 1.20.1, works with our Connector setup."
        modal.notes._value = "Fabric 1.20.1 (harness test, not a real suggestion)"
        sub = FakeInteraction(u1)
        await modal.on_submit(sub)
        if sub.late:
            raise AssertionError(f"modal acknowledged after {sub.late:.1f}s")
        return "posted with vote buttons + discussion thread"
    await step("modal: 💡 Submit Mod Suggestion", submit_modal)
    sugg_id = max(state()["suggestion_votes"], key=int)
    sugg_msg = await TEST.fetch_message(int(sugg_id))
    vote_view = B.SuggestionVoteView()
    await component("button: 👍 Upvote", u2, lambda i: vote_view.handle_vote(i, "up"), message=sugg_msg)
    await component("button: 👎 Downvote", u2, lambda i: vote_view.handle_vote(i, "down"), message=sugg_msg)
    await slash("suggestion_action", admin, message_id=sugg_id,
                status=app_commands.Choice(name="Approved", value="approved"), reason="Harness test approval")
    await slash("suggestion_action", admin, message_id="123", status=app_commands.Choice(name="Denied", value="denied"))
    await slash("post_suggestion_panel", admin)
    await component("button: 💡 Submit Mod Suggestion (panel)", u1, B.SuggestionPanelButton().callback)

    # --- LFG ---
    await slash("lfg", u1, game=app_commands.Choice(name="Survival SMP", value="Survival SMP"), needed=1,
                notes="Zero SMP tonight (harness test)")
    lfg_msg = None
    async for m in TEST.history(limit=5):
        if m.embeds and (m.embeds[0].title or "").startswith("🎮 Looking For Group"):
            lfg_msg = m
            break
    lfg_view = B.LFGSquadView()
    await component("button: ✋ Join Squad (fills it)", u2, lfg_view.join_btn.callback, message=lfg_msg)
    lfg_msg = await TEST.fetch_message(lfg_msg.id)
    await component("button: 🚪 Leave Squad (host refused)", u1, lfg_view.leave_btn.callback, message=lfg_msg)
    await component("button: 🚪 Leave Squad", u2, lfg_view.leave_btn.callback, message=lfg_msg)

    # --- tickets (channel create/delete are dry-run; the ticket "channel" is this test channel) ---
    async def fake_create_text_channel(self, name, **kw):
        dry(f"POST /guilds/{{guild}}/channels (create #{name})")
        return TEST
    discord.Guild.create_text_channel = fake_create_text_channel

    async def fake_delete(self, *, reason=None):
        dry(f"DELETE /channels/{self.id} ({reason})")
    discord.abc.GuildChannel.delete = fake_delete
    discord.TextChannel.delete = fake_delete
    await component("button: 🎫 Open Support Ticket", u1, B.TicketPanelButton().callback)
    await component("button: 🔒 Close Ticket", u1, B.TicketCloseButton().callback)

    # --- panels, roles, menus ---
    await slash("rolespanel", admin)
    await slash("faq_post", admin)
    faq = B.FAQPanelView()
    for btn in (faq.btn_download, faq.btn_link, faq.btn_cosmetics, faq.btn_troubleshoot, faq.btn_support):
        await component(f"button: FAQ {btn.label}", u1, btn.callback)

    notif = B.NotificationRolesSelect()
    notif._values = ["🔄 Update Ping", "🎉 Events Ping"]
    await component("select: 🔔 notification roles", u1, notif.callback)
    color = B.ColorSelectMenu()
    color._values = ["🎨 Royal Purple"]
    await component("select: 🎨 color Royal Purple", u1, color.callback)
    color._values = ["RESET"]
    await component("select: 🎨 color reset", u1, color.callback)
    await slash("role_give", admin, user=u1, role=role("🎮 Looking for Group"))
    await slash("role_remove", admin, user=u1, role=role("🎮 Looking for Group"))

    # --- changelog (posts only here, no pings) ---
    async def watcher():
        await B.changelog_watcher.coro()
        assert "9.9.9" in state()["posted_versions"], "watcher did not record 9.9.9"
        return "posted test notes 9.9.9 once and recorded it"
    await step("task changelog_watcher", watcher)
    await slash("postchangelog", admin, version="9.9.9")
    await slash("postchangelog", admin, version="9.9.9", force=True)
    await slash("postchangelog", admin, version="0.0.1")

    async def prefix(content, user=admin):
        anchor = await TEST.send(f"-# (simulated member message) {content}")
        await B.on_message(FakeMessage(user, content, TEST, anchor))
    await step("prefix !postchangelog 9.9.9", lambda: prefix("!postchangelog 9.9.9"))
    await step("prefix !postchangelog (no args)", lambda: prefix("!postchangelog"))
    await step("prefix !postchangelog (no permission)", lambda: prefix("!postchangelog 9.9.9", FakeMember(100000000000000007, "harness-member-np", perms=discord.Permissions.none())))
    await step("prefix !help", lambda: prefix("!help"))

    # --- events ---
    async def member_join():
        await B.on_member_join(FakeMember(100000000000000005, "harness-newcomer"))
    await step("event on_member_join", member_join)

    async def fake_create_voice(self, name, **kw):
        dry(f"POST /guilds/{{guild}}/channels (create voice {name})")
        return FakeVoice(name)
    discord.Guild.create_voice_channel = fake_create_voice

    async def voice_flow():
        u = FakeMember(100000000000000006, "harness-voice")
        await B.on_voice_state_update(u, SimpleNamespace(channel=None),
                                      SimpleNamespace(channel=SimpleNamespace(name="➕ Create Squad", category=None)))
        room = FakeVoice("🎮 harness-voice's Squad")
        await B.on_voice_state_update(u, SimpleNamespace(channel=room), SimpleNamespace(channel=None))
        assert any("DELETE voice channel" in d for d in DRY), "empty squad room not deleted"
    await step("event on_voice_state_update (join-to-create)", voice_flow)

    # --- stats / web ---
    async def version_lookup():
        v = await B.latest_client_version()
        assert v and re.fullmatch(r"\d+\.\d+\.\d+", v), v
        return f"latest client version = {v}"
    await step("latest_client_version (GitHub)", version_lookup)

    async def stats(presences):
        intents = bot._connection._intents
        old = intents.presences
        intents.presences = presences
        try:
            await B.update_server_stats(GUILD)
        finally:
            intents.presences = old
        return "no rename needed" if not DRY else None
    await step("task server_stats_updater (approximate online)", lambda: stats(False))

    async def status_page():
        resp = await B.handle_status_page(None)
        assert resp.status == 200 and "The Lads Discord Bot" in resp.text
        return "status page renders"
    await step("web status page", status_page)


async def cleanup():
    for tid in sorted(ALLOWED_CHANNELS - {TEST.id}):  # discussion threads created in the test channel
        try:
            await bot.http.delete_channel(tid, reason="bot simulation cleanup")
        except Exception as e:
            print(f"could not delete test thread {tid}: {e}")
    pending = [t for t in asyncio.all_tasks() if t is not asyncio.current_task()]
    if pending:
        await asyncio.wait(pending, timeout=20)


async def main():
    async with bot:
        await bot.login(B.BOT_TOKEN)
        install_guard()
        bot.allowed_mentions = discord.AllowedMentions.none()
        await build_guild()
        try:
            await simulate()
        finally:
            await cleanup()
            bad = [r for r in RESULTS if r[1] == "FAIL"]
            lines = [f"{'❌' if s == 'FAIL' else ('🟡' if s == 'PASS*' else '✅')} {n}" + (f" — {d[:150]}" if s == "FAIL" else "")
                     for n, s, d in RESULTS]
            summary = f"## Summary: {len(RESULTS) - len(bad)}/{len(RESULTS)} passed (🟡 = verified up to the API call)\n"
            chunk = summary
            for line in lines:
                if len(chunk) + len(line) + 1 > 1900:
                    await TEST.send(chunk)
                    chunk = ""
                chunk += line + "\n"
            await TEST.send(chunk)
            print(f"\n{len(RESULTS) - len(bad)}/{len(RESULTS)} passed; sandbox state: {SANDBOX / 'state.json'}")


if __name__ == "__main__":
    if sys.stdout.encoding.lower() != "utf-8":
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    asyncio.run(main())
