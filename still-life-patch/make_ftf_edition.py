"""Turn your own copy of Still Life 0.3 (NeoForge jar from Modrinth) into the Still Life FTF edition 0.3.8-ftf.

usage: python3 make_ftf_edition.py <still_life-0.3 jar> [output jar]

Still Life is All Rights Reserved, so this repository does not contain it or its files; this script edits the jar you
downloaded, on your own computer. It needs only Python 3.8+. What it changes, and why, is in docs/still-life-ftf-edition.md.

Step 1 (the base FTF edition, 0.3-ftf): Still Life is built for Lithosphere's terrain. FreeTerraForged makes the terrain
itself, so the parts of Still Life that shape terrain through Lithosphere are removed and its noise router points back at
Minecraft's own functions (FreeTerraForged replaces them with its own anyway). Its biomes, surface rules, features and
climate table stay (the Deep Dark entry kept below the surface). Step 2 (0.3.1-0.3.8-ftf): patch_sl.py, the fixes made
while testing on FreeTerraForged terrain.
Step 3 (0.3.7-ftf): patch_terrain.py, features that follow FreeTerraForged's slope.
"""
import json, os, shutil, subprocess, sys, tempfile, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))

# Lithosphere / Still Life terrain shaping, which FreeTerraForged replaces
REMOVE = [
    'META-INF/mods.toml',                                     # old Forge metadata
    'data/minecraft/worldgen/density_function/',              # Still Life's overrides of Minecraft's terrain functions
    'data/minecraft/worldgen/noise_settings/overworld_old.json',
    'data/still_life/worldgen/configured_carver/',            # Still Life's carvers (built for Lithosphere's terrain)
    'data/still_life/worldgen/density_function/',             # Still Life's own terrain functions
]

# noise_router entries that referenced Lithosphere/Still Life density functions, with Minecraft 1.21.1's own values
ROUTER = {
 "barrier": {
  "type": "minecraft:noise",
  "noise": "minecraft:aquifer_barrier",
  "xz_scale": 0.5,
  "y_scale": 0.5
 },
 "depth": "minecraft:overworld/depth",
 "final_density": {
  "type": "minecraft:squeeze",
  "argument": {
   "type": "minecraft:mul",
   "argument1": 0.64,
   "argument2": {
    "type": "minecraft:interpolated",
    "argument": {
     "type": "minecraft:blend_density",
     "argument": "minecraft:overworld/sloped_cheese"
    }
   }
  }
 },
 "fluid_level_floodedness": {
  "type": "minecraft:noise",
  "noise": "minecraft:aquifer_fluid_level_floodedness",
  "xz_scale": 1.0,
  "y_scale": 0.67
 },
 "fluid_level_spread": {
  "type": "minecraft:noise",
  "noise": "minecraft:aquifer_fluid_level_spread",
  "xz_scale": 1.0,
  "y_scale": 0.7142857142857143
 },
 "initial_density_without_jaggedness": {
  "type": "minecraft:add",
  "argument1": 0.1171875,
  "argument2": {
   "type": "minecraft:mul",
   "argument1": {
    "type": "minecraft:y_clamped_gradient",
    "from_value": 0.0,
    "from_y": -64,
    "to_value": 1.0,
    "to_y": -40
   },
   "argument2": {
    "type": "minecraft:add",
    "argument1": -0.1171875,
    "argument2": {
     "type": "minecraft:add",
     "argument1": -0.078125,
     "argument2": {
      "type": "minecraft:mul",
      "argument1": {
       "type": "minecraft:y_clamped_gradient",
       "from_value": 1.0,
       "from_y": 496,
       "to_value": 0.0,
       "to_y": 512
      },
      "argument2": {
       "type": "minecraft:add",
       "argument1": 0.078125,
       "argument2": {
        "type": "minecraft:clamp",
        "input": {
         "type": "minecraft:add",
         "argument1": -0.703125,
         "argument2": {
          "type": "minecraft:mul",
          "argument1": 4.0,
          "argument2": {
           "type": "minecraft:quarter_negative",
           "argument": {
            "type": "minecraft:mul",
            "argument1": "minecraft:overworld/depth",
            "argument2": {
             "type": "minecraft:cache_2d",
             "argument": "minecraft:overworld/factor"
            }
           }
          }
         }
        },
        "max": 64.0,
        "min": -64.0
       }
      }
     }
    }
   }
  }
 },
 "temperature": {
  "type": "minecraft:shifted_noise",
  "noise": "minecraft:temperature",
  "xz_scale": 0.25,
  "y_scale": 0.0,
  "shift_x": "minecraft:shift_x",
  "shift_y": 0.0,
  "shift_z": "minecraft:shift_z"
 },
 "vegetation": {
  "type": "minecraft:shifted_noise",
  "noise": "minecraft:vegetation",
  "xz_scale": 0.25,
  "y_scale": 0.0,
  "shift_x": "minecraft:shift_x",
  "shift_y": 0.0,
  "shift_z": "minecraft:shift_z"
 }
}

