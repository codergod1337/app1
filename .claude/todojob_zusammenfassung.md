> **Anzeige in VS Code:** `Strg+Umschalt+V` öffnet diese Datei als gerenderte Vorschau (`Strg+K V` = Vorschau neben dem Text).

# Todo-Jobs und Projekt-Manager der Vorlage: Bestandsaufnahme (Chat vom 2026-10-15)

Wortgleiche Kopie des Chats vom Abend des 2026-10-15. Grundlage: der Code der Vorlage unter `../../hobby/home-plus-extern/intern-instance/` (Backend `backend/src/main/java/de/pahling/home/projectmanager` und `…/todojob`, Frontend `frontend/src/projectmanager` und `frontend/src/todojob`), nicht die Docs der Vorlage und keine Memories. In app1 gibt es von beidem noch nichts.

Stand am Ende des Chats:
- Reihenfolge: zuerst die Todo-Jobs neu aufarbeiten, danach das Projekt-Modul. Ein Job muss ohne Projekt oder anderen Bezug existieren können.
- Die Struktur der Vorlage gilt als Murks (User). Nächster Schritt: Zielzustände und Ereignisse eines Jobs festlegen, dann überlegen, wie wir ihn am sinnvollsten umsetzen. Erst danach Tabellen und Klassen.
- Offene Frage von Claude, noch nicht beantwortet: Bezug am Job frei wie die Bezüge am Projekt der Vorlage (`{feld, wert}`), oder typisiert mit Art und id.
- Idee des Users zum Schluss: ein Solr-Kern für Jobs, damit das System „alte Jobs“ vorschlagen kann, etwa prüfen, ob Datenblätter oder sonstige Dinge zum Abarbeiten vorhanden sind, und so die einzelnen Bausteine vervollständigen. Lesart (alte Jobs wiederfinden, Lücken je Artikel finden, oder beides) noch offen, siehe Ende des Chats.
- Anforderungen des Users an den Job (ganz am Ende): immer Kontext für den Empfänger (Person oder Position); so viel wie möglich aus einem Fundus anklicken statt neu schreiben; Kontext über Hooks am Job; eine Antwort mit allgemein gültigem Wert über Hooks auffindbar und wiederverwertbar.
- Claim bleibt: bei einem Job an eine Position sagt eine Person sichtbar „ich will den“ (Absicht). Davon getrennt das Protokoll, wer welche Information wann beigetragen hat (Beitrag). Offen: wie dieses Protokoll in einem Solr-Job sauber abgebildet wird.

## Chat

**User:**

so was ich nun anpacken würde wäre im vorlagemodell das projekt modul anzusehen so wie die todojobs! also das sind zwei umfangreiche baustellen die aber sehr eng mit einander verknüpft sind! also mach eine genaue aufstellen was sie im vorlageprojekt an funktionalität bieten. ich bin nämlich am überlegen ob wir für allgemeine projekte einen eigenen solr kern bauen sollten oder eine dauerhafte datenbank oder doch nur den pdf export nach abschluss. selbiges gilt für die todo jobs! die struktur ist im vorlageprojekt ein anfang aber hat noch extrem viele lücken und ist bei weitem noch nciht so agil und felxibel wie ich sie gerne hätte. um das aber zu besprechen zu können mach erst mal eine sehr genaue bestandsanalyse, und damit meine ich den code ansehen und nicht irgendwelche veralteten memories

**Claude:**

Bestandsaufnahme ist fertig. Alles unten stammt aus dem Code von intern-instance (Backend `de.pahling.home.projectmanager` und `de.pahling.home.todojob`, Frontend `src/projectmanager` und `src/todojob`), nicht aus Memories. In app1 gibt es von beidem noch nichts.

## 1. Projekt-Modul

