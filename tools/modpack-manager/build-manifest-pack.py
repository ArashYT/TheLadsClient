#!/usr/bin/env python3
"""
Builds lightweight, manifest-driven Experience modpack archives (.zip with modrinth.index.json + overrides/ + pack.json)
for The Lads Launcher ("The Lads SMP Reborn" 26.3 and "Zero SMP" 1.20.1).

All third-party mods, resource packs, and shader packs are resolved to official Modrinth (cdn.modrinth.com)
and CurseForge (edge.forgecdn.net) CDN URLs with cryptographic SHA-1 and SHA-512 hashes.
"""

import argparse
import datetime
import hashlib
import io
import json
import os
import sys
import urllib.parse
import urllib.request
import zipfile

USER_AGENT = "TheLadsClient-modpack-manager/1.9.10"

# Verified CurseForge file IDs and official edge.forgecdn.net URLs for files not hosted on Modrinth
CURSEFORGE_KNOWN_URLS = {
    # The Lads SMP Reborn (26.3 Fabric)
    "BiomesOPlenty-fabric-26.3-26.3.0.0.15.jar": "https://edge.forgecdn.net/files/9168/504/BiomesOPlenty-fabric-26.3-26.3.0.0.15.jar",
    "GlitchCore-fabric-26.3-26.3.0.0.5.jar": "https://edge.forgecdn.net/files/9125/345/GlitchCore-fabric-26.3-26.3.0.0.5.jar",
    "JEI_Buttons-1.2.0.jar": "https://edge.forgecdn.net/files/8927/313/JEI_Buttons-1.2.0.jar",
    "JustEnoughProfessions-fabric-26.3-130.0.jar": "https://edge.forgecdn.net/files/8906/958/JustEnoughProfessions-fabric-26.3-130.0.jar",
    "Measurements-fabric-26.3-6.0.0.jar": "https://edge.forgecdn.net/files/8907/263/Measurements-fabric-26.3-6.0.0.jar",
    "StorageDrawers-fabric-26.3.0.1.jar": "https://edge.forgecdn.net/files/9007/424/StorageDrawers-fabric-26.3.0.1.jar",
    "TC-Redstone-26.3-Fabric-3.2.6.jar": "https://edge.forgecdn.net/files/9148/230/TC-Redstone-26.3-Fabric-3.2.6.jar",
    "TerraBlender-fabric-26.3-26.3.0.0.9.jar": "https://edge.forgecdn.net/files/9033/895/TerraBlender-fabric-26.3-26.3.0.0.9.jar",
    "cloth-config-26.3.158.jar": "https://edge.forgecdn.net/files/8884/63/cloth-config-26.3.158.jar",
    "connectivity-fabric-26.3-7.7.jar": "https://edge.forgecdn.net/files/9011/123/connectivity-fabric-26.3-7.7.jar",
    "cookingforblockheads-fabric-26.3-26.3.0.2.jar": "https://edge.forgecdn.net/files/9035/244/cookingforblockheads-fabric-26.3-26.3.0.2.jar",
    "ctm-reborn-fabric-4.9.2.jar": "https://edge.forgecdn.net/files/8925/877/ctm-reborn-fabric-4.9.2.jar",
    "cupboard-fabric-26.3-4.2.jar": "https://edge.forgecdn.net/files/8890/593/cupboard-fabric-26.3-4.2.jar",
    "dynamiclantern-2.0.0-mc26.3-fabric.jar": "https://edge.forgecdn.net/files/8923/150/dynamiclantern-2.0.0-mc26.3-fabric.jar",
    "femboymod-fabric-0.1.1+26.3-fabric.jar": "https://edge.forgecdn.net/files/9103/462/femboymod-fabric-0.1.1%2b26.3-fabric.jar",
    "lootintegrations-fabric-26.1-4.8.jar": "https://edge.forgecdn.net/files/7912/269/lootintegrations-fabric-26.1-4.8.jar",
    "lootintegrations_moog-2.2.jar": "https://edge.forgecdn.net/files/8920/358/lootintegrations_moog-2.2.jar",
    "mcw-bridges-3.1.2-mc26.3fabric.jar": "https://edge.forgecdn.net/files/8959/855/mcw-bridges-3.1.2-mc26.3fabric.jar",
    "mcw-roads-1.0.0-mc26.3fabric.jar": "https://edge.forgecdn.net/files/8962/173/mcw-roads-1.0.0-mc26.3fabric.jar",
    "shogi-fabric-26.3-26.3.0.3.jar": "https://edge.forgecdn.net/files/8978/492/shogi-fabric-26.3-26.3.0.3.jar",
    "smartbackpacks-1.0.16-fabric-26.3.jar": "https://edge.forgecdn.net/files/9046/259/smartbackpacks-1.0.16-fabric-26.3.jar",
    # The Lads SMP Reborn 0.3.0: Arise SMP mods (2026-10-17)
    "AsyncParticles-26.3.2.0+26.3.jar": "https://edge.forgecdn.net/files/9088/77/AsyncParticles-26.3.2.0%2B26.3.jar",
    "BetterAdvancements-Fabric-26.3-0.6.0.78.jar": "https://edge.forgecdn.net/files/9002/785/BetterAdvancements-Fabric-26.3-0.6.0.78.jar",
    "Incendium_26.3_v5.5.4+26.3.jar": "https://edge.forgecdn.net/files/9147/441/Incendium_26.3_v5.5.4%2B26.3.jar",
    "Ping-Wheel-1.12.2-fabric-26.3.jar": "https://edge.forgecdn.net/files/8931/641/Ping-Wheel-1.12.2-fabric-26.3.jar",
    "Terralith_26.3_v2.6.5+26.3.jar": "https://edge.forgecdn.net/files/8994/208/Terralith_26.3_v2.6.5%2B26.3.jar",
    "XaeroPlus-2.37.0+fabric-26.3-WM1.47.0-MM26.6.0.jar": "https://edge.forgecdn.net/files/9202/16/XaeroPlus-2.37.0%2Bfabric-26.3-WM1.47.0-MM26.6.0.jar",
    "additionallanterns-1.1.2-fabric-mc26.3.jar": "https://edge.forgecdn.net/files/8888/125/additionallanterns-1.1.2-fabric-mc26.3.jar",
    "antixray-fabric-1.4.17+26.3.jar": "https://edge.forgecdn.net/files/9207/632/antixray-fabric-1.4.17%2B26.3.jar",
    "cherishedworlds-fabric-18.0.0+26.3.jar": "https://edge.forgecdn.net/files/8969/3/cherishedworlds-fabric-18.0.0%2B26.3.jar",
    "chestsearchbar-fabric-26.3-1.7.0.jar": "https://edge.forgecdn.net/files/9210/610/chestsearchbar-fabric-26.3-1.7.0.jar",
    "chunksending-fabric-26.3-4.1.1.jar": "https://edge.forgecdn.net/files/9155/632/chunksending-fabric-26.3-4.1.1.jar",
    "clientcrafting-fabric-26.1-2.1.jar": "https://edge.forgecdn.net/files/8002/356/clientcrafting-fabric-26.1-2.1.jar",
    "clienttweaks-fabric-26.3-26.3.0.1.jar": "https://edge.forgecdn.net/files/8893/522/clienttweaks-fabric-26.3-26.3.0.1.jar",
    "commandkeys-fabric-3.104.1+26.3.jar": "https://edge.forgecdn.net/files/9106/946/commandkeys-fabric-3.104.1%2B26.3.jar",
    "configureddefaults-v26.3.0-mc26.3.x+fabric.jar": "https://edge.forgecdn.net/files/8949/926/configureddefaults-v26.3.0-mc26.3.x%2Bfabric.jar",
    "craftingtweaks-fabric-26.3-26.3.0.1.jar": "https://edge.forgecdn.net/files/8893/527/craftingtweaks-fabric-26.3-26.3.0.1.jar",
    "cursors_extended-fabric-4.2.0+26.3.jar": "https://edge.forgecdn.net/files/8882/798/cursors_extended-fabric-4.2.0%2B26.3.jar",
    "disguiseheads-fabric-1.4.2-mc26.3.jar": "https://edge.forgecdn.net/files/8966/259/disguiseheads-fabric-1.4.2-mc26.3.jar",
    "easymagic-v26.3.0-mc26.3.x+fabric.jar": "https://edge.forgecdn.net/files/8958/974/easymagic-v26.3.0-mc26.3.x%2Bfabric.jar",
    "eating-animation-fork-26.3+5.1.0.jar": "https://edge.forgecdn.net/files/8920/284/eating-animation-fork-26.3%2B5.1.0.jar",
    "farsight-fabric-26.3-5.6.jar": "https://edge.forgecdn.net/files/9018/878/farsight-fabric-26.3-5.6.jar",
    "hardcorerevival-fabric-26.3-26.3.0.1.jar": "https://edge.forgecdn.net/files/8893/509/hardcorerevival-fabric-26.3-26.3.0.1.jar",
    "immersivemc-1.6.0-beta1-26.3-fabric.jar": "https://edge.forgecdn.net/files/9024/164/immersivemc-1.6.0-beta1-26.3-fabric.jar",
    "leaky-fabric-26.2-3.5.jar": "https://edge.forgecdn.net/files/8912/388/leaky-fabric-26.2-3.5.jar",
    "limitedchunks-fabric-26.1-4.1.jar": "https://edge.forgecdn.net/files/8026/52/limitedchunks-fabric-26.1-4.1.jar",
    "lithostitched-2.0.6-fabric-26.3.jar": "https://edge.forgecdn.net/files/9154/966/lithostitched-2.0.6-fabric-26.3.jar",
    "particular-26.3-Fabric-1.5.7.jar": "https://edge.forgecdn.net/files/8943/370/particular-26.3-Fabric-1.5.7.jar",
    "pickupnotifier-v26.3.0-mc26.3.x+fabric.jar": "https://edge.forgecdn.net/files/8955/363/pickupnotifier-v26.3.0-mc26.3.x%2Bfabric.jar",
    "polytone-26.3-6.8.6-fabric.jar": "https://edge.forgecdn.net/files/9117/663/polytone-26.3-6.8.6-fabric.jar",
    "reflex-fabric-1.1.2+26.3.jar": "https://edge.forgecdn.net/files/9108/276/reflex-fabric-1.1.2%2B26.3.jar",
    "resourcepackoverrides-v26.3.0-mc26.3.x+fabric.jar": "https://edge.forgecdn.net/files/8947/647/resourcepackoverrides-v26.3.0-mc26.3.x%2Bfabric.jar",
    "rpc-1.2.9+26.1-26.3.jar": "https://edge.forgecdn.net/files/9183/713/rpc-1.2.9%2B26.1-26.3.jar",
    "servercore-fabric-1.5.20+26.3.jar": "https://edge.forgecdn.net/files/8893/825/servercore-fabric-1.5.20%2B26.3.jar",
    "sleepanywhere-1.1-26.3.jar": "https://edge.forgecdn.net/files/8925/367/sleepanywhere-1.1-26.3.jar",
    "smartcompletion-26.3-fabric-3.0.0.jar": "https://edge.forgecdn.net/files/9041/866/smartcompletion-26.3-fabric-3.0.0.jar",
    "spawnanimations-v1.11.6-mc1.17-26.3.9-mod.jar": "https://edge.forgecdn.net/files/9008/810/spawnanimations-v1.11.6-mc1.17-26.3.9-mod.jar",
    "spiffyhud_fabric_3.1.4_MC_26.3.jar": "https://edge.forgecdn.net/files/8924/505/spiffyhud_fabric_3.1.4_MC_26.3.jar",
    "spitting-image-5-26.3-fabric.jar": "https://edge.forgecdn.net/files/8912/769/spitting-image-5-26.3-fabric.jar",
    "strawstatues-v26.3.0-mc26.3.x+fabric.jar": "https://edge.forgecdn.net/files/8945/544/strawstatues-v26.3.0-mc26.3.x%2Bfabric.jar",
    "trade-cycling-fabric-1.0.23+26.3.jar": "https://edge.forgecdn.net/files/9155/650/trade-cycling-fabric-1.0.23%2B26.3.jar",
    # Zero SMP (1.20.1 Forge)
    "backpacked-forge-1.20.1-3.0.9.jar": "https://edge.forgecdn.net/files/7866/682/backpacked-forge-1.20.1-3.0.9.jar",
    "configured-forge-1.20.1-2.2.3.jar": "https://edge.forgecdn.net/files/5180/900/configured-forge-1.20.1-2.2.3.jar",
    "cosmeticarmorreworked-1.20.1-v1a.jar": "https://edge.forgecdn.net/files/4600/191/cosmeticarmorreworked-1.20.1-v1a.jar",
    "customjukeboxdiscs-1.20.1-0.4.0.jar": "https://edge.forgecdn.net/files/9036/103/customjukeboxdiscs-1.20.1-0.4.0.jar",
    "framework-forge-1.20.1-0.8.0.jar": "https://edge.forgecdn.net/files/7573/740/framework-forge-1.20.1-0.8.0.jar",
    "ftb-essentials-forge-2001.2.4.jar": "https://edge.forgecdn.net/files/7609/948/ftb-essentials-forge-2001.2.4.jar",
    "ftb-library-forge-2001.2.13.jar": "https://edge.forgecdn.net/files/8226/927/ftb-library-forge-2001.2.13.jar",
    "moreculling-0.24.6-all.jar": "https://edge.forgecdn.net/files/8691/508/moreculling-0.24.6-all.jar",
    "oceanicrealms-1.0.7-last-laster-ver.jar": "https://edge.forgecdn.net/files/7258/141/oceanicrealms-1.0.7-last-laster-ver.jar",
    "Better_Enderdragon.zip": "https://edge.forgecdn.net/files/4922/763/Better_Enderdragon.zip",
    "Derivative [25.0.1].zip": "https://edge.forgecdn.net/files/8403/225/Derivative%20%5B25.0.1%5D.zip",
}

