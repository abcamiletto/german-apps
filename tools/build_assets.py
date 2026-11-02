"""Package data/nouns.json and the generated media into app/src/main/assets.

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

ROOT = Path(__file__).parent
ASSETS = ROOT.parent / "app/src/main/assets"
SIZE = 512
MARGIN = 20


def convert_image(source: Path, target: Path):
    if target.exists() and target.stat().st_mtime >= source.stat().st_mtime:
        return
    image = Image.open(source).convert("RGBA")
    # The generator leaves faint specks in the transparent area. Clear them before finding the subject.
    image.putalpha(image.getchannel("A").point(lambda value: 0 if value < 24 else value))
    box = image.getchannel("A").getbbox()
    subject = image.crop(box) if box else image
    room = SIZE - 2 * MARGIN
    scale = room / max(subject.size)
    subject = subject.resize((max(1, round(subject.width * scale)), max(1, round(subject.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(subject, ((SIZE - subject.width) // 2, (SIZE - subject.height) // 2))
    canvas.save(target, "WEBP", quality=82, alpha_quality=90, method=6)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-missing", action="store_true", help="Package words without media, for development.")
    args = parser.parse_args()

    words = json.loads((ROOT.parent / "data/nouns.json").read_text())
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

    payload = [
        {
            "id": w["id"],
            "article": w["article"],
            "noun": w["noun"],
            "plural": w["plural"],
            "pluralOnly": w["plural_only"],
            "accepted": w["accepted_articles"],
            "alternatives": w["alternatives"],
            "level": w["level"],
            "region": w["region"],
            "gloss": w["gloss_en"],
            "category": w["category"],
        }
        for w in words
    ]
    (ASSETS / "words.json").write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
    size = sum(p.stat().st_size for p in ASSETS.rglob("*") if p.is_file())
    print(f"assets: {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
