Beide Agenten laufen noch. Sobald ihre Zusammenfassungen da sind, stelle ich den Gesamtüberblick über den AbasManager der Vorlage und den Einbettungsplan für app1.abas.* zusammen.



3:48 AM
Ich habe alles nur gelesen und nichts verändert. Das Abas-Admin-Modul hat 10 Tabs. Alle Aufrufe laufen über useAbasAdmin.js, und kein Tab streamt per fetch. Zwei Tabs pollen: Queue alle 5 s, Fetch-Steuerung alle 10 s. NDJSON-Ströme gibt es nur im Import, nativen Download nur im Export. Am Ende stehen die Widersprüche, die mir im Code aufgefallen sind.

1. Routing und Rollen-Wächter
Router: C:\Users\Bindemaja\Documents\dev\hobby\home-plus-extern\intern-instance\frontend\src\App.jsx, Zeile 620–643. Es ist die App.jsx, nicht die main.jsx.


/abasadmin → <RequireLogin><RequireAbasAdmin><AbasAdminLayout/></RequireAbasAdmin></RequireLogin>
  index        → Navigate "uebersicht"
  uebersicht   → UebersichtTab
  queue        → QueueTab
  queuefehler  → QueueFehlerTab
  log          → EmpfangslogTab
  multipart    → DateieingaengeTab
  fetchcontrol → FetchControlTab
  import       → ImportTab
  export       → ExportTab
  resync       → ResyncTab
  mapper       → MapperTab
RequireAbasAdmin.jsx

