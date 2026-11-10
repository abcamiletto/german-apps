"""The whole word list, most useful to learn first."""
import json
from pathlib import Path

DATA = Path(__file__).parent.parent / "data"
LEVELS = ["A1", "A2", "B1"]
# In wordfreq's scale, where "der" is about 7.5. Each DeReWo class is half as frequent as the one before it.
DEREWO_TOP = 7.6
DEREWO_STEP = 0.301


def spoken(word: dict) -> str:
    """The word as the learner says it, and as the clip reads it."""
    return f"{word['article']} {word['noun']}" if "noun" in word else word["lemma"].replace(" ... ", " ")


def learning_score(word: dict, classes: dict[str, int]) -> float:
    """How common the word is, averaged over written German (DeReWo) and everyday language (wordfreq)."""
    everyday = word["frequency"]
    if word["id"] not in classes:
        # wordfreq credits a phrase such as "stehen bleiben" with its parts, which overstates it.
        name = word.get("noun") or word["lemma"]
        return everyday - 1 if " " in name.removeprefix("sich ") else everyday
    written = DEREWO_TOP - DEREWO_STEP * classes[word["id"]]
    # wordfreq counts spellings, so "bitte" inflates "die Bitte". The lemma count limits how far that can go.
    return (written + min(everyday, written + 1)) / 2


def load() -> list[dict]:
    files = ("nouns.json", "verbs_adjectives.json", "others.json")
    words = [word for name in files for word in json.loads((DATA / name).read_text())]
    # Common meanings the first translation missed. Kept apart so that regenerating the lists can't lose them.
    glosses = json.loads((DATA / "glosses.json").read_text())
    for word in words:
        word["gloss_en"] = glosses.get(word["id"], word["gloss_en"])
    # A short English example for words a gloss alone can't pin down, such as "made of wood" for "aus".
    cues = json.loads((DATA / "cues.json").read_text())
    for word in words:
        word["cue"] = cues.get(word["id"], "")
    path = DATA / "lemma_classes.json"
    classes = json.loads(path.read_text()) if path.exists() else {}
    # Regional words come last. Everything else goes level by level, and by frequency within a level.
    def order(word: dict) -> tuple:
        return word["region"] != "", LEVELS.index(word["level"]), -learning_score(word, classes), spoken(word)

    return sorted(words, key=order)
