import os
import sys
from pathlib import Path

# Provide mock token for test environment so importing bot does not exit
os.environ.setdefault("DISCORD_BOT_TOKEN", "mock_dummy_token_for_tests")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import asyncio
import json
import pytest
import discord
from discord import app_commands

import cosmetics
import bot as B


# Sample UUIDs
UUID_A = "069a79f444e94726a5befca90e38aaf5"
UUID_B = "653b8b64e03d40efb0e271542f741c30"


def test_parse_target_input():
    # Discord mentions
    assert B.parse_target_input("<@123456789012345678>") == ("discord_id", "123456789012345678")
    assert B.parse_target_input("<@!123456789012345678>") == ("discord_id", "123456789012345678")

    # Discord raw ID
    assert B.parse_target_input("123456789012345678") == ("discord_id", "123456789012345678")
    assert B.parse_target_input("9876543210987654321") == ("discord_id", "9876543210987654321")

    # Hyphenated UUID
    assert B.parse_target_input("069a79f4-44e9-4726-a5be-fca90e38aaf5") == ("uuid", "069a79f444e94726a5befca90e38aaf5")

    # Plain Hex UUID
    assert B.parse_target_input("069a79f444e94726a5befca90e38aaf5") == ("uuid", "069a79f444e94726a5befca90e38aaf5")

    # Minecraft IGN
    assert B.parse_target_input("Arash_YT") == ("ign", "Arash_YT")
    assert B.parse_target_input("notch") == ("ign", "notch")
    assert B.parse_target_input("Alex_123") == ("ign", "Alex_123")

    # Invalid targets
    assert B.parse_target_input("invalid target with spaces")[0] == "invalid"
    assert B.parse_target_input("toolong_minecraft_username_over_16_chars")[0] == "invalid"
    assert B.parse_target_input("bad!chars")[0] == "invalid"


def test_paginate_items():
    # Empty
    items, total, page = B.paginate_items([])
    assert items == [] and total == 1 and page == 0

    # Under 25 items
    sample10 = [{"id": f"item_{i}"} for i in range(10)]
    items, total, page = B.paginate_items(sample10, page=0, per_page=25)
    assert len(items) == 10
    assert total == 1
    assert page == 0

    # Exactly 25 items
    sample25 = [{"id": f"item_{i}"} for i in range(25)]
    items, total, page = B.paginate_items(sample25, page=0, per_page=25)
    assert len(items) == 25
    assert total == 1
    assert page == 0

    # 60 items (3 pages: 25, 25, 10)
    sample60 = [{"id": f"item_{i}"} for i in range(60)]

    # Page 0
    items, total, page = B.paginate_items(sample60, page=0, per_page=25)
    assert len(items) == 25
    assert total == 3
    assert page == 0
    assert items[0]["id"] == "item_0"
    assert items[-1]["id"] == "item_24"

    # Page 1
    items, total, page = B.paginate_items(sample60, page=1, per_page=25)
    assert len(items) == 25
    assert total == 3
    assert page == 1
    assert items[0]["id"] == "item_25"
    assert items[-1]["id"] == "item_49"

    # Page 2
    items, total, page = B.paginate_items(sample60, page=2, per_page=25)
    assert len(items) == 10
    assert total == 3
    assert page == 2
    assert items[0]["id"] == "item_50"
    assert items[-1]["id"] == "item_59"

    # Clamp upper bound
    items, total, page = B.paginate_items(sample60, page=99, per_page=25)
    assert len(items) == 10
    assert total == 3
    assert page == 2

    # Clamp lower bound
    items, total, page = B.paginate_items(sample60, page=-5, per_page=25)
    assert len(items) == 25
    assert total == 3
    assert page == 0


def test_filter_cosmetics_by_category():
    items = [
        {"id": "lads:cape_aurora", "category": "Capes"},
        {"id": "lads:cape_dragon", "category": "Capes"},
        {"id": "lads:groove", "category": "Emotes"},
        {"id": "lads:wave", "category": "Emotes"},
        {"id": "lads:hat_wizard", "category": "Others"},
    ]

    capes = B.filter_cosmetics_by_category(items, "Capes")
    assert len(capes) == 2
    assert all(c["category"] == "Capes" for c in capes)

    emotes = B.filter_cosmetics_by_category(items, "Emotes")
    assert len(emotes) == 2
    assert all(c["category"] == "Emotes" for c in emotes)

    others = B.filter_cosmetics_by_category(items, "Others")
    assert len(others) == 1
    assert others[0]["id"] == "lads:hat_wizard"

    all_items = B.filter_cosmetics_by_category(items, "All")
    assert len(all_items) == 5

    none_cat = B.filter_cosmetics_by_category(items, None)
    assert len(none_cat) == 5


