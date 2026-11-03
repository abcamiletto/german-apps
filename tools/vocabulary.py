"""The whole word list: nouns, verbs and adjectives, in the order the app teaches them."""
import json
from pathlib import Path

DATA = Path(__file__).parent.parent / "data"
LEVELS = ["A1", "A2", "B1"]


def spoken(word: dict) -> str:
    """The word as the learner says it, and as the clip reads it."""
    return f"{word['article']} {word['noun']}" if "noun" in word else word["lemma"]


def load() -> list[dict]:
    words = [*json.loads((DATA / "nouns.json").read_text()), *json.loads((DATA / "verbs_adjectives.json").read_text())]
    # Regional words come last. Otherwise the most frequent word of the lowest level comes first, whatever its kind.
    return sorted(words, key=lambda w: (w["region"] != "", LEVELS.index(w["level"]), -w["frequency"], spoken(w)))
