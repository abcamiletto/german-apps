"""Hand-reviewed corrections applied after enrichment. Re-run after enrich_nouns.py."""
import json
from pathlib import Path

PATH = Path(__file__).parent.parent / "data/nouns.json"
LEVEL = {"der_senior": "B1", "das_abgas": "B1"}
ACCEPTED = {"die_ecke": ["die"]}
DUPLICATES = {"das_comic"}  # Same word as der_comic.

words = [w for w in json.loads(PATH.read_text()) if w["id"] not in DUPLICATES]
for word in words:
    word["level"] = LEVEL.get(word["id"], word["level"])
    word["accepted_articles"] = ACCEPTED.get(word["id"], word["accepted_articles"])
    if word["id"] == "der_comic":
        word["accepted_articles"] = ["das", "der"]
assert not [w["id"] for w in words if w["level"] == "UNMATCHED"]
PATH.write_text(json.dumps(words, ensure_ascii=False, indent=1))
print(len(words), "words")
