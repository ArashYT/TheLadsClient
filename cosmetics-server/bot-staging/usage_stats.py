"""
usage_stats.py - Anonymous usage statistics and GitHub release download tracking for The Lads Client.
Provides:
- SQLite storage for anonymous install IDs, first/last seen timestamps, and client versions.
- POST /usage endpoint for launcher heartbeats (rate-limited in memory, no IPs stored).
- GET /usage/summary endpoint returning metrics (numbers only).
- GitHub release download counts (cached 1 hour).
- Slash command /stats for Discord users.
- Server stats channel updater integration ("📥 Downloads: N" and "🟢 Active (7d): N").
"""

import os
import time
import json
import uuid
import sqlite3
import logging
from pathlib import Path
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List, Set

from aiohttp import web, ClientSession, ClientTimeout

logger = logging.getLogger("usage_stats")

# Database setup
DEFAULT_DB_PATH = Path(__file__).resolve().parent / "usage.db"

# Limits & Windows
MAX_BODY_BYTES = 4096
RATE_LIMIT_MAX = 30  # requests per IP
RATE_LIMIT_WINDOW = 600.0  # 10 minutes in seconds
GITHUB_REPO = "ArashYT/TheLadsClient"
GITHUB_CACHE_TTL = 3600.0  # 1 hour

# In-memory IP rate limiter (never persisted, IPs never logged)
_usage_rate_history: Dict[str, List[float]] = {}

# In-memory cache for GitHub downloads
_github_cache: Dict[str, Any] = {
    "total": 0,
    "cached_at": 0.0,
}


def init_db(db_path: Optional[Path] = None) -> sqlite3.Connection:
    """Initialize the SQLite usage database with WAL mode and schema."""
    target_path = db_path or DEFAULT_DB_PATH
    os.makedirs(target_path.parent, exist_ok=True)
    conn = sqlite3.connect(str(target_path))
    conn.row_factory = sqlite3.Row
    with conn:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA synchronous=NORMAL;")
        conn.execute("""
            CREATE TABLE IF NOT EXISTS installs (
                install_id TEXT PRIMARY KEY,
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                launcher_version TEXT NOT NULL,
                client_versions TEXT NOT NULL,
                os TEXT NOT NULL
            );
        """)
        conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_installs_last_seen ON installs(last_seen);
        """)
    return conn


def record_heartbeat(
    install_id: str,
    launcher_version: str,
    client_versions_used: List[str],
    os_name: str,
    timestamp: Optional[int] = None,
    db_path: Optional[Path] = None,
) -> bool:
    """
    Record or update an anonymous launcher heartbeat.
    install_id must be a valid UUID.
    Returns True on success, raises ValueError on invalid install_id.
    """
    clean_id = str(install_id).strip()
    try:
        parsed = uuid.UUID(clean_id)
        normalized_id = str(parsed).lower()
    except (ValueError, AttributeError, TypeError):
        raise ValueError(f"Invalid install_id format: '{clean_id}', must be a valid UUID")

    now = timestamp if timestamp is not None else int(time.time())
    clean_launcher_ver = str(launcher_version or "unknown").strip()[:64]
    clean_os = str(os_name or "windows").strip()[:32]

    # Clean client versions list
    incoming_versions: Set[str] = set()
    for v in client_versions_used:
        if isinstance(v, (str, int, float)):
            s = str(v).strip()[:32]
            if s:
                incoming_versions.add(s)

    conn = init_db(db_path)
    try:
        with conn:
            cur = conn.execute(
                "SELECT client_versions FROM installs WHERE install_id = ?",
                (normalized_id,)
            )
            row = cur.fetchone()
            if row:
                existing_versions: Set[str] = set()
                try:
                    loaded = json.loads(row["client_versions"])
                    if isinstance(loaded, list):
                        existing_versions = set(loaded)
                except Exception:
                    pass
                merged = sorted(existing_versions | incoming_versions)
                conn.execute("""
                    UPDATE installs
                    SET last_seen = ?, launcher_version = ?, client_versions = ?, os = ?
                    WHERE install_id = ?
                """, (now, clean_launcher_ver, json.dumps(merged), clean_os, normalized_id))
            else:
                merged = sorted(incoming_versions)
                conn.execute("""
                    INSERT INTO installs (install_id, first_seen, last_seen, launcher_version, client_versions, os)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (normalized_id, now, now, clean_launcher_ver, json.dumps(merged), clean_os))
        return True
    finally:
        conn.close()


