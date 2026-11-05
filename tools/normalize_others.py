"""Pull every headword that isn't a noun, verb or adjective out of the raw Goethe list text, one chunk at a time.

Writes data/raw_others.json. Safe to re-run: finished chunks are cached in .cache/normalize_others/.
"""
import hashlib
import json
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import structured
from normalize_nouns import chunks

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/normalize_others"
KINDS = ["adverb", "pronoun", "preposition", "conjunction", "article", "number", "particle", "question", "expression", "name"]

PROMPT = """You are building a German vocabulary dataset from the official Goethe-Institut {level} word list.
Below is a chunk of its text, extracted from a PDF with columns preserved. It contains headwords, often with example
sentences in a second column, and thematic lists such as numbers, greetings and countries.

Return EVERY entry that the list presents as vocabulary and that is NOT a common noun, a verb or an adjective:
- `adverb`: gern, oft, sehr, heute, hier, immer, vielleicht, leider, dort, draußen, deshalb
- `pronoun`: ich, du, mich, mir, man, jemand, nichts, alle, dieser, mein, unser, sich, einander
- `preposition`: in, auf, mit, wegen, zwischen, bis. Also its fixed contractions if they are listed: im, zum, zur
- `conjunction`: und, oder, aber, weil, dass, obwohl, wenn, sondern, entweder ... oder
- `article`: der, die, das, ein, eine, kein
- `number`: eins, zwei, zwanzig, hundert, erste, zweite, halb, Viertel-style fractions when they are not nouns
- `particle`: ja, nein, nicht, doch, mal, denn, eben, zu (as in "zu spät")
- `question`: wer, was, wann, wo, wohin, woher, warum, wie, welcher
- `expression`: greetings and set phrases printed as entries: hallo, danke, bitte, tschüs, Guten Tag, Auf Wiedersehen
- `name`: countries, cities and continents used without an article: Deutschland, Europa, Wien

Fields:
- `lemma`: the headword. Fix PDF artefacts in the spelling. "A/B" variants give the commonest spelling in Germany
  as the lemma and the rest in `alternatives`. An entry listing several separate words ("mir, mich") gives one row each.
- `kind`: one of the kinds above.
- `printed`: exactly what the list prints with the headword beyond the word itself, such as "+ Dativ", or "".
- `region`: "A", "CH" or "A,CH" if it is marked as used only in Austria and/or Switzerland. "" otherwise.
- `alternatives`: other spellings, otherwise an empty list.

Do NOT return: common nouns, verbs, adjectives, abbreviations (z. B., usw., ICE), measurement symbols, words that
appear only inside example sentences or instructions, or section titles. Never invent words which are not listed.
Adjectives used as adverbs (schnell, gut) are adjectives, so leave them out.
If the chunk has no such entries, return an empty list.

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
                "required": ["lemma", "kind", "printed", "region", "alternatives"],
                "properties": {
                    "lemma": {"type": "string"},
                    "kind": {"type": "string", "enum": KINDS},
                    "printed": {"type": "string"},
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
    (ROOT.parent / "data/raw_others.json").write_text(json.dumps(rows, ensure_ascii=False, indent=0))
    print(len(rows), "entries", len({r["lemma"] for r in rows}), "unique", file=sys.stderr)


if __name__ == "__main__":
    main()
