"""Inventory a Fabric instance and pin matching Modrinth releases for every client target.

Never copies source-instance jars into a distribution. Missing upstream versions are
recorded in the coverage report. Run with --write after reviewing that report.
"""
import argparse
import concurrent.futures
import hashlib
import io
import json
import pathlib
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
AUDIT = ROOT / 'artifacts/mod-audit'
REFRESH = False
UPDATE = False
GAMES = ['1.21.1', '1.21.11', '26.2', '26.3']
# Only replacements implemented by these version adapters may suppress upstream jars.
NATIVE = {
    '1.21.1': {'threads': 'ThreadPriorityModule', 'classic_minecraft_icon': 'WindowIconMixin', 'autohidehud': 'NativeAutohide'},
    '1.21.11': {'threads': 'ThreadPriorityModule', 'classic_minecraft_icon': 'WindowIconMixin', 'autohidehud': 'NativeAutohide'},
    '26.2': {'threads': 'ThreadPriorityModule', 'appleskin': 'NativeFoodOverlay', 'clumps': 'NativeClumps',
             'dynamic_fps': 'NativeDynamicFps', 'autoreconnectrf': 'NativeReconnect',
             'chatsigninghider': 'ChatIndicatorMixin', 'classic_minecraft_icon': 'WindowIconMixin'},
    '26.3': {'threads': 'ThreadPriorityModule', 'appleskin': 'NativeFoodOverlay', 'clumps': 'NativeClumps',
             'dynamic_fps': 'NativeDynamicFps', 'autoreconnectrf': 'NativeReconnect',
             'chatsigninghider': 'ChatIndicatorMixin', 'classic_minecraft_icon': 'WindowIconMixin'},
}
# 1.4.6: upstream mods Lads Core embeds (com.thelads.core.<adapter>.embedded.*); each copy stands down if its jar is installed.
EMBEDDED = {'entity_texture_features': 'embedded.etf', 'entity_model_features': 'embedded.emf', 'ksyxis': 'embedded.ksyxis',
            'serverpingerfixer': 'embedded.serverpingerfixer', 'fastipping': 'embedded.fastipping',
            'immediatelyfast': 'embedded.immediatelyfast', 'entityculling': 'embedded.entityculling',
            'lazy_ai_pixelindiedev': 'embedded.lazyai', 'quick-pack': 'embedded.quickpack', 'controlling': 'embedded.controlling',
            'searchables': 'embedded.controlling', 'nbtac': 'embedded.nbtac', 'capes': 'embedded.capes',
            'tooltipstxf': 'embedded.tooltips', 'fixbookgui': 'embedded.fixbookgui', 'hoveringhotbar': 'embedded.hoveringhotbar'}
for _game, _cushions, _playtime in [('1.21.1', 'optimizedcushionsbackport', 'worldplaytime'),
                                    ('1.21.11', 'optimizedcushionsbackport', 'worldplaytimereborn'),
                                    ('26.2', 'optimizedcushionsbackport', 'worldplaytimereborn'),
                                    ('26.3', 'optimizedcushions', 'worldplaytimereborn')]:
    NATIVE[_game].update(EMBEDDED, **{_cushions: 'embedded.cushions', _playtime: 'embedded.playtime'})
# Iris 1.10.7 pins Sodium 0.8.7; newer add-ons require Sodium 0.8.14.
# Original projects supply these features on 1.21.1, before the source-instance forks.
COMPATIBLE_PROJECTS = {('1.21.1', 'modernfix'): 'nmDcB62a',
                       ('1.21.1', 'worldplaytimereborn'): 'YkKeggdl',
                       ('1.21.1', 'motionblurplus'): 'fWundlde'}
