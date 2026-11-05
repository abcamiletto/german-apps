"""Look up how common each word is as a lemma in the DeReWo list, and write data/lemma_classes.json.

wordfreq alone only counts spellings, so it ranks "die Bitte" with "bitte" and "weiß" with "wissen".
DeReWo counts lemmas: each step in its frequency class means half as many occurrences.
The list is © Institut für Deutsche Sprache, Mannheim, CC BY-NC 3.0, http://www.ids-mannheim.de/derewo
"""
import io
import json
import re
import urllib.request
import zipfile
from pathlib import Path

from vocabulary import DATA, load

CACHE = Path(__file__).parent / ".cache/derewo-v-ww-bll-320000g-2012-12-31-1.0.zip"
URL = f"https://www.ids-mannheim.de/fileadmin/kl/derewo/{CACHE.name}"
PART_OF_SPEECH = {"noun": "NN", "verb": "V", "adjective": "ADJ"}


def read_list() -> dict[str, dict[str, int]]:
    if not CACHE.exists():
        CACHE.write_bytes(urllib.request.urlopen(URL).read())
    with zipfile.ZipFile(CACHE) as archive:
        text = archive.read(CACHE.with_suffix(".txt").name).decode("latin-1")
    classes: dict[str, dict[str, int]] = {}
    for line in io.StringIO(text):
        fields = line.split()
        if len(fields) >= 2 and not line.startswith("#"):
            classes.setdefault(fields[0], {})[fields[2] if len(fields) > 2 else ""] = int(float(fields[1]))
    return classes


def candidates(word: dict) -> list[str]:
    """The spellings a word may have in the list. Adjectives like "letzte" are listed under other endings."""
    name = word.get("noun") or word["lemma"]
    name = re.sub(r"^sich ", "", name)
    if word.get("kind") != "adjective":
        return [name]
    stem = name.removesuffix("e")
    return [name, stem, f"{stem}er", f"{stem}e"]


def main():
    classes = read_list()
    found = {}
    for word in load():
        kind = word.get("kind", "noun")
        for name in candidates(word):
            if name in classes:
                entry = classes[name]
                found[word["id"]] = entry.get(PART_OF_SPEECH.get(kind, ""), min(entry.values()))
                break
    (DATA / "lemma_classes.json").write_text(json.dumps(found, indent=0, sort_keys=True))
    print(len(found), "of", len(load()), "words found")


if __name__ == "__main__":
    main()
