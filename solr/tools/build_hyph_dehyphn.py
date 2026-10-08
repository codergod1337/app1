"""Wandelt die klassischen deutschen TeX-Trennmuster dehyphn.tex (Rev. 31, LPPL) ins OFFO-XML-Format fuer Lucenes
HyphenationCompoundWordTokenFilter.

Aufruf: python -I build_hyph_dehyphn.py <dehyphn.tex> <ausgabe-ordner>

Warum nicht die modernen Muster hyph-de-1996 (36.709 Muster): Lucenes TernaryTree zaehlt seine Knoten in einem 16-Bit-char,
mehr als 65.535 Knoten gibt es nicht. Die modernen Muster brauchen mehr, der Zaehler laeuft ueber, der Baum bekommt
Zyklen, das Laden endet im StackOverflowError oder liefert falsche Trennstellen. Die klassischen Muster sind ein
Viertel so gross und passen.

Format in dehyphn.tex: Block \\patterns{ ... }, Muster durch Leerraum getrennt, % leitet einen Kommentar ein, Umlaute
als "a "o "u innerhalb von \\n{...}, das ß als Makro \\3.
"""

import re
import sys
from pathlib import Path

tex_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
out_dir.mkdir(parents=True, exist_ok=True)

lines = tex_path.read_text(encoding="utf-8", errors="replace").splitlines()
start = next(i for i, l in enumerate(lines) if l.startswith("\\patterns"))
patterns = []
unknown = set()
for line in lines[start + 1:]:
    stripped = line.strip()
    if stripped.startswith("}"):
        break
    body = line.split("%", 1)[0]
    for token in body.split():
        token = re.sub(r"\\n\{([^}]*)\}", r"\1", token)
        token = token.replace('"a', "ä").replace('"o', "ö").replace('"u', "ü").replace("\\3", "ß")
        if "\\" in token or "{" in token or "}" in token:
            unknown.add(token)
            continue
        patterns.append(token)

letters = sorted({c for p in patterns for c in p if not c.isdigit() and c != "."})
classes = "\n".join(f"{c}{c.upper()}" if len(c.upper()) == 1 and c.upper() != c else c for c in letters)
out_path = out_dir / "hyph_de.xml"
with out_path.open("w", encoding="utf-8", newline="\n") as out:
    out.write('<?xml version="1.0" encoding="UTF-8"?>\n')
    out.write('<!DOCTYPE hyphenation-info SYSTEM "hyphenation.dtd">\n')
    out.write("<!-- Deutsche Trennmuster (neue Rechtschreibung) aus dehyphn.tex Rev. 31 (LPPL), ins OFFO-Format gebracht fuer\n")
    out.write("     Lucenes HyphenationCompoundWordTokenFilter. Lizenz und Herkunft: LICENSE_hyph_de.txt. Erzeugt mit\n")
    out.write("     build_hyph_dehyphn.py. Die modernen Muster hyph-de-1996 sind zu gross fuer Lucenes Baum (16-Bit-Knotenzaehler). -->\n")
    out.write("<hyphenation-info>\n")
    out.write('<hyphen-min before="2" after="2"/>\n')
    out.write("<classes>\n" + classes + "\n</classes>\n")
    out.write("<patterns>\n")
    for p in patterns:
        out.write(p + "\n")
    out.write("</patterns>\n")
    out.write("</hyphenation-info>\n")
print(f"hyph_de.xml: {len(patterns)} Muster, Zeichen: {''.join(letters)}")
if unknown:
    print(f"nicht verstandene Token ({len(unknown)}): {sorted(unknown)[:20]}")
