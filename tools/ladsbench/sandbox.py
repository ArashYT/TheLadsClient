"""Builds the Legion FPS-bench sandboxes (1.9.12 perf lane). Run on Arash-PC with `python -I sandbox.py ...`.

  lads   <name> <harness game dir> <cmdline.txt>       the real Lads launcher profile, from a Verification --title run's game folder and
                                                       its captured java command line; the launcher's renderer step is emulated (26.3:
                                                       Vulkan, Iris + dependents suspended; 1.20.1: OpenGL, Iris suspended) and the
                                                       launcher's JVM flags (LaunchService.BuildOptimizedJvmArguments) are added
  mrpack <name> <cmdline.txt> <mrpack> [<mrpack> ...]  a Modrinth bundle set (OneClient target): client jars from the CDN, hash-checked,
                                                       OneClient's JVM flags (OneLauncher arguments.rs: G1 + ParallelRefProc, compact
                                                       headers, jdk.incubator.vector, -Xms512M -Xmx4096M)
  common                                               bench.ps1 + the bench mods to Legion

Legion layout (\\\\192.168.2.22\\C\\LadsQA\\perf1912 = C:\\LadsQA\\perf1912): jdk<N>\\, libs\\, assets\\, sbx\\<name>\\{game, natives,
java.args, java.txt, options.force}, templates\\<mc>\\, bench.ps1, mods\\. Nothing outside the sandbox folders is written.
"""
import hashlib, json, os, re, shutil, subprocess, sys, urllib.parse, urllib.request, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
DST = os.environ.get('LADSBENCH_DST', r'\\192.168.2.22\C\LadsQA\perf1912')   # LADSBENCH_DST/_LDST: a local test root instead of Legion
LDST = os.environ.get('LADSBENCH_LDST', r'C:\LadsQA\perf1912')
RC = ['/NFL', '/NDL', '/NJH', '/NJS', '/NP', '/MT:8', '/R:1', '/W:1']
JDKS = {25: r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot',
        17: r'C:\Users\Arash\Desktop\The Lads Client Dev\.qa-cache\runtime-data\runtime\java-17\jdk-17.0.20.1+1',
        21: r'C:\Program Files\Java\jdk-21.0.12'}
# CmlLib's default JVM arguments (the harness and the launcher both get them); OneClient's launcher does not add them.
CMLLIB = {'-XX:+UnlockExperimentalVMOptions', '-XX:+UseG1GC', '-XX:G1NewSizePercent=20', '-XX:G1ReservePercent=20',
          '-XX:MaxGCPauseMillis=50', '-XX:G1HeapRegionSize=16M'}
WIDTH, HEIGHT, LADS_RAM = '1600', '900', 6144   # Legion: 16 GB -> LauncherSettings.RecommendedRamMb = 6 GB


def robo(a, b, *extra):
    subprocess.run(['robocopy', a, b, '/E', *extra] + RC)


def parse(cmdline):
    toks = re.findall(r'"[^"]*"|\S+', open(cmdline, encoding='utf-8-sig').read())
    toks = [t[1:-1] if t.startswith('"') and t.endswith('"') else t for t in toks]
    java, i_cp = toks[0], toks.index('-cp')
    i_main = next(i for i, t in enumerate(toks) if t.startswith('net.fabricmc.loader.impl.launch'))
    game = toks[i_main + 1:]
    opt = lambda k: game[game.index(k) + 1]
    return dict(java=java, jvm=toks[1:i_cp] + toks[i_cp + 2:i_main], cp=toks[i_cp + 1].split(';'), main=toks[i_main],
                version=opt('--version'), asset_index=opt('--assetIndex'), assets=opt('--assetsDir'), game_dir=opt('--gameDir'))


def java_major(java_exe):
    out = subprocess.run([java_exe, '-version'], capture_output=True, text=True).stderr
    m = re.search(r'version "(\d+)', out)
    return int(m.group(1))


def copy_libs(cp):
    """Every classpath jar to DST\\libs\\<path below 'libraries'> (or below the game folder for version jars); returns Legion paths."""
    out = []
    for p in cp:
        norm = p.replace('/', '\\')
        k = norm.lower().find('\\libraries\\')
        rel = norm[k + len('\\libraries\\'):] if k >= 0 else os.path.join('versions', os.path.basename(norm))
        d = os.path.join(DST, 'libs', rel)
        if not os.path.exists(d) or os.path.getsize(d) != os.path.getsize(norm):
            os.makedirs(os.path.dirname(d), exist_ok=True)
            shutil.copy2(norm, d)
        out.append(os.path.join(LDST, 'libs', rel))
    return out


