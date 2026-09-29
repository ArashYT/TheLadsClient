"""Build a pinned, exact-version Fabric dependency manifest from upstream Modrinth metadata.

Run explicitly when updating the pack; the launcher never resolves 'latest' at runtime.
"""
import concurrent.futures, hashlib, io, json, pathlib, sys, urllib.parse, urllib.request, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSIONS = ['1.21.11', '26.2']
PROJECTS = ['fabric-api', 'cloth-config', 'sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'modmenu',
    'dynamic-fps', 'appleskin', 'lambdynamiclights', '3dskinlayers', 'not-enough-animations',
    'betterf3', 'better-stats', 'jei', 'xaeros-minimap', 'xaeros-world-map', 'clumps',
    'raised', 'scalablelux', 'entityculling', 'custom-crosshair-mod', 'capes', 'paper-doll',
    'durability-tooltip', 'autoreconnectrf', 'screenshot-viewer', 'tabtweaks']

def fetch(path):
    request = urllib.request.Request('https://api.modrinth.com/v2/' + path,
        headers={'User-Agent': 'TheLadsClient/1.1 (local client pack resolver)'})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)

def versions(project, game):
    return fetch('project/' + project + '/version?' + urllib.parse.urlencode({
        'game_versions': json.dumps([game]), 'loaders': '["fabric"]', 'include_changelog':'false'}))

def survey(item):
    project, game = item
    try:
        found = [v for v in versions(project, game) if v['version_type'] == 'release']
        return {'project':project, 'game':game, 'version':found[0] if found else None}
    except Exception as error:
        return {'project':project, 'game':game, 'error':str(error)}

def lock(game, rows):
    selected = {r['version']['project_id']:r['version'] for r in rows if r['game'] == game and r.get('version')}
    checked = set()
    while set(selected) - checked:
        project = next(iter(set(selected) - checked))
        v = selected[project]
        checked.add(project)
        for dep in v['dependencies']:
            if dep['dependency_type'] != 'required':
                continue
            if dep.get('project_id') in selected:
                continue
            if dep.get('version_id'):
                dependency = fetch('version/' + dep['version_id'])
            elif dep.get('project_id'):
                candidates = [d for d in versions(dep['project_id'], game) if d['version_type'] == 'release']
                if not candidates: raise RuntimeError(f"No stable dependency: {dep}")
                dependency = candidates[0]
            else:
                raise RuntimeError(f"Unresolved dependency: {dep}")
            if game not in dependency['game_versions'] or 'fabric' not in dependency['loaders']:
                raise RuntimeError(f"Wrong-version dependency: {dependency['name']}")
            selected[dependency['project_id']] = dependency
    cache = ROOT / 'artifacts' / 'mod-audit' / 'downloads' / game
    cache.mkdir(parents=True, exist_ok=True)
    entries = []
    for v in selected.values():
        project = fetch('project/' + v['project_id'])
        f = next((f for f in v['files'] if f['primary']), v['files'][0])
        if urllib.parse.urlparse(f['url']).hostname != 'cdn.modrinth.com':
            raise RuntimeError('Unexpected download host: ' + f['url'])
        path = cache / f['filename']
        if not path.exists() or hashlib.sha512(path.read_bytes()).hexdigest() != f['hashes']['sha512']:
            with urllib.request.urlopen(f['url'], timeout=90) as response: data = response.read()
            if hashlib.sha512(data).hexdigest() != f['hashes']['sha512']: raise RuntimeError('Hash mismatch')
            path.write_bytes(data)
        with zipfile.ZipFile(path) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'))
        entries.append({'projectId':v['project_id'], 'projectSlug':project['slug'],
            'name':project['title'], 'modId':metadata['id'], 'versionId':v['id'],
            'version':v['version_number'], 'fileName':f['filename'], 'url':f['url'],
            'sha512':f['hashes']['sha512'], 'size':f['size'],
            'license':project['license']['id'], 'sourceUrl':project.get('source_url'),
            'projectUrl':'https://modrinth.com/mod/' + project['slug']})
        print(game, metadata['id'], v['version_number'], flush=True)
    target = ROOT / 'TheLadsLauncher' / 'game-mods' / game / 'client-mods.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({'minecraftVersion':game, 'mods':entries}, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        result = list(pool.map(survey, [(p,g) for g in VERSIONS for p in PROJECTS]))
    output = ROOT / 'artifacts' / 'mod-audit'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'upstream-versions.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    for row in result:
        v = row.get('version')
        print(row['game'], row['project'], v['version_number'] if v else row.get('error', 'NO STABLE FABRIC RELEASE'))
    if '--lock' in sys.argv:
        for game in VERSIONS: lock(game, result)
