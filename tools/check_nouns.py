"""Report dataset problems, including article disagreements with Wiktionary (german-nouns CSV)."""
import collections
import csv
import json
from pathlib import Path

ROOT = Path(__file__).parent
words = json.loads((ROOT.parent / "data/nouns.json").read_text())
GENUS = {"m": "der", "f": "die", "n": "das"}
wiki = collections.defaultdict(set)
plurals = collections.defaultdict(set)
with (ROOT / ".cache/nouns.csv").open() as handle:
    for row in csv.DictReader(handle):
        for column in ("genus", "genus 1", "genus 2", "genus 3", "genus 4"):
            if row.get(column) in GENUS:
                wiki[row["lemma"]].add(GENUS[row[column]])
        for column in ("nominativ plural", "nominativ plural*", "nominativ plural 1", "nominativ plural 2"):
            if row.get(column):
                plurals[row["lemma"]].add(row[column])

print(len(words), "words", collections.Counter(w["level"] for w in words), collections.Counter(w["region"] for w in words))
print(sorted(collections.Counter(w["category"] for w in words).items(), key=lambda kv: -kv[1]))
print("UNMATCHED:", [w["id"] for w in words if w["level"] == "UNMATCHED"])
print("NOT IN WIKTIONARY:", [w["noun"] for w in words if w["noun"] not in wiki and not w["plural_only"]])
print(
    "ARTICLE MISMATCH:",
    [(w["article"], w["noun"], w["gloss_en"], sorted(wiki[w["noun"]])) for w in words if w["noun"] in wiki and not w["plural_only"] and not set(w["accepted_articles"]) & wiki[w["noun"]]],
)
print(
    "PLURAL MISMATCH:",
    [(w["noun"], w["plural"], sorted(plurals[w["noun"]])) for w in words if w["plural"] and plurals[w["noun"]] and w["plural"] not in plurals[w["noun"]]],
)
print("PLURAL ONLY:", [w["noun"] for w in words if w["plural_only"]])
print("SENSES:", [(w["id"], w["gloss_en"]) for w in words if w["sense"]])
print("MULTI ARTICLE:", [(w["noun"], w["accepted_articles"]) for w in words if len(w["accepted_articles"]) > 1])
same = collections.defaultdict(list)
for w in words:
    same[w["noun"]].append((w["article"], w["gloss_en"]))
print("SAME NOUN:", {k: v for k, v in same.items() if len(v) > 1})
