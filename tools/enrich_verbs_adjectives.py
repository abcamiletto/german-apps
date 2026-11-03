"""Consolidate raw verb and adjective entries into data/verbs_adjectives.json with forms, glosses and image briefs."""
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from enrich_nouns import LEVELS, slug, sort_key
from llm import structured
from wordfreq import zipf_frequency

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/enrich_va"
BATCH = 40
CATEGORIES = {
    "verb": [
        "movement", "daily_routine", "household_cooking", "communication", "thinking_feeling", "work_study",
        "money_shopping", "travel_transport", "social_relations", "change_state", "physical_actions",
        "modal_auxiliary", "abstract",
    ],
    "adjective": [
        "size_shape", "colour", "appearance", "character", "feelings_state", "evaluation", "time_frequency",
        "weather_temperature", "food_taste", "money_value", "body_health", "origin_nationality", "abstract",
    ],
}
# Spotted by comparing against a rule-based scan of the lists; the LLM pass dropped these listed headwords.
RECOVERED = [
    ("adjective", "stündlich", "B1"), ("adjective", "wöchentlich", "B1"),
    ("adjective", "allein", "A1"), ("adjective", "schade", "A2"),
]

PROMPT = """You are an expert German lexicographer preparing a vocabulary app for an English-speaking learner.
Below is a JSON list of raw {kind} entries extracted from the Goethe-Institut A1, A2 and B1 word lists.
Return one output object for EVERY input entry, with `source` copied exactly from the input's `lemma`.

Rules:
- `lemma`: the correct base form{lemma_rule}. Fix parsing mistakes. Keep `lemma` unchanged if it is already right.
- `drop`: true only if the entry isn't a real {kind} (a parsing fragment, or a phrase rather than one word that
  is better learned as another entry, such as "erlaubt sein"). "geboren werden" stays as it is.
- `alternatives`: other accepted spellings of the same word (from the input, or the Swiss ss/ß variant is NOT needed).
{forms_rule}
- `gloss_en`: a short natural English translation{gloss_rule}. Two meanings at most.
- `category`: the single best category.
- `image_brief`: ONE sentence in English describing a single flat illustration from which a learner could guess
  this exact word without any words visible. Be concrete and specific.
{image_rule}
  * Never rely on text, letters or numerals unless a numeral is inherent to the thing (a clock face, a coin).
  * Keep everything non-graphic and suitable for all ages: no blood, gore, nudity, weapons pointed at people.
    For dark words, use a gentle symbol.

Input:
{items}
"""

RULES = {
    "verb": dict(
        lemma_rule=": the infinitive, without \"sich\"",
        forms_rule=(
            "- `reflexive`: true only if the verb is normally used with \"sich\" and can't stand alone in this meaning\n"
            "  (sich beeilen, sich freuen, sich erholen). False for verbs where it's optional (waschen, anziehen).\n"
            "- `forms`: third person singular present, simple past and perfect with its auxiliary, as\n"
            "  \"fährt, fuhr, ist gefahren\". Separable verbs split: \"fährt ab, fuhr ab, ist abgefahren\". Reflexive verbs\n"
            "  include sich: \"freut sich, freute sich, hat sich gefreut\". Trust `printed_forms` when present. When a form\n"
            "  isn't printed, supply the correct standard one."
        ),
        gloss_rule=" starting with \"to\" (\"to drive\")",
        image_rule=(
            "  * Show one person, or a hand, animal or object where natural, caught in the middle of the action, with only\n"
            "    the props needed to recognise it. State the person's look so the drawing is specific.\n"
            "  * Distinguish close neighbours: gehen is an easy walk, laufen is jogging, rennen is a flat-out sprint; sagen is\n"
            "    a person with a single speech bubble, sprechen shows a mouth with sound, reden is two people chatting,\n"
            "    erzählen is someone telling a story to listeners, fragen has a question mark in a bubble, antworten has a\n"
            "    tick in a bubble; wissen is a head with a glowing bulb, kennen is two people recognising each other.\n"
            "  * For modal, auxiliary and abstract verbs, choose one clear conventional metaphor: müssen is a person pushed\n"
            "    along by a big arrow, können is a person flexing with a star, dürfen is a person passing a green tick gate,\n"
            "    wollen is a person reaching for a star, sollen is a pointing finger giving directions to a person.\n"
        ).rstrip(),
    ),
    "adjective": dict(
        lemma_rule=" with no ending",
        forms_rule=(
            "- `reflexive`: always false.\n"
            "- `forms`: the comparative and superlative, as \"größer, am größten\". Use \"\" if the adjective isn't normally\n"
            "  compared (tot, schwanger, verheiratet, deutsch, colours are compared rarely, so use \"\" for them)."
        ),
        gloss_rule=" with no article (\"big, large\")",
        image_rule=(
            "  * Show the quality through ONE clear example, drawn so the quality is unmistakable: heiß is a glowing red\n"
            "    kettle with steam, schwer is a person straining under a huge weight, müde is a yawning person with drooping eyes.\n"
            "  * If the quality only makes sense by comparison, draw the thing that has the quality large and in full colour,\n"
            "    with its opposite smaller and in pale grey beside it: groß is a big elephant next to a small grey mouse.\n"
            "  * Colours are a paint splash of exactly that colour. Nationalities are a flag on a pennant or a small\n"
            "    typical object with the flag.\n"
            "  * For evaluative and abstract words, choose one clear conventional metaphor that separates the word from close\n"
            "    neighbours in meaning.\n"
        ).rstrip(),
    ),
}