**Datenmodell (Postgres, 5 Tabellen)**
- `de.pahling.home.projectmanager.model.Project` (Tabelle `project`): id, name, beschreibung (i18n-JSON), vorlageId, entschlussNoetig, status (`de.pahling.home.projectmanager.model.ProjectStatusEnum`: OFFEN, RUHEND, ABGESCHLOSSEN, ABGEBROCHEN), version (optimistisches Sperren), gestartetAm/gestartetVonGuid, abasSternzeit (Spiegel des Bezugs `sternzeit`), Nachweisfelder. Vier JSON-Text-Spalten: `zustand`, `bezuege`, `plan`, `verlauf`, dazu `entschluesse` (Liste, nur angehängt). Die Einzelspalten `schluss*` sind Altbestand und werden nur noch gelesen.
- `de.pahling.home.projectmanager.model.ProjectVorlage` (`project_vorlage`): key (unique), name/beschreibung i18n, `bezugsfelder` `[{feld, beschriftung, pflicht}]`, `plan` in Blockform mit `TODO_DEFINITION`-Elementen, entschlussNoetig, version. Das ist der dynamische Projekttyp. Seed legt nur FREI an, MACHBARKEIT kommt als Datei `test_data/vorlage_MACHBARKEIT.json` (2 Meilensteine, 26 Todo-Definitionen, alle an eine ARC).
- `de.pahling.home.projectmanager.model.A4PageVorlage` (`a4_page_vorlage`) und `de.pahling.home.projectmanager.model.A4Feld` (`a4_feld`): Seitentypen mit Geometrie in mm, Header/Content/Footer als JSON, Badge-Farben, Feldkatalog mit Herkunft ZUSTAND/GERECHNET/QUELLE. Geseedet: MACHBARKEIT, ANHANG, PROJEKT.
- `de.pahling.home.projectmanager.model.A4Page` (`a4page`) plus Repository existiert noch, wird aber von keinem Service mehr benutzt. Toter Rest des alten Seiten-Reworks.

**Der Plan (Kern der Logik, `de.pahling.home.projectmanager.service.ProjectService`, 1447 Zeilen)**
- Plan = geordnete Liste von Zustandsblöcken `{autoplay, abgeschlossen, zustand:[Elemente]}`. Z 0 ist der Anfang ohne Zustandseinfluss und steht immer.
- Meilenstein = Klammer `{meilenstein, beschreibung, zustaende:[...]}` über Zustände, nie verschachtelt. Intern wird auf der flachen Liste gearbeitet, nur Lesen und Schreiben falten die Klammer.
- Elementtypen: TEXT (Richtext i18n), BILD, DATEI (dmsId), KONTAKT (sternzeit, Kunden-/Lieferantennummer), LINK (extern oder ABAS-Sternzeit), TODO (`jobId` der lebenden Zeile), RESPONSE (der abgeschlossene Job vollständig eingefroren), in der Vorlage TODO_DEFINITION.
- Der Plan wird immer als Ganzes geschrieben (PUT mit Version, 409 bei Konflikt). Geprüft: Form, kein Todo in Z 0, TODO nur mit bekannter jobId, RESPONSE darf nicht verschwinden, abgeschlossene Zustände und Z 0 nach Start sind byteweise gesperrt. Leere Zustände fallen weg, außer Z 0, abgeschlossene und der einzige Zustand eines Meilensteins.
- Platzhalter `{{ersteller}}`, `{{datum}}`, `{{bezug:feld}}` werden genau einmal beim Kopieren der Vorlage ins Projekt ersetzt.
- Bewertung je Zustand: VORBEI, AKTIV, WARTET, dazu `gestartet` (abgeleitet aus Jobstatus), Fortschritt offen/erledigt. Ausgänge VERFALLEN, PROJEKT_ABGESCHLOSSEN, PROJEKT_ABGEBROCHEN zählen nicht als erledigt.
- Verlauf: nur noch System-Durchschlag (PROJEKT_GESTARTET, ZUGESTELLT, ANGELEGT, GELOESCHT, RUECKGEMELDET, ERLEDIGT, VERFALLEN, ZUSTAND_ABGESCHLOSSEN, ENTSCHLUSS, IMPORTIERT). Kommentare im Verlauf sind zurückgebaut.

**Lebenszyklus**
- Anlegen aus Vorlage (Pflicht-Bezugsfelder geprüft, Plan kopiert, je TODO_DEFINITION eine Jobzeile in NOCH_NICHT_GESTARTET, in einer Transaktion in `de.pahling.home.todojob.service.TodoJobService.userCreateProjectAusVorlage`).
- Play-Knopf am Projekt, dann läuft der Nachrücker (Autoplay stellt zu, Hold wartet auf Play am Zustand). PO kann Zustände abschließen (offene Todos verfallen) und Todos ohne Rückmeldung erledigen.
- Abschließen oder Abbrechen: alle lebenden Jobs werden im aktuellen Stand als RESPONSE eingetragen und gelöscht, das Projekt ruht danach. Entschluss ist davon entkoppelt, revisionssicher angehängt.
- Löschen (nur ADMIN oder GESCHAEFTS_LEITUNG) löscht Projekt samt aller Jobzeilen.
- Export/Import: Projekt komplett als JSON `{projekt, jobs}` (Frontend baut die Datei), Import erzeugt immer ein neues Projekt mit neuen Job-ids. Vorlagen ebenso als `vorlage_<KEY>.json`.

