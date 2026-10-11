"""Minimal NBT writer for the empty GameTest structure (no external NBT library needed)."""
import gzip
import struct


def nbt_string(s):
    data = s.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def nbt_named(tag_type, name, payload):
    return struct.pack(">b", tag_type) + nbt_string(name) + payload


def nbt_int(v):
    return struct.pack(">i", v)


def nbt_list(elem_type, payloads):
    return struct.pack(">bi", elem_type, len(payloads)) + b"".join(payloads)


def nbt_compound(entries):
    return b"".join(entries) + b"\x00"


def empty_structure(size):
    palette_entry = nbt_compound([nbt_named(8, "Name", nbt_string("minecraft:air"))])
    root = nbt_compound([
        nbt_named(3, "DataVersion", nbt_int(3955)),
        nbt_named(9, "size", nbt_list(3, [nbt_int(v) for v in size])),
        nbt_named(9, "palette", nbt_list(10, [palette_entry])),
        nbt_named(9, "blocks", nbt_list(10, [])),
        nbt_named(9, "entities", nbt_list(10, [])),
    ])
    return struct.pack(">b", 10) + nbt_string("") + root


def write_empty_structure(path, size=(8, 5, 8)):
    # mtime=0 keeps the file byte-identical between runs.
    with open(path, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as f:
        f.write(empty_structure(list(size)))
