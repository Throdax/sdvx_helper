#!/usr/bin/env python3
"""
migrate_pkl_to_xml.py -- One-time migration script to convert Python pickle files
to the XML format consumed by the Java SDVX Helper application.

Run from within the `python/` directory:
    python migrate_pkl_to_xml.py [--output-dir OUT_DIR]

Output files (written to OUT_DIR, default is the current directory):
    alllog.xml              -- play history (replaces alllog.pkl)
    musiclist.xml           -- song/jacket database (replaces musiclist.pkl)
    rival_log.xml           -- rival scores (replaces out/rival_log.pkl)
    title_conv_table.xml    -- title conversion table (replaces resources/title_conv_table.pkl)
"""

import argparse
import os
import pickle
import sys
import xml.etree.ElementTree as ET
from xml.dom import minidom

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _pretty(elem: ET.Element, encoding: str = 'UTF-8') -> str:
    """Return a pretty-printed XML string for the element."""
    rough = ET.tostring(elem, encoding='unicode')
    reparsed = minidom.parseString(rough)
    return reparsed.toprettyxml(indent="    ", encoding=encoding).decode(encoding)


def _write(path: str, root: ET.Element) -> None:
    content = _pretty(root)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"  Written: {path}")


def _load_pkl(path: str):
    """Load and return a pickle file, or None if not found."""
    if not os.path.exists(path):
        print(f"  WARNING: {path} not found, skipping.")
        return None
    with open(path, 'rb') as f:
        return pickle.load(f)


# ---------------------------------------------------------------------------
# alllog.pkl -> alllog.xml
# ---------------------------------------------------------------------------

def migrate_alllog(pkl_path: str, out_path: str) -> None:
    """Convert alllog.pkl (List[OnePlayData]) to alllog.xml."""
    print(f"\nMigrating {pkl_path} -> {out_path}")
    data = _load_pkl(pkl_path)
    if data is None:
        return

    root = ET.Element('PlayLog')
    for play in data:
        elem = ET.SubElement(root, 'play')
        elem.set('title',      str(play.title))
        elem.set('curScore',   str(play.cur_score))
        elem.set('preScore',   str(play.pre_score))
        elem.set('lamp',       str(play.lamp))
        elem.set('difficulty', str(play.difficulty))
        elem.set('date',       str(play.date))
    _write(out_path, root)
    print(f"  Migrated {len(data)} play records.")


# ---------------------------------------------------------------------------
# musiclist.pkl -> musiclist.xml
# ---------------------------------------------------------------------------

def migrate_musiclist(pkl_path: str, out_path: str) -> None:
    """Convert musiclist.pkl to musiclist.xml."""
    print(f"\nMigrating {pkl_path} -> {out_path}")
    ml = _load_pkl(pkl_path)
    if ml is None:
        return

    root = ET.Element('MusicList')

    def _add_hash_groups(parent_tag: str, data_key: str):
        """Add <{parent_tag}> groups for each difficulty."""
        data = ml.get(data_key, {})
        for diff, title_map in data.items():
            group = ET.SubElement(root, parent_tag)
            group.set('difficulty', str(diff))
            hashes_elem = ET.SubElement(group, 'hashes')
            for title, hash_val in title_map.items():
                entry = ET.SubElement(hashes_elem, 'entry')
                entry.set('title', str(title))
                entry.set('hash',  str(hash_val))

    _add_hash_groups('jacketGroup',    'jacket')
    _add_hash_groups('infoGroup',      'info')
    _add_hash_groups('jacketShaGroup', 'jacket_sha')

    # Titles
    # Real structure in musiclist.pkl: [title(dup), artist, bpm, lvNov, lvAdv, lvExh, lvAppend]
    # Index 0 is a duplicate of the dict key and must be skipped.
    TITLE_FIELDS = [
        (1, 'artist'),
        (2, 'bpm'),
        (3, 'lvNov'),
        (4, 'lvAdv'),
        (5, 'lvExh'),
        (6, 'lvAppend'),
    ]
    for title, info_list in ml.get('titles', {}).items():
        song_elem = ET.SubElement(root, 'song')
        song_elem.set('title', str(title))
        info_elem = ET.SubElement(song_elem, 'info')
        for idx, field in TITLE_FIELDS:
            if idx < len(info_list) and info_list[idx] is not None:
                info_elem.set(field, str(info_list[idx]))

    # GradeS tables
    for key in ('gradeS_lv17', 'gradeS_lv18', 'gradeS_lv19'):
        table_data = ml.get(key, {})
        table_elem = ET.SubElement(root, 'gradeSTable')
        table_elem.set('levelKey', key.replace('gradeS_', ''))
        for title, tier in table_data.items():
            tier_elem = ET.SubElement(table_elem, 'tier')
            tier_elem.set('title', str(title))
            tier_elem.set('tier',  str(tier))

    _write(out_path, root)
    print(f"  Migrated musiclist (titles={len(ml.get('titles',{}))}).")