Solange der Cookie-Restore läuft (!sessionRestoreAttempted), zeigt er nichts (null) und leitet nicht vorschnell um.
Er lässt durch, wenn sessionInfo.rollen ABAS_ADMIN oder ADMIN enthält. Sonst geht es per <Navigate to="/" replace /> auf die Startseite.
Das ist reine Anzeige-Absicherung. Die echte Prüfung macht die SecurityConfig im Backend an /api/rest/abasadmin/**.
Startseiten-Kachel: In pages\home\kachelKatalog.js hat die Kachel ABAS_ADMIN die Rollen ['ABAS_ADMIN','ADMIN'] und das Ziel /abasadmin.

AbasAdminLayout.jsx: SubHeader, darunter <main className="content"><Outlet/></main>.

AbasAdminSubHeader.jsx:

Modulname „ABAS Admin“ und 10 NavLinks, in dieser Reihenfolge: Übersicht, Queue, Queue-Fehler, Empfangslog, Dateieingänge, Fetch-Steuerung, Import, Export, Resync, Mapper.
Pin-Knopf: angepinnt ist der Standard. Der sticky-top ist die gemessene Höhe von .app-header.
Der Kommentar spricht noch von „drei Tabs“ und ist veraltet.
abasFormat.js: Enthält nur formatInstant(iso). Es macht aus einem ISO-Instant toLocaleString('de-DE'), liefert '' bei null und den Rohtext bei ungültigem Datum. Die Funktion wird auch in ArtikelmappeSeite und KundenmappeSeite importiert.

2. Tab „Übersicht“ (UebersichtTab.jsx)
Beim Laden:

Einmalig: adminGetTypKatalog() → GET /api/rest/abasadmin/typkatalog. Ein Fehler wird stumm geschluckt, dann bleibt das Auswahlfeld leer.
lade(text, typ): ruft nacheinander
adminGetStats() → GET /stats
adminSearchSternzeiten(text, typ) → POST /sternzeitsearch mit Body {suchtext, objectType}. Die Antwort ist {treffer[], gesucht, abgeschnitten}.
Danach wird auf Seite 0 gesprungen. Beim Mount läuft lade('', '').
Es gibt kein Polling.
Kopf der Contentbox „ABAS Sternzeiten“:

Freies Eingabefeld, Platzhalter (87448,19,0).
Knopf „einreihen“ (SpeichernKnopf, löst auch bei Enter aus): adminEnqueueSternzeit → POST /sternzeiten mit {sternzeit}. Danach wird das Feld geleert und die Liste neu geladen.
Knopf „loeschen“ (rot, nicht per Enter): confirm („…zum Löschen einreihen? Der Sync löst die Kern-Dokumente auf…“), dann adminDeleteSternzeit → DELETE /sternzeiten mit Body {sternzeit}. Danach Neuladen, Fehler kommen per alert.
Badges (Stats):

Sternzeiten: sternzeitenGesamt
ohne Objekttyp: ohneObjectType
Queue: queueGesamt
in Arbeit: queueInArbeit
mit Fehlern: queueMitFehlern
Fetchen PAUSIERT (rot) bzw. aktiv (grün) nach fetchenPausiert
Filterzeile:

Auswahl Objekttyp:
„alle Objekttypen“
je Katalogeintrag die Zeile "{datenbank} — {bezeichnung}", mit dem Zusatz „(ohne Mapper)“, wenn !hatHandler
Sonderwert OHNE_OBJECT_TYPE = „noch nie gefetcht“
Ein Wechsel lädt sofort neu und leert den Suchtext, weil die Primärschlüssel-Suche keinen Typfilter kennt.
Suchfeld: nur die volle Klammerform. Der Knopf „suchen“ startet lade(suchtext, objectType).
„zurueckZurListe“: erscheint nur, solange ein Suchtext steht.
Tabelle: Sternzeit | Objekttyp | zuletzt gepusht | zuletzt gefetcht | im Kern seit | Fetches | Aktionen

Objekttyp ist objectType, oder gedimmt „noch nicht gefetcht“.
Die Zeitspalten kommen aus abasLastSent, lastFetchFromAbas und syncDatum. Ein fehlendes syncDatum zeigt gedimmt „kein Mapper“.
Fetches ist fetchCounter.
Aktionen je Zeile:
„fetch“: adminEnqueueSternzeit.
AugeKnopf: window.open(apiUrl('/api/rest/abasadmin/viewresponse?sternzeit=…'), '_blank', 'noopener'). Das ist eine GET-Variante im neuen Browser-Tab, das Cookie fährt mit. Sie unterscheidet sich vom POST im Hook.
Papierkorb: dasselbe loeschen() mit confirm.
Blättern und Fußzeile:

Geblättert wird im Browser, 100 Zeilen pro Seite (PRO_SEITE) über die bis zu 1000 geladenen Treffer, mit der Pagination des Hauses.
Die Fußzeile zeigt „Suchergebnis zum Primärschlüssel.“ oder „Die zuletzt gefetchten N Sternzeiten…“. Bei abgeschnitten kommt ein Hinweis „Es gibt mehr…“ dazu.
Leere Fälle:

gesucht → „Zu dieser Sternzeit gibt es keinen Stammsatz…“
sonst → „Hier liegt noch nichts.“
3. Tab „Queue“ (QueueTab.jsx)
Polling alle 5 s (POLL_MS = 5000) per setInterval, dazu sofort beim Mount: adminGetQueue(100) → GET /queue?limit=100.
Knopf „aktualisieren“ im Kopf (ohne laeuft-Anzeige).
Tabelle: Sternzeit | Methode | eingereiht | in Arbeit seit | Versuche | letzter Fehler | Aktionen

Methode: DELETE als rotes Badge, sonst POST als Haupt-Badge.
eingereiht ist queuedAt.
in Arbeit seit ist claimedAt, sonst gedimmt „wartet“.
Versuche ist attempts, rotes Badge wenn > 0.
letzter Fehler ist letzterFehler.
Aktionen:

WiederholenKnopf ist gesperrt bei attempts === 0. Er läuft ohne Rückfrage: adminRetryQueueEintrag → POST /queueretry mit {sternzeit}, danach Neuladen.
Papierkorb fragt „wirklich entfernen?“ und ruft dann adminDeleteQueueEintrag → POST /queuedelete mit {sternzeit}. Der Stammsatz bleibt.
Fußzeile: Aktualisierungstakt, die vordersten 100 in FIFO-Reihenfolge und ein Verweis auf den Reiter Queue-Fehler.

4. Tab „Queue-Fehler“ (QueueFehlerTab.jsx)
Kein Polling, weil die Abfrage die teure ist (attempts > 0 ohne Index). Geladen wird einmal beim Mount und dann per Knopf „aktualisieren“ (mit laeuft): adminGetQueueFehler(100) → GET /queuefehler?limit=100.
Kopf-Badge rot: „N Sternzeiten“, bzw. „100+ Sternzeiten“, wenn genau das Limit erreicht ist.
Tabelle: Sternzeit | Versuche (rotes Badge) | eingereiht | letzter Fehler (voller Text, ungekürzt) | Aktionen.
Aktionen wie im Queue-Tab: Wiederholen (/queueretry) und Papierkorb (/queuedelete mit confirm).
Leerer Fall in grün: „Keine Sternzeit hat einen Fehlversuch — die Queue läuft sauber.“
Die Fußzeile nennt fest „nach 10 Fehlversuchen nicht mehr geholt“.
5. Tab „Empfangslog“ (EmpfangslogTab.jsx)
Kein Polling.
lade(sternzeit) holt beide Protokolle parallel (Promise.all):
adminGetLog(sternzeit, 100) → GET /log?limit=100[&sternzeit=…]
adminGetAufloesungen(sternzeit, 100) → GET /aufloesungen?limit=100[&sternzeit=…]
Kopf: ein Suchfeld mit Hoverlay „Exakte Sternzeit - volle Klammerform“. Es filtert beide Tabellen. Knopf „aktualisieren“ (type submit).
Tabelle 1 (Pushes): Sternzeit | Methode (DELETE rot / POST) | empfangen (empfangenAt).
Leer: „Zu dieser Sternzeit steht kein Push im Protokoll.“ bzw. „Das Protokoll ist leer…“.
Tabelle 2 „Auflösungen (DELETE-Weg)“ erscheint nur, wenn es Einträge gibt: Sternzeit | Kern | doc.id | Handler | Quelle | aufgelöst.
Ein fehlendes docId heißt gedimmt „nichts zu löschen“.
Quelle ABAS ist das Haupt-Badge, jede andere Quelle ein oranges Badge.
Aufräumen im Fuß:
Datumsfeld (type="date"). Ein reiner ABAS_ADMIN bekommt max = heute minus 14 Tage, ein voller ADMIN hat keine Grenze. Das ist nur Komfort, erzwungen wird es im Backend.
Knopf „loeschen“ (rot) fragt per confirm nach und ruft adminRaeumeLogAuf(iso) → DELETE /log mit Body {stichdatum} oder {}.
Das Stichdatum wird als lokale Mitternacht in einen ISO-Instant umgerechnet.
Danach alert: „X Push-Einträge und Y Auflösungen gelöscht.“, dann Neuladen.
6. Tab „Dateieingänge“ (DateieingaengeTab.jsx)
Kein Polling. adminGetDateieingaenge(seite, groesse, sternzeit) → GET /multipart?seite=…&groesse=…[&sternzeit=…]. Die Antwort ist {zeilen, gesamt}.
Server-seitiges Blättern (Offset) mit der Haus-Pagination. Seitengrößen 25/50/100/250, Standard 50. Ein Größenwechsel setzt auf Seite 0 zurück.
Ein Effekt lädt bei Wechsel von Seite oder Größe neu. Das Absenden der Suche lädt Seite 0, entweder direkt oder über setSeite(0).
Tabelle: Klapp-Pfeil | Sternzeit | bezugsNummer | Weg | Datei (fileName · mimeType) | Größe | Status | Dateiart (fileSubClassKey) | doc.id | empfangen.
Größe wird als B/KB/MB formatiert.
Status-Badge: 201 = Haupt-Badge, 208 = grau, 502 = orange, alles andere rot. Ein Hoverlay zeigt meldung.
doc.id ist ein <Link> auf /dms/dokument/{docId}, gekürzt auf 12 Zeichen.
Aufklapp-Mechanik: Immer nur eine Zeile ist offen (aufgeklappt = id). Darunter erscheint eine volle Zeile (colSpan=10) mit „Meldung:“ und dem Body in einem <pre>. Ist der Body JSON, wird er mit JSON.stringify(...,2) eingerückt, sonst wörtlich gezeigt.
Die Fußzeile erwähnt das nächtliche Aufräumen nach 90 Tagen. Es gibt keinen Aufräum-Knopf.
7. Tab „Fetch-Steuerung“ (FetchControlTab.jsx)
Polling alle 10 s (POLL_MS = 10000): adminGetFetchControl() → GET /fetchcontrol.
Genutzte Felder der Antwort: pausiert, parallel, queueGesamt, queueInArbeit, verarbeitetGesamt, fehlerGesamt.
Durchsatzmessung (rein im Client):

Die Messpunkte {zeit, verarbeitet, fehler} liegen in einem useRef, behalten werden die letzten 6 (FENSTER).
Rate = (letzter − erster) / Sekunden. Das ergibt proSek und fehlerProSek.
Ab dem zweiten Punkt erscheint die Rate. Sechs Punkte im 10-s-Takt überspannen real etwa 50 s, nicht die im Kommentar genannte Minute.
Zählersturz: Ist verarbeitetGesamt kleiner als der letzte Punkt (Backend-Neustart), werden die Punkte geleert.
Anzeige:

Groß: „Abas-Abfragen pro Sekunde“ (2 Nachkommastellen) mit „gemittelt über X s“ bzw. „erster Wert nach 10 s“.
Daneben „davon Fehler pro Sekunde“, rot wenn > 0.
Der Regler „Worker parallel“ mit MinusKnopf, Zahl und PlusKnopf: adminSetParallel(stand.parallel ± 1) → POST /parallel mit {parallel}.
Es gibt keine Grenze im Client, das Backend klemmt auf 1..100.
Nach jeder Änderung wird die Messung zurückgesetzt.
Ein Satz zum Zustand, darunter die Badges Queue, in Arbeit, verarbeitet und Fehler (rot wenn > 0).
Knöpfe:

StartKnopf „einschalten“ bzw. StopKnopf „pausieren“: adminSetFetchen(true/false) → POST /fetchcontrol mit {fetchen}. Die Antwort ist der neue Stand.
ResetKnopf „messungZuruecksetzen“ leert nur den Client-Puffer.
Während einer Aktion sind alle Knöpfe gesperrt (schaltet).
Kopf-Badge: PAUSIERT (rot) bzw. aktiv (grün).
8. Tab „Import“ (ImportTab.jsx), zwei Contentboxen
Box A „Sternzeiten importieren“
Datei ablegen (FileDropzone):

Das Format ist egal. Genommen wird alles, was wie (zahl,zahl,zahl) aussieht.
Lokale Analyse (analysiereDatei):
Die Datei wird per File.stream() häppchenweise gelesen und mit TextDecoder im Stream-Modus dekodiert.
Gezählt wird mit dem Regex /\((\d+),(\d+),(\d+)\)/g, gruppiert nach der mittleren Zahl (= ABAS-Datenbank). Treffer über 30 Zeichen werden verworfen.
Chunk-Rand: Der Rest ab der letzten ( wird ins nächste Häppchen übernommen, außer er ist länger als 64 Zeichen.
Fortschritt: „Analysiere Datei … X %“ (gelesene Bytes / Dateigröße).
Danach:
Badge „gefunden: N“.
Je Datenbank eine Checkbox: "{nummer} — {bezeichnungen aus Typkatalog, mit ' / ' verbunden} ({anzahl})", mit dem Zusatz „— ohne Mapper“, wo nötig.
Vorausgewählt sind alle Datenbanken, für die der Typkatalog einen Mapper (hatHandler) meldet.
Ist nichts gewählt, wird alles eingereiht.
Der Typkatalog kommt einmalig von GET /typkatalog, Fehler werden stumm geschluckt.
Knopf „importieren“:

Zeigt den Zusatz (erwartet). Das ist die Summe der gewählten Typen, oder alles, wenn nichts gewählt ist.
adminImportSternzeiten(datei, gewaehlt, null, anZeile) → POST /sternzeitimport als Multipart mit datei und optional datenbanken="19,2,…".
NDJSON-Stream (Mechanik in api\rest\apiRest.js apiUpload):

Die Anfrage läuft per XHR und nicht per fetch.
xhr.onprogress zerlegt das wachsende responseText an \n. Ein Zeiger gelesenBis sorgt dafür, dass keine Zeile doppelt kommt. Jede Zeile wird per JSON.parse an anZeile übergeben.
In onload wird der Rest zerlegt, und das Promise löst mit der letzten Zeile auf.
Der Server schickt etwa alle 2 s {art:'fortschritt', gefunden, gefiltert, eingereiht}. Am Ende kommt {art:'fertig', …, uebersprungen} oder {art:'fehler', meldung}.
Der Tab zeigt den laufenden Stand als Badges (gefunden / gefiltert / eingereiht in grün). Vor der ersten Zeile steht „Datei geht hoch, der Lauf meldet sich gleich …“.
Endet der Lauf mit fehler oder ohne Zeile, erscheint ein Fehlertext.
Der Upload-Fortschritt (anFortschritt) wird mit null übergeben und nicht angezeigt.
Ergebnis: Badges gefunden / gefiltert / eingereiht / übersprungen. Danach werden Datei und Analyse zurückgesetzt.

Fußzeile: Der Import fetcht nichts. Das Abarbeiten startet erst über die Fetch-Steuerung.

Box B „Sternzeit-Tabelle wiederherstellen“
FileDropzone für den NDJSON-Export, je Zeile {sternzeit, objectType}.
Knopf „wiederherstellen“: adminImportSternzeitTabelle(datei, null, anZeile) → POST /sternzeittabelleimport, Multipart mit datei.
Gleiche NDJSON-Mechanik wie oben, Laufanzeige gefunden / gefiltert / „angelegt“ (= eingereiht).
Ergebnis: gefunden, gefiltert („Zeilen ohne brauchbare Sternzeit“), angelegt, übersprungen („Bestand gewinnt“).
Die Queue bleibt leer.
9. Tab „Export“ (ExportTab.jsx), zwei Contentboxen
Einmalig adminGetStats() nur für die Zahl „Im Stamm: N Sternzeiten“. Fehler werden ignoriert.
Box „Sternzeiten exportieren (Queue-Futter)“: Der Knopf „herunterladen“ ruft window.location.assign(apiUrl('/api/rest/abasadmin/export/sternzeiten')). Die Datei enthält eine Sternzeit pro Zeile.
Box „Sternzeit-Tabelle exportieren (Inventar)“: Der Knopf ruft window.location.assign(apiUrl('/api/rest/abasadmin/export/sternzeittabelle')). Ergebnis ist NDJSON im Format {"sternzeit":"…","objectType":"obj:data:19:0"}.
Mechanik: nativer Browser-Download, kein Blob und kein apiCall. Die Antwort ist ein Attachment, der Browser streamt direkt auf die Platte. Die Seite bleibt stehen, es gibt keine Toasts, und der API-Monitor sieht diese Downloads nicht.
10. Tab „Resync“ (ResyncTab.jsx)
Beim Mount adminGetObjectTypes() → POST /objecttypes ohne Body. Die Antwort ist [{objectType, anzahl}], wobei objectType null für noch nie gefetchte Sätze steht.
Auswahlfeld:
„alle Objekttypen (Summe)“
je Typ "{Klartext} — {objectType} ({anzahl})"
Typlose Einträge sind gesperrt und laufen nur über „alle“ mit.
Die Klartexte kommen aus der fest im Code stehenden Tabelle TYP_LABELS:
obj:data:2:1 Artikel
obj:data:19:0 Kontakte
obj:data:20:0 Artikel-Referenzen
obj:data:21:0 Lieferanten-Artikel
alle anderen: „unbekannt“
Knopf „einreihen (N)“: adminResyncSternzeiten(gewaehlt || null) → POST /resync mit {objectType}, wobei null alles bedeutet.
Das ist ein synchroner JSON-Aufruf, kein Stream.
Der Knopf ist gesperrt, wenn die Anzahl 0 ist.
Ergebnis: Badges „X neu eingereiht“, „Y betrachtet“ (gefunden) und, falls > 0, „Z übersprungen“. Danach wird die Typliste neu geladen.
Es wird nur eingereiht. Ob gefetcht wird, entscheidet die Fetch-Steuerung.
11. Tab „Mapper“ (MapperTab.jsx)
Laden:

Beim Mount adminGetMapper() → GET /mapper. Die Liste enthält {name, bezeichnung, extraktTyp, zeilen, objectType, herkunftText, herkunft:{art, eingaben:[{feldName, woher}]}}. Der erste Eintrag wird vorausgewählt.
Bei jedem Wechsel des Namens: adminGetMapperVorschrift(name) → GET /mapper/{name}, und die Infosystem-Eingaben werden geleert.
Box „Extrakt erzeugen“:

Auswahl im Kopf: "{bezeichnung} → {extraktTyp} ({zeilen})".
„Herkunft der Antwort“ zeigt herkunftText in Monospace.
Weg 1, Sternzeit live (Feld plus Knopf „holenUndMappen“):
adminViewResponse(sternzeit) → POST /viewresponse mit {sternzeit}.
Das JSON wird ins Textfeld geschrieben.
Automatische Mapper-Wahl nach content.data.meta.objectType. Bevorzugt wird ein Name, der auf Kern endet.
Dann adminErzeugeExtrakt({mapper, json}) → POST /extrakte.
Weg 2, Infosystem (nur wenn herkunft.art === 'INFOSYSTEM'):
Je Eingabe ein Feld. Felder ohne woher sind gesperrt.
Knopf „infosystemAbfragenUndMappen“ ruft adminErzeugeExtrakt({mapper: name, eingaben}).
Steht antwortJson in der Antwort, wird es ins Textfeld übernommen.
Weg 3, Rohtext:
Ein Textfeld (12 Zeilen) für die eingefügte ABAS-Antwort.
Knopf „durchDasMappingSchicken“: JSON.parse, Mapper-Wahl wie oben, dann adminErzeugeExtrakt({mapper, json}).
Knopf „leeren“.
Die Body-Variante {mapper, sternzeit} aus der Hook-Doku nutzt das Frontend nicht.
Ergebnis, drei Boxen:

„Extrakt — {extraktTyp}“:
Badges bezeichnung, „N Anfragen an ABAS“ und „N Fehler“ (rot/grün).
<pre> mit dem Extrakt-JSON.
„Geholt:“ mit anfragen als Liste mit ·, darunter die Fehlerzeilen.
„Befunde je Zeile“:
Getrennt in „Nicht in der Antwort“ (!feldDa) und „Gelesen“.
Spalten: Quelle | Stelle in der Antwort (adresse) | → Feld im Extrakt (solrFeld + umrechnung) | gelesen (rohWert) | geschrieben (wert, „unverändert“ oder uebersprungen) | Fundort (fundorte).
Werte werden auf 90 Zeichen gekürzt.
„Nicht gelesen“: Badges je Feld, im Kopf „N von felderInAntwort Feldern“.
Box „Vorschrift“ (immer sichtbar):

Kopf: bezeichnung → extraktTyp, Badges je quellen und die Zeilenzahl.
Herkunft.
Tabelle: Quelle (eingerückt um ebene × 1,5 rem) | Stelle | → Feld im Extrakt (plus → subMapper) | Besonderheit (► umrechnung, mehrfach falls nicht ERSTER, bedingung).
Zeilen mit art SUBFETCH oder INFOSYSTEM sind fett.
12. useAbasAdmin.js, Funktion für Funktion
Es gibt bewusst keinen Cache. Alle Pfade beginnen mit /api/rest/abasadmin. Zahlen in URL-Parametern laufen über longParam, das den Regex ^-?\d{1,19}$ prüft und sonst eine Exception wirft. sternzeit als URL-Parameter wird nur per encodeURIComponent übergeben.

Funktion	Methode und Pfad	Body / Parameter	Antwort, Bemerkung
adminGetStats()	GET /stats	–	Kopfzahlen: sternzeitenGesamt, ohneObjectType, queueGesamt, queueInArbeit, queueMitFehlern, fetchenPausiert
adminSearchSternzeiten(suchtext, objectType)	POST /sternzeitsearch	{suchtext, objectType}	{treffer, gesucht, abgeschnitten}. Ohne Text die letzten 1000, mit Text Suche per Primärschlüssel (volle Klammerform). Sonderwert OHNE_OBJECT_TYPE
adminGetTypKatalog()	GET /typkatalog	–	[{objectType, datenbank, bezeichnung, hatHandler}], liegt im Backend im Speicher
adminGetMapper()	GET /mapper	–	Mapper-Liste (siehe Mapper-Tab)
adminGetMapperVorschrift(name)	GET /mapper/{encodeURIComponent(name)}	–	Vorschrift (Ist-Stand)
adminErzeugeExtrakt(body)	POST /extrakte	{mapper, json} oder {mapper, sternzeit} oder {mapper, eingaben}	Schreibt nichts, Subfetches gehen aber live an ABAS
adminEnqueueSternzeit(sternzeit)	POST /sternzeiten	{sternzeit}	200 = eingereiht/umgestellt, 208 = stand schon so drin
adminViewResponse(sternzeit)	POST /viewresponse	{sternzeit}	Rohe ABAS-Antwort, live geholt. Die Übersicht nutzt stattdessen die GET-Variante per window.open
adminDeleteSternzeit(sternzeit)	DELETE /sternzeiten	{sternzeit} im Body	Reiht zum Auflösen ein (Kern-Docs und Stammsatz), kein hartes Löschen
adminGetLog(sternzeit, limit)	GET /log?limit=…[&sternzeit=…]	–	Empfangsprotokoll abas_log: {id, sternzeit, method, empfangenAt}
adminGetDateieingaenge(seite, groesse, sternzeit)	GET /multipart?seite=…&groesse=…[&sternzeit=…]	–	{zeilen, gesamt}, Seite 0-basiert, Größe 1..500
adminGetAufloesungen(sternzeit, limit)	GET /aufloesungen?limit=…[&sternzeit=…]	–	abas_aufloese_log: {sternzeit, kern, docId, handler, quelle, zeitpunkt}
adminRaeumeLogAuf(stichdatum)	DELETE /log	{stichdatum} oder {}	{geloescht, geloeschtAufloesungen}, räumt beide Protokolle
adminGetQueue(limit)	GET /queue?limit=…	–	FIFO-Liste: sternzeit, method, queuedAt, claimedAt, attempts, letzterFehler
adminGetQueueFehler(limit)	GET /queuefehler?limit=…	–	Nur attempts > 0, hartnäckigste zuerst (teure Abfrage)
adminDeleteQueueEintrag(sternzeit)	POST /queuedelete	{sternzeit}	Nur der Queue-Eintrag, der Stammsatz bleibt
adminRetryQueueEintrag(sternzeit)	POST /queueretry	{sternzeit}	Setzt die Fehlversuche zurück
adminImportSternzeiten(datei, datenbanken, anFortschritt, anZeile)	POST /sternzeitimport (apiUpload, XHR)	FormData datei + optional datenbanken="a,b"	NDJSON-Strom, löst mit der letzten Zeile auf
adminImportSternzeitTabelle(datei, anFortschritt, anZeile)	POST /sternzeittabelleimport (apiUpload)	FormData datei	NDJSON-Strom. eingereiht = neu angelegt, uebersprungen = stand schon da
adminGetObjectTypes()	POST /objecttypes	–	[{objectType, anzahl}]. Ein Lesezugriff, aber als POST
adminResyncSternzeiten(objectType)	POST /resync	{objectType} (null = alle)	{gefunden, gefiltert, eingereiht, uebersprungen}, arbeitet in 10.000er-Stapeln
adminGetFetchControl()	GET /fetchcontrol	–	{pausiert, parallel, queueGesamt, queueInArbeit, verarbeitetGesamt, fehlerGesamt}
adminSetFetchen(fetchen)	POST /fetchcontrol	{fetchen}	Neuer Stand
adminSetParallel(parallel)	POST /parallel	{parallel}	Das Backend klemmt auf 1..100 und liefert den gültigen Stand
Zwei Export-Endpunkte stehen nicht im Hook und werden direkt per apiUrl aufgerufen: GET /export/sternzeiten und GET /export/sternzeittabelle. Dasselbe gilt für GET /viewresponse?sternzeit= per window.open.

13. AbasKnopf.jsx
Ein Anker href="abasurl:{sternzeit}", ein eigenes URL-Schema ohne //, damit Chrome keinen Schrägstrich anhängt. Auf dem Arbeitsplatz muss der passende Registry-Eintrag liegen.
Ohne sternzeit rendert er null.
Bildmarke /abas-zeichen.svg. Im Ruhezustand steht die Beschriftung beschriftung('oeffnen', sprache); die Sprache kommt aus dem users_setting LANGUAGE (Standard de). Beim Hovern wird über .aufklapp-text auf „Sternzeit: …“ getauscht.
Das Hoverlay zeigt den hinweis oder „Sternzeit: …“.
Prop schmal: nur die Bildmarke, mit einem aria-label.
Props: sternzeit, hinweis, schmal, className.
Verwendet in:
ArtikelmappeSeite.jsx (Übersicht-Tab, Hinweis „Artikelnummer: …“)
KundenmappeSeite.jsx (Firma und Personen, schmal)
BezugsnummerSeite.jsx
kontakte\KontakteTreffer.jsx
todojob\JobInhalt.jsx (schmal)
projectmanager\InhaltPopup.jsx
projectmanager\ProjectSeite.jsx
14. Suche nach „abas-user“, „fetchrequest“, „bezugsnummern“
POST /api/rest/abas-user/fetchrequest/{sternzeit} (Resync aus der Artikelmappe)
Hook: C:\Users\Bindemaja\Documents\dev\hobby\home-plus-extern\intern-instance\frontend\src\hooks\rest\useAbasUser.js, Funktion userTriggerFetchRequest(sternzeit). POST ohne Body, die Sternzeit steht per encodeURIComponent im Pfad.
Laut Kommentar: 200 = neu eingereiht, 208 = stand schon in der Queue, jeweils mit {sternzeit, angekommen, meldung}. 400 = Sternzeit unbrauchbar.
Zugang ist die Rolle ABAS_READ per Pfadregel.
Einziger Aufrufer: C:\Users\Bindemaja\Documents\dev\hobby\home-plus-extern\intern-instance\frontend\src\pages\artikel\ArtikelmappeSeite.jsx
Recht: darfResync = rollen.includes('ABAS_READ') || rollen.includes('ADMIN') (Zeile 101/192).
Knopf in UebersichtInhalt, Zeile ~1884: ein kleiner icon-button mit ArrowClockwise hinter der Sync-Zeile („Sternzeit … · zuletzt synchronisiert … · in ABAS geändert …“). Hoverlay „Artikel neu aus ABAS holen (Resync)“. Er erscheint nur, wenn darfResync && artikel.sternzeit.
resyncArtikel() (Zeile 359):
Erfolg: Toast "{antwort.meldung ?? 'Eingereiht.'} Der Artikel aktualisiert sich in wenigen Sekunden." (success).
Fehler: Toast "Resync nicht eingereiht: {fehlerText}" (error).
Die Mappe wird bewusst nicht neu geladen, weil der Kern erst nach dem Worker-Fetch frisch ist.
POST /api/rest/abas-user/bezugsnummern
Hook: C:\Users\Bindemaja\Documents\dev\hobby\home-plus-extern\intern-instance\frontend\src\hooks\rest\useBezugsnummer.js, Funktion userLookupBezugsnummer(bezugsNummer). Body {bezugsNummer}. Antwort {bezugsNummer, gefunden, sternzeit, objectType, bezeichnung, meldung}.
Aufrufer 1: pages\bezugsnummer\BezugsnummerSeite.jsx
Route /bezugsnummer (nur RequireLogin). Die Kachel BEZUGSNUMMER steht auf nurEingeloggt.
Formular mit Feld „Beleg- oder Bezugsnummer“ und Knopf „nachschlagen“. Während der Abfrage steht „Frage ABAS …“.
gefunden=false zeigt nur meldung, gedimmt.
Bei einem Treffer erscheint eine Eigenschaften-Tabelle (Bezugsnummer, Objekttyp = bezeichnung, objectType, Sternzeit) mit AbasKnopf und dem Hinweis „Bezugsnummer: …“.
Fehlt objectType, erscheint zusätzlich meldung als Warnung.
Aufrufer 2: pages\archivierung\useHookScan.js (Hook-Scan beim Archivieren von Mails)
Nummern kommen aus Betreff und uniqueBody (hookScan.js: mindestens 4 Ziffern als eigenes Wort; durch Schrägstrich verbundene Gruppen nur, wenn kein Teil mit 0 beginnt und jeder Teil mindestens 4 Ziffern hat).
Abgefragt wird in Wellen über inWellen mit höchstens 3 parallel (ABAS_PARALLEL = 3).
Ergebnis ist die Map Nummer → {laeuft} | Antwort | {fehler}.
Angezeigt in ArchivierungAufbereitenSeite.jsx:
NummerVorschlag: bezeichnung und „objectType · Sternzeit“, dazu HookFeldAuswahl, um die Nummer in ein Hook-Feld zu übernehmen. Beim Überfahren mit der Maus wird die Nummer im Mailtext markiert.
Zähler „x/y bei ABAS gefragt“ und „ABAS kennt keine der N Nummern.“
Angezeigt in ArchivVorschau.jsx: Abschnitt „Erkannte Nummern in ABAS“ (NummerStand), nur mit gefundenen, laufenden oder fehlerhaften Nummern.
In kachelKatalog.js gibt es nur eine Kommentar-Erwähnung.
Auffälligkeiten
Fetch-Steuerung: Der Fußtext sagt „Nach einem Backend-Neustart startet das Fetchen pausiert“, die Doku im selben File sagt aber „startet seit 2026-09-09 AKTIV (start-pausiert = false)“.
useAbasAdmin.js:
Zwei verwaiste JSDoc-Blöcke („MAPPER-DOKU…“, „IST-STAND DES MAPPINGS…“) stehen ohne eigene Funktion da.
Der Kommentar zu adminGetTypKatalog nennt adminZaehleObjectTypes, die Funktion heißt adminGetObjectTypes.
Die Resync-Doku erwähnt noch „Stillgelegte (ignore)“, obwohl ignore zurückgebaut wurde.
Übersicht: Der Kopfkommentar sagt „Stammsatz hart loeschen“. Tatsächlich wird nur zum Auflösen eingereiht (DELETE /sternzeiten).
Resync-Tab: nutzt eine fest im Code stehende TYP_LABELS-Liste. Genau so eine Liste wurde im Import-Tab zugunsten des Typkatalogs ausgebaut.
Polling: Queue (5 s) und Fetch-Steuerung (10 s) nutzen setInterval ohne Schutz gegen sich überlappende Requests.
Dateieingänge: Beim Blättern gilt der aktuell eingetippte Suchtext, auch wenn er nicht abgeschickt wurde.
Durchsatz-Fenster: real etwa 50 s (6 Punkte bei 10 s Takt), nicht wie im Kommentar eine Minute.
3:48 AM
Die Frontend-Zusammenfassung ist da (10 Tabs, alle Endpunkte, Polling, NDJSON-Mechanik). Der Agent für den Mapper-Unterbau läuft noch, danach schreibe ich den Gesamtüberblick und den Plan.



3:51 AM
Alle angefragten Dateien sind gelesen. Damit sich die Fragen 1 und 5 (Solr-Kerne, Registry) beantworten lassen, habe ich zusätzlich nur gelesen: AbasHandler, SolrArtikelService, SolrKontakteService, ArtikelReferenzobjektSyncZiel, SolrConfig sowie Auszüge aus AbasQueueWorkerPool, ArtikelmappenService und DmsFelder. Geändert wurde nichts.

Pfadpräfix im Folgenden: C:\Users\Bindemaja\Documents\dev\hobby\home-plus-extern\intern-instance\backend\src\main\java\de\pahling\home\

Teil A – Die Zusammenhänge
1. Von der ABAS-Antwort (Sternzeit) zum Solr-Dokument

ABAS-Push (AbasErpController POST /api/rest/abas/sternzeit/{sternzeit})
  -> Queue (abas_sternzeit_queue) -> AbasQueueWorkerPool.verarbeitePost(sternzeit)
       1. abasRestService.systemFetchAbasObjektById(sternzeit)   GET obj/data/byId/{sternzeit}?headFields=*
       2. abasResponseParser.extractObjectType(antwort)           z.B. "obj:data:2:1"
       3. Stammsatz AbasSternzeit fortschreiben (objectType, lastFetchFromAbas, fetchCounter)
       4. if abasHandler.hatSyncZiel(objectType):
            AbasHandler.verarbeiteFuerSync(objectType, sternzeit, antwort)
              ziel = syncZielJeTyp.get(objectType)
              ziel.schreibe(sternzeit, ziel.kernMapper().bildeExtrakt(antwort))
            Stammsatz.syncDatum = now
Den Schritt Controller → Queue (AbasInboundService/AbasQueueService) habe ich nicht im Detail gelesen. Der Weg ab dem Worker ist im Code nachgesehen.

Was in kernMapper().bildeExtrakt(antwort) passiert (AbasMapperBasis):

pruefeUndLiesData: parser.navigateToData(antwort) liefert content.data. Fehlt es, wird eine AbasFetchException geworfen. Danach muss parser.extractObjectType(antwort) genau dem objectType() des Mappers entsprechen, sonst ebenfalls AbasFetchException.
AbasZuordnungAusfuehrer.fuehreAus(zuordnungen(), quelle(), data, mitBefunden):
AbasAntwort.von(data, parser) bereitet die Antwort einmal auf: Kopf-Map (Feldname → Knoten), eine Map je Tabellenzeile und meta als Text-Map.
Dann wird jede AbasZuordnung der Reihe nach ausgeführt. Zeilen mit fremder quelle werden übersprungen.
FELD: AbasAdresse.lies(antwort, parser) liefert einen Treffer. Ist er leer, wird bei einer Kaskade die nächste Adresse probiert. Danach werden die Bedingungen geprüft (übersprungen, nurWennTyp, nurWennGesetzt, nichtUeberschreiben, leer). Zum Schluss rechnet AbasUmrechnung.wandle den Wert um, und nach der Mehrfach-Regel landet er in felder[solrFeld].
SUBFETCH / INFOSYSTEM: holt live bei ABAS, lässt den Sub-Mapper laufen und hängt dessen Extrakt-Record unter felder[solrFeld] ein.
SONDERWEG: wird nur gemeldet (Übergangsform; wird derzeit von keinem Mapper genutzt).
Ergebnis ist ein Lauf(felder, befunde, fehler, anfragen).
AbasExtraktBauer.baue(extraktTyp, lauf.felder()) baut per Reflection den kanonischen Konstruktor des Records. Dabei gilt: Liste → List<String>, fehlende Liste → leer, fehlender Skalar → null, Sub-Records bleiben unverändert.
Daraus entsteht AbasExtrakt<T>(daten, befunde, fehler, anfragen).
Danach schreibt das AbasSyncZiel.schreibe(sternzeit, extrakt):

objectType	Sync-Ziel (Klasse)	Ziel	Vorgehen
obj:data:2:1	artikel\service\SolrArtikelService	Solr-Kern artikel (Bean artikelSolrClient, Property solr.core-artikel, Default artikel)	AbasExtraktFelder.flach(daten) → SolrInputDocument. Zusätzlich: istProduktionsartikel (Default false), artikelBildBase64Png (Vorschaubild aus dem DMS), syncDatum, syncStatus (OK/WARN), syncFehler. Dann artikelSolrClient.add. Schlüssel ist artikelNummer.
obj:data:19:0	kontakte\service\SolrKontakteService	Solr-Kern kontakte (kontakteSolrClient, solr.core-kontakte)	Flach machen, dann id = sternzeit und syncDatum setzen, dann add. Ist type null, wird eine AbasFetchException geworfen.
obj:data:20:0	artikel\service\ArtikelReferenzobjektSyncZiel	kein Kern	Liest artikelAbasId (yartikel) und reiht diese Artikel-Sternzeit per AbasQueueService.enqueueSternzeit(..., METHOD_POST, QUELLE_SYSTEM) neu ein. Der Artikel wird dann komplett gefetcht, der 20er als Subfetch.
obj:data:3:30	abas\chance\service\ChanceAbasService	Postgres-Tabelle chance_abas	Upsert über die Sternzeit, Rückfall über die chanceNummer.
Weitere Solr-Kerne aus SolrConfig: dms und nachrichten. Die ABAS-Mapper schreiben dort nicht hin. Nur der Datei-Zuschub (AbasFileInboundService) legt über FileDataService.systemUploadFileData ein DMS-Dokument an. Dass das im dms-Kern landet, schließe ich aus DmsFelder, die Upload-Kette selbst habe ich nicht gelesen.

Löschen: AbasQueueWorkerPool.verarbeiteDelete → AbasTypKatalog.datenbankAusSternzeit → objectTypesMitHandlerFuerDatenbank → je Typ AbasHandler.loeseAufFuerSync → ziel.loesche(sternzeit) → AufloeseErgebnis(kern, geloeschteDocIds) wird in abas_aufloese_log protokolliert.

2. Wofür AbasHerkunft, AbasInfosystem, AbasUmrechnung, AbasAdresse und AbasExtraktFelder da sind
AbasHerkunft: beschreibt für einen ganzen Mapper, woher seine Hauptantwort kommt.

OBJEKT: GET obj/data/byId/{sternzeit}
INFOSYSTEM: POST infosys/data/{gruppe}/{name} mit Eingaben; der objectType lautet dann infosys:data:{gruppe}:{name}
Die Klasse liefert objectType() für die Typprüfung und den Text für den Mapper-Tab. Außerdem nutzt AbasMappingAnsicht.adminErzeugeExtraktAusInfosystem die Eingabeliste.

AbasInfosystem: die Eingabeseite eines Infosystems auf Ebene einer Zuordnungszeile (Gruppe, Name, Eingaben). herkunft ist der Name eines Extrakt-Feldes, dessen Wert gesendet wird; null heißt „leer senden“ (z.B. bstart als Startknopf). Der Ausführer baut daraus den Request. Zu AbasHerkunft ist das eine bewusst parallele Beschreibung: Die Herkunft beschreibt den Sub-Mapper, AbasInfosystem den Aufruf durch den Eltern-Mapper.

AbasUmrechnung: Enum für die Wertwandlung nach dem Lesen. Werte: KEINE, ABAS_DATUM (ISO mit Offset → Date), DEUTSCHE_ZAHL (→ Double), GANZZAHL (→ Integer), JA_NEIN (→ Boolean), KONTAKT_TYP (Adresse→ADRESSE, Kontakt→PERSON, sonst null), STERNZEIT (nur (n,n,n), sonst null), IST_INHOUSE_PRODUCTION ("(InhouseProduction)" → true). Jede Umrechnung schluckt ihre Exception und liefert null.

AbasAdresse: die ausführbare und zugleich anzeigbare Stelle eines Wertes in der Antwort.

Bereiche: KOPF, TABELLE (mit Zeilenfilter filterFeld=filterWert), META
Teil: VALUE (oft die Sternzeit/Referenz) oder TEXT (Klartext)
Varianten: kopfNurWenn (Kopffeld mit Wächterfeld) und zeileMitNamensauswahl (mehrere Feldnamen, der erste vorhandene je Zeile gewinnt)
toString() erzeugt die Doku-Form, z.B. content.data.table[kontaktart=(Kunde)].fields[name=vorgabereferenz].value
AbasExtraktFelder: macht ein Extrakt-Record flach zu Map<Komponentenname, Wert>. Sub-Records werden rekursiv aufgelöst, null und leere Listen fallen weg, bei Namensgleichheit gewinnt der erste. Das ist genau die Form eines Solr-Dokuments. Nutzer sind SolrArtikelService, SolrKontakteService und ArtikelmappenService.userGetFertigung.

3. Wie ein konkreter Mapper deklariert ist (ArtikelKernMapper + ArtikelKern)
Muster jedes Mappers:

@Service-Klasse extends AbasMapperBasis<Record>
Konstruktor super(ausfuehrer, parser, Record.class); Sub-Mapper werden als Konstruktor-Parameter injiziert
static final List<AbasZuordnung> als Vorschrift
herkunft(), bezeichnung(), quelle(), zuordnungen()
Das Record in abas\mapper\extrakt\ ist das Schema. Beim Boot prüft AbasHandler.pruefeRecordPassung:

Jede Komponente hat genau eine Zeile.
ERSTER gehört zu einem Skalar, alle anderen Mehrfach-Werte zu List.
Eine Subfetch-Zeile muss den Typ des Sub-Extrakts treffen.
ArtikelKernMapper:

OBJECT_TYPE = "obj:data:2:1"
Quelle Q = "2er (Artikel)"
Herkunft OBJEKT
Bezeichnung „Artikel-Kern“
91 Zeilen = 91 Komponenten in ArtikelKern
Zur Spalte „DMS-Hook?“: Im Mapper-Code gibt es keinen Hook-Begriff. Ich habe den Zielfeldnamen gegen DmsFelder.HOOK_FELDER abgeglichen (die DMS-Whitelist: artikelNummer, masterArtikelNummer, kundenArtikelNummer, lieferantenArtikelNummer, ean, kundenNummer, lieferantenNummer, chanceNummer, sternzeit, …). Die Leerzellen in den Spalten Umrechnung und Hook bedeuten: keine Umrechnung bzw. kein Hook.

Kopf (content.data.head.fields[name=…], Mehrfach ERSTER):

#	ABAS-Feld	Teil	Zielfeld	Typ	Umrechnung / Bedingung	DMS-Hook?
1	nummer	VALUE	artikelNummer	String	(Solr-Schlüssel)	ja
2	meta.id	–	sternzeit	String	content.data.meta.id	ja
3	such	VALUE	suchbegriff	String		
4	name	VALUE	artikelName	String		
5	vbez	VALUE	verkaufsbezeichnung	String		
6	vkbez	VALUE	verkaufsKurzbezeichnung	String		
7	zeichn	VALUE	zeichnungsnummer	String		
8	index	VALUE	artikelIndex	String		
9	ean	VALUE	ean	String		ja
10	gtin	VALUE	gtin	String		
11	ftext	VALUE	freitext	String		
12	bem	VALUE	bemerkung	String		
13	beschr	VALUE	beschreibung	String		
14	ve	TEXT	mengenEinheitVe	String		
15	ge	TEXT	mengenEinheitGe	String		
16	le	TEXT	mengenEinheitLager	String		
17	vpe	TEXT	verpackungseinheit	String		
18	epe	TEXT	einkaufseinheit	String		
19	lme	TEXT	laengeneinheit	String		
20	bme	TEXT	breiteneinheit	String		
21	vpr	TEXT	verkaufspreis	String		
22	ekpr	TEXT	einkaufspreis	String		
23	lpr	TEXT	listenpreis	String		
24	mpr	TEXT	mittelpreis	String		
25	planpr1	TEXT	planpreis	String		
26	vwaehr	TEXT	waehrung	String		
27	kbprwaeh	TEXT	einkaufspreisWaehrung	String		
28	bestand	TEXT	lagerbestand	String		
29	lgbestand	TEXT	lagerBestandVerfuegbar	String		
30	zlgbestand	TEXT	reservierterBestand	String		
31	zupname	TEXT	lagerplatz	String		
32	abpname	TEXT	abgangsLagerplatz	String		
33	lggew	TEXT	lagergewicht	String		
34	gewicht	TEXT	gewicht	String		
35	brgewicht	TEXT	bruttogewicht	String		
36	ntgewicht	TEXT	nettogewicht	String		
37	mate	TEXT	gewichtseinheit	String		
38	vol	TEXT	volumen	String		
39	sachname	TEXT	sachmerkmal	String		
40	wgruppename	TEXT	warengruppe	String		
41	zmart	TEXT	artikelart	String		
42	bsart	TEXT	beschaffungsart	String		
43	bsart	VALUE	istProduktionsartikel	Boolean	IST_INHOUSE_PRODUCTION	
44	dispoa	TEXT	dispositionsart	String		
45	ahnumname	TEXT	zolltarifnummer	String		
46	wstoffname	TEXT	werkstoff	String		
47	drbelegart	TEXT	belegart	String		
48	gefahrgut	TEXT	gefahrgut	String		
49	skfaehig	TEXT	skontofaehig	String		
50	serpflicht	TEXT	seriennummernPflicht	String		
51	intrarel	TEXT	intrastatRelevant	String		
52	nwpflicht	TEXT	nachweisPflicht	String		
53	yartnrzusatz	VALUE	yArtikelnummerZusatz	String		
54	Kaskade: ymasterartikel TEXT → ymasterartikel VALUE → nummer VALUE nur wenn ymasterslave=Master		yMasterArtikel	String	3 Adressen	indirekt (Alias masterArtikelNummer → yMasterArtikel in ArtikelSolrSearchService.FELD_ALIAS)
55	ykundenbeistellung	VALUE	yKundenbeistellung	String		
56	ykategorie	VALUE	yKategorie	String		
57	yzeichndatum	VALUE	yZeichnungsdatum	String		
58	ydispoa	VALUE	yDispositionsart	String		
59	ydispoaMge	VALUE	yDispositionsmenge	String		
60	yqmappqmpruefv	VALUE	yQmPruefverfahren	String		
61	yqmappqmartex	VALUE	yQmArtikelExtern	String		
62	yvendor	VALUE	herstellerAbasId	String	STERNZEIT	
63	yvendor	TEXT	herstellerNummer	String	nurWennGesetzt(herstellerAbasId)	
64	yvendorbstnr	VALUE	herstellerBestellNummer	String	nurWennGesetzt(herstellerAbasId)	
65	yreferenz	VALUE	referenzObjektAbasId	String	STERNZEIT	
66	erfasserzeichen	VALUE	erfasser	String		
67	zeich	VALUE	bearbeiter	String		
68	erfass	VALUE	abasErstelltAm	Date	ABAS_DATUM	
69	stand	VALUE	abasGeaendertAm	Date	ABAS_DATUM	
70	dokumentbaurl	VALUE	dmsDokumentUrl	String		
71	aktebaurl	VALUE	dashboardUrl	String		
72	bireporturl	VALUE	biReportUrl	String		
Tabelle (content.data.table[elart=…], alle mit ALLE_MIT_LUECKEN, Typ List<String>, kein Hook):

#	Filter	ABAS-Feld	Teil	Zielfeld
73	elart=(Product)	zn	TEXT	stuecklistePosition
74	(Product)	elem	TEXT	stueckliste
75	(Product)	elem	VALUE	stuecklisteAbasId
76	(Product)	zid	VALUE	stuecklisteZid
77	(Product)	elname	TEXT	stuecklisteName
78	(Product)	menge	TEXT	stuecklisteMenge
79	(Product)	elle	TEXT	stuecklisteEinheit
80	(Product)	telementicon	VALUE	stuecklisteTyp
81	elart=(Operation)	zn	TEXT	arbeitsplanPosition
82	(Operation)	elem	TEXT	arbeitsplanNummer
83	(Operation)	elem	VALUE	arbeitsplanAbasId
84	(Operation)	elname	TEXT	arbeitsplanName
85	(Operation)	mgrname	TEXT	arbeitsplanArbeitsplatz
86	(Operation)	zesek	TEXT	arbeitsplanZeitSek
87	(Operation)	zrsek	TEXT	arbeitsplanRuestzeitSek
Subfetch- und Infosystem-Zeilen (keine bricht das Mapping ab; ein Fehler landet in fehler und damit im Solr-Dokument als syncStatus=WARN):

#	Art	Quelle der Anfrage	Zielfeld (Typ)	Sub-Mapper	Flach im Solr-Dokument (Hook?)
88	SUBFETCH	Kopf yreferenz VALUE	referenzobjekt (Referenzobjekt)	ReferenzobjektMapper (20:0)	8 related…-Listen (nein)
89	SUBFETCH	Kopf ylieferantartikel VALUE	lieferantenArtikel (LieferantenArtikel)	LieferantenArtikelMapper (21:0)	lieferantenNummer (ja), lieferantenArtikelNummer (ja), lieferantenArtikelName (übersprungen)
90	SUBFETCH (Kette)	extrakt.herstellerAbasId	hersteller (Hersteller)	HerstellerMapper (1:1)	herstellerName (nein)
91	INFOSYSTEM	POST infosys/data/st/CUSTOMERPRODUCT mit kartikel = extrakt.artikelNummer, bstart = leer	kundenArtikelnummern (KundenArtikelnummern)	KundenArtikelnummernMapper	kundenNummer (ja), kundenArtikelNummer (ja), kundenArtikelName (übersprungen)
KontakteKernMapper (kurz):

OBJECT_TYPE = "obj:data:19:0", TYPE_ADRESSE = "ADRESSE", TYPE_PERSON = "PERSON", Quelle "19er"
40 Zeilen für 38 Komponenten: kundenNummer und lieferantenNummer haben je eine Zeile für ADRESSE und eine für PERSON.
Kopf:

meta.id → id
kontaktorig TEXT → type (KONTAKT_TYP; muss als Erstes kommen, weil nurFuerTyp darauf prüft)
kontaktorig TEXT → kontaktorig, nummer → kontaktNummer, such → suchbegriff, name, ans → anschriftName, str → strasse, plz, nort → ort
staat TEXT → land, region TEXT, tele → telefon, mtele → mobiltelefon, fax, email, urlt → website, kontakt → kontaktperson, anrede TEXT, funktion, bem → bemerkung, ftext → freitext1, ftext2 → freitext2
stand → abasGeaendertAm (Date, ABAS_DATUM), zeich → abasBearbeiter, ustid (nur ADRESSE)
Tabelle (kontaktart=…):

ADRESSE:
(Kunde): vorgabereferenz → kundenNummer, kontaktreferenz → kundeAbasId (ERSTER)
(Lieferant): vorgabereferenz → lieferantenNummer, kontaktreferenz → lieferantAbasId (ERSTER)
(Kundenkontakt) → vorauswahlKundenNummern / vorauswahlKundenAbasIds (ALLE_ENTDOPPELT)
(Lieferantenkontakt) → vorauswahlLieferantenNummern / vorauswahlLieferantenAbasIds (ALLE_ENTDOPPELT)
PERSON:
(Kundenkontakt) → kundenNummer, kundenKontaktAbasId
(Lieferantenkontakt) → lieferantenNummer, lieferantenKontaktAbasId
Subfetch:

subfetchAusFeld(kundeAbasId → kundenKopie, KundenKopieMapper) und subfetchAusFeld(lieferantAbasId → lieferantenKopie, LieferantenKopieMapper)
Beide mit .wennSubfetchScheitertMappingAbbrechen(), also strikt.
Hook-Namen: kundenNummer, lieferantenNummer.
Alle übrigen Mapper:

Mapper	objectType	Herkunft	Extrakt	Ziel
ChanceMapper	obj:data:3:30	OBJEKT	Chance	Postgres chance_abas über ChanceAbasService
FertigungslisteMapper	infosys:data:fe:PRODLIST	INFOSYSTEM fe/PRODLIST (kart, bstart)	Fertigungsliste	kein Kern; live im Artikelmappen-Tab „Fertigung“ (ArtikelmappenService.userGetFertigung)
HerstellerMapper	obj:data:1:1	OBJEKT	Hersteller	Sub-Mapper von ArtikelKern
KundenArtikelnummernMapper	infosys:data:st:CUSTOMERPRODUCT	INFOSYSTEM st/CUSTOMERPRODUCT (kartikel, bstart)	KundenArtikelnummern	Sub-Mapper von ArtikelKern
KundenKopieMapper	obj:data:0:1	OBJEKT	KundenKopie	Sub-Mapper von KontakteKern
LieferantenArtikelMapper	obj:data:21:0	OBJEKT	LieferantenArtikel	Sub-Mapper von ArtikelKern
LieferantenKopieMapper	obj:data:1:1	OBJEKT	LieferantenKopie	Sub-Mapper von KontakteKern
ReferenzobjektArtikelMapper	obj:data:20:0	OBJEKT	ReferenzobjektArtikel	Kern-Mapper des ArtikelReferenzobjektSyncZiel (reiht den Artikel neu ein)
ReferenzobjektMapper	obj:data:20:0	OBJEKT	Referenzobjekt	Sub-Mapper von ArtikelKern
WertemengeMapper	obj:data:109:2	OBJEKT	Wertemenge	kein Ziel, nur Anzeige im Mapper-Tab
4. Subfetch und Infosystem
Subfetch ist eine Zuordnungszeile mit subMapper != null (Art SUBFETCH). Es gibt sie in zwei Formen:
subfetch(quelle, kopfFeld, zielFeld, subMapper): Die Sternzeit kommt aus dem Kopffeld (VALUE) der aktuellen Antwort.
subfetchAusFeld(quelle, extraktFeld, zielFeld, subMapper): Die Sternzeit kommt aus einem schon gefüllten Extrakt-Feld (Kette).
Infosystem ist eine Zeile mit infosystem != null (Art INFOSYSTEM). Sie wird mit AbasZuordnung.infosystem(quelle, gruppe, name, eingaben, zielFeld, subMapper) angelegt.
Auslösung: Beides passiert synchron in AbasZuordnungAusfuehrer.wendeSubfetch, sobald die Zeile in der Reihenfolge der Vorschrift drankommt. Es geht direkt über den AbasRestService, nie über die Queue.
Subfetch:
Ist die Sternzeit leer, gilt der Befund „leer“ bzw. „Feld nicht in der Antwort“.
Passt sie nicht auf das Muster \(\d+,\d+,\d+\), gilt „keine Sternzeit“.
Sonst systemFetchAbasObjektById (GET obj/data/byId).
Infosystem:
Je Eingabe wird der Wert aus lauf.felder()[herkunft] gelesen, bei einer Liste das erste Element. herkunft == null heißt leer senden.
Ist ein gefordertes Feld leer, wird die Zeile mit Befund übersprungen.
Sonst systemFetchAbasInfosystem(gruppe, name, eingaben, false).
Danach subMapper.bildeExtrakt[MitBefunden]; das prüft auch den objectType. Das Sub-Extrakt kommt unter solrFeld, Fehler, Anfragen und Befunde werden übernommen.
Bei einem Fehler: mit mappingAbbrechenWennSubfetchScheitert wird AbasFetchException("Mapping abgebrochen - …") geworfen, sonst wird der Fehler notiert und weitergemacht.
Mit uebersprungenBisAbasLiefert wird die Zeile gar nicht ausgeführt.
Boot-Regeln (AbasHandler.pruefeVorschriften):
Ein Sub-Mapper darf selbst keine Subfetches haben (Tiefe 1).
quellFeld und Infosystem-Eingaben dürfen nur Felder lesen, die eine frühere Zeile liefert.
Daneben können Infosysteme auch Haupt-Herkunft eines Mappers sein (Fertigungsliste, KundenArtikelnummern). Dann rufen AbasHandler.holeInfosystemUndBildeExtrakt oder der Mapper-Tab (adminErzeugeExtraktAusInfosystem) direkt auf.
Der Begriff „Subfetch“ steht auch für den Abruf der mitgeschickten Sternzeit in AbasFileInboundService. BezugsnummerService ruft das Infosystem ow1/BELEG2ID ohne Mapper auf.
5. Mapper-Registry (wie zu einem objectType ein Mapper gefunden wird)
Sammeln: Spring injiziert alle @Service-Beans vom Typ AbasMapper<?> als List, ebenso alle AbasSyncZiel<?>. Es gibt keine explizite Registrierung (Plug-in-Muster).
Sync-Weg (AbasHandler): Der Konstruktor baut Map<String, AbasSyncZiel<?>> syncZielJeTyp, Schlüssel ist ziel.objectType() (Default kernMapper().objectType()). Zwei Ziele für denselben Typ lösen beim Boot eine IllegalStateException aus.
hatSyncZiel(objectType) prüft, ob es ein Ziel gibt.
verarbeiteFuerSync sucht das Ziel und ruft ziel.kernMapper().bildeExtrakt und dann ziel.schreibe.
Damit gilt: je objectType genau ein Kern-Mapper, festgelegt über das Sync-Ziel.
Allgemein: AbasHandler.mapperFuer(objectType) filtert alle Mapper nach m.objectType().equals(objectType). Das kann mehrere liefern: obj:data:1:1 ergibt Hersteller und LieferantenKopie, obj:data:20:0 ergibt Referenzobjekt und ReferenzobjektArtikel.
Mapper-Tab (AbasMappingAnsicht.mapperFuerAntwort): Zuerst gilt der gewählte Mapper, wenn sein objectType passt. Sonst der Kern-Mapper des Sync-Ziels, sonst der erste Mapper mit diesem Typ, sonst 400. Der Name eines Mappers ist der Klassenname ohne „Mapper“ (ArtikelKernMapper → ArtikelKern).
Katalog (AbasTypKatalog): listet Typen mit Sync-Ziel (hatHandler=true) plus die feste Liste OHNE_HANDLER. Das dient der Anzeige und dem Routing beim DELETE (Datenbanknummer aus der Sternzeit → Typen mit Handler).
Teil B – Je Datei
Paket abas\mapper\
de.pahling.home.abas.mapper.AbasAdresse (record)
Zweck: Eine Wertstelle in der ABAS-Antwort, die man ausführen und anzeigen kann. Sie gilt für jeden Datensatz, deshalb kein Index und keine Zeilennummer.
Komponenten: bereich, filterFeld, filterWert, feldName, teil
Enums: Bereich {KOPF, TABELLE, META}, Teil {VALUE, TEXT}
Methoden:
static AbasAdresse meta(String feldName): Adresse auf content.data.meta.<feld>.
static AbasAdresse kopf(String feldName, Teil teil): Adresse auf ein Kopffeld.
static AbasAdresse kopfNurWenn(String feldName, Teil teil, String waechterFeld, String waechterWert): Kopffeld, das nur liefert, wenn ein anderes Kopffeld einen bestimmten Wert hat (für Kaskaden, z.B. Master).
static AbasAdresse zeile(String filterFeld, String filterWert, String feldName, Teil teil): Feld aus Tabellenzeilen, die dem Filter entsprechen.
static AbasAdresse zeileMitNamensauswahl(String filterFeld, String filterWert, List<String> feldNamen, Teil teil): mehrere Kandidaten-Feldnamen (intern mit | verbunden); je Zeile gewinnt der erste vorhandene.
List<String> feldNamen(): die Feldnamen der Adresse, aufgesplittet.
Treffer lies(AbasAntwort antwort, AbasResponseParser parser): wendet die Adresse auf die aufbereitete Antwort an.
String toString(): die Doku-Form, wie sie im Frontend steht.
Nested record Treffer(List<String> werte, boolean feldDa, List<Integer> fundorte): String ersterWert() liefert den ersten Wert oder null. feldDa=false heißt, dass ABAS das Feld nicht (mehr) liefert.
Logik:
Der Filter vergleicht ohne Klammern und ohne Rücksicht auf Groß/Klein.
filterWert == null heißt „Hauptsache vorhanden“.
Tabellen werden mit Lücken gelesen: eine leere Zelle ergibt "".
Aufrufer: AbasZuordnung (Fabriken), AbasZuordnungAusfuehrer, die Mapper.
de.pahling.home.abas.mapper.AbasAntwort (final class)
Zweck: Bereitet eine Antwort einmal je Lauf auf. Hintergrund war ein Performance-Problem: vorher wurde etwa die 60-fache Arbeit gemacht (733 Kopffelder × 68 Zuordnungen).
Methoden:
static AbasAntwort von(JsonNode data, AbasResponseParser parser): baut Kopf-Map, Zeilen-Maps und meta-Map aus content.data.
Map<String, JsonNode> kopf(): Kopffelder nach Name.
List<Map<String, JsonNode>> zeilen(): je Tabellenzeile eine Feld-Map.
Map<String, String> meta(): meta-Werte als Text.
int indexImKopf(JsonNode data, String feldName): Index eines Kopffeldes für die Fundort-Anzeige; wird lazy gebaut.
Aufrufer: AbasZuordnungAusfuehrer.fuehreAus.
de.pahling.home.abas.mapper.AbasExtrakt<T> (record)
Zweck: Ergebnis eines Mappers.
Komponenten: T daten (das Record), List<Befund> befunde (leer im Sync-Weg), List<String> fehler (gescheiterte Subfetches, die nicht abgebrochen haben), List<String> anfragen (was bei ABAS geholt wurde).
Nutzer: alle AbasSyncZiel.schreibe, AbasMappingAnsicht, ArtikelmappenService.
de.pahling.home.abas.mapper.AbasExtraktBauer (final class, Utility)
Zweck: baut aus der Feld-Map das Extrakt-Record per Reflection.
Methode: static <T> T baue(Class<T> typ, Map<String,Object> felder): wählt den Konstruktor nach den Record-Komponenten und wandelt die Werte:
List → List<String> (null wird zu "")
Skalar aus einer Liste → erstes Element
String, Boolean, Double, Integer werden konvertiert
Ein Date ohne vorherige Umrechnung führt zu einer IllegalStateException („Umrechnung ABAS_DATUM fehlt“).
Aufrufer: AbasMapperBasis.
de.pahling.home.abas.mapper.AbasExtraktFelder (final class, Utility)
Zweck: macht ein Record flach zu einer Map für Solr.
Methode: static Map<String,Object> flach(Object extrakt): rekursiv, null und leere Listen fallen weg, putIfAbsent (der erste gewinnt, z.B. landCode).
Aufrufer: SolrArtikelService.schreibe, SolrKontakteService.schreibe, ArtikelmappenService.userGetFertigung.
de.pahling.home.abas.mapper.AbasHerkunft (record)
Komponenten: Art art, String objectType, String gruppe, String name, List<Eingabe> eingaben. enum Art {OBJEKT, INFOSYSTEM}, record Eingabe(String feldName, String woher) (woher ist Klartext, null heißt leer senden).
Methoden:
static AbasHerkunft objekt(String objectType): Herkunft per byId.
static AbasHerkunft infosystem(String gruppe, String name, Eingabe... eingaben): Herkunft per Infosystem; der objectType wird als infosys:data:g:n gebaut.
static Eingabe eingabe(String feldName, String woher): Fabrik für eine Eingabe.
String toString(): lesbare Form für den Mapper-Tab.
Nutzer: jedes AbasMapper.herkunft(), AbasMappingAnsicht.
de.pahling.home.abas.mapper.AbasInfosystem (record)
Komponenten: String gruppe, String name, List<Eingabe> eingaben; record Eingabe(String feldName, String herkunft). In den Zeilen ist herkunft der Name eines Extrakt-Feldes oder null.
Methode: String toString(): POST infosys/data/g/n ⟵ feld=herkunft…
Nutzer: AbasZuordnung.infosystem(...), AbasZuordnungAusfuehrer, AbasHandler (Boot-Check), AbasMappingAnsicht.adresseVon.
de.pahling.home.abas.mapper.AbasMapper<T> (interface)
Zweck: Vertrag „eine Antwort rein, ein Extrakt T raus“, ohne Solr und ohne Kontext.
Methoden:
AbasHerkunft herkunft(): woher die Antwort kommt.
default String objectType(): herkunft().objectType().
String bezeichnung(): Einsatzzweck im Klartext.
String quelle(): Quellenname der eigenen Zeilen.
List<AbasZuordnung> zuordnungen(): die Vorschrift.
Class<T> extraktTyp(): das Record.
AbasExtrakt<T> bildeExtrakt(JsonNode antwort): Sync-Weg ohne Befunde.
AbasExtrakt<T> bildeExtraktMitBefunden(JsonNode antwort): Tab-Weg mit Befund je Zeile.
default boolean hatSubfetches(): hat die Vorschrift Subfetch- oder Infosystem-Zeilen?
de.pahling.home.abas.mapper.AbasMapperBasis<T> (abstract class implements AbasMapper)
Zweck: gemeinsamer Unterbau: objectType prüfen, Ausführer laufen lassen, Record bauen.
Konstruktor: protected AbasMapperBasis(AbasZuordnungAusfuehrer, AbasResponseParser, Class<T>)
Methoden:
Class<T> extraktTyp(): liefert den Record-Typ.
AbasExtrakt<T> bildeExtrakt(JsonNode): prüft und führt ohne Befunde aus.
AbasExtrakt<T> bildeExtraktMitBefunden(JsonNode): prüft und führt mit Befunden aus.
Privat: pruefeUndLiesData wirft eine AbasFetchException bei fehlendem content.data oder falschem objectType.
de.pahling.home.abas.mapper.AbasSyncZiel<T> (interface)
Zweck: Wohin ein Extrakt im Sync geht, je objectType ein Ziel.
Methoden:
default String objectType(): kernMapper().objectType().
String bezeichnung(): Klartext fürs Typ-Auswahlfeld.
AbasMapper<T> kernMapper(): der Mapper, dessen Extrakt das Ziel bekommt.
void schreibe(String sternzeit, AbasExtrakt<T>): schreibt das Extrakt.
AufloeseErgebnis loesche(String sternzeit): löst eine in ABAS gelöschte Sternzeit ohne ABAS-Aufruf auf.
Nested record AufloeseErgebnis(String kern, List<String> geloeschteDocIds) mit static leerlauf(String kern).
Implementierungen: SolrArtikelService (Kern artikel), SolrKontakteService (Kern kontakte), ArtikelReferenzobjektSyncZiel (Queue), ChanceAbasService (Postgres chance_abas).
de.pahling.home.abas.mapper.AbasUmrechnung (enum)
Werte: siehe Teil A, Abschnitt 2.
Methoden:
Object wandle(String roh): Wert nach der Umrechnung; null bei leer oder unlesbar.
String beschreibung(): Halbsatz für die Anzeige.
de.pahling.home.abas.mapper.AbasZuordnung (record)
Zweck: eine Zeile der Mapping-Vorschrift, die zugleich ausgeführt und angezeigt wird.
Komponenten: quelle, List<AbasAdresse> adressen, solrFeld (= Komponentenname im Record, später Solr-Feld), umrechnung, Mehrfach mehrfach, boolean nichtUeberschreiben, String nurWennTyp, String sonderweg, String nurWennGesetzt, AbasMapper<?> subMapper, String quellFeld, AbasInfosystem infosystem, boolean mappingAbbrechenWennSubfetchScheitert, String uebersprungenBisAbasLiefert
Enums: Mehrfach {ERSTER, ALLE, ALLE_ENTDOPPELT, ALLE_MIT_LUECKEN}, Art {FELD, SUBFETCH, INFOSYSTEM, SONDERWEG}
Konstruktoren: Kurzform mit 7 Parametern (Feld) und mit 8 Parametern (Sonderweg).
Methoden:
Art art(): leitet die Zeilenart ab (infosystem → INFOSYSTEM, subMapper → SUBFETCH, sonderweg → SONDERWEG, sonst FELD).
boolean istSubfetch(): subMapper != null, gilt auch für Infosystem-Zeilen.
static kopf(quelle, feldName, teil, solrFeld): Kopffeld, ERSTER.
static kopf(quelle, feldName, teil, solrFeld, umrechnung): Kopffeld mit Umrechnung.
static meta(quelle, metaFeld, solrFeld): Wert aus content.data.meta.
static kaskade(quelle, solrFeld, AbasAdresse...): die erste liefernde Adresse gewinnt.
static zeile(quelle, filterFeld, filterWert, feldName, teil, solrFeld, mehrfach): Tabellenfeld.
static zeile(quelle, AbasAdresse, solrFeld, mehrfach): Tabellenfeld mit beliebiger Adresse (z.B. Namensauswahl).
static subfetch(quelle, feldName, zielFeld, subMapper): Sternzeit aus dem Kopffeld holen und den Sub-Mapper anwenden.
static subfetchAusFeld(quelle, extraktFeld, zielFeld, subMapper): Sternzeit aus einem Extrakt-Feld (Kette).
static infosystem(quelle, gruppe, name, List<Eingabe>, zielFeld, subMapper): Infosystem aufrufen und mappen.
static sonderweg(quelle, feldName, teil, solrFeld, erklaerung) und sonderweg(quelle, adresse, zielFeld, erklaerung): Code setzt das Feld (Übergang).
Nachsetzer (liefern jeweils eine Kopie): ohneUeberschreiben(), nurFuerTyp(String), nurWennGesetzt(String), wennSubfetchScheitertMappingAbbrechen(), uebersprungenBisAbasLiefert(String).
de.pahling.home.abas.mapper.AbasZuordnungAusfuehrer (@Service)
Zweck: der einzige Ort, an dem Zuordnungen angewendet werden, für Sync und Tab gleich.
Abhängigkeiten: AbasResponseParser, AbasRestService
Konstante: STERNZEIT = \(\d+,\d+,\d+\)
Nested:
record Befund(adresse, quelle, solrFeld, feldDa, rohWert, wert, umrechnung, fundorte, uebersprungen): Zeile der Frontend-Ansicht. Bei Subfetches steht in umrechnung die Art („Subfetch“/„Infosystem g/n“).
record Lauf(felder, befunde, fehler, anfragen) mit befundeOderLeer().
Methode: Lauf fuehreAus(List<AbasZuordnung>, String quelle, JsonNode data, boolean mitBefunden): führt die Vorschrift gegen eine Antwort aus.
Reihenfolge der Bedingungen bei FELD: übersprungen → nurWennTyp (vergleicht felder["type"]) → nurWennGesetzt → nichtUeberschreiben → leer bzw. fehlt → schreiben.
Aufrufer: AbasMapperBasis.
de.pahling.home.abas.mapper.ArtikelKernMapper (@Service)
Siehe Teil A, Abschnitt 3.
Konstanten: public static final OBJECT_TYPE = "obj:data:2:1", Q = "2er (Artikel)", KOPF, TABELLE.
Konstruktor-Abhängigkeiten: ReferenzobjektMapper, LieferantenArtikelMapper, HerstellerMapper, KundenArtikelnummernMapper.
Ziel: Solr-Kern artikel über SolrArtikelService.
de.pahling.home.abas.mapper.ChanceMapper (@Service)
Zweck: VK-Chance; der erste Mapper ohne Solr-Kern, sein Ziel ist Postgres chance_abas.
Konstanten: OBJECT_TYPE = "obj:data:3:30", Q = "3:30 (Chance)", KOPF, POSITIONEN. Keine Subfetches.
Kopf:
meta.id → sternzeit, nummer → chanceNummer, such, schlag → schlagwort, typa TEXT → typ, term → termin (bleibt Text)
repstatus/serstatus/opstatus TEXT → reportStatus/bearbeitungsStatus/opStatus
kunde VALUE → kundeAbasId (STERNZEIT), kunde TEXT → kundeNummer, kundename TEXT → kundeName, analog für reempf… und warenempf…
ans2/str2/plz2/nort2 → firma/strasse/plz/ort, staatname2 TEXT → land, adresse2 → adresse, tele, email, waehr/spra/drbelegart TEXT → waehrung/sprache/belegart
erfasserzeichen → erfasser, zeich → bearbeiter, erfass/stand → abasErstelltAm/abasGeaendertAm (ABAS_DATUM), versionn → abasVersion, dokumentbaurl → dmsDokumentUrl, sidebarbaurl → dashboardUrl
Positionen (Filter „artikel vorhanden“, ALLE_MIT_LUECKEN): zn → positionNummern, id → positionSternzeiten, artikel VALUE/TEXT → artikelAbasIds/artikelNummern, tename → artikelNamen, zkuartnr → kundenArtikelNummern, mge TEXT → mengen, le TEXT → einheiten, term → termine, typa TEXT → positionTypen, krit TEXT → kritisch, chzuordnungtext TEXT → chargenZuordnungen.
de.pahling.home.abas.mapper.FertigungslisteMapper (@Service)
Zweck: Infosystem fe/PRODLIST, laufende Fertigung für den Artikelmappen-Tab „Fertigung“. Kein Kern.
Konstanten:
OBJECT_TYPE = "infosys:data:fe:PRODLIST", Q = "Infosystem fe/PRODLIST"
public static final List<String[]> SPALTEN: Komponente → Anzeigename, 16 Spalten
ZEILEN: 16 Zeilen mit Filter „ba vorhanden“, TEXT, ALLE_MIT_LUECKEN: zn, ba, vorgang, mge, limge, ofmge, artle, tsterm, tterm, dispo, bezplatz, bezlgruppe, bezkstelle, proc, engmatz, engkapazz → fertigung…
Herkunft: infosystem("fe","PRODLIST", kart="die Artikelnummer", bstart=leer)
Aufrufer: ArtikelmappenService.userGetFertigung über AbasHandler.holeInfosystemUndBildeExtrakt.
de.pahling.home.abas.mapper.HerstellerMapper (@Service)
OBJECT_TYPE = "obj:data:1:1", Q = "1:1 (Hersteller)"
Eine Zeile: Kaskade name VALUE → such VALUE → herstellerName.
Sub-Mapper von ArtikelKern über subfetchAusFeld(herstellerAbasId).
de.pahling.home.abas.mapper.KontakteKernMapper (@Service)
Siehe Teil A, Abschnitt 3.
Konstanten: OBJECT_TYPE = "obj:data:19:0", TYPE_ADRESSE, TYPE_PERSON, Q = "19er".
Ziel: Solr-Kern kontakte. TYPE_PERSON wird auch in SolrKontakteService.findePersonenZurFirma genutzt.
de.pahling.home.abas.mapper.KundenArtikelnummernMapper (@Service)
OBJECT_TYPE = "infosys:data:st:CUSTOMERPRODUCT"
Zeilen (Filter „tkunde vorhanden“, ALLE_MIT_LUECKEN):
tkunde TEXT → kundenNummer
tkuartnr VALUE → kundenArtikelNummer
Namensauswahl tkuartbez|tkubez|tkuartname|tkuarttext TEXT → kundenArtikelName, übersprungen bis ABAS liefert
Herkunft: infosystem("st","CUSTOMERPRODUCT", kartikel, bstart=leer).
de.pahling.home.abas.mapper.KundenKopieMapper (@Service)
OBJECT_TYPE = "obj:data:0:1", Q = "Kunden-Kopie". 29 Kopfzeilen → KundenKopie.
Besonderheiten:
egkenn → landCode mit ohneUeberschreiben
limit → kundeKreditlimit (DEUTSCHE_ZAHL → Double)
Kaskade sprai TEXT/VALUE, spr TEXT/VALUE → kundeSprache
erechok → kundeERechnungOk (JA_NEIN → Boolean)
grentf → kundeGrenzentfernung (GANZZAHL → Integer)
übrige Felder: selbukreis(name), dndbelegart, belartkunde, laarta, lbed(name), zbed(name), pbed(name), mbed, vart, betreuer(name), vertret(name), haendl, branche, waehr, erechmail, frbez, gart, stverf, vzweig
de.pahling.home.abas.mapper.LieferantenArtikelMapper (@Service)
OBJECT_TYPE = "obj:data:21:0"
Zeilen (Filter „ylieferant vorhanden“, ALLE_MIT_LUECKEN):
ylieferant TEXT → lieferantenNummer
Namensauswahl ylieferantartnr|yliefartnr|yartnrlief|ylieferantenartikelnummer → lieferantenArtikelNummer
Namensauswahl ylieferantartbez|… → lieferantenArtikelName (übersprungen)
de.pahling.home.abas.mapper.LieferantenKopieMapper (@Service)
OBJECT_TYPE = "obj:data:1:1", Q = "Lieferanten-Kopie". 16 Zeilen → LieferantenKopie: landCode (ohneUeberschreiben), Buchungskreis, Belegart (belartlief), Landart, Liefer-, Zahlungs- und Preisbedingungen, Mahnbedingung, Branche, Währung, Kaskade Sprache.
de.pahling.home.abas.mapper.ReferenzobjektArtikelMapper (@Service)
OBJECT_TYPE = "obj:data:20:0". Eine Zeile: yartikel VALUE → artikelAbasId (STERNZEIT).
Kern-Mapper des ArtikelReferenzobjektSyncZiel.
de.pahling.home.abas.mapper.ReferenzobjektMapper (@Service)
OBJECT_TYPE = "obj:data:20:0"
8 Zeilen: Filter yartreferenz = WERKZEUG | GEHTMIT | ALTERNATIVE | BENOETIGT, je yreferenz TEXT → related…ArtikelNummer und VALUE → related…AbasId (ALLE_MIT_LUECKEN).
de.pahling.home.abas.mapper.WertemengeMapper (@Service)
OBJECT_TYPE = "obj:data:109:2", Quelle "109:2 (Wertemenge)"
9 Kopfzeilen: nummer, such → suchbegriff, name, langtxt → langtext, grbez → gruppe, dbez TEXT → datenbank, stand, erfass → erfasst, zeich → bearbeiter (alle String, ohne Datumsumrechnung).
Nur Anzeige.
Für alle Mapper-Unterklassen gilt dieselbe öffentliche API: herkunft(), bezeichnung(), quelle(), zuordnungen() sowie die geerbten bildeExtrakt*, extraktTyp, objectType und hatSubfetches.

Paket abas\mapper\extrakt\ (alle sind reine Records ohne Methoden; jedes ist das Schema seines gleichnamigen Mappers)
Record	Komponenten
ArtikelKern	91 Komponenten, siehe Tabelle in Abschnitt 3 (inkl. Referenzobjekt referenzobjekt, LieferantenArtikel lieferantenArtikel, Hersteller hersteller, KundenArtikelnummern kundenArtikelnummern)
Chance	36 Skalare (sternzeit … dashboardUrl; abasErstelltAm/abasGeaendertAm als Date) und 12 List<String> für die Positionen
Fertigungsliste	16 List<String> fertigung…
Hersteller	String herstellerName
KontakteKern	38 Komponenten, inkl. KundenKopie kundenKopie, LieferantenKopie lieferantenKopie
KundenArtikelnummern	List<String> kundenNummer, kundenArtikelNummer, kundenArtikelName
KundenKopie	30 Felder; Double kundeKreditlimit, Boolean kundeERechnungOk, Integer kundeGrenzentfernung, Rest String
LieferantenArtikel	List<String> lieferantenNummer, lieferantenArtikelNummer, lieferantenArtikelName
LieferantenKopie	16 String-Felder
Referenzobjekt	8 List<String> related…ArtikelNummer/AbasId
ReferenzobjektArtikel	String artikelAbasId
Wertemenge	9 String-Felder
Paket abas\service\
de.pahling.home.abas.service.AbasResponseParser (@Service)
Zweck: reine Lese-Helfer für die ABAS-JSON-Struktur content.data.{meta, head.fields[], table[].fields[]}.
Methoden:
JsonNode navigateToData(JsonNode): liefert content.data, die Wurzel nur bei meta+head, sonst null.
String extractObjectType(JsonNode): data.meta.objectType.
String extractAbasId(JsonNode): data.meta.id (die Sternzeit).
Map<String,JsonNode> parseFieldsArray(JsonNode): fields-Array → Map nach name (der letzte gewinnt).
String getFieldValue(Map, String): value oder null bei leer.
String getFieldText(Map, String): text oder null bei leer.
package-private static stringValue(JsonNode).
Nutzer: praktisch alle ABAS-Klassen.
de.pahling.home.abas.service.AbasTypKatalog (@Service)
Zweck: Verzeichnis der objectTypes mit Klartext und dem Kennzeichen „hat Handler“ (= Sync-Ziel). Ohne DB-Abfrage, beim Boot gebaut.
Konstanten:
TYP_MUSTER obj:data:(\d+):(\d+), STERNZEIT_MUSTER
OHNE_HANDLER: 0:1, 0:2, 1:1, 1:2, 21:0, 71:2, 71:3, 71:6, 71:7, infosys st:CUSTOMERPRODUCT, fe:PRODLIST, owqmapp:QMPRUEFAUFTRAEGE, 97:1, 109:2
Record: AbasTypEintrag(objectType, datenbank, bezeichnung, hatHandler)
Methoden:
Konstruktor (List<AbasSyncZiel>): Sync-Ziele plus OHNE_HANDLER (der Handler gewinnt), sortiert nach Datenbanknummer.
List<AbasTypEintrag> alleTypen(): alle bekannten Typen.
List<AbasTypEintrag> typenMitHandler(): nur die mit Sync-Ziel.
String bezeichnungVon(String objectType): Klartext oder der rohe String, nie null („noch nicht gefetcht“ bei leer).
static int datenbankAus(String objectType): mittlere Zahl des Typ-Strings, sonst -1.
static int datenbankAusSternzeit(String): mittlere Zahl der Sternzeit, sonst -1.
List<String> objectTypesMitHandlerFuerDatenbank(int): Routing-Schlüssel für den DELETE-Weg.
de.pahling.home.abas.service.AbasMappingAnsicht (@Service)
Zweck: Backend des Mapper-Tabs: Liste, Vorschrift und „Extrakt erzeugen“. Derselbe Code wie im Sync, es wird nichts geschrieben.
Endpunkte im AbasAdminController (/api/rest/abasadmin): GET /mapper, GET /mapper/{name}, POST /extrakte.
Records: MapperEintrag, VorschriftZeile(ebene, art, quelle, adresse, zielFeld, umrechnung, mehrfach, bedingung, subMapper), Vorschrift, Ergebnis(name, bezeichnung, extraktTyp, extrakt, befunde, fehler, anfragen, nichtGelesen, felderInAntwort, antwortJson).
Methoden:
List<MapperEintrag> adminGetMapper(): alle Mapper, sortiert nach Bezeichnung.
Vorschrift adminGetVorschrift(String name): alle Zeilen, Sub-Mapper rekursiv mit Ebene 1.
Ergebnis adminErzeugeExtraktAusJson(String name, String rohesJson): Rohtext parsen, passenden Mapper wählen, mit Befunden mappen.
Ergebnis adminErzeugeExtraktAusSternzeit(String name, String sternzeit): live per byId holen und mappen.
Ergebnis adminErzeugeExtraktAusInfosystem(String name, Map<String,Object> eingaben): das Infosystem des Mappers aufrufen und mappen.
nichtGelesen sind die Feldnamen der Hauptantwort, die keine Zeile anfasst.
de.pahling.home.abas.service.AbasDateiMapper (interface)
Zweck: Je Dokumentart (objectType) leitet ein Mapper aus der Subfetch-Antwort die DMS-Vorgaben für eine von ABAS geschobene Datei ab.
Record: DmsVorgabe(fileSubClassKey [Pflicht], fileIndex, fileDescription, Map<String,List<String>> hooks). Die Hooks sind die Whitelist DmsFelder.HOOK_FELDER.
Methoden:
boolean zustaendig(String objectType): ist der Mapper für diesen Typ zuständig?
DmsVorgabe mappe(String sternzeit, JsonNode abasAntwort): liest die Vorgaben; wirft eine IllegalArgumentException, wenn etwas fehlt (wird zum 400).
Stand: Es gibt im Code keine einzige Implementierung, deshalb greift immer der Default.
de.pahling.home.abas.service.AbasDefaultDateiMapper (@Component, implementiert das Interface bewusst nicht)
Methode: AbasDateiMapper.DmsVorgabe mappe(String sternzeit, JsonNode): Dateiart DmsService.TEMP_FSC_KEY ("TEMP") mit dem einzigen Hook sternzeit.
Aufrufer: AbasFileInboundService als ausdrücklicher Rückfall.
de.pahling.home.abas.service.BezugsnummerService (@Service)
Zweck: Löst eine beliebige eingetippte Nummer live bei ABAS auf und liefert Typ und Sternzeit.
Konstanten: INFOSYSTEM_GRUPPE = "ow1", INFOSYSTEM_NAME = "BELEG2ID", FELD_BELEG = "ybeleg", FELD_START = "bstart", FELD_ID = "yid", STERNZEIT_MUSTER.
Record: BezugsnummerAntwort(bezugsNummer, gefunden, sternzeit, objectType, bezeichnung, meldung)
Methode: BezugsnummerAntwort userLookupBezugsnummer(String):
Infosystem BELEG2ID mit ybeleg=Nummer und bstart=leer aufrufen. Ein 400 von ABAS gilt als „unbekannt“.
yid der ersten Tabellenzeile lesen.
systemFetchAbasObjektByIdSchlank holt nur den Kopf, um den objectType zu bekommen.
Klartext über AbasTypKatalog.bezeichnungVon.
Aufrufer: AbasUserController POST /api/rest/abas-user/bezugsnummern. Schreibt nichts.
de.pahling.home.abas.service.AbasFileInboundService (@Service)
Zweck: Datei-Zuschub von ABAS, synchron.
Konstante: PFLICHT_HOOKS = ["bezugsNummer", "sternzeit"]
Record: EmpfangsErgebnis(objectType, fileSubClassKey, FileDataService.UploadErgebnis upload)
Methoden:
EmpfangsErgebnis empfangeDatei(String sternzeit, DateiQuelle datei, String fileName, String mimeType, long fileSizeBytes): Eingaben prüfen (400), Subfetch per byId, ersten zuständigen AbasDateiMapper oder Default anwenden, dann fileDataService.systemUploadFileData(...).
FileDataService.UploadErgebnis empfangeMultipart(Map<String,List<String>> hooks, DateiQuelle, fileName, mimeType, size): Hook-Zuschub ohne Subfetch und ohne Mapper; Pflicht-Hooks prüfen, Ablage immer in TEMP.
Ziel: DMS über den FileDataService-Upload-Kern (SFTP, Hash, Tika, DMS-Dokument).
Aufrufer: AbasErpController (/api/rest/abas/file-multipart, /file64 → empfangeDatei; /multipart → empfangeMultipart).
de.pahling.home.abas.service.AbasFileLogAufraeumer (@Service)
Zweck: löscht jede Nacht um 03:30 (@Scheduled(cron="0 30 3 * * *"), @Transactional) Einträge, die älter als 90 Tage sind.
Konstante: AUFBEWAHRUNG = Duration.ofDays(90)
Methode: void raeumeAuf(): ruft abasFileLogRepository.deleteByEmpfangenAtBefore(now - 90 Tage).
Tabelle: Postgres abas_file_log.
de.pahling.home.abas.service.AbasFetchException (extends RuntimeException)
Zweck: die eine Ausnahme für „mit ABAS hat es nicht geklappt“; die Meldung soll die URL tragen.
Konstruktoren: (String message), (String message, Throwable cause).
Wird von Controllern meist als 502 übersetzt. Auch die Mapper und Sync-Ziele werfen sie bei fachlich unbrauchbaren Antworten.
Paket abas\chance\
de.pahling.home.abas.chance.model.ChanceAbas (@Entity, @Table chance_abas)
Zweck: Die Chance genau so, wie ABAS sie liefert. Wird bei jedem Neu-Fetch überschrieben. Was wir selbst pflegen, gehört laut Kommentar nach chance_db; laut ChanceAbasService wurde das am 2026-09-06 zurückgebaut.
Spalten:
id (IDENTITY), sternzeit (unique, not null), chance_nummer (unique, not null)
alle Skalare aus Chance; abasErstelltAm/abasGeaendertAm als Instant
last_fetch_from_abas
JSON-Text-Spalten (@JsonIgnore): artikel_nummern, master_artikel_nummern (noch nicht befüllt), position_nummern, position_sternzeiten, artikel_abas_ids, artikel_namen, kunden_artikel_nummern, mengen, einheiten, termine, position_typen, kritisch, chargen_zuordnungen
Öffentliche Methoden: Lombok-Getter und -Setter, dazu 13 @JsonProperty-Getter get…AlsListe(), die per JsonTextListen.jsonAlsListe echte Arrays an der API liefern.
de.pahling.home.abas.chance.repository.ChanceAbasRepository (JpaRepository<ChanceAbas, Long>)
Optional<ChanceAbas> findBySternzeit(String)
Optional<ChanceAbas> findByChanceNummer(String)
Wird nur vom ChanceAbasService benutzt.
de.pahling.home.abas.chance.service.ChanceAbasService (@Service, implements AbasSyncZiel<Chance>)
Zweck: Sync-Ziel für obj:data:3:30, schreibt in Postgres chance_abas; außerdem die Lesetür fürs Frontend.
Konstante: PROJEKT_PLANUNG_ROLLE = "PROJEKT_PLANUNG"; adminKey aus ${gebieter.accessrole.admin-key}.
Methoden:
String bezeichnung(): „Chance (VK-Chance → Projektplan)“.
AbasMapper<Chance> kernMapper(): der ChanceMapper.
void schreibe(String sternzeit, AbasExtrakt<Chance>) (@Transactional): Upsert über die Sternzeit, Rückfall chanceNummer; ohne Nummer AbasFetchException. artikelNummern wird entdoppelt gespeichert, die Positionen als parallele JSON-Arrays; setzt lastFetchFromAbas.
AufloeseErgebnis loesche(String sternzeit) (@Transactional): löscht die Zeile; kern = null.
List<ChanceAbas> userLookupChancen(boolean offen, String chanceNummer): prüft die Rolle ADMIN oder PROJEKT_PLANUNG (sonst 403) und liefert entweder genau eine Chance nach Nummer oder alle, sortiert nach lastFetchFromAbas absteigend.
de.pahling.home.abas.chance.controller.ChanceController (@RestController /api/rest/chancen)
List<ChanceAbas> lookupChancen(@RequestBody(required=false) Map<String,Object> body), POST /lookup: liest offen und chanceNummer aus dem Body und ruft chanceAbasService.userLookupChancen.
Auffälligkeiten
Zwei Mapper je objectType: obj:data:1:1 gilt für HerstellerMapper und LieferantenKopieMapper, obj:data:20:0 für ReferenzobjektMapper und ReferenzobjektArtikelMapper. Im Sync ist das eindeutig, weil das Sync-Ziel den Kern-Mapper festlegt. Im Mapper-Tab und in AbasHandler.mapperFuer gewinnt dagegen „der erste“ bzw. kommt eine Liste zurück.
Herkunft doppelt beschrieben: Die Infosystem-Eingaben stehen zweimal da – einmal in AbasHerkunft des Sub-Mappers als Klartext („die Artikelnummer des Elternobjekts“), einmal in AbasInfosystem der Eltern-Zeile als Extrakt-Feldname (artikelNummer). Ausgeführt wird nur Letzteres.
Kommentar und Code widersprechen sich bei KONTAKT_TYP: Der Kommentar sagt „alles andere ist ein Fehler, kein null“, der Code liefert aber null. Geworfen wird erst in SolrKontakteService.schreibe.
Zwei Felder werden nie gefüllt: kundenArtikelName und lieferantenArtikelName sind dauerhaft übersprungen und bleiben damit leer. Sie fallen deshalb in AbasExtraktFelder.flach aus dem Solr-Dokument heraus.
Parameter ohne Wirkung: ChanceController übergibt offen, aber userLookupChancen wertet den Parameter nicht aus.
Keine echten Datei-Mapper: AbasDateiMapper hat derzeit keine Implementierung; jeder Datei-Zuschub landet in TEMP mit dem Hook sternzeit.
3:53 AM
Beide Zusammenfassungen sind da. Ich prüfe noch kurz, ob app1 schon einen System-User im Code hat, denn der ABAS-Worker wäre der erste Hintergrundjob.



Grep "SYSTEM_USER|systemUser|SystemUser|SERVICE_ACCOUNT|serviceAccount" (in C:\Users\Bindemaja\Documents\dev\app1\gitfiles\spring\src\main\java)
27 lines of output
Grep "export (async )?function (upload|request|download)|onProgress|XMLHttpRequest" (in C:\Users\Bindemaja\Documents\dev\app1\gitfiles\frontend-ts\src\api)
10 lines of output
3:55 AM
Ich habe den AbasManager der Vorlage komplett durch: rund 70 Klassen im Backend, 10 Tabs im Frontend. Unten der Funktionsüberblick, dann was bei uns anders wird, dann Paketvorschlag und Stufen. Code schreibe ich erst nach deinem Ja zur Klassenliste der ersten Stufe.

Was der AbasManager der Vorlage kann
Grundbegriff. Eine Sternzeit ist die ABAS-Objektkennung in Klammerform, etwa (87448,19,0). Die mittlere Zahl ist die ABAS-Datenbank: 2 Artikel, 19 Kontakte, 20 Referenzobjekte, 21 Lieferantenartikel. Jedes Objekt trägt einen objectType wie obj:data:2:1.

Acht Bausteine:

Eingang von ABAS. de.pahling.home.abas.controller.AbasErpController unter /api/rest/abas/**, in der Filterkette offen. ABAS pusht POST /sternzeit/{id} für „hat sich geändert“ und DELETE /sternzeit/{id} für „wurde gelöscht“. Autorität ist der Header X-ABAS-Token plus eine optionale Absender-IP-Liste, beides prüft der Controller selbst. Die Antwort hat immer dieselbe Form mit Sternzeit, angekommen und Meldung. Status 200 heißt neu eingereiht, 208 stand schon so drin, dazu 400, 403 und 500. Dazu drei Datei-Wege: Multipart mit Sternzeit, Base64 im JSON, und Multipart mit fertigen Hooks, der als Dateiart TEMP ins DMS geht. Jeder Push landet in abas_log, jeder Dateieingang mit komplettem Body in abas_file_log.

Queue und Worker-Pool. Tabelle abas_sternzeit_queue, Primärschlüssel die Sternzeit, dazu method POST oder DELETE, quelle ABAS, ADMIN, USER oder SYSTEM, queuedAt, claimedAt, attempts und letzterFehler. Einreihen ist idempotent, bei anderer Methode gewinnt der Letzte. Ein einzelner Dispatcher-Thread in de.pahling.home.abas.service.AbasQueueWorkerPool zieht nur so viele freie Einträge, wie die Semaphore Scheine hat, Worker kommen aus einem Cached-Pool. Die Parallelität ist zur Laufzeit zwischen 1 und 100 verstellbar, dazu ein Pause-Schalter. Geweckt wird nach jedem Einreihen und jedem fertigen Worker. de.pahling.home.abas.service.AbasQueueWatcher tickt alle fünf Sekunden als Sicherheitsnetz und gibt Claims frei, die älter als fünf Minuten sind. Bei einem Fehler zählt attempts hoch und die Meldung steht am Eintrag. Ab dem Limit bleibt der Eintrag mit seinem Fehler liegen und wird nicht mehr gezogen, der Retry-Knopf setzt ihn zurück. Zwei Zähler im RAM messen Durchsatz und Fehler.

Stammsatz. de.pahling.home.abas.model.AbasSternzeit in abas_sternzeit: sternzeit, objectType, abasLastSent, lastFetchFromAbas, syncDatum, fetchCounter. Es gibt kein Statusfeld. Ob etwas geholt werden muss, ist eine Abfrage über die beiden Zeitstempel. Die Indizes sind für hundert Millionen Zeilen gebaut, Teilindizes stehen als SQL-Datei, Zählungen kommen ab einer halben Million geschätzt aus dem Postgres-Katalog.

HTTP-Client zu ABAS. de.pahling.home.abas.service.AbasRestService hat einen einzigen Kern mit Adresse, Basic Auth, Timeouts und Fehlerdeutung. Die Adresse ist https://host:port/mw/r/<mandant>/…. Darüber liegen kurze Methoden: Objekt per Id, voll oder schlank mit Feldliste und Zeilenfenster, Infosystem generisch mit Gruppe, Name und Eingaben in Reihenfolge, der Rückweg per POST und Workspace-Editor-Sitzungen für die QM-Werteerfassung. Die Rollen ABAS_READ und ABAS_RETURN hängen an den Methoden für Menschen. Das selbstsignierte Zertifikat wird blind akzeptiert.

Mapper-Unterbau in de.pahling.home.abas.mapper. Eine Vorschrift ist eine Liste von Zeilen, jede Zeile wird ausgeführt und zugleich im Tab angezeigt. Eine Zeile nennt die Adresse in der Antwort, also Kopf, Tabelle mit Zeilenfilter oder Meta, dazu value oder text, Kaskaden und Namensauswahl. Sie nennt das Zielfeld, eine Umrechnung wie Datum, Zahl, Ja/Nein oder Sternzeit, eine Mehrfach-Regel und Bedingungen. Zwei besondere Zeilenarten holen live nach: Subfetch mit der Sternzeit aus einem Kopffeld oder einem schon gefüllten Feld, und Infosystem mit Eingaben aus dem Extrakt. Das Ergebnis je Mapper ist ein Record in de.pahling.home.abas.mapper.extrakt als festes Schema. Der Boot-Check prüft jede Zeile gegen die Record-Komponenten und verbietet Subfetch-Tiefe über eins. de.pahling.home.abas.mapper.AbasSyncZiel je objectType nimmt das Extrakt entgegen: Artikel als Full-Upsert in den Kern artikel mit id gleich Artikelnummer, syncStatus und Vorschaubild aus dem DMS, Kontakte in den Kern kontakte, das Referenzobjekt reiht nur seinen Artikel neu ein, die Chance geht nach Postgres. Der DELETE-Weg löst über das Feld sternzeit auf und protokolliert je Dokument in abas_aufloese_log. de.pahling.home.abas.service.AbasTypKatalog kennt alle Typen mit Klartext und ob ein Ziel existiert. Der Artikel-Mapper hat 91 Zeilen: 72 Kopffelder, 15 Tabellenlisten für Stückliste und Arbeitsplan, drei Subfetches und ein Infosystem.

Admin-Modul, zehn Tabs unter /api/rest/abasadmin/** mit Rolle ABAS_ADMIN oder ADMIN:

Tab	Zeigt und kann	Endpunkte
Übersicht	Kopfzahlen, Typfilter, die letzten 1000 Stammsätze oder eine Suche nach exakter Klammerform; je Zeile fetchen, rohe Antwort ansehen, zum Löschen einreihen	GET stats, GET typkatalog, POST sternzeitsearch, POST und DELETE sternzeiten, POST und GET viewresponse
Queue	die vordersten 100 in FIFO-Ordnung, Polling alle 5 s, Retry und Entfernen	GET queue, POST queueretry, POST queuedelete
Queue-Fehler	nur Einträge mit Fehlversuchen, kein Polling, weil die Abfrage teuer ist	GET queuefehler
Empfangslog	Pushes und Auflösungen, exakter Sternzeit-Filter, Aufräumen ab Stichdatum, ein reiner ABAS_ADMIN nur älter als zwei Wochen	GET log, GET aufloesungen, DELETE log
Dateieingänge	seitenweise mit Status-Badge, Body je Zeile aufklappbar, räumt sich nach 90 Tagen selbst	GET multipart
Fetch-Steuerung	Polling alle 10 s, Abfragen pro Sekunde aus Zählerdifferenzen, Regler für parallel, Pause und Start	GET und POST fetchcontrol, POST parallel
Import	Datei beliebigen Formats, Vorzählung im Browser je Datenbank, Typauswahl, Antwort als NDJSON-Strom mit Fortschritt; zweite Box stellt die Stammtabelle aus NDJSON wieder her	POST sternzeitimport, POST sternzeittabelleimport
Export	zwei Streaming-Downloads: nackte Sternzeiten als Queue-Futter, die Tabelle als NDJSON-Inventar	GET export/sternzeiten, GET export/sternzeittabelle
Resync	je objectType die Anzahl, alle Stammsätze eines Typs neu einreihen in Stapeln zu 10.000, der Stempel ist der Cursor	POST objecttypes, POST resync
Mapper	Mapperliste, Vorschrift je Mapper mit aufgeklappten Sub-Mappern, Probe über Sternzeit live, Infosystem oder Rohtext, Extrakt als JSON plus Befund je Zeile	GET mapper, GET mapper/{name}, POST extrakte
User-Wege unter /api/rest/abas-user/**. Der Resync-Knopf der Artikelmappe reiht per POST /fetchrequest/{sternzeit} ein, Rolle ABAS_READ. POST /bezugsnummern löst jede Beleg- oder Bezugsnummer über das Infosystem ow1/BELEG2ID in Typ und Sternzeit auf, für jeden Angemeldeten. Das nutzen die Bezugsnummer-Seite und der Hook-Scan beim Mail-Archivieren. Dazu ein Knopf mit dem URL-Schema abasurl:, der das Objekt im ABAS-Client öffnet.

Konfiguration gebieter.abas.*: host, port, mandant, basis-pfad, api-user, api-password, inbound-token, dazu sync.parallel 20, takt-ms 5000, claim-timeout-ms 300000, max-fehler 10, start-pausiert false.

Was bei uns anders wird
Die Vorlage zeigt, was anfällt. Die Struktur folgt unseren Regeln:

Pfade. Der ERP-Eingang liegt unter /api/rest/v1/public/abas/…, denn public heißt ohne Anmeldung, und das Token prüft der Controller wie in der Vorlage. Der Admin-Teil liegt unter /api/rest/v1/admin/abas/… mit Tür ADMIN, wie SolrManager und GraphManager. Eine delegierbare AR ABAS_ADMIN ginge später über @PreAuthorize im Service, erst wenn du sie brauchst.
Keine URL-Parameter. Die GET-Endpunkte mit limit, sternzeit oder seite werden POST mit Body. Die rohe ABAS-Antwort zeigt die Maske selbst, ein zweiter GET zum Öffnen im neuen Tab entfällt.
Current user. Worker-Threads haben keinen Token. Sie laufen als System-User über den Security-Context, so wie du es am 2026-10-09 entschieden hast, und dieser Worker ist der erste Job. Dafür fehlt noch die Zeile in users ohne Credentials und eine kleine Umschalt-Hilfe im Paket system. Keine Methodenpaare mit Präfix wie in der Vorlage, der Auslöser steht bei uns nie im Namen.
Schreiben in einen Kern nur über den Schreibservice des Kerns nach der DMS-Blaupause, später codergod1337.app1.solr.artikel…. Kein eigener SolrClient im abas-Paket wie SolrArtikelService der Vorlage. Die Zielfelder einer Vorschrift müssen als Zeilen in solr_field des Kerns stehen, denn Felder legt nur der SolrManager-Tab an. Der Boot-Check der Mapper prüft deshalb gegen solr_field statt gegen ein Record.
Zertifikat. Kein blindes Vertrauen. Wie beim SFTP-Host-Key wird das Zertifikat des ABAS-Servers über die .env gepinnt. Die Absender-IP-Liste bleibt Zugabe, das Token ist die Autorität.
Frontend nach unseren Regeln: Modul modul/abasmanager/, der eine Subheader, die zentrale Table, Hoverlay statt title, DeletePopup statt confirm, Toast statt alert, alle Texte in de, en und bg. Der Resync-Tab nimmt den Typkatalog statt der festen Label-Liste der Vorlage. Polling ohne überlappende Requests. Der NDJSON-Import braucht eine Zeilen-Streaming-Funktion im zentralen Client in api/client.ts, dort gibt es heute nur Upload mit Fortschritt. Exports laufen als normaler Link wie der DMS-Download.
Tabellen heißen abas_* wie solr_*. Die Teilindizes kommen als SQL nach docs/backend.
Konfiguration app1.abas.* in der application.yml aus der .env: abas_host, abas_port, abas_mandant, abas_user, abas_pw, abas_inbound_token, abas_cert_path.
Vorerst weglassen: Rückweg und Editor-Sitzungen, Chance, Bezugsnummer, der abasurl-Knopf und der Datei-Zuschub. Der Datei-Zuschub braucht den DMS-Upload, und der steht seit dem 2026-10-10 auf der Merkliste.
Paketvorschlag und Stufen
Alles unter codergod1337.app1.abas, je Unterpaket controller, service, repository und model:

abas.client: der eine HTTP-Weg zu ABAS, Objekt per Sternzeit und Infosystem, die Ausnahme für „ABAS antwortet nicht“ nach dem Muster von codergod1337.app1.system.sftp.SftpUnavailableException.
abas.sternzeit: Stammsatz, Queue-Eintrag, Push-Protokoll und Auflöse-Protokoll, Queue-Schreiben, Pool, Watcher, der öffentliche ERP-Controller und der Admin-Controller mit Übersicht, Queue, Protokoll, Fetch-Steuerung, Import, Export und Resync.
abas.mapper: Zuordnung, Adresse, Umrechnung, Ausführer, Mapper-Vertrag, Typkatalog und die Probe für den Mapper-Tab. Je Kern ein Unterpaket wie abas.mapper.artikel.
abas.sync: der Sync-Ziel-Vertrag und je Kern die Brücke vom Extrakt zum Schreibservice des Kerns.
abas.file: Datei-Zuschub und sein Protokoll, erst in Stufe 5.
Außerhalb von abas nur eins: die System-User-Umschaltung im Paket codergod1337.app1.system.security, denn jeder spätere Job wie Freigabe-Ablauf oder Reindex braucht sie auch.
Die Stufen, jede für sich lauffähig:

Fundament ohne Mapper. Konfiguration, Client nur mit Objekt per Sternzeit, die vier Entities, Queue, Pool und Watcher mit System-User, ERP-Eingang POST und DELETE, Admin-Endpunkte und Frontend-Tabs Übersicht, Queue, Queue-Fehler, Empfangslog und Fetch-Steuerung. Ergebnis: Pushes kommen an, werden geholt und als Stammsatz notiert. Ohne Ziel gilt „geholt und notiert“ als verarbeitet, genau wie in der Vorlage.
Import, Export, Resync mit NDJSON-Strom und Streaming-Download.
Mapper-Unterbau mit Typkatalog und Mapper-Tab, erst ohne Ziel. Hier beantworten wir die beiden Fragen aus ziele.md: Wie eine Zeile die Quelle im ABAS-Objekt auf Feldname und Typ im Kern und auf Hooks abbildet, und dass Unterabrufe und Infosysteme als Zeilen der Vorschrift gesteuert werden, sichtbar im Tab.
Kern ARTIKEL. Felder im SolrManager-Tab anlegen, Schreibservice in solr.artikel nach DMS-Muster, Sync-Ziel ARTIKEL mit Auflösung beim DELETE und Auflöse-Protokoll.
Datei-Zuschub, sobald der DMS-Upload wieder steht.
User-Wege: Resync-Knopf an der Artikelmappe und Bezugsnummer, wenn die Masken da sind.
Für Stufe 1 brauche ich drei Antworten. Erstens: Bleibt „Sternzeit“ als Hausbegriff in Klassen- und Tabellennamen, also AbasSternzeit und abas_sternzeit, der Rest Englisch wie überall in app1? Zweitens: Reicht für den Admin-Teil die Tür ADMIN unter /admin/abas, oder willst du gleich eine eigene AR ABAS_ADMIN wie in der Vorlage? Drittens: Beim ERP-Push die Sternzeit im Body nach Hausregel, oder im Pfad wie in der Vorlage, weil die ABAS-Skripte so verdrahtet sind?

Danach lege ich das Model der Stufe 1 vor, also die vier Entities mit ihren Feldern, und daraus dann die Methodensignaturen.