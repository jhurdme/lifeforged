"""Step 3 of the Still Life FTF edition (0.3.7-ftf): features that follow FreeTerraForged's terrain.

usage: python3 patch_terrain.py <extracted datapack folder | jar> [output jar]

Uses the bridge's placement filter stilllife_ftf_bridge:terrain (bridge 6.0+: the treeline_* shift fields, talus, faces and
snow fields need it; docs/bridge.md section 15), which reads FreeTerraForged's terrain at each feature's column:
  - trees, fallen trees and tree groups no longer grow on cliffs, 1:1 faces and near-vertical slopes (max_slope, faces), on
    fresh talus or bare rock (avoid_talus), stay out of steep gullies so mountain forests get open avalanche chutes
    (avoid_chutes), thin out on ground that stands out above the land around it: ridges and hilltops (ridge_thinning), and
    on wind-exposed ground (wind_thinning);
  - a ragged treeline: trees thin out towards the snow line (broadleaf trees from 145 to 80 blocks below where the snowy
    peaks begin, conifers from 110 to 50: below where the bridge's mountain snow starts lying in patches), but the line climbs into sheltered gullies and benches and up sunny faces, drops
    on windward ribs and shoulders, and wanders in patches (treeline_shelter / _sun / _wind / _ragged); the bridge's own
    zoning features (treeline clumps, single small spruces, krummholz, subalpine shrubs and ground) carry on above it;
    with the bridge's forest_variety, forests get natural clearings and each tree group grows more or less from region to
    region;
  - features that finish a tree already standing (Still Life's palm crowns, which scan up each column for the trunk's
    marker wood and build the crown around it) carry no filter: with one, the crown was dropped wherever the filter said
    no at its column (steep high slopes, the treeline, thinning) while the trunk stood, leaving bare palm trunks;
  - trees that are only ever placed by another feature (the trees a tree group picks from: their placement has no
    heightmap step) carry no filter: the group's filter decides for them. Before, both had one, so every probability
    (treeline, ridges, facing, clearings) was applied twice;
  - Still Life's "snowy" spruces and bushes (pf_foliage/trees/snowy/*, pf_foliage/bushes/snowy_bush*) put snow on their own
    leaves and, the bushes, snow blocks on the ground around them. A tree group that picks from them grows them only where
    mountain_snow's model holds snow (snow: true); a twin group <name>_plain, with the plain spruces and spruce bushes, is
    added after it to the same biomes and grows where the ground is bare (snow: false, only with mountain_snow on, so
    worlds without it keep the snowy group alone);
  - conifers lean towards north-facing slopes up to 125 blocks below the snow line (fading out over the 30 above, so the
    treeline itself is higher on sunny faces, not held down by it); flowers towards south-facing slopes, ferns and moss towards north-facing
    ones (prefer_facing: on the other side they are skipped part of the time, never removed);
  - grass, fern and flower patches thin out on wind-exposed crests and ridges (wind_thinning, ridge_thinning), so meadows
    are lusher in hollows than on convex ground; shrub patches and shrub groups keep off fresh talus, bare rock and cliffs,
    and end at the snow cap's ragged edge;
  - Still Life's snow layer patches step aside where the bridge sets snow depth (natural_snow_layers): they reach into
    neighbouring chunks after those chunks' snow is done and cut it along chunk borders;
  - Still Life's "steep slope" patches (coarse dirt / packed mud) and "stone cliff" patches only land on steep ground
    (min_slope). On Lithosphere's terrain Still Life could count on them falling on slopes; FreeTerraForged's land is
    mostly gentle, so without this they speckle flat ground too;
  - Still Life's stone_cliff (disks that re-skin snow, gravel, soil and packed mud on cliffs with a neighbouring rock) can no
    longer leave packed mud: with no listed rock next to a block it placed packed mud, and stacked disks grew rectangular
    mud boxes on rock faces. It now copies every natural rock the bridge and FreeTerraForged put on faces (Nature's Spirit
    rocks only where the feature already uses Nature's Spirit, as Still Life Naturalized's does), and a block with no rock
    next to it stays what it was;
  - Still Life's calcite_mountains (calcite disks on rock beside snow blocks) runs only in worlds without the bridge's
    mountain_snow (requires_option "!mountain_snow"): the bridge's snow and rock strata own snowy faces there, and the
    disks wrote into neighbouring chunks, which made two worlds of one seed differ.

It edits every worldgen/placed_feature file it finds, wherever it is in the folder or jar, so it works on the FTF edition
and on add-ons that override Still Life's tree features (for example Still Life Naturalized's FTF jar). It is idempotent:
the filter it writes replaces any terrain filter a placed feature already has, so it can run again on its own output or on
jars patched by an older version. Run on a folder it edits in place; run on a jar it writes a new jar (default:
<name>-terrain.jar).
Slopes are FreeTerraForged's gradient, the same scale as the preset's rock / scree / dirt steepness (defaults 0.8 / 0.6 /
0.45).
"""
import json, os, shutil, sys, tempfile, zipfile