@pytest.mark.asyncio
async def test_resolve_target_discord_linked():
    linked = {
        "123456789012345678": {
            "uuid": UUID_A,
            "ign": "Arash_YT",
            "linked_at": 1700000000
        }
    }

    # Linked user by mention
    uuid, ign, disc_id, err = await B.resolve_target("<@123456789012345678>", linked)
    assert err is None
    assert uuid == UUID_A
    assert ign == "Arash_YT"
    assert disc_id == "123456789012345678"

    # Linked user by raw discord ID
    uuid, ign, disc_id, err = await B.resolve_target("123456789012345678", linked)
    assert err is None
    assert uuid == UUID_A
    assert ign == "Arash_YT"

    # Unlinked discord user
    uuid, ign, disc_id, err = await B.resolve_target("<@999999999999999999>", linked)
    assert uuid is None
    assert "does not have a linked Minecraft account" in err


@pytest.mark.asyncio
async def test_resolve_target_uuid_and_ign_mocked():
    linked = {
        "123456789012345678": {
            "uuid": UUID_A,
            "ign": "Arash_YT"
        }
    }

    # Direct UUID already in linked_accounts
    uuid, ign, disc_id, err = await B.resolve_target(UUID_A, linked)
    assert err is None
    assert uuid == UUID_A
    assert ign == "Arash_YT"
    assert disc_id == "123456789012345678"

    # Mock Mojang session for an unknown IGN
    class MockResp:
        def __init__(self, status, data):
            self.status = status
            self._data = data

        async def json(self):
            return self._data

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc_val, exc_tb):
            pass

    class MockSession:
        def __init__(self, status=200, data=None):
            self.status = status
            self.data = data or {}

        def get(self, url, **kwargs):
            return MockResp(self.status, self.data)

        async def close(self):
            pass

    # Success lookup for Minecraft IGN
    session_ok = MockSession(200, {"id": UUID_B, "name": "Steve"})
    uuid, ign, disc_id, err = await B.resolve_target("Steve", linked, session=session_ok)
    assert err is None
    assert uuid == UUID_B
    assert ign == "Steve"

    # Not found lookup (404)
    session_404 = MockSession(404, {})
    uuid, ign, disc_id, err = await B.resolve_target("NonExistentUser", linked, session=session_404)
    assert uuid is None
    assert "was not found on Mojang" in err


def test_cosmetics_catalog_source_of_truth():
    """Verify that cosmetics.get_all_cosmetics() contains only the 9 free emotes and no hardcoded capes."""
    items = cosmetics.get_all_cosmetics()
    item_ids = [i["id"] for i in items]

    # Exactly 9 default items (all free emotes)
    assert len(items) == len(cosmetics.FREE)

    # 9 Emotes
    for eid in cosmetics.FREE:
        assert eid in item_ids
        item = next(i for i in items if i["id"] == eid)
        assert item["category"] == "Emotes"
        assert item["free"] is True

    # No hardcoded capes
    for cid in ["lads:cape_aurora", "lads:cape_dragon", "lads:cape_founder"]:
        assert cid not in item_ids

    # Dynamic catalog items merge properly
    merged = cosmetics.get_all_cosmetics({1: "lads:cape_dragon"})
    assert "lads:cape_dragon" in [i["id"] for i in merged]