def get_usage_metrics(now_ts: Optional[int] = None, db_path: Optional[Path] = None) -> Dict[str, int]:
    """
    Compute total installs ever and active users for 24h, 7d, and 30d windows.
    """
    now = now_ts if now_ts is not None else int(time.time())
    ts_24h = now - 86400
    ts_7d = now - (7 * 86400)
    ts_30d = now - (30 * 86400)

    conn = init_db(db_path)
    try:
        with conn:
            total_installs = conn.execute("SELECT COUNT(*) FROM installs").fetchone()[0]
            active_24h = conn.execute("SELECT COUNT(*) FROM installs WHERE last_seen >= ?", (ts_24h,)).fetchone()[0]
            active_7d = conn.execute("SELECT COUNT(*) FROM installs WHERE last_seen >= ?", (ts_7d,)).fetchone()[0]
            active_30d = conn.execute("SELECT COUNT(*) FROM installs WHERE last_seen >= ?", (ts_30d,)).fetchone()[0]
        return {
            "total_installs": total_installs,
            "active_24h": active_24h,
            "active_7d": active_7d,
            "active_30d": active_30d,
        }
    finally:
        conn.close()


def is_installer_asset(name: str) -> bool:
    """
    Determines whether a GitHub release asset is an installer/launcher package.
    Counts:
    - Windows installers: TheLadsClient-win-Setup.exe, LadsClient_Installer_*.exe, and any *.exe installer/launcher
    - Portable archives: TheLadsClient-win-Portable.zip (and *.zip packages)
    - Full packages: TheLadsClient-*-full.nupkg
    Excludes:
    - .sig signatures (e.g. lads-update.json.sig)
    - SHA256SUMS.txt
    - nupkg delta files (*-delta.nupkg)
    - Updater manifest / index files (releases.win.json, assets.win.json, lads-update.json, release-build.json, RELEASES)
    - Release documentation (RELEASE_NOTES.md, *.md)
    """
    if not name or not isinstance(name, str):
        return False
    lower = name.lower().strip()

    # Exclusions
    if lower.endswith(".sig"):
        return False
    if lower == "sha256sums.txt" or lower.endswith("sha256sums.txt"):
        return False
    if lower == "releases" or lower.endswith(".json") or lower.endswith(".md"):
        return False
    if "-delta.nupkg" in lower:
        return False

    # Inclusions
    if lower.endswith(".exe"):
        return True
    if lower.endswith(".zip") and ("portable" in lower or "lads" in lower):
        return True
    if lower.endswith("-full.nupkg"):
        return True

    return False


async def fetch_github_downloads(repo: str = GITHUB_REPO, session: Optional[ClientSession] = None) -> int:
    """
    Fetch all GitHub releases for the repo, paginating per_page=100, and sum
    download_count for installer/launcher assets.
    """
    total = 0
    page = 1
    close_session = False
    if session is None:
        session = ClientSession(timeout=ClientTimeout(total=15))
        close_session = True

    try:
        while True:
            url = f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}"
            headers = {
                "User-Agent": "TheLadsClient-UsageStats/1.0",
                "Accept": "application/vnd.github+json",
            }
            async with session.get(url, headers=headers) as resp:
                if resp.status != 200:
                    # Raise so the cached total stays (a rate-limited 403 used to cache a partial count, e.g. 0, for an hour).
                    raise RuntimeError(f"GitHub releases API returned HTTP {resp.status} on page {page}")
                releases = await resp.json()
                if not releases or not isinstance(releases, list):
                    break

                for r in releases:
                    for asset in r.get("assets", []):
                        asset_name = asset.get("name", "")
                        if is_installer_asset(asset_name):
                            total += int(asset.get("download_count", 0))

                if len(releases) < 100:
                    break
                page += 1
        return total
    finally:
        if close_session:
            await session.close()


