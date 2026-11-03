"""Generate a pronunciation clip for every word with Gemini TTS, as mono Opus in .cache/audio/<id>.ogg.

The API key's daily quota counts requests, so each request reads a batch of words on separate lines. The model
pauses for about a second after each line, and the audio is cut at those pauses. check_audio.py then transcribes
every clip on its own, which catches a bad cut. Re-running only fills in missing clips.

  uv run make_audio.py [--only id,id] [--voice Kore] [--model NAME] [--out DIR]
"""
import argparse
import base64
import io
import re
import subprocess
import tempfile
import wave
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from llm import generate
from vocabulary import load, spoken

ROOT = Path(__file__).parent
MODEL = "gemini-3.8-flash-lite-tts"
BATCH = 25
MIN_PAUSE = 0.5
PADDING = 0.08
FILTER = "loudnorm=I=-18:TP=-2:LRA=7"


def pauses(path: str, duration: float) -> list[tuple[float, float]]:
    """Silent spans strictly inside the recording, in order."""
    log = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", path, "-af", f"silencedetect=noise=-38dB:d={MIN_PAUSE}", "-f", "null", "-"],
        capture_output=True, text=True, check=True,
    ).stderr
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", log)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", log)]
    return [(s, e) for s, e in zip(starts, ends) if s > 0.05 and e < duration - 0.05]


def speech_spans(path: str, duration: float) -> list[tuple[float, float]]:
    log = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", path, "-af", "silencedetect=noise=-38dB:d=0.04", "-f", "null", "-"],
        capture_output=True, text=True, check=True,
    ).stderr
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", log)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", log)]
    head = ends[0] if starts and starts[0] <= 0.01 and ends else 0.0
    tail = starts[-1] if starts and (len(ends) < len(starts) or ends[-1] >= duration - 0.01) else duration
    breaks = pauses(path, duration)
    edges = [head] + [v for pause in breaks for v in pause] + [tail]
    return list(zip(edges[0::2], edges[1::2]))


def encode(source: str, start: float, end: float, target: Path):
    start = max(0.0, start - PADDING)
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-ss", f"{start:.3f}", "-to", f"{end + PADDING:.3f}", "-i", source,
         "-af", FILTER, "-ar", "24000", "-ac", "1", "-c:a", "libopus", "-b:a", "32k", "-application", "voip",
         str(target)],
        check=True,
    )


def as_wav(audio: bytes, mime_type: str) -> bytes:
    """Older TTS models return bare 24 kHz PCM instead of a WAV file."""
    if "wav" in mime_type:
        return audio
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(24000)
        output.writeframes(audio)
    return buffer.getvalue()


def synthesize(words: list[dict], voice: str, out: Path, model: str = MODEL) -> int:
    text = "\n".join(f"{spoken(w)}." for w in words)
    body = {
        "contents": [{"parts": [{"text": text}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}},
        },
    }
    for _ in range(3):
        data = generate(model, body)
        inline = data["candidates"][0]["content"]["parts"][0]["inlineData"]
        audio = as_wav(base64.b64decode(inline["data"]), inline["mimeType"])
        with tempfile.NamedTemporaryFile(suffix=".wav") as raw:
            raw.write(audio)
            raw.flush()
            duration = (len(audio) - 44) / 48000
            spans = speech_spans(raw.name, duration)
            if len(spans) == len(words):
                for word, (start, end) in zip(words, spans):
                    encode(raw.name, start, end, out / f"{word['id']}.ogg")
                return len(words)
        print(f"expected {len(words)} clips, found {len(spans)}: {words[0]['id']}", flush=True)
    if len(words) == 1:
        return 0
    middle = len(words) // 2
    return synthesize(words[:middle], voice, out, model) + synthesize(words[middle:], voice, out, model)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--voice", default="Kore")
    parser.add_argument("--model", default=MODEL, help="Each model has its own daily quota.")
    parser.add_argument("--out", default=str(ROOT / ".cache/audio"))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    words = load()
    if args.only:
        wanted = set(args.only.split(","))
        words = [w for w in words if w["id"] in wanted]
    words = [w for w in words if not (out / f"{w['id']}.ogg").exists()]
    batches = [words[i : i + BATCH] for i in range(0, len(words), BATCH)]
    print(len(words), "clips to make in", len(batches), "requests", flush=True)
    with ThreadPoolExecutor(3) as pool:
        made = sum(pool.map(lambda batch: synthesize(batch, args.voice, out, args.model), batches))
    print(made, "new clips in", out)


if __name__ == "__main__":
    main()
