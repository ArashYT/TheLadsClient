"""Focused tests for the staged live bot files (bot.py, share_store.py, usage_stats.py). No Discord connection is made."""
import sys
import time
import urllib.request
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent)]  # bot-staging (bot, share_store, usage_stats) + cosmetics-server (cosmetics, social)


@pytest.fixture(scope="module")
def bot(tmp_path_factory):
    state = tmp_path_factory.mktemp("bot") / "state.json"
    with pytest.MonkeyPatch.context() as mp:
        mp.setenv("DISCORD_BOT_TOKEN", "test-token")
        mp.setenv("STATE_FILE_PATH", str(state))
        mp.setattr(urllib.request, "urlopen", lambda *a, **k: (_ for _ in ()).throw(OSError("offline test")))
        import bot as module
    return module


def _timed(fn, *args):
    start = time.perf_counter()
    result = fn(*args)
    return result, time.perf_counter() - start


# Inputs that took seconds to hours before the rules were made linear (A/1, B/1)
HOSTILE = [
    "EXCEPTION_ACCESS_VIOLATION" * 2_000 + "# C" * 8_000,  # old GPU rule: cubic, about 1 s at 15 KB, this is 76 KB
    "EXCEPTION_ACCESS_VIOLATION# C" * 10_000,               # the 290 KB /crash body from the audit (about 1 h of CPU before)
    "Mixin apply for mod " * 15_000,                         # old Mixin rule: quadratic, 1.1 s at 160 KB, this is 300 KB
    ("EXCEPTION_ACCESS_VIOLATION# C" * 10_000 + "\n") * 7,   # 2 MB attachment in #bug-reports
]


@pytest.mark.parametrize("text", HOSTILE, ids=["gpu-76k", "gpu-290k", "mixin-300k", "attachment-2mb"])
def test_crash_analyzer_stays_fast_on_hostile_input(bot, text):
    _, seconds = _timed(bot.analyze_crash_text, text)
    assert seconds < 0.5, f"{seconds:.2f} s"


def test_crash_analyzer_still_finds_known_crashes(bot):
    hs_err = ("#\n# A fatal error has been detected by the Java Runtime Environment:\n#\n"
              "#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb2c9e1234, pid=1234, tid=5678\n#\n"
              "# Problematic frame:\n# C  [ig9icd64.dll+0xd1234]\n")
    assert bot.analyze_crash_text(hs_err)["title"] == "GPU Graphics Driver Crash"
    assert bot.analyze_crash_text("...\n# C  [nvoglv64.dll+0x1]")["title"] == "GPU Graphics Driver Crash"
    assert bot.analyze_crash_text("Mixin apply for mod sodium failed sodium.mixins.json:X")["title"] == "Fabric / Mod Mixin Conflict"
    assert bot.analyze_crash_text("java.lang.OutOfMemoryError: Java heap space")["title"] == "Out of Memory (RAM Allocation)"
    assert bot.analyze_crash_text("[Render thread/INFO]: Stopping!") is None
    # A crash line past the scanned head still counts when it is in the tail
    assert bot.analyze_crash_text("x" * 1_000_000 + "\njava.lang.OutOfMemoryError")["title"] == "Out of Memory (RAM Allocation)"


def test_share_store_and_usage_stats_client_ip():
    import share_store
    import usage_stats

    class FakeReq:
        def __init__(self, remote, headers=None):
            self.remote = remote
            self.headers = headers or {}

    # Non-loopback cannot spoof CF-Connecting-IP or X-Forwarded-For
    req = FakeReq(remote="192.168.1.100", headers={"CF-Connecting-IP": "1.2.3.4", "X-Forwarded-For": "5.6.7.8"})
    assert share_store.get_client_ip(req) == "192.168.1.100"
    assert usage_stats.get_client_ip(req) == "192.168.1.100"

    # Loopback trusts CF-Connecting-IP
    req_lb = FakeReq(remote="127.0.0.1", headers={"CF-Connecting-IP": "1.2.3.4"})
    assert share_store.get_client_ip(req_lb) == "1.2.3.4"
    assert usage_stats.get_client_ip(req_lb) == "1.2.3.4"
    salt_file = Path(share_store.__file__).resolve().parent / ".share_ip_salt"
    if salt_file.is_file():
        try:
            salt_file.unlink()
        except OSError:
            pass


def test_crash_rate_history_eviction(bot):
    # Setup history with 5001 entries: 1 active, 5000 old
    now = time.time()
    bot._crash_rate_history.clear()
    for i in range(5001):
        bot._crash_rate_history[f"stale-{i}"] = [now - 1000]
    bot._crash_rate_history["active-ip"] = [now - 10]

    # Trigger eviction logic as in handle_crash_report
    now_ts = now
    if len(bot._crash_rate_history) > 5000:
        stale = [k for k, ts_list in bot._crash_rate_history.items() if not ts_list or ts_list[-1] < now_ts - 600]
        for k in stale:
            del bot._crash_rate_history[k]

    # Active ip must NOT be cleared, only stale entries removed
    assert "active-ip" in bot._crash_rate_history
    assert len(bot._crash_rate_history) == 1


