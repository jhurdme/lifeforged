"""Patch the Still Life FTF-edition datapack (extracted in argv[1]) in place."""
import json, sys, os, copy

ROOT = sys.argv[1]
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

def biome(*b): return {'type': 'minecraft:biome', 'biome_is': list(b)}

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

# 1d. 0.3.6-ftf: river beds by water depth. Still Life picks sea-level river beds (Y 52-62) from one noise with ~500-block
#     features, so FTF's wide rivers ran one material for hundreds of blocks and switched across their full width in a straight
#     line. Now: shallows (<= 1 deep) mud / packed mud, 2 deep mixed, 3+ deep gravel / clay (arid rivers: coarse dirt / packed
#     mud / clay), mixed by the 64-block surface noise. Tropical rivers (almost all mud) are unchanged.
def _water(off): return {'type': 'minecraft:water', 'offset': off, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
def _mix(parts):
    out = [cond(noise('minecraft:surface', lo, 10.0), block('minecraft:' + b)) for lo, b in parts[:-1]]
    out.append(block('minecraft:' + parts[-1][1]))
    return {'type': 'minecraft:sequence', 'sequence': out}
def _by_depth(shallow, mid, deep):
    return {'type': 'minecraft:sequence', 'sequence': [cond(_water(-2), _mix(shallow)), cond(_water(-3), _mix(mid)), _mix(deep)]}
_RIVER_BEDS = {
    'still_life:temperate_river': _by_depth([(-0.2, 'mud'), (None, 'packed_mud')], [(0.25, 'mud'), (-0.25, 'packed_mud'), (None, 'gravel')],
                                            [(-0.55, 'gravel'), (None, 'clay')]),
    'still_life:desert_river': _by_depth([(-0.2, 'coarse_dirt'), (None, 'packed_mud')], [(0.25, 'coarse_dirt'), (-0.25, 'packed_mud'), (None, 'clay')],
                                         [(-0.55, 'packed_mud'), (None, 'clay')]),
}
_beds = 0
def _walk_rivers(o):
    global _beds
    if isinstance(o, dict):
        if o.get('type') == 'minecraft:condition' and o['if_true'].get('type') == 'minecraft:biome':
            key = next((k for k in _RIVER_BEDS if k in o['if_true']['biome_is']), None)
            if key and 'river_sediments' in json.dumps(o['then_run']):
                inner = o['then_run']['then_run']['sequence'][0]
                assert inner['if_true']['type'] == 'minecraft:not' and 'river_sediments' in json.dumps(inner['then_run'])
                inner['then_run'] = copy.deepcopy(_RIVER_BEDS[key])
                _beds += 1
                return
        for v in o.values(): _walk_rivers(v)
    elif isinstance(o, list):
        for v in o: _walk_rivers(v)
_walk_rivers(sr)
assert _beds == 2, _beds
log.append('river beds: by water depth for the temperate and arid river groups')

# 1e. River beds at any height. Still Life paints river beds only where the floor is between Y 52 and Y 63 (a y_above(52)
#     group whose rules are each limited by not(y_above(63))): on Lithosphere every river runs at sea level, on FreeTerraForged
#     rivers run at their own height and every bed above Y 63 stayed bare. The band is now measured from the water above the
#     floor: under water, at most 11 blocks (plus surface depth) below the water surface. Vanilla conditions only.
def _is_y_above(c, y):
    return isinstance(c, dict) and c.get('type') in ('minecraft:y_above', 'y_above') and c.get('anchor') == {'absolute': y}
def _is_band_top(c):
    return isinstance(c, dict) and c.get('type') in ('minecraft:not', 'not') and _is_y_above(c.get('invert'), 63)
_band = {'low': 0, 'high': 0}
def _walk_band(o):
    if isinstance(o, list):
        for v in o: _walk_band(v)
        return
    if not isinstance(o, dict):
        return
    c = o.get('if_true')
    if o.get('type') in ('minecraft:condition', 'condition') and _is_y_above(c, 52):
        run = o.get('then_run', {})
        children = run.get('sequence', [run]) if isinstance(run, dict) else []
        tops = [x for x in children if isinstance(x, dict) and _is_band_top(x.get('if_true'))]
        if tops and len(tops) == len(children):
            o['if_true'] = _water_m(-11, c.get('surface_depth_multiplier', 0))
            _band['low'] += 1
            for x in tops:
                x['if_true'] = {'type': 'minecraft:not', 'invert': _water_m(0, x['if_true']['invert'].get('surface_depth_multiplier', 0))}
                _band['high'] += 1
    for v in o.values(): _walk_band(v)
def _water_m(off, mult): return {'type': 'minecraft:water', 'offset': off, 'surface_depth_multiplier': mult, 'add_stone_depth': False}
_walk_band(sr)
assert _band['low'] >= 1 and _band['high'] >= _band['low'], _band
log.append(f"river beds: band measured from the water surface ({_band['low']} lower, {_band['high']} upper bounds)")
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

for l in log:
    print(l)
