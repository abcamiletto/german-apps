<img src="assets/icon.png" width="64" height="64" alt="">

# Wortschatz

A website and an Android app for learning German vocabulary, covering the Goethe-Institut A1, A2 and B1 word lists: 1,942 nouns, 678 verbs, 465 adjectives and 406 other words: pronouns, articles, prepositions, conjunctions, adverbs, numbers and set expressions. Each has its own illustration, pronunciation clip, example phrases and example sentences.

The website is private, at `german.abcamiletto.com`, behind one password. The Android app asks for the same password once, and your progress stays the same everywhere.

## How it works

The app shows a picture and the English word, and you say the German word. For a noun you say its article too, such as "der Hund", and a wrong gender is called out by name. Anything else you say as it is, such as "fahren", "groß" or "mit". The answer screen then explains the word: the conjugation (fährt, fuhr, ist gefahren), comparison (größer, am größten) or case (+ dative), then common phrases and two example sentences, all translated.

- **New words** has two steps: discover five words, with picture, audio, translation, plural and examples, then guess them straight away. All of A1 comes first, then A2, then B1, and within a level the most frequent words come first, whatever their kind.
- **Review** brings back words when they are due, scheduled with [FSRS](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm). Words that would lose the most by waiting another day come first, and related words aren't asked back to back.
- **Practise** suggests what else to repeat: tricky words you have forgotten before, words due soon, and words new this week. **Coming up** shows the reviews of the next week.
- **Suggest** offers five answers of the same kind as the word. For a noun, one of them repeats it with a wrong article.
- **Reveal** shows the answer, and the word returns later in the session.
- **Browse** lists every word, searchable in German or English, even by a conjugated form like "fährt", with audio.

Speaking needs German speech recognition. On Android, Google's app provides it on most devices. On the web, Chrome, Edge and Safari provide it, and Firefox doesn't. If it isn't available, or you deny the microphone, you can type your answers instead.

## Sync

Every device signs in with the same password, and they all share one set of progress. For any word the most recent review wins, and reviews made offline are sent later. Progress already on a device is merged when it first signs in.

## Install on Android

Download the APK from the [latest release](../../releases/latest), open it on your phone and sign in. Every release is signed with the same key, so a new version installs over the old one and keeps your progress.

## Stack

One Kotlin codebase with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/). A screen or a rule is written once and ships to both platforms.