# Upstream Modrinth versions for the 7 compatibility-patched 26.3 mods in The Lads SMP Reborn
MODRINTH_PATCHED_UPSTREAMS = {
    "nofigura-0.1.6.8+26.3-fabric-mc.jar": {
        "version_id": "enEBStOx",
        "url": "https://cdn.modrinth.com/data/qMYudiOM/versions/enEBStOx/nofigura-0.1.6.8%2B26.3-fabric-mc.jar",
    },
    "spotifyoverlay-1.0.0.jar": {
        "version_id": "hhRsYjqo",
        "url": "https://cdn.modrinth.com/data/VtphhPdM/versions/hhRsYjqo/spotifyoverlay-1.0.0.jar",
    },
    "Custom Nether Portals - Fabric - MC 26.1.2- 2.0.0.jar": {
        "version_id": "s87gX7kD",
        "url": "https://cdn.modrinth.com/data/2OCozLqc/versions/s87gX7kD/Custom%20Nether%20Portals%20-%20Fabric%20-%20MC%2026.1.2-%202.0.0.jar",
    },
    "HeadPulseSSC-1.9.1.jar": {
        "version_id": "mCRMrPuO",
        "url": "https://cdn.modrinth.com/data/msxkNWfX/versions/mCRMrPuO/HeadPulseSSC-1.9.1%20%281%29.jar",
    },
    "windui-1.0.2+26.3.jar": {
        "version_id": "FDidG6US",
        "url": "https://cdn.modrinth.com/data/MpV1qfp1/versions/FDidG6US/windui-1.0.2%2B26.3.jar",
    },
    "warden-with-loot-3.5.1.jar": {
        "version_id": "OnA9CUoT",
        "url": "https://cdn.modrinth.com/data/T2Z3j2pD/versions/OnA9CUoT/warden-with-loot-3.5.1.jar",
    },
    "sleep-heal-1.0.0.jar": {
        "version_id": "LjWE7uuQ",
        "url": "https://cdn.modrinth.com/data/biVjJCt5/versions/LjWE7uuQ/sleep-heal-1.0.0.jar",
    },
}