# Iris's last stable 1.21.1 release requires Sodium 0.6; this beta supports 0.8.
COMPATIBLE_RELEASES = {('1.21.1', 'iris'): 'bAo1Qhte',
                       # 1.2.0's Sodium occlusion mixin breaks on Sodium 0.8.12 (every 1.21.1 world join crashed);
                       # 1.2.1-beta supports it. A 'release' preference would silently pick 1.2.0 again.
                       ('1.21.1', 'betterrenderdistance'): 'ZI7FceDv',
                       # Flashback needs voicechat_api >= 2.6.24, which only ships as 'beta' uploads.
                       ('1.21.1', 'voicechat'): '6PKCVne5', ('1.21.11', 'voicechat'): 'MLNG868g',
                       ('26.2', 'voicechat'): 'Ls232EsW', ('26.3', 'voicechat'): 'OLnMVWXy',
                       ('1.21.1', 'reeses-sodium-options'): '3sJ9XmcU',
                       # 0.9.0 crashes fresh configs (Map.replaceAll on a fastutil 8.5.12 map);
                       # 0.9.2+ require Sodium >=0.8.12, which rejects 0.8.12-beta.1.
                       ('1.21.1', 'sodium-extra'): 'taFAlSOP',
                       # 2.2.0 applies an Amendments mixin even when Amendments is absent.
                       ('1.21.1', 'fixbookgui'): '7S8E0FG3', ('1.21.11', 'reeses-sodium-options'): 'yIgAFMna',
                       ('1.21.11', 'sodium-extra'): 'yqY1efrC'}
KNOWN = {'autoreconnectrf': 'PRy8Khga', 'clientsort': 'K0AkAin6',
         'cloth-config': '9s6osm5g', 'fastershadowmapper': 'nSRLvOHG'}
# Removed from the pack (modId -> Modrinth project), matched by either key. They are never surveyed, preserved or
# shipped; lock() lists them under "retired" with their known hashes so launchers retire managed copies.
# GoodMC left in 1.2.3; it is not a native replacement, so it must not go into NATIVE. 1.4.6 dropped Gamma Utils,
# Motion Blur (Plus) with its Satin library, Sound Physics Remastered and Client Sort.
REMOVED = {'goodmc': 'hwir46QE', 'gammautils': 'wdLuzzEP', 'motionblur': 'fWundlde', 'motionblurplus': 'Qbkde6rq',
           'satin': 'fRbqPLg4', 'sound_physics_remastered': 'qyVF9oeo', 'clientsort': 'K0AkAin6'}


def removed(mod_id, project_id):
    return mod_id in REMOVED or project_id in REMOVED.values()


