#!/usr/bin/env python3
"""Writes the GameTest arena structure (a 32x12x32 stone-floored box) as gzipped NBT."""
import gzip
import os
import struct

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "warfront", "structure")


def tag_string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def named(t, name, payload):
    return bytes([t]) + tag_string(name) + payload


def int_(v):
    return struct.pack(">i", v)


def compound(items):
    return b"".join(items) + b"\x00"


def list_(elem_type, payloads):
    return bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def int_list(vals):
    return list_(3, [int_(v) for v in vals])


def write_arena(name, sx, sy, sz):
    palette = [compound([named(8, "Name", tag_string("minecraft:smooth_stone"))]),
               compound([named(8, "Name", tag_string("minecraft:air"))])]
    blocks = []
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                wall = x in (0, sx - 1) or z in (0, sz - 1)
                blocks.append(compound([named(9, "pos", int_list([x, y, z])), named(3, "state", int_(0 if y == 0 or wall else 1))]))
    root = compound([
        named(3, "DataVersion", int_(3955)),
        named(9, "size", int_list([sx, sy, sz])),
        named(9, "palette", list_(10, palette)),
        named(9, "blocks", list_(10, blocks)),
        named(9, "entities", list_(10, [])),
    ])
    data = named(10, "", root)
    os.makedirs(OUT, exist_ok=True)
    with gzip.open(os.path.join(OUT, name + ".nbt"), "wb") as f:
        f.write(data)
    print("wrote", name)


if __name__ == "__main__":
    write_arena("arena", 32, 12, 32)
