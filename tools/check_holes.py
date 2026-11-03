"""Find the blurred holes the image generator leaves in large flat areas of a subject.

A clean drawing goes from opaque to transparent within a few pixels at its edges. A hole is a wide smear of
semi-transparent pixels, so this erodes the semi-transparent mask and reports images where a large core survives.
Neither image_jobs.py check nor the vision QA in check_images.py notices these.

  uv run check_holes.py [--min-core N]
"""
import argparse
import json
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

from PIL import Image, ImageFilter

IMAGES = Path(__file__).parent / ".cache/images"
RESULT = Path(__file__).parent / ".cache/image_holes.json"
ERODE = 9
WORK_SIZE = 1024


def core_size(path: Path) -> int:
    with Image.open(path) as source:
        alpha = source.convert("RGBA").getchannel("A")
    alpha = alpha.resize((WORK_SIZE, WORK_SIZE))
    soft = alpha.point(lambda value: 255 if 25 <= value <= 230 else 0)
    return soft.filter(ImageFilter.MinFilter(ERODE)).histogram()[255]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--min-core", type=int, default=150)
    args = parser.parse_args()
    paths = sorted(IMAGES.glob("*.png"))
    with ProcessPoolExecutor() as pool:
        sizes = dict(zip((p.stem for p in paths), pool.map(core_size, paths, chunksize=40)))
    flagged = {k: v for k, v in sorted(sizes.items(), key=lambda kv: -kv[1]) if v >= args.min_core}
    RESULT.write_text(json.dumps(flagged, indent=0))
    print(len(paths), "images,", len(flagged), "flagged")
    for key, value in flagged.items():
        print(f"  {key} {value}")


if __name__ == "__main__":
    main()