TREE_MAX_SLOPE = 0.65     # between FTF's default scree (0.6) and rock (0.8) steepness: trees thin out on talus, none on cliffs
STEEP_MIN_SLOPE = 0.45    # FTF's default dirt steepness
CLIFF_MIN_SLOPE = 0.6     # FTF's default scree steepness

FILTER = 'stilllife_ftf_bridge:terrain'
TREE_RIDGE_THINNING = 0.4
# the treeline's shift (blocks): up in sheltered ground and on sunny faces, down on windward ribs, plus patchy noise
TREELINE_SHIFT = {'treeline_shelter': 25.0, 'treeline_sun': 30.0, 'treeline_wind': 15.0, 'treeline_ragged': 12.0,
                  'treeline_ragged_size': 28.0}
# conifers lean north lower down (moister, shadier forest), but not near the treeline, which sunny faces carry higher
CONIFER_FACING_MAX_SNOW_LINE = -125.0
BROADLEAF_TREELINE = [-145.0, -80.0]
CONIFER_TREELINE = [-110.0, -50.0]
CONIFERS = ('spruce', 'fir', 'pine', 'larch', 'cedar', 'redwood', 'juniper', 'hemlock', 'snowy', 'taiga', 'boreal', 'conifer')
FLOWERS = ('allium', 'azure_bluet', 'cornflower', 'dandelion', 'lilac', 'oxeye_daisy', 'peonies', 'poppies', 'rose_bush',
           'sunflower', 'tulip', 'wildflower', 'desert_bloom', 'blue_orchid', 'flower', 'lily_of_the_valley')
SHADE = ('fern', 'moss')
GRASS = ('grass_normal', 'grass_common', 'grass_normal_tall', 'grass_common_tall', 'dry_grass')
SHRUBS = ('leaves_bush', 'bushes_', 'berry_bushes')

SNOWY_TREES = 'still_life:pf_foliage/trees/snowy/'
SNOWY_BUSH = 'still_life:pf_foliage/bushes/snowy_bush'
PLAIN_BUSH = 'still_life:cf_foliage/bushes/spruce_bush'


def terrain(**kw):
    return dict({'type': FILTER}, **kw)


def find(placement):
    for i, m in enumerate(placement):
        if isinstance(m, dict) and m.get('type') == FILTER:
            return i
    return -1


def kind_of(m):
    return m.get('type', '').replace('minecraft:', '') if isinstance(m, dict) else ''


def insert(placement, modifier):
    """Adds the filter after the step that picks the column height (heightmap), else before the biome check, else last."""
    for i, m in enumerate(placement):
        if kind_of(m) in ('heightmap', 'surface_relative_threshold_filter'):
            placement.insert(i + 1, modifier)
            return
    for i, m in enumerate(placement):
        if kind_of(m) == 'biome':
            placement.insert(i, modifier)
            return
    placement.append(modifier)


def top_level(placement):
    """True for a feature placed by a biome: its placement picks a column height. Features another feature places (the trees
    a tree group picks from) only check blocks at the position they are given."""
    return any(kind_of(m) in ('heightmap', 'surface_relative_threshold_filter', 'in_square', 'count', 'count_on_every_layer',
                              'noise_based_count', 'noise_threshold_count', 'rarity_filter') for m in placement)


