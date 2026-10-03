#!/usr/bin/env python3
"""
reconcile_alllog.py -- Reconciles the original alllog.pkl with the current
alllog.xml, restoring dates to all historical plays and appending any new
Java-recorded plays.

Strategy:
  1. Load all plays from alllog.pkl (Python/pickle format; has dates).
  2. Load ONLY plays that already have a `date` attribute from alllog.xml
     (these are new Java-recorded plays that postdate the last Python session).
  3. Combine: pickle plays (dates converted to Java format) + new Java plays.
  4. Sort chronologically and write alllog_merged.xml.

Usage (run from the python/ directory):
  python reconcile_alllog.py
  python reconcile_alllog.py --pkl path/to/alllog.pkl --xml path/to/alllog.xml --out path/to/alllog_merged.xml
"""

import sys
import os
import argparse
import pickle
from datetime import datetime
from xml.dom import minidom
import xml.etree.ElementTree as ET


# ---------------------------------------------------------------------------
# Minimal stub for OnePlayData so pickle can deserialise without importing
# the full sdvxh_classes module (which requires PIL, scipy, etc.)
# ---------------------------------------------------------------------------

class _OnePlayDataStub:
    """Receives whatever attributes pickle.load sets via __dict__ update."""
    pass


class _StubUnpickler(pickle.Unpickler):
    """Redirects any class from sdvxh_classes to _OnePlayDataStub."""

    _STUB_MODULES = {'sdvxh_classes', '__main__'}
    _STUB_NAMES   = {'OnePlayData'}

    def find_class(self, module, name):
        if module in self._STUB_MODULES and name in self._STUB_NAMES:
            return _OnePlayDataStub
        return super().find_class(module, name)

PYTHON_DATE_FMT = "%Y%m%d_%H%M%S"
JAVA_DATE_FMT   = "%Y-%m-%d %H:%M:%S"


# ---------------------------------------------------------------------------
# Date helpers
# ---------------------------------------------------------------------------

def _parse_python_date(date_str):
    """Parse a Python yyyyMMdd_HHmmss date string, return datetime or None."""
    if not date_str or str(date_str) == 'None':
        return None
    try:
        return datetime.strptime(str(date_str), PYTHON_DATE_FMT)
    except ValueError:
        return None


def _parse_java_date(date_str):
    """Parse a Java yyyy-MM-dd HH:mm:ss date string, return datetime or None."""
    if not date_str:
        return None
    try:
        return datetime.strptime(str(date_str), JAVA_DATE_FMT)
    except ValueError:
        return None


def _fmt(dt):
    """Format a datetime as yyyy-MM-dd HH:mm:ss for the output XML, or None."""
    if dt is None:
        return None
    return dt.strftime(JAVA_DATE_FMT)


# ---------------------------------------------------------------------------
# Loaders
# ---------------------------------------------------------------------------

def load_pkl_plays(pkl_path):
    """
    Load all OnePlayData records from the pickle file using a stub unpickler
    that does not require sdvxh_classes or any of its heavy dependencies.
    Returns a list of dicts ready for XML serialisation.
    """
    with open(pkl_path, 'rb') as f:
        plays = _StubUnpickler(f).load()

    result = []
    missing_dates = 0
    for p in plays:
        raw_date = getattr(p, 'date', None)
        dt = _parse_python_date(raw_date)
        if dt is None:
            missing_dates += 1
        result.append({
            'title':      str(p.title),
            'curScore':   str(p.cur_score),
            'preScore':   str(p.pre_score),
            'lamp':       str(p.lamp),
            'difficulty': str(p.difficulty),
            'date':       _fmt(dt),
            'dt':         dt,
        })

    print(f"  Loaded {len(result)} plays from pickle "
          f"({len(result) - missing_dates} with dates, {missing_dates} without).")
    return result


def load_xml_new_plays(xml_path):
    """
    Load only plays that HAVE a `date` attribute from alllog.xml.
    Plays without a `date` attribute are old Python-era entries already present
    in the pickle, so they are intentionally skipped.
    Returns a list of dicts ready for XML serialisation.
    """
    tree = ET.parse(xml_path)
    root = tree.getroot()

    result = []
    skipped = 0
    for elem in root.findall('play'):
        date_str = elem.get('date')
        if not date_str:
            skipped += 1
            continue
        dt = _parse_java_date(date_str)
        result.append({
            'title':      elem.get('title', ''),
            'curScore':   elem.get('curScore', '0'),
            'preScore':   elem.get('preScore', '0'),
            'lamp':       elem.get('lamp', ''),
            'difficulty': elem.get('difficulty', ''),
            'date':       date_str,
            'dt':         dt,
        })

    print(f"  Loaded {len(result)} new Java plays from XML "
          f"(skipped {skipped} dateless entries already in pickle).")
    return result


# ---------------------------------------------------------------------------
# Writer
# ---------------------------------------------------------------------------

def write_xml(plays, out_path):
    """Serialise the play list to a pretty-printed XML file."""
    root = ET.Element('PlayLog')
    for p in plays:
        elem = ET.SubElement(root, 'play')
        elem.set('title',      p['title'])
        elem.set('curScore',   p['curScore'])
        elem.set('preScore',   p['preScore'])
        elem.set('lamp',       p['lamp'])
        elem.set('difficulty', p['difficulty'])
        if p['date']:
            elem.set('date', p['date'])

    rough    = ET.tostring(root, encoding='unicode')
    reparsed = minidom.parseString(rough)
    content  = reparsed.toprettyxml(indent="    ", encoding='UTF-8').decode('utf-8')
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(content)

    with_date    = sum(1 for p in plays if p['date'])
    without_date = len(plays) - with_date
    print(f"  Written: {out_path}")
    print(f"  Total plays : {len(plays)}")
    print(f"  With date   : {with_date}")
    print(f"  Without date: {without_date}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Reconcile alllog.pkl with alllog.xml, restoring dates to historical plays.'
    )
    parser.add_argument('--pkl', default='alllog.pkl',
                        help='Path to alllog.pkl (default: alllog.pkl in current directory)')
    parser.add_argument('--xml', default='alllog.xml',
                        help='Path to alllog.xml (default: alllog.xml in current directory)')
    parser.add_argument('--out', default='alllog_merged.xml',
                        help='Output path (default: alllog_merged.xml in current directory)')
    args = parser.parse_args()

    out_path = args.out
    if os.path.isdir(out_path):
        out_path = os.path.join(out_path, 'alllog_merged.xml')

    print('=== SDVX Helper: Reconcile alllog.pkl + alllog.xml ===\n')

    print(f'Loading pickle : {args.pkl}')
    pkl_plays = load_pkl_plays(args.pkl)

    print(f'\nLoading Java XML: {args.xml}')
    xml_new_plays = load_xml_new_plays(args.xml)

    # Merge: all pickle plays come first (base), then new Java plays
    all_plays = pkl_plays + xml_new_plays

    # Sort chronologically; plays with no date sort before everything else
    all_plays.sort(key=lambda p: p['dt'] if p['dt'] else datetime.min)

    print(f'\nMerged total: {len(all_plays)} plays '
          f'({len(pkl_plays)} from pickle + {len(xml_new_plays)} new Java plays)')

    print(f'\nWriting output: {out_path}')
    write_xml(all_plays, out_path)

    print('\n=== Done ===')
    print(f'Rename {out_path} to alllog.xml in your Java installation directory.')


if __name__ == '__main__':
    main()
