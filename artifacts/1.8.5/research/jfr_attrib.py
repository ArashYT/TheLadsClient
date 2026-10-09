#!/usr/bin/env python
"""Attribute JFR CPU samples of a Minecraft/Forge process to mods and to frame sections.

usage: jfr_attrib.py <recording.jfr> [--jfr <jfr binary>] [--mods <mods dir>] [--out summary.json] [--top-threads 8]

Samples = jdk.ExecutionSample (Java code running) + jdk.NativeMethodSample (thread inside a native call: GL driver, LWJGL ...).
Per thread we report
  * first_mod_frame : the innermost frame owned by a mod jar (= who burns the CPU), or '(vanilla/forge/jdk)'
  * on_path         : every mod anywhere on the stack
  * section         : coarse render/tick section by well-known frames (DH, grass, particles, gui, entities, block entities, terrain, tick, ...)
  * top_leaf        : hottest leaf frames
Frames are mapped to a mod by (1) the package -> jar map built from the mods folder, (2) Mixin handler method names that carry the mod id.
"""
import argparse, collections, glob, json, os, re, subprocess, zipfile

EVENTS = 'jdk.ExecutionSample,jdk.NativeMethodSample'
FRAME = re.compile(r'^\s+([A-Za-z0-9_.$/<>-]+)\.([A-Za-z0-9_$<>-]+)\(.*\)\s+line:')

SECTIONS = [  # (name, regex on 'class.method'); first rule that matches ANY frame of the stack wins
    ('DH', r'^com\.seibel\.distanthorizons\.'),
    ('grass', r'^com\.leonardoinc22\.shortgrass\.'),
    ('xaero', r'^xaero\.'),
    ('lads-core', r'^com\.thelads\.'),
    ('particles', r'^net\.minecraft\.client\.particle\.ParticleEngine\.(render|tick)'),
    ('gui', r'^(net\.minecraftforge\.client\.gui\.overlay\.ForgeGui\.render|net\.minecraft\.client\.gui\.Gui\.render|net\.minecraft\.client\.gui\.screens\.)'),
    ('block-entities', r'^net\.minecraft\.client\.renderer\.blockentity\.BlockEntityRenderDispatcher\.'),
    ('entities', r'^net\.minecraft\.client\.renderer\.entity\.EntityRenderDispatcher\.'),
    ('terrain-chunks', r'^me\.jellysquid\.mods\.sodium\.client\.render\.chunk\.'),
    ('chunk-build', r'^me\.jellysquid\.mods\.sodium\.client\.render\.chunk\.compile\.'),
    ('sound', r'^(com\.mojang\.blaze3d\.audio|net\.minecraft\.client\.sounds|com\.sonicether)'),
    ('client-tick', r'^net\.minecraft\.client\.Minecraft\.tick'),
    ('game-renderer', r'^net\.minecraft\.client\.renderer\.GameRenderer\.render'),
    ('server-tick', r'^net\.minecraft\.server\.MinecraftServer\.(tickServer|tickChildren)'),
    ('server-worldgen', r'^net\.minecraft\.(world\.level\.chunk|server\.level\.ChunkMap|world\.level\.levelgen)'),
]
SECTIONS = [(n, re.compile(r)) for n, r in SECTIONS]


def build_maps(mods_dir):
    pkg2mod, ids = {}, {}
    for jar in sorted(glob.glob(os.path.join(mods_dir, '*.jar'))):
        name = os.path.basename(jar)
        short = re.sub(r'[-_ +\[(].*$', '', name)
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        for n in z.namelist():
            if n.endswith('.class') and not n.startswith('META-INF') and '/' in n:
                pkg2mod.setdefault(n.rsplit('/', 1)[0].replace('/', '.'), short)
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
    if '$' not in meth:
        return None
    for tok in meth.split('$'):
        t = tok.lower()
        if len(t) > 3 and t in ids:
            return ids[t]
    return None