def configured_path(root, feature_id):
    if not isinstance(feature_id, str) or ':' not in feature_id:
        return None
    ns, path = feature_id.split(':', 1)
    for dirpath, dirnames, filenames in os.walk(root):
        if dirpath.replace('\\', '/').endswith(f'data/{ns}/worldgen/configured_feature'):
            f = os.path.join(dirpath, *path.split('/')) + '.json'
            if os.path.exists(f):
                return f
    return None


def configured_text(root, feature_id):
    """The text of the configured feature a placed feature points at (searched in every data folder under root)."""
    if not isinstance(feature_id, str):
        return json.dumps(feature_id)
    f = configured_path(root, feature_id)
    return open(f, encoding='utf-8').read() if f else ''


def is_tree(rel, text):
    rel = rel.replace('\\', '/')
    return '/pf_foliage/trees/' in rel or '/pf_foliage/groups/' in rel and (
        '"minecraft:tree"' in text or 'trees/' in text or '_tree' in text)


def tree_filter(base, relp):
    want = terrain(max_slope=TREE_MAX_SLOPE, avoid_chutes=True, ridge_thinning=TREE_RIDGE_THINNING, wind_thinning=0.25,
                   treeline=BROADLEAF_TREELINE, avoid_talus=['fresh', 'rock'], faces=['open', 'ledge'], **TREELINE_SHIFT)
    conifer = any(c in base for c in CONIFERS)
    if conifer:
        want.update(prefer_facing='north', facing_bias=0.3, facing_max_snow_line=CONIFER_FACING_MAX_SNOW_LINE, treeline=CONIFER_TREELINE)
    # forest_variety: natural clearings (position-based, so a group and its trees agree), and for tree groups a regional
    # mix: each group more or less common from region to region, by a variation of its own
    want.update(clearings=0.1)
    if '/pf_foliage/groups/' in relp:
        want.update(regional_mix=0.4, mix_salt=sum(ord(ch) * (i + 1) for i, ch in enumerate(base)) % 997)
    return want, conifer


def snowy_selector(cfg):
    """True when a configured random_selector picks from Still Life's snowy spruces or bushes."""
    if not isinstance(cfg, dict) or cfg.get('type', '').replace('minecraft:', '') != 'random_selector':
        return False
    c = cfg.get('config', {})
    refs = [e.get('feature') for e in c.get('features', [])] + [c.get('default')]
    return any(isinstance(r, str) and (r.startswith(SNOWY_TREES) or r.startswith(SNOWY_BUSH)) for r in refs)


def plain_ref(root, ref):
    """The plain twin of a snowy tree / bush reference (an id or an inline placed feature)."""
    if not isinstance(ref, str):
        return ref
    if ref.startswith(SNOWY_TREES):
        plain = 'still_life:pf_foliage/trees/' + ref[len(SNOWY_TREES):]
        return plain
    if ref.startswith(SNOWY_BUSH):
        # the snowy bush's own placement (its ground check), with Still Life's plain spruce bush
        f = placed_path(root, ref)
        placement = json.load(open(f, encoding='utf-8')).get('placement', []) if f else []
        placement = [m for m in placement if kind_of(m) != 'rarity_filter' or ref.endswith('snowy_bush')]
        return {'feature': PLAIN_BUSH, 'placement': placement}
    return ref


def placed_path(root, feature_id):
    ns, path = feature_id.split(':', 1)
    for dirpath, dirnames, filenames in os.walk(root):
        if dirpath.replace('\\', '/').endswith(f'data/{ns}/worldgen/placed_feature'):
            f = os.path.join(dirpath, *path.split('/')) + '.json'
            if os.path.exists(f):
                return f
    return None


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)


