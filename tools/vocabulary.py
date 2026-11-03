"""The whole word list: nouns, verbs and adjectives, most useful to learn first."""
import json
from pathlib import Path

DATA = Path(__file__).parent.parent / "data"
# In wordfreq's scale, where "der" is about 7.5. Each DeReWo class is half as frequent as the one before it.
DEREWO_TOP = 7.6
DEREWO_STEP = 0.301


def spoken(word: dict) -> str:
    """The word as the learner says it, and as the clip reads it."""
    return f"{word['article']} {word['noun']}" if "noun" in word else word["lemma"]


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
    words = [*json.loads((DATA / "nouns.json").read_text()), *json.loads((DATA / "verbs_adjectives.json").read_text())]
    path = DATA / "lemma_classes.json"
    classes = json.loads(path.read_text()) if path.exists() else {}
    # Regional words come last. Everything else follows frequency, whatever its kind or level.
    return sorted(words, key=lambda w: (w["region"] != "", -learning_score(w, classes), spoken(w)))
