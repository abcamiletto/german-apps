<img src="assets/icon.svg" width="64" height="64" alt="">

# Wortschatz

A website and an Android app for learning German vocabulary, covering the Goethe-Institut A1, A2 and B1 word lists: 1,942 nouns, 678 verbs and 465 adjectives, each with its own illustration and pronunciation clip.

**[Open the website](https://abcamiletto.github.io/german-apps/)** or [install the Android app](../../releases/latest). There is no account. A sync code keeps your progress the same on your phone and in your browsers.

## How it works

The app shows a picture, and you say the word. For a noun you say its article too, such as "der Hund", and a wrong gender is called out by name. For a verb or adjective you say just the word, such as "fahren" or "groß". The answer screen then gives the conjugation (fährt, fuhr, ist gefahren) or comparison (größer, am größten).

- **New words** introduces five words with picture, audio, translation and plural, then tests you on them. The most frequent words come first, whatever their kind or level.
- **Review** brings back words when they are due, scheduled with [FSRS](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm). Words that would lose the most by waiting another day come first, and related words aren't asked back to back.
- **Practise** suggests what else to repeat: tricky words you have forgotten before, words due soon, and words new this week. **Coming up** shows the reviews of the next week.
- **Suggest** offers five answers of the same kind as the word. For a noun, one of them repeats it with a wrong article.
- **Reveal** shows the answer, and the word returns later in the session.
- **Browse** lists every word, searchable in German or English, even by a conjugated form like "fährt", with audio.

Speaking needs German speech recognition. On Android, Google's app provides it on most devices. On the web, Chrome, Edge and Safari provide it, and Firefox doesn't. If it isn't available, or you deny the microphone, you can type your answers instead.

## Sync

Each device creates a sync code the first time it runs. To link another device, open **Sync** (the cloud icon) and enter the code there. The two sets of progress merge, and for any word the most recent review wins. Reviews made offline are sent later.

The code is the only key: anyone who has it can see and change that progress. It holds 80 random bits, and the server stores only its hash.

## Install on Android

Download the APK from the [latest release](../../releases/latest) and open it on your phone. Every release is signed with the same key, so a new version installs over the old one and keeps your progress.

## Stack

One Kotlin codebase with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). A screen or a rule is written once and ships to both platforms.

| Module | What it holds |
| --- | --- |
| `shared/` | Everything the learner sees and every rule: UI, spaced repetition, grading, suggestions |
| `app/` | Android only: Room storage, `SpeechRecognizer`, `MediaPlayer`, the microphone permission |
| `web/` | Browser only (Kotlin/Wasm): local storage, the Web Speech API, `<audio>` |
| `content/` | The word list, images and audio, used by both |
| `server/` | The sync backend at `german.abcamiletto.com`: FastAPI and SQLite |

`shared` asks for the platform through a few small interfaces: `ProgressStore`, `Settings`, `SpeechEngine`, `PronunciationPlayer`, `WordContent` and `MicrophoneAccess`.

## Development

Building requires JDK 21 and the Android SDK.

```sh
./gradlew :shared:testAndroidHostTest      # Shared tests on the JVM
./gradlew :shared:wasmJsBrowserTest        # The same tests in headless Chrome
./gradlew connectedDebugAndroidTest        # End-to-end tests, on a running emulator
./gradlew :app:assembleDebug               # The Android app
./gradlew :web:wasmJsBrowserDevelopmentRun # The website, with live reload
./gradlew :web:wasmJsBrowserDistribution   # The production website
(cd server && uv run pytest)               # The sync server's tests
```

Each push to `main` runs all the tests except the emulator's, then builds a signed release APK and the website. The APK is published as a GitHub release and the website to GitHub Pages, but only when every word has both an image and a clip.

## Server

The backend runs on a Mac mini as two system daemons: `uvicorn` on `127.0.0.1:43130`, and a dedicated Cloudflare tunnel, `wortschatz`, that exposes it as `german.abcamiletto.com`. The tunnel's settings are in `~/.cloudflared/wortschatz.yml`, and the database is in `~/Library/Application Support/Wortschatz/`. Devices keep their own full copy of the progress.

To set it up again, clone this repository into `~/german-apps`, then run `server/deploy/install.sh`. Run `server/deploy/update.sh` to deploy a change. Pushing to `main` does not deploy the server.

## Content pipeline

The scripts in `tools/` produced the committed files in `data/` and `content/`, so you only need them to change the content. They use `uv` and need `GEMINI_API_KEY`.

1. Download the three Goethe word list PDFs and convert them with `pdftotext -layout` into `tools/source_lists/`. The expected filenames are in `tools/normalize_nouns.py`. They aren't in this repository because they are the Goethe-Institut's copyrighted documents.
2. `uv run normalize_nouns.py` extracts the noun entries. `uv run enrich_nouns.py` and `uv run fix_nouns.py` merge them and add translations, categories and image briefs. `uv run check_nouns.py` reports disagreements with Wiktionary. `normalize_verbs_adjectives.py`, `enrich_verbs_adjectives.py` and `fix_verbs_adjectives.py` do the same for verbs and adjectives, preferring the conjugations printed in the lists.
3. `uv run make_audio.py` generates the clips with Gemini TTS, reading 25 words per request because the API quota counts requests per day. Use `--model` to switch model when a daily quota runs out. Then `uv run check_audio.py` transcribes each clip back to catch mistakes.
4. `uv run image_jobs.py plan 50` writes image batches, which Codex agents render with their built-in image tool. `uv run image_jobs.py check` validates the results, `uv run check_images.py` flags readable text or the wrong subject, and `uv run check_holes.py` finds the blurred holes the generator can leave in flat areas.
5. `uv run rank_frequency.py` looks up each word in [DeReWo](https://www.ids-mannheim.de/derewo) for `data/lemma_classes.json`. The learning order in `vocabulary.py` averages its lemma counts with [wordfreq](https://github.com/rspeer/wordfreq), which counts spellings.
6. `uv run build_assets.py` packages everything into `content/`.

Regional words marked in the lists as Austrian or Swiss come last in the learning order and are labelled in the app.

`data/lemma_classes.json` is adapted from the Korpusbasierte Wortgrundformenliste DeReWo, v-ww-bll-320000g-2012-12-31-1.0, © Institut für Deutsche Sprache, Programmbereich Korpuslinguistik, Mannheim, under [CC BY-NC 3.0](https://creativecommons.org/licenses/by-nc/3.0/).
