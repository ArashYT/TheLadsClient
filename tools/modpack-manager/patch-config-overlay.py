#!/usr/bin/env python3
"""Builds a QA config folder: an instance's config with pack patches' configPatches applied. The instance is only read.

  python -I tools/modpack-manager/patch-config-overlay.py <instance dir> <out dir> <update-x.json> [...] [--skip config/<file>]

Pass <out dir> to the QA harness as LADS_VERIFY_CONFIG_FROM. The edits follow ModpackPatchEngine.ApplyConfigPatch (first line with the
bare key gets the value, a missing key is appended; json "set" paths split on dots); --skip leaves a file as the instance has it.
"""
import argparse
import json
import re
import shutil
import sys
from pathlib import Path


def apply(config, patch, skip=()):
    for c in patch.get("configPatches") or []:
        rel = c["file"]
        if rel in skip:
            continue
        f = config / Path(rel).relative_to("config")
        f.parent.mkdir(parents=True, exist_ok=True)
        if (c.get("format") or "json").lower() == "json" and c.get("set") is not None:
            root = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
            for path, value in c["set"].items():
                node, parts = root, path.split(".")
                for part in parts[:-1]:
                    if not isinstance(node.get(part), dict):
                        node[part] = {}
                    node = node[part]
                node[parts[-1]] = value
            f.write_text(json.dumps(root, indent=2), encoding="utf-8")
        elif c.get("keyValues") is not None:
            lines = f.read_text(encoding="utf-8").splitlines() if f.exists() else []
            for key, value in c["keyValues"].items():
                pattern = re.compile(r"^(\s*" + re.escape(key) + r"\s*[=:]\s*)")
                for i, line in enumerate(lines):
                    if m := pattern.match(line):
                        lines[i] = m.group(1) + value
                        break
                else:
                    lines.append(f"{key}={value}")
            f.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("instance")
    ap.add_argument("out")
    ap.add_argument("patches", nargs="+")
    ap.add_argument("--skip", action="append", default=[], help="config/<file> to leave as the instance has it")
    a = ap.parse_args(argv)
    src, out = Path(a.instance).resolve() / "minecraft" / "config", Path(a.out).resolve()
    if not src.is_dir():
        sys.exit(f"no config folder at {src}")
    if out == src or src in out.parents or out in src.parents:
        sys.exit("the output folder must be outside the instance")
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(src, out)
    for p in a.patches:
        apply(out, json.loads(Path(p).read_text(encoding="utf-8")), a.skip)
        print(f"applied {Path(p).name}" + (f" (skipped {', '.join(a.skip)})" if a.skip else ""))
    print(out)


if __name__ == "__main__":
    main()