async def get_cached_github_downloads_async(repo: str = GITHUB_REPO, max_age: float = GITHUB_CACHE_TTL) -> int:
    """
    Get total GitHub downloads with 1-hour in-memory cache.
    Falls back to cached value on network or rate limit errors.
    """
    now = time.time()
    if _github_cache["cached_at"] > 0 and (now - _github_cache["cached_at"]) < max_age:
        return _github_cache["total"]

    try:
        count = await fetch_github_downloads(repo)
        _github_cache["total"] = count
        _github_cache["cached_at"] = now
        return count
    except Exception as e:
        logger.error("Failed to fetch GitHub downloads from %s: %s", repo, e)
        return _github_cache["total"]


try:
    from client_ip import get_client_ip
except ImportError:
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from client_ip import get_client_ip


def check_rate_limit(ip: str, now_ts: Optional[float] = None) -> bool:
    """In-memory sliding window rate limiter per IP. Returns True if allowed."""
    now = now_ts or time.time()
    if len(_usage_rate_history) > 5000:
        stale = [k for k, ts_list in _usage_rate_history.items() if not ts_list or ts_list[-1] < now - RATE_LIMIT_WINDOW]
        for k in stale:
            _usage_rate_history.pop(k, None)

    hits = [t for t in _usage_rate_history.get(ip, []) if t > now - RATE_LIMIT_WINDOW]
    if len(hits) >= RATE_LIMIT_MAX:
        return False
    hits.append(now)
    _usage_rate_history[ip] = hits
    return True


async def handle_usage_heartbeat(request: web.Request) -> web.Response:
    """
    POST /usage
    Accepts:
    {
        "install_id": "guid",
        "launcher_version": "1.0.4",
        "client_versions_used": ["1.8.9"],
        "os": "windows"
    }
    """
    ip = get_client_ip(request)
    if not check_rate_limit(ip):
        return web.json_response(
            {"error": "rate limit exceeded"},
            status=429,
            headers={"Retry-After": "600"}
        )

    # Check content length before reading
    if request.content_length and request.content_length > MAX_BODY_BYTES:
        return web.json_response({"error": "payload too large"}, status=413)

    body = await request.read()
    if len(body) > MAX_BODY_BYTES:
        return web.json_response({"error": "payload too large"}, status=413)

    try:
        data = json.loads(body.decode("utf-8"))
    except Exception:
        return web.json_response({"error": "invalid json"}, status=400)

    if not isinstance(data, dict):
        return web.json_response({"error": "payload must be a JSON object"}, status=400)

    raw_install_id = data.get("install_id")
    if not raw_install_id or not isinstance(raw_install_id, str):
        return web.json_response({"error": "missing or invalid install_id"}, status=400)

    try:
        uuid.UUID(raw_install_id.strip())
    except (ValueError, AttributeError):
        return web.json_response({"error": "install_id must be a valid UUID"}, status=400)

    launcher_version = data.get("launcher_version", "unknown")
    if not isinstance(launcher_version, str):
        launcher_version = str(launcher_version)

    client_versions = data.get("client_versions_used", [])
    if not isinstance(client_versions, list):
        return web.json_response({"error": "client_versions_used must be a list"}, status=400)

    os_name = data.get("os", "windows")
    if not isinstance(os_name, str):
        os_name = str(os_name)

    db_path = request.app.get("usage_db_path", DEFAULT_DB_PATH)
    try:
        record_heartbeat(
            install_id=raw_install_id,
            launcher_version=launcher_version,
            client_versions_used=client_versions,
            os_name=os_name,
            db_path=db_path
        )
        return web.json_response({"ok": True})
    except ValueError as e:
        return web.json_response({"error": str(e)}, status=400)
    except Exception as e:
        logger.error("Failed to record usage heartbeat: %s", e)
        return web.json_response({"error": "internal error"}, status=500)


