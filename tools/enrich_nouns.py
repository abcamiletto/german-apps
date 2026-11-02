"""Consolidate raw entries into data/nouns.json with translations, categories and image briefs."""
import hashlib
import json
import re
import unicodedata
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import structured
from wordfreq import zipf_frequency

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/enrich"
BATCH = 50
LEVELS = ["A1", "A2", "B1"]
CATEGORIES = [
    "people", "family", "jobs", "body_health", "food_drink", "home", "clothing", "city_places", "transport",
    "travel", "nature_animals", "weather_seasons", "time_calendar", "school_learning", "work_business",
    "money_shopping", "technology_media", "culture_leisure", "sport", "society_politics", "countries_languages",
    "feelings_thoughts", "communication", "measures_quantities", "objects_tools", "abstract",
]
# Spotted by comparing against the rule-based extraction; the LLM pass dropped these listed headwords.
RECOVERED = [
    ("die", "Droge", "B1"), ("die", "Drogerie", "B1"), ("die", "Wohngemeinschaft", "B1"),
    ("der", "Personenkraftwagen", "B1"), ("das", "Untergeschoss", "B1"), ("die", "Fantasie", "B1"),
]

PROMPT = """You are an expert German lexicographer preparing a vocabulary app for an English-speaking learner.
Below is a JSON list of raw noun entries extracted from the Goethe-Institut A1, A2 and B1 word lists.
Entries are sorted, so duplicates and variants are adjacent. Return one output object for EVERY distinct real word.

Rules:
- Correct parsing mistakes: nominative singular ("der Angestellte", never "der Angestellter"), proper spelling.
- Merge variants of the same word and meaning. The same word with two accepted genders (der/das Laptop,
  die/das Cola) becomes ONE object: use the most common German article and list all in `accepted_articles`.
- Words that differ in meaning by article stay separate objects (der See / die See, der Leiter / die Leiter).
- Same article and word but different meaning and different plural (die Bank "bench"/"bank") get two objects
  with a distinct `sense`. Otherwise `sense` is "". Use one object for ordinary polysemy.
- Set `drop` true only for fragments which aren't real nouns. Set `plural_only` true only for true pluralia
  tantum such as die Eltern; "die Tropfen" and "die Daten" should follow their real usage.
- `plural`: the full plural word, or null if none is in normal use.
- `gloss_en`: a short natural English translation with its article ("the dog"). Two meanings at most.
- `category`: the single best category.
- `image_brief`: ONE sentence in English describing a single flat illustration that a learner could connect to
  this word without any words visible. Be concrete and specific.
  * For concrete things, depict the thing itself, isolated. Don't add a scene.
  * For people and jobs, show one person with the defining attribute and state their gender explicitly when the
    word is gendered (der Lehrer: a man; die Lehrerin: a woman).
  * For abstract words, choose one clear, conventional visual metaphor. It must distinguish this word from close
    neighbours in meaning.
  * For countries and nationalities, use the country's silhouette or flag, and for languages, a speech bubble
    holding that flag. Weekdays and months use a calendar page with the relevant cell highlighted.
  * Never rely on text, letters, or numerals unless a numeral is inherent to the thing (a clock face, a coin).
  * Keep everything non-graphic and suitable for all ages: no blood, gore, nudity, weapons pointed at people.

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
                "required": [
                    "article", "noun", "sense", "drop", "accepted_articles", "alternatives", "plural", "plural_only",
                    "gloss_en", "category", "image_brief",
                ],
                "properties": {
                    "article": {"type": "string", "enum": ["der", "die", "das"]},
                    "noun": {"type": "string"},
                    "sense": {"type": "string"},
                    "drop": {"type": "boolean"},
                    "accepted_articles": {"type": "array", "items": {"type": "string", "enum": ["der", "die", "das"]}},
                    "alternatives": {"type": "array", "items": {"type": "string"}},
                    "plural": {"type": ["string", "null"]},
                    "plural_only": {"type": "boolean"},
                    "gloss_en": {"type": "string"},
                    "category": {"type": "string", "enum": CATEGORIES},
                    "image_brief": {"type": "string"},
                },
            },
        }
    },
}


def sort_key(noun: str) -> str:
    return unicodedata.normalize("NFKD", noun.casefold())


def consolidate(rows):
    merged = {}
    for row in rows:
        entry = merged.setdefault(
            (row["article"], row["noun"]),
            {**row, "plurals": set(), "alternatives": set(row["alternatives"]), "levels": set()},
        )
        entry["levels"].add(row["level"])
        entry["alternatives"] |= set(row["alternatives"])
        if row["plural"]:
            entry["plurals"].add(row["plural"])
        entry["region"] = entry["region"] if entry["region"] == row["region"] else ""
    return sorted(merged.values(), key=lambda e: (sort_key(e["noun"]), e["article"]))


def slim(entry):
    return {
        "article": entry["article"],
        "noun": entry["noun"],
        "plurals": sorted(entry["plurals"]),
        "plural_only": entry["plural_only"],
        "region": entry["region"],
        "alternatives": sorted(entry["alternatives"]),
    }


def run(batch):
    payload = json.dumps([slim(e) for e in batch], ensure_ascii=False, indent=0)
    key = hashlib.sha256((PROMPT + payload).encode()).hexdigest()[:24]
    path = CACHE / f"{key}.json"
    if not path.exists():
        result = structured(PROMPT.format(items=payload), SCHEMA)
        path.write_text(json.dumps(result, ensure_ascii=False))
        print("batch", batch[0]["noun"], len(result["words"]), flush=True)
    return json.loads(path.read_text())["words"]


def slug(article: str, noun: str, sense: str) -> str:
    text = f"{article}_{noun}" + (f"_{sense}" if sense else "")
    text = text.replace("ß", "ss").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
    text = text.replace("Ä", "ae").replace("Ö", "oe").replace("Ü", "ue")
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "_", text).strip("_")


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    rows = json.loads((ROOT.parent / "data/raw_entries.json").read_text())
    for article, noun, level in RECOVERED:
        rows.append(dict(article=article, noun=noun, plural=None, plural_only=False, region="", alternatives=[], level=level))
    entries = consolidate(rows)
    batches = [entries[i : i + BATCH] for i in range(0, len(entries), BATCH)]
    with ThreadPoolExecutor(10) as pool:
        words = [word for result in pool.map(run, batches) for word in result if not word["drop"]]

    # Source metadata lookup: an output word inherits level and region from its raw inputs.
    lookup = {}
    for entry in entries:
        for form in {entry["noun"], *entry["alternatives"]}:
            lookup.setdefault(form, []).append(entry)

    final, seen = [], set()
    for word in words:
        sources = [e for form in {word["noun"], *word["alternatives"]} for e in lookup.get(form, [])]
        same_article = [e for e in sources if e["article"] in {word["article"], *word["accepted_articles"]}]
        sources = same_article or sources
        word_id = slug(word["article"], word["noun"], word["sense"])
        if word_id in seen:
            continue
        seen.add(word_id)
        levels = {level for e in sources for level in e["levels"]}
        regions = {e["region"] for e in sources} or {""}
        final.append(
            dict(
                id=word_id,
                article=word["article"],
                noun=word["noun"],
                sense=word["sense"],
                plural=None if word["plural_only"] else word["plural"],
                plural_only=word["plural_only"],
                accepted_articles=sorted({word["article"], *word["accepted_articles"]}),
                alternatives=sorted(set(word["alternatives"]) - {word["noun"]}),
                level=next((l for l in LEVELS if l in levels), "UNMATCHED"),
                region="" if "" in regions else sorted(regions)[0],
                gloss_en=word["gloss_en"],
                category=word["category"],
                image_brief=word["image_brief"],
                frequency=round(zipf_frequency(word["noun"], "de"), 2),
            )
        )
    final.sort(key=lambda w: (w["region"] != "", LEVELS.index(w["level"]) if w["level"] in LEVELS else 9, -w["frequency"], w["noun"]))
    (ROOT.parent / "data/nouns.json").write_text(json.dumps(final, ensure_ascii=False, indent=1))
    print(len(final), "words")


if __name__ == "__main__":
    main()
