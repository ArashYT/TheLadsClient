#!/usr/bin/env python3
"""Server compatibility guard for the Zero SMP modpack (Forge 1.20.1).

    python check-server-compat.py <host> [port] [mods dir] [--remove a.jar,b.jar] [--status status.json] [--save status.json]

Status-pings the server (the server-list ping: no login, no account, nothing is sent but the handshake and the status request),
decodes Forge's "forgeData" and reports, against the client pack in <mods dir>:
  ERROR  a channel the server REQUIRES on the client whose mod the pack lacks (the Forge handshake would refuse the join),
  ERROR  a pack mod that registers a network channel but is not on the server (the server would refuse the client; heuristic),
  ERROR  with --remove: a jar whose mod provides a server-required channel, or Corgilib / Data Anchor (see PROTECTED),
  WARN   a mod the server lists (with a version) that the pack lacks / that --remove would drop: the status ping cannot see
         registry sync, so only a join test proves such a removal safe.
Exit code: 0 = no ERROR, 1 = ERROR found, 2 = could not reach the server / bad input (the caller decides whether to go on).

--status reads a recorded ping instead of connecting (tests, offline); --save writes the ping (favicon dropped) for that.
Python 3.8+, standard library only.
"""
import argparse
import io
import json
import re
import socket
import struct
import sys
import zipfile
from pathlib import Path

# Mods whose removal from the client is known to stop players joining even though the ping lists their channels as optional
# (registry sync): patch 1.1.1 removed them and the join failed.
PROTECTED = {"corgilib": "Corgilib", "dataanchor": "Data Anchor"}
# Jars whose file name does not start with their mod id (only needed for jars already gone from the pack folder).
JAR_ALIASES = {"player-animation-lib": "playeranimator"}
# Channel namespaces that belong to the game / Forge itself, never to a pack jar.
BUILTIN_NAMESPACES = {"minecraft", "forge", "fml"}
PROTOCOL_1_20_1 = 763


# ---------------------------------------------------------------- ping

def _varint(n):
    out = b""
    while True:
        b, n = n & 0x7F, n >> 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out


def _read_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("server closed the connection")
        data += chunk
    return data


def _read_varint(sock):
    n = 0
    for i in range(5):
        b = _read_exact(sock, 1)[0]
        n |= (b & 0x7F) << (7 * i)
        if not b & 0x80:
            return n
    raise ValueError("varint too long")


def ping(host, port=25565, timeout=10):
    """Server-list status ping; returns the parsed status JSON."""
    with socket.create_connection((host, port), timeout=timeout) as s:
        s.settimeout(timeout)
        body = b"\x00" + _varint(PROTOCOL_1_20_1) + _varint(len(host.encode())) + host.encode() + struct.pack(">H", port) + _varint(1)
        s.sendall(_varint(len(body)) + body + b"\x01\x00")
        _read_varint(s)  # packet length
        if _read_varint(s) != 0:
            raise ValueError("unexpected status packet")
        return json.loads(_read_exact(s, _read_varint(s)))


# ---------------------------------------------------------------- forgeData

def decode_forge_data(d):
    """Forge's optimized status encoding: 15 bits per char; chars 0-1 = byte size; then truncated flag, ushort mod count,
    per mod varint(channels<<1 | ignoreServerOnly), modId, [version], channels(path, version, requiredOnClient);
    then varint count of non-mod channels (full name, version, requiredOnClient)."""
    size = ord(d[0]) | (ord(d[1]) << 15)
    out, buf, bits = bytearray(), 0, 0
    for ch in d[2:]:
        while bits >= 8:
            out.append(buf & 0xFF)
            buf >>= 8
            bits -= 8
        buf |= (ord(ch) & 0x7FFF) << bits
        bits += 15
    while len(out) < size:
        out.append(buf & 0xFF)
        buf >>= 8
        bits -= 8
    r = io.BytesIO(bytes(out[:size]))

    def byte():
        b = r.read(1)
        if not b:
            raise ValueError("forgeData ends early")
        return b[0]

    def varint():
        n = 0
        for i in range(5):
            b = byte()
            n |= (b & 0x7F) << (7 * i)
            if not b & 0x80:
                return n
        raise ValueError("varint too long")

    def utf():
        return r.read(varint()).decode("utf-8")

    truncated = byte() != 0
    mod_count = (byte() << 8) | byte()
    mods, channels, owner = {}, [], {}
    for _ in range(mod_count):
        flags = varint()
        mod_id = utf()
        mods[mod_id] = None if flags & 1 else utf()  # None = ignoreServerOnly: clients need not have it
        for _ in range(flags >> 1):
            channels.append((mod_id + ":" + utf(), utf(), byte() != 0))
            owner[channels[-1][0]] = mod_id
    for _ in range(varint()):
        channels.append((utf(), utf(), byte() != 0))
    return {"truncated": truncated, "mods": mods, "channels": channels, "owner": owner}


