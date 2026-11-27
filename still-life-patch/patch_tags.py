"""Biome tags for the Still Life biomes that ship without them (FTF edition 0.3.9-ftf).

Still Life 0.3 tags its Afro-Eurasian biomes (a dozen tags each: forest, taiga, hot, cold, wet, structures, animal
variants, Towns and Towers villages) but its American set, added later, only has still_life:is_still_life, and so do a
few of its coldest biomes. Untagged biomes get no villages, mineshafts, trial chambers or strongholds, are not in
minecraft:is_overworld, and are invisible to every mod that asks "is this a forest / a cold biome / a swamp".

Each such biome gets the tags of the tagged Still Life biome that is closest to it (ANALOG below), plus a few of its own
(EXTRA). Nothing is removed and no tagged biome changes. Runs on an extracted jar or on a jar; running it twice changes
nothing.

    python3 patch_tags.py folder
    python3 patch_tags.py in.jar out.jar [new-version]     (new-version, e.g. 0.3.9-ftf, replaces the version in the metadata)
"""
import json
import os
import shutil
import sys
import tempfile
import zipfile

# untagged biome -> the tagged biome whose tags it takes
ANALOG = {
    # aspen and prairie
    'aspen_parkland': 'mixed_forest_steppe',
    'aspen_prairie': 'mixed_forest_steppe',
    'aspen_swamp': 'temperate_swamp',
    'aspen_taiga': 'cold_taiga',
    'aspen_woodlands': 'birch_forest',
    'prairie': 'mixed_forest_steppe',
    'lush_prairie': 'mixed_forest_steppe',
    'wooded_prairie': 'mixed_forest_steppe',
    'prairie_lowlands': 'mixed_forest_steppe',
    'mixed_wood_plains': 'mixed_forest_steppe',
    'semiarid_prairie': 'shrub_steppe',
    'prairie_highlands': 'wooded_highlands',
    'flooded_prairie': 'flooded_grasslands',
    'marl_prairie': 'flooded_grasslands',
    'floodplain': 'temperate_marsh',
    # temperate and boreal forests
    'mixed_wood_forest': 'temperate_mixed_forest',
    'coniferous_woodlands': 'pine_woods',
    'pinelands': 'pine_woods',
    'humid_subtropical_woodlands': 'warm_temperate_woodlands',
    'temperate_rainforest': 'temperate_mixed_forest',
    'coastal_temperate_rainforest': 'temperate_mixed_forest',
    'sparse_temperate_rainforest': 'temperate_forest',
    'old_growth_coastal_temperate_rainforest': 'old_growth_temperate_forest',
    'temperate_rainforest_highlands': 'wooded_highlands',
    'temperate_rainforest_wetlands': 'temperate_swamp',
    'spruce_fir_forest': 'evergreen_taiga',
    'taiga_shield': 'cold_taiga',
    'scorched_taiga': 'cold_taiga',
    'boreal_fields': 'cold_taiga_clearings',
    'wooded_boreal_fields': 'cold_taiga_clearings',
    'snowy_taiga': 'cold_taiga',
    'snowy_taiga_clearings': 'cold_taiga_clearings',
    'muskeg': 'bog',
    'snowy_muskeg': 'bog',
    'bayou': 'mediterranean_swamp',
    # mountains
    'montane_meadows': 'alpine_plains',
    'blooming_montane_meadows': 'alpine_heathlands',
    'painted_peaks': 'barren_peaks',
    'snowy_painted_peaks': 'snowy_barren_peaks',
    # dry lands
    'blooming_desert': 'semiarid_desert',
    'cacti_savanna': 'wooded_semiarid_desert',
    'tropical_scrubland': 'wooded_semiarid_desert',
    'yucca_scrubland': 'xeric_shrubland',
    'painted_xeric_shrubland': 'xeric_shrubland',
    'painted_arid_desert': 'arid_desert',
    'painted_basin': 'arid_desert',
    'salt_lake': 'shrub_steppe',
    'dry_warm_shrubland': 'mediterranean_shrubland',
    'warm_shrubland': 'mediterranean_shrubland',
    # tropics
    'tropical_broadleaf_forest': 'tropical_rainforest',
    'tropical_deciduous_rainforest': 'monsoon_forest',
    'old_growth_tropical_deciduous_rainforest': 'old_growth_tropical_rainforest',
    'flooded_tropical_deciduous_rainforest': 'tropical_wetlands',
    'tropical_thorn_forest': 'tropical_dry_forest',
    'tropical_thorn_clearings': 'grassland_savanna',
    # beaches
    'dry_temperate_beach': 'mediterranean_beach',
    'humid_temperate_beach': 'temperate_beach',
    'warm_temperate_beach': 'temperate_beach',
    'subtropical_beach': 'tropical_beach',
    'snowy_beach': 'arctic_beach',
    # caves
    'mossy_caves': 'glowing_caves',
    # Still Life's coldest Afro-Eurasian biomes, which ship with three tags
    'frozen_taiga': 'cold_taiga',
    'frozen_taiga_clearings': 'cold_taiga_clearings',
    'frozen_muskeg': 'bog',
    'frozen_tundra': 'snowy_tundra',
    'arctic_snowy_beach': 'arctic_beach',
}