**REST und Tor**
- `/api/rest/projektplanung`: lookup (Top 20 nach lastChangedAt, Namensfilter, id, abasSternzeiten), POST (anlegen oder `projektImport`), PATCH (Kopffelder, starten, status), DELETE, POST entschluesse, PUT /{id}/plan, POST /{id}/zustaende/lookup, PATCH /{id}/zustaende/{n}.
- `/vorlagen`, `/pagevorlagen`, `/felder` darunter. Lesen PROJEKT_PLANUNG oder ADMIN, Pflegen von Vorlagen nur ADMIN (Filterkette in `de.pahling.home.sys.security.SecurityConfig`).
- Es gibt keine Suche über Projekte, nur den Top-20-Loader mit Namensfilter und die Geschwisterabfrage über die Chance-Sternzeit.

**Frontend**
- `ProjectManagerSeite.jsx`: Loader, Typ-Popup (Vorlage, Bezüge, Name), Import-Popup, Papierkorb.
- `ProjectSeite.jsx` (2540 Zeilen): die Mappe mit Registern Übersicht (Balkenplan in Arbeitstagen, Zeitleiste, Verlauf, Entschluss/Abschluss/Abbruch), Projekt (Kopfdaten mit Bezugs-Chips, Projekt-Box mit Fahrplan, Drag-and-Drop von Elementen und ganzen Zuständen, Zwischenzonen, Meilenstein-Boxen, Einfügeleiste je Zustand), Dokumente (nur Dateiart PROJEKT_PLAN zu Artikel/Chance), je Meilenstein ein Register. Dieselbe Seite im Vorlagen-Modus unter `/projectmanager/vorlage/{id}`.
- Popups: Todo anlegen/bearbeiten (inkl. DMS_DATEI mit Dateiart und Hooks), Einfügen-Ziel (parallel, dahinter, nach Meilenstein), Inhalt (Text, Bild, Datei, Kontakt, Link), Meilenstein, Bezüge, Kontaktsuche, Chance-Positionen, Rückmelden durch den PO.
- `ProjektVorschauSeite.jsx` plus `VorschauFahrplan.jsx`, `VorschauDurchfuehrung.jsx`: A4-Vorschau im eigenen Fenster, Seitenumbruch per Messbaum. `projektPdf.js`: jsPDF-Vektor-PDF im Browser, fest auf die Pagevorlage MACHBARKEIT verdrahtet. Kein Backend-PDF, keine Ablage ins DMS.
- `PageVorlagenSeite.jsx`: Editor der Seitentypen mit A4-Komponenten.

## 2. TodoJobs

**Datenmodell (eine Tabelle)**
- `de.pahling.home.todojob.model.TodoJob` (`todo_job`): id, pid (UUID, praktisch ungenutzt), titel, `aufgabe` und `antwort` (JSON-Listen von Bausteinen `{id, job_key, job_value, von, am}`), `job_from`/`job_to` (`de.pahling.home.todojob.model.JobAdresse` `{arcKeys, userGuids}`), projectId (lose), `projektKopf` (Snapshot bei Zustellung), bestaetigungNoetig, gelesen (ein Flag je Job, nicht je User), status, ergebnisArt, ergebnisVorgaben (Spalte vorhanden, wird nicht mehr geschrieben), ausfuehrungsDauerTage (Arbeitstage), fertigstellungsDatum, fristablaufVerhalten, erstelltAm, gestartetAm, referenzen (JSON Key → Liste), bearbeitetVonGuid/bearbeitetAm (Claim und Rückmelder), abgeschlossenAm und ausgang (nur Archiv der projektlosen).
- Enums: `TodoJobStatusEnum` (NOCH_NICHT_GESTARTET, GESTARTET, WARTEND, BEARBEITET, FERTIG, ABGESCHLOSSEN), `ErgebnisArtEnum` (LESEN, BOOL, TEXT, ZAHL, DATUM, ABAS_STERNZEIT, DMS_DATEI, ARTIKELNUMMER), `FristablaufVerhaltenEnum` (VERFAELLT, ESKALIERT), `JobKeyEnum` (text, dms, artikel, bool, zahl, datum, sternzeit, dateiart, hook, clarification, uebernahme, bestaetigung, unvollstaendig, bearbeiterwechsel).
- Zwei Welten in einer Tabelle: Projekt-Jobs werden beim Abschluss zur RESPONSE im Plan und die Zeile gelöscht. Projektlose Jobs bleiben mit abgeschlossenAm/ausgang als Archiv stehen.

