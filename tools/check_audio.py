"""QA every clip: duration bounds, then an independent transcription compared with the expected words."""
import base64
import json
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import generate

ROOT = Path(__file__).parent
AUDIO = ROOT / ".cache/audio"
RESULT = ROOT / ".cache/audio_qa.json"
PROMPT = (
    "This clip contains a speaker reading a short German noun phrase. Transcribe exactly what is said, using "
    "German spelling. Output only the words. If it is not German speech, output NOT_GERMAN."
)


def duration(path: Path) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    )
    return float(out.stdout.strip())


def simplify(text: str) -> str:
    return re.sub(r"[^a-zäöüß0-9]", "", text.lower())


def check(word: dict) -> dict:
    path = AUDIO / f"{word['id']}.ogg"
    if not path.exists():
        return {"id": word["id"], "problem": "missing"}
    length = duration(path)
    data = generate(
        "gemini-3.8-flash",
        {
            "contents": [
                {
                    "parts": [
                        {"text": PROMPT},
                        {"inlineData": {"mimeType": "audio/ogg", "data": base64.b64encode(path.read_bytes()).decode()}},
                    ]
                }
            ]
        },
    )
    parts = data["candidates"][0].get("content", {}).get("parts", [])
    heard = "".join(part.get("text", "") for part in parts).strip()
    expected = f"{word['article']} {word['noun']}"
    return {
        "id": word["id"],
        "expected": expected,
        "heard": heard,
        "seconds": round(length, 2),
        "match": simplify(heard) == simplify(expected),
        "problem": "too long" if length > 5 else ("too short" if length < 0.5 else None),
    }


def main():
    words = json.loads((ROOT.parent / "data/nouns.json").read_text())
    with ThreadPoolExecutor(10) as pool:
        results = list(pool.map(check, words))
    RESULT.write_text(json.dumps(results, ensure_ascii=False, indent=0))
    bad = [r for r in results if r.get("problem") or not r.get("match")]
    print(len(results), "clips,", len(bad), "flagged")
    for row in bad:
        print(row)


if __name__ == "__main__":
    main()
