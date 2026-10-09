#!/usr/bin/env python
"""Attribute JFR execution samples of a Minecraft/Forge process to mods.

usage: jfr_attrib.py <recording.jfr> [--jfr <path-to-jfr.exe>] [--thread "Render thread"] [--out summary.json]

Needs: the mods folder of the instance (for the package -> jar map) and a `jfr` binary (JDK 17+).
Frames are attributed to a mod in two ways: (1) the frame's class lives in a package that only one mod jar owns;
(2) the frame is a Mixin handler merged into a vanilla class: handler$xxx$<mixinclass>$name or name like modid$method.
For each sample we report the first (innermost) mod frame (= who is burning CPU) and, separately, every mod that appears
anywhere in the stack (= who is on the path).
"""
import argparse, collections, glob, json, os, re, subprocess, sys, zipfile

def build_pkg_map(mods_dir):
    pkg2mod = {}
    ids = {}
    for jar in sorted(glob.glob(os.path.join(mods_dir, '*.jar'))):
        name = os.path.basename(jar)
        short = re.sub(r'[-_ +\[(].*$', '', name)  # crude: 'embeddium-0.3.31...' -> 'embeddium'
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        for n in z.namelist():
            if n.endswith('.class') and not n.startswith('META-INF'):
                p = n.rsplit('/', 1)[0].replace('/', '.') if '/' in n else ''
                if p and p not in pkg2mod:
                    pkg2mod[p] = short
        # mod ids as mixin tokens
        try:
            if 'META-INF/mods.toml' in z.namelist():
                t = z.read('META-INF/mods.toml').decode('utf8', 'replace')
                for m in re.findall(r'^\s*modId\s*=\s*"([^"]+)"', t, re.M)[:1]:
                    ids[m.lower()] = short
            if 'fabric.mod.json' in z.namelist():
                fj = json.loads(z.read('fabric.mod.json').decode('utf8', 'replace'), strict=False)
                ids[str(fj.get('id', '')).lower()] = short
        except Exception:
            pass
        ids[short.lower()] = short
    return pkg2mod, ids

def mod_of_class(cls, pkg2mod):
    p = cls.rsplit('.', 1)[0] if '.' in cls else ''
    while p:
        if p in pkg2mod:
            return pkg2mod[p]
        p = p.rsplit('.', 1)[0] if '.' in p else ''
    return None

def mod_of_method(meth, ids):
    # Mixin handlers: handler$zzz000$modid$method, or modid$method (MixinExtras / Embeddium style)
    if '$' not in meth:
        return None
    for tok in re.split(r'[$]', meth):
        t = tok.lower()
        if t in ids and len(t) > 3:
            return ids[t]
    return None

FRAME = re.compile(r'^\s+([A-Za-z0-9_.$/<>-]+)\.([A-Za-z0-9_$<>-]+)\(.*\)\s+line:')

def run(jfr_bin, rec):
    cmd = [jfr_bin, 'print', '--events', 'jdk.ExecutionSample', '--stack-depth', '96', rec]
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True, encoding='utf8', errors='replace')
    return p.stdout

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('rec')
    ap.add_argument('--jfr', default='jfr')
    ap.add_argument('--mods', default=os.path.join(os.environ.get('APPDATA', ''), '.theladsclient', 'instances', 'zero-smp-1.20.1', 'minecraft', 'mods'))
    ap.add_argument('--thread', default=None, help='only samples from this thread name (substring)')
    ap.add_argument('--out', default=None)
    a = ap.parse_args()
    pkg2mod, ids = build_pkg_map(a.mods)
    first = collections.Counter(); onpath = collections.Counter(); threads = collections.Counter()
    leaf = collections.Counter()
    total = 0
    cur_thread = None; frames = []
    def flush():
        nonlocal total
        if not frames: return
        if a.thread and (not cur_thread or a.thread.lower() not in cur_thread.lower()): return
        total += 1
        threads[cur_thread or '?'] += 1
        seen = []
        fm = None
        for cls, meth in frames:
            m = mod_of_method(meth, ids) or mod_of_class(cls, pkg2mod)
            if m:
                if fm is None: fm = m
                if m not in seen: seen.append(m)
        first[fm or '(vanilla/forge/jdk)'] += 1
        for m in seen: onpath[m] += 1
        leaf[frames[0][0] + '.' + frames[0][1]] += 1
    for line in run(a.jfr, a.rec):
        if line.startswith('jdk.ExecutionSample'):
            flush(); frames = []; cur_thread = None
        elif 'sampledThread' in line:
            m = re.search(r'sampledThread = "([^"]*)"', line)
            cur_thread = m.group(1) if m else None
        else:
            m = FRAME.match(line)
            if m: frames.append((m.group(1), m.group(2)))
    flush()
    res = {'samples': total,
           'threads': threads.most_common(15),
           'first_mod_frame': [(k, v, round(100.0 * v / max(1, total), 2)) for k, v in first.most_common(40)],
           'on_path': [(k, v, round(100.0 * v / max(1, total), 2)) for k, v in onpath.most_common(40)],
           'top_leaf_frames': leaf.most_common(40)}
    if a.out:
        json.dump(res, open(a.out, 'w'), indent=1)
    print(f"samples: {total}")
    print('threads:', threads.most_common(8))
    print('--- first mod frame (who burns the CPU)')
    for k, v, pct in res['first_mod_frame'][:25]: print(f"{pct:6.2f}%  {v:6d}  {k}")
    print('--- mod anywhere on the stack')
    for k, v, pct in res['on_path'][:25]: print(f"{pct:6.2f}%  {v:6d}  {k}")

if __name__ == '__main__':
    main()
