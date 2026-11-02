"""Hand-reviewed corrections applied after enrichment. Re-run after enrich_nouns.py."""
import json
from pathlib import Path

PATH = Path(__file__).parent.parent / "data/nouns.json"
LEVEL = {"der_senior": "B1", "das_abgas": "B1"}
ACCEPTED = {"die_ecke": ["die"]}
DUPLICATES = {"das_comic"}  # Same word as der_comic.

# Reviewed images whose first brief led to giveaway text or the wrong subject.
BRIEFS = {
    "der_cent": "A small copper coin with only a large numeral 1 on it.",
    "das_taxi": "A yellow taxi car with a blank glowing roof sign and a checkered stripe along its side.",
    "der_titel": "A closed book whose cover has a large blank golden label at the top, with a pointing arrow towards that label.",
    "der_halt": "A person's raised open palm in a clear stop gesture, beside a red octagonal sign with a white horizontal bar.",
    "die_adresse": "A white envelope on a coral backing with a stamp and a location pin, where the address is shown only as three wavy grey lines.",
    "das_zeugnis": "A school report sheet with a ribbon seal, showing only rows of subject icons with the numerals 1, 2 and 3 beside them.",
    "das_kapitel": "An open book with a coloured ribbon, where one section of pages is highlighted in a contrasting colour. The page content is only grey lines.",
    "die_einleitung": "A single page where only the first short paragraph is highlighted in yellow and the rest is pale grey lines. Lines only.",
    "die_zone": "A fenced-off construction zone on a patch of textured grass with tufts, marked out by orange traffic cones and a striped barrier, with a small red flag at one corner.",
    "der_uebersetzer": "A man at a desk between two speech bubbles, one with a German flag and one with a French flag, with a two-way arrow between them.",
    "die_visitenkarte": "A hand holding out a small business card that shows a small person icon and grey lines.",
    "das_einschreiben": "A sealed envelope with a red wax seal, a handwritten signature squiggle on a slip and a pen beside it.",
    "die_theorie": "A chalkboard showing only a diagram of arrows connecting circles, with a glowing light bulb above it.",
    "das_land": "A single country shape filled with fields, a small village and a river, outlined by a bold border line.",
    "das_beispiel": "A row of five identical grey cubes, one of them coral and lifted forward on a small pedestal.",
    "die_wohnung": "A cutaway apartment block where one apartment is brightly lit and shows a sofa, a bed and a kitchen inside, while the other units are grey.",
    "der_winter": "A snow-covered fir tree and a snowman with a scarf, with dusty blue shadows on the snow.",
    "das_papier_material": "A thick stack of paper sheets, seen at an angle, with blue-grey shadows between the sheets and on the side.",
    "das_training": "A sporty woman doing a dumbbell exercise while a coach with a whistle and clipboard stands beside her.",
    "der_hals": "A person in side profile with the neck area highlighted in coral, with a small arrow pointing at it.",
    "die_mailbox": "A smartphone showing a large phone receiver icon and a speech bubble with a sound wave.",
    "der_bereich": "A plain map with one neighbourhood-sized region highlighted in teal with a bold outline.",
    "das_wunder": "A flower blooming from a crack in a dry rock, surrounded by golden sparkles and rays of light.",
    "die_einfuehrung": "A friendly guide showing a new colleague through an open door, with one arm gesturing into the room.",
    "die_wunde": "A knee with a small red scrape, shown non-graphically, next to an open first-aid kit.",
    "die_vollzeit": "An office worker at a desk under an arc running from a rising sun to a setting sun, where the whole arc is filled in teal.",
    "der_bundespraesident": "A dignified man in a suit speaking at a lectern, standing beside a German black, red and gold flag.",
    "der_himmel": "White clouds and a small sun against an irregular soft blue sky shape with no straight edges.",
    "der_keller": "A cutaway house where the underground cellar is lit, with stairs leading down to shelves of jars.",
    "das_feld": "A rounded patch of farmland with neat rows of crops and a small tractor, with no background.",
    "die_mehrwertsteuer": "A shopping receipt with a price tag beside a large percent symbol being added with a plus sign.",
    "der_einer": "A large navy numeral 1 standing on a small pedestal.",
    "die_linie": "A single thick smooth coral line curving across the canvas from lower left to upper right.",
    "die_luege": "A smiling person holding one hand behind their back with their fingers crossed.",
    # These came back with blurred holes, which the generator leaves in large plain flat areas.
    "der_rand": "A notebook page with a bold coral strip along its left edge and a small pencil pointing at that edge. The page shows grey lines.",
    "der_preis": "A cream price tag on a string, with a small stack of gold coins drawn on the tag.",
    "die_mehrheit": "A group of ten small people, seven in teal clothes standing together and three in grey clothes to one side.",
    "die_margarine": "An open tub of yellow margarine with swirls in it, a butter knife resting on the rim, and the lid decorated with a sunflower.",
    "der_chat": "Two overlapping speech bubbles, one coral and one teal, each containing three white dots.",
    "die_hauptstadt": "A country shape in sage green dotted with small trees, with a tall city skyline and a large red star at its centre.",
    "die_runde": "A runner on a red oval running track around a green infield with white field markings.",
    "die_zustimmung": "A hand giving a thumbs up beside a person nodding, with a small green tick badge.",
    "das_fach": "A wooden shelf unit of square compartments, where each compartment holds something different: books, a box, a folder and a plant.",
    "der_lehrer": "A male teacher with a pointer beside a blackboard covered in white chalk drawings of a triangle, a circle and a square.",
    "die_unterhaltung": "Two people sitting facing each other and talking, with a speech bubble containing three dots between them.",
    "die_antwort": "A person with a raised hand and a big smile, next to a speech bubble containing a tick mark.",
    "das_video": "A video player window showing a colourful scene of mountains and a sun, with a white play button and a progress bar.",
    "die_tafel": "A blackboard on a wooden stand, covered in white chalk drawings of a triangle, a circle and a square, with chalk on the tray.",
    "das_verhaeltnis": "Two people joined by a looping ribbon that forms a heart between them.",
    "die_professorin": "A woman professor with glasses holding books beside a blackboard covered in a white chalk atom diagram and geometric shapes.",
    "der_notausgang": "An open door with a person running through it, with a green arrow above the door.",
    "die_flucht": "A person running away through an open gate in a tall fence, looking back over their shoulder.",
    "das_zeichen": "A round traffic sign on a metal pole, with a thick red border and a bold black arrow on a textured white face.",
    "die_vorfahrt": "A yellow diamond priority road sign with a white border, mounted on a metal pole at a crossroads.",
}

words = [w for w in json.loads(PATH.read_text()) if w["id"] not in DUPLICATES]
for word in words:
    word["level"] = LEVEL.get(word["id"], word["level"])
    word["image_brief"] = BRIEFS.get(word["id"], word["image_brief"])
    word["accepted_articles"] = ACCEPTED.get(word["id"], word["accepted_articles"])
    if word["id"] == "der_comic":
        word["accepted_articles"] = ["das", "der"]
assert not [w["id"] for w in words if w["level"] == "UNMATCHED"]
assert not BRIEFS.keys() - {w["id"] for w in words}
PATH.write_text(json.dumps(words, ensure_ascii=False, indent=1))
print(len(words), "words")
