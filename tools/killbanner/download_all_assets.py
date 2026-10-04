"""Downloads every Kingdom Archives kill banner's art and sounds into the Core's assets, and writes banners.json.

Usage: download_all_assets.py <folder with all_banners.json and all_audios.json scraped from kingdomarchives.com/killbanners>
Then run gen_style_java.py and dedupe_assets.py. Writes only inside this repository.
"""
import json
import sys
import urllib.request
import re
import subprocess
import imageio_ffmpeg
import html
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

REPO_ROOT = Path(__file__).resolve().parents[2]
ASSETS_ROOT = REPO_ROOT / 'TheLadsCore/common/src/main/resources/assets/theladscore'
KILLBANNER_DIR = ASSETS_ROOT / 'killbanner'
SOUNDS_DIR = ASSETS_ROOT / 'sounds/killbanner'
SOUNDS_JSON_PATH = ASSETS_ROOT / 'sounds.json'
BANNERS_JSON_PATH = Path(__file__).resolve().parent / 'banners.json'
STRIPS = {'reaver', 'rogue'}  # drawn from their measured 60 fps strips: only their sounds are used

SCRATCH = Path(sys.argv[1])

with open(SCRATCH / 'all_banners.json', 'r', encoding='utf-8') as f:
    ALL_BANNERS = json.load(f)

with open(SCRATCH / 'all_audios.json', 'r', encoding='utf-8') as f:
    ALL_AUDIOS = json.load(f)

KILLBANNER_DIR.mkdir(parents=True, exist_ok=True)
SOUNDS_DIR.mkdir(parents=True, exist_ok=True)

manifest = {}

def process_skin(bid):
    banner = ALL_BANNERS[bid]
    audios = ALL_AUDIOS.get(bid, [])
    attrs = banner['attrs']
    btype = banner['type']
    raw_name = banner['name']
    # Unescape HTML entities like &#039; in name
    display_name = html.unescape(raw_name).strip()
    
    img_dir = KILLBANNER_DIR / bid
    img_dir.mkdir(parents=True, exist_ok=True)
    
    def dl_img(url, name):
        if bid in STRIPS or not url or not isinstance(url, str) or not url.endswith('.png'):
            return False
        dest = img_dir / name
        if dest.exists() and dest.stat().st_size > 0:
            return True
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            data = urllib.request.urlopen(req, timeout=15).read()
            dest.write_bytes(data)
            return True
        except Exception as e:
            print(f"[{bid}] Failed img {name} ({url}): {e}")
            return False

    has_frame = dl_img(attrs.get('data-frame'), 'frame.png')
    
    circle_url = attrs.get('data-circle')
    has_ring = False
    if circle_url and circle_url.endswith('.png') and not circle_url.endswith('/killbanners'):
        has_ring = dl_img(circle_url, 'ring.png')
        
    has_emblem = dl_img(attrs.get('data-emblem'), 'emblem.png')
    has_pip = dl_img(attrs.get('data-pip'), 'pip.png')
    
    # Check variants
    variant_names = [v[1] for v in banner.get('variants', [])]
    if not variant_names:
        variant_names = ["Default"]
        
    for v in [1, 2, 3]:
        dl_img(attrs.get(f'data-emblem-v{v}'), f'emblem_v{v}.png')
        dl_img(attrs.get(f'data-pip-v{v}'), f'pip_v{v}.png')
        
    # Additional data (BannerSwap / Phaseguard)
    if btype == 'BannerSwap' and 'data-additional-data' in attrs:
        extra = json.loads(attrs['data-additional-data'].replace('&quot;', '"'))
        for k, v in extra.items():
            if isinstance(v, str) and v.endswith('.png'):
                url = v if v.startswith('http') else f"https://kingdomarchives.com/{v.lstrip('/')}"
                dl_img(url, f"k{k}.png")
                
    # Download audio and convert to OGG
    sound_count = 0
    for audio_id, audio_url in audios:
        m = re.search(r'audio-(\d+)', audio_id)
        if not m:
            continue
        k_num = m.group(1)
        ogg_dest = SOUNDS_DIR / f"{bid}-kill-{k_num}.ogg"
        if ogg_dest.exists() and ogg_dest.stat().st_size > 0:
            sound_count += 1
            continue
        try:
            req = urllib.request.Request(audio_url, headers={'User-Agent': 'Mozilla/5.0'})
            mp3_data = urllib.request.urlopen(req, timeout=15).read()
            tmp_mp3 = SOUNDS_DIR / f"tmp_{bid}_{k_num}.mp3"
            tmp_mp3.write_bytes(mp3_data)
            subprocess.run([
                FFMPEG, '-y', '-loglevel', 'error',
                '-i', str(tmp_mp3),
                '-ac', '1', '-ar', '48000',
                '-c:a', 'libvorbis', '-q:a', '4',
                str(ogg_dest)
            ], check=True)
            tmp_mp3.unlink()
            sound_count += 1
        except Exception as e:
            print(f"[{bid}] Failed audio {audio_id}: {e}")

    radius = float(attrs.get('data-radius', '140')) / 2.0
    headshot_x = float(attrs.get('data-headshot-x', '0'))
    headshot_y = float(attrs.get('data-headshot-y', '-15'))

    manifest[bid] = {
        'id': bid,
        'name': display_name,
        'type': btype,
        'radius': radius,
        'headshotX': headshot_x,
        'headshotY': headshot_y,
        'variants': variant_names,
        'hasFrame': has_frame,
        'hasRing': has_ring,
        'hasEmblem': has_emblem,
        'hasPip': has_pip,
        'soundCount': sound_count
    }
    print(f"[{bid}] Finished: {display_name} ({btype}), {sound_count} sounds")

if __name__ == '__main__':
    print("Starting concurrent download of all 91 banners...")
    with ThreadPoolExecutor(max_workers=12) as executor:
        list(executor.map(process_skin, ALL_BANNERS.keys()))
        
    # Write manifest
    with open(BANNERS_JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2)
    print(f"Saved manifest with {len(manifest)} banners to {BANNERS_JSON_PATH}")
    
    # Update sounds.json
    sounds_data = {}
    if SOUNDS_JSON_PATH.exists():
        try:
            with open(SOUNDS_JSON_PATH, 'r', encoding='utf-8') as f:
                sounds_data = json.load(f)
        except Exception:
            pass

    for bid in manifest.keys():
        for k in range(1, 6):  # the client shows at most 5 kills
            ogg = SOUNDS_DIR / f"{bid}-kill-{k}.ogg"
            if ogg.exists():
                event_name = f"{bid}_kill_{k}"
                sounds_data[event_name] = {
                    "category": "player",
                    "sounds": [
                        f"theladscore:killbanner/{bid}-kill-{k}"
                    ]
                }
                
    with open(SOUNDS_JSON_PATH, 'w', encoding='utf-8') as f:
        json.dump(sounds_data, f, indent=2)
    print(f"Updated sounds.json with {len(sounds_data)} total sound events")
