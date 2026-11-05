"""Check the written example sentences and phrases without reading them for meaning, and merge them.

  uv run check_examples.py <batch>   reports the problems in .cache/examples/done/<batch>.json
  uv run check_examples.py --merge   checks every batch, then writes data/examples.json
"""
import json
import re
import sys
from pathlib import Path

from vocabulary import DATA, load

CACHE = Path(__file__).parent / ".cache/examples"
LONGEST_SENTENCE = {"A1": 9, "A2": 11, "B1": 14}
LONGEST_PHRASE = 6


def problems(job: dict, row: dict | None) -> list[str]:
    if row is None:
        return ["missing"]
    found = []
    phrases, sentences = row.get("phrases"), row.get("sentences")
    if not isinstance(phrases, list) or not 1 <= len(phrases) <= 2:
        found.append("needs 1 or 2 phrases")
        phrases = []
    if not isinstance(sentences, list) or len(sentences) != 2:
        found.append("needs exactly 2 sentences")
        sentences = []
    for item in [*phrases, *sentences]:
        german, english = item.get("de", ""), item.get("en", "")
        if not german or not english:
            found.append(f"empty text: {item}")
        elif "**" in english:
            found.append(f"markers in the English: {english}")
        elif len(re.findall(r"\*\*[^*]+\*\*", german)) == 0 or german.count("**") % 2:
            found.append(f"mark the word with ** in: {german}")
    for item in phrases:
        german = item.get("de", "")
        if re.search(r"[.!?]$", german) or len(german.split()) > LONGEST_PHRASE:
            found.append(f"a phrase is at most {LONGEST_PHRASE} words with no end punctuation: {german}")
    for item in sentences:
        german = item.get("de", "")
        if not re.search(r"[.!?]$", german) or len(german.split()) > LONGEST_SENTENCE[job["level"]]:
            found.append(f"a {job['level']} sentence is at most {LONGEST_SENTENCE[job['level']]} words and ends in punctuation: {german}")
    texts = [item.get("de") for item in [*phrases, *sentences]]
    if len(texts) != len(set(texts)):
        found.append("repeated text")
    return found


def check(name: str) -> int:
    job_list = json.loads((CACHE / "jobs" / f"{name}.json").read_text())
    path = CACHE / "done" / f"{name}.json"
    rows = {row["id"]: row for row in json.loads(path.read_text())} if path.exists() else {}
    count = 0
    for job in job_list:
        for problem in problems(job, rows.get(job["id"])):
            print(f"{job['id']}: {problem}")
            count += 1
    extra = rows.keys() - {job["id"] for job in job_list}
    if extra:
        print("unknown ids:", sorted(extra))
    print(f"{len(job_list)} words, {count} problems")
    return count


def merge():
    words = load()
    rows = {}
    path = DATA / "examples.json"
    if path.exists():
        rows.update(json.loads(path.read_text()))
    # What is already merged may have been corrected since, so only new words are taken from the batches.
    for done in sorted((CACHE / "done").glob("*.json")):
        for row in json.loads(done.read_text()):
            rows.setdefault(row["id"], {"phrases": row["phrases"], "sentences": row["sentences"]})
    jobs = [{"id": w["id"], "level": w["level"]} for w in words]
    bad = {job["id"]: problems(job, rows.get(job["id"])) for job in jobs}
    bad = {key: value for key, value in bad.items() if value}
    ordered = {w["id"]: rows[w["id"]] for w in sorted(words, key=lambda w: w["id"]) if w["id"] in rows}
    path.write_text(json.dumps(ordered, ensure_ascii=False, indent=0))
    print(len(ordered), "words written,", len(bad), "with problems", list(bad.items())[:20])


if __name__ == "__main__":
    if sys.argv[1] == "--merge":
        merge()
    else:
        sys.exit(1 if check(sys.argv[1]) else 0)