# tags an analog has that say something about that one biome, not about its kind
# (is_arctic and is_tundra are Still Life's own climate classes: left as Still Life set them)
NOT_COPIED = {'c:is_rare', 'still_life:is_arctic', 'still_life:is_tundra'}

# tags of their own
SNOWY = ['c:is_snowy']
BADLANDS = ['minecraft:is_badlands']
EXTRA = {
    'snowy_taiga': SNOWY,
    'snowy_taiga_clearings': SNOWY,
    'snowy_muskeg': SNOWY,
    'frozen_taiga': SNOWY,
    'frozen_taiga_clearings': SNOWY,
    'frozen_muskeg': SNOWY,
    # the painted biomes are Still Life's badlands: mesa mineshafts, and the Towns and Towers badlands villages
    'painted_arid_desert': BADLANDS + ['towns_and_towers:has_structure/village_badlands_pueblo',
                                       'towns_and_towers:has_structure/pillager_outpost_badlands'],
    'painted_basin': BADLANDS + ['towns_and_towers:has_structure/village_badlands_pueblo',
                                 'towns_and_towers:has_structure/pillager_outpost_badlands'],
    'painted_xeric_shrubland': BADLANDS + ['towns_and_towers:has_structure/village_wooded_badlands_tipi',
                                           'towns_and_towers:has_structure/pillager_outpost_wooded_badlands'],
    'painted_peaks': BADLANDS,
    'snowy_painted_peaks': BADLANDS,
}

TAG_DIR = 'tags/worldgen/biome'


def tag_files(root):
    """{tag id: path} of every biome tag in the pack."""
    out = {}
    data = os.path.join(root, 'data')
    for ns in sorted(os.listdir(data)):
        base = os.path.join(data, ns, *TAG_DIR.split('/'))
        for d, _, files in os.walk(base):
            for name in sorted(files):
                if name.endswith('.json'):
                    rel = os.path.relpath(os.path.join(d, name), base).replace(os.sep, '/')[:-5]
                    out[ns + ':' + rel] = os.path.join(d, name)
    return out


def entry_id(v):
    return v if isinstance(v, str) else v.get('id')


def patch(root):
    files = tag_files(root)
    tags = {}
    for tag, path in files.items():
        with open(path, encoding='utf-8') as f:
            tags[tag] = json.load(f)
    members = {tag: {entry_id(v) for v in j.get('values', [])} for tag, j in tags.items()}
    biome_dir = os.path.join(root, 'data', 'still_life', 'worldgen', 'biome')
    biomes = {n[:-5] for n in os.listdir(biome_dir) if n.endswith('.json')}
    for b, a in ANALOG.items():
        if b not in biomes or a not in biomes:
            sys.exit('patch_tags: this does not look like Still Life 0.3 (biome %s or %s is missing)' % (b, a))
    changed = set()
    added = 0
    for b in sorted(ANALOG):
        want = [t for t in sorted(tags) if 'still_life:' + ANALOG[b] in members[t] and t not in NOT_COPIED]
        for t in EXTRA.get(b, []):
            if t not in tags:
                sys.exit('patch_tags: tag %s is not in this jar' % t)
            if t not in want:
                want.append(t)
        for t in want:
            if 'still_life:' + b not in members[t]:
                tags[t].setdefault('values', []).append('still_life:' + b)
                members[t].add('still_life:' + b)
                changed.add(t)
                added += 1
    for t in sorted(changed):
        with open(files[t], 'w', encoding='utf-8') as f:
            json.dump(tags[t], f, indent=2)
    print('patch_tags: %d biomes, %d tag entries added in %d tag files' % (len(ANALOG), added, len(changed)))
    return added


def patch_jar(src, out, version=None):
    work = tempfile.mkdtemp(prefix='sl-tags-')
    try:
        with zipfile.ZipFile(src) as z:
            z.extractall(work)
        patch(work)
        if version:
            # the version of the edition this jar was (0.3.x-ftf), wherever the metadata says it
            import re
            for rel in ('fabric.mod.json', 'quilt.mod.json', 'META-INF/neoforge.mods.toml'):
                path = os.path.join(work, rel)
                if os.path.exists(path):
                    with open(path, encoding='utf-8') as f:
                        text = f.read()
                    text = re.sub(r'''(["']?version["']?\s*[:=]\s*["'])0\.3(?:\.\d+)?-ftf(["'])''', r'\g<1>' + version + r'\g<2>', text)
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(text)
        if os.path.exists(out):
            os.remove(out)
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
            for d, _, names in os.walk(work):
                for name in sorted(names):
                    full = os.path.join(d, name)
                    z.write(full, os.path.relpath(full, work).replace(os.sep, '/'))
        print('wrote', out)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    target = sys.argv[1]
    if target.endswith('.jar'):
        patch_jar(target, sys.argv[2] if len(sys.argv) > 2 else target[:-4] + '-tags.jar', sys.argv[3] if len(sys.argv) > 3 else None)
    else:
        patch(target)
