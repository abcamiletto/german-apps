"""Consolidate the words that aren't nouns, verbs or adjectives into data/others.json, with glosses and image briefs."""
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from enrich_nouns import LEVELS, slug
from llm import structured
from wordfreq import zipf_frequency

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/enrich_others"
BATCH = 40
CATEGORIES = [
    "pronoun", "article", "preposition", "conjunction", "question", "number", "particle", "expression", "name",
    "adverb_time", "adverb_place", "adverb_degree", "adverb_manner", "adverb_connecting",
]
# Essentials the Goethe lists take for granted, found by comparing them with the commonest words in DeReWo.
ESSENTIALS = {
    "A1": [
        "du", "mich", "mir", "uns", "euch", "ihnen", "Ihnen", "sein", "ihr", "im", "am", "zum", "zur", "vom", "beim", "ins",
        "null", "wen", "wem", "sechste", "siebte", "achte", "neunte", "zehnte", "fünfte", "dies",
    ],
    "A2": ["ans", "aufs", "dazu", "davon", "darauf", "darüber", "daran", "darin", "darum", "dadurch"],
    "B1": ["jener", "sowie", "gar", "dennoch", "zuvor", "wobei"],
}
# The same word as an existing entry, so that entry's gloss takes in this sense instead.
SAME_WORD = {"allein", "eigentlich", "gerade", "halb", "natürlich", "ungefähr", "willkommen", "drittel", "viertel", "Verzeihung"}
# Number patterns that only show how to read a figure, and PDF fragments.
SKIPPED = {"hunderteins", "zweihundert", "zweitausendeins", "da(r)", "aller-", "erst-"}