DEFAULT_EXCLUDES = [
    "config/worldedit/.archive-unpack/*",
    "config/jei/world/*",
    "config/resourceful-config-web.json",
    "config/voicechat/username-cache.json",
    "config/ladssmp/quarantine/*",
    "config/ladssmp/audit.log",
    # Per-player runtime state from the instance the pack is built from
    "config/betterjoinmessages_seen_players.json",
    "config/windui/chat/*",
    "config/flashback/*",
    "config/jei/server/world/*",
    "config/jeiworldgen-generation-cache.dat",
    "config/jeiworldgen-loot-cache.dat",
]


def is_excluded(rel_path: str) -> bool:
    rel_lower = rel_path.replace("\\", "/").lower()
    name_lower = os.path.basename(rel_lower)
    if "username-cache" in rel_lower or "hs_err_pid" in name_lower or ".mixin.out" in rel_lower:
        return True
    if name_lower.endswith((".backup", ".bak", ".log", ".exe", ".dll")):  # executables are third-party binaries mods unpack at runtime
        return True
    for pat in DEFAULT_EXCLUDES:
        p = pat.lower()
        if p.endswith("/*"):
            prefix = p[:-1]
            if rel_lower.startswith(prefix):
                return True
        elif rel_lower == p:
            return True
    return False


