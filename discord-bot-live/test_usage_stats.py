"""
Unit tests for usage_stats.py
Tests SQLite storage, time windows, asset classification,
HTTP payload validation (bad GUIDs, oversized bodies, rate limits),
and /usage/summary number outputs.
"""

import os
import json
import uuid
import time
import pytest
from pathlib import Path
from aiohttp import web
from aiohttp.test_utils import AioHTTPTestCase

import usage_stats


@pytest.fixture
def temp_db(tmp_path):
    """Provide an isolated SQLite database path for testing."""
    db_file = tmp_path / "test_usage.db"
    return db_file


def test_record_heartbeat_valid(temp_db):
    test_id = str(uuid.uuid4())
    ts = 1700000000
    ok = usage_stats.record_heartbeat(
        install_id=test_id,
        launcher_version="1.0.4",
        client_versions_used=["1.8.9", "1.20.1"],
        os_name="windows",
        timestamp=ts,
        db_path=temp_db
    )
    assert ok is True

    conn = usage_stats.init_db(temp_db)
    row = conn.execute("SELECT * FROM installs WHERE install_id = ?", (test_id.lower(),)).fetchone()
    conn.close()

    assert row is not None
    assert row["install_id"] == test_id.lower()
    assert row["first_seen"] == ts
    assert row["last_seen"] == ts
    assert row["launcher_version"] == "1.0.4"
    assert row["os"] == "windows"
    assert json.loads(row["client_versions"]) == ["1.20.1", "1.8.9"]


def test_record_heartbeat_rejects_invalid_guid(temp_db):
    for bad in ["not-a-guid", "12345", "", "zzz-yyy"]:
        with pytest.raises(ValueError):
            usage_stats.record_heartbeat(
                install_id=bad,
                launcher_version="1.0.4",
                client_versions_used=["1.8.9"],
                os_name="windows",
                db_path=temp_db
            )


def test_record_heartbeat_merges_client_versions(temp_db):
    test_id = str(uuid.uuid4())
    t1 = 1700000000
    t2 = 1700010000

    usage_stats.record_heartbeat(
        install_id=test_id,
        launcher_version="1.0.4",
        client_versions_used=["1.8.9"],
        os_name="windows",
        timestamp=t1,
        db_path=temp_db
    )

    usage_stats.record_heartbeat(
        install_id=test_id,
        launcher_version="1.0.5",
        client_versions_used=["1.20.1", "1.8.9"],
        os_name="windows",
        timestamp=t2,
        db_path=temp_db
    )

    conn = usage_stats.init_db(temp_db)
    row = conn.execute("SELECT * FROM installs WHERE install_id = ?", (test_id.lower(),)).fetchone()
    conn.close()

    assert row["first_seen"] == t1
    assert row["last_seen"] == t2
    assert row["launcher_version"] == "1.0.5"
    assert json.loads(row["client_versions"]) == ["1.20.1", "1.8.9"]


def test_usage_metrics_windows(temp_db):
    now = 1700000000
    # 1. Active 2 hours ago (< 24h, < 7d, < 30d)
    id1 = str(uuid.uuid4())
    usage_stats.record_heartbeat(id1, "1.0.0", ["1.8.9"], "windows", timestamp=now - 7200, db_path=temp_db)

    # 2. Active 3 days ago (> 24h, < 7d, < 30d)
    id2 = str(uuid.uuid4())
    usage_stats.record_heartbeat(id2, "1.0.0", ["1.8.9"], "windows", timestamp=now - (3 * 86400), db_path=temp_db)

    # 3. Active 15 days ago (> 7d, < 30d)
    id3 = str(uuid.uuid4())
    usage_stats.record_heartbeat(id3, "1.0.0", ["1.8.9"], "windows", timestamp=now - (15 * 86400), db_path=temp_db)

    # 4. Active 45 days ago (> 30d, but counted in total installs)
    id4 = str(uuid.uuid4())
    usage_stats.record_heartbeat(id4, "1.0.0", ["1.8.9"], "windows", timestamp=now - (45 * 86400), db_path=temp_db)

    metrics = usage_stats.get_usage_metrics(now_ts=now, db_path=temp_db)
    assert metrics["total_installs"] == 4
    assert metrics["active_24h"] == 1
    assert metrics["active_7d"] == 2
    assert metrics["active_30d"] == 3


