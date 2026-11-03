"""Writes the app icon in every size the website and the Android app use, from assets/icon-source.png."""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent.parent
SOURCE = ROOT / "assets/icon-source.png"
PAPER = (246, 247, 251, 255)
# Android crops an adaptive icon, and only promises a circle 66 of its 108 units across. Corners need room too.
ADAPTIVE_SIZE = 432
ADAPTIVE_SUBJECT = 224


def subject() -> Image.Image:
    image = Image.open(SOURCE).convert("RGBA")
    image.putalpha(image.getchannel("A").point(lambda value: 0 if value < 40 else value))
    return image.crop(image.getchannel("A").getbbox())


def fit(image: Image.Image, canvas: int, room: int, background=(0, 0, 0, 0)) -> Image.Image:
    scale = room / max(image.size)
    resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
    result = Image.new("RGBA", (canvas, canvas), background)
    result.alpha_composite(resized, ((canvas - resized.width) // 2, (canvas - resized.height) // 2))
    return result


def main():
    image = subject()
    web = ROOT / "web/src/wasmJsMain/resources"
    fit(image, 512, 480).save(web / "icon.png", optimize=True)
    fit(image, 180, 148, PAPER).convert("RGB").save(web / "apple-touch-icon.png", optimize=True)
    fit(image, 256, 240).save(ROOT / "assets/icon.png", optimize=True)
    shared = ROOT / "shared/src/commonMain/composeResources/drawable"
    shared.mkdir(exist_ok=True)
    fit(image, 288, 272).save(shared / "icon.webp", quality=92, method=6)
    drawable = ROOT / "app/src/main/res/drawable-nodpi"
    drawable.mkdir(exist_ok=True)
    fit(image, ADAPTIVE_SIZE, ADAPTIVE_SUBJECT).save(drawable / "ic_launcher_foreground.webp", quality=92, method=6)


if __name__ == "__main__":
    main()