**Bausteinlogik (`de.pahling.home.todojob.service.JobBausteinService`)**
- Antwort wird nie als Ganzes geschrieben, nur Baustein für Baustein geprüft, angereichert und gestempelt. dms wird gegen Solr-DMS aufgelöst (Name, Dateiart), artikel gegen den Artikel-Spiegel, dateiart gegen FileSubClass, hook gegen die Hook-Felder.
- bool und datum sind einwertig und ersetzen den Vorgänger. clarification nur über den eigenen Weg. bestaetigung, unvollstaendig, bearbeiterwechsel schreibt nur der Server (Log im Job-Body).
- "Ganz fertig" prüft: mindestens ein Baustein der verlangten Art, keine offene Rückfrage. ARTIKELNUMMER und DMS_DATEI sind prüfbar: FERTIG bleibt stehen, bis jeder Baustein im Spiegel/DMS steht.

**Lebenszyklus und Rechte (`TodoJobService`, 1476 Zeilen)**
- Erteilen: Tor TODO_JOB oder ADMIN. Ohne Projekt sofort GESTARTET, mit Projekt ins Plan-Element und erst durch den Nachrücker zugestellt. Felder ändern nur vor der Zustellung.
- Zustellung setzt gestartetAm, berechnet die Frist aus Arbeitstagen, schreibt projektKopf und setzt `from` auf den Projektstarter.
- Status folgt dem Tun: eintragen/claimen = BEARBEITET, Rückfrage an Ersteller = WARTEND, Antwort = BEARBEITET, fertig = FERTIG oder direkt ABGESCHLOSSEN.
- Claim: erster Schreiber in bearbeitetVonGuid, 403 mit Klarnamen für andere. Lösen durch Inhaber oder Ersteller-Seite. Jeder Wechsel wird geloggt.
- Ersteller-Seite: bestätigen, als unvollständig zurückgeben (Job zurück auf BEARBEITET), ohne Rückmeldung erledigen, löschen. Bei Projekt-Jobs gilt zusätzlich die Rolle PROJEKT_PLANUNG als Ersteller-Seite und als Empfänger-Bypass.
- Vertretung über Slave-ARCs (nicht transitiv) auf Empfänger- und Erstellerseite.
- Verfall und Nachprüfung laufen opportunistisch bei jedem Lesen des Eingangs oder Nachrücken. Es gibt keinen Scheduler für Fristen.
- Alle Eingangsfilter laufen in Java über alle lebenden Zeilen (bewusst für unter 10.000 Zeilen). Archivabfragen laden alle projektlosen Abgeschlossenen und filtern dann in Java.

**REST**
- `/api/rest/todojobs`: lookup (Eingang, id öffnet und setzt gelesen, projectId, erteilte, erteilteVertretung, vertretung, kuerzlichAbgeschlossen, archiv je Jahr), POST, PATCH /{id} (fertig, bestaetigen, unvollstaendig, abschliessen, Felder), DELETE /{id}, POST/DELETE /bausteine, POST/DELETE /claims, POST /clarification.

**Frontend**
- `TodoSeite.jsx`: vier Listen (Eingang, Vertretung mit per UsersSetting gemerkten Filter-Badges, Von mir erstellt, Von meinen Positionen erstellt) plus Kürzlich abgeschlossen (4 Wochen, projektlos). Route `/todo/{jobId}` öffnet direkt, `?hochgeladen=` hängt frisch hochgeladene Dateien an.
- `TodoPopup.jsx`: ein Job in einer von vier Rollen. Worum es geht, Aufgabe, Antwort bisher, Claim, Rückfrage/Antwort, Zwischenergebnis (`Rueckmeldung.jsx` je Ergebnisart, DMS_DATEI mit Angeboten aus Hooks, Upload-Rückweg, DMS-Suche), Zurückmelden/Bestätigen, Danke-Nachricht an den Melder.
- `TodoErteilenSeite.jsx`: freie Jobs ohne Projekt, einsprachig, mit Live-Vorschau. `TodoArchivSeite.jsx`: je Jahr, Sicht zurückgemeldet/entsendet, Frontend-Filter über JSON-Text. `TodoAufpoppen.jsx`: Postfach-Takt 300 s, Ereignis-Merker im localStorage, Nerv-Popup nach N Stunden. `MeineFreigabenSeite.jsx` hängt als Rest des Message-Moduls im TODO-SubHeader.
- `JobInhalt.jsx` ist der eine Renderer der Bausteine, `rueckmeldeHelfer.js` die abgeleitete Logik (offene Rückfrage, poppt bei wem, fertigmeldbar).
- Integrationen: `dms/BildMaengelPopup.jsx` schickt einen LESEN-Job an ARC_ARBEITS_VORBEREITUNG. `n8n/workflows/todo-artikelnummer.json` ist ein Entwurf ohne Gegenstück im Backend.