def schema(kind: str) -> dict:
    return {
        "type": "object",
        "required": ["words"],
        "properties": {
            "words": {
                "type": "array",
                "items": {
                    "type": "object",
                    "required": [
                        "source", "lemma", "drop", "reflexive", "alternatives", "forms", "gloss_en", "category", "image_brief",
                    ],
                    "properties": {
                        "source": {"type": "string"},
                        "lemma": {"type": "string"},
                        "drop": {"type": "boolean"},
                        "reflexive": {"type": "boolean"},
                        "alternatives": {"type": "array", "items": {"type": "string"}},
                        "forms": {"type": "string"},
                        "gloss_en": {"type": "string"},
                        "category": {"type": "string", "enum": CATEGORIES[kind]},
                        "image_brief": {"type": "string"},
                    },
                },
            }
        },
    }


def consolidate(rows):
    merged = {}
    for row in rows:
        entry = merged.setdefault(
            (row["kind"], row["lemma"]),
            {"kind": row["kind"], "lemma": row["lemma"], "printed": set(), "alternatives": set(), "levels": set(), "regions": set()},
        )
        entry["levels"].add(row["level"])
        entry["regions"].add(row["region"])
        entry["alternatives"] |= set(row["alternatives"])
        if row["printed_forms"]:
            entry["printed"].add(row["printed_forms"])
    return sorted(merged.values(), key=lambda e: (e["kind"], sort_key(e["lemma"])))


def run(batch):
    kind = batch[0]["kind"]
    payload = json.dumps(
        [{"lemma": e["lemma"], "printed_forms": sorted(e["printed"]), "alternatives": sorted(e["alternatives"])} for e in batch],
        ensure_ascii=False,
        indent=0,
    )
    prompt = PROMPT.format(kind=kind, items=payload, **RULES[kind])
    path = CACHE / f"{hashlib.sha256(prompt.encode()).hexdigest()[:24]}.json"
    if not path.exists():
        result = structured(prompt, schema(kind))
        path.write_text(json.dumps(result, ensure_ascii=False))
        print("batch", kind, batch[0]["lemma"], len(result["words"]), flush=True)
    return json.loads(path.read_text())["words"]


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    rows = json.loads((ROOT.parent / "data/raw_verbs_adjectives.json").read_text())
    for kind, lemma, level in RECOVERED:
        rows.append(dict(kind=kind, lemma=lemma, printed_forms="", reflexive=False, region="", alternatives=[], level=level))
    entries = consolidate(rows)
    batches = [
        group[i : i + BATCH]
        for kind in CATEGORIES
        for group in [[e for e in entries if e["kind"] == kind]]
        for i in range(0, len(group), BATCH)
    ]
    with ThreadPoolExecutor(10) as pool:
        results = list(pool.map(run, batches))

    final, seen, lost = [], set(), []
    for batch, words in zip(batches, results):
        by_source = {w["source"]: w for w in words}
        for entry in batch:
            word = by_source.get(entry["lemma"])
            if word is None:
                lost.append(entry["lemma"])
                continue
            if word["drop"]:
                continue
            kind = entry["kind"]
            word_id = slug("verb" if kind == "verb" else "adj", word["lemma"], "")
            if word_id in seen:
                continue
            seen.add(word_id)
            reflexive = kind == "verb" and word["reflexive"]
            alternatives = set(word["alternatives"]) | entry["alternatives"]
            final.append(
                dict(
                    id=word_id,
                    kind=kind,
                    lemma=f"sich {word['lemma']}" if reflexive else word["lemma"],
                    base=word["lemma"],
                    forms=word["forms"],
                    printed=sorted(entry["printed"]),
                    alternatives=sorted(alternatives - {word["lemma"]}),
                    level=next(l for l in LEVELS if l in entry["levels"]),
                    region="" if "" in entry["regions"] else sorted(entry["regions"])[0],
                    gloss_en=word["gloss_en"],
                    category=word["category"],
                    image_brief=word["image_brief"],
                    frequency=round(zipf_frequency(word["lemma"], "de"), 2),
                )
            )
    (ROOT.parent / "data/verbs_adjectives.json").write_text(json.dumps(final, ensure_ascii=False, indent=1))
    print(len(final), "words;", "lost:", lost)


if __name__ == "__main__":
    main()