def copy_assets(assets, index):
    d = os.path.join(DST, 'assets')
    os.makedirs(os.path.join(d, 'indexes'), exist_ok=True)
    shutil.copy2(os.path.join(assets, 'indexes', index + '.json'), os.path.join(d, 'indexes'))
    robo(os.path.join(assets, 'log_configs'), os.path.join(d, 'log_configs'))
    objs = json.load(open(os.path.join(assets, 'indexes', index + '.json')))['objects'].values()
    n = 0
    for o in objs:
        h = o['hash']
        src, dst = os.path.join(assets, 'objects', h[:2], h), os.path.join(d, 'objects', h[:2], h)
        if not os.path.exists(dst):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst); n += 1
    print(f'assets index {index}: {n} new objects')


def copy_jdk(major):
    if not os.path.exists(os.path.join(DST, f'jdk{major}', 'bin', 'java.exe')):
        robo(JDKS[major], os.path.join(DST, f'jdk{major}'))


def fix_path(t, src_game, name):
    natives = os.path.join(src_game, 'versions')
    if natives.lower() in t.lower():   # -Djava.library.path=...\versions\<id>\natives[/java] etc.
        k = t.lower().find('\\natives')
        return t[:t.find('=') + 1] + os.path.join(LDST, 'sbx', name, 'natives') + t[k + len('\\natives'):]
    return t


def write_args(name, info, jvm, cp, extra_game=()):
    s = os.path.join(DST, 'sbx', name)
    game = [info['main'], '--username', 'LadsBench', '--version', info['version'], '--gameDir', os.path.join(LDST, 'sbx', name, 'game'),
            '--assetsDir', os.path.join(LDST, 'assets'), '--assetIndex', info['asset_index'], '--uuid', '5d1b6b0e8a8c4c62a1a0c0ffee001912',
            '--accessToken', '0', '--clientId', 'clientId', '--xuid', 'xuid', '--versionType', 'release', '--width', WIDTH, '--height', HEIGHT,
            *extra_game]
    def q(t):
        t = t.replace('\\', '\\\\')
        return f'"{t}"' if ' ' in t or ';' in t else t
    lines = [q(t) for t in jvm] + ['-cp', q(';'.join(cp))] + [q(t) for t in game]
    open(os.path.join(s, 'java.args'), 'w', newline='\r\n').write('\n'.join(lines) + '\n')


def jar_meta(path):
    try:
        with zipfile.ZipFile(path) as z:
            return json.loads(z.read('fabric.mod.json').decode('utf-8-sig'), strict=False)
    except (KeyError, zipfile.BadZipFile, ValueError):
        return None


