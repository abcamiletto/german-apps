"""Extract article+noun candidates from `pdftotext -layout` dumps of the Goethe A1/A2/B1 lists.

Usage: uv run extract_nouns.py <dir-with-txt> <out.json>
"""
import json
import re
import sys
from pathlib import Path

LISTS = {
    "A1": "A1_SD1_Wortliste_02.txt",
    "A2": "Goethe-Zertifikat_A2_Wortliste.txt",
    "B1": "Goethe-Zertifikat_B1_Wortliste.txt",
}
NOUN = r"[A-ZÄÖÜ][a-zäöüßA-ZÄÖÜ-]+"
PLURAL = r"(?P<plural>¨?-(?:[a-zäöüß]+)?|¨)"
ENTRY = re.compile(
    rf"^(?P<article>der|die|das)\s+(?P<noun>{NOUN})"
    rf"(?:\s*,\s*{PLURAL})?"
    r"(?P<tail>\s*(?:\((?P<note>[^)]*)\))?.*)$"
)
GROUPED = re.compile(rf"^(?P<noun>{NOUN})(?:\s*,\s*{PLURAL})(?P<tail>\s*(?:\((?P<note>[^)]*)\))?\s*)$")


def segments(line: str):
    """A layout line holds up to three columns separated by runs of spaces."""
    return [s.strip() for s in re.split(r"\s{2,}|\t", line.strip()) if s.strip()]


def plausible_tail(tail: str) -> bool:
    """Reject wrapped example sentences such as `der Zug fährt um acht`."""
    tail = tail.strip()
    if not tail:
        return True
    if tail[0] in "(→;/":
        return True
    # A1 prints `die Flasche, -n Eine Flasche Bier, bitte.` with a single space.
    return bool(re.match(r"[A-ZÄÖÜ]", tail))


def parse(level: str, path: Path):
    entries = []
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        for segment in segments(raw):
            match = ENTRY.match(segment)
            if match and plausible_tail(match["tail"]):
                entries.append(
                    dict(
                        level=level,
                        article=match["article"],
                        noun=match["noun"],
                        plural=match["plural"],
                        note=match["note"],
                        line=number,
                        raw=segment[:90],
                    )
                )
    return entries


def main(source: str, out: str):
    root = Path(source)
    rows = [row for level, name in LISTS.items() for row in parse(level, root / name)]
    Path(out).write_text(json.dumps(rows, ensure_ascii=False, indent=0))
    print(len(rows), "candidates", len({(r["article"], r["noun"]) for r in rows}), "unique")


if __name__ == "__main__":
    main(*sys.argv[1:])