NEOFORGE_MODS_TOML = "\nmodLoader = 'javafml'\nloaderVersion = '[1,)'\nlicense = 'LicenseRef-All-Rights-Reserved'\nshowAsResourcePack = false\nmods = [\n\t{ modId = 'mr_still_life', version = '0.3.8-ftf', displayName = 'Still Life (FTF edition)', description = 'Still Life replaces Minecraft’s default Overworld with a more realistic alternative! Unofficial FreeTerraForged edition: replaces the normal Still Life jar, needs FreeTerraForged and Lifeforged (formerly Still Life x FreeTerraForged Bridge) 6.0 or later. 0.3.8-ftf: the Dappled Forest ground again (coarse dirt patches), for worlds where Lifeforged places VanillaBackport biomes. 0.3.7-ftf: trees stay off cliffs, the steep-slope and stone-cliff patches only land on steep ground (FreeTerraForged slope, read by the bridge), and river beds follow the water level at any height. 0.3.6-ftf: river beds at sea level follow the water depth (mud and packed mud in the shallows, gravel and clay in the channel) instead of long single-material stretches. 0.3.5-ftf: granite and diorite only show where erosion cuts into the rock (no pink or white mountains), big snow patches also on rock, and salt lakes and aspen swamps that work at any height (white salt crust, clay lake beds, mud) instead of only at sea level. Lifeforged adds pools in wetlands.', logoFile = 'still-life_pack.png', credits = 'Generated by Modrinth', authors = 'Jayzet', displayURL = 'https://modrinth.com/datapack/still-life' },\n]\n\n[[dependencies.mr_still_life]]\nmodId = 'freeterraforged'\ntype = 'required'\nversionRange = '[0,)'\nordering = 'NONE'\nside = 'BOTH'\n\n# pale oak, bush, leaf litter, cactus flower, resin, place_on_ground decorator (1.21.4+ content)\n[[dependencies.mr_still_life]]\nmodId = 'vanillabackport'\ntype = 'required'\nversionRange = '[1.2,)'\nordering = 'NONE'\nside = 'BOTH'\n"


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(src)), 'still_life-0.3.8-ftf-1.21.1.jar')
    work = tempfile.mkdtemp(prefix='sl-ftf-')
    try:
        with zipfile.ZipFile(src) as z:
            names = z.namelist()
            if 'data/minecraft/worldgen/noise_settings/overworld.json' not in names or 'fabric.mod.json' not in names:
                sys.exit('this does not look like the Still Life 0.3 jar')
            z.extractall(work)

        # 1. base FTF edition
        for r in REMOVE:
            path = os.path.join(work, r)
            if os.path.isdir(path):
                shutil.rmtree(path)
            elif os.path.exists(path):
                os.remove(path)
        ns_path = os.path.join(work, 'data/minecraft/worldgen/noise_settings/overworld.json')
        with open(ns_path, encoding='utf-8') as f:
            ns = json.load(f)
        ns['noise_router'].update(ROUTER)
        with open(ns_path, 'w', encoding='utf-8') as f:
            json.dump(ns, f, indent=2)
        # the Deep Dark entry of the climate table that Still Life gives depth 0 (the surface layer): kept below the
        # surface (depth 0.2-1.0), as in every FTF edition since 0.3-ftf. FreeTerraForged copies this table into a new
        # world's level.dat, so it only matters for worlds created with the jar
        dim_path = os.path.join(work, 'data/minecraft/dimension/overworld.json')
        with open(dim_path, encoding='utf-8') as f:
            dim = json.load(f)
        dd = [e for e in dim['generator']['biome_source']['biomes'] if e['biome'] == 'minecraft:deep_dark'
              and e['parameters']['depth'] in (0, [0, 0])]
        if len(dd) != 1:
            sys.exit('this does not look like the Still Life 0.3 jar (deep dark entries changed)')
        dd[0]['parameters']['depth'] = [0.2, 1.0]
        with open(dim_path, 'w', encoding='utf-8') as f:
            json.dump(dim, f, indent=2)
        fab_path = os.path.join(work, 'fabric.mod.json')
        with open(fab_path, encoding='utf-8') as f:
            fab = json.load(f)
        fab['version'] = '0.3.8-ftf'
        fab['name'] = 'Still Life (FTF edition)'
        fab['description'] = fab['description'] + ' Unofficial FreeTerraForged edition.'
        fab.setdefault('depends', {}).update({'freeterraforged': '*', 'vanillabackport': '*'})
        with open(fab_path, 'w', encoding='utf-8') as f:
            json.dump(fab, f, indent=2, ensure_ascii=False)
        with open(os.path.join(work, 'META-INF/neoforge.mods.toml'), 'w', encoding='utf-8') as f:
            f.write(NEOFORGE_MODS_TOML)
        # launchers (Prism) may read the version from the Fabric/Quilt metadata
        quilt_path = os.path.join(work, 'quilt.mod.json')
        if os.path.exists(quilt_path):
            with open(quilt_path, encoding='utf-8') as f:
                q = f.read()
            with open(quilt_path, 'w', encoding='utf-8') as f:
                f.write(q.replace('"version":"0.3"', '"version":"0.3.8-ftf"', 1))

        # 2. the 0.3.1-0.3.8-ftf fixes made while testing on FreeTerraForged terrain
        subprocess.run([sys.executable, os.path.join(HERE, 'patch_sl.py'), work], check=True)
        # 3. 0.3.7-ftf: features that follow FreeTerraForged's slope (the bridge's stilllife_ftf_bridge:terrain filter)
        subprocess.run([sys.executable, os.path.join(HERE, 'patch_terrain.py'), work], check=True)

        # 3. repack
        if os.path.exists(out):
            os.remove(out)
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
            for root, _, files in os.walk(work):
                for name in sorted(files):
                    full = os.path.join(root, name)
                    z.write(full, os.path.relpath(full, work).replace(os.sep, '/'))
        print('wrote', out)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    main()
