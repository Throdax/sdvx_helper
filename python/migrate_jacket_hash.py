#!/usr/bin/env python3
"""
migrate_jacket_hash.py -- Migrates jacket image hashes in musiclist.xml from
aHash (size 10 = 25-char hex, or size 8 = 16-char hex) to pHash-16 (64-char hex),
and renames the corresponding jacket PNG files to match the new hash values.

Run from the SDVX Helper Java installation root (where resources/ and jackets/ live):

    python migrate_jacket_hash.py [--music-list resources/musiclist.xml]
                                   [--jackets-dir jackets/]
                                   [--dry-run]

Options:
    --music-list PATH   Path to musiclist.xml (default: resources/musiclist.xml)
    --jackets-dir PATH  Path to the jackets folder (default: jackets/)
    --dry-run           Compute hashes in memory only; no files are modified.
                        Prints a collision and loss report at the end.

The pHash-16 algorithm (hash_size=16, highfreq_factor=4) mirrors the Java
PerceptualHasher.jacketHash() implementation exactly so that hashes produced here
are byte-for-byte identical to those the Java application will compute at runtime.
"""

import argparse
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

try:
    import imagehash
    from PIL import Image
except ImportError:
    print(
        "ERROR: 'imagehash' and 'Pillow' are required.\n"
        "Install with: pip install imagehash Pillow",
        file=sys.stderr,
    )
    sys.exit(1)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

_OLD_HASH_RE = re.compile(r'^[0-9a-f]{16}$|^[0-9a-f]{25}$')
_NEW_HASH_LENGTH = 64

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _compute_phash16(image_path):
    """
    Compute pHash-16 for the image at image_path.

    Uses hash_size=16 and highfreq_factor=4, matching Java's
    PerceptualHasher.jacketHash() which resizes to hash_size * 4 = 64 pixels
    before the DCT step.  Returns a 64-character lowercase hex string.
    """
    img = Image.open(image_path).convert('RGB')
    result = str(imagehash.phash(img, hash_size=16, highfreq_factor=4))
    if len(result) != _NEW_HASH_LENGTH:
        raise ValueError(
            f"Unexpected hash length {len(result)} for '{image_path}' "
            f"(expected {_NEW_HASH_LENGTH})"
        )
    return result


def _detect_old_hash_size(music_list_path):
    """
    Return the detected aHash size (8 or 10) by inspecting the first old-style
    jacketGroup entry in musiclist.xml, or None if no old-style entries are found.

    Hash length 25 corresponds to aHash size 10.
    Hash length 16 corresponds to aHash size 8.
    """
    tree = ET.parse(music_list_path)
    for entry in tree.getroot().iter('entry'):
        h = entry.get('hash', '')
        if len(h) == 25:
            return 10
        if len(h) == 16:
            return 8
    return None


def _build_reverse_index(root):
    """
    Build and return a dict mapping each old-style hash to the list of
    (title, difficulty, hashes_parent_element, entry_element) tuples that
    reference it in the jacketGroup sections of musiclist.xml.

    Only entries whose hash length matches a known old-style format are included.
    """
    index = {}
    for group in root.findall('jacketGroup'):
        difficulty = group.get('difficulty', '')
        hashes_elem = group.find('hashes')
        if hashes_elem is None:
            continue
        for entry in hashes_elem.findall('entry'):
            h = entry.get('hash', '')
            if _OLD_HASH_RE.match(h):
                index.setdefault(h, []).append(
                    (entry.get('title', ''), difficulty, hashes_elem, entry)
                )
    return index


def _write_xml(tree, path):
    """Write an ElementTree to path with UTF-8 encoding and an XML declaration."""
    ET.indent(tree.getroot(), space='    ')
    tree.write(path, encoding='utf-8', xml_declaration=True)
    print(f"  Written: {path}")


# ---------------------------------------------------------------------------
# Normal migration
# ---------------------------------------------------------------------------