@pytest.mark.asyncio
async def test_bot_store_all_access_and_grants(tmp_path):
    """Verify CosmeticsStore all-access, grant/revoke, and transactions audit trail."""
    db_file = tmp_path / "bot_test_cosmetics.db"
    store = cosmetics.CosmeticsStore(db_file)

    # 1. User initially has no entitlements (except free emotes)
    assert not await store.has_all_access(UUID_A)
    assert not await store.has_entitlement(UUID_A, "lads:cape_aurora")
    assert not await store.has_entitlement(UUID_A, "lads:future_item_9999")
    assert await store.has_entitlement(UUID_A, "lads:groove")

    # 2. Grant specific item
    await store.grant(UUID_A, "lads:cape_aurora", source="staff:testadmin")
    assert await store.has_entitlement(UUID_A, "lads:cape_aurora")
    assert not await store.has_entitlement(UUID_A, "lads:future_item_9999")

    # Verify audit log in transactions
    with store._get_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT event_type, items_json FROM transactions WHERE uuid = ? ORDER BY rowid DESC;", (UUID_A,))
        row = cur.fetchone()
        assert row[0] == "admin.grant"
        d = json.loads(row[1])
        assert d["item_id"] == "lads:cape_aurora"
        assert d["source"] == "staff:testadmin"

    # 3. Revoke specific item
    await store.revoke(UUID_A, "lads:cape_aurora", actor="staff:testadmin")
    assert not await store.has_entitlement(UUID_A, "lads:cape_aurora")

    # 4. Enable All-Access pass
    changed = await store.set_all_access(UUID_A, True, granted_by="staff:testadmin")
    assert changed is True
    assert await store.has_all_access(UUID_A)

    # All-access unlocks catalog items AND future items
    assert await store.has_entitlement(UUID_A, "lads:cape_aurora")
    assert await store.has_entitlement(UUID_A, "lads:cape_dragon")
    assert await store.has_entitlement(UUID_A, "lads:future_item_9999")
    assert await store.has_entitlement(UUID_A, "lads:unreleased_cosmetic_2028")

    # Details
    details = await store.get_all_access_details(UUID_A)
    assert details is not None
    assert details["granted_by"] == "staff:testadmin"

    # Profile shows all_access=True and includes free items
    prof = await store.get_profile(UUID_A)
    assert prof.get("all_access") is True
    assert "lads:groove" in prof["owned"]

    # When catalog items exist, all-access profile also contains them
    cosmetics._DEFAULT_CATALOG = {1: "lads:cape_aurora"}
    try:
        prof_cat = await store.get_profile(UUID_A)
        assert "lads:cape_aurora" in prof_cat["owned"]
    finally:
        cosmetics._DEFAULT_CATALOG = None

    # 5. Disable All-Access pass
    changed = await store.set_all_access(UUID_A, False, granted_by="staff:testadmin")
    assert changed is True
    assert not await store.has_all_access(UUID_A)
    assert not await store.has_entitlement(UUID_A, "lads:future_item_9999")


def test_cosmetics_group_commands_registered():
    """Verify that /cosmetics group and all 4 subcommands are registered with staff permissions."""
    cmd = B.bot.tree.get_command("cosmetics")
    assert cmd is not None
    assert isinstance(cmd, app_commands.Group)

    subcommands = {c.name: c for c in cmd.commands}
    assert "grant" in subcommands
    assert "revoke" in subcommands
    assert "list" in subcommands
    assert "allaccess" in subcommands

    # Verify manage_guild permission check on each subcommand
    for sub_name, sub_cmd in subcommands.items():
        checks = sub_cmd.checks
        assert len(checks) >= 1, f"Missing permission check on /cosmetics {sub_name}"


def test_cosmetics_select_view_components(tmp_path):
    """Verify that CosmeticsSelectView properly initializes rows and controls."""
    db_file = tmp_path / "view_test.db"
    store = cosmetics.CosmeticsStore(db_file)

    class FakeUser:
        id = 12345
        name = "StaffTester"
        mention = "<@12345>"

    all_items = cosmetics.get_all_cosmetics()

    view = B.CosmeticsSelectView(
        mode="grant",
        target_uuid=UUID_A,
        target_ign="Arash_YT",
        target_discord_id="12345",
        admin_user=FakeUser(),
        items=all_items,
        store=store
    )

    # Row 0: Category select
    # Row 1: Cosmetics multi-select
    # Row 2: Prev, Next, Grant Selected, Grant ALL Current, Cancel buttons
    assert len(view.children) >= 5

    cat_select = next(c for c in view.children if isinstance(c, discord.ui.Select) and c.row == 0)
    assert any(opt.value == "All" for opt in cat_select.options)
    assert any(opt.value == "Capes" for opt in cat_select.options)
    assert any(opt.value == "Emotes" for opt in cat_select.options)

    cosm_select = next(c for c in view.children if isinstance(c, discord.ui.Select) and c.row == 1)
    assert len(cosm_select.options) <= 25

    embed = view.build_embed()
    assert "Arash_YT" in embed.title
    assert "Grant" in embed.title


