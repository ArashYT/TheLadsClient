#!/usr/bin/env python
"""Apply A/B config variants to a SANDBOX COPY of the Zero SMP game folder (never the real instance).

usage: variants.py <sandbox gameDir> <variant> [<variant> ...]
       variants.py --list

Each variant is a list of (file, key-regex, replacement-line) edits done on the copy; keys that are missing are appended.
The refusal below keeps this script away from the real instance.
"""
import os, re, sys

REAL = os.path.normcase(os.path.join(os.environ.get('APPDATA', ''), '.theladsclient', 'instances', 'zero-smp-1.20.1'))

# (relative file, regex matching the whole line to replace (first match), new line, append-if-missing)
V = {
 'mf_dynres':   [('config/modernfix-mixins.properties', r'^\s*mixin\.perf\.dynamic_resources\s*=.*$', 'mixin.perf.dynamic_resources=true')],
 'mf_max':      [('config/modernfix-mixins.properties', r'^\s*mixin\.perf\.dynamic_resources\s*=.*$', 'mixin.perf.dynamic_resources=true'),
                 ('config/modernfix-mixins.properties', r'^\s*mixin\.perf\.dynamic_entity_renderers\s*=.*$', 'mixin.perf.dynamic_entity_renderers=true'),
                 ('config/modernfix-mixins.properties', r'^\s*mixin\.perf\.clear_mixin_classinfo\s*=.*$', 'mixin.perf.clear_mixin_classinfo=true'),
                 ('config/modernfix-mixins.properties', r'^\s*mixin\.perf\.faster_item_rendering\s*=.*$', 'mixin.perf.faster_item_rendering=true')],
 'grass_lo':    [('config/grassiergrass-client.toml', r'^\s*renderRadius\s*=.*$', '\trenderRadius = 64'),
                 ('config/grassiergrass-client.toml', r'^\s*bladesPerBlock\s*=.*$', '\tbladesPerBlock = 6'),
                 ('config/grassiergrass-client.toml', r'^\s*bladeParticles\s*=.*$', '\tbladeParticles = false'),
                 ('config/grassiergrass-client.toml', r'^\s*denseFlowers\s*=.*$', '\tdenseFlowers = false')],
 'grass_mid':   [('config/grassiergrass-client.toml', r'^\s*renderRadius\s*=.*$', '\trenderRadius = 96')],
 'particles_lo':[('config/particular-common.toml', r'^\s*rainRipples\s*=.*$', '\t\trainRipples = false'),
                 ('config/particular-common.toml', r'^\s*waterDripRipples\s*=.*$', '\t\twaterDripRipples = false'),
                 ('config/immersivewind.json', r'^\s*"enableRainHitParticles"\s*:.*$', '  "enableRainHitParticles": false,'),
                 ('config/immersivewind.json', r'^\s*"enableSplashParticles"\s*:.*$', '  "enableSplashParticles": false,'),
                 ('config/particlerain/config.json', r'^\s*"maxParticleAmount"\s*:.*$', '    "maxParticleAmount": 1000,')],
 'spr_lo':      [('config/sound_physics_remastered/soundphysics.properties', r'^environment_evaluation_ray_count=.*$', 'environment_evaluation_ray_count=16'),
                 ('config/sound_physics_remastered/soundphysics.properties', r'^environment_evaluation_ray_bounces=.*$', 'environment_evaluation_ray_bounces=2'),
                 ('config/sound_physics_remastered/soundphysics.properties', r'^max_occlusion_rays=.*$', 'max_occlusion_rays=8')],
 'emb_auto':    [('config/embeddium-options.json', r'^\s*"chunk_builder_threads"\s*:.*$', '    "chunk_builder_threads": 0,')],
 'emb_1':       [('config/embeddium-options.json', r'^\s*"chunk_builder_threads"\s*:.*$', '    "chunk_builder_threads": 1,')],
 'dh_lo':       [('config/DistantHorizons.toml', r'^\s*maxHorizontalResolution\s*=.*$', '\t\t\t\tmaxHorizontalResolution = "TWO_BLOCKS"')],
 'no_minimap':  [('config/xaero/minimap/profiles/default.cfg', r'^display_minimap\s*=.*$', 'display_minimap = false')],
 'no_radar':    [('config/xaero/minimap/profiles/default.cfg', r'^display_radar\s*=.*$', 'display_radar = false')],
 'bb3':         [('options.txt', r'^biomeBlendRadius:.*$', 'biomeBlendRadius:3')],
 'rd12':        [('options.txt', r'^renderDistance:.*$', 'renderDistance:12'), ('options.txt', r'^simulationDistance:.*$', 'simulationDistance:8')],
 'rd8':         [('options.txt', r'^renderDistance:.*$', 'renderDistance:8'), ('options.txt', r'^simulationDistance:.*$', 'simulationDistance:6')],
}