def snowy_twins(root, counts):
    """Plain twins of the tree groups that pick from snowy trees: <group>_plain, added after the group in every biome."""
    twins = {}
    for dirpath, dirnames, filenames in os.walk(root):
        d = dirpath.replace('\\', '/')
        if not d.endswith('/worldgen/configured_feature') and '/worldgen/configured_feature/' not in d:
            continue
        for name in filenames:
            if not name.endswith('.json') or name.endswith('_plain.json'):
                continue
            path = os.path.join(dirpath, name)
            try:
                cfg = json.load(open(path, encoding='utf-8'))
            except ValueError:
                continue
            if not snowy_selector(cfg):
                continue
            c = cfg['config']
            twin = {'type': cfg['type'], 'config': {
                'features': [dict(e, feature=plain_ref(root, e.get('feature'))) for e in c.get('features', [])],
                'default': plain_ref(root, c.get('default'))}}
            dump(path[:-5] + '_plain.json', twin)
            # data/<ns>/worldgen/configured_feature/<rel> -> id
            dd = d.split('/data/', 1)[1] if '/data/' in d else d.split('data/', 1)[1]
            ns = dd.split('/', 1)[0]
            sub = d.split('/worldgen/configured_feature', 1)[1].strip('/')
            cid = f"{ns}:{(sub + '/' if sub else '')}{name[:-5]}"
            twins[cid] = cid + '_plain'
    # placed features using these groups get a twin too
    placed_twins = {}
    for dirpath, dirnames, filenames in os.walk(root):
        d = dirpath.replace('\\', '/')
        if '/worldgen/placed_feature' not in d:
            continue
        for name in filenames:
            if not name.endswith('.json') or name.endswith('_plain.json'):
                continue
            path = os.path.join(dirpath, name)
            try:
                data = json.load(open(path, encoding='utf-8'))
            except ValueError:
                continue
            if data.get('feature') in twins:
                ns = d.split('data/', 1)[1].split('/', 1)[0]
                sub = d.split('/worldgen/placed_feature', 1)[1].strip('/')
                pid = f"{ns}:{(sub + '/' if sub else '')}{name[:-5]}"
                placed_twins[pid] = (path, pid + '_plain', twins[data['feature']])
    for pid, (path, twin_id, cfg_twin) in placed_twins.items():
        data = json.load(open(path, encoding='utf-8'))
        data['feature'] = cfg_twin
        dump(path[:-5] + '_plain.json', data)
        counts['snowy_groups'] += 1
    # biomes: the twin right after the group
    for dirpath, dirnames, filenames in os.walk(root):
        if not dirpath.replace('\\', '/').endswith('/worldgen/biome') and '/worldgen/biome/' not in dirpath.replace('\\', '/'):
            continue
        for name in filenames:
            if not name.endswith('.json'):
                continue
            path = os.path.join(dirpath, name)
            try:
                b = json.load(open(path, encoding='utf-8'))
            except ValueError:
                continue
            changed = False
            for step in b.get('features', []):
                if not isinstance(step, list):
                    continue
                for pid, (_, twin_id, _) in placed_twins.items():
                    if pid in step and twin_id not in step:
                        step.insert(step.index(pid) + 1, twin_id)
                        changed = True
            if changed:
                dump(path, b)
                counts['snowy_biomes'] += 1
    return set(placed_twins), {t for _, t, _ in placed_twins.values()}


CLIFF_ROCKS = ['granite', 'diorite', 'andesite', 'deepslate', 'tuff', 'basalt', 'smooth_basalt', 'stone', 'calcite', 'dripstone_block',
               'cobblestone', 'mossy_cobblestone', 'cobbled_deepslate', 'gravel']
CLIFF_ROCKS_NS = ['travertine', 'cobbled_travertine', 'mossy_cobbled_travertine', 'kaolin', 'light_gray_kaolin', 'gray_kaolin',
                  'orange_kaolin', 'brown_kaolin', 'white_kaolin', 'red_kaolin', 'yellow_kaolin', 'chert', 'white_chalk']
NEIGHBOURS = [[1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1], [0, -1, 0], [0, -2, 0]]


def simple_state(name):
    return {'type': 'minecraft:simple_state_provider', 'state': {'Name': name}}


