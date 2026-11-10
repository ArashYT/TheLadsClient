"""Check and apply upstream updates of the pinned client packs (TheLadsLauncher/game-mods/<game>/client-mods.json).

  check [game...]                        pins with a newer Modrinth build for that game
  apply <game> <modId>[=<versionId>]...  re-pin; the old hash goes to "published" so launchers still know that jar
  install <game> <folder>                copy the pack's default-on jars into a mods folder (in-game check without the launcher)

Unlike sync_instance_mods.py this needs no source instance and never adds or removes a mod. Update in small groups and
start the game after each one: a pin is only proven by a game start.
"""
import concurrent.futures
import hashlib
import io
import json
import pathlib
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKS = ROOT / 'TheLadsLauncher/game-mods'
DOWNLOADS = ROOT / 'artifacts/mod-audit/downloads'  # same cache as sync_instance_mods.py
STABILITY = ['release', 'beta', 'alpha']


def api(path):
    for attempt in range(5):
        request = urllib.request.Request('https://api.modrinth.com/v2/' + path,
                                         headers={'User-Agent': 'TheLadsClient/1.13.9 (client pack update check)'})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            if error.code != 429 and error.code < 500:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError('API unavailable: ' + path)


def loader(game):
    return 'forge' if game == '1.8.9' else 'fabric'


def dump(manifest, raw):
    """The manifest in the file's own style (line ends, escaped or raw non-ASCII), so a re-pin only changes its own lines."""
    text = json.dumps(manifest, indent=2, ensure_ascii=raw.isascii()) + '\n'
    return text.replace('\n', '\r\n').encode('utf8') if b'\r\n' in raw else text.encode('utf8')


def load(game):
    path = PACKS / game / 'client-mods.json'
    raw = path.read_bytes()
    manifest = json.loads(raw)
    if dump(manifest, raw) != raw:
        raise ValueError(str(path) + ' is not in the format this tool writes; a re-pin would rewrite the whole file')
    return path, manifest, raw


def newest(mod, game):
    """(pinned, newer) Modrinth versions: the newest build after the pin that is at least as stable as the pin."""
    if '/' in mod['projectId']:  # a GitHub release (Ionium): only its newest tag is reported, re-pin it by hand
        with urllib.request.urlopen(f"https://api.github.com/repos/{mod['projectId']}/releases?per_page=1", timeout=60) as response:
            tag = json.load(response)[0]
        pinned = {'version_type': 'alpha', 'date_published': ''}
        return pinned, None if tag['tag_name'] == mod['versionId'] else {
            'version_number': tag['tag_name'], 'version_type': 'github', 'date_published': tag['published_at'], 'id': '-', 'dependencies': []}
    listed = api('project/' + mod['projectId'] + '/version?' + urllib.parse.urlencode({
        'game_versions': json.dumps([game]), 'loaders': json.dumps([loader(game)]), 'include_changelog': 'false'})) or []
    pinned = next((v for v in listed if v['id'] == mod['versionId']), None) or api('version/' + mod['versionId'])
    if not pinned:
        return None, None
    allowed = STABILITY[:STABILITY.index(pinned['version_type']) + 1]
    newer = [v for v in listed if v['version_type'] in allowed and v['date_published'] > pinned['date_published']]
    return pinned, max(newer, key=lambda v: v['date_published'], default=None)


def check(games):
    for game in games:
        mods = load(game)[1]['mods']
        projects = {m['projectId']: m['modId'] for m in mods}
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            found = list(pool.map(lambda m: newest(m, game), mods))
        updates = 0
        for mod, (pinned, new) in zip(mods, found):
            if not pinned:
                print(game, mod['modId'], 'PIN NOT ON MODRINTH', mod['versionId'])
            elif new:
                updates += 1
                needs = [projects.get(d.get('project_id'), '?' + str(d.get('project_id'))) + ('=' + d['version_id'] if d.get('version_id') else '')
                         for d in new['dependencies'] if d['dependency_type'] == 'required']
                print(f"{game} {mod['modId']}: {mod['version']} -> {new['version_number']} ({new['version_type']} {new['date_published'][:10]},"
                      f" {new['id']}) needs {', '.join(needs) or '-'}")
        print(f'{game}: {updates} of {len(mods)} pins have a newer build', flush=True)