# Variants the model kept apart, merged into the first word.
MERGED = {
    "selbst": ["selber"], "nirgends": ["nirgendwo"], "noch mal": ["nochmals"], "samstags": ["sonnabends"],
    "so viel": ["soviel"], "heraus": ["raus"], "herein": [], "ein bisschen": ["bisschen"], "derselbe": ["selbe"],
}
# Corrections after reading through the result.
FIXES = {
    "es": {"forms": "es, ihm"},
    "sie": {"forms": "sie, ihr, ihnen"},
    "Sie": {"forms": "Sie, Ihnen"},
    "wir": {"forms": "uns"},
    "ihr": {"gloss_en": "you (plural, informal), her", "forms": "euch, ihr"},
    "sich": {"forms": "reflexive"},
    "der": {"forms": "den, dem, des"},
    "die": {"forms": "der, den"},
    "das": {"forms": "dem, des"},
    "ein": {"forms": "eine"},
    "kein": {"forms": "keine"},
    "mein": {"forms": "meine"},
    "dein": {"forms": "deine"},
    "sein": {"forms": "seine"},
    "unser": {"forms": "unsere"},
    "euer": {"forms": "eure"},
    "irgendein": {"forms": "irgendeine"},
    "was für ein": {"forms": "was für eine"},
    "manche": {"forms": "mancher, manches"},
    "meiste": {"forms": ""},
    "sämtliche": {"forms": ""},
    "zum Beispiel": {"forms": ""},
    "dieser": {"alternatives": []},
    "drin": {"alternatives": []},
    "lange": {"alternatives": []},
    "ein bisschen": {"level": "A1"},
    "übermorgen": {"gloss_en": "the day after tomorrow"},
    "vorgestern": {"gloss_en": "the day before yesterday"},
    "wen": {"gloss_en": "whom (accusative)", "forms": "accusative of wer", "heard_as": ["wenn"]},
    "wem": {"gloss_en": "to whom (dative)", "forms": "dative of wer"},
    "quer": {"image_brief": "A person walking diagonally across a short zebra crossing, with a bright arrow showing the diagonal path."},
    "Deutschland": {"image_brief": "A pennant flag with horizontal stripes of black, red and gold."},
    "im Freien": {"image_brief": "A person sitting cross-legged on a picnic blanket next to one small tree, with no scenery behind them."},
    "fast": {"image_brief": "A navy battery icon almost fully charged, with crisp solid sage green segments and one empty segment."},
    "manche": {"image_brief": "Six small coins in a row, of which two are bright yellow and the rest grey, with nothing behind them."},
}
PROMPT = """You are an expert German lexicographer preparing a vocabulary app for an English-speaking learner.
Below is a JSON list of raw entries extracted from the Goethe-Institut A1, A2 and B1 word lists: pronouns, articles,
prepositions, conjunctions, question words, numbers, particles, set expressions, names and adverbs.
Return one output object for EVERY input entry, with `source` copied exactly from the input's `lemma`.

Rules:
- `lemma`: the word as a learner would say it. Stems take their base form: "dein-" is "dein", "ander-" is "andere",
  "jed-" is "jeder", "welch-" is "welcher", "ein-" is "ein", "kein-" is "kein", "dies-" is "dieser", "all-" is "alle",
  "einig-" is "einige", "manch-" is "manche", "solch-" is "solcher", "meist-" is "meiste", "beid-" is "beide",
  "selb-" is "selbe". Gendered variants become the masculine: "jede" and "jedes" are "jeder", "welche" is "welcher",
  "dieselbe" and "dasselbe" are "derselbe". Keep "Sie" capitalised for formal you. Keep "der", "die" and "das" as articles.
  Remove "!" and similar punctuation. Paired conjunctions keep an ellipsis: "entweder ... oder".
- `alternatives`: other accepted spellings a learner may type: "tschüs" for "tschüss", "gerne" for "gern". No inflected forms.
- `heard_as`: what a German speech recogniser may write when it hears this word alone, in lowercase: homophones ("das"
  for "dass", "mann" for "man", "seid" for "seit", "wen" for "wenn", "meer" for "mehr", "dir" for "der"), and figures
  for numbers ("7" for "sieben", "21" for "einundzwanzig", "1" for "eins" and "ein", "1." for "erste"). Empty if none.
- `forms`: what the word does to its neighbours or how it changes, in English, very short:
  prepositions give their case: "+ dative", "+ accusative", "+ accusative or dative", "+ genitive".
  Personal pronouns give their other cases: "ihn, ihm" for "er", "mich, mir" for "ich". An object form names its role:
  "accusative of ich" for "mich", "dative of ich" for "mir". Articles and determiners give the three genders, such
  as "jede, jedes" for "jeder". Contractions give the full form: "in dem" for "im". Otherwise "".
- `gloss_en`: a short natural English translation, two meanings at most, and it must tell the word apart from similar
  ones without saying the German. Examples: "him (accusative)" for "ihn", "him, to him (dative)" for "ihm", "you (informal)"
  for "du", "you (formal)" for "Sie", "you (plural, informal)" and "her, their" for "ihr", "the (masculine)" for "der",
  "the (feminine, plural)" for "die", "the (neuter)" for "das", "in the (dative)" for "im", "into the" for "ins",
  "because (verb goes last)" for "weil", "because (normal word order)" for "denn". Give "sein" as the possessive "his, its".
  Put the commonest meaning first, and never start with "to".
- `category`: the single best category. Time adverbs include "abends" and "heute". "adverb_connecting" is for words like
  "deshalb", "trotzdem" and "außerdem". "adverb_degree" is for words like "sehr" and "fast".
- `image_brief`: ONE sentence in English describing a single flat illustration that helps a learner recall this word
  without any words visible. Be concrete and specific. Never rely on text or letters.
  * Prepositions and place adverbs show a small coral ball placed relative to a teal box, or another clear spatial
    scene: "auf" is a ball on top of a box, "unter" is below it. Time prepositions use a clock or calendar.
  * Pronouns use simple people with a pointing gesture: "ich" is a person pointing at themselves, "du" is a hand pointing
    at one person, "wir" is a small group with arms round each other, "mich" is an arrow landing on a person.
    Possessives show a person hugging an object.
  * Conjunctions use a clear metaphor with two objects: "und" is an apple and a pear side by side joined by a band,
    "oder" is a person choosing between two paths, "aber" is a smooth road ending at a barrier, "weil" is a rain cloud
    with an arrow pointing to an umbrella.
  * Numbers show exactly that many identical objects in a tidy arrangement, or for large numbers one large numeral.
    Digits are allowed for numbers only.
  * Time adverbs show a clock, sun, moon or calendar page with one highlighted day. Question words show a question-mark
    shape beside the thing asked about: a clock for "wann", a map pin for "wo".
  * Names show a simple flag on a pennant, or a map silhouette.
  * Keep everything non-graphic and suitable for all ages.

Input:
{items}
"""

