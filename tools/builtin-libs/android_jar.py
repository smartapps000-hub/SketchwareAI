#!/usr/bin/env python3
"""Writes app/src/main/assets/libs/android.jar.zip: the compile platform projects are built against on the phone.

v1.9.0: the android.jar of platform android-37.0 (Android 17) with the public API that Android 17's SDK dropped and
Sketchware projects call from add-source blocks put back, copied from android-34's android.jar (compile-time stubs, as
in the SDK):
  - android.hardware.fingerprint.FingerprintManager and its nested classes (deprecated since API 28, gone in API 37)
  - android.content.Context.FINGERPRINT_SERVICE
Fingerprint code that compiled in v1.8.13 (API 34) still compiles. Other API 34 members API 37 dropped are not put
back (Thread.suspend/resume/destroy/countStackFrames/stop(Throwable), Notification.WearableExtender.get/setBackground,
CarrierConfigManager.KEY_HIDE_ENABLE_2G, MediaDrm's two annotation types; engineering/architecture-state.md lists the
diff). Whether the fingerprint calls work on Android 17 depends on the phone, as it did for an app compiled against API 34.

usage: android_jar.py <android-37.0 android.jar> <android-34 android.jar> <out android.jar.zip>
The inner android.jar keeps the base jar's entry order, names, dates and stored entries; the added classes are stored
the same way. The outer zip has one deflated entry dated 2024-01-01 (as before).
"""
import io
import struct
import sys
import zipfile

RESTORE_CLASSES = "android/hardware/fingerprint/"
RESTORE_FIELDS = [("android/content/Context.class", "FINGERPRINT_SERVICE")]


def read_pool(data):
    """(entries, end offset): entries[i] = (tag, raw bytes incl. tag) for constant pool index i."""
    count = struct.unpack(">H", data[8:10])[0]
    pool = [None] * count
    off, i = 10, 1
    while i < count:
        tag = data[off]
        if tag == 1:
            length = struct.unpack(">H", data[off + 1:off + 3])[0]
            size = 3 + length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            size = 5
        elif tag in (5, 6):
            size = 9
        elif tag in (7, 8, 16, 19, 20):
            size = 3
        elif tag == 15:
            size = 4
        else:
            raise ValueError("constant pool tag %d" % tag)
        pool[i] = (tag, data[off:off + size])
        off += size
        i += 2 if tag in (5, 6) else 1
    return pool, off


def utf8(pool, index):
    tag, raw = pool[index]
    assert tag == 1
    return raw[3:].decode("utf-8")


def fields(data, pool, end):
    """[(start, end, access, name, descriptor, constant string or None)] of the class's fields, and the fields offset."""
    off = end + 6
    interfaces = struct.unpack(">H", data[off:off + 2])[0]
    off += 2 + 2 * interfaces
    fields_at = off
    count = struct.unpack(">H", data[off:off + 2])[0]
    off += 2
    out = []
    for _ in range(count):
        start = off
        access, name, desc, attrs = struct.unpack(">HHHH", data[off:off + 8])
        off += 8
        constant = None
        for _ in range(attrs):
            attr_name, length = struct.unpack(">HI", data[off:off + 6])
            if utf8(pool, attr_name) == "ConstantValue":
                value = struct.unpack(">H", data[off + 6:off + 8])[0]
                tag, raw = pool[value]
                if tag == 8:
                    constant = utf8(pool, struct.unpack(">H", raw[1:3])[0])
            off += 6 + length
        out.append((start, off, access, utf8(pool, name), utf8(pool, desc), constant))
    return out, fields_at


def add_string_field(data, name, descriptor, access, value):
    """The class with one more field `name` (a String constant), new constant pool entries appended."""
    pool, end = read_pool(data)
    existing, fields_at = fields(data, pool, end)
    if any(f[3] == name for f in existing):
        return data
    count = len(pool)
    extra = b""

    def find_or_add_utf8(text):
        nonlocal extra, count
        for i, entry in enumerate(pool):
            if entry and entry[0] == 1 and entry[1][3:] == text.encode("utf-8"):
                return i
        raw = text.encode("utf-8")
        extra += b"\x01" + struct.pack(">H", len(raw)) + raw
        pool.append((1, b"\x01" + struct.pack(">H", len(raw)) + raw))
        count += 1
        return count - 1

    name_index = find_or_add_utf8(name)
    desc_index = find_or_add_utf8(descriptor)
    attr_index = find_or_add_utf8("ConstantValue")
    value_utf8 = find_or_add_utf8(value)
    extra += b"\x08" + struct.pack(">H", value_utf8)
    pool.append((8, b"\x08" + struct.pack(">H", value_utf8)))
    count += 1
    string_index = count - 1
    field = struct.pack(">HHHH", access, name_index, desc_index, 1) + struct.pack(">HIH", attr_index, 2, string_index)
    field_count = struct.unpack(">H", data[fields_at:fields_at + 2])[0]
    out = (data[:8] + struct.pack(">H", count) + data[10:end] + extra + data[end:fields_at]
           + struct.pack(">H", field_count + 1) + field + data[fields_at + 2:])
    # check: the new class reads back with the field
    pool2, end2 = read_pool(out)
    after, _ = fields(out, pool2, end2)
    assert [f for f in after if f[3] == name][0][5] == value
    assert len(after) == len(existing) + 1
    return out


def main():
    base_path, old_path, out_path = sys.argv[1:4]
    base = zipfile.ZipFile(base_path)
    old = zipfile.ZipFile(old_path)
    base_names = set(base.namelist())
    restored_fields = {}
    for class_name, field in RESTORE_FIELDS:
        old_data = old.read(class_name)
        pool, end = read_pool(old_data)
        found = [f for f in fields(old_data, pool, end)[0] if f[3] == field]
        assert found and found[0][5] is not None, field
        restored_fields.setdefault(class_name, []).append(found[0])
    added = []
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_STORED) as out:
        for info in base.infolist():
            data = base.read(info)
            for f in restored_fields.get(info.filename, []):
                data = add_string_field(data, f[3], f[4], f[2], f[5])
            entry = zipfile.ZipInfo(info.filename, info.date_time)
            entry.compress_type = info.compress_type
            entry.external_attr = info.external_attr
            entry.create_system = info.create_system
            out.writestr(entry, data)
        stamp = base.infolist()[-1].date_time
        for info in old.infolist():
            if info.filename.startswith(RESTORE_CLASSES) and info.filename not in base_names:
                entry = zipfile.ZipInfo(info.filename, stamp)
                entry.compress_type = zipfile.ZIP_STORED
                entry.external_attr = info.external_attr
                out.writestr(entry, old.read(info))
                added.append(info.filename)
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as outer:
        entry = zipfile.ZipInfo("android.jar", (2024, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = 0o100644 << 16
        entry.create_system = 3
        outer.writestr(entry, jar.getvalue())
    print("android.jar %d bytes; classes added: %s; fields added: %s" % (
        len(jar.getvalue()), ", ".join(added), ", ".join(c + "." + f[3] for c, fs in restored_fields.items() for f in fs)))


if __name__ == "__main__":
    main()