def fix_stone_cliff(root, counts):
    """Still Life's stone_cliff disks re-skin snow, gravel and soil on cliffs with the first neighbouring rock on a list; with
    no listed rock next to a block they placed packed mud, and packed mud was itself a target, so stacked disks grew
    rectangular mud boxes on rock faces. The list now has every natural rock the bridge and FreeTerraForged put on faces
    (Nature's Spirit's only in a copy of the feature that already uses Nature's Spirit blocks, so a jar without that mod
    still loads), and a block with no rock next to it stays what it was (a rule per target block, checked last), so the
    fallback is never reached. Packed mud stays a target: mud next to rock becomes that rock, other mud stays mud."""
    for dirpath, dirnames, filenames in os.walk(root):
        if not dirpath.replace('\\', '/').endswith('/worldgen/configured_feature') or 'stone_cliff.json' not in filenames:
            continue
        path = os.path.join(dirpath, 'stone_cliff.json')
        data = json.load(open(path, encoding='utf-8'))
        try:
            disk = data['config']['feature']['feature']['config']
            provider = disk['state_provider']
            target = disk['target']['blocks']
        except (KeyError, TypeError):
            continue
        target = [target] if isinstance(target, str) else list(target)
        ns = 'natures_spirit:' in json.dumps(data)
        rocks = []
        for r in provider.get('rules', []):
            b = r.get('if_true', {}).get('blocks')
            off = r.get('if_true', {}).get('offset', [0, 0, 0])
            if isinstance(b, str) and off != [0, 0, 0] and b not in rocks and 'packed_mud' not in b:
                rocks.append(b)
        for b in ['minecraft:' + r for r in CLIFF_ROCKS] + (['natures_spirit:' + r for r in CLIFF_ROCKS_NS] if ns else []):
            if b not in rocks:
                rocks.append(b)
        rules = [{'if_true': {'type': 'minecraft:matching_blocks', 'blocks': b, 'offset': off}, 'then': simple_state(b)}
                 for b in rocks for off in NEIGHBOURS]
        # no rock next to it: the block stays what it was
        rules += [{'if_true': {'type': 'minecraft:matching_blocks', 'blocks': t, 'offset': [0, 0, 0]}, 'then': simple_state(t)}
                  for t in target]
        new_target = target
        new_provider = {'fallback': simple_state('minecraft:stone'), 'rules': rules}
        if provider == new_provider and target == new_target:
            continue
        disk['state_provider'] = new_provider
        disk['target']['blocks'] = new_target
        dump(path, data)
        counts['stone_cliff_fixed'] += 1


SNOW_DISKS = ('disk_snow_tundra', 'disk_snow_mountains')


def stone_cliff_snowsafe(root, counts):
    """A twin of the (fixed) stone_cliff that leaves snow blocks and powder snow alone, placed right after it in every
    biome; the bridge runs one or the other (requires_option no_snow_curve / !no_snow_curve)."""
    # (biomes overridden by another pack, e.g. Still Life Naturalized's, get the twin too: its placed feature is in the
    # main jar)
    twin_ids = ['still_life:stone_cliff']
    for dirpath, dirnames, filenames in os.walk(root):
        d = dirpath.replace('\\', '/')
        if d.endswith('/worldgen/configured_feature') and 'stone_cliff.json' in filenames:
            data = json.load(open(os.path.join(dirpath, 'stone_cliff.json'), encoding='utf-8'))
            try:
                disk = data['config']['feature']['feature']['config']
            except (KeyError, TypeError):
                continue
            snow = ('minecraft:snow_block', 'minecraft:powder_snow')
            t = disk['target']['blocks']
            disk['target']['blocks'] = [b for b in ([t] if isinstance(t, str) else t) if b not in snow]
            disk['state_provider']['rules'] = [r for r in disk['state_provider'].get('rules', [])
                                               if r.get('if_true', {}).get('blocks') not in snow]
            dump(os.path.join(dirpath, 'stone_cliff_snowsafe.json'), data)
        if d.endswith('/worldgen/placed_feature') and 'stone_cliff.json' in filenames:
            data = json.load(open(os.path.join(dirpath, 'stone_cliff.json'), encoding='utf-8'))
            if not isinstance(data.get('feature'), str):
                continue
            data['feature'] = data['feature'] + '_snowsafe'
            dump(os.path.join(dirpath, 'stone_cliff_snowsafe.json'), data)
            ns = d.split('data/', 1)[1].split('/', 1)[0]
            if ns + ':stone_cliff' not in twin_ids:
                twin_ids.append(ns + ':stone_cliff')
    for dirpath, dirnames, filenames in os.walk(root):
        if not dirpath.replace('\\', '/').endswith('/worldgen/biome') and '/worldgen/biome/' not in dirpath.replace('\\', '/'):
            continue
        for name in filenames:
            if not name.endswith('.json'):
                continue
            path = os.path.join(dirpath, name)
            try:
                b = json.load(open(path, encoding='utf-8'))
            except ValueError:
                continue
            changed = False
            for step in b.get('features', []):
                if not isinstance(step, list):
                    continue
                for pid in twin_ids:
                    if pid in step and pid + '_snowsafe' not in step:
                        step.insert(step.index(pid) + 1, pid + '_snowsafe')
                        changed = True
            if changed:
                dump(path, b)
                counts['stone_cliff_twins'] += 1