def server_info(status):
    """{'truncated', 'mods': {id: version|None}, 'channels': [(name, version, required)], 'owner': {channel: mod}} or None for a non-Forge server."""
    fd = status.get("forgeData")
    if not fd:
        return None
    if fd.get("d"):
        return decode_forge_data(fd["d"])
    return {  # old, un-optimized form
        "truncated": bool(fd.get("truncated")),
        "mods": {m["modId"]: m.get("modmarker") for m in fd.get("mods", [])},
        "channels": [(c["res"], c.get("version", ""), bool(c.get("required"))) for c in fd.get("channels", [])],
        "owner": {},
    }


# ---------------------------------------------------------------- client jars

def _mods_toml_ids(text):
    try:
        import tomllib
        return [m["modId"] for m in tomllib.loads(text).get("mods", []) if "modId" in m]
    except Exception:  # missing tomllib (3.8-3.10) or a mods.toml real TOML parsers reject: only the [[mods]] tables count
        ids = []
        for section in re.split(r"^\s*\[\[", text, flags=re.M):
            if section.startswith("mods]]"):
                ids += re.findall(r'^\s*modId\s*=\s*"([^"]+)"', section, flags=re.M)
        return ids


def norm(mod_id):
    return mod_id.lower().replace("-", "_")


def jar_info(source, depth=0):
    """{'ids': mod ids the jar provides (nested jars included), 'forge': has a mods.toml, 'ignore': its displayTest ignores the
    server version, 'path': where it is} for one jar."""
    info = {"ids": set(), "forge": False, "ignore": False, "path": source if isinstance(source, (str, Path)) else None}
    try:
        z = zipfile.ZipFile(source if isinstance(source, (str, Path)) else io.BytesIO(source))
    except (zipfile.BadZipFile, OSError):
        return info
    with z:
        for name in z.namelist():
            if name == "META-INF/mods.toml":
                text = z.read(name).decode("utf-8", "ignore")
                info["forge"] = True
                info["ids"].update(_mods_toml_ids(text))
                info["ignore"] |= bool(re.search(r'displayTest\s*=\s*"(IGNORE_\w+|NONE)"', text))
            elif name == "fabric.mod.json":
                try:
                    meta = json.loads(z.read(name).decode("utf-8", "ignore"), strict=False)
                    info["ids"].update([meta["id"]] + [p if isinstance(p, str) else p.get("id") for p in meta.get("provides", [])])
                except (ValueError, KeyError):
                    pass
            elif depth < 2 and name.endswith(".jar") and name.startswith(("META-INF/jarjar/", "META-INF/jars/")):
                info["ids"].update(jar_info(z.read(name), depth + 1)["ids"])
    info["ids"] = {norm(i) for i in info["ids"] if i}
    return info


def strict_channel(path):
    """True when a class of the jar registers a Forge network channel whose version predicates compare strings (String::equals):
    such a channel is refused unless both sides have it. Accept-anything predicates (appleskin, configured, Distant Horizons) are not."""
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if name.endswith(".class"):
                raw = z.read(name)
                if (b"newSimpleChannel" in raw or b"newEventChannel" in raw or b"ChannelBuilder" in raw) and b"equals" in raw                         and not (b"acceptMissingOr" in raw or b"ABSENT" in raw or b"ACCEPTVANILLA" in raw):
                    return True
    return False


def scan_mods_dir(mods_dir):
    """{jar file name: jar_info} for the enabled jars of a mods folder."""
    return {p.name: jar_info(p) for p in sorted(Path(mods_dir).glob("*.jar"))}


def guess_ids(file_name, known_ids):
    """Mod ids of a jar we only know by name (already gone from the dir): file-name prefixes matched to known server ids."""
    tokens = [t for t in re.split(r"[-_+ .]+", Path(file_name).stem.lower()) if t]
    squashed = {i.replace("_", "").lower(): i for i in known_ids}
    found = {mod for alias, mod in JAR_ALIASES.items() if alias in Path(file_name).stem.lower() and mod in known_ids}
    for n in range(1, min(4, len(tokens)) + 1):
        hit = squashed.get("".join(tokens[:n]))
        if hit:
            found.add(hit)
    return found


# ---------------------------------------------------------------- analysis

def required_mods(server):
    """Mod ids that own a channel the server requires on the client. A channel inside a mod's group belongs to that mod; a
    free-standing one (full name) belongs to the mod whose id is its namespace, if any (puzzleslib-1:play has no such mod)."""
    mods = {norm(m) for m in server["mods"]}
    out = set()
    for name, _, required in server["channels"]:
        ns = server["owner"].get(name) or name.split(":")[0]
        if required and norm(ns) in mods and norm(ns) not in BUILTIN_NAMESPACES:
            out.add(norm(ns))
    return out