## 3. Lücken und Fakten, die für die Kern/DB/PDF-Frage zählen

- **Wahrheit liegt zu 100 % in Postgres-JSON-Text.** Plan, Verlauf, Bezüge, Bausteine sind unstrukturierte Spalten. Abfragbar sind nur name, status, vorlageId, abasSternzeit, lastChangedAt. Keine Suche nach Artikelnummer, Kunde, Empfänger, Rückmeldeinhalt oder Zeitraum über Projekte.
- **Das Archiv der Projekt-Jobs ist der Plan selbst.** RESPONSE friert Aufgabe, Antwort, Adressen, Stempel, Referenzen ein. Die Jobzeile ist danach weg. Projektlose Jobs liegen dagegen in `todo_job` mit abgeschlossenAm. Zwei Archive, zwei Zugriffswege, kein gemeinsamer Blick.
- **PDF ist Browser-seitig (jsPDF), nicht reproduzierbar ohne Client, fest an die Pagevorlage MACHBARKEIT gebunden** und wird nirgends abgelegt. Löscht jemand das Projekt, ist das PDF die einzige Quelle, aber nur wenn es jemand vorher gespeichert hat.
- **Vorlagen sind nicht im Stammdaten-Export.** `de.pahling.home.sys.stammdaten.service.StammdatenExportService` exportiert A4-Felder und Pagevorlagen, aber keine `project_vorlage`. Vorlagen kommen nur per Hand-Datei.
- **Toter oder halber Bestand:** `A4Page` samt Repository ungenutzt; `ergebnisVorgaben` wird nicht mehr geschrieben; `Project.zustand` wird gepatcht, aber von keiner Oberfläche gefüllt; `schluss*`-Spalten nur Rückwärtskompatibilität; `JobKeyEnum.UEBERNAHME` nur noch Fallback für alte Zeilen; `pid` am Job wird vergeben, aber nirgends gebraucht.
- **Struktur-Grenzen des Plans:** keine Abhängigkeiten außer "nächster Zustand", keine Bedingungen, kein Zeitplan mit Soll-Terminen (nur Dauer in Arbeitstagen ab Zustellung), keine Wiederholung, keine Teilprojekte, Meilensteine nie verschachtelt, Z 0 nach Start eingefroren.
- **Job-Grenzen:** gelesen ist ein Flag je Job statt je User; genau ein Rückmelder je Job; keine Feiertage; keine Eskalationsstufen außer Rot; keine Benachrichtigung außer Popup im Browser; Ersteller-Vertretung bei Fristablauf laut Doc offen; Erteilen-Seite einsprachig.
- **Dokumentierte offene Punkte** (docs/projektplan.md Abschnitt 7 und todo.txt): "Chance laden" je Position, Chancen als Filter im Loader, Empfänger der Machbarkeits-Todos verteilen, Rendern als Schnappschuss, kein neues Projekt solange eines zum Artikel offen ist, Positionen einer Chance an den Positionsmaster binden.
- **Skalierung:** jeder Eingang lädt alle lebenden Jobs und filtert in Java; der Projekt-Loader zeigt die 20 zuletzt geänderten. Beides ist für wenige hundert Zeilen gebaut, nicht für ein dauerhaftes Archiv über Jahre.

**User:**

ja ich denke wir sollen dann erst mal die  todo jobs nochmal aufarbeiten! denn die sollen ja auch ohne einen projekt oder "anderen" bezug existieren können. und das sollte davor stehen

**Claude:**

Verstanden, und ich halte das für die richtige Reihenfolge. Der Job wird die Grundentität, das Projekt ist nur ein Verbraucher davon.

