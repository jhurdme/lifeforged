"""Patch the Still Life FTF-edition datapack (extracted in argv[1]) in place."""
import json, sys, os, copy

ROOT = sys.argv[1]
CARVE = '--no-carve' not in sys.argv  # 0.3.2+: the bridge places the VanillaBackport biomes at runtime
def p(*a): return os.path.join(ROOT, *a)
def load(path): return json.load(open(path))
def save(path, d):
    with open(path, 'w') as f:
        json.dump(d, f, indent=2)

log = []

# ---------------------------------------------------------------- 1. surface rule
ns_path = p('data/minecraft/worldgen/noise_settings/overworld.json')
ns = load(ns_path)
sr = ns['surface_rule']

# 1a. granite/diorite: Still Life paints whole mountain surfaces with them. 0.3.3: they stay as the rock a few blocks down,
#     so they only show where FreeTerraForged's erosion strips slopes to bedrock (cliffs, gullies, bare high rock) - streaks,
#     not pink/white mountains. Diorite (reads as snow from afar) gets half its band; the rest is plain stone.
#     Ores and vanilla granite/diorite blobs are features and are not affected.
def cond(c, then): return {'type': 'minecraft:condition', 'if_true': c, 'then_run': then}
def block(n): return {'type': 'minecraft:block', 'result_state': {'Name': n}}
def noise(name, lo, hi): return {'type': 'minecraft:noise_threshold', 'noise': name, 'min_threshold': lo, 'max_threshold': hi}
TOP3 = {'type': 'minecraft:stone_depth', 'offset': 2, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'}
def below_surface(name): return {'type': 'minecraft:sequence', 'sequence': [cond(TOP3, block('minecraft:stone')), block(name)]}
found = []
def walk(r):
    t = r.get('type', '').replace('minecraft:', '')
    if t == 'sequence':
        for x in r['sequence']: walk(x)
    elif t == 'condition':
        if r['if_true'].get('noise') == 'still_life:intrusion':
            if r['if_true'].get('min_threshold') == 0.3:  # the igneous band (the other two are metamorphic / sedimentary)
                found.append(r)
        else:
            walk(r['then_run'])
walk(sr)
assert len(found) == 1, len(found)
seq = found[0]['then_run']['sequence']
names = [x['then_run'].get('result_state', {}).get('Name') for x in seq]
assert names == ['minecraft:granite', 'minecraft:diorite', 'minecraft:smooth_basalt'], names
assert seq[0]['if_true']['min_threshold'] == 0.2 and seq[1]['if_true']['min_threshold'] == -0.4
found[0]['then_run']['sequence'] = [
    cond(noise('still_life:pluton', 0.2, 10.0), below_surface('minecraft:granite')),
    cond(noise('still_life:pluton', -0.4, -0.1), below_surface('minecraft:diorite')),
    cond(noise('still_life:pluton', -0.1, 0.2), block('minecraft:stone')),
    seq[2],  # smooth basalt, unchanged
]
log.append('surface geology: granite/diorite only 3+ blocks below the surface (exposed by erosion), diorite band halved')

# 1b. VanillaBackport surface for its biomes (VB injects these through NoiseBasedChunkGenerator.buildSurface,
#     which FreeTerraForged's generator does not use). 2D noise version so FTF and the GPU surface path accept it.
def cond(c, then): return {'type': 'minecraft:condition', 'if_true': c, 'then_run': then}
def block(n): return {'type': 'minecraft:block', 'result_state': {'Name': n}}
def noise(name, lo, hi): return {'type': 'minecraft:noise_threshold', 'noise': name, 'min_threshold': lo, 'max_threshold': hi}
def biome(*b): return {'type': 'minecraft:biome', 'biome_is': list(b)}

vb_rules = [
    # Sulfur Caves rock: placed by the bridge (4.3+) as a feature, because FreeTerraForged skips surface rules deep in rock.
    cond(biome('minecraft:dappled_forest'),
         cond({'type': 'minecraft:stone_depth', 'offset': 0, 'add_surface_depth': False, 'secondary_depth_range': 0, 'surface_type': 'floor'},
              cond({'type': 'minecraft:water', 'offset': 0, 'surface_depth_multiplier': 0, 'add_stone_depth': False},
                   cond(noise('minecraft:small_patch', 1.2, 1000.0), block('minecraft:coarse_dirt'))))),
]
assert sr['type'].endswith('sequence')
seq = sr['sequence']
# keep the bedrock floor first
first = json.dumps(seq[0])
assert 'bedrock' in first, first[:200]
sr['sequence'] = [seq[0]] + vb_rules + seq[1:]
log.append('surface: added dappled_forest (coarse dirt) rule')

# 1c. Wetlands at any height. Still Life builds its salt lakes for sea level (Lithosphere drops wetland climate to sea level):
#     the salt crust only at Y 62-64 and the lake bed at Y 40-61. On FreeTerraForged terrain they sit anywhere, so the crust
#     goes on dry ground at any height (mostly white calcite, dithered with clay, as Still Life's Y 62 band) and clay under water.
#     Aspen swamps get some mud on dry ground (the bridge adds pools of water to them and the other wetlands).
WATER0 = {'type': 'minecraft:water', 'offset': 0, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
floor_seq = None
for x in sr['sequence']:
    if x.get('type', '').endswith('condition') and x['if_true'].get('type') == 'minecraft:stone_depth' \
            and x['if_true'].get('surface_type') == 'floor' and x['if_true'].get('offset') == 1:
        floor_seq = x['then_run']['sequence']
assert floor_seq is not None
salt = [x for x in floor_seq if x.get('if_true', {}).get('biome_is') == ['still_life:salt_lake']]
assert len(salt) == 1, len(salt)
crust = {'type': 'minecraft:sequence', 'sequence': [
    cond(noise('minecraft:surface_dithered', 0.1, 10.0), block('minecraft:calcite')),
    cond(noise('minecraft:surface_dithered', -0.1, 0.1), block('minecraft:clay')),
    cond(noise('minecraft:surface_dithered', -10.0, -0.1), block('minecraft:calcite')),
]}
salt[0]['then_run'] = {'type': 'minecraft:sequence', 'sequence': [cond(WATER0, crust), block('minecraft:clay')]}
i = floor_seq.index(salt[0])
floor_seq.insert(i + 1, cond(biome('still_life:aspen_swamp'),
                             cond(WATER0, cond(noise('minecraft:surface_dithered', 0.8, 10.0), block('minecraft:mud')))))
log.append('surface: salt_lake crust (calcite/clay) on dry ground and clay lake bed at any height; aspen_swamp mud patches')
save(ns_path, ns)

# ---------------------------------------------------------------- 2. big snow patches also on rock
ds_path = p('data/still_life/worldgen/configured_feature/cf_terrain/disk_snow_mountains.json')
ds = load(ds_path)
disk = ds['config']['feature']['feature']['config']
tgt = disk['target']['blocks']
for b in ['minecraft:stone', 'minecraft:granite', 'minecraft:diorite', 'minecraft:andesite', 'minecraft:tuff',
          'minecraft:calcite', 'minecraft:deepslate', 'minecraft:smooth_basalt']:
    if b not in tgt:
        tgt.append(b)
save(ds_path, ds)
log.append('disk_snow_mountains: targets now ' + ', '.join(x.split(':')[1] for x in tgt))

# ---------------------------------------------------------------- 2b. salt crust rings in lakes at any height
dc_path = p('data/still_life/worldgen/placed_feature/pf_terrain/disk_calcite_salt_lake.json')
dc = load(dc_path)
before_n = len(dc['placement'])
dc['placement'] = [m for m in dc['placement'] if m.get('type') != 'minecraft:height_range']
assert len(dc['placement']) == before_n - 1
save(dc_path, dc)
log.append('disk_calcite_salt_lake: no longer limited to Y 60-62 (still only in shallow water over a mud/clay bed)')

# ---------------------------------------------------------------- 3. VanillaBackport biomes in Still Life's climate table
if not CARVE:
    for l in log: print(l)
    print('dimension: left as Still Life ships it (the bridge adds the VanillaBackport biomes)')
    sys.exit(0)
dim_path = p('data/minecraft/dimension/overworld.json')
dim = load(dim_path)
entries = dim['generator']['biome_source']['biomes']
before = len(entries)

def rng(v): return [v, v] if isinstance(v, (int, float)) else list(v)

def carve(donor, new, dim_key, lo, hi, where=lambda prm: True):
    """Split every `donor` entry along `dim_key`; the part inside [lo, hi] becomes `new`."""
    global entries
    out, n = [], 0
    for e in entries:
        if e['biome'] != donor or not where(e['parameters']):
            out.append(e); continue
        a, b = rng(e['parameters'][dim_key])
        s, t = max(a, lo), min(b, hi)
        if not (t > s):
            out.append(e); continue
        n += 1
        if a < s:
            low = copy.deepcopy(e); low['parameters'][dim_key] = [a, s]; out.append(low)
        mid = copy.deepcopy(e); mid['biome'] = new; mid['parameters'][dim_key] = [s, t]; out.append(mid)
        if b > t:
            high = copy.deepcopy(e); high['parameters'][dim_key] = [t, b]; out.append(high)
    entries = out
    log.append(f'{new}: carved from {n} {donor} entries ({dim_key} {lo}..{hi})')
    return n

def temp_in(lo, hi):
    return lambda prm: rng(prm['temperature'])[0] >= lo - 1e-6 and rng(prm['temperature'])[1] <= hi + 1e-6

# Pale Garden: the most humid part of Still Life's temperate old-growth / mixed-wood forests
assert carve('still_life:old_growth_temperate_forest', 'minecraft:pale_garden', 'humidity', 0.6, 1.0) > 0
assert carve('still_life:mixed_wood_forest', 'minecraft:pale_garden', 'humidity', 0.6, 1.0) > 0
# Dappled Forest: the drier, cool part of temperate forest (Afro-Eurasian) and wooded prairie (American)
assert carve('still_life:temperate_forest', 'minecraft:dappled_forest', 'humidity', -0.275, -0.105, temp_in(-0.35, -0.27)) > 0
assert carve('still_life:wooded_prairie', 'minecraft:dappled_forest', 'humidity', -0.275, -0.105, temp_in(-0.35, -0.27)) > 0
# ... and the same dry band of cool aspen woodlands (American) and inland temperate mixed forest (Afro-Eurasian)
assert carve('still_life:aspen_woodlands', 'minecraft:dappled_forest', 'humidity', -0.275, -0.105) > 0
assert carve('still_life:temperate_mixed_forest', 'minecraft:dappled_forest', 'humidity', -0.275, -0.105, temp_in(-0.41, -0.34)) > 0
# Sulfur Caves: a thin humidity band of Still Life's generic barren caves in every climate (shallow and deep) ...
assert carve('still_life:barren_caves', 'minecraft:sulfur_caves', 'humidity', 0.18, 0.235,
             lambda prm: rng(prm['humidity'])[0] >= 0.179 and rng(prm['humidity'])[1] <= 0.236) > 0
# ... plus a warm band
assert carve('still_life:barren_caves', 'minecraft:sulfur_caves', 'humidity', 0.035, 1.0,
             lambda prm: abs(rng(prm['temperature'])[0] - 0.3) < 1e-3 and abs(rng(prm['temperature'])[1] - 0.345) < 1e-3) > 0
assert carve('still_life:barren_caves', 'minecraft:sulfur_caves', 'humidity', -0.405, 1.0,
             lambda prm: abs(rng(prm['temperature'])[0] - 0.23) < 1e-3 and abs(rng(prm['temperature'])[1] - 0.345) < 1e-3 and rng(prm['depth'])[0] > 0.5) > 0

dim['generator']['biome_source']['biomes'] = entries
save(dim_path, dim)
log.append(f'dimension: {before} -> {len(entries)} parameter points')

for l in log:
    print(l)