class MockResponse:
    def __init__(self):
        self.deferred = False
        self.ephemeral = False
        self.messages = []

    async def defer(self, ephemeral=False):
        self.deferred = True
        self.ephemeral = ephemeral

    async def send_message(self, content=None, embed=None, view=None, ephemeral=False):
        self.messages.append({"content": content, "embed": embed, "view": view, "ephemeral": ephemeral})


class MockFollowup:
    def __init__(self):
        self.sent = []

    async def send(self, content=None, embed=None, view=None, ephemeral=False):
        self.sent.append({"content": content, "embed": embed, "view": view, "ephemeral": ephemeral})
        return self


class MockUser:
    def __init__(self, user_id=12345, name="AdminUser"):
        self.id = user_id
        self.name = name
        self.mention = f"<@{user_id}>"


class MockInteraction:
    def __init__(self, user=None):
        self.response = MockResponse()
        self.followup = MockFollowup()
        self.user = user or MockUser()
        self.guild = None


@pytest.mark.asyncio
async def test_slash_cosmetics_grant_all_free_fallback(monkeypatch, tmp_path):
    """When only free emotes exist, /cosmetics grant responds with the required fallback notice."""
    state_file = tmp_path / "state.json"
    state_file.write_text(json.dumps({
        "linked_accounts": {
            "123456789012345678": {"uuid": UUID_A, "ign": "Arash_YT"}
        }
    }), encoding="utf-8")
    monkeypatch.setattr(B, "load_state", lambda: json.loads(state_file.read_text(encoding="utf-8")))

    monkeypatch.setattr(cosmetics, "_DEFAULT_CATALOG", None)
    monkeypatch.delenv("COSMETICS_CATALOG", raising=False)

    interaction = MockInteraction()
    await B.slash_cosmetics_grant.callback(interaction, "Arash_YT")

    assert len(interaction.followup.sent) == 1
    sent = interaction.followup.sent[0]
    assert sent["content"] == "Every cosmetic is free right now; use `/cosmetics allaccess` to pre-unlock future paid ones."
    assert sent["ephemeral"] is True


@pytest.mark.asyncio
async def test_slash_cosmetics_grant_with_paid_items(monkeypatch, tmp_path):
    """When catalog items exist, /cosmetics grant only includes non-free items in the selection view."""
    state_file = tmp_path / "state.json"
    state_file.write_text(json.dumps({
        "linked_accounts": {
            "123456789012345678": {"uuid": UUID_A, "ign": "Arash_YT"}
        }
    }), encoding="utf-8")
    monkeypatch.setattr(B, "load_state", lambda: json.loads(state_file.read_text(encoding="utf-8")))

    db_file = tmp_path / "cosmetics.db"
    store = cosmetics.CosmeticsStore(db_file)
    monkeypatch.setattr(cosmetics, "get_store", lambda: store)

    custom_catalog = {1: "lads:cape_phoenix"}
    monkeypatch.setattr(cosmetics, "_DEFAULT_CATALOG", custom_catalog)

    interaction = MockInteraction()
    await B.slash_cosmetics_grant.callback(interaction, "Arash_YT")

    assert len(interaction.followup.sent) == 1
    sent = interaction.followup.sent[0]
    assert sent["view"] is not None
    assert [item["id"] for item in sent["view"].items] == ["lads:cape_phoenix"]


@pytest.mark.asyncio
async def test_slash_cosmetics_list_free_emotes_format(monkeypatch, tmp_path):
    """Verify that /cosmetics list marks all free emotes as (free)."""
    state_file = tmp_path / "state.json"
    state_file.write_text(json.dumps({
        "linked_accounts": {
            "123456789012345678": {"uuid": UUID_A, "ign": "Arash_YT"}
        }
    }), encoding="utf-8")
    monkeypatch.setattr(B, "load_state", lambda: json.loads(state_file.read_text(encoding="utf-8")))

    db_file = tmp_path / "cosmetics.db"
    store = cosmetics.CosmeticsStore(db_file)
    monkeypatch.setattr(cosmetics, "get_store", lambda: store)

    interaction = MockInteraction()
    await B.slash_cosmetics_list.callback(interaction, "Arash_YT")

    assert len(interaction.followup.sent) == 1
    sent = interaction.followup.sent[0]
    embed = sent["embed"]
    assert embed is not None
    free_field = next(f for f in embed.fields if "Free Emotes" in f.name)
    for emote in cosmetics.FREE:
        name = cosmetics.format_item_name(emote)
        assert f"{name} (free)" in free_field.value