async def handle_usage_summary(request: web.Request) -> web.Response:
    """
    GET /usage/summary
    Returns JSON metrics with numbers only.
    """
    db_path = request.app.get("usage_db_path", DEFAULT_DB_PATH)
    metrics = get_usage_metrics(db_path=db_path)
    downloads = await get_cached_github_downloads_async()

    summary = {
        "downloads_ever": downloads,
        "github_downloads": downloads,
        "website_downloads": 0,
        "total_installs": metrics["total_installs"],
        "active_24h": metrics["active_24h"],
        "active_7d": metrics["active_7d"],
        "active_30d": metrics["active_30d"],
    }
    return web.json_response(summary)


async def get_stats_targets(stats_cat: Optional[Any] = None) -> Dict[str, str]:
    """
    Returns channel target names for update_server_stats in bot.py.
    Targets match voice channels by key:
      - "Downloads:": "📥 Downloads: N"
      - "Active (7d):": "🟢 Active (7d): N"
    Optionally creates missing voice channels in stats_cat if bot has manage_channels permission.
    """
    try:
        downloads = await get_cached_github_downloads_async()
        metrics = get_usage_metrics()
        active_7d = metrics.get("active_7d", 0)

        targets = {"Active (7d):": f"🟢 Active (7d): {active_7d:,}"}
        if downloads:  # 0 = GitHub not reached yet (rate limited after a restart): keep the channel's last count
            targets["Downloads:"] = f"📥 Downloads: {downloads:,}"

        if stats_cat and hasattr(stats_cat, "voice_channels"):
            existing_names = [ch.name for ch in stats_cat.voice_channels]
            for key, target_name in targets.items():
                if not any(key in name for name in existing_names):
                    try:
                        guild = getattr(stats_cat, "guild", None)
                        if guild and hasattr(guild, "me") and getattr(guild.me.guild_permissions, "manage_channels", False):
                            await stats_cat.create_voice_channel(name=target_name)
                    except Exception as e:
                        logger.debug("Could not auto-create stats channel %s: %s", key, e)
        return targets
    except Exception as e:
        logger.error("Error computing usage stats targets: %s", e)
        return {}


def setup_slash_command(bot: Any) -> None:
    """Register the /stats slash command on the Discord bot tree."""
    if not bot or not hasattr(bot, "tree"):
        return

    import discord

    @bot.tree.command(name="stats", description="View The Lads Client download and active player statistics")
    async def slash_stats(interaction: discord.Interaction):
        await interaction.response.defer()
        downloads = await get_cached_github_downloads_async()
        metrics = get_usage_metrics()

        embed = discord.Embed(
            title="📊 The Lads Client — Statistics",
            color=0xFA364E,
            timestamp=datetime.now(timezone.utc)
        )
        embed.add_field(name="📥 Total Downloads", value=f"**{downloads:,}**" if downloads else "unavailable right now", inline=True)
        embed.add_field(name="👥 Total Installs", value=f"**{metrics['total_installs']:,}**", inline=True)
        embed.add_field(name="\u200b", value="\u200b", inline=True)
        embed.add_field(name="🟢 Active (24h)", value=f"**{metrics['active_24h']:,}**", inline=True)
        embed.add_field(name="🟢 Active (7d)", value=f"**{metrics['active_7d']:,}**", inline=True)
        embed.add_field(name="🟢 Active (30d)", value=f"**{metrics['active_30d']:,}**", inline=True)
        embed.set_footer(text="The Lads Client • Privacy-First Anonymous Stats")
        await interaction.followup.send(embed=embed)


def setup(app: web.Application, bot: Optional[Any] = None, db_path: Optional[Path] = None) -> None:
    """Configure HTTP routes on the aiohttp application and register slash command."""
    if db_path:
        app["usage_db_path"] = db_path
    init_db(db_path)
    app.router.add_post('/usage', handle_usage_heartbeat)
    app.router.add_get('/usage/summary', handle_usage_summary)
    if bot:
        setup_slash_command(bot)