MONOLITHS = ('pf_other/ground/monolith', 'pf_other/ground/monolith_small')


def remove_monoliths(root, counts):
    """Still Life's hidden easter egg: two obsidian monolith features (a giant pillar at 20000, 100, 20000 and small ones at
    eight positions around it, fixed_placement, each with a geode-like shell), listed in nearly every biome. They are no
    part of a natural landscape, so the FTF edition drops them: the references in every biome's feature lists (any
    namespace, also biome modifiers), the placed and configured feature files, and the two block tags only they use.
    Idempotent."""
    refs = {'still_life:' + m for m in MONOLITHS}

    def strip(v):
        if isinstance(v, list):
            out = []
            for x in v:
                if isinstance(x, str) and x in refs:
                    counts['monolith_refs'] += 1
                    continue
                out.append(strip(x))
            return out
        return v

    for dirpath, dirnames, filenames in os.walk(root):
        d = dirpath.replace(os.sep, '/')
        for name in filenames:
            if not name.endswith('.json'):
                continue
            path = os.path.join(dirpath, name)
            feature_dir = '/worldgen/placed_feature/pf_other/ground' in d or '/worldgen/configured_feature/cf_other/ground' in d
            if feature_dir and name in ('monolith.json', 'monolith_small.json'):
                os.remove(path)
                counts['monolith_files'] += 1
                continue
            if d.endswith('/tags/block') and name in ('monolith_cannot_replace.json', 'monolith_invalid_blocks.json'):
                os.remove(path)
                counts['monolith_files'] += 1
                continue
            if '/worldgen/biome' not in d and '/biome_modifier' not in d:
                continue
            try:
                text = open(path, encoding='utf-8').read()
            except OSError:
                continue
            if 'monolith' not in text:
                continue
            data = json.loads(text)
            before = counts['monolith_refs']
            if isinstance(data, dict):
                for key in ('features',):
                    if key in data:
                        data[key] = strip(data[key])
            if counts['monolith_refs'] != before:
                dump(path, data)


