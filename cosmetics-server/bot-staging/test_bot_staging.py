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