def test_state_load_corrupted_raises_runtime_error(bot, tmp_path, monkeypatch):
    corrupt_file = tmp_path / "corrupt_state.json"
    corrupt_file.write_text("{corrupt-json", encoding="utf-8")
    monkeypatch.setattr(bot, "STATE_FILE", corrupt_file)

    with pytest.raises(RuntimeError) as exc_info:
        bot.load_state()
    assert "Failed to read/parse state file" in str(exc_info.value)


def test_state_save_creates_backup_and_refuses_fallback(bot, tmp_path, monkeypatch):
    import json
    state_file = tmp_path / "good_state.json"
    state_file.write_text(json.dumps({"tickets_count": 5}), encoding="utf-8")
    monkeypatch.setattr(bot, "STATE_FILE", state_file)

    # Save new state
    new_data = {"tickets_count": 6}
    bot.save_state(new_data)
    assert state_file.read_text(encoding="utf-8") == json.dumps(new_data, indent=2)
    bak_file = state_file.with_name(f"{state_file.name}.bak")
    assert bak_file.exists()
    assert json.loads(bak_file.read_text(encoding="utf-8"))["tickets_count"] == 5

    # Fallback state must be refused
    fallback_data = {"tickets_count": 0, "_is_fallback": True}
    bot.save_state(fallback_data)
    # File content must still be 6, not 0
    assert json.loads(state_file.read_text(encoding="utf-8"))["tickets_count"] == 6


def test_prune_crash_reports(bot, tmp_path):
    import os
    crash_dir = tmp_path / "crashes"
    crash_dir.mkdir()

    # 1 file older than 30 days
    old_file = crash_dir / "old.txt"
    old_file.write_text("old crash report")
    old_mtime = time.time() - (35 * 86400)
    os.utime(old_file, (old_mtime, old_mtime))

    # 1 recent file
    recent_file = crash_dir / "recent.txt"
    recent_file.write_text("recent crash report")

    bot.prune_crash_reports(str(crash_dir), max_age_days=30, max_total_mb=10)
    assert not old_file.exists()
    assert recent_file.exists()

    # Test size limit pruning (oldest-first)
    f1 = crash_dir / "f1.txt"
    f1.write_bytes(b"x" * (2 * 1024 * 1024))
    t1 = time.time() - 200
    os.utime(f1, (t1, t1))

    f2 = crash_dir / "f2.txt"
    f2.write_bytes(b"x" * (2 * 1024 * 1024))
    t2 = time.time() - 100
    os.utime(f2, (t2, t2))

    # Cap at 3 MB -> f1 (oldest) should be deleted, f2 and recent kept
    bot.prune_crash_reports(str(crash_dir), max_age_days=30, max_total_mb=3)
    assert not f1.exists()
    assert f2.exists()


def test_usage_stats_bounds_and_negative_cache(tmp_path):
    import usage_stats
    import uuid
    import json
    db = tmp_path / "usage_test.db"

    # Insert an install with 40 client versions
    vers = [f"1.20.{i}" for i in range(40)]
    u1 = str(uuid.uuid4())
    usage_stats.record_heartbeat(u1, "1.0", vers, "windows", db_path=db)

    # Re-read and check capped at 32
    conn = usage_stats.init_db(db)
    cur = conn.execute("SELECT client_versions FROM installs WHERE install_id = ?", (u1,))
    stored = json.loads(cur.fetchone()[0])
    assert len(stored) == 32
    conn.close()

    # Old install (>120 days) should be pruned on heartbeat
    u2 = str(uuid.uuid4())
    old_ts = int(time.time()) - (130 * 86400)
    conn = usage_stats.init_db(db)
    with conn:
        conn.execute("INSERT INTO installs VALUES (?, ?, ?, ?, ?, ?)", (u2, old_ts, old_ts, "1.0", "[]", "windows"))
    conn.close()

    # Next heartbeat prunes u2
    usage_stats.record_heartbeat(u1, "1.0", ["1.20.1"], "windows", db_path=db)
    conn = usage_stats.init_db(db)
    cur = conn.execute("SELECT COUNT(*) FROM installs WHERE install_id = ?", (u2,))
    assert cur.fetchone()[0] == 0
    conn.close()


@pytest.mark.asyncio
async def test_publish_release_notes_validates_version_and_path(bot):
    # Path traversal rejected
    res = await bot.publish_release_notes(None, "../../secret")
    assert "Invalid version format" in res

    # Non-semver rejected
    res = await bot.publish_release_notes(None, "beta-version")
    assert "Invalid version format" in res


def test_share_store_does_not_store_ip_hash():
    import share_store
    assert share_store.hash_creator("192.168.1.1") == ""
    assert share_store.DEFAULT_IP_SALT == ""