Konkret heißt das für mich:
- Ein Job hängt an nichts fest. Projekt, Chance, Artikel, Dokument oder gar nichts sind nur Bezüge am Job, keine Spalte `projectId` mehr als Sonderfall.
- Der Job hat ein eigenes Archiv, unabhängig vom Bezug. In der Vorlage wird ein Projekt-Job beim Abschluss gelöscht und lebt nur noch als RESPONSE im Plan, ein freier Job bleibt stehen. Das ist genau die Zweiteilung, die weg muss.
- Was ein Bezug beim Abschluss mit dem Ergebnis macht (Projekt schreibt es in seinen Plan), ist Sache des Bezugs, nicht des Jobs.

Meinst du es so? Wenn ja, wäre meine erste Frage vor allem anderen: soll der Bezug am Job frei sein wie die Bezüge am Projekt der Vorlage, also eine Liste `{feld, wert}` ohne feste Feldliste, oder typisiert mit einer Art und einer id?

**User:**

ich meine dass wir die umsetzung von todojobs mit rückfragen rück antwort meldungen wer ihrn übernimmt und sämtliche mögliche zustände die ein job haben kann sollten wir nochmal zusammenfassen und dann überlegen wir, wie wir den am sinnvollsten umsetzen, weil die bisherige struktur ist murks!

**Claude:**

Hier die Zusammenfassung, wie Jobs in der Vorlage tatsächlich laufen. Alles aus `de.pahling.home.todojob.service.TodoJobService`, `de.pahling.home.todojob.service.JobBausteinService` und `frontend/src/todojob/rueckmeldeHelfer.js`.

## Die Zustände

Die Spalte `status` kennt sechs Werte. Was ein Job wirklich ist, steht aber in fünf Stellen gleichzeitig: `status`, der Antwortliste (letzter Baustein), `bearbeitetVonGuid`, `gelesen` und `abgeschlossenAm`/`ausgang`.

| Status | Wie hinein | Wie hinaus |
|---|---|---|
| NOCH_NICHT_GESTARTET | Anlegen mit Projekt | Zustellung durch Nachrücker oder Play am Zustand. Löschen, Verfall durch Zustand abschließen, Projektende |
| GESTARTET | Zustellung, oder Erteilen ohne Projekt | Erster Baustein oder Claim, Rückfrage, Fertig, Verfall, PO erledigt |
| WARTEND | Nur durch Rückfrage des Empfängers an den Ersteller | Antwort des Erstellers. Weitere Bausteine lassen WARTEND stehen |
| BEARBEITET | Baustein, Claim, Erstellerantwort, Unvollständig | Fertig, Rückfrage, Verfall, PO erledigt |
| FERTIG | Fertig bei Bestätigungspflicht oder nicht bestandener Systemprüfung | Bestätigen, Unvollständig, Nachprüfung, Projektende. Empfänger kann nichts mehr tun, Verfall greift nicht |
| ABGESCHLOSSEN | Immer über eine der Abschlusswege | Terminal. Mit Projekt existiert die Zeile nicht mehr |

Ausgänge am Abschluss: ERLEDIGT, VERFALLEN, GELESEN, PROJEKT_ABGESCHLOSSEN, PROJEKT_ABGEBROCHEN. Die RESPONSE im Plan hält zusätzlich den `stand` fest, also den Status in dem Moment.

Abgeleitete Zustände, die nirgends gespeichert sind:
- Offene Rückfrage: der letzte Baustein der Antwort ist eine clarification. Ihr `an` sagt, bei wem der Ball liegt.
- Poppt bei wem: ungelesen und FERTIG beim Ersteller, ungelesen und letzte clarification bei deren `an`, sonst beim Empfänger.
- Fertigmeldbar: kein letzter Baustein clarification an ersteller, und mindestens ein Baustein der verlangten Art.
- Fristfarbe aus Arbeitstagen bis zur Frist. Der Eingang färbt gelb ab 2 Tagen, Popup und Mappe ab 5 Tagen. Zwei Regeln für dasselbe.
- Gestartet ist ein Zustand im Plan, sobald eines seiner Todos nicht mehr NOCH_NICHT_GESTARTET ist.

## Handlungen und Übergänge

**Empfänger-Seite** (`to` trifft eigene ARC oder guid, Slave-ARC, oder Rolle PROJEKT_PLANUNG als Bypass):
- Öffnen setzt `gelesen` für alle, der Erste nimmt es den anderen weg.
- Claim schreibt die eigene guid in `bearbeitetVonGuid`, besetzt gibt 403 mit Klarname. Der erste Baustein claimt implizit mit.
- Baustein anhängen oder entfernen: nur bei GESTARTET, WARTEND, BEARBEITET, nicht bei LESEN, nur frei oder eigener Claim. Setzt BEARBEITET, außer es ist WARTEND.
- Rückfrage: ein clarification-Baustein mit `an: ersteller`, Status WARTEND, `gelesen` false.
- Fertig: Prüfung der Antwort, dann FERTIG oder sofort ABGESCHLOSSEN. LESEN hat keinen Fertig-Weg, nur Bestätigen gleich GELESEN.

