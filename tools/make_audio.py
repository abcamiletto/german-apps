"""Generate a pronunciation clip for every word with Gemini TTS, as mono Opus in .cache/audio/<id>.ogg.

  uv run make_audio.py [--only id,id] [--voice Kore] [--out DIR]
"""
import argparse
import base64
import json
import subprocess
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import generate

ROOT = Path(__file__).parent
MODEL = "gemini-3.8-flash-tts"
# Send only the words: this model reads any instructions in the prompt aloud.
# Trim leading and trailing silence, normalize loudness, and encode small mono speech clips.
FILTER = (
    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,"
    "areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.1,areverse,"
    "loudnorm=I=-18:TP=-2:LRA=7"
)


def synthesize(word: dict, voice: str, out: Path) -> str | None:
    path = out / f"{word['id']}.ogg"
    if path.exists():
        return None
    text = f"{word['article']} {word['noun']}"
    data = generate(
        MODEL,
        {
            "contents": [{"parts": [{"text": f"{text}."}]}],
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}},
            },
        },
    )
    inline = data["candidates"][0]["content"]["parts"][0]["inlineData"]
    with tempfile.NamedTemporaryFile(suffix=".wav") as raw:
        raw.write(base64.b64decode(inline["data"]))
        raw.flush()
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", raw.name, "-af", FILTER, "-ar", "24000", "-ac", "1",
             "-c:a", "libopus", "-b:a", "32k", "-application", "voip", str(path)],
            check=True,
        )
    return word["id"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--voice", default="Kore")
    parser.add_argument("--out", default=str(ROOT / ".cache/audio"))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    words = json.loads((ROOT.parent / "data/nouns.json").read_text())
    if args.only:
        wanted = set(args.only.split(","))
        words = [w for w in words if w["id"] in wanted]
    with ThreadPoolExecutor(4) as pool:
        done = [r for r in pool.map(lambda w: synthesize(w, args.voice, out), words) if r]
    print(len(done), "new clips in", out)


if __name__ == "__main__":
    main()
