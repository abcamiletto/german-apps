"""Have a second model read every example for mistakes, since the writer can't catch its own. Writes .cache/examples/qa.json.

  uv run qa_examples.py
"""
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import structured
from vocabulary import DATA, load, spoken

ROOT = Path(__file__).parent
CACHE = ROOT / ".cache/examples"
BATCH = 40

PROMPT = """You are a strict native-speaker German teacher proofreading a vocabulary app for English speakers.
Each input word has short phrases and sentences, with the word marked by ** in the German, and an English translation.

Report only real faults:
- `grammar`: wrong gender, case ending, verb form or position, preposition, or spelling.
- `translation`: the English does not mean the same as the German.
- `unnatural`: no native speaker would say it this way. Do not report what is merely plain or simple.
- `wrong_word`: the marked word is not the target word, or it shows a meaning of a different word.
Put a fix in `fixed_de` and `fixed_en`, keeping the ** marks. Return an empty list if everything is fine, which will
be the usual case. Don't report style preferences, British or American spelling, or optional commas.

Input:
{items}
"""

SCHEMA = {
    "type": "object",
    "required": ["faults"],
    "properties": {
        "faults": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["id", "de", "kind", "reason", "fixed_de", "fixed_en"],
                "properties": {
                    "id": {"type": "string"},
                    "de": {"type": "string"},
                    "kind": {"type": "string", "enum": ["grammar", "translation", "unnatural", "wrong_word"]},
                    "reason": {"type": "string"},
                    "fixed_de": {"type": "string"},
                    "fixed_en": {"type": "string"},
                },
            },
        }
    },
}


def run(items: list[dict]) -> list[dict]:
    text = json.dumps(items, ensure_ascii=False)
    path = CACHE / "qa" / f"{hashlib.sha256((PROMPT + text).encode()).hexdigest()[:20]}.json"
    if path.exists():
        return json.loads(path.read_text())["faults"]
    result = structured(PROMPT.format(items=text), SCHEMA)
    path.write_text(json.dumps(result, ensure_ascii=False))
    return result["faults"]


def main():
    (CACHE / "qa").mkdir(parents=True, exist_ok=True)
    examples = json.loads((DATA / "examples.json").read_text())
    items = [
        {"id": w["id"], "word": spoken(w), "english": w["gloss_en"], "level": w["level"], **examples[w["id"]]}
        for w in load()
        if w["id"] in examples
    ]
    with ThreadPoolExecutor(10) as pool:
        faults = [f for part in pool.map(run, [items[i : i + BATCH] for i in range(0, len(items), BATCH)]) for f in part]
    (CACHE / "qa.json").write_text(json.dumps(faults, ensure_ascii=False, indent=1))
    print(len(items), "words read,", len(faults), "faults")
    for fault in faults:
        print(f"{fault['id']} [{fault['kind']}] {fault['de']} -> {fault['fixed_de']} ({fault['reason']})")


if __name__ == "__main__":
    main()
