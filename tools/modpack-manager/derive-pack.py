#!/usr/bin/env python3
"""
Builds the next version of a manifest pack archive from the previous archive when only first-party override jars
(ladssmp-*.jar) and/or config files change, without a dev instance and without Modrinth lookups (build-manifest-pack.py
does the full build from an instance; this keeps every third-party file reference exactly as in the base archive).

  py -3 derive-pack.py --base lads-smp-reborn-26.3-0.5.0.zip --out lads-smp-reborn-26.3-0.5.1.zip --version 0.5.1 \
      --swap-jar ladssmp-0.3.1.jar=path\\to\\ladssmp-0.3.2.jar [--set-file config/x.json=path\\to\\x.json] [--delete-file config/y.json]

What it changes, nothing else:
  - overrides/mods/<old jar> is replaced by overrides/mods/<new jar name>
  - pack.json: packVersion, createdUtc, appliedPatches (+ the new version), the mods[] entry of each swapped jar (name, size, sha1)
  - modrinth.index.json: versionId
  - overrides/<path> files given with --set-file / --delete-file
"""
import argparse, datetime, hashlib, json, os, sys, zipfile


def sha1(b: bytes) -> str:
    return hashlib.sha1(b).hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--version", required=True)
    ap.add_argument("--swap-jar", action="append", default=[], help="OLDNAME.jar=path/to/NEWNAME.jar (an overrides/mods jar)")
    ap.add_argument("--set-file", action="append", default=[], help="config/rel/path=local file (added or replaced under overrides/)")
    ap.add_argument("--delete-file", action="append", default=[], help="path under overrides/ to drop")
    a = ap.parse_args()

    swaps = {}
    for s in a.swap_jar:
        old, new = s.split("=", 1)
        swaps[old] = (os.path.basename(new), open(new, "rb").read())
    sets = {}
    for s in a.set_file:
        rel, local = s.split("=", 1)
        sets["overrides/" + rel.replace("\\", "/")] = open(local, "rb").read()
    deletes = {"overrides/" + d.replace("\\", "/") for d in a.delete_file}

    zin = zipfile.ZipFile(a.base)
    names = {i.filename for i in zin.infolist()}
    for old in swaps:
        if "overrides/mods/" + old not in names:
            print("base archive has no overrides/mods/" + old, file=sys.stderr)
            return 2

    out_tmp = a.out + ".part"
    with zipfile.ZipFile(out_tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            fn = item.filename
            data = zin.read(fn)
            if fn.startswith("overrides/mods/") and fn[len("overrides/mods/"):] in swaps:
                new_name, new_data = swaps[fn[len("overrides/mods/"):]]
                zout.writestr("overrides/mods/" + new_name, new_data)
                continue
            if fn in deletes:
                continue
            if fn in sets:
                zout.writestr(fn, sets.pop(fn))
                continue
            if fn == "pack.json":
                pj = json.loads(data.decode("utf-8"))
                pj["packVersion"] = a.version
                pj["createdUtc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
                applied = list(pj.get("appliedPatches", []))
                if a.version not in applied:
                    applied.append(a.version)
                pj["appliedPatches"] = applied
                for m in pj["mods"]:
                    if m["fileName"] in swaps:
                        new_name, new_data = swaps[m["fileName"]]
                        m["fileName"], m["size"], m["sha1"] = new_name, len(new_data), sha1(new_data)
                data = json.dumps(pj, indent=2).encode("utf-8")
            elif fn == "modrinth.index.json":
                ix = json.loads(data.decode("utf-8"))
                ix["versionId"] = a.version
                data = json.dumps(ix, indent=2).encode("utf-8")
            zout.writestr(item if fn not in ("pack.json", "modrinth.index.json") else fn, data)
        for fn, data in sets.items():  # new files
            zout.writestr(fn, data)
    os.replace(out_tmp, a.out)
    print("wrote", a.out, os.path.getsize(a.out), "bytes, sha256", hashlib.sha256(open(a.out, "rb").read()).hexdigest().upper())
    return 0


if __name__ == "__main__":
    sys.exit(main())
