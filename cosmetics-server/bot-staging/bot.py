"""
The Lads Discord Bot - Complete All-In-One Community & Gaming Suite
Features:
1. Complete Moderation Suite (/ban, /unban, /kick, /timeout, /untimeout, /warn, /warnings, /clearwarnings, /purge, /slowmode, /lock, /unlock)
2. Advanced AutoMod (Anti-Invite, Anti-Phishing/Scam links, Mass Mention protection, Word Blacklist, Auto-logs to #mod-logs)
3. Dynamic "Join-to-Create" Squad Voice Channels (Auto-creates temporary squad rooms and auto-deletes when empty)
4. Community Leveling & XP System (/rank, /leaderboard, /setxp, balanced RPG curve, role reward at Lvl 5)
5. Custom Tags / Canned Knowledge Base (/tag, /tag_create, /tag_delete, /tag_list with preloaded client guides)
6. Sticky Messages (/sticky_set, /sticky_clear to keep important notices pinned at bottom of chat)
7. Live Server Stats Voice Channels (Auto-updating Members, Online, and Version counters)
8. Interactive Giveaway System (/giveaway_start, /giveaway_end, auto-end at the deadline, live button entries)
9. Support Ticket System (Interactive [Open Ticket] button in #tech-support, private ticket channels, transcripts)
10. Minecraft Client & Tech Tools (/download, /mcstatus, /skin, /cape, Automated Crash Log Analyzer)
11. Interactive Suggestions System (/suggest with live Upvote/Downvote buttons and staff review)
12. Fancy Self-Roles with interactive Dropdown menu in #roles
13. Automated Changelog Watcher (syncs docs/releases to #changelog)
14. Custom Roles & Default Auto-Role on join (👥 Member for users, 🤖 Bots for bots)
15. 10+ Fun & Community Commands (/pvp, /meme, /8ball, /rps, /coinflip, /roll, /rate, /ship, /cat, /dog)
"""

import os
import sys
import shutil
from typing import Any, Optional
import json
import math
import logging
from logging.handlers import RotatingFileHandler

# ------------------ STRUCTURED LOGGING CONFIGURATION ------------------
LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bot_app.log")
try:
    _file_handler = RotatingFileHandler(LOG_FILE, maxBytes=5 * 1024 * 1024, backupCount=5, encoding="utf-8")
    _file_handler.setFormatter(logging.Formatter("[%(asctime)s] [%(levelname)s] %(name)s: %(message)s", datefmt="%Y-%m-%d %H:%M:%S"))
    _root_log = logging.getLogger()
    _root_log.setLevel(logging.INFO)
    _root_log.addHandler(_file_handler)
    for _mod in ("cosmetics", "social", "share_store", "usage_stats", "discord"):
        logging.getLogger(_mod).setLevel(logging.INFO)
except Exception as _log_err:
    print(f"Note: could not initialize file logger: {_log_err}", flush=True)
import base64
import hmac
import hashlib
import time
import re
import random
import asyncio
import traceback
import urllib.request
import urllib.parse
from pathlib import Path
from datetime import datetime, timedelta, timezone
import aiohttp
from aiohttp import web
import discord
from discord import app_commands
from discord.ext import commands, tasks
import cosmetics

# UTF-8 stdout configuration for Windows
if sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
if sys.stderr.encoding.lower() != 'utf-8':
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

def _bot_token():
    # The token lives outside the code: DISCORD_BOT_TOKEN, else .bot_token next to this script (never commit or share it).
    token = os.getenv("DISCORD_BOT_TOKEN") or ((Path(__file__).parent / ".bot_token").read_text().strip() if (Path(__file__).parent / ".bot_token").exists() else "")
    if not token:
        sys.exit("Set DISCORD_BOT_TOKEN or put the bot token in .bot_token next to this script.")
    return token

BOT_TOKEN = _bot_token()
GUILD_ID = int(os.getenv("DISCORD_GUILD_ID", "1556190105419653230"))
RELEASES_DIR_ENV = os.getenv("RELEASES_DIR")
if RELEASES_DIR_ENV:
    RELEASES_DIR = Path(RELEASES_DIR_ENV)
else:
    local_releases = Path(__file__).resolve().parent.parent / "Lads Client" / "docs" / "releases"
    RELEASES_DIR = local_releases if local_releases.exists() else (Path(__file__).parent / "releases")
STATE_FILE = Path(os.getenv("STATE_FILE_PATH", str(Path(__file__).parent / "state.json")))
CLIENT_REPO = "ArashYT/TheLadsClient"  # same repo the launcher's AutoUpdater reads releases from
STATS_CATEGORY = "📊 ━━ SERVER STATS ━━"

NOTIFICATION_ROLES = [
    {
        "name": "📢 Announcements Ping",
        "description": "Pings for major announcements and server updates",
        "emoji": "📢",
        "color": 0xF1C40F
    },
    {
        "name": "🔄 Update Ping",
        "description": "Pings when a new client version or patch notes drop",
        "emoji": "🔄",
        "color": 0x3498DB
    },
    {
        "name": "🧪 Beta Ping",
        "description": "Pings for pre-release builds and experimental features",
        "emoji": "🧪",
        "color": 0x1ABC9C
    },
    {
        "name": "🎉 Events Ping",
        "description": "Pings for game nights, tournaments, and giveaways",
        "emoji": "🎉",
        "color": 0xE91E63
    },
    {
        "name": "💡 Sneak Peeks Ping",
        "description": "Pings for sneak peeks, previews, and devlogs",
        "emoji": "💡",
        "color": 0x9B59B6
    },
    {
        "name": "🎮 Looking for Group",
        "description": "Pings when players are recruiting for Bedwars, Duels, or SMP squads",
        "emoji": "🎮",
        "color": 0x1ABC9C
    }
]

COLOR_ROLES = [
    {"name": "🎨 Crimson Red", "emoji": "🔴", "color": 0xE74C3C, "desc": "Fiery crimson red"},
    {"name": "🎨 Sunset Orange", "emoji": "🟠", "color": 0xE67E22, "desc": "Warm vibrant sunset orange"},
    {"name": "🎨 Golden Amber", "emoji": "🟡", "color": 0xF1C40F, "desc": "Bright golden amber"},
    {"name": "🎨 Emerald Green", "emoji": "🟢", "color": 0x2ECC71, "desc": "Lush emerald green"},
    {"name": "🎨 Electric Cyan", "emoji": "🔵", "color": 0x00D2D3, "desc": "Vivid glowing electric cyan"},
    {"name": "🎨 Sapphire Blue", "emoji": "💎", "color": 0x3498DB, "desc": "Cool deep sapphire blue"},
    {"name": "🎨 Royal Purple", "emoji": "🟣", "color": 0x9B59B6, "desc": "Rich royal amethyst purple"},
    {"name": "🎨 Sakura Pink", "emoji": "🌸", "color": 0xFF7675, "desc": "Aesthetic pastel sakura pink"},
    {"name": "🎨 Platinum Silver", "emoji": "⚪", "color": 0xDFE6E9, "desc": "Sleek metallic platinum silver"},
]

LEVEL_MILESTONES = {
    5: {"name": "✨ Supporter", "emoji": "✨", "desc": "Access to custom colors & supporter badge"},
    10: {"name": "💎 Elite Lad", "emoji": "💎", "desc": "Exclusive squad perks & elite rank"},
    20: {"name": "👑 Legend Lad", "emoji": "👑", "desc": "Prestige community legend status"},
    30: {"name": "🔥 Mythic Lad", "emoji": "🔥", "desc": "Top tier veteran status"}
}

DEFAULT_TAGS = {
    "java": (
        "☕ **Recommended Java Versions for The Lads Client:**\n"
        "• **Minecraft 1.20+ / 26.2 / 26.3:** Requires **Java 17 or Java 21** (64-bit).\n"
        "• **Minecraft 1.8.9:** Best performance with **Java 8 or Java 17**.\n"
        "Download official builds from: https://adoptium.net"
    ),
    "ram": (
        "💾 **How to Allocate More RAM / Memory:**\n"
        "1. Open **The Lads Launcher**.\n"
        "2. Click the **Settings** tab in the top bar.\n"
        "3. Look for the **Allocated Memory / RAM** slider.\n"
        "4. Set it between **3072 MB (3GB)** and **4096 MB (4GB)** for optimal FPS without stuttering!"
    ),
    "fps": (
        "⚡ **The Lads Client FPS Optimization Guide:**\n"
        "• **Render Distance:** Set to 8–12 chunks.\n"
        "• **Simulation Distance:** Set to 6–8 chunks.\n"
        "• **Graphics:** Fast or Fancy (avoid Fabulous on budget GPUs).\n"
        "• **Max Framerate:** Unlimited or matched to your monitor Hz.\n"
        "• **VSync:** Turn OFF for minimum input latency in PvP."
    ),
    "zoom": (
        "🔍 **Lads Smooth Zoom:**\n"
        "• Default Keybind: Press `C` to zoom in smoothly.\n"
        "• Scroll with mouse wheel while zooming to increase/decrease magnification (1.25x to 20x)!\n"
        "• Customize hold vs toggle mode in **Settings → Controls**."
    ),
    "report": (
        "🐛 **How to Report a Bug or Crash:**\n"
        "1. Include your client/launcher version.\n"
        "2. Describe steps to reproduce the issue.\n"
        "3. Upload your `.minecraft/logs/latest.log` or crash report in <#1556193352976826399>.\n"
        "Our automated diagnostic assistant will scan it immediately!"
    ),
    "link": (
        "🎮 **How to Link your Minecraft Account:**\n"
        "• Run `/link ign:<MinecraftUsername> code:<VerificationCode>` in any channel.\n"
        "• The bot verifies your ownership, assigns the **🎮 Linked** role, and syncs your Minecraft avatar & skin!"
    )
}

# ------------------ STATE MANAGEMENT ------------------