def _backup_files(music_list_path, jackets_dir, detected_size):
    """
    Phase A: back up musiclist.xml and all old-style jacket PNGs before any
    changes are made.  Returns the number of jacket files backed up.
    """
    music_list_dir = os.path.dirname(os.path.abspath(music_list_path))
    backup_xml = os.path.join(music_list_dir, f'musiclist.{detected_size}.xml')
    print(f"  musiclist.xml -> musiclist.{detected_size}.xml")
    shutil.copy2(music_list_path, backup_xml)

    pre_migration_dir = os.path.join(jackets_dir, 'jackets_pre_migration')
    os.makedirs(pre_migration_dir, exist_ok=True)

    backed_up = 0
    for filename in os.listdir(jackets_dir):
        stem, ext = os.path.splitext(filename)
        if ext.lower() == '.png' and _OLD_HASH_RE.match(stem):
            src = os.path.join(jackets_dir, filename)
            dst = os.path.join(pre_migration_dir, filename)
            if not os.path.exists(dst):
                shutil.copy2(src, dst)
            backed_up += 1

    print(f"  Backed up {backed_up} jacket(s) to jackets_pre_migration/")
    return backed_up


def run_migration(music_list_path, jackets_dir):
    """Perform the full migration: backup, rehash, rename files, update and save XML."""
    print(f"\n=== SDVX Helper: Jacket Hash Migration ===")
    print(f"  Music list : {os.path.abspath(music_list_path)}")
    print(f"  Jackets dir: {os.path.abspath(jackets_dir)}")

    detected_size = _detect_old_hash_size(music_list_path)
    if detected_size is None:
        print("\nNo old-style jacket hashes found in musiclist.xml. Nothing to do.")
        return

    old_char_count = 25 if detected_size == 10 else 16
    print(f"\nDetected hash format: aHash-{detected_size} ({old_char_count}-char hex)")

    # Phase A -- backup
    print("\n[Phase A] Backing up original files...")
    backed_up = _backup_files(music_list_path, jackets_dir, detected_size)

    # Phase B -- migrate
    print("\n[Phase B] Migrating hashes and renaming jacket files...")
    tree = ET.parse(music_list_path)
    root = tree.getroot()
    reverse_index = _build_reverse_index(root)

    migrated = 0
    merged = 0
    lost = 0

    # Track oldHash -> newHash to avoid re-hashing the same file when multiple
    # difficulty entries share the same jacket (e.g., NOV and ADV same image).
    processed = {}

    for i, (old_hash, entries) in enumerate(reverse_index.items(), start=1):
        old_file = os.path.join(jackets_dir, f'{old_hash}.png')
        label = f"{entries[0][0]} -- {entries[0][1]}"

        if not os.path.exists(old_file):
            print(f"  [{i}/{len(reverse_index)}] LOST  : {old_hash}  ({label})")
            for _title, _diff, hashes_parent, entry_elem in entries:
                hashes_parent.remove(entry_elem)
            lost += 1
            continue

        if old_hash not in processed:
            new_hash = _compute_phash16(old_file)
            processed[old_hash] = new_hash
        else:
            new_hash = processed[old_hash]

        new_file = os.path.join(jackets_dir, f'{new_hash}.png')

        if not os.path.exists(new_file):
            os.rename(old_file, new_file)
            print(f"  [{i}/{len(reverse_index)}] OK    : {old_hash[:12]}... -> {new_hash[:12]}...  ({label})")
            migrated += 1
        else:
            # The new-name file already exists because a previous old hash produced
            # the same pHash-16 (i.e., the two old images are visually identical).
            # The old file is a safe-to-remove duplicate since it is already backed up.
            os.remove(old_file)
            print(f"  [{i}/{len(reverse_index)}] MERGE : {old_hash[:12]}... -> {new_hash[:12]}...  (collision, duplicate removed)")
            merged += 1

        for _title, _diff, _hashes_parent, entry_elem in entries:
            entry_elem.set('hash', new_hash)

    # Phase C -- write updated XML
    print("\n[Phase C] Writing updated musiclist.xml...")
    _write_xml(tree, music_list_path)

    print(f"\n=== Migration complete ===")
    print(f"  Backed up  : {backed_up}")
    print(f"  Migrated   : {migrated}")
    print(f"  Merged     : {merged}  (collision -- duplicate old file removed)")
    print(f"  Lost       : {lost}  (no matching .png, entry removed from XML)")


# ---------------------------------------------------------------------------
# Dry-run
# ---------------------------------------------------------------------------

