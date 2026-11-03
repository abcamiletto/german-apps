"""Pull the verb and adjective headwords out of the raw Goethe list text, one chunk at a time.

Writes data/raw_verbs_adjectives.json. Safe to re-run: finished chunks are cached in .cache/normalize_va/.
"""
import hashlib
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import structured
from normalize_nouns import chunks

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/normalize_va"

PROMPT = """You are building a German vocabulary dataset from the official Goethe-Institut {level} word list.
Below is a chunk of its text, extracted from a PDF with columns preserved. It contains headwords and, often,
example sentences in a second column. Line wraps can split a headword and its forms across lines, for example
"beantworten," / "beantwortet," / "hat beantwortet" is one verb.

Return EVERY verb and adjective that the list presents as a vocabulary entry, including word-group entries:
- `kind`: "verb" or "adjective".
- `lemma`: the infinitive, or the adjective's base form. No "sich". Fix PDF artefacts in the spelling.
  A list entry such as "eigen-" gives "eigen". "A/B" spelling variants give the most common spelling in Germany,
  with the rest in `alternatives`.
- `printed_forms`: the conjugation exactly as printed after the headword, such as "fährt, fuhr, ist gefahren" or
  "basteln, bastelt, hat gebastelt" without the infinitive. Use "" if the list prints none. Never invent forms here.
- `reflexive`: true only if the list prints "(sich)" or "sich" with the headword.
- `region`: "A", "CH" or "A,CH" if the entry is marked as used only in Austria and/or Switzerland. "" otherwise.

An adjective is a word that a German speaker would naturally put before a noun with an ending (ein großer Hund,
das besetzte Zimmer). Participles listed as their own entry count, such as "besetzt" or "verheiratet". Colours count.

Do NOT return: nouns, pure adverbs (gern, oft, sehr, heute, hier, immer, vielleicht, leider), pronouns, determiners,
prepositions, conjunctions, particles, numbers (eins, zwei, erste), greetings, or words that appear only inside
example sentences or instructions. Never invent words which are not listed.
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
                "required": ["kind", "lemma", "printed_forms", "reflexive", "region", "alternatives"],
                "properties": {
                    "kind": {"type": "string", "enum": ["verb", "adjective"]},
                    "lemma": {"type": "string"},
                    "printed_forms": {"type": "string"},
                    "reflexive": {"type": "boolean"},
                    "region": {"type": "string", "enum": ["", "A", "CH", "A,CH"]},
                    "alternatives": {"type": "array", "items": {"type": "string"}},
                },
            },
        }
    },
}


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
    out = ROOT.parent / "data/raw_verbs_adjectives.json"
    out.write_text(json.dumps(rows, ensure_ascii=False, indent=0))
    unique = {(r["kind"], r["lemma"]) for r in rows}
    print(len(rows), "entries", len(unique), "unique", file=sys.stderr)


if __name__ == "__main__":
    main()