def lads(name, src_game, cmdline):
    info = parse(cmdline)
    mc = '26.3' if info['version'].endswith('26.3') else '1.20.1'
    major = java_major(info['java'])
    s = os.path.join(DST, 'sbx', name)
    g = os.path.join(s, 'game')
    for d in (g, os.path.join(s, 'off')):
        if os.path.isdir(d): shutil.rmtree(d)
    os.makedirs(os.path.join(s, 'off'))
    robo(os.path.join(src_game, 'mods'), os.path.join(g, 'mods'))
    defaults = os.path.join(HERE, '..', '..', 'TheLadsLauncher', 'game-mods', mc, 'configureddefaults')
    robo(defaults, os.path.join(g, 'configureddefaults'))
    robo(defaults, g)   # PackDefaults.Apply on a fresh profile
    # the harness's QaDiscord.ForceOff on a fresh profile: Lads Discord RPC and Dynamic FPS off (QA rule), everything else Core defaults
    open(os.path.join(g, 'thelads_config.json'), 'w').write('{"modules": {"DynamicFPS": {"enabled": false}, "DiscordRPC": {"enabled": false}}}\n')
    for f in ('lads_accounts.json', 'lads_profile.json', 'lads_versions.json'):
        if os.path.exists(os.path.join(src_game, f)): shutil.copy2(os.path.join(src_game, f), g)
    # BundledModInstaller/PackDefaults leave ModernFix dynamic_resources off on a launched profile (1.9.8)
    mf = os.path.join(g, 'config', 'modernfix-mixins.properties')
    if os.path.exists(mf):
        open(mf, 'w').write(open(mf).read().replace('mixin.perf.dynamic_resources=true', 'mixin.perf.dynamic_resources=false'))
    # Renderer step (launcher default GraphicsRenderer = Vulkan): Iris and every mod depending on it are suspended
    # (GraphicsRenderer.Dependents: iris, euphoria_patcher, irisflwcompat and everything depending on them, transitively)
    metas = {f: jar_meta(os.path.join(g, 'mods', f)) for f in os.listdir(os.path.join(g, 'mods')) if f.endswith('.jar')}
    ids, changed = {'iris', 'euphoria_patcher', 'irisflwcompat'}, True
    while changed:
        changed = False
        for m in filter(None, metas.values()):
            if m.get('id') not in ids and ids & set(m.get('depends') or {}): ids.add(m['id']); changed = True
    for f, m in metas.items():
        if m and m.get('id') in ids:
            shutil.move(os.path.join(g, 'mods', f), os.path.join(s, 'off', f)); print('suspended', f)
    force = ['preferredGraphicsBackend:"vulkan"'] if mc == '26.3' else []
    open(os.path.join(s, 'options.force'), 'w').write('\n'.join(force) + ('\n' if force else ''))
    robo(os.path.join(src_game, 'versions', info['version'], 'natives'), os.path.join(s, 'natives'))
    jvm = [fix_path(t, src_game, name) for t in info['jvm'] if not t.startswith(('-Dthelads.', '-Xmx', '-Xms'))]
    i = next(k for k, t in enumerate(jvm) if t.startswith('-Dlog4j.configurationFile'))
    jvm = jvm[:i] + lads_flags(major) + jvm[i:]
    jvm = [t.replace(info['assets'], os.path.join(LDST, 'assets')) for t in jvm]
    common(name, info, major, jvm, mc)


def lads_flags(major):
    """LaunchService.BuildOptimizedJvmArguments + the launcher's -Xmx/-Xms (after CmlLib's defaults, as the launcher orders them)."""
    f = ['-XX:+IgnoreUnrecognizedVMOptions', '-XX:+UnlockExperimentalVMOptions', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled',
         '-XX:G1NewSizePercent=20', '-XX:G1ReservePercent=20', '-XX:MaxGCPauseMillis=35', '-XX:G1HeapRegionSize=32M']
    f += ['-XX:+UseCompactObjectHeaders'] if major >= 25 else ['-Xshare:auto'] if major < 17 else []
    return f + [f'-Xmx{LADS_RAM}m', '-Xms512m']