def run_dry_run(music_list_path, jackets_dir):
    """
    Compute pHash-16 for all old-style jacket entries in memory only.
    No file is created, renamed, deleted, or modified.  Prints a collision and
    loss report so the migration can be evaluated before committing to it.
    """
    print(f"\n=== SDVX Helper: Jacket Hash Migration (DRY RUN) ===")
    print(f"  Music list : {os.path.abspath(music_list_path)}")
    print(f"  Jackets dir: {os.path.abspath(jackets_dir)}")

    detected_size = _detect_old_hash_size(music_list_path)
    if detected_size is None:
        print("\nNo old-style jacket hashes found in musiclist.xml. Nothing to migrate.")
        return

    old_char_count = 25 if detected_size == 10 else 16
    print(f"\nDetected hash format: aHash-{detected_size} ({old_char_count}-char hex)")

    tree = ET.parse(music_list_path)
    root = tree.getroot()
    reverse_index = _build_reverse_index(root)
    total_entries = sum(len(v) for v in reverse_index.values())

    print(f"\nHashing {len(reverse_index)} unique old jacket files (this may take a moment)...")

    # forward_map[newHash] = [(oldHash, title, diff), ...]
    forward_map = {}
    lost_list = []
    processed = {}

    for i, (old_hash, entries) in enumerate(reverse_index.items(), start=1):
        old_file = os.path.join(jackets_dir, f'{old_hash}.png')

        if not os.path.exists(old_file):
            for title, diff, _hashes_parent, _entry_elem in entries:
                lost_list.append((old_hash, title, diff))
            continue

        if old_hash not in processed:
            if i % 100 == 0:
                print(f"  Progress: {i} / {len(reverse_index)}", flush=True)
            new_hash = _compute_phash16(old_file)
            processed[old_hash] = new_hash
        else:
            new_hash = processed[old_hash]

        for title, diff, _hashes_parent, _entry_elem in entries:
            forward_map.setdefault(new_hash, []).append((old_hash, title, diff))

    # Collisions: new hashes that multiple distinct old hashes map to
    collisions = {
        nh: items
        for nh, items in forward_map.items()
        if len({old for old, _t, _d in items}) > 1
    }
    merged_away = sum(
        len({old for old, _t, _d in items}) - 1
        for items in collisions.values()
    )

    # Report
    print(f"\n=== DRY-RUN REPORT ===")

    if collisions:
        print(f"\nCOLLISIONS (multiple old hashes -> same new pHash-16 hash):")
        for new_hash, items in sorted(collisions.items()):
            print(f"\n  new: {new_hash}")
            seen_old = set()
            for old_hash, title, diff in items:
                if old_hash not in seen_old:
                    print(f"    old: {old_hash}  ({title} -- {diff})")
                    seen_old.add(old_hash)
            print(f"    -> only 1 file will remain after migration")
    else:
        print(f"\nCOLLISIONS: none")

    if lost_list:
        print(f"\nLOST (no matching .png file in jackets/):")
        for old_hash, title, diff in lost_list:
            print(f"  {old_hash}  ({title} -- {diff})")
    else:
        print(f"\nLOST: none")

    would_migrate = len(reverse_index) - len(lost_list)
    unique_new = len(forward_map)

    print(f"\nSUMMARY:")
    print(f"  Total entries in musiclist.xml : {total_entries}")
    print(f"  Unique old hash files          : {len(reverse_index)}")
    print(f"  Would be migrated              : {would_migrate}")
    print(f"  Would be lost (no file)        : {len(lost_list)}")
    if collisions:
        print(f"  Collisions (multi -> 1)        : {len(collisions)}  ({merged_away} duplicate file(s) would be removed)")
    else:
        print(f"  Collisions (multi -> 1)        : 0")
    print(f"  Unique new hashes              : {unique_new}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Migrate SDVX Helper jacket hashes from aHash to pHash-16.'
    )
    parser.add_argument(
        '--music-list', '-m',
        default=os.path.join('resources', 'musiclist.xml'),
        help='Path to musiclist.xml (default: resources/musiclist.xml)',
    )
    parser.add_argument(
        '--jackets-dir', '-j',
        default='jackets',
        help='Path to the jackets folder (default: jackets/)',
    )
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help=(
            'Compute hashes in memory only; no files are modified. '
            'Prints a collision and loss report.'
        ),
    )
    args = parser.parse_args()

    if not os.path.exists(args.music_list):
        print(f"ERROR: musiclist.xml not found at '{args.music_list}'", file=sys.stderr)
        sys.exit(1)
    if not os.path.isdir(args.jackets_dir):
        print(f"ERROR: jackets directory not found at '{args.jackets_dir}'", file=sys.stderr)
        sys.exit(1)

    if args.dry_run:
        run_dry_run(args.music_list, args.jackets_dir)
    else:
        run_migration(args.music_list, args.jackets_dir)


if __name__ == '__main__':
    main()
