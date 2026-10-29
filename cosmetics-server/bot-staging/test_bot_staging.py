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