| Module | What it holds |
| --- | --- |
| `shared/` | Everything the learner sees and every rule: UI, spaced repetition, grading, suggestions |
| `app/` | Android only: Room storage, `SpeechRecognizer`, `MediaPlayer`, the microphone permission |
| `web/` | Browser only (Kotlin/Wasm): local storage, the Web Speech API, `<audio>` |
| `content/` | The word list, images and audio, used by both |
| `server/` | The private server at `german.abcamiletto.com`: sign-in, the website, and sync, with FastAPI |
| `database/` | Where progress and sessions are stored, as [Convex](https://www.convex.dev) functions |

`shared` asks for the platform through a few small interfaces: `ProgressStore`, `Settings`, `SpeechEngine`, `PronunciationPlayer`, `WordContent` and `MicrophoneAccess`.

## Development

Building requires JDK 21 and the Android SDK.

```sh
./gradlew :shared:testAndroidHostTest      # Shared tests on the JVM
./gradlew :shared:wasmJsBrowserTest        # The same tests in headless Chrome
./gradlew connectedDebugAndroidTest        # End-to-end tests, on a running emulator
./gradlew :app:assembleDebug               # The Android app
./gradlew :web:wasmJsBrowserDevelopmentRun # The website, with live reload and no sign-in or sync
./gradlew :web:wasmJsBrowserDistribution   # The production website
(cd server && uv run pytest)               # The sync server's tests
(cd database && npm ci && npm test)        # The database functions' tests
```

Each push to `main` runs all the tests except the emulator's, then builds a signed release APK and the website. Both are published as a GitHub release, but only when every word has both an image and a clip. The server picks up each release within five minutes.

## Server

The server runs on a Mac mini as three system daemons: `uvicorn` on `127.0.0.1:43130`, a dedicated Cloudflare tunnel, `wortschatz`, that exposes it as `german.abcamiletto.com`, and a job that deploys each new release. Its password hash and website are in `~/Library/Application Support/Wortschatz/`, and the tunnel's settings are in `~/.cloudflared/wortschatz.yml`.

Progress and sessions are in a hosted Convex deployment in Ireland, which only this server talks to. The file `convex.json`, next to the password, holds `{"url": ..., "secret": ...}`, and the same secret is the deployment's `WORTSCHATZ_SERVER_SECRET` environment variable. CI deploys `database/` on every push to `main`, using the `CONVEX_DEPLOY_KEY` repository secret. If Convex is down the server answers 500, which leaves devices signed in and still working from their own copy. Devices keep a full copy of the progress and send all of it whenever they sign in, so clearing the `sessions` table is enough to have them restore a lost database.

Only `/health` and the icon answer without signing in. Responses are marked `private`, so that Cloudflare can't cache them and serve them to someone who hasn't signed in.

To set it up, clone this repository into `~/german-apps`, write `convex.json`, then run `server/deploy/install.sh`, which also asks for the password. To change the password and sign every device out, run `server/deploy/set_password.sh`.

## Content pipeline

The scripts in `tools/` produced the committed files in `data/` and `content/`, so you only need them to change the content. They use `uv` and need `GEMINI_API_KEY`.

1. Download the three Goethe word list PDFs and convert them with `pdftotext -layout` into `tools/source_lists/`. The expected filenames are in `tools/normalize_nouns.py`. They aren't in this repository because they are the Goethe-Institut's copyrighted documents.
2. `uv run normalize_nouns.py` extracts the noun entries. `uv run enrich_nouns.py` and `uv run fix_nouns.py` merge them and add translations, categories and image briefs. `uv run check_nouns.py` reports disagreements with Wiktionary. `normalize_verbs_adjectives.py`, `enrich_verbs_adjectives.py` and `fix_verbs_adjectives.py` do the same for verbs and adjectives, preferring the conjugations printed in the lists. `normalize_others.py` and `enrich_others.py` cover every other word, and add a few essentials the lists take for granted, such as `du`, `uns` and `im`.
3. `uv run make_audio.py` generates the clips with Gemini TTS, reading 25 words per request because the API quota counts requests per day. Use `--model` to switch model when a daily quota runs out. Then `uv run check_audio.py` transcribes each clip back to catch mistakes.
4. `uv run image_jobs.py plan 50` writes image batches, which Codex agents render with their built-in image tool. `uv run image_jobs.py check` validates the results, `uv run check_images.py` flags readable text or the wrong subject, and `uv run check_holes.py` finds the blurred holes the generator can leave in flat areas.
5. `uv run make_example_jobs.py 195` writes batches of words, and Codex agents write their phrases and sentences. `uv run check_examples.py --merge` checks their form and writes `data/examples.json`, then `uv run qa_examples.py` has Gemini proofread them, and its reports are reviewed by hand. `data/glosses.json` holds translations widened with a common second meaning, and it wins over the generated lists. Words sharing a meaning count as synonyms, so a new one must not link unrelated words.
6. `uv run rank_frequency.py` looks up each word in [DeReWo](https://www.ids-mannheim.de/derewo) for `data/lemma_classes.json`. The learning order in `vocabulary.py` goes level by level and, within a level, averages its lemma counts with [wordfreq](https://github.com/rspeer/wordfreq), which counts spellings.
7. `uv run build_assets.py` packages everything into `content/`. `uv run make_icon.py` writes the app icon in every size, from `assets/icon-source.png`.

Regional words marked in the lists as Austrian or Swiss come last in the learning order and are labelled in the app.

`data/lemma_classes.json` is adapted from the Korpusbasierte Wortgrundformenliste DeReWo, v-ww-bll-320000g-2012-12-31-1.0, © Institut für Deutsche Sprache, Programmbereich Korpuslinguistik, Mannheim, under [CC BY-NC 3.0](https://creativecommons.org/licenses/by-nc/3.0/).
