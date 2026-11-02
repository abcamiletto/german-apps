"""Vision QA: flag images that show readable words or don't depict their noun. Writes .cache/image_qa.json."""
import base64
import io
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

from llm import structured_parts

ROOT = Path(__file__).parent
IMAGES = ROOT / ".cache/images"
BATCH = 30
PROMPT = """You are reviewing illustrations for a German vocabulary app, where the learner must name the noun from the picture alone.
Each image below is preceded by its index and the noun it should show. Report ONLY images with a real problem.
- `text`: the image contains readable letters or words, particularly anything that reveals the noun. Digits on clocks, calendars or coins are fine. Foreign-looking gibberish letters count.
- `wrong`: the image clearly depicts something else. A reasonable visual metaphor for an abstract noun is fine.
- `style`: it isn't a flat illustration (e.g. photo-like), or it has a visible background panel or checkerboard.
Return an empty list when all images are fine."""
SCHEMA = {
    "type": "object",
    "required": ["problems"],
    "properties": {
        "problems": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["index", "kind", "detail"],
                "properties": {
                    "index": {"type": "integer"},
                    "kind": {"type": "string", "enum": ["text", "wrong", "style"]},
                    "detail": {"type": "string"},
                },
            },
        }
    },
}


def thumb(path: Path) -> str:
    image = Image.open(path).convert("RGBA")
    image.thumbnail((320, 320))
    canvas = Image.new("RGB", image.size, (255, 255, 255))
    canvas.paste(image, (0, 0), image)
    buffer = io.BytesIO()
    canvas.save(buffer, "JPEG", quality=80)
    return base64.b64encode(buffer.getvalue()).decode()


def review(batch: list[dict]) -> list[dict]:
    parts = [{"text": PROMPT}]
    for index, word in enumerate(batch):
        parts.append({"text": f"[{index}] {word['article']} {word['noun']} ({word['gloss_en']})"})
        parts.append({"inlineData": {"mimeType": "image/jpeg", "data": thumb(IMAGES / f"{word['id']}.png")}})
    result = structured_parts(parts, SCHEMA)
    return [
        {"id": batch[p["index"]]["id"], "kind": p["kind"], "detail": p["detail"]}
        for p in result["problems"]
        if 0 <= p["index"] < len(batch)
    ]


def main():
    words = [w for w in json.loads((ROOT.parent / "data/nouns.json").read_text()) if (IMAGES / f"{w['id']}.png").exists()]
    batches = [words[i : i + BATCH] for i in range(0, len(words), BATCH)]
    with ThreadPoolExecutor(6) as pool:
        problems = [p for found in pool.map(review, batches) for p in found]
    (ROOT / ".cache/image_qa.json").write_text(json.dumps(problems, ensure_ascii=False, indent=1))
    print(len(words), "images reviewed,", len(problems), "flagged")


if __name__ == "__main__":
    main()
