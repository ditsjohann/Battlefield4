#!/usr/bin/env python3
"""High-detail gun models and textures for WARFRONT.

Each gun is built from named parts (boxes) grouped as body / mag / optic / muzzle. Every face of
every part gets its own UV island on a per-gun 256x256 texture, painted procedurally by material
(machined metal with edge wear, polymer stipple, wood grain, rubber) plus surface details (rail
teeth, handguard vents, grip checkering, magazine ribs, ejection port, barrel flutes, muzzle
ports, stamped model markings) and baked top-down lighting.

Outputs (under assets/warfront):
  textures/item/gun/<gun>.png                        painted texture sheet
  models/item/<gun>.json                              base model + overrides
  models/item/gun/<gun>_<optic>[_suppressed][_nomag].json   variants
  models/item/gun/<gun>_mag.json                      magazine alone (first-person reload animation)

    python3 tools/gen_guns.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "warfront")
TEX = 512
GEOM = 0.6  # geometry shrink so long guns fit the -16..32 model bounds; display scales compensate
R = random.Random(17)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


MATS = {
    "receiver": hexc("#2a2d31"),
    "metal": hexc("#3b3f45"),
    "steel": hexc("#5f656d"),
    "blued": hexc("#1f2328"),
    "polymer": hexc("#1d1e21"),
    "tan": hexc("#a38b60"),
    "coyote": hexc("#7d6545"),
    "wood": hexc("#6a4325"),
    "od": hexc("#4f5a2c"),
    "rubber": hexc("#151516"),
    "glass": hexc("#244e7e"),
    "red": hexc("#ff2a1a"),
    "brass": hexc("#c49a3c"),
    "olive_drab": hexc("#4a5230"),
    "white": hexc("#d8d8d8"),
}


class Part:
    def __init__(self, frm, to, mat, detail=None, rot=None, group="body", name=None):
        self.frm = frm
        self.to = to
        self.mat = mat
        if isinstance(detail, dict) and "angle" in detail:  # rotation passed positionally
            detail, rot = None, detail
        self.detail = detail or {}
        if isinstance(self.detail, str):
            self.detail = {f: self.detail for f in ("north", "south", "east", "west", "up", "down")}
        self.rot = rot
        self.group = group
        self.name = name


def P(frm, to, mat, detail=None, rot=None, group="body", name=None):
    return Part(frm, to, mat, detail, rot, group, name)


def side(d):
    """Detail only on the two long side faces."""
    return {"east": d, "west": d}


def tops(d):
    return {"east": d, "west": d, "up": d}


def rotx(angle, origin):
    return {"angle": angle, "axis": "x", "origin": origin}


# ------------------------------------------------------------------ shared building blocks
def ar_lower(c, grip="polymer", z0=6.0, z1=13.6):
    return [
        P([7.1, 7.6, z0], [8.9, 9.4, z1], c, side("stamp"), name="lower"),
        P([7.0, 6.6, z0 + 0.6], [9.0, 7.7, z0 + 3.4], c, name="magwell"),
        P([7.25, 4.2, 11.4], [8.75, 8.0, 13.4], grip, "grip", rotx(-22.5, [8, 8, 12.4]), name="grip"),
        P([7.65, 6.95, 9.6], [8.35, 7.25, 11.8], c, name="trigger_guard"),
        P([7.88, 7.25, 10.5], [8.12, 8.0, 10.85], "steel", name="trigger"),
        P([8.9, 8.2, 11.6], [9.15, 8.7, 12.4], "steel", name="selector"),
    ]


def ar_upper(c, z0=4.6, z1=13.6):
    return [
        P([7.15, 9.4, z0], [8.85, 11.0, z1], c, {"east": "port", "west": "plain"}, name="upper"),
        P([7.45, 11.0, z0 - 0.2], [8.55, 11.5, z1], "receiver", tops("rail"), name="top_rail"),
        P([7.6, 10.55, z1], [8.4, 11.0, z1 + 0.7], "steel", name="charging_handle"),
        P([8.85, 9.9, z1 - 2.4], [9.3, 10.5, z1 - 1.2], "steel", name="forward_assist"),
        P([8.85, 9.6, z1 - 5.2], [8.95, 10.6, z1 - 3.0], "brass", name="bolt_glimpse"),
    ]


def ar_stock(c, rubber="rubber"):
    return [
        P([7.55, 9.6, 13.6], [8.45, 10.6, 19.5], "metal", name="buffer_tube"),
        P([7.2, 8.0, 16.6], [8.8, 11.0, 22.0], c, side("stipple"), name="stock"),
        P([7.3, 11.0, 17.2], [8.7, 11.45, 21.2], c, name="cheek_riser"),
        P([7.1, 7.4, 22.0], [8.9, 11.25, 22.8], rubber, "grip", name="buttpad"),
        P([7.75, 8.7, 20.6], [8.25, 9.2, 21.0], "steel", name="sling_loop"),
    ]


def curved_mag(c, z=6.9, top=6.8, rib="ribs"):
    return [
        P([7.25, 4.6, z], [8.75, top, z + 2.4], c, side(rib), group="mag", name="mag_upper"),
        P([7.25, 2.4, z + 0.55], [8.75, 4.75, z + 2.95], c, side(rib), rotx(22.5, [8, 4.75, z + 1.2]), group="mag", name="mag_lower"),
        P([7.15, 2.0, z + 1.4], [8.85, 2.55, z + 3.6], "polymer", rotx(22.5, [8, 4.75, z + 1.2]), group="mag", name="mag_base"),
    ]


def straight_mag(c, z=6.9, length=4.0, rib="ribs"):
    top = 6.8
    return [
        P([7.25, top - length, z], [8.75, top, z + 2.4], c, side(rib), group="mag", name="mag_body"),
        P([7.15, top - length - 0.5, z - 0.1], [8.85, top - length, z + 2.6], "polymer", group="mag", name="mag_base"),
    ]


# ------------------------------------------------------------------ the guns
def ar17():
    parts = ar_lower("receiver") + ar_upper("receiver") + ar_stock("polymer") + [
        P([6.85, 8.55, -6.2], [9.15, 11.05, 4.6], "polymer", {"east": "vent", "west": "vent", "down": "vent", "up": "plain"}, name="handguard"),
        P([7.45, 11.05, -6.2], [8.55, 11.5, 4.6], "receiver", tops("rail"), name="hg_rail"),
        P([7.55, 9.45, -11.0], [8.45, 10.35, -6.2], "metal", side("flute"), name="barrel"),
        P([7.35, 9.25, -9.2], [8.65, 10.75, -8.0], "receiver", name="gas_block"),
        P([7.35, 9.25, -13.6], [8.65, 10.55, -11.0], "receiver", side("ports"), group="muzzle", name="muzzle"),
        P([7.75, 11.5, -5.8], [8.25, 12.45, -5.3], "receiver", group="iron_front", name="front_sight"),
        P([6.6, 9.2, -2.0], [6.85, 10.2, 0.0], "steel", name="sling_mount"),
    ] + curved_mag("polymer")
    return dict(parts=parts, sight=12.4, mark="WF-17")


def kv12():
    parts = [
        P([7.1, 8.0, 3.0], [8.9, 10.6, 14.0], "blued", {"east": "port", "west": "stamp"}, name="receiver"),
        P([7.2, 10.6, 3.2], [8.8, 11.2, 13.4], "blued", name="dust_cover"),
        P([7.45, 11.2, 5.0], [8.55, 11.55, 12.0], "receiver", tops("rail"), name="optic_rail"),
        P([8.9, 9.6, 6.0], [9.2, 9.9, 12.0], "steel", name="charging_lever"),
        P([6.8, 7.8, -5.0], [9.2, 10.2, 3.0], "wood", {"east": "wood", "west": "wood", "down": "wood", "up": "wood"}, name="lower_handguard"),
        P([7.2, 10.2, -4.6], [8.8, 11.0, 3.0], "wood", tops("wood"), name="gas_tube_cover"),
        P([7.55, 8.95, -12.0], [8.45, 9.85, -5.0], "metal", name="barrel"),
        P([7.45, 10.0, -7.5], [8.55, 10.9, -5.0], "metal", name="gas_tube"),
        P([7.35, 9.6, -8.6], [8.65, 11.2, -7.6], "blued", name="gas_block"),
        P([7.35, 8.75, -14.6], [8.65, 10.05, -12.0], "blued", side("ports"), group="muzzle", name="muzzle"),
        P([7.75, 10.9, -12.0], [8.25, 12.1, -11.4], "blued", group="iron_front", name="front_sight"),
        P([7.25, 4.4, 10.6], [8.75, 8.0, 12.6], "wood", "grip", rotx(-22.5, [8, 8, 11.6]), name="grip"),
        P([7.65, 7.0, 8.6], [8.35, 7.3, 10.8], "blued", name="trigger_guard"),
        P([7.88, 7.3, 9.6], [8.12, 8.0, 9.95], "steel", name="trigger"),
        P([7.3, 7.6, 14.0], [8.7, 10.4, 17.0], "wood", side("wood"), name="stock_neck"),
        P([7.2, 6.6, 17.0], [8.8, 10.4, 22.2], "wood", side("wood"), rotx(-22.5, [8, 10.4, 17.0]), name="stock"),
        P([7.1, 5.2, 21.0], [8.9, 9.4, 21.8], "rubber", "grip", rotx(-22.5, [8, 10.4, 17.0]), name="buttplate"),
    ] + [
        P([7.25, 4.4, 5.4], [8.75, 8.0, 7.8], "coyote", side("ribs"), group="mag", name="mag_upper"),
        P([7.25, 2.0, 6.2], [8.75, 4.5, 8.6], "coyote", side("ribs"), rotx(22.5, [8, 4.5, 6.6]), group="mag", name="mag_lower"),
        P([7.2, 1.5, 7.4], [8.8, 2.2, 9.6], "coyote", rotx(45, [8, 4.5, 6.6]), group="mag", name="mag_tip"),
    ]
    return dict(parts=parts, sight=12.4, mark="KV-12")


def cx9():
    parts = ar_lower("receiver", grip="polymer") + ar_upper("receiver") + [
        P([6.85, 8.55, -2.6], [9.15, 11.05, 4.6], "tan", {"east": "vent", "west": "vent", "down": "vent"}, name="handguard"),
        P([7.45, 11.05, -2.6], [8.55, 11.5, 4.6], "receiver", tops("rail"), name="hg_rail"),
        P([7.55, 9.45, -6.4], [8.45, 10.35, -2.6], "metal", name="barrel"),
        P([7.35, 9.25, -8.6], [8.65, 10.55, -6.4], "receiver", side("ports"), group="muzzle", name="muzzle"),
        P([7.75, 11.5, -2.3], [8.25, 12.45, -1.8], "receiver", group="iron_front", name="front_sight"),
        P([7.6, 6.3, -1.0], [8.4, 8.55, 0.4], "tan", "grip", name="vgrip_stub"),
        P([7.55, 9.6, 13.6], [8.45, 10.5, 18.6], "metal", name="buffer_tube"),
        P([7.25, 8.0, 17.4], [8.75, 10.9, 20.0], "tan", side("stipple"), name="stock"),
        P([7.15, 7.6, 20.0], [8.85, 11.1, 20.6], "rubber", "grip", name="buttpad"),
        P([7.6, 8.6, 14.0], [8.4, 9.2, 17.4], "tan", name="stock_strut"),
    ] + straight_mag("tan", length=3.6)
    return dict(parts=parts, sight=12.4, mark="CX-9")


def hmg240():
    parts = ar_lower("od") + [
        P([7.0, 9.4, 3.6], [9.0, 11.2, 13.6], "od", {"east": "port", "west": "stamp"}, name="receiver"),
        P([7.2, 11.2, 3.8], [8.8, 11.7, 12.0], "od", tops("plain"), name="feed_cover"),
        P([7.45, 11.7, 5.0], [8.55, 12.0, 11.6], "receiver", tops("rail"), name="cover_rail"),
        P([6.9, 8.6, -6.0], [9.1, 11.0, 3.6], "od", {"east": "vent", "west": "vent", "up": "vent"}, name="heat_shield"),
        P([7.5, 9.4, -15.0], [8.5, 10.4, -6.0], "metal", side("flute"), name="barrel"),
        P([7.6, 10.4, -9.0], [8.4, 11.0, 2.0], "metal", name="gas_tube"),
        P([7.2, 11.6, -1.0], [8.8, 12.0, 2.2], "polymer", name="carry_handle"),
        P([7.6, 11.0, -0.8], [8.4, 11.6, -0.4], "polymer", name="carry_post_a"),
        P([7.6, 11.0, 1.8], [8.4, 11.6, 2.2], "polymer", name="carry_post_b"),
        P([7.3, 9.15, -17.4], [8.7, 10.65, -15.0], "receiver", side("ports"), group="muzzle", name="muzzle"),
        P([6.5, 5.2, -12.2], [7.1, 9.4, -11.6], "steel", None, rotx(22.5, [6.8, 9.4, -11.9]), name="bipod_l"),
        P([8.9, 5.2, -12.2], [9.5, 9.4, -11.6], "steel", None, rotx(22.5, [9.2, 9.4, -11.9]), name="bipod_r"),
        P([7.3, 9.2, -12.4], [8.7, 9.8, -11.4], "receiver", name="bipod_clamp"),
        P([7.55, 9.6, 13.6], [8.45, 10.6, 16.0], "metal", name="buffer"),
        P([7.2, 7.6, 15.6], [8.8, 11.1, 22.2], "polymer", side("stipple"), name="stock"),
        P([7.1, 7.2, 22.2], [8.9, 11.3, 23.0], "rubber", "grip", name="buttpad"),
        P([9.0, 9.6, 6.0], [9.8, 10.3, 9.0], "brass", "ribs", name="belt"),
    ] + [
        P([6.2, 3.2, 4.8], [9.8, 7.7, 10.2], "olive_drab", {"east": "stamp", "west": "ribs"}, group="mag", name="box_mag"),
        P([6.4, 7.7, 5.2], [9.6, 8.0, 9.8], "olive_drab", group="mag", name="box_lid"),
    ]
    return dict(parts=parts, sight=12.9, mark="HMG240")


def sr338():
    parts = [
        P([7.1, 8.2, 4.0], [8.9, 10.8, 14.6], "receiver", {"east": "port", "west": "stamp"}, name="action"),
        P([7.45, 10.8, 2.0], [8.55, 11.3, 13.6], "receiver", tops("rail"), name="scope_rail"),
        P([9.0, 9.9, 12.4], [10.9, 10.5, 13.0], "steel", name="bolt_handle"),
        P([10.4, 9.6, 12.2], [11.2, 10.8, 13.2], "polymer", name="bolt_knob"),
        P([6.7, 7.6, -6.0], [9.3, 10.4, 4.0], "od", {"east": "vent", "west": "vent", "down": "vent"}, name="forend"),
        P([7.55, 8.9, -20.0], [8.45, 9.8, -6.0], "metal", side("flute"), name="barrel"),
        P([7.25, 8.65, -23.0], [8.75, 10.05, -20.0], "receiver", side("ports"), group="muzzle", name="muzzle"),
        P([7.25, 4.4, 12.0], [8.75, 8.2, 14.0], "od", "grip", rotx(-22.5, [8, 8.2, 13.0]), name="grip"),
        P([7.65, 7.0, 10.2], [8.35, 7.3, 12.4], "receiver", name="trigger_guard"),
        P([7.88, 7.3, 11.2], [8.12, 8.0, 11.55], "steel", name="trigger"),
        P([7.4, 9.4, 14.6], [8.6, 10.6, 23.4], "od", side("stipple"), name="stock_spine"),
        P([7.4, 6.4, 20.6], [8.6, 9.4, 23.4], "od", side("stipple"), name="stock_rear"),
        P([7.4, 6.4, 16.8], [8.6, 7.4, 20.6], "od", name="stock_lower"),
        P([7.5, 10.6, 17.0], [8.5, 11.3, 21.6], "polymer", name="cheek_piece"),
        P([7.3, 6.0, 23.4], [8.7, 10.8, 24.2], "rubber", "grip", name="buttpad"),
        P([7.75, 5.4, 22.0], [8.25, 6.4, 22.6], "steel", name="monopod"),
    ] + [
        P([7.25, 5.6, 7.0], [8.75, 8.2, 9.4], "receiver", side("ribs"), group="mag", name="mag_body"),
        P([7.15, 5.2, 6.9], [8.85, 5.6, 9.5], "polymer", group="mag", name="mag_base"),
    ]
    return dict(parts=parts, sight=12.4, mark="SR338")


def m7():
    parts = ar_lower("tan") + ar_upper("receiver") + ar_stock("tan") + [
        P([6.85, 8.55, -8.6], [9.15, 11.05, 4.6], "tan", {"east": "vent", "west": "vent", "down": "vent"}, name="handguard"),
        P([7.45, 11.05, -8.6], [8.55, 11.5, 4.6], "receiver", tops("rail"), name="hg_rail"),
        P([7.55, 9.45, -15.0], [8.45, 10.35, -8.6], "metal", side("flute"), name="barrel"),
        P([7.3, 9.2, -17.6], [8.7, 10.6, -15.0], "receiver", side("ports"), group="muzzle", name="muzzle"),
        P([7.75, 11.5, -8.2], [8.25, 12.45, -7.7], "receiver", group="iron_front", name="front_sight"),
    ] + straight_mag("polymer", length=2.8)
    return dict(parts=parts, sight=12.4, mark="M-7")


def sg12():
    parts = [
        P([7.05, 8.0, 5.6], [8.95, 11.1, 14.0], "blued", {"east": "port", "west": "stamp"}, name="receiver"),
        P([7.45, 11.1, 6.0], [8.55, 11.5, 13.4], "receiver", tops("rail"), name="top_rail"),
        P([8.95, 8.6, 7.0], [9.5, 10.6, 11.0], "polymer", "ribs", name="side_saddle"),
        P([9.5, 8.8, 7.3], [9.8, 10.4, 10.7], "red", None, name="shells"),
        P([7.5, 9.4, -13.0], [8.5, 10.4, 5.6], "metal", name="barrel"),
        P([7.55, 8.2, -11.0], [8.45, 9.2, 5.6], "metal", name="tube"),
        P([7.65, 10.4, -12.6], [8.35, 10.95, -12.1], "brass", None, group="iron_front", name="bead"),
        P([7.25, 4.4, 12.0], [8.75, 8.0, 14.0], "polymer", "grip", rotx(-22.5, [8, 8, 13.0]), name="grip"),
        P([7.65, 7.0, 10.0], [8.35, 7.3, 12.2], "blued", name="trigger_guard"),
        P([7.88, 7.3, 11.0], [8.12, 8.0, 11.35], "steel", name="trigger"),
        P([7.25, 7.6, 14.0], [8.75, 10.8, 22.0], "wood", side("wood"), name="stock"),
        P([7.1, 7.2, 22.0], [8.9, 11.0, 22.8], "rubber", "grip", name="buttpad"),
        P([7.4, 9.0, -14.0], [8.6, 10.6, -13.0], "blued", group="muzzle", name="muzzle"),
    ] + [
        # the pump forend is the "mag" group for the shotgun: it slides when cycling
        P([6.8, 7.7, -6.0], [9.2, 9.7, 1.4], "wood", {"east": "grip", "west": "grip", "down": "grip", "up": "wood"}, group="mag", name="pump"),
    ]
    return dict(parts=parts, sight=11.2, mark="SG-12")


def p12():
    parts = [
        P([7.25, 9.1, 3.6], [8.75, 11.0, 12.0], "polymer", {"east": "serrate", "west": "serrate", "up": "plain"}, name="slide"),
        P([7.5, 9.35, 3.2], [8.5, 10.35, 3.6], "metal", group="muzzle", name="muzzle"),
        P([7.3, 8.0, 4.4], [8.7, 9.1, 11.6], "polymer", side("stamp"), name="frame"),
        P([7.35, 7.4, 4.6], [8.65, 8.0, 6.6], "polymer", "rail", name="frame_rail"),
        P([7.3, 4.0, 9.4], [8.7, 8.3, 12.0], "polymer", "grip", rotx(-22.5, [8, 8, 11.0]), name="grip"),
        P([7.7, 7.2, 7.8], [8.3, 7.5, 9.6], "polymer", name="trigger_guard"),
        P([7.9, 7.5, 8.6], [8.1, 8.0, 8.9], "steel", name="trigger"),
        P([7.75, 11.0, 4.0], [8.25, 11.5, 4.5], "steel", group="iron_front", name="front_sight"),
        P([7.4, 11.0, 11.0], [8.6, 11.5, 11.6], "steel", group="iron_rear", name="rear_sight"),
    ] + [
        P([7.4, 3.2, 9.9], [8.6, 4.2, 11.9], "polymer", None, rotx(-22.5, [8, 8, 11.0]), group="mag", name="mag_base"),
    ]
    return dict(parts=parts, sight=11.3, mark="P12")


# ------------------------------------------------------------------ attachments
def optic(name):
    if name == "iron_sights":
        return [
            P([7.3, 11.5, 11.8], [8.7, 11.95, 12.6], "receiver", group="optic", name="rear_base"),
            P([7.3, 11.95, 11.8], [7.7, 12.75, 12.3], "receiver", group="optic", name="rear_ear_l"),
            P([8.3, 11.95, 11.8], [8.7, 12.75, 12.3], "receiver", group="optic", name="rear_ear_r"),
        ]
    if name == "red_dot":
        return [
            P([7.25, 11.5, 8.0], [8.75, 12.05, 10.6], "receiver", group="optic", name="rd_base"),
            P([7.15, 12.05, 9.5], [7.45, 13.5, 10.3], "polymer", group="optic", name="rd_l"),
            P([8.55, 12.05, 9.5], [8.85, 13.5, 10.3], "polymer", group="optic", name="rd_r"),
            P([7.15, 13.35, 9.5], [8.85, 13.75, 10.3], "polymer", group="optic", name="rd_top"),
            P([8.85, 12.4, 9.7], [9.15, 12.9, 10.1], "steel", group="optic", name="rd_knob"),
            P([7.9, 12.6, 9.92], [8.1, 12.8, 9.98], "red", group="optic", name="rd_dot"),
        ]
    if name == "holo":
        return [
            P([7.0, 11.5, 5.8], [9.0, 12.1, 10.8], "polymer", group="optic", name="holo_base"),
            P([6.8, 12.1, 6.1], [7.2, 14.1, 7.0], "polymer", group="optic", name="holo_l"),
            P([8.8, 12.1, 6.1], [9.2, 14.1, 7.0], "polymer", group="optic", name="holo_r"),
            P([6.8, 13.9, 6.1], [9.2, 14.3, 7.0], "polymer", {"up": "stipple"}, group="optic", name="holo_top"),
            P([6.9, 12.1, 9.0], [9.1, 12.9, 10.8], "polymer", {"east": "stamp"}, group="optic", name="holo_battery"),
            P([7.85, 12.95, 6.5], [8.15, 13.15, 6.55], "red", group="optic", name="holo_ring"),
        ]
    if name == "prism_4x":
        return [
            P([7.1, 11.5, 6.0], [8.9, 12.2, 11.2], "polymer", group="optic", name="prism_mount"),
            P([7.1, 12.2, 5.2], [8.9, 14.0, 11.6], "receiver", {"east": "stamp"}, group="optic", name="prism_body"),
            P([7.3, 12.4, 5.0], [8.7, 13.8, 5.2], "glass", "lens", group="optic", name="prism_lens"),
            P([7.6, 14.0, 7.6], [8.4, 14.5, 8.6], "steel", group="optic", name="prism_ruby"),
            P([7.3, 12.4, 11.6], [8.7, 13.8, 11.8], "glass", "lens", group="optic", name="prism_eye"),
        ]
    if name == "sniper_8x":
        return [
            P([7.4, 11.3, 4.4], [8.6, 12.2, 5.6], "steel", group="optic", name="ring_f"),
            P([7.4, 11.3, 10.4], [8.6, 12.2, 11.6], "steel", group="optic", name="ring_r"),
            P([7.2, 12.2, 2.8], [8.8, 13.8, 13.2], "blued", group="optic", name="tube"),
            P([6.85, 11.85, 0.2], [9.15, 14.15, 2.8], "blued", group="optic", name="obj_bell"),
            P([7.0, 12.0, 0.0], [9.0, 14.0, 0.2], "glass", "lens", group="optic", name="obj_lens"),
            P([6.95, 11.95, 13.2], [9.05, 14.05, 15.2], "blued", group="optic", name="eyepiece"),
            P([7.7, 13.8, 7.0], [8.3, 14.6, 8.2], "steel", "ribs", group="optic", name="turret_e"),
            P([8.8, 12.7, 7.0], [9.6, 13.3, 8.2], "steel", "ribs", group="optic", name="turret_w"),
        ]
    raise ValueError(name)


def suppressor(muzzle):
    z0 = muzzle.frm[2]
    cy = (muzzle.frm[1] + muzzle.to[1]) / 2
    length = min(9.0, z0 + 31.8 - 0.4)  # stay inside the model format's bounds on long barrels
    return [
        P([7.1, cy - 0.95, z0 - length], [8.9, cy + 0.95, z0 + 0.4], "receiver", side("stipple"), group="sup", name="can"),
        P([7.25, cy - 0.8, z0 - length - 0.4], [8.75, cy + 0.8, z0 - length], "metal", group="sup", name="can_cap"),
    ]


GUNS = {"ar17": ar17, "kv12": kv12, "cx9": cx9, "hmg240": hmg240, "sr338": sr338, "m7": m7, "sg12": sg12, "p12": p12}
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


# ------------------------------------------------------------------ painting
FONT = {  # 3x5 pixel glyphs for stamped markings
    "0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
    "5": "111100111001111", "6": "111100111101111", "7": "111001010010010", "8": "111101111101111", "9": "111101111001111",
    "-": "000000111000000", "W": "101101101111101", "F": "111100110100100", "K": "101110100110101", "V": "101101101101010",
    "C": "111100100100111", "X": "101101010101101", "H": "101101111101101", "M": "101111111101101", "G": "111100101101111",
    "S": "111100111001111", "R": "110101110101101", "P": "111101111100100", ".": "000000000000010",
}


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return tuple(clamp(x * f) for x in c)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def _hash(ix, iy, seed):
    h = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed):
    """Smooth value noise in [0, 1]."""
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    c, d = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy


# Physically-motivated finishes. All variation is luminance-only (no per-channel jitter), so
# surfaces never pick up hue shifts; colour only changes where the material really changes.
FINISH = {
    # mat: (grain amplitude, cloud amplitude, edge highlight, edge shadow)
    "receiver": (0.020, 0.035, 1.30, 0.80),   # hard-anodised aluminium
    "metal": (0.030, 0.040, 1.25, 0.80),      # parkerised steel
    "steel": (0.030, 0.030, 1.20, 0.82),
    "blued": (0.018, 0.045, 1.35, 0.78),
    "polymer": (0.028, 0.025, 1.18, 0.82),
    "tan": (0.022, 0.030, 1.10, 0.86),
    "coyote": (0.022, 0.030, 1.10, 0.86),
    "od": (0.022, 0.030, 1.12, 0.84),
    "olive_drab": (0.022, 0.030, 1.12, 0.84),
    "wood": (0.0, 0.0, 1.10, 0.85),
    "rubber": (0.035, 0.015, 1.10, 0.85),
    "brass": (0.020, 0.050, 1.20, 0.85),
    "glass": (0.0, 0.0, 1.0, 1.0),
    "red": (0.0, 0.0, 1.0, 1.0),
    "white": (0.02, 0.02, 1.05, 0.9),
}


def paint_face(img, rect, mat, detail, face, dims, mark, ppu, seed):
    """rect = (x0, y0, w, h): the face's pixels. A 1px gutter around it is filled by extrusion."""
    px = img.load()
    x0, y0, w, h = rect
    base = MATS[mat]
    grain, cloud, hi, lo = FINISH[mat]
    side_face = face in ("east", "west", "north", "south")
    along_u = face in ("east", "west")       # u runs along the gun on the long side faces
    along_v = face in ("up", "down")
    out = [[None] * w for _ in range(h)]
    for j in range(h):
        for i in range(w):
            u, v = i / ppu, j / ppu
            # length / across coordinates in model units, for patterns that follow the barrel axis
            L, A = (u, v) if along_u else (v, u) if along_v else (u, v)
            f = 1.0
            f += (vnoise(u * 0.9, v * 0.9, seed) - 0.5) * 2 * cloud
            f += (_hash(i, j, seed + 7) - 0.5) * 2 * grain
            if side_face and h > 3:
                f *= 1.05 - 0.10 * (j / (h - 1))
            c = base
            if mat == "wood":
                # long flowing grain with darker late-wood lines and a soft varnish sheen
                warp = vnoise(L * 0.35, A * 0.6, seed) * 3.0
                g = math.sin((A * 5.2 + warp) * math.pi)
                streak = vnoise(L * 0.15, A * 3.5, seed + 3)
                c = mix(hexc("#5e3a20"), hexc("#80542f"), 0.5 + 0.35 * g * (0.6 + 0.4 * streak))
                if g > 0.86:
                    c = mix(c, hexc("#3e2412"), 0.55)
                f *= 0.97 + 0.06 * vnoise(L * 2.0, A * 2.0, seed + 5)
            if mat == "brass":
                f *= 0.9 + 0.25 * (1 - j / max(1, h - 1))
            d = detail
            if d == "rail" and (along_u or along_v):
                k = (L * ppu) % (0.62 * ppu)
                slot = k > 0.36 * ppu
                f *= 0.58 if slot else 1.06
                if not slot and k < 1:
                    f *= 1.12
            elif d == "vent":
                # M-LOK style rounded slots with a lit lower lip
                span = (h if along_u else w) / ppu
                pitch, sl = 1.45, 0.85
                k = L % pitch
                m = A
                if span > 1.2 and 0.18 * span < m < 0.82 * span and 0.25 < k < 0.25 + sl:
                    edge = min(k - 0.25, 0.25 + sl - k, m - 0.18 * span, 0.82 * span - m)
                    f *= 0.34 if edge > 0.08 else 0.55
                    if 0.82 * span - m < 0.12:
                        f *= 1.6
            elif d == "grip":
                # fine diamond checkering, low contrast
                q = (i + j) % 4 == 0 or (i - j) % 4 == 0
                f *= 0.86 if q else 1.03
            elif d == "ribs":
                k = (A * ppu) % (0.55 * ppu)
                f *= 0.72 if k < 1 else 1.08 if k < 2 else 1.0
            elif d == "flute" and side_face:
                hgt = h / ppu
                for fy in (hgt * 0.33, hgt * 0.66):
                    if abs(v - fy) < 0.09:
                        f *= 0.62
                    elif 0.09 <= v - fy < 0.18:
                        f *= 1.12
            elif d == "ports":
                span = (h if along_u else w) / ppu
                k = L % 0.6
                if 0.15 < k < 0.4 and 0.3 * span < A < 0.7 * span:
                    f *= 0.22
            elif d == "serrate":
                if L > (w / ppu) * 0.6 and (L * ppu) % 3 < 1.2:
                    f *= 0.6
            elif d == "stipple":
                if _hash(i // 2, j // 2, seed + 11) < 0.22:
                    f *= 0.86
            elif d == "lens":
                t = j / max(1, h - 1)
                c = mix(hexc("#2f5b7a"), hexc("#0c1820"), t)
                c = mix(c, hexc("#6a3d7a"), 0.25 * vnoise(u * 2, v * 2, seed))  # coating tint
                if i < w * 0.4 and j < h * 0.35:
                    c = mix(c, (225, 235, 255), 0.7)
                f = 1.0
            if mat == "red":
                c, f = (255, 52, 34), 1.0
            if mat == "glass" and d != "lens":
                c, f = hexc("#1b3346"), 1.0
            out[j][i] = shade(c, f)
    # bevels: a thin highlight on the top edge, soft shadow on the others (crisp at any ppu)
    if mat not in ("glass", "red") and detail != "lens":
        for i in range(w):
            if face != "down":
                out[0][i] = shade(out[0][i], hi)
            out[h - 1][i] = shade(out[h - 1][i], lo)
        for j in range(1, h - 1):
            out[j][0] = shade(out[j][0], 0.5 + 0.5 * lo)
            out[j][w - 1] = shade(out[j][w - 1], 0.5 + 0.5 * lo)
    # ejection port: dark window with the bolt carrier showing
    if detail == "port" and w > 14 and h > 6:
        pw, ph = int(w * 0.3), max(4, int(h * 0.4))
        sx, sy = int(w * 0.42), int(h * 0.24)
        for j in range(ph):
            for i in range(pw):
                c = (16, 16, 18)
                if 1 <= j <= ph - 2 and 1 <= i <= pw - 2:
                    c = shade(hexc("#6c6f73"), 0.75 + 0.35 * (1 - j / ph))
                    if pw * 0.35 < i < pw * 0.62:
                        c = shade(MATS["brass"], 0.85)
                out[sy + j][sx + i] = c
        for i in range(pw):
            out[min(h - 1, sy + ph)][sx + i] = shade(out[min(h - 1, sy + ph)][sx + i], 1.35)
    # engraved markings: small, low contrast, like real roll marks
    if detail == "stamp" and mark and w > len(mark) * 4 + 6 and h > 9:
        sx = 3
        sy = h - 8
        ink = shade(base, 1.55) if sum(base) < 300 else shade(base, 0.7)
        for ch in mark:
            g = FONT.get(ch)
            if g:
                for gi, bit in enumerate(g):
                    if bit == "1":
                        out[sy + gi // 3][sx + gi % 3] = ink
            sx += 4
    # write with a 1px extruded gutter so mipmaps/filtering never bleed neighbouring faces
    for j in range(-1, h + 1):
        for i in range(-1, w + 1):
            px[x0 + i, y0 + j] = out[min(h - 1, max(0, j))][min(w - 1, max(0, i))] + (255,)


class Packer:
    def __init__(self, size):
        self.size = size
        self.x = self.y = self.row = 0

    def place(self, w, h):
        w2, h2 = w + 2, h + 2  # gutter
        if self.x + w2 > self.size:
            self.x = 0
            self.y += self.row
            self.row = 0
        if self.y + h2 > self.size:
            raise OverflowError
        r = (self.x + 1, self.y + 1, w, h)
        self.x += w2
        self.row = max(self.row, h2)
        return r


FACES = ("north", "south", "east", "west", "up", "down")


def face_dims(p, face):
    dx, dy, dz = (abs(p.to[i] - p.frm[i]) for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def build_texture(gid, parts, mark):
    for ppu in (12, 11, 10, 9, 8, 7, 6):
        img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        pk = Packer(TEX)
        uvs = {}
        try:
            jobs = []
            for idx, p in enumerate(parts):
                for f in FACES:
                    du, dv = face_dims(p, f)
                    w, h = max(2, math.ceil(du * ppu)), max(2, math.ceil(dv * ppu))
                    jobs.append((h, w, idx, f, du, dv))
            jobs.sort(key=lambda j: (-j[0], -j[1]))
            for h, w, idx, f, du, dv in jobs:
                r = pk.place(w, h)
                p = parts[idx]
                paint_face(img, r, p.mat, p.detail.get(f), f, (du, dv), mark, ppu, seed=idx * 31 + 5)
                x0, y0, ww, hh = r
                s = 16.0 / TEX
                uvs[(idx, f)] = [round(x0 * s, 5), round(y0 * s, 5), round((x0 + ww) * s, 5), round((y0 + hh) * s, 5)]
        except OverflowError:
            continue
        path = os.path.join(ROOT, "textures", "item", "gun", gid + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        return uvs, ppu
    raise RuntimeError("texture overflow for " + gid)


# ------------------------------------------------------------------ shape refinement
TUBES = {"barrel", "gas_tube", "buffer_tube", "buffer", "muzzle", "tube", "can", "can_cap", "ring_f", "ring_r",
         "obj_bell", "eyepiece", "bolt_knob", "gas_block", "obj_lens", "monopod"}
NO_CHAMFER = {"rail", "lens"}


def refine(parts):
    """Round off the box look: tubes become octagonal prisms (box + 45-degree twin around the
    barrel axis); chunky bodies get chamfered corners (two overlapping, slightly inset boxes)."""
    out = []
    for p in parts:
        dx, dy, dz = (p.to[i] - p.frm[i] for i in range(3))
        if p.name in TUBES and p.rot is None and abs(dx - dy) < 0.35 and p.mat not in ("glass",):
            out.append(p)
            cx, cy, cz = ((p.frm[i] + p.to[i]) / 2 for i in range(3))
            s = (dx + dy) / 2 * 0.98
            twin = Part([cx - s / 2, cy - s / 2, p.frm[2]], [cx + s / 2, cy + s / 2, p.to[2]], p.mat, dict(p.detail),
                        {"angle": 45, "axis": "z", "origin": [cx, cy, cz]}, p.group, p.name)
            out.append(twin)
            continue
        details = set(p.detail.values())
        if dx >= 1.3 and dy >= 1.3 and dz >= 1.0 and p.mat not in ("glass", "red", "brass") and not (details & NO_CHAMFER):
            k = min(0.22, dx * 0.12, dy * 0.12)
            a = Part([p.frm[0] + k, p.frm[1], p.frm[2]], [p.to[0] - k, p.to[1], p.to[2]], p.mat, dict(p.detail), p.rot, p.group, p.name)
            b = Part([p.frm[0], p.frm[1] + k, p.frm[2] + 0.001], [p.to[0], p.to[1] - k, p.to[2] - 0.001], p.mat, dict(p.detail), p.rot, p.group, p.name)
            out += [a, b]
            continue
        out.append(p)
    return out


def element(p, idx, uvs, tex_ref):
    e = {"from": [round(8 + (c - 8) * GEOM, 4) for c in p.frm], "to": [round(8 + (c - 8) * GEOM, 4) for c in p.to],
         "faces": {f: {"uv": uvs[(idx, f)], "texture": tex_ref} for f in FACES}}
    if p.rot:
        e["rotation"] = {"angle": p.rot["angle"], "axis": p.rot["axis"], "origin": [round(8 + (c - 8) * GEOM, 4) for c in p.rot["origin"]]}
    if p.name:
        e["name"] = p.name
    return e


def display(length, is_pistol):
    k = 1 / GEOM
    sc = lambda v: [round(v * k, 4)] * 3
    gui = min(1.15, 15.5 / (length * GEOM)) if not is_pistol else 1.05
    grip = (8, 6.5, 12.3) if not is_pistol else (8, 6.5, 10.6)
    s3 = 0.55
    t3 = [0, round(-(grip[1] - 8) * s3, 3), round(-(grip[2] - 8) * s3, 3)]
    return {
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": sc(0.45)},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": sc(0.45)},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": t3, "scale": sc(s3)},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": t3, "scale": sc(s3)},
        "gui": {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": [round(gui, 4)] * 3},
        "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": sc(0.4)},
        "fixed": {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": sc(0.6)},
        "head": {"rotation": [0, 90, 0], "translation": [0, 6, 0], "scale": sc(0.8)},
    }


def gen():
    sights = {}
    for gid, fn in GUNS.items():
        spec = fn()
        base = refine(spec["parts"])
        optics = GUN_OPTICS[gid]
        muzzle = next(p for p in base if p.name == "muzzle")
        all_parts = list(base)
        for o in optics:
            all_parts += refine(optic(o))
        if gid != "sg12":
            all_parts += refine(suppressor(muzzle))
        uvs, ppu = build_texture(gid, all_parts, spec["mark"])
        tex_ref = "#gun"
        textures = {"gun": f"warfront:item/gun/{gid}", "particle": f"warfront:item/gun/{gid}"}
        zs = [c for p in base for c in (p.frm[2], p.to[2])]
        disp = display(max(zs) - min(zs), gid == "p12")
        index = {id(p): i for i, p in enumerate(all_parts)}

        def model(parts):
            return {"textures": textures, "elements": [element(p, index[id(p)], uvs, tex_ref) for p in parts], "display": disp, "gui_light": "front"}

        overrides = []
        for o in optics:
            for sup in (False, True):
                if gid == "sg12" and sup:
                    continue
                for nomag in (False, True):
                    parts = [p for p in base if (p.group != "iron_front" or o == "iron_sights") and (not nomag or p.group != "mag")]
                    parts = [p for p in parts if not (sup and p.group == "muzzle" and gid != "p12")]
                    parts += optic_parts(all_parts, o)
                    if sup:
                        parts += [p for p in all_parts if p.group == "sup"]
                    name = f"{gid}_{o}" + ("_suppressed" if sup else "") + ("_nomag" if nomag else "")
                    write(os.path.join(ROOT, "models", "item", "gun", name + ".json"), model(parts))
                    pred = {"warfront:optic": OPTIC_ORDINAL[o] / 10.0}
                    if sup:
                        pred["warfront:suppressed"] = 1
                    if nomag:
                        pred["warfront:nomag"] = 1
                    overrides.append({"predicate": pred, "model": f"warfront:item/gun/{name}"})
        # base model = first optic, full gun
        first = optics[0]
        parts = [p for p in base if p.group != "iron_front" or first == "iron_sights"] + optic_parts(all_parts, first)
        m = model(parts)
        m["overrides"] = overrides
        write(os.path.join(ROOT, "models", "item", gid + ".json"), m)
        write(os.path.join(ROOT, "models", "item", "gun", gid + "_mag.json"), model([p for p in base if p.group == "mag"]))
        sights[gid] = spec["sight"]
        print(f"{gid}: {len(all_parts)} parts, texture ppu={ppu}, {len(overrides)} variants")
    write(os.path.join(os.path.dirname(__file__), "gun_sights.json"), sights)


def optic_parts(all_parts, name):
    """The optic parts for `name` as they appear (same objects) in all_parts."""
    names = {p.name for p in optic(name)}
    return [p for p in all_parts if p.group == "optic" and p.name in names]


if __name__ == "__main__":
    gen()
