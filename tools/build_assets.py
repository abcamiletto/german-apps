"""Package the word list and the generated media into /content, which the app and the website share.

Images become 512 px transparent WebP squares, trimmed to the subject with an even margin.
Audio clips are copied as they are.
  uv run build_assets.py [--allow-missing]
"""
import argparse
import json
import shutil
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

from vocabulary import DATA, load

ROOT = Path(__file__).parent
ASSETS = ROOT.parent / "content"
SIZE = 512
MARGIN = 20


def convert_image(source: Path, target: Path):
    if target.exists() and target.stat().st_mtime >= source.stat().st_mtime:
        return
    image = Image.open(source).convert("RGBA")
    # The generator leaves faint specks in the transparent area. Clear them before finding the subject.
    image.putalpha(image.getchannel("A").point(lambda value: 0 if value < 40 else value))
    box = image.getchannel("A").getbbox()
    subject = image.crop(box) if box else image
    room = SIZE - 2 * MARGIN
    scale = room / max(subject.size)
    subject = subject.resize((max(1, round(subject.width * scale)), max(1, round(subject.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(subject, ((SIZE - subject.width) // 2, (SIZE - subject.height) // 2))
    canvas.save(target, "WEBP", quality=82, alpha_quality=90, method=6)


def entry(word: dict, examples: dict) -> dict:
    shared = {
        "id": word["id"],
        "alternatives": word["alternatives"],
        "level": word["level"],
        "region": word["region"],
        "gloss": word["gloss_en"],
        "cue": word["cue"],
        "category": word["category"],
        **examples.get(word["id"], {}),
    }
    if "noun" in word:
        return {
            **shared,
            "kind": "noun",
            "article": word["article"],
            "lemma": word["noun"],
            "plural": word["plural"],
            "pluralOnly": word["plural_only"],
            "accepted": word["accepted_articles"],
        }
    return {**shared, "kind": word["kind"], "lemma": word["lemma"], "forms": word["forms"], "heardAs": word.get("heard_as", [])}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-missing", action="store_true", help="Package words without media, for development.")
    args = parser.parse_args()

    words = load()
    (ASSETS / "images").mkdir(parents=True, exist_ok=True)
    (ASSETS / "audio").mkdir(parents=True, exist_ok=True)
    images = ROOT / ".cache/images"
    audio = ROOT / ".cache/audio"

    missing_images = [w["id"] for w in words if not (images / f"{w['id']}.png").exists()]
    missing_audio = [w["id"] for w in words if not (audio / f"{w['id']}.ogg").exists()]
    print(f"{len(words)} words, {len(missing_images)} without images, {len(missing_audio)} without audio")
    if (missing_images or missing_audio) and not args.allow_missing:
        raise SystemExit("Media is incomplete. Pass --allow-missing for a development build.")

    def package(word):
        image = images / f"{word['id']}.png"
        if image.exists():
            convert_image(image, ASSETS / "images" / f"{word['id']}.webp")
        clip = audio / f"{word['id']}.ogg"
        if clip.exists():
            shutil.copyfile(clip, ASSETS / "audio" / f"{word['id']}.ogg")

    with ThreadPoolExecutor(8) as pool:
        list(pool.map(package, words))

    examples = json.loads((DATA / "examples.json").read_text())
    missing_examples = [w["id"] for w in words if w["id"] not in examples]
    print(len(missing_examples), "words without examples")
    if missing_examples and not args.allow_missing:
        raise SystemExit("Examples are incomplete. Pass --allow-missing for a development build.")
    payload = [entry(w, examples) for w in words]
    (ASSETS / "words.json").write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
    size = sum(p.stat().st_size for p in ASSETS.rglob("*") if p.is_file())
    print(f"assets: {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
