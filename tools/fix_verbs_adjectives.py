"""Hand-reviewed corrections applied after enrich_verbs_adjectives.py. Re-run after it."""
import json
from pathlib import Path

PATH = Path(__file__).parent.parent / "data/verbs_adjectives.json"

# Determiners and adverbs that slipped through as adjectives.
DROP = {"adj_ander", "adj_besonders", "adj_gar", "adj_meist", "adj_saemtlich"}

# These only occur with an ending, so the list prints "letzt-". Show the form a learner would actually say.
ENDINGS = {
    "adj_letzt": "letzte", "adj_naechst": "nächste", "adj_link": "linke", "adj_recht": "rechte", "adj_ober": "obere",
    "adj_inner": "innere", "adj_mittler": "mittlere", "adj_vorder": "vordere", "adj_eigen": "eigene",
    "adj_frueher": "frühere", "adj_heutig": "heutige",
}

# Generated comparatives that are grammatical but never used.
NOT_COMPARED = {
    "alternativ", "alltäglich", "bestimmt", "demokratisch", "einheitlich", "falsch", "freiwillig", "geheim", "gespannt",
    "gewohnt", "global", "heimlich", "historisch", "ideal", "individuell", "interkulturell", "international", "kulturell",
    "künstlich", "menschlich", "möglich", "natürlich", "offiziell", "öffentlich", "perfekt", "politisch", "rechte",
    "richtig", "sinnlos", "vordere", "wahrscheinlich", "wertlos", "willkommen", "wirklich", "zahlreich", "zentral",
    "zufällig", "zugänglich",
}
FORMS = {"adj_neu": "neuer, am neuesten"}
ALTERNATIVES = {"verb_verbieten": []}

# Reviewed images whose first brief led to giveaway text or the wrong subject.
BRIEFS = {
    # Crowds run off the canvas, and written words give the answer away.
    "verb_ankommen": "A traveller with a rolling suitcase stepping down from an open train door, waving hello.",
    "verb_auffallen": "One person in a bright yellow raincoat standing in a short row of three people in dark grey suits.",
    "verb_buchstabieren": "A boy speaking with a row of blank coloured wooden toy blocks floating out of his speech bubble one by one.",
    "verb_einfuegen": "A mouse cursor dragging a coral rectangle into a gap between two grey blocks of lines on a screen.",
    "verb_ergaenzen": "A hand with a pencil filling the one empty slot in a row of coloured puzzle pieces.",
    "verb_erkennen": "A person spotting a friend from a distance and pointing with a surprised smile, with a magnifying glass in the other hand.",
    "verb_kennen": "Two acquaintances smiling and waving at each other, with a small friendly speech bubble holding a tick between them.",
    "verb_rechnen": "A student pressing buttons on a pocket calculator, with a notepad of plus and equals symbols beside it.",
    "verb_schreiben": "A hand gripping a fountain pen and drawing flowing wavy lines on a notepad page.",
    "verb_streiken": "Two factory workers standing in front of a closed gate holding blank placards in the air.",
    "verb_tun": "A person juggling three gears with motion lines around them.",
    "verb_ueberzeugen": "A presenter pointing at a rising bar chart while one listener nods, with a light bulb above the listener's head.",
    "verb_zusammenfassen": "A person at a desk pressing three loose sheets of grey lines into one small coral card.",
    "verb_zuschauen": "A spectator sitting on a chair and leaning forward with wide eyes, watching a small stage with a performer on it.",
    "adj_beliebt": "A smiling person with three friends around them, all giving thumbs up, with floating hearts above.",
    "adj_freiwillig": "A person eagerly stepping forward from a line of two grey figures, with one hand raised.",
    "adj_interessiert": "A person leaning forward with their chin on one hand and wide attentive eyes, looking at a glowing book.",
    "adj_populaer": "A smiling singer with a microphone on a small stage, with a large gold star above and many floating hearts around.",
}

words = [w for w in json.loads(PATH.read_text()) if w["id"] not in DROP]
for word in words:
    if word["id"] in ENDINGS:
        stem, word["lemma"] = word["lemma"], ENDINGS[word["id"]]
        word["alternatives"] = sorted({*word["alternatives"], stem, stem + "er", stem + "es", stem + "en"} - {word["lemma"]})
    if word["lemma"] in NOT_COMPARED:
        word["forms"] = ""
    word["forms"] = FORMS.get(word["id"], word["forms"])
    word["alternatives"] = ALTERNATIVES.get(word["id"], word["alternatives"])
    word["image_brief"] = BRIEFS.get(word["id"], word["image_brief"])
ids = {w["id"] for w in words}
assert not (ENDINGS.keys() | FORMS.keys() | ALTERNATIVES.keys() | BRIEFS.keys()) - ids, "unknown id"
PATH.write_text(json.dumps(words, ensure_ascii=False, indent=1))
print(len(words), "words")