def is_lads_core_jar(filename: str) -> bool:
    low = filename.lower()
    return low.startswith("theladscore") or "theladscore" in low


def is_first_party_override_jar(filename: str) -> bool:
    low = filename.lower()
    return low.startswith("ladssmp-") or low == "ladssmp.jar"


def query_modrinth_by_hashes(hashes: list[str], algorithm: str = "sha512") -> dict:
    if not hashes:
        return {}
    req = urllib.request.Request(
        "https://api.modrinth.com/v2/version_files",
        data=json.dumps({"hashes": hashes, "algorithm": algorithm}).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": USER_AGENT},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def query_modrinth_version(version_id: str) -> dict:
    req = urllib.request.Request(
        f"https://api.modrinth.com/v2/version/{version_id}",
        headers={"User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_url_bytes(url: str, cache_dir: str) -> bytes:
    os.makedirs(cache_dir, exist_ok=True)
    key = hashlib.sha256(url.encode("utf-8")).hexdigest()[:24] + "_" + os.path.basename(urllib.parse.unquote(url))
    cache_path = os.path.join(cache_dir, key)
    if os.path.isfile(cache_path):
        with open(cache_path, "rb") as f:
            return f.read()
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
    with open(cache_path, "wb") as f:
        f.write(data)
    return data


def build_overlay_zip_bytes(upstream_bytes: bytes, patched_bytes: bytes) -> bytes:
    with zipfile.ZipFile(io.BytesIO(upstream_bytes), "r") as z_up:
        up_entries = {i.filename: z_up.read(i.filename) for i in z_up.infolist() if not i.is_dir()}
    with zipfile.ZipFile(io.BytesIO(patched_bytes), "r") as z_pat:
        pat_entries = {i.filename: z_pat.read(i.filename) for i in z_pat.infolist() if not i.is_dir()}

    removed = [k for k in up_entries if k not in pat_entries]
    if removed:
        raise ValueError(f"Unexpected removed entries in patched jar: {removed}")

    changed = {k: v for k, v in pat_entries.items() if up_entries.get(k) != v}
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as z_out:
        for k in sorted(changed.keys()):
            z_out.writestr(k, changed[k])
    return buf.getvalue()


def load_cf_instance_urls() -> dict[str, str]:
    urls = dict(CURSEFORGE_KNOWN_URLS)
    cf_root = os.path.expanduser(r"~\curseforge\minecraft\Instances")
    if os.path.isdir(cf_root):
        for inst_name in os.listdir(cf_root):
            m_path = os.path.join(cf_root, inst_name, "minecraftinstance.json")
            if os.path.isfile(m_path):
                try:
                    with open(m_path, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    for addon in data.get("installedAddons", []):
                        inf = addon.get("installedFile") or {}
                        fn = inf.get("fileName")
                        dl = inf.get("downloadUrl")
                        if fn and dl and fn not in urls:
                            urls[fn] = dl
                except Exception:
                    pass
    return urls


def parse_version_tuple(v: str):
    try:
        return tuple(int(x) for x in v.split("."))
    except Exception:
        return (0, 0)


def build_pack(
    instance_dir: str,
    output_zip: str,
    pack_version: str = "",
    patches_dir: str = "",
    applied_patches_override: str | None = None,
    cache_dir: str = "",
) -> dict:
    game_dir = os.path.join(instance_dir, "minecraft")
    mods_dir = os.path.join(game_dir, "mods")
    if not os.path.isdir(mods_dir):
        raise FileNotFoundError(f"No mods directory in {game_dir}")

    with open(os.path.join(instance_dir, "instance.json"), "r", encoding="utf-8") as f:
        instance_json = json.load(f)

    # The dev launcher's copy (<id>-dev, "<name> (Dev)") builds the same pack (ExperiencesService.CanonicalPackId).
    pack_id = instance_json["id"].removesuffix("-dev")
    mc_version = instance_json["mcVersion"]
    loader = instance_json["loader"]
    loader_version = instance_json["loaderVersion"]
    pack_name = instance_json.get("name", pack_id).removesuffix(" (Dev)")

    # Load patches for this pack
    patches = []
    if patches_dir and os.path.isdir(patches_dir):
        for fn in sorted(os.listdir(patches_dir)):
            if fn.startswith("update-") and fn.endswith(".json"):
                full_p = os.path.join(patches_dir, fn)
                with open(full_p, "r", encoding="utf-8") as pf:
                    pdata = json.load(pf)
                if pdata.get("packId") == pack_id:
                    patches.append((fn, full_p, pdata))
        patches.sort(key=lambda x: parse_version_tuple(x[2].get("version", "0.0")))

    if not pack_version:
        if patches:
            pack_version = patches[-1][2]["version"]
        else:
            pack_version = instance_json.get("packVersion") or "1.0.0"

    if applied_patches_override is not None:
        applied_patches = [x.strip() for x in applied_patches_override.split(",") if x.strip()]
    else:
        applied_patches = [
            p[2]["version"]
            for p in patches
            if parse_version_tuple(p[2]["version"]) <= parse_version_tuple(pack_version)
        ]

    if not cache_dir:
        cache_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")

    cf_urls = load_cf_instance_urls()

    # Collect all candidate archive files in mods/, resourcepacks/, shaderpacks/
    archive_candidates = []
    for sub, exts in [("mods", (".jar",)), ("resourcepacks", (".zip",)), ("shaderpacks", (".zip",))]:
        sub_dir = os.path.join(game_dir, sub)
        if not os.path.isdir(sub_dir):
            continue
        for fn in sorted(os.listdir(sub_dir)):
            full_p = os.path.join(sub_dir, fn)
            if not os.path.isfile(full_p):
                continue
            if not fn.lower().endswith(exts):
                continue
            with open(full_p, "rb") as f:
                data = f.read()
            archive_candidates.append(
                {
                    "sub": sub,
                    "name": fn,
                    "rel_path": f"{sub}/{fn}",
                    "full_path": full_p,
                    "size": len(data),
                    "sha1": hashlib.sha1(data).hexdigest().lower(),
                    "sha512": hashlib.sha512(data).hexdigest().lower(),
                    "bytes": data,
                }
            )

    # Batch query Modrinth API by SHA-512
    sha512_list = [c["sha512"] for c in archive_candidates]
    modrinth_matches = query_modrinth_by_hashes(sha512_list, "sha512")

    manifest_files = []
    jar_overlays = []
    override_archives = []
    unresolved_jars = []
    mods_metadata = []
    has_lads_core = False

    stats = {
        "total_archives": len(archive_candidates),
        "modrinth_direct": 0,
        "modrinth_patched": 0,
        "curseforge": 0,
        "lads_core_bundled": 0,
        "first_party_overrides": 0,
        "other_overrides": 0,
    }

    for item in archive_candidates:
        fn = item["name"]
        rel = item["rel_path"]
        h1 = item["sha1"]
        h512 = item["sha512"]
        sz = item["size"]

        if item["sub"] == "mods":
            mods_metadata.append({"fileName": fn, "size": sz, "sha1": h1})

        # 1. Lads Core (bundled with launcher via ModpackCoreSync)
        if item["sub"] == "mods" and is_lads_core_jar(fn):
            has_lads_core = True
            stats["lads_core_bundled"] += 1
            continue

        # 2. First-party Lads SMP mod (kept in overrides/mods/)
        if item["sub"] == "mods" and is_first_party_override_jar(fn):
            override_archives.append(item)
            stats["first_party_overrides"] += 1
            continue

        # 3. Direct Modrinth match by SHA-512
        if h512 in modrinth_matches:
            ver_obj = modrinth_matches[h512]
            file_obj = next(
                (f for f in ver_obj.get("files", []) if f.get("hashes", {}).get("sha512", "").lower() == h512),
                ver_obj["files"][0],
            )
            dl_url = file_obj["url"]
            manifest_files.append(
                {
                    "path": rel,
                    "hashes": {"sha1": h1, "sha512": h512},
                    "env": {"client": "required", "server": "required"},
                    "downloads": [dl_url],
                    "fileSize": sz,
                }
            )
            stats["modrinth_direct"] += 1
            continue

        # 4. Compatibility-patched Modrinth upstream mod (downloaded from Modrinth CDN + tiny entry overlay)
        if fn in MODRINTH_PATCHED_UPSTREAMS:
            up_info = MODRINTH_PATCHED_UPSTREAMS[fn]
            ver_obj = query_modrinth_version(up_info["version_id"])
            file_obj = ver_obj["files"][0]
            up_url = file_obj["url"]
            up_sha1 = file_obj["hashes"]["sha1"].lower()
            up_sha512 = file_obj["hashes"]["sha512"].lower()
            up_size = file_obj["size"]

            up_bytes = fetch_url_bytes(up_url, cache_dir)
            if hashlib.sha1(up_bytes).hexdigest().lower() != up_sha1:
                raise ValueError(f"Upstream hash mismatch for {fn} from {up_url}")

            overlay_bytes = build_overlay_zip_bytes(up_bytes, item["bytes"])
            overlay_rel = f"jar-overlays/{fn}.overlay.zip"
            overlay_sha256 = hashlib.sha256(overlay_bytes).hexdigest().lower()

            manifest_files.append(
                {
                    "path": rel,
                    "hashes": {"sha1": up_sha1, "sha512": up_sha512},
                    "env": {"client": "required", "server": "required"},
                    "downloads": [up_url],
                    "fileSize": up_size,
                }
            )
            jar_overlays.append(
                {
                    "path": rel,
                    "overlayZip": overlay_rel,
                    "overlaySha256": overlay_sha256,
                    "patchedSha1": h1,
                    "patchedSha512": h512,
                    "bytes": overlay_bytes,
                }
            )
            stats["modrinth_patched"] += 1
            continue

        # 5. CurseForge CDN match
        if fn in cf_urls:
            cf_url = cf_urls[fn]
            manifest_files.append(
                {
                    "path": rel,
                    "hashes": {"sha1": h1, "sha512": h512},
                    "env": {"client": "required", "server": "required"},
                    "downloads": [cf_url],
                    "fileSize": sz,
                }
            )
            stats["curseforge"] += 1
            continue

        # 6. Remaining non-CDN archive (e.g. Quark Programmer Art.zip generated by Quark).
        # A third-party mod jar is never shipped inside the pack: it must resolve to Modrinth or CurseForge.
        if item["sub"] == "mods" and fn.lower().endswith(".jar"):
            unresolved_jars.append(fn)
            continue
        override_archives.append(item)
        stats["other_overrides"] += 1

    if unresolved_jars:
        raise ValueError("Third-party jars not found on Modrinth/CurseForge (add a CDN URL, never bundle them): " + ", ".join(sorted(unresolved_jars)))

    # Loader dependency key for modrinth.index.json
    loader_key_map = {
        "fabric": "fabric-loader",
        "quilt": "quilt-loader",
        "forge": "forge",
        "neoforge": "neoforge",
    }
    deps = {"minecraft": mc_version}
    if loader.lower() in loader_key_map and loader_version:
        deps[loader_key_map[loader.lower()]] = loader_version

    modrinth_index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": pack_version,
        "name": pack_name,
        "summary": f"Official The Lads Modpack ({pack_name})",
        "dependencies": deps,
        "files": manifest_files,
    }

    pack_json = {
        "packId": pack_id,
        "packVersion": pack_version,
        "createdUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "mcVersion": mc_version,
        "loader": loader,
        "loaderVersion": loader_version,
        "modCount": len(mods_metadata),
        "includeLadsCore": has_lads_core,
        "appliedPatches": applied_patches,
        "mods": mods_metadata,
    }

    os.makedirs(os.path.dirname(os.path.abspath(output_zip)), exist_ok=True)
    tmp_zip = output_zip + ".tmp"
    if os.path.exists(tmp_zip):
        os.remove(tmp_zip)

    override_rel_set = {x["rel_path"] for x in override_archives}
    manifest_rel_set = {x["path"] for x in manifest_files}

    with zipfile.ZipFile(tmp_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        # 1. modrinth.index.json
        zf.writestr("modrinth.index.json", json.dumps(modrinth_index, indent=2).encode("utf-8"))

        # 2. pack.json
        zf.writestr("pack.json", json.dumps(pack_json, indent=2).encode("utf-8"))

        # 3. jar-overlays/ (if any)
        if jar_overlays:
            overlay_manifest = [
                {
                    "path": o["path"],
                    "overlayZip": o["overlayZip"],
                    "overlaySha256": o["overlaySha256"],
                    "patchedSha1": o["patchedSha1"],
                    "patchedSha512": o["patchedSha512"],
                }
                for o in jar_overlays
            ]
            zf.writestr("jar-overlays/manifest.json", json.dumps({"formatVersion": 1, "overlays": overlay_manifest}, indent=2).encode("utf-8"))
            for o in jar_overlays:
                zf.writestr(o["overlayZip"], o["bytes"])

        # 4. First-party / override archives
        for item in override_archives:
            zf.writestr(f"overrides/{item['rel_path']}", item["bytes"])

        # 5. Configs & non-archive files under config, defaultconfigs, configureddefaults, resourcepacks, shaderpacks
        for include_dir in ["config", "defaultconfigs", "configureddefaults", "resourcepacks", "shaderpacks"]:
            root_d = os.path.join(game_dir, include_dir)
            if not os.path.isdir(root_d):
                continue
            for dirpath, _, filenames in os.walk(root_d):
                for fn in sorted(filenames):
                    full_p = os.path.join(dirpath, fn)
                    rel = os.path.relpath(full_p, game_dir).replace("\\", "/")
                    if is_excluded(rel):
                        continue
                    if rel in manifest_rel_set or rel in override_rel_set:
                        continue
                    # Skip any .jar or .disabled inside resourcepacks/shaderpacks/mods
                    if include_dir in ("resourcepacks", "shaderpacks") and fn.lower().endswith(".zip"):
                        continue
                    with open(full_p, "rb") as f:
                        zf.writestr(f"overrides/{rel}", f.read())

        # 6. servers.dat & icon.png
        servers_dat = os.path.join(game_dir, "servers.dat")
        if os.path.isfile(servers_dat):
            with open(servers_dat, "rb") as f:
                zf.writestr("overrides/servers.dat", f.read())

        icon_png = os.path.join(instance_dir, "icon.png")
        if os.path.isfile(icon_png):
            with open(icon_png, "rb") as f:
                zf.writestr("overrides/icon.png", f.read())

        # 7. Patches
        for fn, full_p, _ in patches:
            with open(full_p, "rb") as f:
                zf.writestr(f"patches/{fn}", f.read())

    os.replace(tmp_zip, output_zip)
    with open(output_zip, "rb") as f:
        sha256_hex = hashlib.sha256(f.read()).hexdigest().upper()
    size_bytes = os.path.getsize(output_zip)

    return {
        "pack_id": pack_id,
        "pack_version": pack_version,
        "output_zip": output_zip,
        "size_bytes": size_bytes,
        "size_mb": round(size_bytes / (1024 * 1024), 2),
        "sha256": sha256_hex,
        "stats": stats,
        "applied_patches": applied_patches,
    }


def main():
    parser = argparse.ArgumentParser(description="Build manifest-driven Lads Experience modpack zip.")
    parser.add_argument("--instance-dir", required=True, help="Path to the instance folder")
    parser.add_argument("--output", required=True, help="Output .zip path")
    parser.add_argument("--pack-version", default="", help="Override packVersion")
    parser.add_argument("--patches-dir", default="", help="Directory containing update-*.json patches")
    parser.add_argument("--applied-patches", default=None, help="Comma-separated list of already-applied patch versions")
    args = parser.parse_args()

    res = build_pack(
        instance_dir=os.path.expandvars(args.instance_dir),
        output_zip=os.path.expandvars(args.output),
        pack_version=args.pack_version,
        patches_dir=os.path.expandvars(args.patches_dir) if args.patches_dir else "",
        applied_patches_override=args.applied_patches,
    )
    print(json.dumps(res, indent=2))


if __name__ == "__main__":
    main()
