"""Baut aus dem heruntergeladenen Hunspell-Woerterbuch die Wortliste fuer die Komposita-Zerlegung im Solr-Configset.

Aufruf: python -I build_compound_files.py <frami.dic> <ausgabe-ordner>

compound_de.txt: die Teile, in die ein Kompositum zerlegt werden darf (DictionaryCompoundWordTokenFilter, dictionary):
- alle grossgeschriebenen Eintraege des Woerterbuchs, also die Substantive, ohne Affix-Flags, klein, nur Buchstaben,
  mindestens 4 Zeichen; kleingeschriebene Eintraege (Verben, Adjektive, Partikel wie "eil", "nun", "ran") bleiben
  draussen, sie erzeugen beim Zerlegen nur Muell;
- dazu die "-ung"-Substantive, die das Woerterbuch nicht als Eintrag fuehrt, sondern per Affix-Flag J aus dem Verb
  bildet (rechnen -> Rechnung, bestellen -> Bestellung), nach den Regeln der .aff-Datei.
Das Woerterbuch ist absichtlich eine Liste von Grundformen: Stuende ein ganzes Kompositum selbst darin, waere es der
laengste Treffer und wuerde nicht mehr zerlegt.
"""

import re
import sys
from pathlib import Path

dic_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
out_dir.mkdir(parents=True, exist_ok=True)

word_pattern = re.compile(r"^[a-zäöüß]{4,}$")


def ung_nouns(verb):
    """Die -ung-Bildungen der Regel SFX J aus de_DE_frami.aff, nur die Einzahl"""
    if verb.endswith("eln"):
        return [verb[:-3] + "lung"]
    if verb.endswith("ern"):
        return [verb[:-1] + "ung"]
    if verb.endswith("en"):
        return [verb[:-2] + "ung"]
    if verb.endswith("el"):
        return [verb[:-2] + "lung"]
    if not verb.endswith("n"):
        return [verb + "ung"]
    return []


nouns = set()
derived = set()
with dic_path.open(encoding="iso-8859-1") as dic:
    next(dic)  # erste Zeile: die Anzahl
    for line in dic:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        entry = line.split("\t", 1)[0]
        word, _, flags = entry.partition("/")
        if word[:1].isupper():
            lowered = word.lower()
            if word_pattern.match(lowered):
                nouns.add(lowered)
        elif "J" in flags:
            for noun in ung_nouns(word.lower()):
                if word_pattern.match(noun):
                    derived.add(noun)

words = nouns | derived
compound_path = out_dir / "compound_de.txt"
with compound_path.open("w", encoding="utf-8", newline="\n") as out:
    out.write("# Woerterbuch fuer die Komposita-Zerlegung (DictionaryCompoundWordTokenFilter, dictionary): die Substantive\n")
    out.write("# des de_DE_frami Hunspell-Woerterbuchs (igerman98 plus frami), klein, ohne Affix-Flags, dazu die -ung-Substantive,\n")
    out.write("# die das Woerterbuch per Affix aus Verben bildet. Lizenz GPLv2/GPLv3, siehe LICENSE_compound_de.txt.\n")
    out.write("# Ein Kompositum wird nur in Teile zerlegt, die hier stehen. Erzeugt mit build_compound_files.py.\n")
    for word in sorted(words):
        out.write(word + "\n")
print(f"compound_de.txt: {len(nouns)} Substantive + {len(derived)} -ung-Bildungen = {len(words)} Woerter")
