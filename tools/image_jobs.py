"""Write image generation batches for words that have no valid image yet, and validate finished images.

  uv run image_jobs.py plan <batch-size>   writes .cache/image_batches/batch-NNN.json
  uv run image_jobs.py check               validates .cache/images/*.png
"""
import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent
WORDS = json.loads((ROOT.parent / "data/nouns.json").read_text())
IMAGES = ROOT / ".cache/images"
BATCHES = ROOT / ".cache/image_batches"

STYLE = (
    "Flat vector-style illustration for a language-learning app. Style: clean geometric shapes, solid fills, no "
    "outlines, no gradients, no textures, no 3D, no photorealism, at most one simple darker tone per shape for form. "
    "Palette: soft and harmonious - coral, teal, mustard, dusty blue, sage green, warm brown, navy, cream, with natural "
    "colours where the subject requires them. People have simple friendly faces, rounded proportions and a varied "
    "range of skin tones. Composition: one centred subject filling about 75% of a square canvas, with clear margins "
    "on all sides and nothing cut off. Show only what the subject line names: no extra people, objects or decoration. Background: fully transparent, with no scenery, floor, frame, circle, card "
    "or cast shadow behind the subject. Absolutely no text, letters, words, labels or logos anywhere; digits only "
    "when the subject itself is defined by them, such as a clock face or a coin. Non-graphic and suitable for all ages."
)


def subject(word: dict) -> str:
    """Briefs sometimes ask for a backdrop, which would conflict with the transparent style."""
    return re.sub(r"\s*(on|against|with) an? (plain |solid |clean )?\w* ?background", "", word["image_brief"])


def valid(path: Path) -> str | None:
    """Return a problem description, or None if the image is usable."""
    with Image.open(path) as source:
        image = source.convert("RGBA")
    width, height = image.size
    alpha = image.getchannel("A")
    histogram = alpha.histogram()
    transparent = sum(histogram[:16]) / (width * height)
    if transparent < 0.10:
        return f"background not transparent ({transparent:.0%} clear)"
    if transparent > 0.97:
        return f"almost empty ({transparent:.0%} clear)"
    border = [alpha.getpixel((x, y)) for x in range(0, width, 8) for y in (0, height - 1)]
    border += [alpha.getpixel((x, y)) for y in range(0, height, 8) for x in (0, width - 1)]
    if sum(value > 128 for value in border) / len(border) > 0.08:
        return "subject touches the canvas edge"
    return None


def plan(size: int):
    BATCHES.mkdir(parents=True, exist_ok=True)
    for old in BATCHES.glob("batch-*.json"):
        old.unlink()
    missing = [w for w in WORDS if not (IMAGES / f"{w['id']}.png").exists()]
    for index in range(0, len(missing), size):
        jobs = [
            {
                "id": w["id"],
                "word": f"{w['article']} {w['noun']}",
                "prompt": f"Subject: {subject(w)}\n{STYLE}",
            }
            for w in missing[index : index + size]
        ]
        path = BATCHES / f"batch-{index // size:03d}.json"
        path.write_text(json.dumps(jobs, ensure_ascii=False, indent=1))
    print(len(missing), "missing,", len(list(BATCHES.glob("batch-*.json"))), "batches")


def check():
    IMAGES.mkdir(parents=True, exist_ok=True)
    ids = {w["id"] for w in WORDS}
    problems = {p.stem: valid(p) for p in sorted(IMAGES.glob("*.png"))}
    bad = {k: v for k, v in problems.items() if v}
    print(f"{len(problems)} images, {len(bad)} bad, {len(ids - problems.keys())} missing, {len(problems.keys() - ids)} unknown")
    for key, value in bad.items():
        print(" ", key, value)


if __name__ == "__main__":
    {"plan": lambda: plan(int(sys.argv[2])), "check": check}[sys.argv[1]]()
