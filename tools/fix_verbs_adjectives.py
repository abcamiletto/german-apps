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
    # The generator refuses the first brief for "lügen", as it did for "die Lüge".
    "verb_luegen": "A smiling person talking with one hand behind their back and their fingers crossed.",
    "verb_unterlassen": "A person stopping themselves with a hand pulled back from a big red button, shaking their head.",
    # Reviewed images that missed the meaning.
    "adj_normal": "A round gauge whose needle rests in the middle green zone, with pale grey zones on either side.",
    "verb_buchstabieren": "A boy speaking with three wooden toy blocks showing the letters A, B and C floating out of his speech bubble one by one.",
    "adj_wahrscheinlich": "A balance scale tipped far down towards a big green tick, with a small grey cross on the raised side.",
    "adj_total": "A finished round jigsaw puzzle of many colourful pieces with no piece missing, with a small green tick beside it.",
    "adj_voellig": "A battery with every segment filled in bright green, and a small sparkle beside it.",
    "adj_typisch": "A row of four identical pale grey houses with pointed roofs, followed by a fifth identical house in full colour with a green tick above it.",
    "adj_duenn": "A very thin bright red pencil in full colour, standing next to a very fat pale grey pencil.",
    "verb_aussprechen": "A woman pointing at her own lips while speaking slowly, with a speech bubble that holds a sound wave.",
    "verb_eintragen": "A hand with a pen filling in the empty line of a table in an open register, where the handwriting is only a wavy line.",
    "adj_doppelt": "A single domino tile lying flat, with six dots on each of its two halves.",
    "adj_einzig": "One bright gold star in a row of empty dashed outlines where other stars are missing.",
    "adj_gewohnt": "A relaxed person walking along a deep worn path looping through grass, with a trail of old footprints ahead of them.",
    "verb_annehmen": "One person holding out a wrapped gift while another person takes it with both hands and a grateful smile.",
    "adj_intensiv": "A large blazing orange flame with bold radiating lines, next to a tiny pale grey flame.",
    "verb_beeinflussen": "A person whispering into another person's ear, while the listener's thought bubble changes from grey to bright colour.",
    "adj_betrunken": "A grown man with a red nose swaying and holding an empty beer glass, with wobbly spirals above his head.",
    "adj_mobil": "A tiny house on wheels being towed along by a small car.",
    "verb_ausschliessen": "Three friends standing together inside a bold circle, with a fourth person in pale grey left outside the circle.",
    "adj_kuenstlich": "A small robot holding a stiff bright plastic flower with a visible price tag and a plug on its stem.",
    "adj_gesamt": "A whole round pie with all eight slices marked, under one big curved bracket spanning the entire pie.",
    "adj_aeusserlich": "A person in front of a standing mirror straightening their jacket and hair.",
    "adj_mager": "A lean skinless chicken breast in full colour, next to a smaller fatty slice of bacon in pale grey.",
    "adj_sogenannt": "A person raising both hands and making air quotes with their fingers, with a raised eyebrow and two large quotation marks beside them.",
    "verb_ausstellen": "A framed painting on a wooden easel under a spotlight, with a visitor admiring it.",
    "adj_weiss": "A bold splash of white paint with soft pale-grey shading on its droplets.",
    "adj_ruhig": "A person sitting cross-legged with closed eyes on a small round green hill, with a still pond beside them.",
    "adj_national": "A tall flagpole with a large plain two-colour flag, planted in the middle of a sage green country shape with a bold border.",
    # Same idea, with the flat areas broken up, because the generator punched blurred holes in them.
    "verb_verstecken": "A child crouching behind a green armchair with a bold diamond pattern on it, peeking around its side.",
    "adj_flach": "A wide flat green plain covered with tufts of grass and small flowers, next to a small grey hill far behind it.",
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
