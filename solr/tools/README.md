Lesbar machen in VS Code: `Strg+Shift+V` (Vorschau), `Strg+K V` (Vorschau daneben).

# Werkzeuge für das Basis-Configset

Zwei Skripte erzeugen die Dateien in `solr/configsets/app1/conf/lang/`, die der Volltext-Typ `text_suche` für die
Komposita-Zerlegung braucht. Beide laufen mit Python 3, ohne Pakete. Quellen und Lizenzen stehen in
`conf/lang/LICENSE_*.txt`.

| Skript | Quelle | erzeugt |
|---|---|---|
| `build_hyph_dehyphn.py` | `dehyphn.tex` Rev. 31 von CTAN (`language/hyphenation/dehyph/dehyphn.tex`, LPPL) | `hyph_de.xml`, die Trennmuster im OFFO-Format |
| `build_compound_files.py` | `de_DE_frami.dic` aus dem LibreOffice-Wörterbuch-Repository (`de/de_DE_frami.dic`, GPL) | `compound_de.txt`, die Substantive plus „-ung“-Bildungen |

```
python -I build_hyph_dehyphn.py dehyphn.tex ../configsets/app1/conf/lang
python -I build_compound_files.py de_DE_frami.dic ../configsets/app1/conf/lang
```

Ein Kern bekommt die Dateien nur beim Anlegen aus dem Configset kopiert. Nach einer Änderung heißt das: Kern neu
aufbauen (`docs/backend/solr.md`, Neuaufbau eines Kerns).

Warum die alten Trennmuster und nicht hyph-de-1996 aus tex-hyphen (MIT): Lucenes Trennmuster-Baum zählt seine Knoten
in einem 16-Bit-`char`, mehr als 65.535 gibt es nicht. Die 36.709 modernen Muster brauchen mehr, der Zähler läuft über,
der Baum bekommt Zyklen, das Laden endet im Stapelüberlauf oder mit falschen Trennstellen. Die 5.801 klassischen Muster
passen und trennen Komposita sauber genug, um sie an den Fugen zu zerlegen.