def norm_thread(t):
    t = re.sub(r'\d+', 'N', t or '?')
    return t[:60]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('rec'); ap.add_argument('--jfr', default='jfr')
    ap.add_argument('--mods', default=os.path.join(os.environ.get('APPDATA', ''), '.theladsclient', 'instances', 'zero-smp-1.20.1', 'minecraft', 'mods'))
    ap.add_argument('--out'); ap.add_argument('--top-threads', type=int, default=8)
    ap.add_argument('--offset', type=float, default=0.0, help='seconds to ADD to JFR wall-clock times to match the game log (JFR prints standard time, the log uses DST)')
    ap.add_argument('--phases', help='json {name:{begin:secs_of_day,end:secs_of_day}} to bucket the Render/Server thread samples per phase')
    ap.add_argument('--phase-threads', default='main,Server thread')
    a = ap.parse_args()
    pkg2mod, ids = build_maps(a.mods)
    per = collections.defaultdict(lambda: {'samples': 0, 'java': 0, 'native': 0, 'first': collections.Counter(), 'path': collections.Counter(),
                                           'section': collections.Counter(), 'leaf': collections.Counter()})
    cur = None; kind = 'java'; frames = []; cur_t = None
    phases = json.load(open(a.phases)) if a.phases else {}
    pthreads = [x.strip() for x in a.phase_threads.split(',')]
    byphase = collections.defaultdict(lambda: collections.defaultdict(lambda: {'n': 0, 'sec': 0.0, 'first': collections.Counter(), 'section': collections.Counter(), 'path': collections.Counter()}))

    def flush():
        if not frames or cur is None: return
        w = 0.010 if kind == 'java' else 0.020   # seconds of CPU per sample (profile.jfc: Java 10 ms, native 20 ms)
        d = per[norm_thread(cur)]
        d['samples'] += 1; d[kind] += 1; d['sec'] = d.get('sec', 0.0) + w
        first = None; seen = []; sec = None
        for cls, meth in frames:
            m = mod_of_method(meth, ids) or mod_of_class(cls, pkg2mod)
            if m:
                if first is None: first = m
                if m not in seen: seen.append(m)
        full = [c + '.' + mt for c, mt in frames]
        for name, rx in SECTIONS:
            if any(rx.match(f) for f in full): sec = name; break
        d['first'][first or '(vanilla/forge/jdk)'] += w
        for m in seen: d['path'][m] += w
        d['section'][sec or 'other'] += w
        d['leaf'][full[0]] += w
        if phases and cur_t is not None:
            for pn, pv in phases.items():
                if pv['begin'] <= cur_t <= pv['end']:
                    for key in ([cur] if cur in pthreads else []) + ['*ALL*']:
                        b = byphase[pn][key]; b['n'] += 1; b['sec'] = b.get('sec', 0.0) + w; b['first'][first or '(vanilla/forge/jdk)'] += w; b['section'][sec or 'other'] += w
                        for m in seen: b['path'][m] += w

    p = subprocess.Popen([a.jfr, 'print', '--events', EVENTS, '--stack-depth', '96', a.rec], stdout=subprocess.PIPE, text=True, encoding='utf8', errors='replace')
    for line in p.stdout:
        if line.startswith('jdk.ExecutionSample') or line.startswith('jdk.NativeMethodSample'):
            flush(); frames = []; cur = None; kind = 'native' if 'Native' in line else 'java'
        elif line.strip().startswith('startTime ='):
            m = re.search(r'(\d+):(\d+):(\d+\.\d+)', line)
            cur_t = (int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3)) + a.offset) if m else None
        elif 'sampledThread' in line:
            m = re.search(r'sampledThread = "([^"]*)"', line); cur = m.group(1) if m else '?'
        else:
            m = FRAME.match(line)
            if m: frames.append((m.group(1), m.group(2)))
    flush()
    total = sum(d['samples'] for d in per.values())
    out = {'total_samples': total, 'threads': {}}
    for t, d in sorted(per.items(), key=lambda kv: -kv[1]['samples'])[:a.top_threads]:
        n = max(1e-9, d['sec'])
        out['threads'][t] = {'samples': d['samples'], 'java': d['java'], 'native': d['native'], 'cpu_sec': round(d['sec'], 1),
                             'first_mod_frame': [(k, round(v, 2), round(100 * v / n, 1)) for k, v in d['first'].most_common(25)],
                             'on_path': [(k, round(v, 2), round(100 * v / n, 1)) for k, v in d['path'].most_common(25)],
                             'section': [(k, round(v, 2), round(100 * v / n, 1)) for k, v in d['section'].most_common(15)],
                             'top_leaf': [(k, round(v, 2)) for k, v in d['leaf'].most_common(15)]}
    if phases:
        def pct(c, tot, n=12): return [(k, round(v, 2), round(100 * v / max(1e-9, tot), 1)) for k, v in c.most_common(n)]
        out['by_phase'] = {pn: {th: {'samples': b['n'], 'cpu_sec': round(b['sec'], 2), 'section': pct(b['section'], b['sec'], 10), 'first_mod_frame': pct(b['first'], b['sec']), 'on_path': pct(b['path'], b['sec'])} for th, b in ths.items()} for pn, ths in byphase.items()}
    if a.out:
        json.dump(out, open(a.out, 'w'), indent=1)
    print('total samples', total)
    for t, d in out['threads'].items():
        print(f"\n== {t}: {d['samples']} samples (java {d['java']}, native {d['native']})")
        print('  section :', ', '.join(f'{k} {pct}%' for k, _, pct in d['section'][:8]))
        print('  first   :', ', '.join(f'{k} {pct}%' for k, _, pct in d['first_mod_frame'][:8]))
        print('  on path :', ', '.join(f'{k} {pct}%' for k, _, pct in d['on_path'][:10]))


if __name__ == '__main__':
    main()