def patch(root):
    counts = {k: 0 for k in ('stone_cliff_twins', 'snow_disks', 'crowns', 'snow', 'trees', 'conifers', 'inner_trees', 'flowers', 'shade', 'grass', 'shrubs', 'steep_slope',
                             'stone_cliff', 'stone_cliff_fixed', 'snowy_groups', 'snowy_biomes', 'calcite', 'monolith_refs',
                             'monolith_files')}
    remove_monoliths(root, counts)
    fix_stone_cliff(root, counts)
    stone_cliff_snowsafe(root, counts)
    snowy_groups, plain_groups = snowy_twins(root, counts)
    for dirpath, dirnames, filenames in os.walk(root):
        d = dirpath.replace('\\', '/')
        if '/worldgen/placed_feature' not in d:
            continue
        for name in filenames:
            if not name.endswith('.json'):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, root)
            try:
                data = json.load(open(path, encoding='utf-8'))
            except ValueError:
                continue
            placement = data.get('placement')
            if not isinstance(placement, list):
                continue
            base = name[:-5]
            relp = rel.replace(os.sep, '/')
            ns = d.split('data/', 1)[1].split('/', 1)[0] if 'data/' in d else ''
            sub = d.split('/worldgen/placed_feature', 1)[1].strip('/')
            pid = f"{ns}:{(sub + '/' if sub else '')}{base}"
            top = top_level(placement)
            if base.startswith('steep_slope_'):
                want = terrain(min_slope=STEEP_MIN_SLOPE)
                counts['steep_slope'] += 1
            elif base in ('snow_layers', 'snow_layers_no_freeze'):
                # Still Life's own snow depth patches: they reach into neighbouring chunks and rewrite snow there after
                # that chunk's snow was done, which cuts it along chunk borders. The bridge's natural_snow_layers does
                # this job per column, so in its worlds these step aside.
                want = terrain(skip_with_natural_snow=True)
                counts['snow'] += 1
            elif base == 'calcite_mountains':
                # calcite disks on rock next to snow blocks (and packed ice under overhangs): with mountain_snow the
                # bridge's snow model and rock strata own the look of snowy faces, and the disks reach up to 4 blocks into
                # neighbouring chunks, so where they land depended on which chunk finished first (two fresh worlds of one
                # seed differed); only in worlds without mountain_snow
                want = terrain(requires_option='!mountain_snow')
                counts['calcite'] += 1
            elif base == 'stone_cliff':
                # kept with ground_cover too: in generated chunks the fixed disks turn the packed mud Still Life's snowy
                # surface leaves in the risers of stepped snow faces into the rock beside it (exposed packed mud on steep
                # ground: 18.7 per chunk with the disks, 73.8 without, on a snowy face at -17472,-7072). In snow-line
                # curve worlds (bridge 6.0, format 18+) its twin stone_cliff_snowsafe runs instead, which leaves snow
                # alone: the disks reach into neighbouring chunks, and erasing the snow mountain_snow had laid there or not
                # depended on which chunk was generated first (seams along chunk edges)
                want = terrain(min_slope=CLIFF_MIN_SLOPE, requires_option='no_snow_curve')
                counts['stone_cliff'] += 1
            elif base == 'stone_cliff_snowsafe':
                want = terrain(min_slope=CLIFF_MIN_SLOPE, requires_option='!no_snow_curve')
            elif base in SNOW_DISKS:
                # Still Life's snow disks: in snow-line curve worlds only where its own surface is kept (arctic and frozen
                # biomes); elsewhere mountain_snow decides the snow ground, and disks reaching across chunk borders made it
                # depend on generation order
                want = terrain(requires_option='own_surface')
                counts['snow_disks'] += 1
            elif is_tree(rel, configured_text(root, data.get('feature'))) and any(kind_of(m) == 'environment_scan' for m in placement):
                # features that finish a tree already standing (Still Life's palm crowns: a scan up each column for the
                # trunk's marker wood, then the crown is built around it): no filter, or the crown is dropped where the
                # trunk stands (steep high slopes, treeline, thinning) and the palm stays a bare trunk of marker wood
                want = None
                counts['crowns'] += 1
            elif is_tree(rel, configured_text(root, data.get('feature'))):
                if not top:
                    # placed by a tree group at the group's column: the group's filter decides
                    want = None
                    counts['inner_trees'] += 1
                else:
                    want, conifer = tree_filter(base.replace('_plain', ''), relp)
                    counts['conifers'] += conifer
                    counts['trees'] += 1
                    if pid in snowy_groups:
                        # snowy spruces and bushes (snow on their leaves and around them) only in snowfields
                        want.update(snow=True)
                    elif pid in plain_groups:
                        want.update(snow=False, requires_option='mountain_snow')
            elif not top:
                continue
            elif '/pf_foliage/ground/' in relp and any(f in base for f in FLOWERS):
                want = terrain(prefer_facing='south', facing_bias=0.4, wind_thinning=0.3)
                counts['flowers'] += 1
            elif '/pf_foliage/ground/' in relp and any(f in base for f in SHADE):
                want = terrain(prefer_facing='north', facing_bias=0.4, wind_thinning=0.3)
                counts['shade'] += 1
            elif '/pf_foliage/ground/' in relp and any(base.startswith(g) for g in GRASS):
                # meadows sparser on wind-exposed, convex ground
                want = terrain(wind_thinning=0.35, ridge_thinning=0.25)
                counts['grass'] += 1
            elif ('/pf_foliage/ground/' in relp and any(s in base for s in SHRUBS)) or (
                    '/pf_foliage/groups/' in relp and 'shrubs' in base):
                # shrubs: not on fresh talus, bare rock or cliffs, and only up to the snow cap's ragged edge
                want = terrain(max_slope=0.8, avoid_talus=['fresh', 'rock'], faces=['open', 'ledge'], treeline=[-10.0, 4.0],
                               treeline_ragged=10.0, treeline_shelter=15.0)
                counts['shrubs'] += 1
            else:
                continue
            i = find(placement)
            if want is None:
                if i < 0:
                    continue
                del placement[i]
            elif i >= 0:
                if placement[i] == want:
                    continue
                placement[i] = want
            else:
                insert(placement, want)
            dump(path, data)
    print(f"terrain filters: {counts['trees']} tree features (max_slope {TREE_MAX_SLOPE}, ragged treeline, {counts['conifers']} "
          f"conifers lean north), {counts['inner_trees']} trees placed by groups left to the group's filter, "
          f"{counts['crowns']} tree-completing features (palm crowns) left unfiltered, "
          f"{counts['snowy_groups']} snowy tree groups only in snowfields with plain twins in {counts['snowy_biomes']} biomes, "
          f"{counts['flowers']} flower features lean south, {counts['shade']} fern/moss features lean north, "
          f"{counts['grass']} grass features thin on exposed ground, {counts['shrubs']} shrub features off talus and cliffs, "
          f"{counts['steep_slope']} steep slope patches (min_slope {STEEP_MIN_SLOPE}), "
          f"{counts['snow']} snow layer features step aside for the bridge's snow, "
          f"{counts['stone_cliff']} stone cliff patches (min_slope {CLIFF_MIN_SLOPE}), "
          f"{counts['stone_cliff_fixed']} stone cliff features without the packed mud fallback, "
          f"{counts['stone_cliff_twins']} biomes with the snow-safe stone cliff twin, {counts['snow_disks']} snow disks only on own surface in curve worlds, "
          f"{counts['calcite']} calcite mountain disks only without mountain_snow, "
          f"{counts['monolith_refs']} monolith references and {counts['monolith_files']} monolith files removed")
    return counts


def patch_jar(src, out):
    work = tempfile.mkdtemp()
    try:
        with zipfile.ZipFile(src) as z:
            z.extractall(work)
        patch(work)
        if os.path.exists(out):
            os.remove(out)
        with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
            seen = set()
            for info in zin.infolist():
                seen.add(info.filename)
                if info.is_dir():
                    zout.writestr(info, b'')
                    continue
                src_file = os.path.join(work, *info.filename.split('/'))
                if not os.path.exists(src_file):
                    # removed by the patch (the monolith easter egg)
                    continue
                with open(src_file, 'rb') as f:
                    zout.writestr(info, f.read())
            # files the patch added (plain twins of the snowy tree groups)
            for dirpath, dirnames, filenames in os.walk(work):
                for name in filenames:
                    arc = os.path.relpath(os.path.join(dirpath, name), work).replace(os.sep, '/')
                    if arc not in seen:
                        with open(os.path.join(dirpath, name), 'rb') as f:
                            zout.writestr(arc, f.read())
        print('wrote', out)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    target = sys.argv[1]
    if target.endswith('.jar'):
        default = target[:-4] + '-terrain.jar'
        patch_jar(target, sys.argv[2] if len(sys.argv) > 2 else default)
    else:
        patch(target)
