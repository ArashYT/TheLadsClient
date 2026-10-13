import json
import os
import time
import urllib.request

slug_remap = {
    'better-render-distance': 'bobby',
    'modernfix-mvus': 'modernfix',
    'flashback': 'replaymod'
}

with open('TheLadsLauncher/game-mods/26.3/client-mods.json', 'r', encoding='utf-8') as f:
    data26 = json.load(f)

entries = []
print(f"Processing {len(data26['mods'])} mods for 1.20.1...")

for m in data26['mods']:
    slug = m['projectSlug']
    target_slug = slug_remap.get(slug, slug)
    
    # 1. Fetch project info
    p_url = f"https://api.modrinth.com/v2/project/{target_slug}"
    req = urllib.request.Request(p_url, headers={'User-Agent': 'TheLadsClient/1.9.5-dev'})
    with urllib.request.urlopen(req) as resp:
        proj = json.load(resp)
    
    # 2. Fetch versions for 1.20.1 fabric
    v_url = f"https://api.modrinth.com/v2/project/{target_slug}/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22fabric%22%5D"
    req = urllib.request.Request(v_url, headers={'User-Agent': 'TheLadsClient/1.9.5-dev'})
    with urllib.request.urlopen(req) as resp:
        vers = json.load(resp)
    
    if not vers:
        raise ValueError(f"No 1.20.1 fabric version for {target_slug}")
    
    # pick the first release, or the first version
    releases = [v for v in vers if v.get('version_type') == 'release']
    v = releases[0] if releases else vers[0]
    
    primary = next((f for f in v['files'] if f.get('primary')), v['files'][0])
    
    # modId: if it was remapped, check known modId
    mod_id = m['modId']
    if target_slug == 'bobby':
        mod_id = 'bobby'
    elif target_slug == 'modernfix':
        mod_id = 'modernfix'
    elif target_slug == 'replaymod':
        mod_id = 'replaymod'
    
    license_id = proj.get('license', {}).get('id') or m.get('license') or 'Custom'
    source_url = proj.get('source_url') or m.get('sourceUrl')
    
    entry = {
        'projectId': proj['id'],
        'projectSlug': proj['slug'],
        'name': proj['title'],
        'modId': mod_id,
        'versionId': v['id'],
        'version': v['version_number'],
        'fileName': primary['filename'],
        'url': primary['url'],
        'sha512': primary['hashes']['sha512'],
        'size': primary['size'],
        'license': license_id,
        'sourceUrl': source_url,
        'projectUrl': f"https://modrinth.com/mod/{proj['slug']}"
    }
    entries.append(entry)
    print(f"Done {len(entries)}/{len(data26['mods'])}: {entry['name']} ({entry['version']})")
    time.sleep(0.05)

# Sort entries by modId
entries.sort(key=lambda x: x['modId'])

manifest = {
    'minecraftVersion': '1.20.1',
    'resolveThroughApi': True,
    'mods': entries
}

os.makedirs('TheLadsLauncher/game-mods/1.20.1', exist_ok=True)
with open('TheLadsLauncher/game-mods/1.20.1/client-mods.json', 'w', encoding='utf-8') as f:
    json.dump(manifest, f, indent=2)

print('Successfully generated TheLadsLauncher/game-mods/1.20.1/client-mods.json!')
