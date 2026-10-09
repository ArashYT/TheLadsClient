"""Mixin sanity check for built Core jars (run before launching a Fabric game).

    python tools/check-mixins.py <jar> [<jar> ...]

Fails (exit 1) when, in any jar:
  - a class listed in a mixin config (mixins/client/server) is not in the jar,
  - fabric.mod.json lists a mixin config twice or that is not in the jar,
  - a *.mixins.json at the jar root is not listed in fabric.mod.json (it would never load),
  - the same mixin class sits in two configs,
  - a config's refmap file is missing from the jar,
  - the jar targets Minecraft 1.20.x (Loom remaps by name there) and a listed config has no refmap or no "defaultRequire": 1.
"""
import json
import sys
import zipfile


def check(path):
    jar = zipfile.ZipFile(path)
    names = set(jar.namelist())
    problems = []
    mod = json.loads(jar.read("fabric.mod.json")) if "fabric.mod.json" in names else {}
    listed_configs = [c if isinstance(c, str) else c.get("config") for c in mod.get("mixins", [])]
    mc = str(mod.get("depends", {}).get("minecraft", ""))
    strict = mc.startswith("1.20")

    for config in {c for c in listed_configs if listed_configs.count(c) > 1}:
        problems.append(f"{config}: listed twice in fabric.mod.json")
    for config in listed_configs:
        if config not in names:
            problems.append(f"{config}: listed in fabric.mod.json but not in the jar")
    for config in sorted(n for n in names if n.endswith(".mixins.json") and "/" not in n):
        if mod and config not in listed_configs:
            problems.append(f"{config}: in the jar but not listed in fabric.mod.json (never loads)")

    seen = {}
    total = 0
    for config in sorted(set(listed_configs) | {n for n in names if n.endswith(".mixins.json") and "/" not in n}):
        if config not in names:
            continue
        data = json.loads(jar.read(config))
        package = data.get("package", "").replace(".", "/")
        classes = [m for key in ("mixins", "client", "server") for m in data.get(key, [])]
        total += len(classes)
        for m in classes:
            if f"{package}/{m.replace('.', '/')}.class" not in names:
                problems.append(f"{config}: mixin class {m} missing from the jar")
            full = f"{package}/{m}"
            if full in seen and seen[full] != config:
                problems.append(f"{full}: listed in both {seen[full]} and {config}")
            seen[full] = config
        refmap = data.get("refmap")
        if refmap and refmap not in names:
            problems.append(f"{config}: refmap {refmap} missing from the jar")
        if strict and config in listed_configs:
            if not refmap:
                problems.append(f"{config}: no refmap (1.20.x mixins are remapped by name)")
            elif refmap in names:  # info only: accessor/invoker-only mixins legitimately have no entry
                mapped = json.loads(jar.read(refmap)).get("mappings", {})
                bare = [m for m in classes if f"{package}/{m}" not in mapped]
                if bare:
                    print(f"  info: {config}: {len(bare)}/{len(classes)} mixin classes have no refmap entry: {bare}")
            if (data.get("injectors") or {}).get("defaultRequire") != 1 and data.get("defaultRequire") != 1:
                problems.append(f"{config}: defaultRequire is not 1")
    print(f"{path}: {len(listed_configs)} configs in fabric.mod.json, {total} mixin classes, "
          f"{'1.20.x strict' if strict else 'lenient'}, {len(problems)} problem(s)")
    for p in problems:
        print("  PROBLEM:", p)
    return not problems


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    results = [check(p) for p in sys.argv[1:]]
    sys.exit(0 if all(results) else 1)