SCHEMA = {
    "type": "object",
    "required": ["words"],
    "properties": {
        "words": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["source", "lemma", "alternatives", "heard_as", "forms", "gloss_en", "category", "image_brief"],
                "properties": {
                    "source": {"type": "string"},
                    "lemma": {"type": "string"},
                    "alternatives": {"type": "array", "items": {"type": "string"}},
                    "heard_as": {"type": "array", "items": {"type": "string"}},
                    "forms": {"type": "string"},
                    "gloss_en": {"type": "string"},
                    "category": {"type": "string", "enum": CATEGORIES},
                    "image_brief": {"type": "string"},
                },
            },
        }
    },
}


def run(batch: list[dict]) -> list[dict]:
    items = json.dumps(batch, ensure_ascii=False)
    key = hashlib.sha256((PROMPT + items).encode()).hexdigest()[:20]
    path = CACHE / f"{key}.json"
    if path.exists():
        return json.loads(path.read_text())["words"]
    result = structured(PROMPT.format(items=items), SCHEMA)
    missing = {b["lemma"] for b in batch} - {w["source"] for w in result["words"]}
    if missing:
        raise RuntimeError(f"Dropped by the model: {missing}")
    path.write_text(json.dumps(result, ensure_ascii=False))
    return result["words"]


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    raw = json.loads((ROOT.parent / "data/raw_others.json").read_text())
    raw += [
        {"lemma": lemma, "kind": "", "printed": "", "region": "", "alternatives": [], "level": level}
        for level, lemmas in ESSENTIALS.items()
        for lemma in lemmas
    ]
    sources: dict[str, dict] = {}
    for row in sorted(raw, key=lambda r: LEVELS.index(r["level"])):
        if row["lemma"] not in SAME_WORD | SKIPPED:
            sources.setdefault(row["lemma"], row)
    items = [
        {"lemma": r["lemma"], "kind": r["kind"], "printed": r["printed"], "alternatives": r["alternatives"]}
        for r in sources.values()
    ]
    with ThreadPoolExecutor(10) as pool:
        enriched = [w for part in pool.map(run, [items[i : i + BATCH] for i in range(0, len(items), BATCH)]) for w in part]

    words: dict[str, dict] = {}
    for word in sorted(enriched, key=lambda w: LEVELS.index(sources[w["source"]]["level"])):
        source = sources.get(word["source"])
        if source is None:
            continue
        kept = words.get(word["lemma"])
        if kept:
            # Variants of one word merge, and the earliest level wins because of the sort.
            kept["alternatives"] = sorted({*kept["alternatives"], *word["alternatives"]} - {kept["lemma"]})
            continue
        words[word["lemma"]] = {
            "id": slug("other", word["lemma"], "formal" if word["lemma"] in ("Sie", "Ihnen") else ""),
            "kind": "other",
            "lemma": word["lemma"],
            "forms": word["forms"],
            "alternatives": [a for a in word["alternatives"] if a != word["lemma"]],
            "heard_as": [h.lower() for h in word["heard_as"]],
            "level": source["level"],
            "region": source["region"],
            "gloss_en": word["gloss_en"],
            "category": word["category"],
            "image_brief": word["image_brief"],
            "frequency": zipf_frequency(word["lemma"].replace("...", " ").replace("…", " "), "de"),
        }
    for lemma, merged in MERGED.items():
        for other in merged:
            removed = words.pop(other)
            kept = words[lemma]
            kept["alternatives"] = sorted({*kept["alternatives"], other, *removed["alternatives"]} - {lemma})
            kept["heard_as"] = sorted({*kept["heard_as"], *removed["heard_as"]})
    for lemma, fix in FIXES.items():
        words[lemma].update(fix)

    rows = sorted(words.values(), key=lambda w: w["id"])
    ids = [w["id"] for w in rows]
    if len(ids) != len(set(ids)):
        raise RuntimeError(f"Duplicate ids: {sorted({i for i in ids if ids.count(i) > 1})}")
    (ROOT.parent / "data/others.json").write_text(json.dumps(rows, ensure_ascii=False, indent=0))
    print(len(rows), "words")


if __name__ == "__main__":
    main()