def fetch(file, game, hosts=('cdn.modrinth.com',)):
    url = urllib.parse.urlparse(file['url'])
    if url.scheme != 'https' or url.hostname not in hosts:
        raise ValueError('Unexpected CDN URL: ' + file['url'])
    path = DOWNLOADS / game / (file['hashes']['sha512'] + '.jar')
    if not path.exists():
        with urllib.request.urlopen(urllib.request.Request(file['url'], headers={'User-Agent': 'TheLadsClient/1.13.9'}), timeout=300) as response:
            data = response.read()
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    data = path.read_bytes()
    if len(data) != file['size'] or hashlib.sha512(data).hexdigest() != file['hashes']['sha512']:
        path.unlink()
        raise ValueError('Download hash/size mismatch: ' + file['filename'])
    return path


def apply(game, specs):
    path, manifest, raw = load(game)
    published = manifest.setdefault('published', {})
    for spec in specs:
        mod_id, _, version_id = spec.partition('=')
        mod = next(m for m in manifest['mods'] if m['modId'] == mod_id)
        version = api('version/' + version_id) if version_id else newest(mod, game)[1]
        if not version:
            print(game, mod_id, 'has no newer build')
            continue
        # The launcher's ModrinthReleaseVerifier refuses a pin that is not this project's build for this game and loader.
        if version['project_id'] != mod['projectId'] or game not in version['game_versions'] or loader(game) not in version['loaders']:
            raise ValueError(f"{version['id']} is not a {loader(game)} build of {mod_id} for {game}")
        file = next((f for f in version['files'] if f['primary']), version['files'][0])
        jar = fetch(file, game)
        if loader(game) == 'fabric':
            with zipfile.ZipFile(io.BytesIO(jar.read_bytes())) as archive:
                jar_id = json.loads(archive.read('fabric.mod.json'), strict=False)['id']
            if jar_id != mod_id:
                raise ValueError(f"{file['filename']} is mod '{jar_id}', not '{mod_id}'")
        old = mod['sha512']
        mod.update(versionId=version['id'], version=version['version_number'], fileName=file['filename'], url=file['url'],
                   sha512=file['hashes']['sha512'], size=file['size'])
        hashes = [h for h in published.get(mod_id, []) + [old] if h != mod['sha512']]
        published[mod_id] = list(dict.fromkeys(hashes))
        print(game, mod_id, '->', mod['version'], mod['versionId'])
    manifest['published'] = {mod_id: hashes for mod_id, hashes in sorted(published.items()) if hashes}
    path.write_bytes(dump(manifest, raw))


def install(game, folder):
    folder = pathlib.Path(folder)
    folder.mkdir(parents=True, exist_ok=True)
    mods = [m for m in load(game)[1]['mods'] if m.get('defaultEnabled', True)]
    for mod in mods:
        target = folder / mod['fileName']
        if not target.exists() or hashlib.sha512(target.read_bytes()).hexdigest() != mod['sha512']:
            shutil.copyfile(fetch({'url': mod['url'], 'size': mod['size'], 'filename': mod['fileName'],
                                   'hashes': {'sha512': mod['sha512']}}, game,
                                  ('cdn.modrinth.com', 'github.com')), target)  # the hosts ClientModInstaller takes
    print(game, 'installed', len(mods), 'jars in', folder)


def main():
    command, args = (sys.argv[1:2] or ['check'])[0], sys.argv[2:]
    if command == 'check':
        check(args or sorted(p.name for p in PACKS.iterdir() if (p / 'client-mods.json').exists()))
    elif command == 'apply' and len(args) >= 2:
        apply(args[0], args[1:])
    elif command == 'install' and len(args) == 2:
        install(args[0], args[1])
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