**Ersteller-Seite** (`from` trifft eigene ARC oder guid, Slave-ARC):
- Felder ändern nur vor der Zustellung.
- Antwort auf Rückfrage: clarification mit `an: empfaenger`, Status BEARBEITET, `gelesen` false.
- Bestätigen: FERTIG nach ABGESCHLOSSEN, Log-Baustein bestaetigung.
- Unvollständig: FERTIG zurück nach BEARBEITET mit Text, Log-Baustein unvollstaendig, Claim bleibt.
- Erledigen ohne Rückmeldung: in jedem Status außer FERTIG und ABGESCHLOSSEN, auch vor der Zustellung.
- Löschen, außer der Zustand im Plan ist gesperrt. Claim lösen.

**System**:
- Zustellung setzt GESTARTET, `gelesen` false, gestartetAm, Frist aus Arbeitstagen, `projektKopf` und überschreibt `from` mit dem Projektstarter.
- Verfall: VERFAELLT, Frist vor heute, Status nicht FERTIG, nur zugestellte Jobs. Läuft nur, wenn jemand den Eingang öffnet oder der Nachrücker läuft. Kein Scheduler.
- Nachprüfung: FERTIG ohne Bestätigungspflicht wird ABGESCHLOSSEN, sobald Artikel oder Datei im Spiegel sind.
- Zustand abschließen und Projektende schließen alle lebenden Jobs mit dem jeweiligen Ausgang.

## Rückfrage, Claim, Meldung und wo es hakt

- **Die Antwortliste ist drei Dinge auf einmal:** Nutzdaten (text, dms, artikel, bool), Gespräch (clarification) und Protokoll (bestaetigung, unvollstaendig, bearbeiterwechsel). Jede Auswertung muss den letzten Baustein lesen, um zu wissen, bei wem der Ball liegt.
- **Rückfrage ist ein Nachrichtenstrang ohne eigene Identität.** Frage und Antwort sind nur durch Reihenfolge verbunden. Hängt der Empfänger nach seiner Frage noch einen Text an, gilt die Frage als nicht mehr offen, obwohl der Status WARTEND bleibt. Zwei Fragen nacheinander gehen nicht sauber.
- **Status und Antwort widersprechen sich.** WARTEND wird nur bei Rückfrage gesetzt, aber nicht zurückgenommen, wenn der Empfänger weiterarbeitet. FERTIG blockiert Verfall. ABGESCHLOSSEN steht nur bei projektlosen Zeilen in der Spalte.
- **`bearbeitetVonGuid` hat zwei Bedeutungen:** wer den Job gerade hält und wer ihn zuletzt zurückgemeldet hat. Unvollständig lässt den Claim stehen, PO-Erledigen überschreibt ihn.
- **`gelesen` ist ein Flag je Job, nicht je Person.** Bei einem Job an eine Position mit fünf Leuten sieht nur der Erste das Popup. Es wird bei Zustellung, jeder Clarification, FERTIG und Unvollständig zurückgesetzt, also für beide Seiten dieselbe Spalte.
- **Meldung heißt nur Browser-Popup.** Takt 300 s, Merker im localStorage je Browser, Nerv-Popup alle N Stunden. Keine Mail, kein Teams, keine Meldung an Vertreter.
- **Rollenprüfung ist uneinheitlich.** PROJEKT_PLANUNG darf als Empfänger alles und beim Claim als Ersteller-Seite gelten, aber Bestätigen, Unvollständig und Löschen verlangen echtes `from`.
- **Zwei Archive.** Projekt-Jobs verschwinden in die RESPONSE des Plans, freie Jobs bleiben in der Tabelle. Das Archiv-Register kennt nur die freien.
- **Lebende und archivierte Jobs in einer Tabelle,** gefiltert in Java über alle Zeilen.

Das ist der Ist-Stand. Sollen wir als nächsten Schritt die Zielzustände und Ereignisse eines Jobs festlegen, bevor wir über Tabellen reden?

**User:**

ich werde gerade aber sehr müde und wir sollten das wissen über todo jobs festhalten, dass wir da morgen drüber weiterplanen können

**Claude:**