def load_state():
    default_state = {
        "posted_versions": [],
        "custom_roles": [],
        "warnings": {},
        "tickets_count": 0,
        "suggestion_votes": {},
        "levels": {},            # user_id -> {"xp": int, "level": int, "messages": int}
        "tags": DEFAULT_TAGS,    # tag_name -> content
        "sticky_messages": {},   # channel_id -> {"text": str, "last_msg_id": int}
        "blacklisted_words": [],
        "giveaways": {},         # message_id -> {"prize": str, "ends_at": iso, "winners": int, "participants": list, "channel_id": int}
        "linked_accounts": {},   # user_id -> {"ign": str, "uuid": str, "linked_at": iso}
        "pending_links": {}      # user_id -> {"ign": str, "uuid": str, "code": str, "requested_at": iso}
    }
    if STATE_FILE.exists():
        last_err = None
        for attempt in range(3):
            try:
                with open(STATE_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    for k, v in default_state.items():
                        if k not in data:
                            data[k] = v
                    return data
            except Exception as e:
                last_err = e
                time.sleep(0.05)
        raise RuntimeError(f"Failed to read/parse state file {STATE_FILE}: {last_err}")
    return default_state

def save_state(state):
    if not isinstance(state, dict):
        print("Error: refusing to save invalid state", flush=True)
        return
    try:
        tmp_file = STATE_FILE.with_name(f"{STATE_FILE.name}.tmp")
        with open(tmp_file, "w", encoding="utf-8") as f:
            json.dump(state, f, indent=2)
        if STATE_FILE.exists():
            bak_file = STATE_FILE.with_name(f"{STATE_FILE.name}.bak")
            try:
                shutil.copy2(STATE_FILE, bak_file)
            except Exception as e:
                print(f"Warning: could not backup state file: {e}", flush=True)
        os.replace(tmp_file, STATE_FILE)
    except Exception as e:
        print(f"Error saving state: {e}", flush=True)


# ------------------ IN-MEMORY TRACKERS ------------------

xp_cooldowns = {}         # user_id -> datetime of last XP grant
temp_voice_squads = set() # channel_ids of dynamic squad rooms


# ------------------ HELPER: MOD LOGGING ------------------

async def log_to_modlogs(guild: discord.Guild, embed: discord.Embed):
    channel = discord.utils.get(guild.text_channels, name="mod-logs")
    if channel:
        try:
            await channel.send(embed=embed)
        except Exception as e:
            print(f"Error logging to #mod-logs: {e}", flush=True)


# ------------------ HELPER: FRIENDLY ERROR REPLIES ------------------

async def reply_with_error(interaction: discord.Interaction, error: Exception):
    """Shared by the slash command tree, views and modals: log the traceback, tell the user privately."""
    if isinstance(error, app_commands.CheckFailure):
        text = "🚫 You don't have permission to use this command."
    else:
        text = "⚠️ Something went wrong while running that. The error was logged for staff; please try again in a moment."
        cmd = getattr(interaction.command, "name", None) or (interaction.data or {}).get("custom_id", "?")
        print(f"[Interaction Error] {cmd}: {error!r}", flush=True)
        traceback.print_exception(type(error), error, error.__traceback__)
    try:
        if interaction.response.is_done():
            await interaction.followup.send(text, ephemeral=True)
        else:
            await interaction.response.send_message(text, ephemeral=True)
    except discord.HTTPException:
        pass


class LadsView(discord.ui.View):
    async def on_error(self, interaction: discord.Interaction, error: Exception, item: discord.ui.Item):
        await reply_with_error(interaction, error)


# ------------------ LEVELING FORMULA ------------------

def xp_for_level(level: int) -> int:
    return 5 * (level ** 2) + (50 * level) + 100


# ------------------ FANCY SELF ROLES (SELECT MENU) ------------------

class NotificationRolesSelect(discord.ui.Select):
    def __init__(self):
        options = [
            discord.SelectOption(
                label=r["name"].split(" ", 1)[1] if " " in r["name"] else r["name"],
                value=r["name"],
                description=r["description"],
                emoji=r["emoji"]
            )
            for r in NOTIFICATION_ROLES
        ]
        super().__init__(
            placeholder="🔔 Click to choose your notification preferences...",
            min_values=0,
            max_values=len(options),
            options=options,
            custom_id="lads_notif_select_menu_v4"
        )

    async def callback(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=True)  # up to 6 role edits can outlast the 3 s reply window
        guild = interaction.guild
        member = interaction.user
        selected_role_names = set(self.values)

        all_role_names = [r["name"] for r in NOTIFICATION_ROLES]
        added = []
        removed = []

        for role_name in all_role_names:
            role = discord.utils.get(guild.roles, name=role_name)
            if not role:
                continue

            if role_name in selected_role_names:
                if role not in member.roles:
                    await member.add_roles(role, reason="Self-roles selection")
                    added.append(role.name)
            else:
                if role in member.roles:
                    await member.remove_roles(role, reason="Self-roles selection")
                    removed.append(role.name)

        # member.roles is the pre-click snapshot, so report what was selected (and exists)
        current_active = [n for n in all_role_names if n in selected_role_names and discord.utils.get(guild.roles, name=n)]
        current_active_text = "\n".join([f"• {name}" for name in current_active]) if current_active else "*None active*"

        changes = []
        if added:
            changes.append(f"**Added:** {', '.join(added)}")
        if removed:
            changes.append(f"**Removed:** {', '.join(removed)}")
        change_summary = "\n".join(changes) if changes else "No changes made."

        embed = discord.Embed(
            title="🔔 Notification Preferences Updated",
            description=f"{change_summary}\n\n**Your Active Alert Roles:**\n{current_active_text}",
            color=0x2ECC71,
            timestamp=datetime.now()
        )
        embed.set_footer(text="You can re-open this menu anytime to change your alerts!")
        await interaction.followup.send(embed=embed, ephemeral=True)


class NotificationRolesView(LadsView):
    def __init__(self):
        super().__init__(timeout=None)
        self.add_item(NotificationRolesSelect())


def build_roles_panel_embed():
    embed = discord.Embed(
        title="🔔 Notification Role Preferences",
        description=(
            "Welcome to **The Lads** alert preferences!\n\n"
            "Use the **interactive dropdown menu below** to choose which notifications you want to receive.\n"
            "You can select multiple roles, or remove any role at any time.\n\n"
            "📢 **Announcements** — Server news, tournaments & major events\n"
            "🔄 **Client Updates** — Release notes, patches & changelogs\n"
            "🧪 **Beta Builds** — Pre-release testing builds & experimental features\n"
            "🎉 **Events & Giveaways** — Community gaming nights & giveaways\n"
            "💡 **Sneak Peeks** — WIP development previews & devlogs\n"
            "🎮 **Looking for Group** — Squad recruitment alerts for Bedwars, Duels & SMP\n\n"
            "*Click the dropdown below to customize your alerts!*"
        ),
        color=0x5865F2
    )
    embed.set_footer(text="The Lads Client • Interactive Roles", icon_url="https://cdn.discordapp.com/embed/avatars/0.png")
    return embed


# ------------------ COSMETIC COLOR ROLES (SELECT MENU) ------------------

class ColorSelectMenu(discord.ui.Select):
    def __init__(self):
        options = [
            discord.SelectOption(
                label=c["name"].replace("🎨 ", ""),
                value=c["name"],
                emoji=c["emoji"],
                description=c["desc"]
            )
            for c in COLOR_ROLES
        ]
        options.append(
            discord.SelectOption(
                label="Reset Color",
                value="RESET",
                emoji="❌",
                description="Remove custom color & revert to default role color"
            )
        )
        super().__init__(
            placeholder="🎨 Choose your custom username color...",
            min_values=1,
            max_values=1,
            options=options,
            custom_id="lads_color_roles_select_v2"
        )

    async def callback(self, interaction: discord.Interaction):
        guild = interaction.guild
        member = interaction.user
        selected = self.values[0]

        all_color_names = [c["name"] for c in COLOR_ROLES]
        if selected != "RESET" and selected not in all_color_names:
            await interaction.response.send_message("\u274c Invalid color selection.", ephemeral=True)
            return
        roles_to_remove = [r for r in member.roles if r.name in all_color_names]
        if roles_to_remove:
            try:
                await member.remove_roles(*roles_to_remove, reason="Changing custom color")
            except Exception as e:
                print(f"Error removing color roles: {e}", flush=True)

        if selected == "RESET":
            embed = discord.Embed(
                title="🎨 Color Reset",
                description="Your custom username color has been removed. You are now using your default role color.",
                color=0x95A5A6
            )
            await interaction.response.send_message(embed=embed, ephemeral=True)
            return

        new_role = discord.utils.get(guild.roles, name=selected)
        if new_role:
            try:
                await member.add_roles(new_role, reason="Custom color role selected")
                embed = discord.Embed(
                    title="🎨 Color Updated!",
                    description=f"Your username color is now set to **{new_role.name}**!",
                    color=new_role.color
                )
                await interaction.response.send_message(embed=embed, ephemeral=True)
            except Exception as e:
                await interaction.response.send_message(f"❌ Failed to set color: {e}", ephemeral=True)
        else:
            await interaction.response.send_message("❌ Color role not found. Please contact staff.", ephemeral=True)


class ColorRolesView(LadsView):
    def __init__(self):
        super().__init__(timeout=None)
        self.add_item(ColorSelectMenu())


def build_colors_panel_embed():
    embed = discord.Embed(
        title="🎨 Custom Username Color Palette",
        description=(
            "Personalize your appearance in chat! 🌈\n\n"
            "Select any aesthetic color from the **dropdown menu below** to change your username color across all channels.\n\n"
            "🔴 **Crimson Red** • 🟠 **Sunset Orange** • 🟡 **Golden Amber**\n"
            "🟢 **Emerald Green** • 🔵 **Electric Cyan** • 💎 **Sapphire Blue**\n"
            "🟣 **Royal Purple** • 🌸 **Sakura Pink** • ⚪ **Platinum Silver**\n\n"
            "*You can change or reset your color at any time!*"
        ),
        color=0x9B59B6
    )
    embed.set_footer(text="The Lads • Aesthetic Color Selector", icon_url="https://cdn.discordapp.com/embed/avatars/0.png")
    return embed


# ------------------ INTERACTIVE FAQ KNOWLEDGE BASE ------------------

class FAQPanelView(LadsView):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Download & Setup", style=discord.ButtonStyle.primary, emoji="🚀", custom_id="lads_faq_download_v2")
    async def btn_download(self, interaction: discord.Interaction, button: discord.ui.Button):
        embed = discord.Embed(
            title="🚀 How to Download & Install The Lads Client",
            description=(
                "**1. Download the Client:**\n"
                "• Head to <#1556193313374077068> or type `/download` to grab the latest release `.jar`.\n\n"
                "**2. Prerequisites:**\n"
                "• Minecraft 1.21.1 with **Fabric Loader** installed.\n"
                "• **Java 21 (64-bit)** is required (if you use the official The Lads Launcher, Java is bundled automatically!).\n\n"
                "**3. Manual Installation:**\n"
                "• Press `Win + R`, type `%appdata%\\.minecraft`, and hit Enter.\n"
                "• Open or create the `mods` folder.\n"
                "• Drop the downloaded `lads-client-vX.X.X.jar` and `fabric-api.jar` into `mods`.\n"
                "• Launch Minecraft using your Fabric 1.21.1 installation!\n\n"
                "💡 *Need instant links? Type `/download` in any bot channel!*"
            ),
            color=0x3498DB
        )
        embed.set_thumbnail(url="https://mc-heads.net/avatar/MHF_Chest/100")
        await interaction.response.send_message(embed=embed, ephemeral=True)

    @discord.ui.button(label="Link MC Account", style=discord.ButtonStyle.success, emoji="🎮", custom_id="lads_faq_link_v2")
    async def btn_link(self, interaction: discord.Interaction, button: discord.ui.Button):
        embed = discord.Embed(
            title="🎮 Linking Your Minecraft Account",
            description=(
                "Link your Minecraft IGN to unlock full community integration & stats!\n\n"
                "**How to Link:**\n"
                "• Go to <#1556193342277034054> and run `/link <your_in_game_name>`\n"
                "• The bot instantly checks your IGN with Mojang's authentication API.\n\n"
                "**What you unlock:**\n"
                "• **`🎮 Linked` Role** with member verification.\n"
                "• Automatic nickname synchronization to your real IGN.\n"
                "• Personalized `/skin` and `/cape` viewers.\n"
                "• In-depth `/profile` and `/whois` lookups showing your 3D player render & UUID.\n"
                "• Your IGN displayed on `/rank` and `/leaderboard` cards!\n\n"
                "💡 *Want to unlink or switch accounts? Simply run `/unlink`.*"
            ),
            color=0x2ECC71
        )
        embed.set_thumbnail(url="https://mc-heads.net/avatar/Steve/100")
        await interaction.response.send_message(embed=embed, ephemeral=True)

    @discord.ui.button(label="Capes & Cosmetics", style=discord.ButtonStyle.secondary, emoji="✨", custom_id="lads_faq_cosmetics_v2")
    async def btn_cosmetics(self, interaction: discord.Interaction, button: discord.ui.Button):
        embed = discord.Embed(
            title="✨ Custom Capes & Client Cosmetics",
            description=(
                "**Viewing Capes:**\n"
                "• Run `/cape [username]` to preview any player's Mojang or OptiFine cape in high definition, complete with a direct texture download link!\n\n"
                "**Viewing Skins:**\n"
                "• Run `/skin [username]` to view 3D front & back HD skin renders.\n\n"
                "**In-Game Cosmetics:**\n"
                "• Press `Right Shift` in-game to open the Lads Client configuration menu.\n"
                "• Navigate to the **Cosmetics** tab to toggle wings, halos, and custom nametags.\n\n"
                "📸 *Want to showcase your setup? Post your screenshots in <#1556339107234316368>!*"
            ),
            color=0xE91E63
        )
        embed.set_thumbnail(url="https://mc-heads.net/avatar/Alex/100")
        await interaction.response.send_message(embed=embed, ephemeral=True)

    @discord.ui.button(label="Fix Crashes & Lag", style=discord.ButtonStyle.danger, emoji="🛠️", custom_id="lads_faq_troubleshoot_v2")
    async def btn_troubleshoot(self, interaction: discord.Interaction, button: discord.ui.Button):
        embed = discord.Embed(
            title="🛠️ Troubleshooting Crashes & Lag",
            description=(
                "Follow these proven solutions to fix 99% of common issues:\n\n"
                "**1. Out Of Memory / Java Heap Space:**\n"
                "• Open Minecraft Launcher ➔ `Installations` ➔ click `...` on Fabric ➔ `Edit`.\n"
                "• Click `More Options` at bottom ➔ in JVM arguments, change `-Xmx2G` to `-Xmx4G`.\n\n"
                "**2. Low FPS / Stuttering:**\n"
                "• Ensure Minecraft is running on your dedicated graphics card (NVIDIA / AMD) instead of Intel Integrated Graphics.\n"
                "• Turn off VSync and set max framerate to match your monitor refresh rate.\n\n"
                "**3. Automated Crash Log Analyzer:**\n"
                "• Upload your `.minecraft/logs/latest.log` or `crash-reports` file in <#1556193352976826399>.\n"
                "• Our automated bot assistant will instantly parse the log and tell you the exact cause and fix!\n\n"
                "🎫 *Still stuck? Open a ticket in <#1556193359394250752>.*"
            ),
            color=0xE74C3C
        )
        embed.set_thumbnail(url="https://mc-heads.net/avatar/MHF_TNT/100")
        await interaction.response.send_message(embed=embed, ephemeral=True)

    @discord.ui.button(label="Rules & Support", style=discord.ButtonStyle.secondary, emoji="❓", custom_id="lads_faq_support_v2")
    async def btn_support(self, interaction: discord.Interaction, button: discord.ui.Button):
        embed = discord.Embed(
            title="❓ Server Rules & Community Support",
            description=(
                "**Community Rules:**\n"
                "• Please review <#1556193292595634259> to keep our community welcoming and fair.\n\n"
                "**Need Staff Help?**\n"
                "• Open a private support ticket in <#1556193359394250752> by clicking **[🎫 Open Support Ticket]**.\n"
                "• Our staff team will respond directly in your private channel.\n\n"
                "**Suggestions:**\n"
                "• Have an idea for a new feature? Use `/suggest` in <#1556193347390144562>.\n\n"
                "**Looking for a Team?**\n"
                "• Visit <#1556339111592206428> or type `/lfg` to recruit teammates for Bedwars or Duels!"
            ),
            color=0xF1C40F
        )
        embed.set_thumbnail(url="https://mc-heads.net/avatar/MHF_Question/100")
        await interaction.response.send_message(embed=embed, ephemeral=True)


def build_faq_panel_embed():
    embed = discord.Embed(
        title="📚 The Lads Community & Client Knowledge Base",
        description=(
            "Welcome to the official **The Lads** Knowledge Base! 🌟\n\n"
            "Need help getting started, fixing an error, or linking your account?\n"
            "**Click any button below** to view detailed instructions instantly without waiting for staff!\n\n"
            "🚀 **Download & Setup** — How to install Lads Client & Java prerequisites\n"
            "🎮 **Link Minecraft Account** — Verify your IGN, sync skins & unlock profile perks\n"
            "✨ **Capes & Cosmetics** — How to equip & view custom capes and client cosmetics\n"
            "🛠️ **Fix Crashes & Lag** — Memory allocation, FPS boost & log troubleshooting\n"
            "❓ **Rules & Support** — Discord rules, ticket assistance & finding squads\n\n"
            "*Responses are shown privately to you so this channel stays 100% clean.*"
        ),
        color=0x3498DB
    )
    embed.set_thumbnail(url="https://mc-heads.net/avatar/MHF_Chest/100")
    embed.set_footer(text="The Lads FAQ • Click a button below for instant answers", icon_url="https://cdn.discordapp.com/embed/avatars/0.png")
    return embed


# ------------------ LOOKING FOR GROUP (LFG) SQUAD VIEW ------------------

class LFGSquadView(LadsView):
    """Persistent and stateless: the squad is read back from the post's embed, so the buttons keep working after a bot restart."""
    def __init__(self):
        super().__init__(timeout=None)

    @staticmethod
    def read_squad(message: discord.Message):
        fields = {f.name: f.value for f in message.embeds[0].fields}
        squad_text = fields.get("🛡️ Current Squad", "")
        squad = [int(x) for x in re.findall(r"<@!?(\d+)>", squad_text)]
        host = re.search(r"<@!?(\d+)> \*\(Host\)\*", squad_text)
        total = re.search(r"/ (\d+)", fields.get("👥 Squad Status", ""))
        host_id = int(host.group(1)) if host else (squad[0] if squad else 0)
        needed = int(total.group(1)) - 1 if total else 1
        return host_id, fields.get("🎮 Game", "").strip("*"), needed, fields.get("📝 Notes"), squad

    @discord.ui.button(label="Join Squad", style=discord.ButtonStyle.success, emoji="✋", custom_id="lfg_join_btn")
    async def join_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        host_id, game, needed, notes, squad = self.read_squad(interaction.message)
        uid = interaction.user.id
        if uid in squad:
            await interaction.response.send_message("❌ You are already in this squad!", ephemeral=True)
            return
        if len(squad) - 1 >= needed:
            await interaction.response.send_message("❌ This squad is already full!", ephemeral=True)
            return

        squad.append(uid)
        await self.update_embed(interaction, host_id, game, needed, notes, squad)

    @discord.ui.button(label="Leave Squad", style=discord.ButtonStyle.secondary, emoji="🚪", custom_id="lfg_leave_btn")
    async def leave_btn(self, interaction: discord.Interaction, button: discord.ui.Button):
        host_id, game, needed, notes, squad = self.read_squad(interaction.message)
        uid = interaction.user.id
        if uid == host_id:
            await interaction.response.send_message("❌ As the host, you cannot leave your own squad. To cancel, you may delete this post.", ephemeral=True)
            return
        if uid not in squad:
            await interaction.response.send_message("❌ You are not in this squad.", ephemeral=True)
            return

        squad.remove(uid)
        await self.update_embed(interaction, host_id, game, needed, notes, squad)

    async def update_embed(self, interaction: discord.Interaction, host_id: int, game: str, needed: int, notes, squad: list):
        total_slots = 1 + needed
        current_count = len(squad)
        remaining = max(0, total_slots - current_count)
        is_full = remaining == 0

        slots_vis = "🟩 " * current_count + "⬜ " * remaining
        squad_mentions = "\n".join([f"• <@{m_id}>" + (" *(Host)*" if m_id == host_id else "") for m_id in squad])

        embed = interaction.message.embeds[0]
        embed.clear_fields()
        embed.add_field(name="🎮 Game", value=f"**{game}**", inline=True)
        embed.add_field(name="👥 Squad Status", value=f"**{current_count} / {total_slots}**\n{slots_vis}", inline=True)
        if notes:
            embed.add_field(name="📝 Notes", value=notes, inline=False)
        embed.add_field(name="🛡️ Current Squad", value=squad_mentions, inline=False)

        embed.color = 0x2ECC71 if is_full else 0x3498DB
        embed.title = f"🎮 Squad Full! — {game}" if is_full else f"🎮 Looking For Group — {game}"
        self.join_btn.disabled = is_full
        await interaction.response.edit_message(embed=embed, view=self)
        if is_full:
            ping_members = " ".join([f"<@{m}>" for m in squad])
            await interaction.followup.send(f"🚀 **Squad is READY!** {ping_members} — Jump into <#1556193374640541877>!")


# ------------------ SUPPORT TICKET SYSTEM ------------------

class TicketPanelButton(discord.ui.Button):
    def __init__(self):
        super().__init__(
            label="Open Support Ticket",
            style=discord.ButtonStyle.primary,
            emoji="🎫",
            custom_id="lads_open_ticket_btn_v2"
        )

    async def callback(self, interaction: discord.Interaction):
        guild = interaction.guild
        member = interaction.user

        # Match on the topic's member ID: the channel name is truncated to 15 chars, so a name lookup missed long usernames
        existing_ch = next((c for c in guild.text_channels if c.topic and f"(ID: {member.id})" in c.topic), None)
        if existing_ch:
            await interaction.response.send_message(f"❌ You already have an open ticket in {existing_ch.mention}!", ephemeral=True)
            return
        await interaction.response.defer(ephemeral=True, thinking=True)

        cat_tickets = discord.utils.get(guild.categories, name="🎫 ━━ TICKETS ━━")

        overwrites = {
            guild.default_role: discord.PermissionOverwrite(view_channel=False),
            member: discord.PermissionOverwrite(view_channel=True, send_messages=True, attach_files=True, embed_links=True)
        }

        for r_name in ["👑 Owner", "🛡️ Admin", "🛡️ Staff", "🔨 Moderator", "🎧 Helper"]:
            role = discord.utils.get(guild.roles, name=r_name)
            if role:
                overwrites[role] = discord.PermissionOverwrite(view_channel=True, send_messages=True, manage_messages=True)

        state = load_state()
        state["tickets_count"] = state.get("tickets_count", 0) + 1
        ticket_num = state["tickets_count"]
        save_state(state)

        ch_name = f"ticket-{member.name.lower()[:15]}"
        ticket_ch = await guild.create_text_channel(
            name=ch_name,
            category=cat_tickets,
            overwrites=overwrites,
            topic=f"Support ticket for {member.name} (ID: {member.id}) | Ticket #{ticket_num:04d}"
        )

        embed = discord.Embed(
            title=f"🎫 Support Ticket #{ticket_num:04d}",
            description=(
                f"Hello {member.mention}, welcome to your support ticket!\n\n"
                "**Please describe your issue in detail:**\n"
                "• What happened?\n"
                "• Which game/launcher version are you on?\n"
                "• If it's a crash, please upload your crash log or screenshot.\n\n"
                "Our staff team will assist you shortly!"
            ),
            color=0x3498DB,
            timestamp=datetime.now()
        )
        embed.set_footer(text="Click the button below when your issue is resolved.")

        view = TicketControlView()
        await ticket_ch.send(content=f"{member.mention} Welcome!", embed=embed, view=view)
        await interaction.followup.send(f"✅ Created ticket channel: {ticket_ch.mention}", ephemeral=True)

        log_embed = discord.Embed(
            title="🎫 Ticket Created",
            description=f"**User:** {member.mention} (`{member.id}`)\n**Channel:** {ticket_ch.mention}\n**Ticket:** #{ticket_num:04d}",
            color=0x3498DB,
            timestamp=datetime.now()
        )
        await log_to_modlogs(guild, log_embed)


class TicketPanelView(LadsView):
    def __init__(self):
        super().__init__(timeout=None)
        self.add_item(TicketPanelButton())


class TicketCloseButton(discord.ui.Button):
    def __init__(self):
        super().__init__(
            label="Close Ticket",
            style=discord.ButtonStyle.danger,
            emoji="🔒",
            custom_id="lads_close_ticket_btn_v2"
        )

    async def callback(self, interaction: discord.Interaction):
        channel = interaction.channel
        guild = interaction.guild
        member = interaction.user

        await interaction.response.send_message("🔒 Closing ticket and generating transcript in 5 seconds...")

        lines = []
        async for msg in channel.history(limit=150, oldest_first=True):
            time_str = msg.created_at.strftime("%Y-%m-%d %H:%M:%S")
            lines.append(f"[{time_str}] {msg.author.name}: {msg.content}")

        transcript_text = "\n".join(lines)
        if len(transcript_text) > 3000:
            transcript_text = transcript_text[-3000:]

        log_embed = discord.Embed(
            title=f"🔒 Ticket Closed: #{channel.name}",
            description=f"**Closed By:** {member.mention} (`{member.id}`)\n**Channel:** #{channel.name}",
            color=0xE74C3C,
            timestamp=datetime.now()
        )
        if transcript_text:
            log_embed.add_field(name="Recent Transcript Snippet", value=f"```text\n{transcript_text[:1000]}\n```", inline=False)

        await log_to_modlogs(guild, log_embed)
        await asyncio.sleep(5)
        await channel.delete(reason=f"Ticket closed by {member.name}")


class TicketControlView(LadsView):
    def __init__(self):
        super().__init__(timeout=None)
        self.add_item(TicketCloseButton())


def build_ticket_panel_embed():
    embed = discord.Embed(
        title="🎫 The Lads Client Support Center",
        description=(
            "Need help with **The Lads Client**, installing mods, or having launcher crashes?\n\n"
            "Click **[ Open Support Ticket ]** below to create a private support room with our staff team.\n\n"
            "**Before opening a ticket:**\n"
            "• Check <#1556193319921520750> for common questions\n"
            "• Make sure you have your crash log ready if reporting a crash\n"
            "• Please be patient after opening your ticket!"
        ),
        color=0x3498DB
    )
    embed.set_footer(text="The Lads Support • 1-on-1 Assistance")
    return embed


# ------------------ SUGGESTIONS SYSTEM ------------------

def build_suggestion_panel_embed():
    embed = discord.Embed(
        title="💡 The Lads — Mod & Feature Suggestion Center",
        description=(
            "Have a mod, feature, or gameplay mechanic you want to see in **The Lads Client / Modpack**?\n\n"
            "Click **[ 💡 Submit Mod Suggestion ]** below to open the suggestion form!\n\n"
            "### 📋 What you will be asked to provide:\n"
            "• **Mod / Feature Name** — Name of the mod\n"
            "• **Link to Page** — CurseForge, Modrinth, or GitHub link\n"
            "• **What You Like Specifically** — Key gameplay mechanics and why it fits\n"
            "• **Version / Loader (Optional)** — Fabric, NeoForge, or config notes\n\n"
            "Every suggestion gets community voting (👍 / 👎) and its own discussion thread!"
        ),
        color=0xF1C40F
    )
    embed.set_footer(text="The Lads Community • Help Shape Our Modpack & Client")
    return embed


class SuggestionPanelButton(discord.ui.Button):
    def __init__(self):
        super().__init__(
            label="Submit Mod Suggestion",
            style=discord.ButtonStyle.success,
            emoji="💡",
            custom_id="lads_open_suggestion_modal_btn_v1"
        )

    async def callback(self, interaction: discord.Interaction):
        await interaction.response.send_modal(SuggestionModal())


class SuggestionPanelView(LadsView):
    def __init__(self):
        super().__init__(timeout=None)
        self.add_item(SuggestionPanelButton())


class SuggestionModal(discord.ui.Modal, title="💡 Submit Mod Suggestion"):
    mod_name = discord.ui.TextInput(
        label="Mod / Feature Name",
        placeholder="e.g. Create Mod, Iris Shaders, Waystones, Sodium",
        min_length=2,
        max_length=100,
        required=True
    )
    mod_link = discord.ui.TextInput(
        label="Link to Mod Page",
        placeholder="CurseForge, Modrinth, or GitHub URL",
        min_length=3,
        max_length=300,
        required=True
    )
    what_i_like = discord.ui.TextInput(
        label="What do you like specifically about it?",
        style=discord.TextStyle.paragraph,
        placeholder="Describe what makes this mod awesome, key gameplay mechanics, and why it fits The Lads...",
        min_length=10,
        max_length=1500,
        required=True
    )
    notes = discord.ui.TextInput(
        label="Version / Loader / Notes (Optional)",
        placeholder="e.g. Fabric 1.20.1, NeoForge, performance tweaks, config notes",
        max_length=200,
        required=False
    )

    async def on_error(self, interaction: discord.Interaction, error: Exception):
        await reply_with_error(interaction, error)

    async def on_submit(self, interaction: discord.Interaction):
        guild = interaction.guild
        sugg_channel = discord.utils.get(guild.text_channels, name="suggestions")
        if not sugg_channel:
            await interaction.response.send_message("❌ Suggestions channel not found.", ephemeral=True)
            return
        await interaction.response.defer(ephemeral=True, thinking=True)  # post + thread + thread message can outlast 3 s

        state = load_state()
        state["suggestions_count"] = state.get("suggestions_count", 0) + 1
        sugg_num = state["suggestions_count"]

        mod_name_val = self.mod_name.value.strip()
        link_val = self.mod_link.value.strip()
        what_val = self.what_i_like.value.strip()
        notes_val = self.notes.value.strip()

        if link_val.startswith("http://") or link_val.startswith("https://"):
            formatted_link = f"[{link_val}]({link_val})"
        else:
            formatted_link = link_val

        embed = discord.Embed(
            title=f"💡 Mod Suggestion: {mod_name_val}",
            color=0xF1C40F,
            timestamp=datetime.now()
        )
        embed.set_author(name=interaction.user.name, icon_url=interaction.user.display_avatar.url)
        embed.add_field(name="🔗 Mod Page", value=formatted_link, inline=False)
        embed.add_field(name="❤️ What I Like Specifically", value=what_val, inline=False)
        if notes_val:
            embed.add_field(name="⚙️ Version & Notes", value=notes_val, inline=False)
        embed.add_field(name="Status", value="⏳ **Pending Review**", inline=False)
        embed.set_footer(text=f"Suggestion #{sugg_num:04d} • Upvote below if you want this added!")

        view = SuggestionVoteView(upvotes=0, downvotes=0)
        msg = await sugg_channel.send(embed=embed, view=view)

        state.setdefault("suggestion_votes", {})[str(msg.id)] = {
            "sugg_num": sugg_num,
            "up": [],
            "down": [],
            "mod_name": mod_name_val,
            "mod_link": link_val,
            "author_id": interaction.user.id,
            "channel_id": msg.channel.id
        }
        save_state(state)

        # Automatically create discussion thread
        thread = None
        try:
            thread_title = f"💬 Discuss: {mod_name_val}"[:95]
            thread = await msg.create_thread(name=thread_title, auto_archive_duration=1440)
            await thread.send(f"Discuss **{mod_name_val}** suggested by {interaction.user.mention} here! Give feedback, share config ideas, or point out compatibility issues.")
        except Exception as e:
            print(f"Failed to create suggestion thread: {e}", flush=True)

        thread_mention = f" and thread {thread.mention}" if thread else ""
        await interaction.followup.send(
            f"✅ Your suggestion for **{mod_name_val}** has been posted in {sugg_channel.mention}{thread_mention}!",
            ephemeral=True
        )

        log_embed = discord.Embed(
            title="💡 New Suggestion Submitted",
            description=(
                f"**User:** {interaction.user.mention} (`{interaction.user.id}`)\n"
                f"**Mod Name:** {mod_name_val}\n"
                f"**Link:** {link_val}\n"
                f"**Message:** [Jump to Suggestion]({msg.jump_url})"
            ),
            color=0xF1C40F,
            timestamp=datetime.now()
        )
        await log_to_modlogs(guild, log_embed)


class SuggestionVoteView(LadsView):
    def __init__(self, upvotes: int = 0, downvotes: int = 0):
        super().__init__(timeout=None)
        self.up_btn = discord.ui.Button(
            label=f"👍 Upvote ({upvotes})",
            style=discord.ButtonStyle.success,
            custom_id="lads_sugg_upvote_btn_v1"
        )
        self.up_btn.callback = self.upvote_callback

        self.down_btn = discord.ui.Button(
            label=f"👎 Downvote ({downvotes})",
            style=discord.ButtonStyle.secondary,
            custom_id="lads_sugg_downvote_btn_v1"
        )
        self.down_btn.callback = self.downvote_callback

        self.add_item(self.up_btn)
        self.add_item(self.down_btn)

    async def upvote_callback(self, interaction: discord.Interaction):
        await self.handle_vote(interaction, vote_type="up")

    async def downvote_callback(self, interaction: discord.Interaction):
        await self.handle_vote(interaction, vote_type="down")

    async def handle_vote(self, interaction: discord.Interaction, vote_type: str):
        msg = interaction.message
        msg_id = str(msg.id)
        state = load_state()
        votes = state.setdefault("suggestion_votes", {}).setdefault(msg_id, {"up": [], "down": []})

        user_id = interaction.user.id
        up_list = set(votes.get("up", []))
        down_list = set(votes.get("down", []))

        if vote_type == "up":
            if user_id in up_list:
                up_list.remove(user_id)
            else:
                up_list.add(user_id)
                down_list.discard(user_id)
        else:
            if user_id in down_list:
                down_list.remove(user_id)
            else:
                down_list.add(user_id)
                up_list.discard(user_id)

        votes["up"] = list(up_list)
        votes["down"] = list(down_list)
        state["suggestion_votes"][msg_id] = votes
        save_state(state)

        self.up_btn.label = f"👍 Upvote ({len(up_list)})"
        self.down_btn.label = f"👎 Downvote ({len(down_list)})"
        await interaction.response.edit_message(view=self)



# ------------------ GIVEAWAY SYSTEM (INTERACTIVE BUTTON) ------------------

class GiveawayButton(discord.ui.DynamicItem[discord.ui.Button], template=r"giveaway_enter_(?P<msg_id>\d+)"):
    """Dynamic item (registered in setup_hook), so every giveaway's button keeps working after a bot restart."""
    def __init__(self, message_id: str, count: int = 0):
        super().__init__(discord.ui.Button(
            label=f"🎉 Enter Giveaway ({count})",
            style=discord.ButtonStyle.success,
            custom_id=f"giveaway_enter_{message_id}"
        ))
        self.msg_id = str(message_id)

    @classmethod
    async def from_custom_id(cls, interaction: discord.Interaction, item: discord.ui.Button, match: re.Match):
        giveaway = load_state().get("giveaways", {}).get(match["msg_id"], {})
        return cls(match["msg_id"], len(giveaway.get("participants", [])))

    async def callback(self, interaction: discord.Interaction):
        state = load_state()
        giveaway = state.get("giveaways", {}).get(str(self.msg_id))
        if not giveaway:
            await interaction.response.send_message("❌ This giveaway has ended.", ephemeral=True)
            return

        participants = set(giveaway.get("participants", []))
        user_id = interaction.user.id

        if user_id in participants:
            participants.remove(user_id)
            msg = "👋 You have left the giveaway."
        else:
            participants.add(user_id)
            msg = "🎉 You have entered the giveaway! Good luck!"

        giveaway["participants"] = list(participants)
        state.setdefault("giveaways", {})[str(self.msg_id)] = giveaway
        save_state(state)

        self.item.label = f"🎉 Enter Giveaway ({len(participants)})"
        await interaction.response.edit_message(view=self.view)
        await interaction.followup.send(msg, ephemeral=True)


class GiveawayView(LadsView):
    def __init__(self, message_id: str, count: int = 0):
        super().__init__(timeout=None)
        self.add_item(GiveawayButton(message_id, count))


async def end_giveaway(message_id: str):
    """Draw winners, announce them and drop the giveaway from state. Returns False if it was already gone."""
    state = load_state()
    giveaway = state.get("giveaways", {}).pop(str(message_id), None)
    if not giveaway:
        return False
    save_state(state)

    channel = bot.get_channel(giveaway.get("channel_id"))
    if not channel:
        return True
    participants = giveaway.get("participants", [])
    prize = giveaway.get("prize", "Prize")
    if not participants:
        await channel.send(f"😢 No one entered the giveaway for **{prize}**.")
    else:
        lucky_winners = random.sample(participants, k=min(giveaway.get("winners", 1), len(participants)))
        winner_mentions = ", ".join([f"<@{uid}>" for uid in lucky_winners])
        win_embed = discord.Embed(
            title="🎉 Giveaway Ended!",
            description=f"**Prize:** {prize}\n**Winner(s):** {winner_mentions}\n\nCongratulations! Contact staff to claim your prize.",
            color=0x2ECC71,
            timestamp=datetime.now()
        )
        await channel.send(content=f"GG {winner_mentions}!", embed=win_embed)
    try:  # close the entry button on the original post
        await channel.get_partial_message(int(message_id)).edit(view=None)
    except discord.HTTPException:
        pass
    return True


# ------------------ CRASH LOG ANALYZER ------------------

CRASH_RULES = [
    {
        "pattern": r"(java\.lang\.UnsupportedClassVersionError|class file version (6[1-9]|7[0-9]))",
        "title": "Java Version Incompatibility",
        "color": 0xE74C3C,
        "solution": (
            "**Issue:** The game is running on an outdated Java version.\n"
            "**Solution:** Minecraft 1.20+ requires **Java 17 or Java 21**.\n"
            "Please download and install Java 17/21 from Adoptium or your launcher settings."
        )
    },
    {
        "pattern": r"(java\.lang\.OutOfMemoryError|Java heap space)",
        "title": "Out of Memory (RAM Allocation)",
        "color": 0xE67E22,
        "solution": (
            "**Issue:** Minecraft ran out of memory.\n"
            "**Solution:** In The Lads Launcher, navigate to **Settings → Memory / RAM** and increase allocated RAM to **3 GB or 4 GB**."
        )
    },
    {
        # The hs_err "Problematic frame" line ("# C  [nvoglv64.dll+0x...]"): line-anchored, so it stays linear on hostile input
        "pattern": r"(?m)(nvoglv64\.dll|ig9ic64\.dll|ig10ic64\.dll|atio6axx\.dll|^#[ \t]+C[ \t]+\[(nv|ig|at))",
        "title": "GPU Graphics Driver Crash",
        "color": 0xE74C3C,
        "solution": (
            "**Issue:** OpenGL crashed inside your graphics card driver.\n"
            "**Solution:** Update your GPU drivers (NVIDIA GeForce Experience, AMD Adrenalin, or Intel Driver Assistant) and restart your PC."
        )
    },
    {
        "pattern": r"(DuplicateModsException|Duplicate mod ID found)",
        "title": "Duplicate Mods Installed",
        "color": 0xF1C40F,
        "solution": (
            "**Issue:** Two mods with the same ID or different versions of the same mod are in your `mods` folder.\n"
            "**Solution:** Open your `mods` folder and delete older duplicate versions of the conflicting mod."
        )
    },
    {
        "pattern": r"(MixinPrepareException|Mixin apply for mod .{0,200} failed)",
        "title": "Fabric / Mod Mixin Conflict",
        "color": 0x9B59B6,
        "solution": (
            "**Issue:** Two mods are modifying the same game code (Mixin conflict).\n"
            "**Solution:** Check which mod names are listed in the error above and remove the incompatible mod."
        )
    }
]

CRASH_SCAN_CHARS = 256_000  # head and tail of a log that the rules scan: bounds the cost of /crash, attachments and /crashlog


def analyze_crash_text(log_text: str):
    if len(log_text) > 2 * CRASH_SCAN_CHARS:
        log_text = log_text[:CRASH_SCAN_CHARS] + "\n" + log_text[-CRASH_SCAN_CHARS:]
    for rule in CRASH_RULES:
        if re.search(rule["pattern"], log_text, re.IGNORECASE):
            return rule
    return None


# ------------------ CHANGELOG PARSER & POSTER ------------------

def parse_changelog(content: str, version: str):
    sections = {}
    current_sec = "Summary"
    current_lines = []

    for line in content.splitlines():
        header_match = re.match(r"^##\s+(.+)$", line)
        if header_match:
            if current_lines:
                sections[current_sec] = "\n".join(current_lines).strip()
                current_lines = []
            current_sec = header_match.group(1).strip()
        elif line.startswith("# "):
            continue
        else:
            current_lines.append(line)

    if current_lines:
        sections[current_sec] = "\n".join(current_lines).strip()

    return sections


async def post_changelog_update(guild: discord.Guild, version: str, content: str, force: bool = False):
    changelog_channel = discord.utils.get(guild.text_channels, name="changelog")
    if not changelog_channel:
        print(f"Warning: #changelog channel not found in guild {guild.name}", flush=True)
        return False

    update_role = discord.utils.get(guild.roles, name="🔄 Update Ping")
    role_mention = update_role.mention if update_role else "@everyone"

    sections = parse_changelog(content, version)

    embeds = []
    current_embed = discord.Embed(
        title=f"🚀 The Lads Client v{version} is Live!",
        description=f"A new update for **The Lads Client** is now available. Here is what's new in **v{version}**:",
        color=0x1ABC9C,
        timestamp=datetime.now()
    )
    current_embed_size = len(current_embed.title or "") + len(current_embed.description or "")

    order = ["Highlights", "Added", "Changed", "Fixed"]
    all_sections = []
    for s in order:
        if s in sections:
            all_sections.append((s, sections[s]))
    for s, t in sections.items():
        if s not in order and s != "Summary":
            all_sections.append((s, t))

    for sec_name, text in all_sections:
        chunks = []
        curr_chunk = ""
        for line in text.split("\n"):
            if len(curr_chunk) + len(line) + 1 > 900:
                chunks.append(curr_chunk.strip())
                curr_chunk = line + "\n"
            else:
                curr_chunk += line + "\n"
        if curr_chunk.strip():
            chunks.append(curr_chunk.strip())

        for idx, chunk in enumerate(chunks, start=1):
            field_name = f"✨ {sec_name}" + (f" (Part {idx})" if len(chunks) > 1 else "")
            field_val = chunk
            field_len = len(field_name) + len(field_val)

            if current_embed_size + field_len > 4000:
                embeds.append(current_embed)
                current_embed = discord.Embed(
                    title=f"🚀 The Lads Client v{version} (Continued)",
                    color=0x1ABC9C,
                    timestamp=datetime.now()
                )
                current_embed_size = len(current_embed.title or "")

            current_embed.add_field(name=field_name, value=field_val, inline=False)
            current_embed_size += field_len

    if current_embed.fields:
        embeds.append(current_embed)

    if embeds:
        embeds[-1].set_footer(text="The Lads Client • Official Release Notes")

    msg_content = f"{role_mention} 📢 **New Client Release: v{version}**"
    for idx, emb in enumerate(embeds):
        content_to_send = msg_content if idx == 0 else None
        await changelog_channel.send(content=content_to_send, embed=emb)
        await asyncio.sleep(0.5)

    print(f"Successfully posted changelog v{version} to #changelog ({len(embeds)} embeds)", flush=True)
    return True


# ------------------ STATUS WEB SERVER ------------------

bot_start_time = datetime.now(timezone.utc)

async def handle_status_page(request):
    guild = bot.get_guild(GUILD_ID)
    uptime_sec = int((datetime.now(timezone.utc) - bot_start_time).total_seconds())
    hours, rem = divmod(uptime_sec, 3600)
    minutes, seconds = divmod(rem, 60)
    uptime_str = f"{hours}h {minutes}m {seconds}s"
    ping_ms = round(bot.latency * 1000) if bot.latency and math.isfinite(bot.latency) and bot.latency > 0 else 12

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>The Lads Bot — Status</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0b0f19; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }}
        .card {{ background: #131b2e; border: 1px solid #1e293b; border-radius: 20px; padding: 36px; max-width: 460px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.6); text-align: center; }}
        .badge {{ display: inline-flex; align-items: center; gap: 8px; background: rgba(34, 197, 94, 0.15); color: #4ade80; padding: 6px 16px; border-radius: 9999px; font-weight: 600; font-size: 13px; margin-bottom: 20px; border: 1px solid rgba(34, 197, 94, 0.3); }}
        .pulse {{ width: 8px; height: 8px; background: #22c55e; border-radius: 50%; box-shadow: 0 0 12px #22c55e; }}
        h1 {{ margin: 0 0 6px 0; font-size: 26px; color: #38bdf8; font-weight: 800; letter-spacing: -0.5px; }}
        p.subtitle {{ color: #94a3b8; margin: 0 0 24px 0; font-size: 14px; }}
        .grid {{ display: grid; grid-template-columns: 1fr 1fr; gap: 12px; text-align: left; }}
        .box {{ background: #0b0f19; padding: 16px; border-radius: 12px; border: 1px solid #1e293b; }}
        .box-title {{ font-size: 11px; color: #64748b; text-transform: uppercase; font-weight: 700; letter-spacing: 0.5px; }}
        .box-value {{ font-size: 17px; font-weight: 700; color: #f1f5f9; margin-top: 4px; }}
        .footer {{ margin-top: 24px; font-size: 12px; color: #475569; }}
    </style>
</head>
<body>
    <div class="card">
        <div class="badge"><div class="pulse"></div> 24/7 Cloud Node Active</div>
        <h1>The Lads Discord Bot</h1>
        <p class="subtitle">Official Community & Client Assistant</p>
        <div class="grid">
            <div class="box">
                <div class="box-title">Community</div>
                <div class="box-value">{guild.name if guild else 'Connected'}</div>
            </div>
            <div class="box">
                <div class="box-title">Members</div>
                <div class="box-value">{guild.member_count if guild else 'Active'}</div>
            </div>
            <div class="box">
                <div class="box-title">Latency</div>
                <div class="box-value">{ping_ms} ms</div>
            </div>
            <div class="box">
                <div class="box-title">Uptime</div>
                <div class="box-value">{uptime_str}</div>
            </div>
        </div>
        <div class="footer">The Lads Client Services • arashyt.ca</div>
    </div>
</body>
</html>"""
    return web.Response(text=html, content_type='text/html')

_pack_rate_history = {}

INVITE_ONLY_EXPERIENCES = {"lads-smp-reborn-26.3", "experimental"}  # "experimental": launcher experimental modes (Horizon), /experimental_invite

_crash_rate_history = {}
_global_crash_history = []
_discord_crash_history = []
_recent_crash_hashes = {}
CRASH_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'crash_reports')

def prune_crash_reports(crash_dir: str = CRASH_DIR, max_age_days: int = 30, max_total_mb: int = 200):
    """Delete crash reports older than 30 days and enforce total size limit (oldest-first)."""
    if not os.path.isdir(crash_dir):
        return
    now = time.time()
    max_age_sec = max_age_days * 86400
    files = []
    total_bytes = 0
    try:
        for entry in os.scandir(crash_dir):
            if entry.is_file():
                try:
                    stat = entry.stat()
                    mtime = stat.st_mtime
                    size = stat.st_size
                    if now - mtime > max_age_sec:
                        try:
                            os.remove(entry.path)
                        except OSError:
                            pass
                    else:
                        files.append((entry.path, mtime, size))
                        total_bytes += size
                except OSError:
                    pass
    except Exception as e:
        print(f"Error scanning crash reports: {e}", flush=True)
        return

    max_bytes = max_total_mb * 1024 * 1024
    if total_bytes > max_bytes:
        files.sort(key=lambda x: x[1])  # oldest first
        for p, _, size in files:
            try:
                os.remove(p)
                total_bytes -= size
                if total_bytes <= max_bytes:
                    break
            except OSError:
                pass

async def handle_crash_report(request):
    # Lads launcher (client profiles only) posts a crashed game's report, hs_err and log tail; staff see it in #crash-reports.
    import share_store
    ip = share_store.get_client_ip(request)
    now_ts = time.time()

    global _global_crash_history
    _global_crash_history = [t for t in _global_crash_history if t > now_ts - 3600]
    if len(_global_crash_history) >= 30:
        return web.json_response({"error": "global rate limit exceeded"}, status=429, headers={"Retry-After": "3600"})

    hits = [t for t in _crash_rate_history.get(ip, []) if t > now_ts - 600]
    if len(hits) >= 3:
        return web.json_response({"error": "rate limit exceeded"}, status=429, headers={"Retry-After": "600"})
    _crash_rate_history[ip] = hits + [now_ts]
    _global_crash_history.append(now_ts)
    if len(_crash_rate_history) > 5000:
        stale = [k for k, ts_list in _crash_rate_history.items() if not ts_list or ts_list[-1] < now_ts - 600]
        for k in stale:
            del _crash_rate_history[k]
    try:
        data = await request.json()
    except Exception:
        return web.json_response({"error": "bad json"}, status=400)
    if not isinstance(data, dict):
        return web.json_response({"error": "bad json"}, status=400)
    part = lambda k, n=200_000: str(data.get(k) or "")[:n]
    crash, hs_err, log, win = part("crash"), part("hsErr"), part("log"), part("windowsEvent", 20_000)
    launcher, mc, code = part("launcher", 32), part("minecraft", 32), part("exitCode", 16)
    if not (crash or hs_err or log or win):
        return web.json_response({"error": "empty"}, status=400)
    user = re.search(r"Setting user: (\w{1,16})", log)
    ign = user.group(1) if user else "unknown"
    kind = "Java crash report" if crash else "JVM crash (hs_err)" if hs_err else "Windows crash event" if win else "no crash report (log only)"
    os.makedirs(CRASH_DIR, exist_ok=True)
    prune_crash_reports(CRASH_DIR)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    name = f"{stamp}-{re.sub(r'[^A-Za-z0-9_]', '', ign)}-{random.randint(0, 0xffff):04x}.txt"
    body = (f"Lads launcher {launcher} | Minecraft {mc} | exit code {code} | {ign} (unverified)\n\n"
            f"===== crash report =====\n{crash}\n\n===== hs_err =====\n{hs_err}\n\n===== Windows crash events =====\n{win}\n\n===== latest.log (tail) =====\n{log}\n")
    with open(os.path.join(CRASH_DIR, name), "w", encoding="utf-8") as f:
        f.write(body)
    guild = bot.get_guild(GUILD_ID)
    if guild:
        channel = discord.utils.get(guild.text_channels, name="crash-reports")
        if channel is None:
            staff = discord.utils.get(guild.text_channels, name="mod-logs")
            try:
                channel = await guild.create_text_channel("crash-reports", category=staff.category if staff else None, topic="Automatic Lads Client crash reports from the launcher")
            except Exception:
                channel = staff
        if channel:
            rule = analyze_crash_text(crash + "\n" + hs_err + "\n" + win + "\n" + log)
            key_lines = [m.group(0).strip() for m in re.finditer(r"^Description:.*|^Caused by:.*|^#\s+EXCEPTION_\w+.*|^# [CVJj]  .*|Faulting module name:.*|Exception code:.*", crash + "\n" + hs_err + "\n" + win, re.M)]
            first = "\n".join(dict.fromkeys(key_lines))[:600]
            if first:
                key_hash = hashlib.sha256(first.encode("utf-8")).hexdigest()
            else:
                log_tail = log[-500:] if log else ""
                key_hash = hashlib.sha256(f"{ign}:{kind}:{log_tail}".encode("utf-8")).hexdigest()
            now_mono = time.monotonic()
            stale_hashes = [h for h, ts in _recent_crash_hashes.items() if now_mono - ts > 3600]
            for h in stale_hashes:
                del _recent_crash_hashes[h]
            global _discord_crash_history
            _discord_crash_history = [t for t in _discord_crash_history if t > now_mono - 3600]
            if len(_discord_crash_history) < 10 and key_hash not in _recent_crash_hashes:
                _discord_crash_history.append(now_mono)
                _recent_crash_hashes[key_hash] = now_mono
                embed = discord.Embed(title=f"\U0001f4a5 Client crash: {ign} (unverified)", color=0xE74C3C,
                                      description=f"**{kind}**\nMinecraft `{mc}` · launcher `{launcher}` · exit `{code}`\n{first}")
                if rule:
                    embed.add_field(name=rule.get("title", "Known issue"), value=rule.get("solution", "")[:1000], inline=False)
                try:
                    await channel.send(embed=embed, file=discord.File(os.path.join(CRASH_DIR, name)))
                except Exception as e:
                    print(f"crash report post failed: {e}", flush=True)
    return web.json_response({"ok": True})

_exp_rate_history: dict[str, list[float]] = {}

async def handle_experience_access(request):
    # Launcher asks: may any of these Minecraft usernames see this Experience? Edited live with /smp_invite.
    import client_ip
    ip = client_ip.client_ip(request)
    now_ts = time.time()
    if len(_exp_rate_history) > 5000:
        stale = [k for k, ts_list in _exp_rate_history.items() if not ts_list or ts_list[-1] < now_ts - 60.0]
        for k in stale:
            del _exp_rate_history[k]
    hits = [t for t in _exp_rate_history.get(ip, []) if t > now_ts - 60.0]
    if len(hits) >= 60:
        return web.json_response({"error": "rate limit exceeded"}, status=429)
    hits.append(now_ts)
    _exp_rate_history[ip] = hits

    exp = request.match_info['exp']
    if exp not in INVITE_ONLY_EXPERIENCES:
        return web.json_response({"allowed": True})
    names = {n.strip().lower() for n in request.query.getall('u', [])[:3] if n.strip()}
    invited = set(load_state().get("experience_invites", {}).get(exp, []))
    return web.json_response({"allowed": bool(names & invited)}, headers={"Cache-Control": "no-store"})

async def start_web_server(port: int = 8085):
    try:
        app = web.Application(client_max_size=2 * 1024 * 1024)
        try:
            import cosmetics
            cosmetics.setup(app)
        except Exception as e:
            print(f"  Note: cosmetics routes not loaded: {e}", flush=True)
        app.router.add_get('/', handle_status_page)
        app.router.add_get('/health', lambda r: web.json_response({"status": "ok", "latency_ms": round(bot.latency * 1000) if bot.latency and math.isfinite(bot.latency) and bot.latency > 0 else None}))
        import share_store
        app.router.add_post('/share', share_store.handle_post_share)
        app.router.add_get('/share/{code}', share_store.handle_get_share)
        import usage_stats
        usage_stats.setup(app, bot)
        packs_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'packs')
        async def handle_pack(request):
            client_ip = share_store.get_client_ip(request)
            now_ts = time.time()
            window_sec = 60.0
            limit_per_min = 30
            if len(_pack_rate_history) > 5000:
                stale = [k for k, ts_list in _pack_rate_history.items() if not ts_list or ts_list[-1] < now_ts - window_sec]
                for k in stale:
                    del _pack_rate_history[k]
            hits = [t for t in _pack_rate_history.get(client_ip, []) if t > now_ts - window_sec]
            if len(hits) >= limit_per_min:
                retry_after = max(1, int(window_sec - (now_ts - hits[0]) + 0.999))
                return web.json_response(
                    {"error": "rate limit exceeded"},
                    status=429,
                    headers={"Retry-After": str(retry_after)}
                )
            hits.append(now_ts)
            _pack_rate_history[client_ip] = hits

            name = request.match_info['name']
            path = os.path.join(packs_dir, name)
            if name != os.path.basename(name) or not name.endswith('.zip') or not os.path.isfile(path):
                return web.json_response({"error": "not found"}, status=404)
            return web.FileResponse(path, headers={"Cache-Control": "public, max-age=3600"})
        app.router.add_get('/packs/{name}', handle_pack)
        app.router.add_get('/experiences/{exp}/access', handle_experience_access)
        app.router.add_post('/crash', handle_crash_report)
        runner = web.AppRunner(app)
        await runner.setup()
        site = web.TCPSite(runner, '127.0.0.1', port)
        await site.start()
        print(f"  Status Web Server running on http://127.0.0.1:{port}", flush=True)
        return runner, site
    except Exception as e:
        print(f"  Note: web status server startup error: {e}", flush=True)
        sys.exit(f"Web status server startup error: {e}")


# ------------------ BOT CLASS ------------------

def _presence_intent_enabled() -> bool:
    """Presence is a privileged intent: asking for it while the Developer Portal toggle is off makes discord.py refuse to connect."""
    try:
        req = urllib.request.Request("https://discord.com/api/v10/applications/@me", headers={
            "Authorization": f"Bot {BOT_TOKEN}",
            "User-Agent": "DiscordBot (https://github.com/ArashYT/TheLadsClient, 1.0)",
        })
        with urllib.request.urlopen(req, timeout=10) as resp:
            flags = json.load(resp).get("flags", 0)
        return bool(flags & ((1 << 12) | (1 << 13)))  # GATEWAY_PRESENCE | GATEWAY_PRESENCE_LIMITED
    except Exception as e:
        print(f"Could not read application flags ({e}); requesting the presence intent anyway", flush=True)
        return True


class LadsBot(commands.Bot):
    def __init__(self):
        intents = discord.Intents.default()
        intents.members = True
        intents.message_content = True
        intents.voice_states = True
        intents.presences = _presence_intent_enabled()  # needed for the 🟢 Online counter; without it we fall back to approximate counts
        if not intents.presences:
            print("Presence intent is OFF in the Developer Portal: the Online counter uses Discord's approximate count", flush=True)
        super().__init__(command_prefix="!", intents=intents)

    async def setup_hook(self):
        self.add_view(NotificationRolesView())
        self.add_view(ColorRolesView())
        self.add_view(FAQPanelView())
        self.add_view(TicketPanelView())
        self.add_view(TicketControlView())
        self.add_view(SuggestionPanelView())
        self.add_view(SuggestionVoteView())
        self.add_view(LFGSquadView())
        self.add_dynamic_items(GiveawayButton)
        print("Registered persistent UI Views", flush=True)

        # Register slash commands from helper modules before tree.sync
        try:
            import usage_stats
            if self.tree.get_command("stats") is None:
                usage_stats.setup_slash_command(self)
                print("Registered /stats slash command", flush=True)
        except Exception as e:
            print(f"Note: usage_stats slash command not registered: {e}", flush=True)

        # Start Web Server in setup_hook before gateway connect/tree.sync so player APIs are decoupled
        if not getattr(self, "web_server_started", False):
            self.web_server_started = True
            await start_web_server()

    async def on_ready(self):
        print(f"\n[Bot Ready] Logged in as: {self.user} (ID: {self.user.id})", flush=True)
        guild = self.get_guild(GUILD_ID)
        if not guild:
            print(f"Could not find guild {GUILD_ID}", flush=True)
            return

        print(f"Connected to Guild: '{guild.name}' ({guild.id})", flush=True)

        # 1. Post/Update self-roles panel in #roles
        roles_channel = discord.utils.get(guild.text_channels, name="roles")
        if roles_channel:
            try:
                # Check notification panel
                notif_msg = None
                color_msg = None
                async for msg in roles_channel.history(limit=10):
                    if msg.author == self.user and msg.embeds:
                        title = msg.embeds[0].title or ""
                        if "Notification Role Preferences" in title:
                            notif_msg = msg
                        elif "Custom Username Color" in title:
                            color_msg = msg

                if not notif_msg:
                    embed = build_roles_panel_embed()
                    view = NotificationRolesView()
                    await roles_channel.send(embed=embed, view=view)
                    print("  Posted Notification Self-Roles panel in #roles", flush=True)
                else:
                    # Update existing message to ensure fresh options
                    embed = build_roles_panel_embed()
                    view = NotificationRolesView()
                    await notif_msg.edit(embed=embed, view=view)

                if not color_msg:
                    c_embed = build_colors_panel_embed()
                    c_view = ColorRolesView()
                    await roles_channel.send(embed=c_embed, view=c_view)
                    print("  Posted Color Palette panel in #roles", flush=True)
                else:
                    c_embed = build_colors_panel_embed()
                    c_view = ColorRolesView()
                    await color_msg.edit(embed=c_embed, view=c_view)
            except Exception as e:
                print(f"  Error with #roles: {e}", flush=True)

        # 2. Post Interactive Knowledge Base Panel in #faq
        faq_channel = discord.utils.get(guild.text_channels, name="faq")
        if faq_channel:
            try:
                faq_msg = None
                async for msg in faq_channel.history(limit=10):
                    if msg.author == self.user and msg.embeds:
                        title = msg.embeds[0].title or ""
                        if "Knowledge Base" in title or "FAQ" in title:
                            faq_msg = msg
                            break
                if not faq_msg:
                    f_embed = build_faq_panel_embed()
                    f_view = FAQPanelView()
                    await faq_channel.send(embed=f_embed, view=f_view)
                    print("  Posted Interactive Knowledge Base panel in #faq", flush=True)
                else:
                    f_embed = build_faq_panel_embed()
                    f_view = FAQPanelView()
                    await faq_msg.edit(embed=f_embed, view=f_view)
            except Exception as e:
                print(f"  Error with #faq: {e}", flush=True)

        # 3. Post Ticket Panel in #tech-support if not already present
        support_channel = discord.utils.get(guild.text_channels, name="tech-support")
        if support_channel:
            try:
                already_ticket_posted = False
                async for msg in support_channel.history(limit=10):
                    if msg.author == self.user and msg.embeds and "Support Center" in (msg.embeds[0].title or ""):
                        already_ticket_posted = True
                        break
                if not already_ticket_posted:
                    t_embed = build_ticket_panel_embed()
                    t_view = TicketPanelView()
                    await support_channel.send(embed=t_embed, view=t_view)
                    print("  Posted Ticket Support Panel in #tech-support", flush=True)
            except Exception as e:
                print(f"  Error with #tech-support: {e}", flush=True)

        # 3b. Post/Update Suggestion Panel in #suggestions
        sugg_channel = discord.utils.get(guild.text_channels, name="suggestions")
        if sugg_channel:
            try:
                already_sugg_posted = False
                async for msg in sugg_channel.history(limit=15):
                    if msg.author == self.user and msg.embeds and "Suggestion Center" in (msg.embeds[0].title or ""):
                        already_sugg_posted = True
                        break
                if not already_sugg_posted:
                    s_embed = build_suggestion_panel_embed()
                    s_view = SuggestionPanelView()
                    await sugg_channel.send(embed=s_embed, view=s_view)
                    print("  Posted Suggestion Panel in #suggestions", flush=True)
            except Exception as e:
                print(f"  Error with #suggestions: {e}", flush=True)

        # 4. Post Welcome Starters in Community Channels
        gw_channel = discord.utils.get(guild.text_channels, name="giveaways")
        if gw_channel:
            try:
                has_bot_msg = False
                async for msg in gw_channel.history(limit=5):
                    if msg.author == self.user:
                        has_bot_msg = True
                        break
                if not has_bot_msg:
                    gw_embed = discord.Embed(
                        title="🎉 The Lads — Official Community Giveaways",
                        description=(
                            "Welcome to **#giveaways**! 🎁\n\n"
                            "This is the official home for community giveaways, event raffles, and exclusive client cosmetic rewards!\n\n"
                            "• **Entering Giveaways:** Simply click the **[🎉 Enter Giveaway]** button on any active giveaway embed.\n"
                            "• **Never Miss Out:** Head to <#1556197211082072105> and select the **🎉 Events Ping** role to get notified when new giveaways start!\n"
                            "• **Fair & Transparent:** All winners are drawn completely at random by the bot.\n\n"
                            "*Good luck to all members! Keep chatting and climbing the ranks!*"
                        ),
                        color=0xE91E63
                    )
                    gw_embed.set_footer(text="The Lads Community Giveaways", icon_url="https://cdn.discordapp.com/embed/avatars/0.png")
                    await gw_channel.send(embed=gw_embed)
                    print("  Posted welcome notice in #giveaways", flush=True)
            except Exception as e:
                print(f"  Error with #giveaways: {e}", flush=True)

        setups_channel = discord.utils.get(guild.text_channels, name="client-setups")
        if setups_channel:
            try:
                has_bot_msg = False
                async for msg in setups_channel.history(limit=5):
                    if msg.author == self.user:
                        has_bot_msg = True
                        break
                if not has_bot_msg:
                    s_embed = discord.Embed(
                        title="📸 The Lads — Client Setups & Configurations",
                        description=(
                            "Show off your personalized setup and inspire fellow lads! ✨\n\n"
                            "**What to share in this channel:**\n"
                            "• **Screenshots (F1 / F2):** Your custom HUD layout, crosshair, Armor HUD & Keystrokes.\n"
                            "• **Resource Packs & Shaders:** Share download links or names of packs you pair with Lads Client.\n"
                            "• **Sensitivity & Settings:** Your favorite FOV, mouse DPI, and motion blur tweaks.\n"
                            "• **Configs:** Export and share your `.json` mod configs from `.minecraft/config/ladsclient`.\n\n"
                            "*React to the cleanest setups with ⭐ and 🔥!*"
                        ),
                        color=0x1ABC9C
                    )
                    s_embed.set_footer(text="The Lads Setup Showcase", icon_url="https://cdn.discordapp.com/embed/avatars/0.png")
                    await setups_channel.send(embed=s_embed)
                    print("  Posted starter guide in #client-setups", flush=True)
            except Exception as e:
                print(f"  Error with #client-setups: {e}", flush=True)

        lfg_channel = discord.utils.get(guild.text_channels, name="looking-for-group")
        if lfg_channel:
            try:
                has_bot_msg = False
                async for msg in lfg_channel.history(limit=5):
                    if msg.author == self.user:
                        has_bot_msg = True
                        break
                if not has_bot_msg:
                    lfg_embed = discord.Embed(
                        title="🎮 The Lads — Looking For Group (LFG)",
                        description=(
                            "Never queue alone again! Find teammates for Bedwars, Skywars, Duels, or SMP. ⚔️\n\n"
                            "**How to recruit a squad:**\n"
                            "• Use the command `/lfg` (e.g. `/lfg game:Bedwars 4v4 needed:3 notes:mic on`)\n"
                            "• The bot posts a recruitment card with live **[✋ Join Squad]** and **[🚪 Leave Squad]** buttons!\n"
                            "• When the squad fills up, everyone is notified to join <#1556193374640541877>.\n\n"
                            "🔔 *Want to receive squad recruitment pings? Grab the **🎮 Looking for Group** role in <#1556197211082072105>!*"
                        ),
                        color=0x3498DB
                    )
                    lfg_embed.set_footer(text="The Lads LFG Hub", icon_url="https://cdn.discordapp.com/embed/avatars/0.png")
                    await lfg_channel.send(embed=lfg_embed)
                    print("  Posted recruitment guide in #looking-for-group", flush=True)
            except Exception as e:
                print(f"  Error with #looking-for-group: {e}", flush=True)

        # 5. Sync Slash Commands directly to this guild
        try:
            guild_obj = discord.Object(id=GUILD_ID)
            self.tree.copy_global_to(guild=guild_obj)
            synced = await self.tree.sync(guild=guild_obj)
            print(f"  Synced {len(synced)} slash command(s) to guild '{guild.name}'", flush=True)
        except Exception as e:
            print(f"  Failed syncing slash commands: {e}", flush=True)

        # 6. Auto-assign default roles & sync milestones
        member_role = discord.utils.get(guild.roles, name="👥 Member")
        bot_role = discord.utils.get(guild.roles, name="🤖 Bots")
        linked_role = discord.utils.get(guild.roles, name="🎮 Linked")

        try:
            state = load_state()
            linked_data = state.get("linked_accounts", {})
            levels_data = state.get("levels", {})
        except Exception as e:
            print(f"Warning: could not load state in on_ready: {e}", flush=True)
            linked_data = {}
            levels_data = {}

        for m in guild.members:
            if m.bot:
                if bot_role and bot_role not in m.roles:
                    try:
                        await m.add_roles(bot_role, reason="Auto-role for bots")
                    except Exception:
                        pass
            else:
                if member_role and member_role not in m.roles:
                    try:
                        await m.add_roles(member_role, reason="Auto-role default Member role")
                    except Exception:
                        pass
                if str(m.id) in linked_data and linked_role and linked_role not in m.roles:
                    try:
                        await m.add_roles(linked_role, reason="Sync Linked role on startup")
                    except Exception:
                        pass

                # Milestone Roles Sync
                user_lvl_info = levels_data.get(str(m.id), {})
                mem_lvl = user_lvl_info.get("level", 1)
                for m_lvl, m_info in sorted(LEVEL_MILESTONES.items()):
                    if mem_lvl >= m_lvl:
                        m_role = discord.utils.get(guild.roles, name=m_info["name"])
                        if m_role and m_role not in m.roles:
                            try:
                                await m.add_roles(m_role, reason=f"Level {m_lvl} startup sync")
                            except Exception:
                                pass

        # 5. Start background tasks
        if not changelog_watcher.is_running():
            changelog_watcher.start()
            print("  Started Changelog Watcher task (checking docs/releases every 20s)", flush=True)

        if not server_stats_updater.is_running():
            server_stats_updater.start()
            print("  Started Server Stats Updater task (updating stats every 10m)", flush=True)

        if not giveaway_ender.is_running():
            giveaway_ender.start()
            print("  Started Giveaway Ender task (checking every 1m)", flush=True)

        # 6. Start Web Status Server (once: on_ready fires again after a full reconnect)
        if not getattr(self, "web_server_started", False):
            self.web_server_started = True
            asyncio.create_task(start_web_server())

        print("[Bot Initialization Complete]\n", flush=True)


bot = LadsBot()


# ------------------ EVENT: MEMBER JOIN ------------------

@bot.event
async def on_member_join(member: discord.Member):
    guild = member.guild

    # 1. Assign Default Role & Check Linked State
    if member.bot:
        bot_role = discord.utils.get(guild.roles, name="🤖 Bots")
        if bot_role:
            try:
                await member.add_roles(bot_role, reason="Auto-role for bots on join")
            except Exception as e:
                print(f"Error giving bot role: {e}", flush=True)
    else:
        member_role = discord.utils.get(guild.roles, name="👥 Member")
        if member_role:
            try:
                await member.add_roles(member_role, reason="Auto-role default Member role on join")
            except Exception as e:
                print(f"Error giving member role: {e}", flush=True)

        # Restore Linked role if user was previously linked
        state = load_state()
        if str(member.id) in state.get("linked_accounts", {}):
            linked_role = discord.utils.get(guild.roles, name="🎮 Linked")
            if linked_role:
                try:
                    await member.add_roles(linked_role, reason="Restore Linked role on rejoin")
                except Exception:
                    pass

        # 2. Send Welcome Message in #general
        gen_channel = discord.utils.get(guild.text_channels, name="general")
        if gen_channel:
            welcome_embed = discord.Embed(
                title=f"👋 Welcome to The Lads, {member.name}!",
                description=(
                    f"Welcome to the official community server for **The Lads Client**, {member.mention}!\n\n"
                    "📌 **Quick Links to Get Started:**\n"
                    "• Read the community rules in <#1556193292595634259>\n"
                    "• Pick your notification roles in <#1556197211082072105>\n"
                    "• Download the client in <#1556193313374077068>\n"
                    "• Link your Minecraft account with `/link <ign>` to get **`🎮 Linked`**!\n"
                    "• Join discussions in <#1556193331430563944>!"
                ),
                color=0x1ABC9C,
                timestamp=datetime.now()
            )
            welcome_embed.set_thumbnail(url=member.display_avatar.url)
            welcome_embed.set_footer(text=f"Member #{guild.member_count}")
            try:
                await gen_channel.send(content=f"Welcome {member.mention}!", embed=welcome_embed)
            except Exception as e:
                print(f"Error sending welcome embed: {e}", flush=True)


# ------------------ EVENT: DYNAMIC VOICE CHANNELS (JOIN TO CREATE) ------------------

@bot.event
async def on_voice_state_update(member: discord.Member, before: discord.VoiceState, after: discord.VoiceState):
    guild = member.guild

    # 1. Join-to-Create Voice Room
    if after.channel and "Create Squad" in after.channel.name:
        voice_cat = after.channel.category
        try:
            # Create temporary channel
            squad_name = f"🎮 {member.name}'s Squad"
            new_vc = await guild.create_voice_channel(
                name=squad_name,
                category=voice_cat,
                user_limit=10,
                reason=f"Dynamic temporary squad voice room for {member.name}"
            )
            temp_voice_squads.add(new_vc.id)
            # Move member into new voice room
            await member.move_to(new_vc)
            print(f"[Dynamic VC] Created {new_vc.name} for {member.name}", flush=True)
        except Exception as e:
            print(f"Error creating dynamic voice channel: {e}", flush=True)

    # 2. Cleanup empty temporary squad rooms
    # The name check also catches rooms created before a bot restart (the in-memory set starts empty)
    if before.channel and (before.channel.id in temp_voice_squads or before.channel.name.endswith("'s Squad")):
        if len(before.channel.members) == 0:
            try:
                temp_voice_squads.discard(before.channel.id)
                await before.channel.delete(reason="Temporary squad room empty")
                print(f"[Dynamic VC] Deleted empty room #{before.channel.name}", flush=True)
            except Exception as e:
                print(f"Error deleting temp voice room: {e}", flush=True)


# ------------------ EVENT: AUTOMOD, XP, STICKY & CRASH LOGS ------------------

INVITE_REGEX = re.compile(r"(discord\.gg/|discord\.com/invite/|discordapp\.com/invite/)", re.IGNORECASE)
PHISHING_REGEX = re.compile(
    r"(?:https?://\S*(?:steamcommunity-[^\s/]+\.(?:com|ru|link)|(?:discord|nitro)[^\s/]*gift|free-nitro|steam-giveaway)"
    r"|steamcommunity-[^\s/]+\.(?:com|ru|link))",
    re.IGNORECASE
)

@bot.event
async def on_message(message: discord.Message):
    if message.author.bot or not message.guild:
        return

    author = message.author
    channel = message.channel
    content_lower = message.content.lower()
    is_staff = any(r.name in ["👑 Owner", "🛡️ Admin", "🛡️ Staff", "🔨 Moderator"] for r in author.roles)

    # 1. Word Blacklist Filter
    state = load_state()
    blacklist = state.get("blacklisted_words", [])
    if not is_staff and blacklist:
        for bad_word in blacklist:
            if re.search(rf"\b{re.escape(bad_word.lower())}\b", content_lower):
                try:
                    await message.delete()
                    await channel.send(f"⚠️ {author.mention}, your message contained blocked language.", delete_after=5)
                    log_embed = discord.Embed(
                        title="🛡️ AutoMod: Blacklisted Word Deleted",
                        description=f"**User:** {author.mention} (`{author.id}`)\n**Channel:** {channel.mention}\n**Word:** `{bad_word}`",
                        color=0xE74C3C,
                        timestamp=datetime.now()
                    )
                    await log_to_modlogs(message.guild, log_embed)
                    return
                except Exception as e:
                    print(f"Blacklist Error: {e}", flush=True)

    # 2. Phishing / Scam Link Detection
    if not is_staff and PHISHING_REGEX.search(message.content):
        try:
            await message.delete()
            await author.timeout(timedelta(hours=1), reason="AutoMod: Phishing/Scam link detected")
            log_embed = discord.Embed(
                title="🛡️ AutoMod: Phishing Scam Link Blocked",
                description=f"**User:** {author.mention} (`{author.id}`)\n**Channel:** {channel.mention}\n**Action:** Message deleted & 1-Hour Timeout",
                color=0xE74C3C,
                timestamp=datetime.now()
            )
            log_embed.add_field(name="Content", value=f"`{message.content[:500]}`", inline=False)
            await log_to_modlogs(message.guild, log_embed)
            await channel.send(f"⚠️ {author.mention} has been timed out for posting suspicious scam links.", delete_after=5)
            return
        except Exception as e:
            print(f"AutoMod Phishing Error: {e}", flush=True)

    # 3. Invite Link Filter (non-staff)
    if not is_staff and INVITE_REGEX.search(message.content):
        try:
            await message.delete()
            log_embed = discord.Embed(
                title="🛡️ AutoMod: Discord Invite Blocked",
                description=f"**User:** {author.mention} (`{author.id}`)\n**Channel:** {channel.mention}\n**Action:** Message deleted",
                color=0xE67E22,
                timestamp=datetime.now()
            )
            await log_to_modlogs(message.guild, log_embed)
            await channel.send(f"⚠️ {author.mention}, posting Discord invite links is not permitted here.", delete_after=5)
            return
        except Exception as e:
            print(f"AutoMod Invite Error: {e}", flush=True)

    # 4. Mass Mention Filter (5+ mentions)
    if not is_staff and len(message.mentions) >= 5:
        try:
            await message.delete()
            await author.timeout(timedelta(minutes=15), reason="AutoMod: Mass mentions spam")
            log_embed = discord.Embed(
                title="🛡️ AutoMod: Mass Mention Spam Blocked",
                description=f"**User:** {author.mention} (`{author.id}`)\n**Channel:** {channel.mention}\n**Action:** 15-Minute Timeout",
                color=0xE74C3C,
                timestamp=datetime.now()
            )
            await log_to_modlogs(message.guild, log_embed)
            await channel.send(f"⚠️ {author.mention} has been timed out for mass mentions.", delete_after=5)
            return
        except Exception as e:
            print(f"AutoMod Mass Mention Error: {e}", flush=True)

    # 5. Automated Crash Log Analyzer in #bug-reports and #tech-support
    if channel.name in ["bug-reports", "tech-support"]:
        log_content = message.content
        for att in message.attachments:
            if att.filename.endswith((".log", ".txt")):
                if att.size > 2 * 1024 * 1024:
                    continue
                try:
                    bytes_data = await att.read()
                    log_content += "\n" + bytes_data.decode("utf-8", errors="ignore")
                except Exception:
                    pass

        if "--accessToken" in log_content or re.search(r"eyJ[A-Za-z0-9_-]{20,}", log_content):
            warn_embed = discord.Embed(
                title="⚠️ Security Notice: Sensitive Token Detected in Log",
                description=(
                    f"{author.mention}, your uploaded log or message appears to contain a Minecraft `--accessToken` or session token.\n"
                    "**Please delete your message/attachment immediately** and redact any tokens before re-uploading!"
                ),
                color=0xE67E22,
                timestamp=datetime.now()
            )
            await message.reply(embed=warn_embed)

        analysis = analyze_crash_text(log_content)
        if analysis:
            diag_embed = discord.Embed(
                title=f"🛠️ Crash Log Diagnostic: {analysis['title']}",
                description=analysis["solution"],
                color=analysis["color"],
                timestamp=datetime.now()
            )
            diag_embed.set_footer(text="The Lads Automated Diagnostic Assistant")
            await message.reply(embed=diag_embed)

    # 6. Community Leveling & XP System
    uid = str(author.id)
    now = datetime.now()
    last_xp_time = xp_cooldowns.get(author.id)
    if not last_xp_time or (now - last_xp_time).total_seconds() >= 60:
        xp_cooldowns[author.id] = now
        gained_xp = random.randint(15, 25)

        levels_data = state.setdefault("levels", {})
        user_data = levels_data.setdefault(uid, {"xp": 0, "level": 1, "messages": 0, "username": author.name})
        user_data["messages"] = user_data.get("messages", 0) + 1
        user_data["username"] = author.name

        old_level = user_data.get("level", 1)
        new_xp = user_data.get("xp", 0) + gained_xp
        user_data["xp"] = new_xp

        # Check level up
        needed = xp_for_level(old_level)
        if new_xp >= needed:
            new_level = old_level + 1
            user_data["level"] = new_level
            save_state(state)

            new_milestones = []
            for m_lvl, m_info in sorted(LEVEL_MILESTONES.items()):
                if old_level < m_lvl <= new_level:
                    m_role = discord.utils.get(message.guild.roles, name=m_info["name"])
                    if m_role and m_role not in author.roles:
                        try:
                            await author.add_roles(m_role, reason=f"Level {m_lvl} milestone reward")
                            new_milestones.append(f"{m_info['emoji']} **{m_info['name']}**")
                        except Exception as e:
                            print(f"Error awarding milestone role: {e}", flush=True)

            milestone_text = ""
            if new_milestones:
                milestone_text = f"\n\n🎖️ **Milestone Role Unlocked:** {', '.join(new_milestones)}! Check `/colors` to customize your look!"

            lvl_embed = discord.Embed(
                title="🎉 Level Up!",
                description=f"GG {author.mention}, you just reached **Level {new_level}**! 🚀{milestone_text}",
                color=0xF1C40F
            )
            try:
                await channel.send(embed=lvl_embed, delete_after=12)
            except Exception:
                pass
        else:
            save_state(state)

    # 7. Sticky Messages (re-posts sticky notice to bottom of channel)
    ch_key = str(channel.id)
    sticky_info = state.get("sticky_messages", {}).get(ch_key)
    if sticky_info:
        last_id = sticky_info.get("last_msg_id")
        sticky_text = sticky_info.get("text")
        # Don't loop on bot's own sticky
        if message.id != last_id and sticky_text:
            if last_id:
                try:
                    old_msg = await channel.fetch_message(last_id)
                    await old_msg.delete()
                except Exception:
                    pass

            sticky_embed = discord.Embed(
                title="📌 Notice",
                description=sticky_text,
                color=0x3498DB
            )
            sticky_embed.set_footer(text="Sticky Announcement")
            new_sticky_msg = await channel.send(embed=sticky_embed)
            state["sticky_messages"][ch_key]["last_msg_id"] = new_sticky_msg.id
            save_state(state)

    await bot.process_commands(message)


# ------------------ BACKGROUND TASKS ------------------

@tasks.loop(seconds=20)
async def changelog_watcher():
    if not RELEASES_DIR.exists():
        return

    guild = bot.get_guild(GUILD_ID)
    if not guild:
        return

    try:
        state = load_state()
    except Exception as e:
        print(f"Warning: could not load state in changelog_watcher: {e}", flush=True)
        return
    posted_versions = set(state.get("posted_versions", []))

    release_files = list(RELEASES_DIR.glob("*.md"))
    for r_file in release_files:
        version_match = re.search(r"(\d+\.\d+\.\d+)", r_file.name)
        if not version_match:
            continue
        version = version_match.group(1)

        if version not in posted_versions:
            try:
                content = r_file.read_text(encoding="utf-8")
                success = await post_changelog_update(guild, version, content)
                if success:
                    posted_versions.add(version)
                    state["posted_versions"] = list(posted_versions)
                    save_state(state)
                    asyncio.create_task(update_server_stats(guild))  # refresh 🚀 Client right after a release post
            except Exception as e:
                print(f"Error posting release {version}: {e}", flush=True)


def _version_key(v: str):
    return tuple(int(x) for x in v.split("."))


async def latest_client_version():
    """Newest GitHub release of the client; falls back to the highest version we have release notes for."""
    try:
        async with aiohttp.ClientSession(max_field_size=65536) as session:  # github.com's page headers exceed aiohttp's 8 KB default
            # The release page redirects to .../releases/tag/vX.Y.Z: no GitHub API call, so no 60-per-hour quota shared with
            # the launchers and everything else on this internet connection.
            async with session.get(f"https://github.com/{CLIENT_REPO}/releases/latest", allow_redirects=False,
                                   headers={"User-Agent": "TheLadsBot"},
                                   timeout=aiohttp.ClientTimeout(total=8)) as resp:
                match = re.search(r"/tag/v?(\d+\.\d+\.\d+)", resp.headers.get("Location", ""))
                if match:
                    return match.group(1)
                print(f"GitHub latest release lookup returned HTTP {resp.status}", flush=True)
    except Exception as e:
        print(f"GitHub latest release lookup failed: {e}", flush=True)

    try:
        known = list(load_state().get("posted_versions", []))
    except Exception as e:
        print(f"Warning: could not load state in latest_client_version: {e}", flush=True)
        known = []
    if RELEASES_DIR.exists():
        known += [f.stem for f in RELEASES_DIR.glob("*.md")]
    known = [v for v in known if re.fullmatch(r"\d+\.\d+\.\d+", v)]
    return max(known, key=_version_key, default=None)


async def update_server_stats(guild: discord.Guild):
    stats_cat = discord.utils.get(guild.categories, name=STATS_CATEGORY)
    if not stats_cat:
        return

    if bot.intents.presences:
        online_members = sum(1 for m in guild.members if m.status != discord.Status.offline)
    else:  # no presence data without the privileged intent: use Discord's own approximate count
        online_members = (await bot.fetch_guild(guild.id, with_counts=True)).approximate_presence_count or 0
    version = await latest_client_version()

    targets = {
        "Members:": f"👥 Members: {guild.member_count}",
        "Online:": f"🟢 Online: {online_members}",
        "Client:": f"🚀 Client: v{version}" if version else None,
    }
    import usage_stats
    targets.update(await usage_stats.get_stats_targets(stats_cat))
    for ch in stats_cat.voice_channels:
        for key, target_name in targets.items():
            # Discord allows only 2 renames per channel per 10 minutes, so rename only when the value changed
            if key in ch.name and target_name and ch.name != target_name:
                try:
                    old_name = ch.name
                    await ch.edit(name=target_name)
                    print(f"  Stats: {old_name!r} -> {target_name!r}", flush=True)
                except Exception as e:
                    print(f"Error updating stats channel {ch.name}: {e}", flush=True)


@tasks.loop(minutes=10)
async def server_stats_updater():
    guild = bot.get_guild(GUILD_ID)
    if guild:
        await update_server_stats(guild)


@tasks.loop(minutes=1)
async def giveaway_ender():
    now = datetime.now(timezone.utc)
    try:
        giveaways = list(load_state().get("giveaways", {}).items())
    except Exception as e:
        print(f"Warning: could not load state in giveaway_ender: {e}", flush=True)
        return
    for msg_id, giveaway in giveaways:
        try:
            if datetime.fromisoformat(giveaway["ends_at"]) <= now:
                await end_giveaway(msg_id)
        except Exception as e:
            print(f"Error ending giveaway {msg_id}: {e}", flush=True)


# ------------------ LEVELING & XP COMMANDS ------------------

@bot.tree.command(name="rank", description="Check your or another member's level, XP, and rank.")
@app_commands.describe(user="Member to inspect")
async def slash_rank(interaction: discord.Interaction, user: discord.Member = None):
    target = user or interaction.user
    state = load_state()
    levels = state.get("levels", {})

    user_data = levels.get(str(target.id), {"xp": 0, "level": 1, "messages": 0})
    current_xp = user_data.get("xp", 0)
    current_lvl = user_data.get("level", 1)
    msgs = user_data.get("messages", 0)

    needed_xp = xp_for_level(current_lvl)
    prev_xp = xp_for_level(current_lvl - 1) if current_lvl > 1 else 0
    progress = max(0, min(current_xp - prev_xp, needed_xp - prev_xp))
    span = max(1, needed_xp - prev_xp)
    pct = int((progress / span) * 100)

    filled = int(pct / 10)
    bar = "█" * filled + "░" * (10 - filled)

    # Calculate Leaderboard Rank
    sorted_users = sorted(levels.items(), key=lambda x: x[1].get("xp", 0), reverse=True)
    rank_pos = next((idx for idx, (uid, _) in enumerate(sorted_users, start=1) if uid == str(target.id)), len(sorted_users))

    embed = discord.Embed(
        title=f"📊 Rank Card — {target.name}",
        color=0x1ABC9C,
        timestamp=datetime.now()
    )

    linked_acc = state.get("linked_accounts", {}).get(str(target.id))
    if linked_acc:
        embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{linked_acc['uuid']}/100")
        embed.add_field(name="🎮 Minecraft IGN", value=f"**`{linked_acc['ign']}`**", inline=True)
    else:
        embed.set_thumbnail(url=target.display_avatar.url)

    embed.add_field(name="🏆 Leaderboard Rank", value=f"**#{rank_pos}**", inline=True)
    embed.add_field(name="⭐ Level", value=f"**{current_lvl}**", inline=True)
    embed.add_field(name="💬 Messages", value=f"**{msgs:,}**", inline=True)

    # Determine current tier and next tier
    current_badge = "None"
    for m_lvl, m_info in sorted(LEVEL_MILESTONES.items()):
        if current_lvl >= m_lvl:
            current_badge = f"{m_info['emoji']} {m_info['name']}"

    next_tier_str = "Max Tier Reached! 🌟"
    for m_lvl, m_info in sorted(LEVEL_MILESTONES.items()):
        if current_lvl < m_lvl:
            next_tier_str = f"{m_info['emoji']} {m_info['name']} (Lvl {m_lvl})"
            break

    embed.add_field(name="🎖️ Tier", value=f"**{current_badge}**", inline=True)
    embed.add_field(name="🎯 Next Tier", value=f"**{next_tier_str}**", inline=True)
    embed.add_field(name=f"📈 Progress to Lvl {current_lvl + 1} ({pct}%)", value=f"`[{bar}]` ({progress:,} / {span:,} XP)", inline=False)
    embed.set_footer(text=f"Total XP: {current_xp:,} • Use /colors to change your name color")
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="leaderboard", description="View the Top 10 most active community members.")
async def slash_leaderboard(interaction: discord.Interaction):
    state = load_state()
    levels = state.get("levels", {})
    linked = state.get("linked_accounts", {})

    if not levels:
        await interaction.response.send_message("ℹ️ No XP recorded yet. Start chatting to gain XP!", ephemeral=True)
        return

    sorted_users = sorted(levels.items(), key=lambda x: x[1].get("xp", 0), reverse=True)[:10]

    lines = []
    medals = ["🥇", "🥈", "🥉", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"]

    for idx, (uid, data) in enumerate(sorted_users):
        raw_name = data.get("username", f"User <@{uid}>")
        user_ign = linked.get(uid, {}).get("ign")
        name = f"{raw_name} (`{user_ign}`)" if user_ign else raw_name
        lvl = data.get("level", 1)
        xp = data.get("xp", 0)
        medal = medals[idx] if idx < len(medals) else f"**#{idx+1}**"

        badge = ""
        if lvl >= 30:
            badge = "🔥 "
        elif lvl >= 20:
            badge = "👑 "
        elif lvl >= 10:
            badge = "💎 "
        elif lvl >= 5:
            badge = "✨ "

        lines.append(f"{medal} {badge}**{name}** — Level **{lvl}** ({xp:,} XP)")

    embed = discord.Embed(
        title="🏆 The Lads Activity Leaderboard",
        description="\n".join(lines),
        color=0xF1C40F,
        timestamp=datetime.now()
    )
    if interaction.guild.icon:
        embed.set_thumbnail(url=interaction.guild.icon.url)
    embed.set_footer(text="Earn 15-25 XP once a minute by chatting in text channels!")
    await interaction.response.send_message(embed=embed)


# ------------------ CUSTOM TAGS / KNOWLEDGE BASE ------------------

@bot.tree.command(name="tag", description="Retrieve a saved helpful tag or guide.")
@app_commands.describe(name="Tag name (e.g. java, ram, fps, zoom, report)")
async def slash_tag(interaction: discord.Interaction, name: str):
    state = load_state()
    tags = state.get("tags", {})
    tag_name = name.lower()

    if tag_name in tags:
        await interaction.response.send_message(tags[tag_name])
    else:
        avail = ", ".join([f"`{t}`" for t in tags.keys()])
        await interaction.response.send_message(f"❌ Tag `{name}` not found.\n**Available Tags:** {avail}", ephemeral=True)


@bot.tree.command(name="tag_create", description="Create or edit a custom tag.")
@app_commands.describe(name="Tag name", content="Content to display")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_tag_create(interaction: discord.Interaction, name: str, content: str):
    state = load_state()
    tags = state.setdefault("tags", {})
    tags[name.lower()] = content
    save_state(state)
    await interaction.response.send_message(f"✅ Created tag `{name.lower()}`!", ephemeral=True)


@bot.tree.command(name="tag_delete", description="Delete a custom tag.")
@app_commands.describe(name="Tag to delete")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_tag_delete(interaction: discord.Interaction, name: str):
    state = load_state()
    tags = state.setdefault("tags", {})
    if name.lower() in tags:
        del tags[name.lower()]
        save_state(state)
        await interaction.response.send_message(f"🗑️ Deleted tag `{name.lower()}`.", ephemeral=True)
    else:
        await interaction.response.send_message(f"❌ Tag `{name}` not found.", ephemeral=True)


@bot.tree.command(name="tag_list", description="List all available tags.")
async def slash_tag_list(interaction: discord.Interaction):
    state = load_state()
    tags = state.get("tags", {})
    tag_names = [f"`{t}`" for t in tags.keys()]
    embed = discord.Embed(
        title="📚 Community Tags & Guides",
        description=", ".join(tag_names) if tag_names else "No tags saved.",
        color=0x3498DB
    )
    embed.set_footer(text="Use /tag <name> to view any guide!")
    await interaction.response.send_message(embed=embed)


# ------------------ STICKY MESSAGES (CARL-BOT STYLE) ------------------

@bot.tree.command(name="sticky_set", description="Pin an auto-reposting notice to the bottom of a channel.")
@app_commands.describe(message="The message text", channel="Target channel (default: current)")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_sticky_set(interaction: discord.Interaction, message: str, channel: discord.TextChannel = None):
    target = channel or interaction.channel
    state = load_state()
    stickies = state.setdefault("sticky_messages", {})

    # Post initial sticky
    embed = discord.Embed(title="📌 Notice", description=message, color=0x3498DB)
    embed.set_footer(text="Sticky Announcement")
    msg = await target.send(embed=embed)

    stickies[str(target.id)] = {"text": message, "last_msg_id": msg.id}
    save_state(state)

    await interaction.response.send_message(f"✅ Sticky message set in {target.mention}!", ephemeral=True)


@bot.tree.command(name="sticky_clear", description="Remove the sticky message from a channel.")
@app_commands.describe(channel="Target channel (default: current)")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_sticky_clear(interaction: discord.Interaction, channel: discord.TextChannel = None):
    target = channel or interaction.channel
    state = load_state()
    stickies = state.setdefault("sticky_messages", {})
    ch_key = str(target.id)

    if ch_key in stickies:
        last_id = stickies[ch_key].get("last_msg_id")
        if last_id:
            try:
                old = await target.fetch_message(last_id)
                await old.delete()
            except Exception:
                pass
        del stickies[ch_key]
        save_state(state)
        await interaction.response.send_message(f"🗑️ Sticky message removed from {target.mention}!", ephemeral=True)
    else:
        await interaction.response.send_message(f"ℹ️ No sticky message active in {target.mention}.", ephemeral=True)


# ------------------ GIVEAWAY SYSTEM (GIVEAWAYBOT STYLE) ------------------

@bot.tree.command(name="giveaway_start", description="Start an interactive giveaway with timer and buttons.")
@app_commands.describe(prize="Prize description", duration_minutes="Duration in minutes", winners="Number of winners")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_giveaway_start(interaction: discord.Interaction, prize: str, duration_minutes: app_commands.Range[int, 1, 43200], winners: app_commands.Range[int, 1, 20] = 1):
    ends_at = datetime.now(timezone.utc) + timedelta(minutes=duration_minutes)
    unix_end = int(ends_at.timestamp())

    embed = discord.Embed(
        title="🎉 GIVEAWAY 🎉",
        description=(
            f"**Prize:** {prize}\n"
            f"**Hosted By:** {interaction.user.mention}\n"
            f"**Winners:** {winners}\n"
            f"**Ends:** <t:{unix_end}:R> (<t:{unix_end}:f>)\n\n"
            "Click **[ 🎉 Enter Giveaway ]** below to participate!"
        ),
        color=0xE91E63
    )
    embed.set_footer(text="The Lads Community Giveaway")

    await interaction.response.send_message("Creating giveaway...", ephemeral=True)
    msg = await interaction.channel.send(embed=embed)

    view = GiveawayView(message_id=str(msg.id), count=0)
    await msg.edit(view=view)

    state = load_state()
    state.setdefault("giveaways", {})[str(msg.id)] = {
        "prize": prize,
        "ends_at": ends_at.isoformat(),
        "winners": winners,
        "participants": [],
        "channel_id": interaction.channel_id
    }
    save_state(state)


@bot.tree.command(name="giveaway_end", description="End a giveaway early and pick the winners.")
@app_commands.describe(message_id="ID of the giveaway message")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_giveaway_end(interaction: discord.Interaction, message_id: str):
    await interaction.response.defer(ephemeral=True)
    if await end_giveaway(message_id.strip()):
        await interaction.followup.send("✅ Giveaway ended successfully!", ephemeral=True)
    else:
        await interaction.followup.send("❌ Giveaway not found or already completed.", ephemeral=True)


# ------------------ WORD BLACKLIST (DYNO STYLE) ------------------

@bot.tree.command(name="blacklist_add", description="Add a word to the AutoMod word filter.")
@app_commands.describe(word="Word to block")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_blacklist_add(interaction: discord.Interaction, word: str):
    state = load_state()
    bl = set(state.get("blacklisted_words", []))
    bl.add(word.lower().strip())
    state["blacklisted_words"] = list(bl)
    save_state(state)
    await interaction.response.send_message(f"✅ Added `{word.lower().strip()}` to the word blacklist.", ephemeral=True)


@bot.tree.command(name="blacklist_remove", description="Remove a word from the AutoMod word filter.")
@app_commands.describe(word="Word to remove")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_blacklist_remove(interaction: discord.Interaction, word: str):
    state = load_state()
    bl = set(state.get("blacklisted_words", []))
    if word.lower().strip() in bl:
        bl.remove(word.lower().strip())
        state["blacklisted_words"] = list(bl)
        save_state(state)
        await interaction.response.send_message(f"🗑️ Removed `{word.lower().strip()}` from blacklist.", ephemeral=True)
    else:
        await interaction.response.send_message(f"❌ Word `{word}` not in blacklist.", ephemeral=True)


@bot.tree.command(name="blacklist_list", description="List all blacklisted words.")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_blacklist_list(interaction: discord.Interaction):
    state = load_state()
    bl = state.get("blacklisted_words", [])
    if not bl:
        await interaction.response.send_message("ℹ️ No words currently blacklisted.", ephemeral=True)
        return
    words_str = ", ".join([f"`{w}`" for w in bl])
    await interaction.response.send_message(f"🛡️ **Blacklisted Words ({len(bl)}):**\n{words_str}", ephemeral=True)


# ------------------ MODERATION SUITE (SLASH COMMANDS) ------------------

def can_act(interaction: discord.Interaction, target: discord.Member | discord.Role) -> tuple[bool, str]:
    """Ensure the invoking user can act on the target member or role according to Discord hierarchy."""
    guild = interaction.guild
    invoker = interaction.user
    if not guild or not isinstance(invoker, discord.Member):
        return False, "This command can only be used in a server."

    is_owner = (guild.owner_id == invoker.id)

    if isinstance(target, discord.Role):
        if target.is_default():  # @everyone
            return False, "Cannot modify the default @everyone role."
        if target.managed:
            return False, "Cannot modify bot-managed or integration roles."
        if not is_owner and invoker.top_role <= target:
            return False, f"You cannot manage {target.mention} because your top role is not higher than it."
        bot_member = guild.me
        if bot_member and bot_member.top_role <= target:
            return False, f"I cannot manage {target.mention} because my top role is not higher than it."
        return True, ""

    elif isinstance(target, discord.Member):
        if target.id == invoker.id:
            return False, "You cannot target yourself with this moderation action."
        if target.id == guild.owner_id:
            return False, "You cannot moderate the server owner."
        if not is_owner and invoker.top_role <= target.top_role:
            return False, f"You cannot moderate {target.mention} because their top role is higher than or equal to yours."
        bot_member = guild.me
        if bot_member and bot_member.top_role <= target.top_role:
            return False, f"I cannot moderate {target.mention} because their top role is higher than or equal to mine."
        return True, ""

    return False, "Invalid target."


@bot.tree.command(name="ban", description="Ban a member from the server.")
@app_commands.describe(user="Member to ban", reason="Reason for ban", delete_days="Days of message history to delete (0-7)")
@app_commands.checks.has_permissions(ban_members=True)
async def slash_ban(interaction: discord.Interaction, user: discord.Member, reason: str = "No reason provided", delete_days: int = 0):
    allowed, msg = can_act(interaction, user)
    if not allowed:
        await interaction.response.send_message(f"\u274c {msg}", ephemeral=True)
        return
    try:
        await user.ban(reason=reason, delete_message_days=min(max(delete_days, 0), 7))
        embed = discord.Embed(
            title="🔨 Member Banned",
            description=f"**User:** {user.mention} (`{user.id}`)\n**Moderator:** {interaction.user.mention}\n**Reason:** {reason}",
            color=0xE74C3C,
            timestamp=datetime.now()
        )
        await interaction.response.send_message(embed=embed)
        await log_to_modlogs(interaction.guild, embed)
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to ban user: {e}", ephemeral=True)


@bot.tree.command(name="unban", description="Unban a user by their User ID.")
@app_commands.describe(user_id="Discord ID of user to unban", reason="Reason for unban")
@app_commands.checks.has_permissions(ban_members=True)
async def slash_unban(interaction: discord.Interaction, user_id: str, reason: str = "No reason provided"):
    try:
        user_obj = await bot.fetch_user(int(user_id))
        await interaction.guild.unban(user_obj, reason=reason)
        embed = discord.Embed(
            title="🔓 Member Unbanned",
            description=f"**User:** {user_obj.mention} (`{user_obj.id}`)\n**Moderator:** {interaction.user.mention}\n**Reason:** {reason}",
            color=0x2ECC71,
            timestamp=datetime.now()
        )
        await interaction.response.send_message(embed=embed)
        await log_to_modlogs(interaction.guild, embed)
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to unban: {e}", ephemeral=True)


@bot.tree.command(name="kick", description="Kick a member from the server.")
@app_commands.describe(user="Member to kick", reason="Reason for kick")
@app_commands.checks.has_permissions(kick_members=True)
async def slash_kick(interaction: discord.Interaction, user: discord.Member, reason: str = "No reason provided"):
    allowed, msg = can_act(interaction, user)
    if not allowed:
        await interaction.response.send_message(f"\u274c {msg}", ephemeral=True)
        return
    try:
        await user.kick(reason=reason)
        embed = discord.Embed(
            title="👢 Member Kicked",
            description=f"**User:** {user.mention} (`{user.id}`)\n**Moderator:** {interaction.user.mention}\n**Reason:** {reason}",
            color=0xE67E22,
            timestamp=datetime.now()
        )
        await interaction.response.send_message(embed=embed)
        await log_to_modlogs(interaction.guild, embed)
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to kick user: {e}", ephemeral=True)


@bot.tree.command(name="timeout", description="Timeout (mute) a member for a given duration.")
@app_commands.describe(user="Member to timeout", minutes="Duration in minutes", reason="Reason for timeout")
@app_commands.checks.has_permissions(moderate_members=True)
async def slash_timeout(interaction: discord.Interaction, user: discord.Member, minutes: int, reason: str = "No reason provided"):
    allowed, msg = can_act(interaction, user)
    if not allowed:
        await interaction.response.send_message(f"\u274c {msg}", ephemeral=True)
        return
    try:
        duration = timedelta(minutes=minutes)
        await user.timeout(duration, reason=reason)
        embed = discord.Embed(
            title="⏳ Member Timed Out",
            description=f"**User:** {user.mention} (`{user.id}`)\n**Duration:** {minutes} minute(s)\n**Moderator:** {interaction.user.mention}\n**Reason:** {reason}",
            color=0xF1C40F,
            timestamp=datetime.now()
        )
        await interaction.response.send_message(embed=embed)
        await log_to_modlogs(interaction.guild, embed)
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to timeout: {e}", ephemeral=True)


@bot.tree.command(name="untimeout", description="Remove timeout from a member.")
@app_commands.describe(user="Member to remove timeout from", reason="Reason")
@app_commands.checks.has_permissions(moderate_members=True)
async def slash_untimeout(interaction: discord.Interaction, user: discord.Member, reason: str = "Timeout removed"):
    allowed, msg = can_act(interaction, user)
    if not allowed:
        await interaction.response.send_message(f"\u274c {msg}", ephemeral=True)
        return
    try:
        await user.timeout(None, reason=reason)
        embed = discord.Embed(
            title="🔊 Timeout Removed",
            description=f"**User:** {user.mention} (`{user.id}`)\n**Moderator:** {interaction.user.mention}\n**Reason:** {reason}",
            color=0x2ECC71,
            timestamp=datetime.now()
        )
        await interaction.response.send_message(embed=embed)
        await log_to_modlogs(interaction.guild, embed)
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to remove timeout: {e}", ephemeral=True)


@bot.tree.command(name="warn", description="Issue a formal warning to a member.")
@app_commands.describe(user="Member to warn", reason="Reason for warning")
@app_commands.checks.has_permissions(moderate_members=True)
async def slash_warn(interaction: discord.Interaction, user: discord.Member, reason: str):
    state = load_state()
    warnings = state.setdefault("warnings", {})
    uid = str(user.id)
    user_warns = warnings.setdefault(uid, [])

    warn_record = {
        "moderator": interaction.user.name,
        "moderator_id": interaction.user.id,
        "reason": reason,
        "timestamp": datetime.now().isoformat()
    }
    user_warns.append(warn_record)
    save_state(state)

    try:
        dm_embed = discord.Embed(
            title=f"⚠️ Warning Received in {interaction.guild.name}",
            description=f"You have been warned by staff.\n**Reason:** {reason}",
            color=0xE67E22
        )
        await user.send(embed=dm_embed)
    except Exception:
        pass

    embed = discord.Embed(
        title="⚠️ Member Warned",
        description=f"**User:** {user.mention} (`{user.id}`)\n**Moderator:** {interaction.user.mention}\n**Total Warnings:** {len(user_warns)}\n**Reason:** {reason}",
        color=0xE67E22,
        timestamp=datetime.now()
    )
    await interaction.response.send_message(embed=embed)
    await log_to_modlogs(interaction.guild, embed)


@bot.tree.command(name="warnings", description="View warning history for a member.")
@app_commands.describe(user="Member to check")
@app_commands.checks.has_permissions(moderate_members=True)
async def slash_warnings(interaction: discord.Interaction, user: discord.Member):
    state = load_state()
    user_warns = state.get("warnings", {}).get(str(user.id), [])

    if not user_warns:
        await interaction.response.send_message(f"✅ {user.mention} has a clean record with 0 warnings.", ephemeral=True)
        return

    embed = discord.Embed(
        title=f"📋 Warnings for {user.name} ({len(user_warns)} total)",
        color=0xE67E22,
        timestamp=datetime.now()
    )
    recent_warns = user_warns[-20:]
    start_num = len(user_warns) - len(recent_warns) + 1
    for idx, w in enumerate(recent_warns, start=start_num):
        dt = w.get("timestamp", "")[:10]
        embed.add_field(
            name=f"#{idx} | {dt} by {w.get('moderator', 'Staff')}"[:256],
            value=w.get("reason", "No reason")[:1024],
            inline=False
        )
    await interaction.response.send_message(embed=embed, ephemeral=True)


@bot.tree.command(name="clearwarnings", description="Clear all warnings for a member.")
@app_commands.describe(user="Member to clear warnings for")
@app_commands.checks.has_permissions(moderate_members=True)
async def slash_clearwarnings(interaction: discord.Interaction, user: discord.Member):
    state = load_state()
    if str(user.id) in state.get("warnings", {}):
        del state["warnings"][str(user.id)]
        save_state(state)
        await interaction.response.send_message(f"✅ Cleared all warnings for {user.mention}!")
    else:
        await interaction.response.send_message(f"ℹ️ {user.mention} has no warnings to clear.", ephemeral=True)


@bot.tree.command(name="purge", description="Bulk delete messages in the current channel.")
@app_commands.describe(amount="Number of messages to delete (1-100)", user="Optional: only delete messages from this user")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_purge(interaction: discord.Interaction, amount: int, user: discord.Member = None):
    if amount < 1 or amount > 100:
        await interaction.response.send_message("❌ Amount must be between 1 and 100.", ephemeral=True)
        return

    await interaction.response.defer(ephemeral=True)
    check_fn = (lambda m: m.author == user) if user else (lambda m: True)  # purge() calls check(); None crashed it
    deleted = await interaction.channel.purge(limit=amount, check=check_fn)

    embed = discord.Embed(
        title="🧹 Messages Purged",
        description=f"**Channel:** {interaction.channel.mention}\n**Deleted:** {len(deleted)} message(s)\n**Moderator:** {interaction.user.mention}" + (f"\n**Target:** {user.mention}" if user else ""),
        color=0x3498DB,
        timestamp=datetime.now()
    )
    await log_to_modlogs(interaction.guild, embed)
    await interaction.followup.send(f"✅ Successfully deleted {len(deleted)} message(s)!", ephemeral=True)


@bot.tree.command(name="slowmode", description="Set channel slowmode delay.")
@app_commands.describe(seconds="Slowmode in seconds (0 to disable, max 21600)")
@app_commands.checks.has_permissions(manage_channels=True)
async def slash_slowmode(interaction: discord.Interaction, seconds: int):
    seconds = max(0, min(seconds, 21600))
    await interaction.channel.edit(slowmode_delay=seconds)
    if seconds == 0:
        await interaction.response.send_message("⚡ Slowmode has been disabled for this channel.")
    else:
        await interaction.response.send_message(f"🐢 Slowmode set to `{seconds}` second(s) for this channel.")


@bot.tree.command(name="lock", description="Lock down the current channel so members cannot send messages.")
@app_commands.checks.has_permissions(manage_channels=True)
async def slash_lock(interaction: discord.Interaction):
    overwrite = interaction.channel.overwrites_for(interaction.guild.default_role)
    overwrite.send_messages = False
    await interaction.channel.set_permissions(interaction.guild.default_role, overwrite=overwrite)
    await interaction.response.send_message("🔒 **Channel locked.** Regular members can no longer send messages.")


@bot.tree.command(name="unlock", description="Unlock the current channel.")
@app_commands.checks.has_permissions(manage_channels=True)
async def slash_unlock(interaction: discord.Interaction):
    overwrite = interaction.channel.overwrites_for(interaction.guild.default_role)
    overwrite.send_messages = True
    await interaction.channel.set_permissions(interaction.guild.default_role, overwrite=overwrite)
    await interaction.response.send_message("🔓 **Channel unlocked.** Regular members can now send messages.")


# ------------------ MINECRAFT & CLIENT COMMANDS ------------------

@bot.tree.command(name="download", description="Get download links and setup instructions for The Lads Client.")
async def slash_download(interaction: discord.Interaction):
    embed = discord.Embed(
        title="📥 Download The Lads Client",
        description=(
            "Get the latest build of **The Lads Client**!\n\n"
            "**Supported Versions:** Minecraft 1.8.9, 1.20.1 (Forge via Connector), 26.3\n"
            "**Recommended Java:** Java 17 or Java 21 (64-bit)\n\n"
            "🔗 **Official Downloads:**\n"
            "• [Download Windows Installer (.exe)](https://github.com/ArashYT/TheLadsClient/releases/latest)\n"
            "• [Download Portable ZIP Archive](https://github.com/ArashYT/TheLadsClient/releases/latest)\n\n"
            "*Need help? Open a ticket in <#1556193359394250752>!*"
        ),
        color=0x1ABC9C,
        timestamp=datetime.now()
    )
    if interaction.guild.icon:
        embed.set_thumbnail(url=interaction.guild.icon.url)
    embed.set_footer(text="The Lads Client • Official Builds")
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="mcstatus", description="Check the status and player count of any Minecraft server.")
@app_commands.describe(address="Server address / IP (e.g. hypixel.net)")
async def slash_mcstatus(interaction: discord.Interaction, address: str):
    await interaction.response.defer()
    clean_addr = address.strip()
    if not re.match(r"^[a-zA-Z0-9.-]+(?::\d{1,5})?$", clean_addr):
        await interaction.followup.send("❌ Invalid server address format. Use a valid hostname or IP (e.g. `hypixel.net`).", ephemeral=True)
        return
    quoted_addr = urllib.parse.quote(clean_addr, safe=":")
    url = f"https://api.mcsrvstat.us/3/{quoted_addr}"
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=8)) as resp:
                if resp.status != 200:
                    await interaction.followup.send(f"❌ Failed to reach status API for `{clean_addr}`.")
                    return
                data = await resp.json()

        online = data.get("online", False)
        if not online:
            embed = discord.Embed(
                title=f"🔴 {clean_addr} is Offline",
                description="The server did not respond to ping or is currently offline.",
                color=0xE74C3C
            )
            await interaction.followup.send(embed=embed)
            return

        players_online = data.get("players", {}).get("online", 0)
        players_max = data.get("players", {}).get("max", 0)
        version = data.get("version", "Unknown")
        motd_list = data.get("motd", {}).get("clean", [])
        motd = "\n".join(motd_list) if motd_list else "No MOTD"

        embed = discord.Embed(
            title=f"🟢 {clean_addr} is Online!",
            description=f"```text\n{motd}\n```",
            color=0x2ECC71,
            timestamp=datetime.now()
        )
        embed.add_field(name="👥 Players", value=f"**{players_online:,}** / {players_max:,}", inline=True)
        embed.add_field(name="🏷️ Version", value=f"`{version}`", inline=True)
        embed.add_field(name="🌐 Host / IP", value=f"`{data.get('ip', clean_addr)}`", inline=True)
        icon_url = f"https://api.mcsrvstat.us/icon/{quoted_addr}"
        embed.set_thumbnail(url=icon_url)

        await interaction.followup.send(embed=embed)
    except Exception as e:
        await interaction.followup.send(f"❌ Error checking server status: {e}")


@bot.tree.command(name="skin", description="View and download a Minecraft player's skin.")
@app_commands.describe(player="Minecraft Username (defaults to your linked account)")
async def slash_skin(interaction: discord.Interaction, player: str = None):
    await interaction.response.defer()
    target_player = player.strip() if player else None
    if not target_player:
        state = load_state()
        linked = state.get("linked_accounts", {}).get(str(interaction.user.id))
        if linked:
            target_player = linked.get("ign")
        else:
            await interaction.followup.send("💡 Please specify a player: `/skin player:<name>`, or link your account with `/link <ign> <code>` to view your skin anytime!", ephemeral=True)
            return

    if not re.match(r"^[a-zA-Z0-9_]{2,16}$", target_player):
        await interaction.followup.send(f"❌ Invalid Minecraft username format: `{target_player}`.", ephemeral=True)
        return
    quoted_player = urllib.parse.quote(target_player, safe="")

    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"https://api.mojang.com/users/profiles/minecraft/{quoted_player}") as resp:
                if resp.status != 200:
                    await interaction.followup.send(f"❌ Minecraft player `{target_player}` not found.")
                    return
                data = await resp.json()

        uuid = data["id"]
        exact_name = data["name"]

        embed = discord.Embed(
            title=f"🎮 Minecraft Skin — {exact_name}",
            description=f"[Download Texture](https://mc-heads.net/download/{uuid}) • [View on NameMC](https://namemc.com/profile/{exact_name})",
            color=0x3498DB,
            timestamp=datetime.now()
        )
        embed.set_image(url=f"https://mc-heads.net/body/{uuid}/right")
        embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
        embed.set_footer(text=f"UUID: {uuid}")
        await interaction.followup.send(embed=embed)
    except Exception as e:
        await interaction.followup.send(f"❌ Error looking up skin: {e}")


@bot.tree.command(name="cape", description="View a Minecraft player's cape.")
@app_commands.describe(player="Minecraft Username (defaults to your linked account)")
async def slash_cape(interaction: discord.Interaction, player: str = None):
    await interaction.response.defer()
    target_player = player.strip() if player else None
    if not target_player:
        state = load_state()
        linked = state.get("linked_accounts", {}).get(str(interaction.user.id))
        if linked:
            target_player = linked.get("ign")
        else:
            await interaction.followup.send("💡 Please specify a player: `/cape player:<name>`, or link your account with `/link <ign> <code>` to view your cape anytime!", ephemeral=True)
            return

    if not re.match(r"^[a-zA-Z0-9_]{2,16}$", target_player):
        await interaction.followup.send(f"❌ Invalid Minecraft username format: `{target_player}`.", ephemeral=True)
        return
    quoted_player = urllib.parse.quote(target_player, safe="")

    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"https://api.mojang.com/users/profiles/minecraft/{quoted_player}") as resp:
                if resp.status != 200:
                    await interaction.followup.send(f"❌ Minecraft player `{target_player}` not found.")
                    return
                data = await resp.json()

        uuid = data["id"]
        exact_name = data["name"]

        cape_url = None
        cape_type = None

        # 1. Check official Mojang session textures
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(f"https://sessionserver.mojang.com/session/minecraft/profile/{urllib.parse.quote(uuid, safe='')}") as s_resp:
                    if s_resp.status == 200:
                        s_data = await s_resp.json()
                        for prop in s_data.get("properties", []):
                            if prop.get("name") == "textures":
                                tex_json = json.loads(base64.b64decode(prop["value"]).decode("utf-8"))
                                capes = tex_json.get("textures", {}).get("CAPE")
                                if capes and "url" in capes:
                                    cape_url = capes["url"].replace("http://", "https://")
                                    cape_type = "Official Mojang Cape"
        except Exception:
            pass

        # 2. Check for OptiFine cape if no Mojang cape found
        if not cape_url:
            try:
                optifine_url = f"http://s.optifine.net/capes/{urllib.parse.quote(exact_name, safe='')}.png"
                async with aiohttp.ClientSession() as session:
                    async with session.get(optifine_url, timeout=aiohttp.ClientTimeout(total=4)) as of_resp:
                        if of_resp.status == 200 and "image" in of_resp.headers.get("content-type", ""):
                            cape_url = optifine_url
                            cape_type = "OptiFine Cape"
            except Exception:
                pass

        # 3. Build response embed without broken images
        if cape_url:
            embed = discord.Embed(
                title=f"🧣 {cape_type} — {exact_name}",
                description=f"[Download Texture]({cape_url}) • [View on NameMC](https://namemc.com/profile/{exact_name})",
                color=0x9B59B6,
                timestamp=datetime.now()
            )
            embed.set_image(url=cape_url)
            embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
            embed.set_footer(text=f"UUID: {uuid}")
        else:
            embed = discord.Embed(
                title=f"🧣 Minecraft Cape — {exact_name}",
                description=(
                    f"**{exact_name}** does not have an active Mojang or OptiFine cape equipped.\n\n"
                    f"• [Check Profile on NameMC](https://namemc.com/profile/{exact_name})\n"
                    "• [Browse Minecraft Capes](https://namemc.com/capes)"
                ),
                color=0x95A5A6,
                timestamp=datetime.now()
            )
            embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
            embed.set_footer(text=f"UUID: {uuid}")

        await interaction.followup.send(embed=embed)
    except Exception as e:
        await interaction.followup.send(f"❌ Error looking up cape: {e}")


# ------------------ MINECRAFT ACCOUNT LINKING ------------------

def _compute_link_code(uuid: str, user_id_str: str = "") -> str:
    """Compute a deterministic HMAC-SHA256 verification code for a Minecraft UUID (+ optional Discord user ID)."""
    clean_uuid = uuid.replace("-", "").lower()
    msg = f"lads-link:{clean_uuid}:{user_id_str}" if user_id_str else f"lads-link:{clean_uuid}"
    return hmac.new(BOT_TOKEN.encode("utf-8"), msg.encode("utf-8"), hashlib.sha256).hexdigest()[:8].upper()


def _check_local_launcher_account(uuid: str, code: str) -> bool:
    """Check if ~/.theladsclient/lads_accounts.json contains a verified account matching uuid and HMAC/code."""
    clean_uuid = uuid.replace("-", "").lower()
    candidates = [
        Path.home() / "AppData" / "Roaming" / ".theladsclient" / "lads_accounts.json",
        Path.home() / ".theladsclient" / "lads_accounts.json",
    ]
    for path in candidates:
        if not path.exists():
            continue
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
            accounts = raw if isinstance(raw, list) else raw.get("accounts", raw.get("Accounts", []))
            if isinstance(accounts, dict):
                accounts = list(accounts.values())
            for acc in accounts:
                if not isinstance(acc, dict):
                    continue
                acc_uuid = str(acc.get("uuid") or acc.get("Uuid") or acc.get("id") or "").replace("-", "").lower()
                if acc_uuid == clean_uuid:
                    expected = hmac.new(BOT_TOKEN.encode("utf-8"), f"lads-account:{clean_uuid}".encode("utf-8"), hashlib.sha256).hexdigest()[:8].upper()
                    if hmac.compare_digest(code, expected):
                        return True
        except Exception:
            pass
    return False


@bot.tree.command(name="link", description="Link your Minecraft account to your Discord profile.")
@app_commands.describe(
    ign="Your Minecraft In-Game Name",
    code="Verification code from The Lads Client / Staff (required to complete linking)"
)
async def slash_link(interaction: discord.Interaction, ign: str, code: str = None):
    await interaction.response.defer(ephemeral=True)
    clean_ign = ign.strip()

    if not re.match(r"^[a-zA-Z0-9_]{2,16}$", clean_ign):
        await interaction.followup.send(f"❌ Invalid Minecraft username format: `{clean_ign}`. Minecraft usernames are 3-16 alphanumeric characters.", ephemeral=True)
        return

    quoted_ign = urllib.parse.quote(clean_ign, safe="")
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"https://api.mojang.com/users/profiles/minecraft/{quoted_ign}") as resp:
                if resp.status in (404, 204):
                    await interaction.followup.send(f"❌ Minecraft account `{clean_ign}` does not exist on Mojang's servers. Please verify spelling.", ephemeral=True)
                    return
                elif resp.status != 200:
                    await interaction.followup.send(f"❌ Mojang API returned status {resp.status}. Please try again shortly.", ephemeral=True)
                    return
                data = await resp.json()
    except Exception as e:
        await interaction.followup.send(f"❌ Network error contacting Mojang API: {e}", ephemeral=True)
        return

    uuid = data["id"]
    exact_name = data["name"]
    formatted_uuid = f"{uuid[0:8]}-{uuid[8:12]}-{uuid[12:16]}-{uuid[16:20]}-{uuid[20:]}"

    state = load_state()
    linked = state.setdefault("linked_accounts", {})
    pending = state.setdefault("pending_links", {})
    user_id_str = str(interaction.user.id)

    # Prevent duplicate claims across different Discord users
    for uid, acc in linked.items():
        if acc.get("uuid") == uuid and uid != user_id_str:
            await interaction.followup.send(
                f"❌ The Minecraft account **`{exact_name}`** is already linked to <@{uid}>.\n"
                f"If you own this account and believe this is an error, please open a ticket in <#1556193359394250752> (`#tech-support`).",
                ephemeral=True
            )
            return

    expected_user_code = _compute_link_code(uuid, user_id_str)
    expected_uuid_code = _compute_link_code(uuid)
    pending_entry = pending.get(user_id_str, {})
    pending_code = pending_entry.get("code", "") if pending_entry.get("uuid") == uuid else ""

    clean_code = code.strip().upper().removeprefix("LADS-") if code else ""
    verified = False
    if clean_code:
        # Check cosmetics in-game link code and grant Discord cloak
        try:
            import cosmetics
            store = cosmetics._DEFAULT_STORE or cosmetics.CosmeticsStore()
            consumed_uuid = await store.consume_link_code(clean_code)
            if consumed_uuid and consumed_uuid.lower() == uuid.lower():
                verified = True
                try:
                    await store.grant(uuid, "lads:cape_discord", source="discord-link")
                except Exception as _ge:
                    print(f"Note: error granting discord cape: {_ge}", flush=True)
        except Exception as _ce:
            print(f"Note: cosmetics link code check error: {_ce}", flush=True)

        if not verified and (
            hmac.compare_digest(clean_code, expected_user_code)
            or hmac.compare_digest(clean_code, expected_uuid_code)
            or (pending_code and hmac.compare_digest(clean_code, pending_code))
            or _check_local_launcher_account(uuid, clean_code)
        ):
            verified = True

    if not verified:
        pending[user_id_str] = {
            "ign": exact_name,
            "uuid": uuid,
            "code": expected_user_code,
            "requested_at": datetime.now(timezone.utc).isoformat()
        }
        save_state(state)

        if interaction.guild:
            req_embed = discord.Embed(
                title="🔐 Pending Minecraft Link Verification",
                description=(
                    f"**User:** {interaction.user.mention} (`{interaction.user.id}`)\n"
                    f"**Requested IGN:** `{exact_name}` (`{formatted_uuid}`)\n"
                    f"**Verification Code:** `LADS-{expected_user_code}`"
                ),
                color=0xF1C40F,
                timestamp=datetime.now()
            )
            await log_to_modlogs(interaction.guild, req_embed)

        msg = (
            f"🔐 **Ownership Verification Required for `{exact_name}`**\n\n"
            "To prevent unauthenticated IGN squatting, linking a Minecraft account requires a verification code.\n"
            f"• Run `/link ign:{exact_name} code:<VerificationCode>` using the code from **The Lads Launcher** or approved by staff in <#1556193359394250752>."
        )
        if clean_code:
            msg = f"❌ Invalid verification code `{code}` for **`{exact_name}`**.\n\n" + msg
        await interaction.followup.send(msg, ephemeral=True)
        return

    # Remove pending entry and save verified link to state
    pending.pop(user_id_str, None)
    linked[user_id_str] = {
        "ign": exact_name,
        "uuid": uuid,
        "linked_at": datetime.now(timezone.utc).isoformat(),
        "verified": True
    }
    save_state(state)

    # Assign 🎮 Linked role
    role_added = False
    linked_role = discord.utils.get(interaction.guild.roles, name="🎮 Linked")
    if not linked_role:
        try:
            linked_role = await interaction.guild.create_role(
                name="🎮 Linked",
                color=discord.Color.from_rgb(46, 204, 113),
                reason="Automatic Linked role for Minecraft accounts"
            )
        except Exception:
            pass

    if linked_role and linked_role not in interaction.user.roles:
        try:
            await interaction.user.add_roles(linked_role, reason=f"Linked Minecraft account {exact_name}")
            role_added = True
        except Exception as e:
            print(f"Could not assign Linked role: {e}", flush=True)

    # Try syncing nickname
    nick_changed = False
    try:
        if interaction.user.display_name != exact_name:
            await interaction.user.edit(nick=exact_name, reason=f"Sync nickname to Minecraft IGN {exact_name}")
            nick_changed = True
    except Exception:
        pass

    embed = discord.Embed(
        title="🎮 Minecraft Account Linked!",
        description=f"Successfully linked {interaction.user.mention} to **`{exact_name}`**!",
        color=0x2ECC71,
        timestamp=datetime.now()
    )
    embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
    embed.set_image(url=f"https://mc-heads.net/body/{uuid}/right")
    embed.add_field(name="👤 Minecraft IGN", value=f"**`{exact_name}`**", inline=True)
    embed.add_field(name="🆔 Mojang UUID", value=f"`{formatted_uuid}`", inline=True)
    embed.add_field(name="🔗 NameMC", value=f"[namemc.com/profile/{exact_name}](https://namemc.com/profile/{exact_name})", inline=True)

    status_notes = []
    if role_added or (linked_role and linked_role in interaction.guild.roles and linked_role in interaction.user.roles):
        status_notes.append("• Granted **`🎮 Linked`** role")
    if nick_changed:
        status_notes.append(f"• Server nickname updated to **`{exact_name}`**")
    status_notes.append("• Linked to client stats & personalized `/skin`, `/cape`, and `/rank`")

    embed.add_field(name="✨ Verification Status", value="\n".join(status_notes), inline=False)
    embed.set_footer(text="The Lads Minecraft Verification • Type /unlink to detach")

    await interaction.followup.send(embed=embed)


@bot.tree.command(name="unlink", description="Unlink your Minecraft account from your Discord profile.")
async def slash_unlink(interaction: discord.Interaction):
    state = load_state()
    linked = state.setdefault("linked_accounts", {})
    uid = str(interaction.user.id)

    if uid not in linked:
        await interaction.response.send_message("ℹ️ You don't have a linked Minecraft account. Use `/link <ign>` to link one!", ephemeral=True)
        return

    old_ign = linked[uid].get("ign", "Unknown")
    del linked[uid]
    save_state(state)

    linked_role = discord.utils.get(interaction.guild.roles, name="🎮 Linked")
    if linked_role and linked_role in interaction.user.roles:
        try:
            await interaction.user.remove_roles(linked_role, reason="Unlinked Minecraft account")
        except Exception:
            pass

    embed = discord.Embed(
        title="🔓 Minecraft Account Unlinked",
        description=f"Your Discord account has been unlinked from **`{old_ign}`**.\nYour `🎮 Linked` role has been removed.",
        color=0xE67E22,
        timestamp=datetime.now()
    )
    embed.set_footer(text="You can link an account anytime with /link <ign>")
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="profile", description="View a player's linked Minecraft profile and community stats.")
@app_commands.describe(user="Discord member to inspect", ign="Minecraft username to search")
async def slash_profile(interaction: discord.Interaction, user: discord.Member = None, ign: str = None):
    await interaction.response.defer()
    state = load_state()
    linked = state.get("linked_accounts", {})
    levels = state.get("levels", {})

    target_ign = None
    target_uuid = None
    target_discord_user = None

    if ign:
        clean_ign = ign.strip()
        if not re.fullmatch(r"^[a-zA-Z0-9_]{1,16}$", clean_ign):
            await interaction.followup.send("\u274c Invalid Minecraft username. Names must be 1-16 letters, numbers, or underscores.")
            return
        quoted_ign = urllib.parse.quote(clean_ign, safe="")
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(f"https://api.mojang.com/users/profiles/minecraft/{quoted_ign}") as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        target_ign = data["name"]
                        target_uuid = data["id"]
        except Exception:
            pass
        if not target_ign:
            await interaction.followup.send(f"❌ Minecraft account `{ign}` not found on Mojang.")
            return

        for uid, acc in linked.items():
            if acc.get("uuid") == target_uuid:
                try:
                    target_discord_user = interaction.guild.get_member(int(uid)) or await bot.fetch_user(int(uid))
                except discord.NotFound:  # linked account was deleted
                    pass
                break
    else:
        target_member = user or interaction.user
        target_discord_user = target_member
        acc = linked.get(str(target_member.id))
        if not acc:
            if target_member == interaction.user:
                await interaction.followup.send("ℹ️ You haven't linked your Minecraft account yet! Use `/link <ign>` to link your account.", ephemeral=True)
            else:
                await interaction.followup.send(f"ℹ️ {target_member.mention} hasn't linked their Minecraft account yet.")
            return
        target_ign = acc["ign"]
        target_uuid = acc["uuid"]

    formatted_uuid = f"{target_uuid[0:8]}-{target_uuid[8:12]}-{target_uuid[12:16]}-{target_uuid[16:20]}-{target_uuid[20:]}"

    embed = discord.Embed(
        title=f"🎮 Minecraft Profile — {target_ign}",
        url=f"https://namemc.com/profile/{target_ign}",
        color=0x1ABC9C,
        timestamp=datetime.now()
    )
    embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{target_uuid}/100")
    embed.set_image(url=f"https://mc-heads.net/body/{target_uuid}/right")

    embed.add_field(name="👤 Username", value=f"**`{target_ign}`**", inline=True)
    embed.add_field(name="🆔 UUID", value=f"`{formatted_uuid}`", inline=True)

    if target_discord_user:
        embed.add_field(name="💬 Discord User", value=f"{target_discord_user.mention} (`{target_discord_user.name}`)", inline=True)
        u_lvl_data = levels.get(str(target_discord_user.id), {})
        lvl = u_lvl_data.get("level", 1)
        xp = u_lvl_data.get("xp", 0)
        msgs = u_lvl_data.get("messages", 0)
        embed.add_field(name="⭐ Server Level", value=f"Level **{lvl}** ({xp:,} XP)", inline=True)
        embed.add_field(name="💬 Messages", value=f"**{msgs:,}** msgs", inline=True)
    else:
        embed.add_field(name="💬 Discord Link", value="*Not linked to this server*", inline=True)

    embed.add_field(name="🌐 NameMC Profile", value=f"[Click here to view skins & history](https://namemc.com/profile/{target_ign})", inline=False)
    embed.set_footer(text="The Lads Community Profile")

    await interaction.followup.send(embed=embed)


@bot.tree.command(name="whois", description="Find out which Discord member owns a Minecraft username.")
@app_commands.describe(ign="Minecraft username")
async def slash_whois(interaction: discord.Interaction, ign: str):
    await interaction.response.defer()
    state = load_state()
    linked = state.get("linked_accounts", {})
    clean_ign = ign.strip().lower()

    found_uid = None
    exact_name = None
    uuid = None
    for uid, acc in linked.items():
        if acc.get("ign", "").lower() == clean_ign:
            found_uid = uid
            exact_name = acc.get("ign")
            uuid = acc.get("uuid")
            break

    if not found_uid:
        await interaction.followup.send(f"❌ No Discord member on this server has linked the Minecraft account **`{ign}`**.", ephemeral=True)
        return

    member = interaction.guild.get_member(int(found_uid))
    embed = discord.Embed(
        title=f"🔍 Player Identification: {exact_name}",
        color=0x3498DB,
        timestamp=datetime.now()
    )
    if uuid:
        embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
    embed.add_field(name="👤 Minecraft IGN", value=f"**`{exact_name}`**", inline=True)
    embed.add_field(name="💬 Discord Account", value=member.mention if member else f"<@{found_uid}>", inline=True)
    if member:
        embed.add_field(name="🏷️ Nickname", value=f"`{member.display_name}`", inline=True)
        embed.add_field(name="📅 Joined Server", value=f"<t:{int(member.joined_at.timestamp())}:R>" if member.joined_at else "Unknown", inline=True)
    await interaction.followup.send(embed=embed)


@bot.tree.command(name="unlink_staff", description="[Staff] Force-unlink a member's Minecraft account.")
@app_commands.describe(user="Discord member to unlink", reason="Reason for unlink")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_unlink_staff(interaction: discord.Interaction, user: discord.Member, reason: str = "Staff reset"):
    state = load_state()
    linked = state.setdefault("linked_accounts", {})
    uid = str(user.id)

    if uid not in linked:
        await interaction.response.send_message(f"ℹ️ {user.mention} does not have a linked Minecraft account.", ephemeral=True)
        return

    old_ign = linked[uid].get("ign", "Unknown")
    del linked[uid]
    save_state(state)

    linked_role = discord.utils.get(interaction.guild.roles, name="🎮 Linked")
    if linked_role and linked_role in user.roles:
        try:
            await user.remove_roles(linked_role, reason=f"Staff unlink by {interaction.user.name}: {reason}")
        except Exception:
            pass

    embed = discord.Embed(
        title="🔨 Minecraft Account Force-Unlinked",
        description=f"Staff {interaction.user.mention} force-unlinked {user.mention} from **`{old_ign}`**.\n**Reason:** {reason}",
        color=0xE74C3C,
        timestamp=datetime.now()
    )
    await interaction.response.send_message(embed=embed)
    await log_to_modlogs(interaction.guild, embed)


@bot.tree.command(name="smp_invite", description="[Staff] Add, remove or list Minecraft usernames invited to The Lads SMP Reborn.")
@app_commands.describe(action="add, remove or list", ign="Minecraft username (not needed for list)")
@app_commands.choices(action=[app_commands.Choice(name=n, value=n) for n in ("add", "remove", "list")])
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_smp_invite(interaction: discord.Interaction, action: app_commands.Choice[str], ign: str = ""):
    await edit_invites(interaction, "lads-smp-reborn-26.3", "The Lads SMP Reborn", action, ign)


@bot.tree.command(name="experimental_invite", description="[Staff] Add, remove or list Minecraft usernames allowed to use the launcher's experimental modes.")
@app_commands.describe(action="add, remove or list", ign="Minecraft username (not needed for list)")
@app_commands.choices(action=[app_commands.Choice(name=n, value=n) for n in ("add", "remove", "list")])
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_experimental_invite(interaction: discord.Interaction, action: app_commands.Choice[str], ign: str = ""):
    await edit_invites(interaction, "experimental", "Experimental modes", action, ign)


async def edit_invites(interaction: discord.Interaction, exp: str, label: str, action: app_commands.Choice[str], ign: str):
    state = load_state()
    invites = state.setdefault("experience_invites", {}).setdefault(exp, [])
    name = ign.strip().lower()
    if action.value != "list" and not re.fullmatch(r"[a-z0-9_]{3,16}", name):
        await interaction.response.send_message("❌ Give a valid Minecraft username.", ephemeral=True)
        return
    if action.value == "add" and name not in invites:
        invites.append(name)
        save_state(state)
        embed = discord.Embed(
            title="\U0001f4cb Experience Invite Added",
            description=f"**Experience:** {label} (`{exp}`)\n**IGN:** `{name}`\n**Staff:** {interaction.user.mention}",
            color=0x2ECC71,
            timestamp=datetime.now()
        )
        await log_to_modlogs(interaction.guild, embed)
    elif action.value == "remove" and name in invites:
        invites.remove(name)
        save_state(state)
        embed = discord.Embed(
            title="\U0001f4cb Experience Invite Removed",
            description=f"**Experience:** {label} (`{exp}`)\n**IGN:** `{name}`\n**Staff:** {interaction.user.mention}",
            color=0xE74C3C,
            timestamp=datetime.now()
        )
        await log_to_modlogs(interaction.guild, embed)
    listing = ", ".join(f"`{n}`" for n in sorted(invites)) or "nobody"
    await interaction.response.send_message(f"✅ {label} invites ({len(invites)}): {listing}", ephemeral=True)


# ------------------ COSMETICS MANAGEMENT (STAFF COMMANDS & UI) ------------------

COSMETICS_DISCORD_MENTION_RE = re.compile(r"^<@!?(\d+)>$")
COSMETICS_DISCORD_ID_RE = re.compile(r"^\d{17,20}$")
COSMETICS_HEX_UUID_RE = re.compile(r"^[0-9a-f]{32}$", re.IGNORECASE)
COSMETICS_HYPHEN_UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.IGNORECASE)
COSMETICS_MC_NAME_RE = re.compile(r"^[A-Za-z0-9_]{1,16}$")


def parse_target_input(target: str) -> tuple[str, str]:
    """Parse target input string into (target_type, cleaned_value).
    target_type is one of: 'discord_id', 'uuid', 'ign', or 'invalid'.
    """
    clean = (target or "").strip()
    m_mention = COSMETICS_DISCORD_MENTION_RE.match(clean)
    if m_mention:
        return "discord_id", m_mention.group(1)
    if COSMETICS_DISCORD_ID_RE.match(clean):
        return "discord_id", clean
    if COSMETICS_HYPHEN_UUID_RE.match(clean):
        return "uuid", clean.replace("-", "").lower()
    if COSMETICS_HEX_UUID_RE.match(clean):
        return "uuid", clean.lower()
    if COSMETICS_MC_NAME_RE.match(clean):
        return "ign", clean
    return "invalid", clean


async def resolve_target(
    target: str,
    linked_accounts: dict,
    session: aiohttp.ClientSession | None = None
) -> tuple[str | None, str | None, str | None, str | None]:
    """Resolves target to (uuid, exact_name, discord_id, error_message).
    Target can be a Discord mention (<@123>), numeric Discord ID, Minecraft IGN, or UUID.
    """
    t_type, val = parse_target_input(target)
    if t_type == "invalid":
        return None, None, None, f"❌ Invalid target `{target}`. Please provide a Minecraft username, UUID, or Discord user."

    if t_type == "discord_id":
        acc = linked_accounts.get(val)
        if not acc:
            return None, None, None, f"❌ Discord user <@{val}> does not have a linked Minecraft account."
        uuid = acc.get("uuid")
        ign = acc.get("ign", "Unknown")
        return uuid, ign, val, None

    own_session = False
    if session is None:
        session = aiohttp.ClientSession()
        own_session = True

    try:
        if t_type == "uuid":
            uuid = val
            disc_id = None
            ign = None
            for uid, acc in linked_accounts.items():
                if acc.get("uuid") == uuid:
                    disc_id = uid
                    ign = acc.get("ign")
                    break
            if not ign:
                try:
                    async with session.get(f"https://sessionserver.mojang.com/session/minecraft/profile/{uuid}", timeout=aiohttp.ClientTimeout(total=5)) as resp:
                        if resp.status == 200:
                            data = await resp.json()
                            ign = data.get("name", uuid)
                        else:
                            ign = uuid
                except Exception:
                    ign = uuid
            return uuid, ign, disc_id, None

        if t_type == "ign":
            clean_ign = val
            try:
                async with session.get(f"https://api.mojang.com/users/profiles/minecraft/{urllib.parse.quote(clean_ign)}", timeout=aiohttp.ClientTimeout(total=5)) as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        uuid = data["id"]
                        exact_name = data["name"]
                        disc_id = None
                        for uid, acc in linked_accounts.items():
                            if acc.get("uuid") == uuid:
                                disc_id = uid
                                break
                        return uuid, exact_name, disc_id, None
                    elif resp.status in (404, 204):
                        return None, None, None, f"❌ Minecraft account `{clean_ign}` was not found on Mojang."
                    else:
                        return None, None, None, f"❌ Mojang API returned status {resp.status} for `{clean_ign}`."
            except Exception as e:
                return None, None, None, f"❌ Error contacting Mojang API: {e}"
    finally:
        if own_session:
            await session.close()

    return None, None, None, "❌ Could not resolve target."


def paginate_items(items: list[Any], page: int = 0, per_page: int = 25) -> tuple[list[Any], int, int]:
    """Paginates a list of items.
    Returns (page_items, total_pages, clamped_current_page).
    Page is 0-indexed.
    """
    if not items:
        return [], 1, 0
    total_pages = max(1, (len(items) + per_page - 1) // per_page)
    clamped_page = max(0, min(page, total_pages - 1))
    start = clamped_page * per_page
    end = start + per_page
    return items[start:end], total_pages, clamped_page


def filter_cosmetics_by_category(items: list[dict], category: str | None = None) -> list[dict]:
    """Filters cosmetic items by category ('Capes', 'Emotes', 'Others', or 'All'/None)."""
    if not category or category.lower() == "all":
        return list(items)
    cat_lower = category.lower()
    return [item for item in items if item.get("category", "").lower() == cat_lower]


class CosmeticsSelectView(LadsView):
    def __init__(
        self,
        mode: str,  # "grant" or "revoke"
        target_uuid: str,
        target_ign: str,
        target_discord_id: str | None,
        admin_user: discord.User | discord.Member,
        items: list[dict],
        store: cosmetics.CosmeticsStore,
        timeout: float = 300.0
    ):
        super().__init__(timeout=timeout)
        self.mode = mode
        self.target_uuid = target_uuid
        self.target_ign = target_ign
        self.target_discord_id = target_discord_id
        self.admin_user = admin_user
        self.items = items
        self.store = store
        self.current_category = "All"
        self.current_page = 0
        self.selected_ids: set[str] = set()
        self._build_components()

    def build_embed(self) -> discord.Embed:
        is_grant = (self.mode == "grant")
        action_verb = "Grant" if is_grant else "Revoke"
        title = f"🎁 {action_verb} Cosmetics: {self.target_ign}" if is_grant else f"🗑️ {action_verb} Cosmetics: {self.target_ign}"
        color = 0x3498DB if is_grant else 0xE67E22

        embed = discord.Embed(
            title=title,
            description=(
                f"Select cosmetics below to {action_verb.lower()} for **`{self.target_ign}`**.\n"
                f"Use the Category dropdown or buttons to switch views, select items, and click **{action_verb} Selected**.\n"
                f"Currently selected: **{len(self.selected_ids)}** cosmetic(s)."
            ),
            color=color,
            timestamp=datetime.now()
        )
        embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{self.target_uuid}/100")
        embed.add_field(name="Target Player", value=f"**`{self.target_ign}`** (`{self.target_uuid}`)", inline=True)
        if self.target_discord_id:
            embed.add_field(name="Discord", value=f"<@{self.target_discord_id}>", inline=True)
        embed.add_field(name="Active View", value=f"Category: **{self.current_category}**", inline=True)
        return embed

    def _build_components(self) -> None:
        self.clear_items()

        # Category Counts
        all_count = len(self.items)
        capes_count = len(filter_cosmetics_by_category(self.items, "Capes"))
        emotes_count = len(filter_cosmetics_by_category(self.items, "Emotes"))
        others_count = len(filter_cosmetics_by_category(self.items, "Others"))

        # Row 0: Category Selector
        category_options = [
            discord.SelectOption(label=f"All Cosmetics ({all_count})", value="All", emoji="🌐", default=(self.current_category == "All")),
            discord.SelectOption(label=f"Capes ({capes_count})", value="Capes", emoji="✨", default=(self.current_category == "Capes")),
            discord.SelectOption(label=f"Emotes ({emotes_count})", value="Emotes", emoji="💃", default=(self.current_category == "Emotes")),
        ]
        if others_count > 0 or self.current_category == "Others":
            category_options.append(
                discord.SelectOption(label=f"Others ({others_count})", value="Others", emoji="🎩", default=(self.current_category == "Others"))
            )

        cat_select = discord.ui.Select(
            placeholder="Filter by Category...",
            options=category_options,
            row=0
        )
        cat_select.callback = self._on_category_select
        self.add_item(cat_select)

        # Filter items and Paginate
        filtered = filter_cosmetics_by_category(self.items, self.current_category)
        page_items, total_pages, self.current_page = paginate_items(filtered, self.current_page, 25)

        # Row 1: Cosmetics Multi-Select Menu
        if page_items:
            cosmetic_options = []
            for item in page_items:
                cid = item["id"]
                cat = item.get("category", "Cosmetic")
                emoji = "✨" if cat == "Capes" else ("💃" if cat == "Emotes" else "🎩")
                is_selected = cid in self.selected_ids
                cosmetic_options.append(
                    discord.SelectOption(
                        label=item.get("name", cid)[:100],
                        value=cid,
                        description=f"[{cat}] {cid}"[:100],
                        emoji=emoji,
                        default=is_selected
                    )
                )

            cosm_select = discord.ui.Select(
                placeholder=f"Select cosmetics to {self.mode} (Page {self.current_page + 1}/{total_pages})...",
                options=cosmetic_options,
                min_values=0,
                max_values=min(len(cosmetic_options), 25),
                row=1
            )
            cosm_select.callback = self._on_cosmetics_select
            self.add_item(cosm_select)
        else:
            empty_select = discord.ui.Select(
                placeholder="No cosmetics available in this category.",
                options=[discord.SelectOption(label="No items available", value="none", default=True)],
                disabled=True,
                row=1
            )
            self.add_item(empty_select)

        # Row 2: Action Buttons
        is_grant = (self.mode == "grant")

        btn_prev = discord.ui.Button(label="◀ Prev", style=discord.ButtonStyle.secondary, disabled=(self.current_page == 0), row=2)
        btn_prev.callback = self._on_prev_click
        self.add_item(btn_prev)

        btn_next = discord.ui.Button(label="Next ▶", style=discord.ButtonStyle.secondary, disabled=(self.current_page >= total_pages - 1), row=2)
        btn_next.callback = self._on_next_click
        self.add_item(btn_next)

        if is_grant:
            btn_selected = discord.ui.Button(label=f"Grant Selected ({len(self.selected_ids)})", style=discord.ButtonStyle.success, emoji="✅", row=2)
            btn_selected.callback = self._on_action_selected
            self.add_item(btn_selected)

            btn_all = discord.ui.Button(label="Grant ALL Current", style=discord.ButtonStyle.primary, emoji="🌟", row=2)
            btn_all.callback = self._on_action_all
            self.add_item(btn_all)
        else:
            btn_selected = discord.ui.Button(label=f"Revoke Selected ({len(self.selected_ids)})", style=discord.ButtonStyle.danger, emoji="🗑️", row=2)
            btn_selected.callback = self._on_action_selected
            self.add_item(btn_selected)

            btn_all = discord.ui.Button(label="Revoke ALL Current", style=discord.ButtonStyle.danger, emoji="⚠️", row=2)
            btn_all.callback = self._on_action_all
            self.add_item(btn_all)

        btn_cancel = discord.ui.Button(label="Cancel", style=discord.ButtonStyle.secondary, emoji="❌", row=2)
        btn_cancel.callback = self._on_cancel_click
        self.add_item(btn_cancel)

    async def _on_category_select(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return
        vals = interaction.data.get("values", ["All"])
        self.current_category = vals[0]
        self.current_page = 0
        self._build_components()
        await interaction.response.edit_message(embed=self.build_embed(), view=self)

    async def _on_cosmetics_select(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return
        selected_now = set(interaction.data.get("values", []))
        filtered = filter_cosmetics_by_category(self.items, self.current_category)
        page_items, _, _ = paginate_items(filtered, self.current_page, 25)
        page_item_ids = {item["id"] for item in page_items}

        self.selected_ids = (self.selected_ids - page_item_ids) | selected_now
        self._build_components()
        await interaction.response.edit_message(embed=self.build_embed(), view=self)

    async def _on_prev_click(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return
        if self.current_page > 0:
            self.current_page -= 1
        self._build_components()
        await interaction.response.edit_message(embed=self.build_embed(), view=self)

    async def _on_next_click(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return
        self.current_page += 1
        self._build_components()
        await interaction.response.edit_message(embed=self.build_embed(), view=self)

    async def _on_action_selected(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return
        if not self.selected_ids:
            await interaction.response.send_message("⚠️ Please select at least one cosmetic from the menu first.", ephemeral=True)
            return

        await interaction.response.defer(ephemeral=True)
        is_grant = (self.mode == "grant")
        action_name = "Granted" if is_grant else "Revoked"
        source_label = f"staff:{self.admin_user.name}"

        processed_items = []
        for cid in sorted(self.selected_ids):
            if is_grant:
                await self.store.grant(self.target_uuid, cid, source=source_label)
            else:
                await self.store.revoke(self.target_uuid, cid, actor=source_label)
            friendly = cosmetics.format_item_name(cid)
            processed_items.append(f"• **{friendly}** (`{cid}`)")

        summary_embed = discord.Embed(
            title=f"✅ Cosmetics {action_name} Successfully",
            description=f"{action_name} **{len(processed_items)}** cosmetic(s) for **`{self.target_ign}`**.",
            color=0x2ECC71 if is_grant else 0xE74C3C,
            timestamp=datetime.now()
        )
        summary_embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{self.target_uuid}/100")
        summary_embed.add_field(name="Target Player", value=f"**`{self.target_ign}`** (`{self.target_uuid}`)", inline=True)
        summary_embed.add_field(name="Staff Member", value=self.admin_user.mention, inline=True)
        summary_embed.add_field(name="Action", value=f"{'Grant' if is_grant else 'Revoke'} Selected", inline=True)

        items_text = "\n".join(processed_items)
        if len(items_text) > 1000:
            items_text = items_text[:990] + "\n• *(and more...)*"
        summary_embed.add_field(name=f"{action_name} Items", value=items_text, inline=False)

        await interaction.edit_original_response(embed=summary_embed, view=None)
        await log_to_modlogs(interaction.guild, summary_embed)

    async def _on_action_all(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return

        await interaction.response.defer(ephemeral=True)
        is_grant = (self.mode == "grant")
        action_name = "Granted" if is_grant else "Revoked"
        source_label = f"staff:{self.admin_user.name}"

        if is_grant:
            target_ids = [item["id"] for item in self.items]
        else:
            target_ids = [item["id"] for item in self.items]

        if not target_ids:
            await interaction.followup.send(f"ℹ️ No cosmetics to {self.mode}.", ephemeral=True)
            return

        for cid in target_ids:
            if is_grant:
                await self.store.grant(self.target_uuid, cid, source=source_label)
            else:
                await self.store.revoke(self.target_uuid, cid, actor=source_label)

        summary_embed = discord.Embed(
            title=f"🌟 All Current Cosmetics {action_name}",
            description=f"{action_name} all current **{len(target_ids)}** cosmetic(s) for **`{self.target_ign}`**.",
            color=0x2ECC71 if is_grant else 0xE74C3C,
            timestamp=datetime.now()
        )
        summary_embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{self.target_uuid}/100")
        summary_embed.add_field(name="Target Player", value=f"**`{self.target_ign}`** (`{self.target_uuid}`)", inline=True)
        summary_embed.add_field(name="Staff Member", value=self.admin_user.mention, inline=True)
        summary_embed.add_field(name="Total Items", value=str(len(target_ids)), inline=True)

        await interaction.edit_original_response(embed=summary_embed, view=None)
        await log_to_modlogs(interaction.guild, summary_embed)

    async def _on_cancel_click(self, interaction: discord.Interaction):
        if interaction.user.id != self.admin_user.id:
            await interaction.response.send_message("❌ This menu is only for the staff member who ran the command.", ephemeral=True)
            return
        await interaction.response.edit_message(content="❌ Operation cancelled.", embed=None, view=None)


# Cosmetics Slash Command Group
cosmetics_group = app_commands.Group(
    name="cosmetics",
    description="[Staff] Manage cosmetics entitlements, grants, and all-access",
    default_permissions=discord.Permissions(manage_guild=True),
    guild_only=True
)


@cosmetics_group.command(name="grant", description="[Staff] Grant cosmetics to a player via interactive menu.")
@app_commands.describe(target="Minecraft username or @Discord user")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_cosmetics_grant(interaction: discord.Interaction, target: str):
    await interaction.response.defer(ephemeral=True)
    state = load_state()
    linked = state.get("linked_accounts", {})
    uuid, ign, disc_id, err = await resolve_target(target, linked)
    if err:
        await interaction.followup.send(err, ephemeral=True)
        return

    all_cosmetics_list = cosmetics.get_all_cosmetics()
    grantable_items = [item for item in all_cosmetics_list if item["id"] not in cosmetics.FREE]
    if not grantable_items:
        await interaction.followup.send(
            "Every cosmetic is free right now; use `/cosmetics allaccess` to pre-unlock future paid ones.",
            ephemeral=True
        )
        return

    store = cosmetics.get_store()
    view = CosmeticsSelectView(
        mode="grant",
        target_uuid=uuid,
        target_ign=ign,
        target_discord_id=disc_id,
        admin_user=interaction.user,
        items=grantable_items,
        store=store
    )
    embed = view.build_embed()
    if await store.has_all_access(uuid):
        embed.description = "⚠️ **Note: Player currently has All-Access enabled (all current & future cosmetics already unlocked).**\n\n" + (embed.description or "")
    await interaction.followup.send(embed=embed, view=view, ephemeral=True)


@cosmetics_group.command(name="revoke", description="[Staff] Revoke cosmetics from a player via interactive menu.")
@app_commands.describe(target="Minecraft username or @Discord user")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_cosmetics_revoke(interaction: discord.Interaction, target: str):
    await interaction.response.defer(ephemeral=True)
    state = load_state()
    linked = state.get("linked_accounts", {})
    uuid, ign, disc_id, err = await resolve_target(target, linked)
    if err:
        await interaction.followup.send(err, ephemeral=True)
        return

    store = cosmetics.get_store()
    list_res = await store.list_cosmetics(uuid)
    active_entitlements = [e["item_id"] for e in list_res.get("entitlements", []) if e.get("active")]

    has_aa = await store.has_all_access(uuid)
    if not active_entitlements:
        msg = f"ℹ️ Player **`{ign}`** does not have any active custom cosmetic entitlements to revoke."
        if has_aa:
            msg += "\n\n🌟 *Note: Player currently has All-Access enabled. Use `/cosmetics allaccess` to turn off all-access.*"
        await interaction.followup.send(msg, ephemeral=True)
        return

    all_known = cosmetics.get_all_cosmetics()
    known_map = {item["id"]: item for item in all_known}
    owned_items = []
    for cid in active_entitlements:
        if cid in known_map:
            owned_items.append(dict(known_map[cid]))
        else:
            owned_items.append({
                "id": cid,
                "name": cosmetics.format_item_name(cid),
                "category": cosmetics.categorize_item(cid)
            })

    view = CosmeticsSelectView(
        mode="revoke",
        target_uuid=uuid,
        target_ign=ign,
        target_discord_id=disc_id,
        admin_user=interaction.user,
        items=owned_items,
        store=store
    )
    embed = view.build_embed()
    if has_aa:
        embed.description = "⚠️ **Note: Player currently has All-Access enabled. Use `/cosmetics allaccess` to turn off all-access.**\n\n" + (embed.description or "")
    await interaction.followup.send(embed=embed, view=view, ephemeral=True)


@cosmetics_group.command(name="list", description="[Staff] View a player's cosmetics profile, owned items, and all-access status.")
@app_commands.describe(target="Minecraft username or @Discord user")
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_cosmetics_list(interaction: discord.Interaction, target: str):
    await interaction.response.defer(ephemeral=True)
    state = load_state()
    linked = state.get("linked_accounts", {})
    uuid, ign, disc_id, err = await resolve_target(target, linked)
    if err:
        await interaction.followup.send(err, ephemeral=True)
        return

    store = cosmetics.get_store()
    res = await store.list_cosmetics(uuid)
    has_aa = await store.has_all_access(uuid)
    aa_details = await store.get_all_access_details(uuid)

    embed = discord.Embed(
        title=f"✨ Cosmetics Profile: {ign}",
        color=0x9B59B6,
        timestamp=datetime.now()
    )
    embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
    embed.add_field(name="👤 Minecraft IGN", value=f"**`{ign}`** (`{uuid}`)", inline=True)
    embed.add_field(name="💬 Discord Account", value=f"<@{disc_id}>" if disc_id else "*Not linked*", inline=True)

    if has_aa:
        granted_info = f"\n*Granted by: `{aa_details['granted_by']}` at <t:{aa_details['granted_at']}:R>*" if aa_details else ""
        embed.add_field(name="🌟 All-Access Pass", value=f"**ENABLED** (Auto-unlocks ALL current & future cosmetics){granted_info}", inline=False)
    else:
        embed.add_field(name="🌟 All-Access Pass", value="*DISABLED*", inline=False)

    active_ents = [e for e in res.get("entitlements", []) if e.get("active")]
    if active_ents:
        lines = []
        for e in active_ents:
            name = cosmetics.format_item_name(e["item_id"])
            lines.append(f"• **{name}** (`{e['item_id']}`) - source: `{e['source']}`")
        text = "\n".join(lines)
        if len(text) > 1000:
            text = text[:990] + "\n• *(and more...)*"
        embed.add_field(name=f"🎒 Active Entitlements ({len(active_ents)})", value=text, inline=False)
    else:
        embed.add_field(name="🎒 Active Entitlements", value="*(None)*", inline=False)

    equipped = res.get("equipped", {})
    if equipped:
        eq_lines = [f"• **{slot}**: `{item_id}`" for slot, item_id in equipped.items()]
        embed.add_field(name="👗 Currently Equipped", value="\n".join(eq_lines), inline=False)
    else:
        embed.add_field(name="👗 Currently Equipped", value="*(None equipped)*", inline=False)

    free_names = [f"{cosmetics.format_item_name(e)} (free)" for e in cosmetics.FREE]
    embed.add_field(name="💃 Free Emotes (Everyone)", value=", ".join(free_names), inline=False)

    revoked_ents = [e for e in res.get("entitlements", []) if not e.get("active")]
    if revoked_ents:
        r_lines = [f"• `{e['item_id']}` (revoked <t:{e['revoked_at']}:R>)" for e in revoked_ents[:5]]
        embed.add_field(name=f"🚫 Revoked Entitlements ({len(revoked_ents)})", value="\n".join(r_lines), inline=False)

    await interaction.followup.send(embed=embed, ephemeral=True)


@cosmetics_group.command(name="allaccess", description="[Staff] Toggle 'everything unlocked' all-access pass for a player.")
@app_commands.describe(target="Minecraft username or @Discord user", state="on (unlock everything + future) or off")
@app_commands.choices(state=[
    app_commands.Choice(name="on - Unlock all current and future cosmetics", value="on"),
    app_commands.Choice(name="off - Revoke all-access pass", value="off")
])
@app_commands.checks.has_permissions(manage_guild=True)
async def slash_cosmetics_allaccess(interaction: discord.Interaction, target: str, state: app_commands.Choice[str]):
    await interaction.response.defer(ephemeral=True)
    state_data = load_state()
    linked = state_data.get("linked_accounts", {})
    uuid, ign, disc_id, err = await resolve_target(target, linked)
    if err:
        await interaction.followup.send(err, ephemeral=True)
        return

    enabled = (state.value.lower() == "on")
    store = cosmetics.get_store()
    source_label = f"staff:{interaction.user.name}"
    await store.set_all_access(uuid, enabled, granted_by=source_label)

    if enabled:
        title = "🌟 All-Access Pass Enabled"
        desc = (
            f"All-Access has been **ENABLED** for **`{ign}`** (`{uuid}`).\n\n"
            f"This player now has **every current and future cosmetic auto-unlocked**!"
        )
        color = 0x2ECC71
    else:
        title = "🔒 All-Access Pass Disabled"
        desc = (
            f"All-Access has been **DISABLED** for **`{ign}`** (`{uuid}`).\n\n"
            f"This player now only has access to their specific entitlements and free cosmetics."
        )
        color = 0xE67E22

    embed = discord.Embed(
        title=title,
        description=desc,
        color=color,
        timestamp=datetime.now()
    )
    embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{uuid}/100")
    embed.add_field(name="Target Player", value=f"**`{ign}`** (`{uuid}`)", inline=True)
    embed.add_field(name="Updated By", value=interaction.user.mention, inline=True)
    if disc_id:
        embed.add_field(name="Discord", value=f"<@{disc_id}>", inline=True)

    await interaction.followup.send(embed=embed, ephemeral=True)
    await log_to_modlogs(interaction.guild, embed)

bot.tree.add_command(cosmetics_group)


@bot.tree.command(name="crashlog", description="Analyze a pasted crash log or stack trace.")
@app_commands.describe(text="Crash log text snippet")
async def slash_crashlog(interaction: discord.Interaction, text: str):
    analysis = analyze_crash_text(text)
    if analysis:
        diag_embed = discord.Embed(
            title=f"🛠️ Crash Log Diagnostic: {analysis['title']}",
            description=analysis["solution"],
            color=analysis["color"],
            timestamp=datetime.now()
        )
        diag_embed.set_footer(text="The Lads Automated Diagnostic Assistant")
        await interaction.response.send_message(embed=diag_embed)
    else:
        await interaction.response.send_message("ℹ️ No known signature found. Please post the complete file in <#1556193352976826399>.", ephemeral=True)


# ------------------ SUGGESTION COMMANDS ------------------

@bot.tree.command(name="suggest", description="Open the form to submit a mod or feature suggestion.")
async def slash_suggest(interaction: discord.Interaction):
    await interaction.response.send_modal(SuggestionModal())


@bot.tree.command(name="post_suggestion_panel", description="Post or re-post the Suggestion Center panel with the submission button.")
@app_commands.checks.has_permissions(administrator=True)
async def slash_post_suggestion_panel(interaction: discord.Interaction):
    embed = build_suggestion_panel_embed()
    view = SuggestionPanelView()
    await interaction.channel.send(embed=embed, view=view)
    await interaction.response.send_message("✅ Suggestion panel posted!", ephemeral=True)


@bot.tree.command(name="suggestion_action", description="Update a suggestion's status (Approved / In Progress / Denied).")
@app_commands.describe(message_id="ID of the suggestion message", status="New status", reason="Optional feedback note")
@app_commands.choices(status=[
    app_commands.Choice(name="Approved", value="approved"),
    app_commands.Choice(name="In Progress", value="progress"),
    app_commands.Choice(name="Denied", value="denied")
])
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_suggestion_action(interaction: discord.Interaction, message_id: str, status: app_commands.Choice[str], reason: str = None):
    sugg_channel = discord.utils.get(interaction.guild.text_channels, name="suggestions")
    if not sugg_channel:
        await interaction.response.send_message("❌ Suggestions channel not found.", ephemeral=True)
        return
    await interaction.response.defer(ephemeral=True)  # fetch + edit + thread post + log can outlast 3 s

    try:
        msg = await sugg_channel.fetch_message(int(message_id))
    except (ValueError, discord.NotFound):
        await interaction.followup.send(f"❌ No message `{message_id}` in {sugg_channel.mention}.", ephemeral=True)
        return
    if not msg.embeds:
        await interaction.followup.send("❌ Target message has no embed.", ephemeral=True)
        return

    embed = msg.embeds[0]

    colors = {
        "approved": 0x2ECC71,
        "progress": 0x3498DB,
        "denied": 0xE74C3C
    }
    labels = {
        "approved": "🟢 **Approved**",
        "progress": "🟡 **In Progress / Planned**",
        "denied": "🔴 **Denied**"
    }

    embed.color = colors.get(status.value, 0x95A5A6)
    val = labels.get(status.value) + (f"\n*Staff Note: {reason}*" if reason else "")
    status_idx = next((i for i, f in enumerate(embed.fields) if f.name == "Status"), None)
    if status_idx is None:
        embed.add_field(name="Status", value=val, inline=False)
    else:
        embed.set_field_at(status_idx, name="Status", value=val, inline=False)

    await msg.edit(embed=embed)

    if msg.thread:
        note_str = f"\n*Reason:* {reason}" if reason else ""
        await msg.thread.send(f"📢 **Suggestion Status Update:** {labels.get(status.value)} by {interaction.user.mention}{note_str}")

    await interaction.followup.send(f"✅ Updated suggestion status to {status.name}!", ephemeral=True)

    log_embed = discord.Embed(
        title=f"💡 Suggestion {status.name}",
        description=f"**Staff:** {interaction.user.mention}\n**Suggestion:** [Jump to Message]({msg.jump_url})\n**Reason:** {reason or 'None'}",
        color=colors.get(status.value, 0x95A5A6),
        timestamp=datetime.now()
    )
    await log_to_modlogs(interaction.guild, log_embed)


# ------------------ ROLE MANAGEMENT COMMANDS ------------------

@bot.tree.command(name="role_give", description="Give any role to a server member.")
@app_commands.describe(user="Member to give the role to", role="Role to assign")
@app_commands.checks.has_permissions(manage_roles=True)
async def slash_role_give(interaction: discord.Interaction, user: discord.Member, role: discord.Role):
    allowed_r, msg_r = can_act(interaction, role)
    if not allowed_r:
        await interaction.response.send_message(f"\u274c {msg_r}", ephemeral=True)
        return
    allowed_u, msg_u = can_act(interaction, user)
    if not allowed_u:
        await interaction.response.send_message(f"\u274c {msg_u}", ephemeral=True)
        return
    try:
        await user.add_roles(role, reason=f"Given by {interaction.user.name}")
        await interaction.response.send_message(f"✅ Added {role.mention} to {user.mention}!")
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to assign role: {e}", ephemeral=True)


@bot.tree.command(name="role_remove", description="Remove a role from a server member.")
@app_commands.describe(user="Member to remove the role from", role="Role to remove")
@app_commands.checks.has_permissions(manage_roles=True)
async def slash_role_remove(interaction: discord.Interaction, user: discord.Member, role: discord.Role):
    allowed_r, msg_r = can_act(interaction, role)
    if not allowed_r:
        await interaction.response.send_message(f"\u274c {msg_r}", ephemeral=True)
        return
    allowed_u, msg_u = can_act(interaction, user)
    if not allowed_u:
        await interaction.response.send_message(f"\u274c {msg_u}", ephemeral=True)
        return
    try:
        await user.remove_roles(role, reason=f"Removed by {interaction.user.name}")
        await interaction.response.send_message(f"✅ Removed {role.mention} from {user.mention}!")
    except Exception as e:
        await interaction.response.send_message(f"❌ Failed to remove role: {e}", ephemeral=True)


# ------------------ COMMANDS: CHANGELOG & ROLES PANEL ------------------

@bot.tree.command(name="rolespanel", description="Post the interactive notification self-roles dropdown panel.")
@app_commands.checks.has_permissions(manage_roles=True)
async def slash_roles_panel(interaction: discord.Interaction, channel: discord.TextChannel = None):
    target_channel = channel or discord.utils.get(interaction.guild.text_channels, name="roles") or interaction.channel
    embed = build_roles_panel_embed()
    view = NotificationRolesView()
    await target_channel.send(embed=embed, view=view)
    await interaction.response.send_message(f"✅ Self-roles panel posted in {target_channel.mention}!", ephemeral=True)


async def publish_release_notes(guild: discord.Guild, version: str, force: bool = False) -> str:
    """Shared by /postchangelog and !postchangelog. Returns the status line to show the caller."""
    version = version.strip().lstrip("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        return f"❌ Invalid version format `{version}`. Expected `X.Y.Z` (e.g. `1.12.0`)."
    file_path = (RELEASES_DIR / f"{version}.md").resolve()
    if file_path.parent != RELEASES_DIR.resolve() or not file_path.exists():
        return f"❌ Could not find changelog file for version `{version}` in `{RELEASES_DIR}`."
    if version in load_state().get("posted_versions", []) and not force:
        return f"ℹ️ v{version} was already posted to <#1556193307766427750>. Use `force: True` to post it again."

    if not await post_changelog_update(guild, version, file_path.read_text(encoding="utf-8"), force=force):
        return f"❌ Failed to post changelog for v{version} (#changelog not found)."
    state = load_state()
    state["posted_versions"] = sorted(set(state.get("posted_versions", [])) | {version})
    save_state(state)
    asyncio.create_task(update_server_stats(guild))  # refresh 🚀 Client right after a release post
    return f"✅ Changelog for **v{version}** successfully posted to <#1556193307766427750>!"


@bot.tree.command(name="postchangelog", description="Manually post a changelog to #changelog.")
@app_commands.describe(version="Version to post (e.g. 1.6.0)", force="Force repost even if already posted")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_post_changelog(interaction: discord.Interaction, version: str, force: bool = False):
    await interaction.response.defer(ephemeral=True)  # multi-embed posts take longer than the 3 s reply window
    await interaction.followup.send(await publish_release_notes(interaction.guild, version, force), ephemeral=True)


@bot.command(name="postchangelog")
@commands.has_guild_permissions(manage_messages=True)
async def prefix_post_changelog(ctx: commands.Context, version: str, force: str = ""):
    """!postchangelog <version> [force]"""
    await ctx.reply(await publish_release_notes(ctx.guild, version, force.lower() in ("force", "true", "yes", "1")))


@prefix_post_changelog.error
async def prefix_post_changelog_error(ctx: commands.Context, error: commands.CommandError):
    if isinstance(error, commands.MissingRequiredArgument):
        await ctx.reply("Usage: `!postchangelog <version> [force]`")
    elif isinstance(error, commands.CheckFailure):
        await ctx.reply("🚫 You need Manage Messages to post changelogs.")
    else:
        print(f"[Command Error] !postchangelog: {error!r}", flush=True)
        traceback.print_exception(type(error), error, error.__traceback__)
        await ctx.reply("⚠️ Something went wrong posting that changelog; the error was logged.")


@bot.tree.error
async def on_app_command_error(interaction: discord.Interaction, error: app_commands.AppCommandError):
    await reply_with_error(interaction, getattr(error, "original", error))


# ------------------ FUN & COMMUNITY COMMANDS ------------------

@bot.tree.command(name="coinflip", description="Flip a coin!")
async def slash_coinflip(interaction: discord.Interaction):
    result = random.choice(["Heads 🪙", "Tails 🪙"])
    embed = discord.Embed(
        title="🪙 Coin Flip Result",
        description=f"The coin landed on **{result}**!",
        color=0xF1C40F
    )
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="roll", description="Roll a dice (e.g. 6, 20, 100 sides).")
@app_commands.describe(sides="Number of sides on the dice (default: 6)")
async def slash_roll(interaction: discord.Interaction, sides: int = 6):
    sides = max(2, min(sides, 1000000))
    result = random.randint(1, sides)
    embed = discord.Embed(
        title="🎲 Dice Roll",
        description=f"{interaction.user.mention} rolled a **d{sides}** and got **{result}**!",
        color=0x3498DB
    )
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="8ball", description="Ask the magic 8-ball a question.")
@app_commands.describe(question="Your question")
async def slash_8ball(interaction: discord.Interaction, question: str):
    answers = [
        "It is certain, like finding diamonds at Y=-58.",
        "Without a doubt, 100% full Netherite.",
        "Yes – definitely, smooth 240 FPS ahead.",
        "You may rely on it, as strong as Bedrock.",
        "As I see it, yes.",
        "Most likely, Creeper didn't hiss yet.",
        "Outlook good, your ping is 12ms.",
        "Signs point to yes.",
        "Reply hazy, try again after respawn.",
        "Ask again later, server is restarting.",
        "Better not tell you now, you might jump into lava.",
        "Cannot predict now, chunk loading error.",
        "Concentrate and ask again.",
        "Don't count on it, bed exploded.",
        "My reply is no, you got combo'd.",
        "My sources say no, bad omen.",
        "Outlook not so good, fallen into the void.",
        "Very doubtful, like breaking Obsidian with bare hands."
    ]
    response = random.choice(answers)
    embed = discord.Embed(
        title="🎱 Magic 8-Ball",
        color=0x9B59B6,
        timestamp=datetime.now()
    )
    embed.add_field(name="❓ Question", value=question, inline=False)
    embed.add_field(name="🔮 Answer", value=response, inline=False)
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="pvp", description="Challenge another player to a funny Minecraft duel!")
@app_commands.describe(opponent="The player you want to fight")
async def slash_pvp(interaction: discord.Interaction, opponent: discord.Member):
    if opponent.id == interaction.user.id:
        await interaction.response.send_message("❌ You can't fight yourself! (Though falling into the void counts...)", ephemeral=True)
        return

    author = interaction.user
    state = load_state()
    linked = state.get("linked_accounts", {})
    author_ign = linked.get(str(author.id), {}).get("ign")
    opp_ign = linked.get(str(opponent.id), {}).get("ign")
    author_label = f"{author.name} (`{author_ign}`)" if author_ign else author.name
    opp_label = f"{opponent.name} (`{opp_ign}`)" if opp_ign else opponent.name

    scenarios = [
        f"⚔️ {author.mention} started with a flawless 6-hit W-tap combo on {opponent.mention}!",
        f"🎣 {opponent.mention} landed a sick fishing rod trick and knocked {author.mention} backward!",
        f"🍎 {author.mention} ate an Enchanted Golden Apple and tanked 3 critical hits!",
        f"💥 {opponent.mention} placed an End Crystal and wiped half the battlefield!",
        f"🏹 {author.mention} hit a 360 noscope bow shot from 40 blocks away!",
        f"🪄 {opponent.mention} threw a Splash Potion of Harming II right into {author.mention}'s face!",
        f"🪜 {author.mention} clutched with a water bucket off the cliff!",
        f"🔨 {opponent.mention} jumped off a tower and delivered a devastating Mace smash!"
    ]
    duel_events = random.sample(scenarios, k=min(4, len(scenarios)))

    winner = random.choice([author, opponent])
    loser = opponent if winner == author else author
    remaining_hp = random.randint(1, 10)

    winner_ign = linked.get(str(winner.id), {}).get("ign")
    winner_suffix = f" (`{winner_ign}`)" if winner_ign else ""

    embed = discord.Embed(
        title="⚔️ Minecraft PvP Duel Arena",
        description=f"**{author_label}** ⚔️ **{opp_label}**\n\n" + "\n".join(duel_events) + f"\n\n🏆 **Winner: {winner.mention}{winner_suffix}!** ({remaining_hp} ❤ HP remaining)\n💀 *{loser.mention} was sent back to spawn.*",
        color=0xE74C3C,
        timestamp=datetime.now()
    )
    if winner_ign:
        winner_uuid = linked.get(str(winner.id), {}).get("uuid")
        if winner_uuid:
            embed.set_thumbnail(url=f"https://mc-heads.net/avatar/{winner_uuid}/100")

    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="rps", description="Play Rock, Paper, Scissors against the bot!")
@app_commands.describe(choice="Choose Rock, Paper, or Scissors")
@app_commands.choices(choice=[
    app_commands.Choice(name="Rock 🪨", value="rock"),
    app_commands.Choice(name="Paper 📄", value="paper"),
    app_commands.Choice(name="Scissors ✂️", value="scissors")
])
async def slash_rps(interaction: discord.Interaction, choice: app_commands.Choice[str]):
    bot_choice = random.choice(["rock", "paper", "scissors"])
    emojis = {"rock": "🪨 Rock", "paper": "📄 Paper", "scissors": "✂️ Scissors"}

    user_val = choice.value
    if user_val == bot_choice:
        result = "🤝 It's a Tie!"
        color = 0xF1C40F
    elif (user_val == "rock" and bot_choice == "scissors") or \
         (user_val == "paper" and bot_choice == "rock") or \
         (user_val == "scissors" and bot_choice == "paper"):
        result = f"🎉 {interaction.user.name} Wins!"
        color = 0x2ECC71
    else:
        result = "🤖 The Lads Bot Wins!"
        color = 0xE74C3C

    embed = discord.Embed(
        title="🎮 Rock, Paper, Scissors",
        description=f"**Your Choice:** {emojis[user_val]}\n**Bot's Choice:** {emojis[bot_choice]}\n\n### {result}",
        color=color
    )
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="meme", description="Fetch a fresh trending Minecraft meme!")
async def slash_meme(interaction: discord.Interaction):
    await interaction.response.defer()
    valid_exts = (".png", ".jpg", ".jpeg", ".gif", ".webp")
    data = None
    try:
        async with aiohttp.ClientSession() as session:
            # Try fetching up to 3 times to get a direct image link
            for _ in range(3):
                async with session.get("https://meme-api.com/gimme/MinecraftMemes", timeout=aiohttp.ClientTimeout(total=5)) as resp:
                    if resp.status == 200:
                        candidate = await resp.json()
                        img_url = candidate.get("url", "")
                        if any(img_url.lower().endswith(ext) for ext in valid_exts) and not candidate.get("nsfw"):
                            data = candidate
                            break
            # Fallback to wholesome memes if Minecraft gave video
            if not data:
                async with session.get("https://meme-api.com/gimme/wholesomememes", timeout=aiohttp.ClientTimeout(total=5)) as resp:
                    if resp.status == 200:
                        candidate = await resp.json()
                        img_url = candidate.get("url", "")
                        if any(img_url.lower().endswith(ext) for ext in valid_exts) and not candidate.get("nsfw"):
                            data = candidate

        if not data or not data.get("url"):
            await interaction.followup.send("❌ Could not fetch a meme image right now. Please try again!")
            return

        embed = discord.Embed(
            title=data.get("title", "Minecraft Meme"),
            url=data.get("postLink", "https://reddit.com/r/MinecraftMemes"),
            color=0x1ABC9C,
            timestamp=datetime.now()
        )
        embed.set_image(url=data.get("url"))
        embed.set_footer(text=f"👍 {data.get('ups', 0)} upvotes • r/{data.get('subreddit', 'MinecraftMemes')}")
        await interaction.followup.send(embed=embed)
    except Exception as e:
        await interaction.followup.send(f"❌ Error getting meme: {e}")


@bot.tree.command(name="rate", description="Rate anything on a scale of 0 to 10!")
@app_commands.describe(subject="What would you like rated?")
async def slash_rate(interaction: discord.Interaction, subject: str):
    rating = random.randint(0, 10)
    comments = {
        0: "Absolute trash, uncraftable.",
        1: "Worse than wooden hoe durability.",
        2: "Pretty bad, 15 FPS experience.",
        3: "Needs serious optimization.",
        4: "Below average, missing sodium/optifine.",
        5: "Decent, standard vanilla Minecraft.",
        6: "Not bad, pretty clean HUD.",
        7: "Solid! Worth crafting.",
        8: "Very impressive, 144 FPS smooth!",
        9: "Almost perfect! God tier!",
        10: "👑 10/10 MASTERPIECE! Full Netherite with Enchanted Golden Apple!"
    }
    embed = discord.Embed(
        title="⭐ Rating Machine",
        description=f"I rate **{subject}** a **{rating}/10**!\n\n*{comments[rating]}*",
        color=0xF1C40F
    )
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="ship", description="Calculate the love or friendship compatibility between two people!")
@app_commands.describe(user1="First person", user2="Second person")
async def slash_ship(interaction: discord.Interaction, user1: discord.Member, user2: discord.Member):
    score = (user1.id + user2.id) % 101

    filled = int(score / 10)
    empty = 10 - filled
    bar = "█" * filled + "░" * empty

    if score > 85:
        msg = "💖 A match made in heaven! Get married in Minecraft already!"
    elif score > 65:
        msg = "💕 Great chemistry! Ready to share a base and diamond chest."
    elif score > 45:
        msg = "🤝 Good duo partners! Can grind Bedwars together."
    elif score > 20:
        msg = "😬 A little awkward... Someone is stealing iron ingots."
    else:
        msg = "💀 Total disaster! One of you will push the other into lava."

    embed = discord.Embed(
        title="💘 Compatibility Meter",
        description=f"**{user1.mention}** 💕 **{user2.mention}**\n\n**Match Score:** `{score}%`\n`[{bar}]`\n\n{msg}",
        color=0xE91E63
    )
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="cat", description="Get a random cute cat picture!")
async def slash_cat(interaction: discord.Interaction):
    await interaction.response.defer()
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get("https://api.thecatapi.com/v1/images/search?mime_types=jpg,png,gif", timeout=aiohttp.ClientTimeout(total=6)) as resp:
                data = await resp.json()
                img_url = data[0]["url"]
        embed = discord.Embed(title="🐱 Meow!", color=0xE67E22, timestamp=datetime.now())
        embed.set_image(url=img_url)
        embed.set_footer(text="The Lads Cat Gallery")
        await interaction.followup.send(embed=embed)
    except Exception as e:
        await interaction.followup.send(f"❌ Failed to get cat: {e}")


@bot.tree.command(name="dog", description="Get a random cute dog picture!")
async def slash_dog(interaction: discord.Interaction):
    await interaction.response.defer()
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get("https://dog.ceo/api/breeds/image/random", timeout=aiohttp.ClientTimeout(total=6)) as resp:
                data = await resp.json()
                img_url = data["message"]
        embed = discord.Embed(title="🐶 Woof!", color=0x3498DB, timestamp=datetime.now())
        embed.set_image(url=img_url)
        embed.set_footer(text="The Lads Dog Gallery")
        await interaction.followup.send(embed=embed)
    except Exception as e:
        await interaction.followup.send(f"❌ Failed to get dog: {e}")


@bot.tree.command(name="colors", description="Customize your chat username color with an interactive palette.")
async def slash_colors(interaction: discord.Interaction):
    embed = build_colors_panel_embed()
    view = ColorRolesView()
    await interaction.response.send_message(embed=embed, view=view, ephemeral=True)


@bot.tree.command(name="faq", description="View the interactive community & client knowledge base.")
async def slash_faq(interaction: discord.Interaction):
    embed = build_faq_panel_embed()
    view = FAQPanelView()
    await interaction.response.send_message(embed=embed, view=view, ephemeral=True)


@bot.tree.command(name="faq_post", description="Post or refresh the persistent Knowledge Base panel in #faq (Admin only).")
@app_commands.default_permissions(administrator=True)
async def slash_faq_post(interaction: discord.Interaction):
    faq_channel = discord.utils.get(interaction.guild.text_channels, name="faq")
    if not faq_channel:
        await interaction.response.send_message("❌ Channel #faq not found.", ephemeral=True)
        return
    embed = build_faq_panel_embed()
    view = FAQPanelView()
    await faq_channel.send(embed=embed, view=view)
    await interaction.response.send_message(f"✅ Successfully posted the Knowledge Base FAQ Panel to {faq_channel.mention}!", ephemeral=True)


@bot.tree.command(name="lfg", description="Recruit teammates for Bedwars, Duels, Skywars, or SMP!")
@app_commands.describe(
    game="Game or mode you want to play",
    needed="Number of teammates needed (1 to 4)",
    notes="Optional details (e.g. mic required, grinding wins)"
)
@app_commands.choices(game=[
    app_commands.Choice(name="Bedwars (4v4v4v4)", value="Bedwars 4v4"),
    app_commands.Choice(name="Bedwars (Doubles)", value="Bedwars Doubles"),
    app_commands.Choice(name="Duels (1v1 / 2v2)", value="Duels"),
    app_commands.Choice(name="Skywars", value="Skywars"),
    app_commands.Choice(name="Survival SMP", value="Survival SMP"),
    app_commands.Choice(name="Other / Custom Game", value="Custom Game"),
])
@app_commands.checks.cooldown(1, 300, key=lambda i: (i.guild_id, i.user.id))
async def slash_lfg(interaction: discord.Interaction, game: app_commands.Choice[str], needed: app_commands.Range[int, 1, 4], notes: str = None):
    lfg_ch = discord.utils.get(interaction.guild.text_channels, name="looking-for-group")
    target_ch = lfg_ch if lfg_ch else interaction.channel

    lfg_role = discord.utils.get(interaction.guild.roles, name="🎮 Looking for Group")
    ping_str = lfg_role.mention if lfg_role else ""

    state = load_state()
    linked_data = state.get("linked_accounts", {}).get(str(interaction.user.id))
    ign_text = f" (`{linked_data['ign']}`)" if linked_data else ""

    total_slots = 1 + needed
    slots_vis = "🟩 " + "⬜ " * needed

    embed = discord.Embed(
        title=f"🎮 Looking For Group — {game.value}",
        description=f"{interaction.user.mention}{ign_text} is looking for **{needed}** more teammate{'s' if needed > 1 else ''}!\nClick **[Join Squad]** below to hop in.",
        color=0x3498DB,
        timestamp=datetime.now()
    )
    embed.add_field(name="🎮 Game", value=f"**{game.value}**", inline=True)
    embed.add_field(name="👥 Squad Status", value=f"**1 / {total_slots}**\n{slots_vis}", inline=True)
    if notes:
        embed.add_field(name="📝 Notes", value=notes, inline=False)
    embed.add_field(name="🛡️ Current Squad", value=f"• {interaction.user.mention} *(Host)*", inline=False)
    embed.set_thumbnail(url=interaction.user.display_avatar.url)
    embed.set_footer(text="The Lads LFG • Click Join Squad to team up")

    view = LFGSquadView()

    if interaction.channel == target_ch:
        await interaction.response.send_message(content=ping_str, embed=embed, view=view)
    else:
        msg = await target_ch.send(content=ping_str, embed=embed, view=view)
        await interaction.response.send_message(f"✅ Your squad recruitment post has been created in {target_ch.mention}! [View Post]({msg.jump_url})", ephemeral=True)


if __name__ == "__main__":
    bot.run(BOT_TOKEN)