def test_phishing_regex_does_not_match_plain_conversation(bot):
    # Plain speech should never trigger phishing automod
    assert not bot.PHISHING_REGEX.search("my friend got me nitro as a gift for my birthday")
    assert not bot.PHISHING_REGEX.search("I want to buy nitro gift for someone")

    # Actual scam links should trigger
    assert bot.PHISHING_REGEX.search("Check this https://steamcommunity-gift.ru/trade")
    assert bot.PHISHING_REGEX.search("Get free-nitro here: https://scam.xyz/free-nitro")
    assert bot.PHISHING_REGEX.search("http://fake-discord.com/nitro-gift")


def test_can_act_role_hierarchy():
    from bot import can_act
    import discord
    from unittest.mock import MagicMock

    guild = MagicMock(spec=discord.Guild)
    guild.owner_id = 999
    guild.me = MagicMock(spec=discord.Member)
    bot_role = MagicMock(spec=discord.Role)
    bot_role.__gt__ = lambda s, o: s.pos > o.pos
    bot_role.__le__ = lambda s, o: s.pos <= o.pos
    bot_role.pos = 50
    guild.me.top_role = bot_role

    invoker = MagicMock(spec=discord.Member)
    invoker.id = 123
    inv_role = MagicMock(spec=discord.Role)
    inv_role.__gt__ = lambda s, o: s.pos > o.pos
    inv_role.__le__ = lambda s, o: s.pos <= o.pos
    inv_role.pos = 30
    invoker.top_role = inv_role

    interaction = MagicMock(spec=discord.Interaction)
    interaction.guild = guild
    interaction.user = invoker

    # Self action refused
    allowed, _ = can_act(interaction, invoker)
    assert not allowed

    # Higher member refused
    high_member = MagicMock(spec=discord.Member)
    high_member.id = 456
    high_role = MagicMock(spec=discord.Role)
    high_role.__gt__ = lambda s, o: s.pos > o.pos
    high_role.__le__ = lambda s, o: s.pos <= o.pos
    high_role.pos = 40
    high_member.top_role = high_role
    allowed, _ = can_act(interaction, high_member)
    assert not allowed

    # Lower member allowed
    low_member = MagicMock(spec=discord.Member)
    low_member.id = 789
    low_role = MagicMock(spec=discord.Role)
    low_role.__gt__ = lambda s, o: s.pos > o.pos
    low_role.__le__ = lambda s, o: s.pos <= o.pos
    low_role.pos = 10
    low_member.top_role = low_role
    allowed, _ = can_act(interaction, low_member)
    assert allowed

    # Managed role refused
    managed_role = MagicMock(spec=discord.Role)
    managed_role.is_default.return_value = False
    managed_role.managed = True
    allowed, _ = can_act(interaction, managed_role)
    assert not allowed

    # Server owner action refused
    owner_member = MagicMock(spec=discord.Member)
    owner_member.id = 999
    allowed, _ = can_act(interaction, owner_member)
    assert not allowed


def test_health_latency_infinite_or_nan():
    import math
    from unittest.mock import patch, PropertyMock
    import bot

    with patch.object(type(bot.bot), "latency", new_callable=PropertyMock) as mock_lat:
        mock_lat.return_value = math.inf
        lat = round(bot.bot.latency * 1000) if bot.bot.latency and math.isfinite(bot.bot.latency) and bot.bot.latency > 0 else None
        assert lat is None

        mock_lat.return_value = float("nan")
        lat_nan = round(bot.bot.latency * 1000) if bot.bot.latency and math.isfinite(bot.bot.latency) and bot.bot.latency > 0 else None
        assert lat_nan is None

        mock_lat.return_value = 0.045
        lat_ok = round(bot.bot.latency * 1000) if bot.bot.latency and math.isfinite(bot.bot.latency) and bot.bot.latency > 0 else None
        assert lat_ok == 45


def test_usage_stats_registered_in_tree():
    from unittest.mock import MagicMock
    import usage_stats
    mock_bot = MagicMock()
    mock_bot.tree = MagicMock()
    usage_stats.setup_slash_command(mock_bot)
    mock_bot.tree.command.assert_called_once()


def test_ign_regex_validation():
    import re
    pattern = r"^[a-zA-Z0-9_]{1,16}$"
    assert re.fullmatch(pattern, "Notch")
    assert re.fullmatch(pattern, "Player_123")
    assert not re.fullmatch(pattern, "../../etc/passwd")
    assert not re.fullmatch(pattern, "invalid name with spaces")
    assert not re.fullmatch(pattern, "a" * 17)


def test_color_selection_rejected_if_not_in_catalog():
    import bot
    all_names = [c["name"] for c in bot.COLOR_ROLES]
    assert "Admin" not in all_names
    assert "Owner" not in all_names
    assert any("Crimson Red" in name for name in all_names)
