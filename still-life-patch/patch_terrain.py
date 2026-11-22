"""Step 3 of the Still Life FTF edition (0.3.7-ftf): features that follow FreeTerraForged's terrain.

usage: python3 patch_terrain.py <extracted datapack folder | jar> [output jar]

Uses the bridge's placement filter stilllife_ftf_bridge:terrain (bridge 5.6+), which reads FreeTerraForged's slope at each
feature's column:
  - trees, fallen trees and tree groups no longer grow on cliffs and near-vertical slopes (max_slope);
  - Still Life's "steep slope" patches (coarse dirt / packed mud) and "stone cliff" patches only land on steep ground
    (min_slope). On Lithosphere's terrain Still Life could count on them falling on slopes; FreeTerraForged's land is
    mostly gentle, so without this they speckle flat ground too.

It edits every worldgen/placed_feature file it finds, wherever it is in the folder or jar, so it works on the FTF edition
and on add-ons that override Still Life's tree features (for example Still Life Naturalized's FTF jar).
Run on a folder it edits in place; run on a jar it writes a new jar (default: <name>-terrain.jar).
Slopes are FreeTerraForged's gradient, the same scale as the preset's rock / scree / dirt steepness (defaults 0.8 / 0.6 /
0.45).
"""
import json, os, shutil, sys, tempfile, zipfile

TREE_MAX_SLOPE = 0.7      # between FTF's default scree (0.6) and rock (0.8) steepness: trees thin out on talus, none on cliffs
STEEP_MIN_SLOPE = 0.45    # FTF's default dirt steepness
CLIFF_MIN_SLOPE = 0.6     # FTF's default scree steepness

FILTER = 'stilllife_ftf_bridge:terrain'


def terrain(**kw):
    return dict({'type': FILTER}, **kw)


def has_filter(placement):
    return any(isinstance(m, dict) and m.get('type') == FILTER for m in placement)


def insert(placement, modifier):
    """Adds the filter after the step that picks the column height (heightmap), else before the biome check, else last."""
    for i, m in enumerate(placement):
        if isinstance(m, dict) and m.get('type', '').replace('minecraft:', '') in ('heightmap', 'surface_relative_threshold_filter'):
            placement.insert(i + 1, modifier)
            return
    for i, m in enumerate(placement):
        if isinstance(m, dict) and m.get('type', '').replace('minecraft:', '') == 'biome':
            placement.insert(i, modifier)
            return
    placement.append(modifier)


def configured_text(root, feature_id):
    """The text of the configured feature a placed feature points at (searched in every data folder under root)."""
    if not isinstance(feature_id, str) or ':' not in feature_id:
        return json.dumps(feature_id)
    ns, path = feature_id.split(':', 1)
    for dirpath, dirnames, filenames in os.walk(root):
        if dirpath.replace('\\', '/').endswith(f'data/{ns}/worldgen/configured_feature'):
            f = os.path.join(dirpath, *path.split('/')) + '.json'
            if os.path.exists(f):
                return open(f, encoding='utf-8').read()
    return ''


def is_tree(rel, text):
    rel = rel.replace('\\', '/')
    return '/pf_foliage/trees/' in rel or '/pf_foliage/groups/' in rel and (
        '"minecraft:tree"' in text or 'trees/' in text or '_tree' in text)


def patch(root):
    counts = {'trees': 0, 'steep_slope': 0, 'stone_cliff': 0}
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
            if not isinstance(placement, list) or has_filter(placement):
                continue
            base = name[:-5]
            if base.startswith('steep_slope_'):
                insert(placement, terrain(min_slope=STEEP_MIN_SLOPE))
                counts['steep_slope'] += 1
            elif base == 'stone_cliff':
                insert(placement, terrain(min_slope=CLIFF_MIN_SLOPE))
                counts['stone_cliff'] += 1
            elif is_tree(rel, configured_text(root, data.get('feature'))):
                insert(placement, terrain(max_slope=TREE_MAX_SLOPE))
                counts['trees'] += 1
            else:
                continue
            with open(path, 'w', encoding='utf-8', newline='\n') as f:
                json.dump(data, f, indent=2)
    print(f"terrain filters: {counts['trees']} tree features (max_slope {TREE_MAX_SLOPE}), "
          f"{counts['steep_slope']} steep slope patches (min_slope {STEEP_MIN_SLOPE}), "
          f"{counts['stone_cliff']} stone cliff patches (min_slope {CLIFF_MIN_SLOPE})")
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
            for info in zin.infolist():
                if info.is_dir():
                    zout.writestr(info, b'')
                    continue
                with open(os.path.join(work, *info.filename.split('/')), 'rb') as f:
                    zout.writestr(info, f.read())
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