(Hat diese Datei, den Schritt in `rules/ziele.md`, den Verweis in `vorlage-erkenntnisse.md` und die Memory angelegt.)

**User:**

genau weil mit einem solr kern denke ich könnten wir besser "alte jobs" vorschlagen. also prüfe ob datenblätter vorhanden sind oder sonstige dinge zum abarbeiten, dann kann man die einzelnen bausteile vervollständigen

**Claude:**

Notiert. Ich lese das so: ein Solr-Kern für Jobs wäre nicht nur Archiv, sondern der Suchraum, aus dem das System selbst Vorschläge macht. Zwei Lesarten, sag mir morgen, welche du meinst oder ob beide:

1. Alte Jobs wiederfinden: beim Anlegen eines Jobs oder in der Artikelmappe die früheren Jobs zum selben Bezug (Artikel, Kunde, Chance) samt ihren Antworten vorschlagen, damit man Bausteine übernimmt, statt sie neu einzutragen.
2. Lücken finden: das System prüft je Artikel, ob Datenblatt, Foto, Zeichnung und so weiter vorhanden sind, und schlägt die Jobs vor, die die fehlenden Bausteine beschaffen. Erledigte Jobs und vorhandene Dateien zählen als „schon da“.

**User:**

ja und ein job soll auch immer einen kontext bereit stellen für den der die aufgabe zugeteilt bekommt, also person oder position! aber es geht ja darum, dass man so viel wie möglich aus einem fundus nur anklicken muss anstatt es dann neu schreiben zu müssen! und wenn wir an einen job auch über hooks kontext verleihen können ist das natürlich auch nice! oder die antwort einen allgemein gültigen wert hat, ist sie über hooks evtl auffindbar und wieder verwertbar

**Claude:**

Notiert. So lese ich es, als vier Anforderungen an den Job:

1. Jeder Job trägt seinen Kontext für den, der ihn bekommt (Person oder Position): worum es geht, woran es hängt, was schon da ist. In der Vorlage war das der `projektKopf`-Schnappschuss, nur bei Projekt-Jobs.
2. Beim Erteilen und beim Antworten klickt man aus einem Fundus zusammen (Textbausteine, frühere Antworten, Dateien, Artikel) statt neu zu schreiben.
3. Hooks am Job wie am DMS-Dokument: ein Job hängt über artikelNummer, kundenNummer und so weiter an denselben Nummern wie alles andere und ist über sie auffindbar.
4. Eine Antwort mit allgemein gültigem Wert (zum Beispiel „Lieferzeit 6 Wochen“ zu Artikel X) wird über die Hooks des Jobs wiederfindbar und an anderer Stelle wiederverwertbar, nicht nur im Job selbst.

Das passt zu deinem Solr-Kern-Gedanken: der Job als Dokument im Kern mit denselben Hooks wie das DMS. Morgen klären wir die Zustände, dann sehen wir, was davon Feld, Baustein oder Hook wird.

**User:**

ja zusätzlich soll ein job auch weiterhin geclaimt werden können, wenn er an eine position geht! so dass gleich klar ist wer den job bearbeiten WILL! also ABSICHT! und wer ihn tatsächlich BEARBEITET, also wer wleche information dazu beiträgt und wann. da bin ich mir noch nicht so ganz sicher wie wir diese art logging in einem solr todojob vernünftig und ohne pfusch abbilden

**Claude:**

Notiert, als zwei getrennte Dinge:

1. Absicht: der Claim. Geht ein Job an eine Position, sagt genau eine Person „ich will den“, und das ist für alle sofort sichtbar. Lösbar durch den Inhaber oder die Ersteller-Seite.
2. Beitrag: wer welche Information wann beigetragen hat. Das ist ein Protokoll je Eintrag, nicht ein Feld am Job.

In der Vorlage gab es beides schon getrennt, nur verwischt: jeder Baustein trägt `von` und `am` (das ist der Beitrag), und `bearbeitetVonGuid` ist der Claim, der aber zugleich als „Rückmelder“ überschrieben wird. Dazu lagen Protokolleinträge (bestaetigung, unvollstaendig, bearbeiterwechsel) in derselben Liste wie die Nutzdaten.

Offen für morgen, als eigener Punkt: wie das Protokoll der Beiträge sauber abgebildet wird, wenn der Job ein Solr-Dokument ist. Solr ist gut im Finden, aber kein Ort für ein wachsendes Protokoll mit vielen kleinen Schreibvorgängen. Das klären wir, sobald die Zustände stehen.