# inverse / "live flip" variants: used by flipper.py while a game is running (Forge TOML configs are file-watched)
V['grass_base'] = [('config/grassiergrass-client.toml', r'^\s*renderRadius\s*=.*$', '	renderRadius = 160'),
                   ('config/grassiergrass-client.toml', r'^\s*bladesPerBlock\s*=.*$', '	bladesPerBlock = 10'),
                   ('config/grassiergrass-client.toml', r'^\s*bladeParticles\s*=.*$', '	bladeParticles = true'),
                   ('config/grassiergrass-client.toml', r'^\s*denseFlowers\s*=.*$', '	denseFlowers = true')]
V['grass_r64'] = [('config/grassiergrass-client.toml', r'^\s*renderRadius\s*=.*$', '	renderRadius = 64')]
V['grass_r32'] = [('config/grassiergrass-client.toml', r'^\s*renderRadius\s*=.*$', '	renderRadius = 32')]
V['grass_b3'] = [('config/grassiergrass-client.toml', r'^\s*bladesPerBlock\s*=.*$', '	bladesPerBlock = 3')]
V['particles_base'] = [('config/particular-common.toml', r'^\s*rainRipples\s*=.*$', '		rainRipples = true'),
                       ('config/particular-common.toml', r'^\s*waterDripRipples\s*=.*$', '		waterDripRipples = true')]
V['particular_off'] = [('config/particular-common.toml', r'^\s*rainRipples\s*=.*$', '		rainRipples = false'),
                       ('config/particular-common.toml', r'^\s*waterDripRipples\s*=.*$', '		waterDripRipples = false'),
                       ('config/particular-common.toml', r'^\s*fallingLeaves\s*=.*$', '		fallingLeaves = false'),
                       ('config/particular-common.toml', r'^\s*fireflies\s*=.*$', '		fireflies = false'),
                       ('config/particular-common.toml', r'^\s*caveDust\s*=.*$', '		caveDust = false')]
V['particular_on'] = [(f, rx, n.replace('false', 'true')) for f, rx, n in V['particular_off']]

V['all_cfg'] = V['mf_dynres'] + V['grass_lo'] + V['particles_lo'] + V['spr_lo'] + V['emb_auto']

def apply(game, name):
    for rel, rx, new in V[name]:
        p = os.path.join(game, *rel.split('/'))
        if not os.path.exists(p):
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, 'w', encoding='utf8').write('')
        lines = open(p, encoding='utf8').read().split('\n')
        for i, l in enumerate(lines):
            if re.match(rx, l):
                lines[i] = new; break
        else:
            lines.append(new)
        open(p, 'w', encoding='utf8', newline='\n').write('\n'.join(lines))
        print(f'  {name}: {rel}: {new.strip()}')

if __name__ == '__main__':
    if sys.argv[1:2] == ['--list']:
        for k in V: print(k, '->', [(f, n.strip()) for f, _, n in V[k]])
        sys.exit(0)
    game = os.path.abspath(sys.argv[1])
    if os.path.normcase(game).startswith(REAL):
        sys.exit('refusing to edit the real instance: ' + game)
    for v in sys.argv[2:]:
        apply(game, v)
