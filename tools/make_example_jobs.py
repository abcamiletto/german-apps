"""Split the words that have no examples yet into batches, for the agents that write example sentences and phrases.

  uv run make_example_jobs.py <batch-size>   writes .cache/examples/jobs/batch-NNN.json
"""
import json
import sys
from pathlib import Path

from vocabulary import DATA, load, spoken

CACHE = Path(__file__).parent / ".cache/examples"


def main(size: int):
    jobs = CACHE / "jobs"
    (CACHE / "done").mkdir(parents=True, exist_ok=True)
    jobs.mkdir(parents=True, exist_ok=True)
    for old in jobs.glob("*.json"):
        old.unlink()
    path = DATA / "examples.json"
    finished = json.loads(path.read_text()) if path.exists() else {}
    words = [
        {
            "id": w["id"],
            "word": spoken(w),
            "kind": w.get("kind", "noun"),
            "level": w["level"],
            "english": w["gloss_en"],
            "forms": w.get("forms", ""),
            "plural": w.get("plural") or "",
        }
        for w in load()
        if w["id"] not in finished
    ]
    for index in range(0, len(words), size):
        (jobs / f"batch-{index // size:03d}.json").write_text(json.dumps(words[index : index + size], ensure_ascii=False, indent=0))
    print(len(words), "words,", len(list(jobs.glob("*.json"))), "batches")


if __name__ == "__main__":
    main(int(sys.argv[1]))