def test_is_installer_asset():
    # Installers and launcher packages count
    assert usage_stats.is_installer_asset("TheLadsClient-win-Setup.exe") is True
    assert usage_stats.is_installer_asset("LadsClient_Installer_BETA_0.14.9.exe") is True
    assert usage_stats.is_installer_asset("TheLadsLauncher.exe") is True
    assert usage_stats.is_installer_asset("TheLadsClient-win-Portable.zip") is True
    assert usage_stats.is_installer_asset("TheLadsClient-1.9.13-full.nupkg") is True

    # Deltas, docs, signatures, updater metadata do NOT count
    assert usage_stats.is_installer_asset("TheLadsClient-1.9.13-delta.nupkg") is False
    assert usage_stats.is_installer_asset("lads-update.json.sig") is False
    assert usage_stats.is_installer_asset("SHA256SUMS.txt") is False
    assert usage_stats.is_installer_asset("releases.win.json") is False
    assert usage_stats.is_installer_asset("assets.win.json") is False
    assert usage_stats.is_installer_asset("release-build.json") is False
    assert usage_stats.is_installer_asset("RELEASE_NOTES.md") is False
    assert usage_stats.is_installer_asset("RELEASES") is False


class TestUsageApi(AioHTTPTestCase):
    async def get_application(self):
        app = web.Application()
        self.test_db = Path(self.temp_dir.name) / "api_test.db"
        usage_stats.setup(app, db_path=self.test_db)
        return app

    def setUp(self):
        import tempfile
        self.temp_dir = tempfile.TemporaryDirectory()
        usage_stats._usage_rate_history.clear()
        super().setUp()

    def tearDown(self):
        super().tearDown()
        self.temp_dir.cleanup()

    async def test_post_usage_success(self):
        payload = {
            "install_id": str(uuid.uuid4()),
            "launcher_version": "1.0.4",
            "client_versions_used": ["1.8.9"],
            "os": "windows"
        }
        resp = await self.client.post("/usage", json=payload)
        assert resp.status == 200
        data = await resp.json()
        assert data.get("ok") is True

    async def test_post_usage_rejects_bad_guid(self):
        payload = {
            "install_id": "not-a-real-guid",
            "launcher_version": "1.0.4",
            "client_versions_used": ["1.8.9"],
            "os": "windows"
        }
        resp = await self.client.post("/usage", json=payload)
        assert resp.status == 400
        data = await resp.json()
        assert "error" in data

    async def test_post_usage_rejects_oversized_body(self):
        huge_string = "x" * (usage_stats.MAX_BODY_BYTES + 100)
        resp = await self.client.post("/usage", data=huge_string.encode("utf-8"), headers={"Content-Type": "application/json"})
        assert resp.status == 413

    async def test_post_usage_rate_limiting(self):
        payload = {
            "install_id": str(uuid.uuid4()),
            "launcher_version": "1.0.4",
            "client_versions_used": ["1.8.9"],
            "os": "windows"
        }
        # Exhaust rate limit
        for _ in range(usage_stats.RATE_LIMIT_MAX):
            resp = await self.client.post("/usage", json=payload)
            assert resp.status == 200

        # Next request must be 429
        resp = await self.client.post("/usage", json=payload)
        assert resp.status == 429
        assert "Retry-After" in resp.headers

    async def test_get_usage_summary_numbers_only(self):
        # Insert a heartbeat first
        test_id = str(uuid.uuid4())
        usage_stats.record_heartbeat(test_id, "1.0.4", ["1.8.9"], "windows", db_path=self.test_db)

        # Mock github cache so test does not hit network
        usage_stats._github_cache["total"] = 302
        usage_stats._github_cache["cached_at"] = time.time()

        resp = await self.client.get("/usage/summary")
        assert resp.status == 200
        data = await resp.json()

        expected_keys = [
            "downloads_ever",
            "github_downloads",
            "website_downloads",
            "total_installs",
            "active_24h",
            "active_7d",
            "active_30d"
        ]
        for key in expected_keys:
            assert key in data
            assert isinstance(data[key], int), f"Field {key} must be an integer, got {type(data[key])}"

        assert data["total_installs"] == 1
        assert data["downloads_ever"] == 302
