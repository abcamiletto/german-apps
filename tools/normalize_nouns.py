"""Turn the raw Goethe list text into clean noun entries, one chunk at a time.

Writes data/raw_entries.json. Safe to re-run: finished chunks are cached in .cache/normalize/.
"""
import hashlib
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import structured

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/normalize"
LISTS = {
    "A1": "A1_SD1_Wortliste_02.txt",
    "A2": "Goethe-Zertifikat_A2_Wortliste.txt",
    "B1": "Goethe-Zertifikat_B1_Wortliste.txt",
}
CHUNK_LINES = 220
OVERLAP = 12

PROMPT = """You are building a German vocabulary dataset from the official Goethe-Institut {level} word list.
Below is a chunk of its text, extracted from a PDF with columns preserved. It contains headwords and, often,
example sentences in a second column. Line wraps can split headwords (e.g. "die Geschwindigkeits-" then
"begrenzung" means die Geschwindigkeitsbegrenzung). Thematic word-group sections sometimes omit the article.

Return EVERY noun that the list presents as a vocabulary entry, including sub-entries and word-group entries:
- Use the article printed in the list. If the list omits it, supply the correct standard German article.
- A slash pair such as "der Arzt, ¨-e / die Ärztin, -nen" gives two entries.
- "die Grundschule/Realschule/" gives one entry per noun.
- Spelling variants of one word ("die Soße/Sauce", "das Müesli/Müsli") give one entry using the most common
  spelling in Germany, with the rest in `alternatives`.
- `plural`: the full plural noun without an article ("Ärzte", "Kinder"), derived from the list's notation
  (", ¨-e", ", -n"). Use your German knowledge when the list prints no plural but one exists. Use null if the
  noun has no usual plural.
- `plural_only`: true for words like "die Eltern", "die Leute". For these the article is "die" and `noun` is
  the plural form, with `plural` null.
- `region`: "A", "CH" or "A,CH" if the entry is marked as used only in Austria and/or Switzerland (for example
  "(A)", "(CH)", "(A, CH)"). Use "" if it is standard in Germany, including "(D)" and "(D, A)".
- Fix PDF artefacts in the spelling. Never invent words which are not listed.

Do NOT return: nouns that appear only inside example sentences or instructions, verbs, adjectives, adverbs,
country names without an article (Deutschland, Frankreich), or cities and people's names. Country names that
take an article (die Schweiz, die USA, die Türkei) must be returned.
If the chunk is a preface or instructions with no vocabulary entries, return an empty list.

--- CHUNK ({level}, lines {start}-{end}) ---
{text}
"""

SCHEMA = {
    "type": "object",
        "required": ["entries"],
    "properties": {
        "entries": {
            "type": "array",
            "items": {
                "type": "object",
                                "required": ["article", "noun", "plural", "plural_only", "region", "alternatives"],
                "properties": {
                    "article": {"type": "string", "enum": ["der", "die", "das"]},
                    "noun": {"type": "string"},
                    "plural": {"type": ["string", "null"]},
                    "plural_only": {"type": "boolean"},
                    "region": {"type": "string", "enum": ["", "A", "CH", "A,CH"]},
                    "alternatives": {"type": "array", "items": {"type": "string"}},
                },
            },
        }
    },
}


def chunks():
    for level, name in LISTS.items():
        lines = (ROOT / "source_lists" / name).read_text().splitlines()
        for start in range(0, len(lines), CHUNK_LINES - OVERLAP):
            end = min(start + CHUNK_LINES, len(lines))
            yield level, start, end, "\n".join(lines[start:end])


def run(chunk):
    level, start, end, text = chunk
    key = hashlib.sha256((PROMPT + level + text).encode()).hexdigest()[:20]
    path = CACHE / f"{level}-{start:05d}-{key}.json"
    if path.exists():
        return level, json.loads(path.read_text())["entries"]
    result = structured(PROMPT.format(level=level, start=start, end=end, text=text), SCHEMA)
    path.write_text(json.dumps(result, ensure_ascii=False))
    print(level, start, len(result["entries"]), flush=True)
    return level, result["entries"]


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(10) as pool:
        results = list(pool.map(run, chunks()))
    rows = [{**entry, "level": level} for level, entries in results for entry in entries]
    out = ROOT.parent / "data/raw_entries.json"
    out.write_text(json.dumps(rows, ensure_ascii=False, indent=0))
    print(len(rows), "entries", len({(r["article"], r["noun"]) for r in rows}), "unique", file=sys.stderr)


if __name__ == "__main__":
    main()