def oc_flags(major):
    """OneLauncher arguments.rs (4 GB default: G1; ZGC only from 8 GB)."""
    f = ['-Xms512M', '-Xmx4096M', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled']
    f += ['-XX:+UseCompactObjectHeaders'] if major >= 24 else []
    return f + (['--add-modules=jdk.incubator.vector'] if major >= 21 else [])


def resolve(mc, out, slugs):
    """A modrinth.index.json mrpack with the newest Fabric <mc> release (else beta) of each slug plus required dependencies."""
    api = lambda u: json.load(urllib.request.urlopen(urllib.request.Request('https://api.modrinth.com/v2/' + u,
        headers={'User-Agent': 'TheLadsClient-perf-bench/1.0'}), timeout=60))
    q = urllib.parse.quote
    files, done, todo = [], set(), list(slugs)
    while todo:
        slug = todo.pop(0)
        vs = api(f'project/{q(slug)}/version?loaders={q(json.dumps(["fabric"]))}&game_versions={q(json.dumps([mc]))}')
        if not vs: print('NO VERSION', slug); continue
        v = next((x for x in vs if x['version_type'] == 'release'), vs[0])
        if v['project_id'] in done: continue
        done.add(v['project_id'])
        f = next(x for x in v['files'] if x['primary']) if any(x['primary'] for x in v['files']) else v['files'][0]
        files.append({'path': 'mods/' + f['filename'], 'hashes': f['hashes'], 'downloads': [f['url']], 'fileSize': f['size']})
        print(slug, v['version_number'])
        todo += [d['project_id'] for d in v['dependencies'] if d['dependency_type'] == 'required' and d.get('project_id') and d['project_id'] not in done]
    with zipfile.ZipFile(out, 'w') as z:
        z.writestr('modrinth.index.json', json.dumps({'formatVersion': 1, 'game': 'minecraft', 'name': os.path.basename(out), 'files': files}))


def mrpack(name, cmdline, packs, flags=oc_flags):
    info = parse(cmdline)
    mc = '26.3' if info['version'].endswith('26.3') else '1.20.1'
    major = java_major(info['java'])
    s = os.path.join(DST, 'sbx', name)
    g = os.path.join(s, 'game')
    if os.path.isdir(g): shutil.rmtree(g)
    os.makedirs(os.path.join(g, 'mods'))
    cache = os.path.join(HERE, 'build', 'mrpack-cache')
    os.makedirs(cache, exist_ok=True)
    seen = set()
    for p in packs:
        with zipfile.ZipFile(p) as z:
            idx = json.loads(z.read('modrinth.index.json'))
            for n in z.namelist():
                if n.startswith(('overrides/', 'client-overrides/')) and not n.endswith('/'):
                    d = os.path.join(g, n.split('/', 1)[1]); os.makedirs(os.path.dirname(d), exist_ok=True)
                    open(d, 'wb').write(z.read(n))
        for f in idx['files']:
            if f.get('env', {}).get('client') == 'unsupported' or not f['path'].startswith('mods/') or f['path'] in seen: continue
            seen.add(f['path'])
            c = os.path.join(cache, f['hashes']['sha1'] + '.jar')
            if not os.path.exists(c):
                data = urllib.request.urlopen(urllib.request.Request(f['downloads'][0], headers={'User-Agent': 'TheLadsClient-perf-bench/1.0'}), timeout=120).read()
                if hashlib.sha512(data).hexdigest() != f['hashes']['sha512']: raise SystemExit('hash mismatch ' + f['path'])
                open(c, 'wb').write(data)
            shutil.copy2(c, os.path.join(g, f['path']))
    print(name, len(seen), 'mods')
    open(os.path.join(s, 'options.force'), 'w').write('')
    robo(os.path.join(info['game_dir'], 'versions', info['version'], 'natives'), os.path.join(s, 'natives'))
    keep = [fix_path(t, info['game_dir'], name) for t in info['jvm']
            if not t.startswith(('-Dthelads.', '-Xmx', '-Xms', '-XX:+Disable')) and t not in CMLLIB]
    i = next(k for k, t in enumerate(keep) if t.startswith('-Dlog4j.configurationFile'))
    jvm = [t.replace(info['assets'], os.path.join(LDST, 'assets')) for t in keep[:i] + flags(major) + keep[i:]]
    common(name, info, major, jvm, mc)


def common(name, info, major, jvm, mc):
    s = os.path.join(DST, 'sbx', name)
    shutil.copy2(os.path.join(HERE, f'mc{mc.replace(".", "_")}', 'build', 'libs', f'ladsbench-mc{mc}-1.0.0.jar'), os.path.join(s, 'game', 'mods'))
    os.makedirs(os.path.join(s, 'game', 'ladsbench'), exist_ok=True)
    copy_jdk(major)
    copy_assets(info['assets'], info['asset_index'])
    write_args(name, info, jvm, copy_libs(info['cp']))
    open(os.path.join(s, 'java.txt'), 'w').write(f'{major}\n{mc}\n')
    print(f'{name}: mc {mc}, java {major}, {len(os.listdir(os.path.join(s, "game", "mods")))} mod jars')


def ship():
    os.makedirs(DST, exist_ok=True)
    shutil.copy2(os.path.join(HERE, 'bench.ps1'), DST)


if __name__ == '__main__':
    a = sys.argv[1:]
    {'lads': lambda: lads(a[1], a[2], a[3]), 'mrpack': lambda: mrpack(a[1], a[2], a[3:]), 'common': ship,
     'resolve': lambda: resolve(a[1], a[2], a[3:]),   # resolve <mc> <out.mrpack> <slug> ...
     'mrpack-lads': lambda: mrpack(a[1], a[2], a[3:], lads_flags)}[a[0]]()
