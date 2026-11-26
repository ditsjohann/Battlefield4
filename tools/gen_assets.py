#!/usr/bin/env python3
"""Generates WARFRONT's JSON assets and data: 3D gun item models (with optic / suppressor
variants selected through item-property overrides), item & block models, blockstates,
sounds.json, the English lang file, damage types, tags, recipes and loot tables.

    python3 tools/gen_assets.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "warfront")
D = os.path.join(ROOT, "data")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


# ------------------------------------------------------------------ swatches (parts atlas, 64x64, 4x4 grid)
SW = {"gunmetal": 0, "steel": 1, "polymer": 2, "tan": 3, "wood": 4, "olive": 5, "bright": 6, "lens": 7,
      "red": 8, "tank_green": 9, "tank_tan": 10, "rubber": 11, "orange": 12, "white": 13, "blue": 14, "redteam": 15}


def uv(sw):
    i = SW[sw]
    u, v = (i % 4) * 4, (i // 4) * 4  # in 16-unit model UV space (64px atlas -> 16 units)
    return [u + 0.5, v + 0.5, u + 3.5, v + 3.5]


def box(frm, to, sw, rot=None, name=None):
    e = {"from": frm, "to": to,
         "faces": {f: {"uv": uv(sw), "texture": "#parts"} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    if name:
        e["name"] = name
    return e


# ------------------------------------------------------------------ gun geometry
# Gun space: x centred on 8, barrel toward -z, sight line around y=12.
def rifle(furn="polymer", metal="gunmetal", barrel_front=-10, hg_front=-4, stock="fixed", mag="curved", grip_col=None):
    g = grip_col or furn
    parts = [
        box([7, 8, 4], [9, 11, 14], metal, name="receiver"),
        box([7.25, 11, 3], [8.75, 11.5, 13.5], "steel", name="rail"),
        box([6.75, 8.25, hg_front], [9.25, 10.75, 4], furn, name="handguard"),
        box([7.5, 9, barrel_front], [8.5, 10, hg_front], metal, name="barrel"),
        box([7.25, 8.75, barrel_front - 2], [8.75, 10.25, barrel_front], "steel", name="muzzle"),
        box([7.25, 4, 11], [8.75, 8, 13], g, rot={"angle": -22.5, "axis": "x", "origin": [8, 8, 12]}, name="grip"),
        box([7.6, 7.2, 10], [8.4, 8, 11.5], "steel", name="trigger_guard"),
        box([7.75, 10.75, hg_front + 0.5], [8.25, 12.4, hg_front + 1], "steel", name="front_sight"),
    ]
    if mag == "curved":
        parts.append(box([7.25, 3.5, 6], [8.75, 8, 9], "polymer" if furn != "wood" else "gunmetal", rot={"angle": 22.5, "axis": "x", "origin": [8, 8, 7.5]}, name="mag"))
    elif mag == "straight":
        parts.append(box([7.25, 4, 6.5], [8.75, 8, 9], "polymer", name="mag"))
    elif mag == "box":
        parts.append(box([6.25, 3.5, 5], [9.75, 8, 10], "olive", name="mag_box"))
        parts.append(box([9.5, 7, 7], [10.25, 9, 9], "steel", name="feed"))
    elif mag == "short":
        parts.append(box([7.25, 5.5, 7], [8.75, 8, 9.5], "polymer", name="mag"))
    if stock == "fixed":
        parts.append(box([7.25, 7, 14], [8.75, 10.5, 21.5], furn, name="stock"))
        parts.append(box([7.1, 6.5, 21.5], [8.9, 10.75, 22.5], "rubber", name="buttpad"))
    elif stock == "collapsible":
        parts.append(box([7.6, 9.25, 14], [8.4, 10, 19], "steel", name="buffer"))
        parts.append(box([7.25, 7.25, 18], [8.75, 10.5, 20.5], furn, name="stock"))
    elif stock == "skeleton":
        parts.append(box([7.4, 9.5, 14], [8.6, 10.5, 22], furn, name="stock_top"))
        parts.append(box([7.4, 6.5, 19.5], [8.6, 10.5, 22], furn, name="stock_back"))
        parts.append(box([7.4, 6.5, 16], [8.6, 7.5, 20], furn, name="stock_bottom"))
    return parts


GUNS = {
    "ar17": dict(parts=rifle(), sight_y=12.4),
    "kv12": dict(parts=rifle(furn="wood", metal="gunmetal", barrel_front=-11, hg_front=-3, mag="curved"), sight_y=12.4),
    "cx9": dict(parts=rifle(furn="tan", barrel_front=-6, hg_front=-2, stock="collapsible", mag="straight", grip_col="polymer"), sight_y=12.4),
    "hmg240": dict(parts=rifle(furn="olive", metal="gunmetal", barrel_front=-14, hg_front=-3, mag="box") + [
        box([6.8, 6, -11], [7.4, 9.2, -10.4], "steel", rot={"angle": 22.5, "axis": "x", "origin": [7, 9, -10.7]}, name="bipod_l"),
        box([8.6, 6, -11], [9.2, 9.2, -10.4], "steel", rot={"angle": 22.5, "axis": "x", "origin": [9, 9, -10.7]}, name="bipod_r"),
        box([7.6, 10, -8], [8.4, 10.6, 1], "gunmetal", name="gas_tube"),
        box([7.0, 11.5, 0], [9.0, 12.2, 3], "polymer", name="carry_handle"),
    ], sight_y=12.4),
    "sr338": dict(parts=rifle(furn="olive", metal="gunmetal", barrel_front=-18, hg_front=-2, stock="skeleton", mag="short") + [
        box([9, 10, 11.5], [10.8, 10.8, 12.3], "bright", name="bolt_handle"),
        box([9.9, 9.6, 11.4], [10.9, 10.6, 12.4], "polymer", name="bolt_knob"),
    ], sight_y=12.4),
    "m7": dict(parts=rifle(furn="tan", metal="gunmetal", barrel_front=-14, hg_front=-4, stock="fixed", mag="straight"), sight_y=12.4),
    "sg12": dict(parts=[
        box([7, 8, 6], [9, 11, 14], "gunmetal", name="receiver"),
        box([7.25, 11, 5.5], [8.75, 11.5, 13.5], "steel", name="rail"),
        box([7.4, 9.3, -12], [8.6, 10.5, 6], "gunmetal", name="barrel"),
        box([7.5, 8, -9], [8.5, 9.2, 6], "steel", name="tube"),
        box([6.8, 7.6, -5], [9.2, 9.6, 1.5], "wood", name="forend"),
        box([7.25, 4, 11], [8.75, 8, 13], "polymer", rot={"angle": -22.5, "axis": "x", "origin": [8, 8, 12]}, name="grip"),
        box([7.25, 7, 14], [8.75, 10.5, 21.5], "wood", name="stock"),
        box([7.1, 6.5, 21.5], [8.9, 10.75, 22.5], "rubber", name="buttpad"),
        box([7.75, 10.5, -11.5], [8.25, 11.6, -11], "bright", name="bead"),
    ], sight_y=11.9),
    "p12": dict(parts=[
        box([7.25, 9, 4], [8.75, 11, 12], "polymer", name="slide"),
        box([7.5, 9.3, 3.5], [8.5, 10.4, 4], "gunmetal", name="muzzle"),
        box([7.3, 8, 5], [8.7, 9, 11.5], "polymer", name="frame"),
        box([7.3, 3.5, 9.5], [8.7, 8.2, 12], "polymer", rot={"angle": -22.5, "axis": "x", "origin": [8, 8, 11]}, name="grip"),
        box([7.75, 11, 4.3], [8.25, 11.6, 4.8], "steel", name="front_sight"),
        box([7.4, 11, 11], [8.6, 11.6, 11.6], "steel", name="rear_sight"),
    ], sight_y=11.4),
}

OPTICS = {
    "iron_sights": [box([7.25, 11.5, 12], [7.6, 12.6, 12.6], "steel"), box([8.4, 11.5, 12], [8.75, 12.6, 12.6], "steel")],
    "red_dot": [box([7.1, 11.5, 7.5], [8.9, 12, 10.5], "polymer"), box([7.1, 12, 9.8], [7.4, 13.6, 10.3], "polymer"),
                box([8.6, 12, 9.8], [8.9, 13.6, 10.3], "polymer"), box([7.1, 13.4, 9.8], [8.9, 13.7, 10.3], "polymer"),
                box([7.85, 12.55, 9.9], [8.15, 12.85, 10.0], "red")],
    "holo": [box([6.8, 11.5, 6], [9.2, 12.1, 10.5], "polymer"), box([6.8, 12.1, 6.2], [7.2, 14, 7], "polymer"), box([8.8, 12.1, 6.2], [9.2, 14, 7], "polymer"),
             box([6.8, 13.8, 6.2], [9.2, 14.2, 7], "polymer"), box([7.2, 12.1, 6.5], [8.8, 13.8, 6.6], "lens"),
             box([6.8, 12.1, 9.4], [9.2, 12.8, 10.5], "polymer")],
    "prism_4x": [box([7, 11.5, 6], [9, 12.2, 11], "polymer"), box([7.1, 12.2, 5], [8.9, 14, 11.5], "gunmetal"),
                 box([7.3, 12.4, 4.8], [8.7, 13.8, 5.0], "lens"), box([7.6, 14, 7.5], [8.4, 14.5, 8.5], "steel")],
    "sniper_8x": [box([7.4, 11.5, 4.5], [8.6, 12.3, 5.5], "steel"), box([7.4, 11.5, 10.5], [8.6, 12.3, 11.5], "steel"),
                  box([7.2, 12.2, 3], [8.8, 13.8, 13], "gunmetal"), box([6.9, 11.9, 0.5], [9.1, 14.1, 3], "gunmetal"),
                  box([7.0, 12.0, 0.3], [9.0, 14.0, 0.5], "lens"), box([7.0, 12.0, 13], [9.0, 14.0, 15], "gunmetal"),
                  box([7.7, 13.8, 7], [8.3, 14.6, 8.2], "steel")],
}
OPTIC_ORDINAL = {"iron_sights": 0, "red_dot": 1, "holo": 2, "prism_4x": 3, "sniper_8x": 4}
GUN_OPTICS = {
    "ar17": ["iron_sights", "red_dot", "holo", "prism_4x"],
    "kv12": ["iron_sights", "red_dot", "holo", "prism_4x"],
    "cx9": ["iron_sights", "red_dot", "holo"],
    "hmg240": ["iron_sights", "red_dot", "holo", "prism_4x"],
    "sr338": ["iron_sights", "prism_4x", "sniper_8x"],
    "m7": ["iron_sights", "red_dot", "holo", "prism_4x", "sniper_8x"],
    "sg12": ["iron_sights", "red_dot"],
    "p12": ["iron_sights", "red_dot"],
}


def suppressor_for(parts):
    m = [e for e in parts if e.get("name") in ("muzzle",)]
    if not m:
        return []
    z = m[0]["from"][2]
    return [box([7.1, 8.6, z - 8], [8.9, 10.4, z], "polymer")]


def gun_display(length_units):
    # length_units ~ 30 for a rifle; keep inventory icons readable
    gui_scale = round(min(0.62, 16.0 / length_units * 0.9), 3)
    return {
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
        "thirdperson_righthand": {"rotation": [-90, 0, 0], "translation": [0, 1.5, -3.5], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [-90, 0, 0], "translation": [0, 1.5, -3.5], "scale": [0.55, 0.55, 0.55]},
        "gui": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [gui_scale, gui_scale, gui_scale]},
        "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 90, 0], "translation": [0, 6, 0], "scale": [0.8, 0.8, 0.8]},
    }


def gen_guns():
    for gid, spec in GUNS.items():
        parts = spec["parts"]
        zs = [e["from"][2] for e in parts] + [e["to"][2] for e in parts]
        length = max(zs) - min(zs)
        disp = gun_display(length)
        overrides = []
        for optic in GUN_OPTICS[gid]:
            for sup in (False, True):
                if gid in ("sg12",) and sup:
                    continue
                name = f"{gid}_{optic}" + ("_suppressed" if sup else "")
                elems = list(parts) + OPTICS[optic] + (suppressor_for(parts) if sup else [])
                write(os.path.join(A, "models", "item", "gun", name + ".json"),
                      {"textures": {"parts": "warfront:entity/parts", "particle": "warfront:entity/parts"},
                       "elements": elems, "display": disp, "gui_light": "front"})
                pred = {"warfront:optic": OPTIC_ORDINAL[optic] / 10.0}
                if sup:
                    pred["warfront:suppressed"] = 1
                overrides.append({"predicate": pred, "model": f"warfront:item/gun/{name}"})
        # base model = default optic without suppressor; overrides ordered so later (more specific) win
        base_elems = list(parts) + OPTICS[GUN_OPTICS[gid][0]]
        write(os.path.join(A, "models", "item", gid + ".json"),
              {"textures": {"parts": "warfront:entity/parts", "particle": "warfront:entity/parts"},
               "elements": base_elems, "display": disp, "gui_light": "front", "overrides": overrides})
    write(os.path.join(os.path.dirname(__file__), "gun_sights.json"), {g: s["sight_y"] for g, s in GUNS.items()})


# ------------------------------------------------------------------ gadgets / flat items
FLAT = ["frag_grenade", "medic_bag", "ammo_bag", "c4", "defibrillator", "repair_tool", "spawn_beacon", "rocket", "tank_deployer",
        "dog_tag", "field_binoculars"]


def gen_items():
    for n in FLAT:
        write(os.path.join(A, "models", "item", n + ".json"), {"parent": "minecraft:item/handheld" if n in ("repair_tool",) else "minecraft:item/generated",
                                                               "textures": {"layer0": f"warfront:item/{n}"}})
    # rocket launcher: a 3D tube
    tube = [box([6.8, 8.5, -8], [9.2, 10.9, 20], "olive"), box([6.5, 8.2, -9], [9.5, 11.2, -8], "gunmetal"),
            box([6.5, 8.2, 20], [9.5, 11.2, 21], "gunmetal"), box([7.4, 4.5, 8], [8.6, 8.5, 10], "polymer"),
            box([7.4, 4.5, 2], [8.6, 8.5, 3.5], "polymer"), box([9.2, 10, 4], [10.4, 12, 7], "gunmetal"),
            box([7.3, 9, -9.2], [8.7, 10.4, -9], "orange")]
    write(os.path.join(A, "models", "item", "rocket_launcher.json"),
          {"textures": {"parts": "warfront:entity/parts", "particle": "warfront:entity/parts"}, "elements": tube,
           "display": gun_display(30), "gui_light": "front"})
    write(os.path.join(A, "models", "item", "soldier_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    for b in ["war_table", "sandbag", "resupply_crate", "reinforced_concrete", "capture_point"]:
        write(os.path.join(A, "models", "item", b + ".json"), {"parent": f"warfront:block/{b}"})


def gen_blocks():
    bm = os.path.join(A, "models", "block")
    write(os.path.join(bm, "sandbag.json"), {"parent": "minecraft:block/cube_column",
                                             "textures": {"side": "warfront:block/sandbag", "end": "warfront:block/sandbag_top"}})
    write(os.path.join(bm, "resupply_crate.json"), {"parent": "minecraft:block/cube_column",
                                                    "textures": {"side": "warfront:block/resupply_crate", "end": "warfront:block/resupply_crate_top"}})
    write(os.path.join(bm, "reinforced_concrete.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "warfront:block/reinforced_concrete"}})
    write(os.path.join(bm, "tower_core.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "warfront:block/tower_core"}})
    write(os.path.join(bm, "vehicle_pad.json"), {"parent": "minecraft:block/cube_top",
                                                 "textures": {"top": "warfront:block/vehicle_pad", "side": "warfront:block/capture_point_side"}})
    write(os.path.join(bm, "hq_beacon.json"), {"parent": "minecraft:block/cube_top",
                                               "textures": {"top": "warfront:block/hq_beacon", "side": "warfront:block/capture_point_side"}})
    write(os.path.join(bm, "capture_point.json"), {
        "textures": {"top": "warfront:block/capture_point", "side": "warfront:block/capture_point_side", "particle": "warfront:block/capture_point_side"},
        "elements": [{"from": [1, 0, 1], "to": [15, 3, 15], "faces": {
            "up": {"uv": [1, 1, 15, 15], "texture": "#top"}, "down": {"uv": [1, 1, 15, 15], "texture": "#side"},
            "north": {"uv": [1, 13, 15, 16], "texture": "#side"}, "south": {"uv": [1, 13, 15, 16], "texture": "#side"},
            "east": {"uv": [1, 13, 15, 16], "texture": "#side"}, "west": {"uv": [1, 13, 15, 16], "texture": "#side"}}}]})
    # war table: legs + map top
    wt = {"textures": {"top": "warfront:block/war_table_top", "side": "warfront:block/war_table_side", "particle": "warfront:block/war_table_side"},
          "elements": []}
    def cube(frm, to, top="#side", side="#side"):
        return {"from": frm, "to": to, "faces": {"up": {"texture": top}, "down": {"texture": "#side"}, "north": {"texture": side},
                                                 "south": {"texture": side}, "east": {"texture": side}, "west": {"texture": side}}}
    wt["elements"].append(cube([0, 11, 0], [16, 14, 16], top="#top"))
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        wt["elements"].append(cube([x, 0, z], [x + 2, 11, z + 2]))
    wt["elements"].append(cube([2, 4, 2], [14, 5, 14]))
    write(os.path.join(bm, "war_table.json"), wt)
    bs = os.path.join(A, "blockstates")
    for b in ["sandbag", "resupply_crate", "reinforced_concrete", "tower_core", "vehicle_pad", "hq_beacon", "capture_point"]:
        write(os.path.join(bs, b + ".json"), {"variants": {"": {"model": f"warfront:block/{b}"}}})
    write(os.path.join(bs, "war_table.json"), {"variants": {
        "facing=north": {"model": "warfront:block/war_table"}, "facing=east": {"model": "warfront:block/war_table", "y": 90},
        "facing=south": {"model": "warfront:block/war_table", "y": 180}, "facing=west": {"model": "warfront:block/war_table", "y": 270}}})


def gen_sounds():
    names = sorted(f[:-4] for f in os.listdir(os.path.join(A, "sounds")) if f.endswith(".ogg"))
    sounds = {}
    for n in names:
        entry = {"sounds": [{"name": f"warfront:{n}"}], "subtitle": f"subtitles.warfront.{n}"}
        if n.endswith("_far") or n in ("tower_collapse", "tower_alarm", "explosion"):
            entry["sounds"] = [{"name": f"warfront:{n}", "attenuation_distance": 64 if not n.endswith("_far") else 24}]
        sounds[n] = entry
    write(os.path.join(A, "sounds.json"), sounds)
    return names


def gen_lang(sound_names):
    lang = {
        "itemGroup.warfront": "WARFRONT",
        "key.categories.warfront": "WARFRONT",
        "key.warfront.reload": "Reload",
        "key.warfront.fire_mode": "Fire Mode",
        "key.warfront.spot": "Spot / Squad Order",
        "key.warfront.deploy": "Deploy Screen",
        "key.warfront.scoreboard": "Battle Scoreboard",
        "item.warfront.frag_grenade": "Frag Grenade",
        "item.warfront.medic_bag": "Medic Bag",
        "item.warfront.ammo_bag": "Ammo Bag",
        "item.warfront.defibrillator": "Defibrillator",
        "item.warfront.rocket_launcher": "RL-4 Breacher",
        "item.warfront.repair_tool": "Repair Tool",
        "item.warfront.c4": "C4 Explosives",
        "item.warfront.spawn_beacon": "Radio Spawn Beacon",
        "item.warfront.field_binoculars": "Field Binoculars",
        "item.warfront.tank_deployer": "MBT-9 Airdrop",
        "item.warfront.dog_tag": "Dog Tag",
        "item.warfront.soldier_spawn_egg": "Soldier Spawn Egg",
        "block.warfront.war_table": "War Table",
        "block.warfront.sandbag": "Sandbags",
        "block.warfront.resupply_crate": "Resupply Crate",
        "block.warfront.capture_point": "Capture Point",
        "block.warfront.hq_beacon": "HQ Beacon",
        "block.warfront.reinforced_concrete": "Reinforced Concrete",
        "block.warfront.tower_core": "Tower Core",
        "block.warfront.vehicle_pad": "Vehicle Pad",
        "entity.warfront.soldier": "Soldier",
        "entity.warfront.tank": "MBT-9 Juggernaut",
        "entity.warfront.frag_grenade": "Frag Grenade",
        "entity.warfront.rocket": "Rocket",
        "entity.warfront.c4": "C4 Charge",
        "entity.warfront.supply_bag": "Supply Bag",
        "entity.warfront.spawn_beacon": "Spawn Beacon",
        "death.attack.warfront.bullet": "%1$s was shot",
        "death.attack.warfront.bullet.player": "%1$s was shot by %2$s",
        "death.attack.warfront.headshot": "%1$s took a headshot",
        "death.attack.warfront.headshot.player": "%1$s was headshot by %2$s",
        "death.attack.warfront.bleed_out": "%1$s bled out",
        "death.attack.warfront.bleed_out.player": "%1$s bled out",
        "death.attack.warfront.crushed": "%1$s was run over",
        "death.attack.warfront.crushed.player": "%1$s was run over by %2$s",
    }
    for g in GUNS:
        lang[f"item.warfront.{g}"] = g.upper()
    for n in sound_names:
        lang[f"subtitles.warfront.{n}"] = n.replace("_", " ").capitalize()
    write(os.path.join(A, "lang", "en_us.json"), lang)


def gen_data():
    dt = os.path.join(D, "warfront", "damage_type")
    write(os.path.join(dt, "bullet.json"), {"message_id": "warfront.bullet", "exhaustion": 0.1, "scaling": "never"})
    write(os.path.join(dt, "headshot.json"), {"message_id": "warfront.headshot", "exhaustion": 0.1, "scaling": "never"})
    write(os.path.join(dt, "bleed_out.json"), {"message_id": "warfront.bleed_out", "exhaustion": 0, "scaling": "never"})
    write(os.path.join(dt, "crushed.json"), {"message_id": "warfront.crushed", "exhaustion": 0.1, "scaling": "never"})
    tags = os.path.join(D, "minecraft", "tags", "damage_type")
    write(os.path.join(tags, "no_knockback.json"), {"replace": False, "values": ["warfront:bullet", "warfront:headshot", "warfront:bleed_out"]})
    write(os.path.join(tags, "is_projectile.json"), {"replace": False, "values": ["warfront:bullet", "warfront:headshot"]})
    write(os.path.join(tags, "bypasses_cooldown.json"), {"replace": False, "values": ["warfront:bullet", "warfront:headshot"]})
    write(os.path.join(tags, "bypasses_armor.json"), {"replace": False, "values": ["warfront:bleed_out"]})
    write(os.path.join(tags, "bypasses_invulnerability.json"), {"replace": False, "values": ["warfront:bleed_out"]})
    # recipes
    rc = os.path.join(D, "warfront", "recipe")
    write(os.path.join(rc, "war_table.json"), {"type": "minecraft:crafting_shaped", "category": "misc",
                                               "pattern": ["MPM", "LCL", "L L"],
                                               "key": {"M": {"item": "minecraft:map"}, "P": {"item": "minecraft:paper"},
                                                       "L": {"tag": "minecraft:logs"}, "C": {"item": "minecraft:compass"}},
                                               "result": {"id": "warfront:war_table", "count": 1}})
    write(os.path.join(rc, "sandbag.json"), {"type": "minecraft:crafting_shaped", "category": "building",
                                             "pattern": ["WSW", "SWS", "WSW"],
                                             "key": {"W": {"tag": "minecraft:wool"}, "S": {"tag": "minecraft:sand"}},
                                             "result": {"id": "warfront:sandbag", "count": 8}})
    write(os.path.join(rc, "reinforced_concrete.json"), {"type": "minecraft:crafting_shaped", "category": "building",
                                                         "pattern": ["SIS", "IGI", "SIS"],
                                                         "key": {"S": {"item": "minecraft:stone"}, "I": {"item": "minecraft:iron_bars"},
                                                                 "G": {"item": "minecraft:gravel"}},
                                                         "result": {"id": "warfront:reinforced_concrete", "count": 8}})
    lt = os.path.join(D, "warfront", "loot_table", "blocks")
    for b in ["war_table", "sandbag", "resupply_crate", "reinforced_concrete"]:
        write(os.path.join(lt, b + ".json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"warfront:{b}"}],
                                                                                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # mineable tags
    write(os.path.join(D, "minecraft", "tags", "block", "mineable", "pickaxe.json"), {"replace": False, "values": ["warfront:reinforced_concrete", "warfront:resupply_crate"]})
    write(os.path.join(D, "minecraft", "tags", "block", "mineable", "shovel.json"), {"replace": False, "values": ["warfront:sandbag"]})
    write(os.path.join(D, "minecraft", "tags", "block", "mineable", "axe.json"), {"replace": False, "values": ["warfront:war_table"]})


if __name__ == "__main__":
    gen_guns()
    gen_items()
    gen_blocks()
    names = gen_sounds()
    gen_lang(names)
    gen_data()
    print("assets generated")