# ---------------------------------------------------------------------------
# rival_log.pkl -> rival_log.xml
# ---------------------------------------------------------------------------

def migrate_rival_log(pkl_path: str, out_path: str) -> None:
    """Convert rival_log.pkl (Dict[str, List[MusicInfo]]) to rival_log.xml."""
    print(f"\nMigrating {pkl_path} -> {out_path}")
    data = _load_pkl(pkl_path)
    if data is None:
        return

    root = ET.Element('RivalLog')
    for rival_name, scores in data.items():
        rival_elem = ET.SubElement(root, 'rival')
        rival_elem.set('name', str(rival_name))
        for minfo in scores:
            score_elem = ET.SubElement(rival_elem, 'score')
            score_elem.set('title',      str(getattr(minfo, 'title', '')))
            score_elem.set('artist',     str(getattr(minfo, 'artist', '')))
            score_elem.set('bpm',        str(getattr(minfo, 'bpm', '')))
            score_elem.set('difficulty', str(getattr(minfo, 'difficulty', '')))
            score_elem.set('lv',         str(getattr(minfo, 'lv', '')))
            score_elem.set('bestScore',  str(getattr(minfo, 'best_score', 0)))
            score_elem.set('bestLamp',   str(getattr(minfo, 'best_lamp', '')))
            score_elem.set('date',       str(getattr(minfo, 'date', '')))
    _write(out_path, root)
    rival_count = len(data)
    print(f"  Migrated {rival_count} rival(s).")


# ---------------------------------------------------------------------------
# title_conv_table.pkl -> title_conv_table.xml
# ---------------------------------------------------------------------------

def migrate_title_conv_table(pkl_path: str, out_path: str) -> None:
    """Convert title_conv_table.pkl (Dict[str, str]) to title_conv_table.xml."""
    print(f"\nMigrating {pkl_path} -> {out_path}")
    data = _load_pkl(pkl_path)
    if data is None:
        return

    root = ET.Element('TitleConversionTable')
    for local_title, maya2_title in data.items():
        entry = ET.SubElement(root, 'entry')
        entry.set('localTitle', str(local_title))
        entry.set('maya2Title', str(maya2_title))
    _write(out_path, root)
    print(f"  Migrated {len(data)} title mappings.")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description='Migrate SDVX Helper pickle files to XML.')
    parser.add_argument('--output-dir', '-o', default='.', help='Output directory (default: current directory)')
    args = parser.parse_args()

    out_dir = args.output_dir
    os.makedirs(out_dir, exist_ok=True)

    print('=== SDVX Helper: Pickle to XML Migration ===')
    print(f'Output directory: {os.path.abspath(out_dir)}')

    # Add current directory to sys.path so we can import domain classes
    sys.path.insert(0, os.path.dirname(__file__) or '.')

    migrate_alllog(
        pkl_path=os.path.join('resources', 'alllog.pkl'),
        out_path=os.path.join(out_dir, 'alllog.xml')
    )
    migrate_musiclist(
        pkl_path=os.path.join('resources', 'musiclist.pkl'),
        out_path=os.path.join(out_dir, 'musiclist.xml')
    )
    migrate_rival_log(
        pkl_path=os.path.join('out', 'rival_log.pkl'),
        out_path=os.path.join(out_dir, 'rival_log.xml')
    )
    migrate_title_conv_table(
        pkl_path=os.path.join('resources', 'title_conv_table.pkl'),
        out_path=os.path.join(out_dir, 'title_conv_table.xml')
    )

    print('\n=== Migration complete. ===')
    print('Copy the generated XML files to the Java application working directory.')


if __name__ == '__main__':
    main()