def analyze(server, jars):
    """Findings [(level, text)] for the pack (jars from scan_mods_dir) against the server."""
    findings = []
    have = set().union(*(j["ids"] for j in jars.values())) if jars else set()
    server_ids = {norm(m) for m in server["mods"]}
    for mod in sorted(required_mods(server) - have):
        findings.append(("ERROR", f"server requires a channel of '{mod}' on the client; the pack has no such mod: players cannot join"))
    for mod, version in sorted(server["mods"].items()):
        if version is not None and norm(mod) not in have and not norm(mod).startswith("fabric_") and norm(mod) not in BUILTIN_NAMESPACES:
            findings.append(("WARN", f"server lists '{mod}' {version}; the pack lacks it (server list shows 'incompatible'; join depends on registry sync)"))
    for name, info in jars.items():  # pack mods the server lacks that register a strict channel: the server would refuse the client
        if info["forge"] and not info["ignore"] and info["ids"] and not (info["ids"] & server_ids) and info["path"] and strict_channel(info["path"]):
            findings.append(("ERROR", f"{name} registers a version-matched network channel (mods {sorted(info['ids'])[:4]}) and the server has none of them: "
                                      "the server refuses this client (heuristic from the jar's classes; test a join)"))
    return findings


def check_removals(server, removing, jars):
    """Findings [(level, text)] for removing jars (file names) from the pack."""
    findings = []
    required = required_mods(server)
    listed = {norm(m): m for m, v in server["mods"].items() if v is not None}
    still = set().union(*(j["ids"] for n, j in jars.items() if n not in removing))  # another jar (e.g. a nested copy) may keep the mod
    for name in removing:
        ids = (jars[name]["ids"] if name in jars else {norm(i) for i in guess_ids(name, set(server["mods"]))}) - still
        for mod in sorted(ids & required):
            findings.append(("ERROR", f"removing {name}: mod '{mod}' owns a channel the server requires on the client"))
        for mod in sorted(ids & set(PROTECTED)):
            findings.append(("ERROR", f"removing {name}: {PROTECTED[mod]} must stay (patch 1.1.1 dropped it and the Zero SMP server refused the join)"))
        for mod in sorted(ids & set(listed) - required - set(PROTECTED)):
            findings.append(("WARN", f"removing {name}: the server lists '{listed[mod]}' {server['mods'][listed[mod]]}; only a join test proves this safe"))
    return findings


# ---------------------------------------------------------------- cli

def main(argv=None):
    ap = argparse.ArgumentParser(description="Status-ping a Forge 1.20.1 server and compare it with a client mods folder.")
    ap.add_argument("target", nargs="*", help="host [port] [mods dir]  (host:port also works)")
    ap.add_argument("--remove", default="", help="comma separated jar names a patch would remove; checked against the server")
    ap.add_argument("--status", help="read this recorded status JSON instead of pinging")
    ap.add_argument("--save", help="write the ping (favicon dropped) to this file")
    args = ap.parse_args(argv)

    host, port, mods_dir = None, 25565, None
    for a in args.target:
        if re.fullmatch(r"\d{1,5}", a):
            port = int(a)
        elif host is None and not Path(a).is_dir():
            host, _, p = a.partition(":")
            port = int(p) if p.isdigit() else port
        else:
            mods_dir = a
    try:
        if args.status:
            status = json.loads(Path(args.status).read_text(encoding="utf-8"))
        elif host:
            status = ping(host, port)
        else:
            ap.error("give a host (or --status FILE)")
        server = server_info(status)
    except (OSError, ValueError, KeyError) as e:
        print(f"cannot read the server status: {e}", file=sys.stderr)
        return 2
    if args.save:
        Path(args.save).write_text(json.dumps({k: v for k, v in status.items() if k != "favicon"}, indent=1), encoding="utf-8")
    if server is None:
        print("The server sent no forgeData: not a Forge server, nothing to compare.")
        return 0
    required = [c for c in server["channels"] if c[2]]
    print(f"{host or args.status}: {len(server['mods'])} mods, {len(server['channels'])} channels ({len(required)} required on the client)"
          + ("  [TRUNCATED: the list is incomplete]" if server["truncated"] else ""))

    findings = []
    jars = {}
    if mods_dir:
        jars = scan_mods_dir(mods_dir)
        print(f"client pack: {len(jars)} jars in {mods_dir}")
        findings += analyze(server, jars)
    removing = [r.strip() for r in args.remove.split(",") if r.strip()]
    if removing:
        findings += check_removals(server, removing, jars)
    if server["truncated"]:
        findings.append(("WARN", "the server's mod list was truncated by the ping: channels of the missing mods are not checked"))
    for level, text in findings:
        print(f"{level}: {text}")
    errors = sum(1 for level, _ in findings if level == "ERROR")
    print(f"{errors} error(s), {len(findings) - errors} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