def api(path, body=None):
    cache = AUDIT / 'api' / (hashlib.sha256((path + json.dumps(body)).encode()).hexdigest() + '.json')
    if not REFRESH and cache.exists() and time.time() - cache.stat().st_mtime < 24 * 60 * 60:
        return json.loads(cache.read_text(encoding='utf8'))
    for attempt in range(4):
        request = urllib.request.Request('https://api.modrinth.com/v2/' + path,
            data=None if body is None else json.dumps(body).encode(),
            headers={'User-Agent': 'TheLadsClient/1.2.1 (client pack inventory)', 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                result = json.load(response)
            cache.parent.mkdir(parents=True, exist_ok=True)
            cache.write_text(json.dumps(result), encoding='utf8')
            return result
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            if error.code != 429 and error.code < 500:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError('API unavailable: ' + path)


def versions(project, game):
    return api('project/' + project + '/version?' + urllib.parse.urlencode({
        'game_versions': json.dumps([game]), 'loaders': '["fabric"]', 'include_changelog': 'false'})) or []


def inventory(instance):
    rows = []
    for path in sorted((instance / 'mods').glob('*.jar')):
        with zipfile.ZipFile(path) as archive:
            metadata = json.loads(archive.read('fabric.mod.json'), strict=False)
        rows.append({'file': path.name, 'sha512': hashlib.sha512(path.read_bytes()).hexdigest(),
                     'id': metadata['id'], 'name': metadata.get('name', metadata['id']),
                     'version': metadata['version'], 'contact': metadata.get('contact', {})})
    matches = api('version_files', {'hashes': [r['sha512'] for r in rows], 'algorithm': 'sha512'})
    for row in rows:
        row['modrinth'] = matches.get(row['sha512'])
        row['project'] = row['modrinth']['project_id'] if row['modrinth'] else KNOWN.get(row['id'])
        if not row['project']:
            # Exact slug lookups only. Search results are not proof of project identity.
            project = api('project/' + row['id'].replace('_', '-'))
            if project:
                row['project'] = project['id']
    return rows


def survey(item):
    row, game = item
    result = {'game': game, 'modId': row['id'], 'name': row['name'], 'projectId': row['project']}
    if row['id'] in NATIVE[game]:
        return result | {'status': 'native', 'implementation': NATIVE[game][row['id']]}
    if not row['project']:
        return result | {'status': 'unavailable', 'reason': 'No verified Modrinth project'}
    equivalent = COMPATIBLE_PROJECTS.get((game, row['id']))
    if equivalent:
        candidates = versions(equivalent, game)
        if not candidates:
            raise ValueError('Compatible original project has no release: ' + equivalent)
        selected = next(iter([v for v in candidates if v['version_type'] == 'release'] or candidates))
        return result | {'status': 'modrinth', 'projectId': equivalent, 'replacement': 'Original project replaces the newer fork', 'selected': selected}
    override = COMPATIBLE_RELEASES.get((game, row['id']))
    if override:
        return result | {'status': 'modrinth', 'selected': api('version/' + override)}
    source = row['modrinth']
    if not UPDATE and game == '26.3' and source and game in source['game_versions'] and 'fabric' in source['loaders']:
        selected = source
    else:
        candidates = versions(row['project'], game)
        stable = [v for v in candidates if v['version_type'] == 'release']
        selected = next(iter(stable or candidates), None)
    if not selected:
        return result | {'status': 'unavailable', 'reason': 'No Fabric release declares compatibility with ' + game}
    return result | {'status': 'modrinth', 'selected': selected}


def entry(version, game):
    project = api('project/' + version['project_id'])
    file = next((f for f in version['files'] if f['primary']), version['files'][0])
    url = urllib.parse.urlparse(file['url'])
    if url.scheme != 'https' or url.hostname != 'cdn.modrinth.com':
        raise ValueError('Unexpected CDN URL')
    path = AUDIT / 'downloads' / game / (file['hashes']['sha512'] + '.jar')
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        with urllib.request.urlopen(file['url'], timeout=120) as response:
            data = response.read()
        if len(data) != file['size'] or hashlib.sha512(data).hexdigest() != file['hashes']['sha512']:
            raise ValueError('Download hash/size mismatch: ' + file['filename'])
        path.write_bytes(data)
    data = path.read_bytes()
    if hashlib.sha512(data).hexdigest() != file['hashes']['sha512']:
        raise ValueError('Corrupt cache: ' + str(path))
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'), strict=False)
    return {'projectId': project['id'], 'projectSlug': project['slug'], 'name': project['title'],
            'modId': metadata['id'], 'versionId': version['id'], 'version': version['version_number'],
            'fileName': file['filename'], 'url': file['url'], 'sha512': file['hashes']['sha512'],
            'size': file['size'], 'license': project['license']['id'], 'sourceUrl': project.get('source_url'),
            'projectUrl': 'https://modrinth.com/mod/' + project['slug']}


def retired(old):
    """Keep the old manifest's retired entries and add removed mods it still shipped, with their hashes."""
    entries = {r['modId']: r for r in old.get('retired', [])}
    for mod in old['mods']:
        if removed(mod['modId'], mod['projectId']):
            item = entries.setdefault(mod['modId'], {'modId': mod['modId'], 'projectId': mod['projectId'], 'name': mod['name'],
                                                     'sha512': [], 'reason': 'Removed from The Lads Client pack.'})
            if mod['sha512'] not in item['sha512']:
                item['sha512'].append(mod['sha512'])
    return sorted(entries.values(), key=lambda r: r['modId'])


def lock(game, rows):
    selected = {r['selected']['project_id']: r['selected'] for r in rows if r['game'] == game and 'selected' in r}
    target = ROOT / 'TheLadsLauncher/game-mods' / game / 'client-mods.json'
    old = json.loads(target.read_text(encoding='utf8')) if target.exists() else {'mods': []}
    # Preserve previous client features outside the instance's inventory.
    for mod in old['mods']:
        if mod['modId'] not in NATIVE[game] and not removed(mod['modId'], mod['projectId']) and mod['projectId'] not in selected:
            version = api('version/' + mod['versionId'])
            if version and game in version['game_versions']:
                selected[mod['projectId']] = version
    checked = set()
    constraints = {}
    while set(selected) - checked:
        project_id = next(iter(set(selected) - checked))
        checked.add(project_id)
        for dep in selected[project_id]['dependencies']:
            if dep['dependency_type'] != 'required':
                continue
            dep_id = dep.get('project_id')
            if dep_id in selected:
                if dep.get('version_id') and selected[dep_id]['id'] != dep['version_id']:
                    if dep_id in constraints and constraints[dep_id] != dep['version_id']:
                        raise ValueError('Conflicting pinned dependency: ' + str(dep))
                    version = api('version/' + dep['version_id'])
                    if not version or game not in version['game_versions'] or 'fabric' not in version['loaders']:
                        raise ValueError('Wrong-version pinned dependency: ' + str(dep))
                    selected[dep_id] = version
                    constraints[dep_id] = version['id']
                    checked.discard(dep_id)
                continue
            if dep.get('version_id'):
                version = api('version/' + dep['version_id'])
            elif dep_id:
                candidates = versions(dep_id, game)
                version = next(iter([v for v in candidates if v['version_type'] == 'release'] or candidates), None)
            else:
                version = None
            if not version or game not in version['game_versions'] or 'fabric' not in version['loaders']:
                raise ValueError('Unavailable required dependency: ' + str(dep))
            selected[version['project_id']] = version
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        entries = list(pool.map(lambda v: entry(v, game), selected.values()))
    if len({e['modId'] for e in entries}) != len(entries):
        raise ValueError('Duplicate Fabric mod IDs for ' + game)
    retire = retired(old)
    # A dependency or a fork can reintroduce a removed mod; fail before writing instead of shipping it again.
    shipped = [e['modId'] for e in entries if removed(e['modId'], e['projectId'])
               or any(e['modId'] == r['modId'] or e['projectId'] == r['projectId'] for r in retire)]
    if shipped:
        raise ValueError('Retired mods would ship again for ' + game + ': ' + ', '.join(shipped)
                         + ' (drop the requiring mod, or remove the id from REMOVED and the manifest\'s retired list)')
    entries.sort(key=lambda e: e['modId'])
    manifest = {'minecraftVersion': game, 'resolveThroughApi': True, 'mods': entries}
    if retire:
        manifest['retired'] = retire
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    for row in rows:
        if row['game'] == game and row['status'] == 'modrinth':
            row['selected'] = selected[row['projectId']]
    print(game, 'pinned', len(entries), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('instance', type=pathlib.Path)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--update', action='store_true', help='Choose latest compatible releases instead of source-instance versions')
    parser.add_argument('--refresh', action='store_true', help='Refresh upstream metadata even when cached')
    args = parser.parse_args()
    global REFRESH, UPDATE
    REFRESH = args.refresh
    UPDATE = args.update
    AUDIT.mkdir(parents=True, exist_ok=True)
    inventory_rows = inventory(args.instance)
    (AUDIT / 'instance-26.3.json').write_text(json.dumps(inventory_rows, indent=2), encoding='utf8')
    # Prefer the identified/newest copy when the input instance contains duplicate mod IDs.
    unique = {}
    for row in inventory_rows:
        if row['id'] not in unique or row['modrinth']:
            unique[row['id']] = row
    # Removed mods get no coverage rows even while the source instance still contains them.
    surveyed = [row for row in unique.values() if not removed(row['id'], row['project'])]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(survey, [(row, game) for game in GAMES for row in surveyed]))
    for game in GAMES:
        print(game, {status: sum(r['game'] == game and r['status'] == status for r in rows) for status in ['native', 'modrinth', 'unavailable']}, flush=True)
        for r in rows:
            if r['game'] == game and r['status'] == 'unavailable':
                print('  MISSING', r['modId'], r['reason'], flush=True)
        if args.write:
            lock(game, rows)
    coverage = [{k: v for k, v in r.items() if k != 'selected'} | ({'versionId': r['selected']['id']} if 'selected' in r else {}) for r in rows]
    destination = ROOT / 'docs/mod-coverage.json'
    destination.write_text(json.dumps({'source': args.instance.name, 'jarCount': len(inventory_rows),
        'uniqueModCount': len(unique), 'duplicates': sorted({r['id'] for r in inventory_rows if sum(x['id'] == r['id'] for x in inventory_rows) > 1}),
        'coverage': coverage}, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
