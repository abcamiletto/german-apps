<img src="assets/icon.svg" width="64" height="64" alt="">

# Wortschatz

An Android app for learning German nouns together with their articles, covering the Goethe-Institut A1, A2 and B1 word lists: 1,942 nouns, each with its own illustration and pronunciation clip.

It runs entirely on the phone, with no account and no server.

## How it works

The app shows a picture, and you say the article and the noun, such as "der Hund". The recogniser's answer is graded separately for the article and the noun, so a wrong gender is called out by name.

- **Learn new words** introduces five words with picture, audio, translation and plural, then tests you on them.
- **Review** brings back words when they are due, scheduled with [FSRS](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm). When nothing is due, it practises the words you are most likely to forget.
- **Suggest** offers five answers: the correct one, the same noun with a wrong article, and three similar nouns.
- **Reveal** shows the answer, and the word returns later in the session.
- **Browse** lists every word, searchable in German or English, with audio.

Speaking needs a German speech recognition service on the phone, which Google's app provides on most devices. If it isn't available, or you deny the microphone, you can type your answers instead.

## Install

Download the APK from the [latest release](../../releases/latest) and open it on your phone. Every release is signed with the same key, so a new version installs over the old one and keeps your progress.

## Stack

Kotlin, Jetpack Compose with Material 3, Room, Navigation Compose, Coil and kotlinx.serialization. The word list, images and audio ship inside the APK as assets.

## Development

Building requires JDK 21 and the Android SDK.

```sh
./gradlew testDebugUnitTest          # Unit tests
./gradlew connectedDebugAndroidTest  # End-to-end tests, on a running emulator
./gradlew assembleDebug
```

Each push to `main` runs the unit tests and builds a signed release APK. A GitHub release is published only when every word has both an image and a clip.

## Content pipeline

The scripts in `tools/` produced the committed files in `data/` and `app/src/main/assets/`, so you only need them to change the content. They use `uv` and need `GEMINI_API_KEY`.

1. Download the three Goethe word list PDFs and convert them with `pdftotext -layout` into `tools/source_lists/`. The expected filenames are in `tools/normalize_nouns.py`. They aren't in this repository because they are the Goethe-Institut's copyrighted documents.
2. `uv run normalize_nouns.py` extracts the noun entries. `uv run enrich_nouns.py` and `uv run fix_nouns.py` merge them and add translations, categories and image briefs. `uv run check_nouns.py` reports disagreements with Wiktionary.
3. `uv run make_audio.py` generates the clips with Gemini TTS, and `uv run check_audio.py` transcribes each one back to catch mistakes.
4. `uv run image_jobs.py plan 50` writes image batches, which Codex agents render with their built-in image tool. `uv run image_jobs.py check` validates the results.
5. `uv run build_assets.py` packages everything into the app.

Regional words marked in the lists as Austrian or Swiss come last in the learning order and are labelled in the app.
