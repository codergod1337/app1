# Architektur

- Zwischen allen Klassen gilt lose Kopplung.

## Paketstruktur (Backend)

- Package by Feature: unter `codergod1337.app1` ein Paket `system` (Security, User, Rechte) und daneben je ein Paket pro Feature.
- Features lassen sich als eigenes Paket hinzufügen, ohne andere Pakete anzupassen.
- Jedes Paket ist fast immer aufgeteilt in `controller/`, `service/`, `repository/` und `model/`.
- In `model/` kommen Entities (z. B. `Users`), Enums und, falls nötig, DTOs.
- DTOs möglichst vermeiden: Klassen lieber so aufteilen, dass direkt die jeweilige Klasse verwendet werden kann (so wie `Users` und `UsersCredentials` getrennt sind).
- Pakete können Unterpakete haben. Die sind genauso aufgeteilt (z. B. `feature_a/teil_x/controller/`).

## Endpunkte

- Schema: `/api/rest/v1/<ar.key>/<endpunkt>`. Aus dem Pfad muss sofort ablesbar sein, welche AccessRole ein Endpunkt verlangt: `public` heißt ohne Anmeldung, `admin` heißt AR `ADMIN`, ohne Segment heißt angemeldet (die Rechte prüfen dann die AR).
- Die `area` steht nicht im Pfad, sondern als Claim im Token. Nur der Login hat einen Pfad pro area: `/api/rest/v1/public/<area>/login/…`.
- Keine Parameter in der URL. Daten kommen immer im Request-Body.
- Einzige Ausnahme ist GET: Dort dürfen in der URL nur eine guid oder eine Long-ID stehen.

## Controller und Services

- Controller prüfen die Form der Eingabe, prüfen bei Bedarf den Zugriff und geben die Anfrage an den zuständigen Service weiter.
- Keine Hilfsmethoden im Controller.
- Ein Service nutzt nur sein eigenes Repository. Daten einer anderen Klasse holt er über deren Service.
- Ausnahme nur bei einem Zirkelschluss (Service A braucht B, B braucht A): Dann darf ein Service das Repository der anderen Klasse direkt nutzen. Das wird an beiden Stellen kommentiert: am drohenden Zirkelschluss und dort, wo das fremde Repository direkt genutzt wird.

## Zugriffsfälle

Jede Aktion gehört zu genau einem von drei Fällen:

1. `system`: Der Server handelt selbst, ohne Token. Er läuft dann als System-User: eigene Zeile in `users` mit fester guid, ohne `UsersCredentials` (kein Login möglich). Beispiele: alle User löschen, die sich ein Jahr lang nicht angemeldet haben; das System lädt eine Datei hoch und ist dann ihr Autor.
2. `admin`: Ein angemeldeter User mit privilegierter AR. Je nach Rolle(n) darf er mehr, auch an fremden Objekten.
3. `user`: Ein angemeldeter User ohne privilegierte AR. Er darf nur auf seine eigenen Objekte schreiben, das muss geprüft werden. Beispiel: den eigenen username ändern.

Den ausführenden User (current user) liefert ein Provider aus dem Security-Context. Service-Methoden bekommen ihn nie als Parameter. Es gibt immer einen current user: einen angemeldeten User oder den System-User. Ist keiner im Kontext, wirft der Provider einen Fehler.

Ein Konto löschen oder deaktivieren ist immer `admin`, nie `user`: Ein User kann sich nicht selbst löschen, auch nicht weich. Was er selbst darf: Passwort ändern und seine Sitzungen beenden.

## Frontend

- React mit TypeScript, gebaut mit Vite, im Ordner `frontend-ts/`.

### Aufbau einer Seite

- Immer da sind nur `header`, `main` und `footer`. Der `footer` ist unabhängig vom Modul.
- In `main` rendert das Modul selbst: optional seinen `subheader` (mit Bootstrap `sticky-top` bleibt er beim Scrollen stehen), dann `main-content` mit dem eigentlichen Inhalt.
- In `main-content` steht genau eine von drei Darstellungen: ContentBox, Matrix oder A4-Ansicht.
- ContentBox: `contentbox` mit `contentbox-header`, `contentbox-main` und `contentbox-footer`. Der Footer ist fürs Design immer da, nur sein Inhalt (Text, Knöpfe) ist optional. In ContentBoxen stehen auch Tabellen.
- Tabelle: einmal zentral definiert (`components/Table.tsx`), mit Kopfzeile, Zeile A und Zeile B (abwechselnd, A und B dürfen gleich aussehen). Jede Tabelle im Frontend ist diese Komponente, nie ein eigenes `<table>`, auch nicht für Zeilen mit Eingabefeldern: Zellen dürfen Eingaben enthalten. Fehlt der Tabelle etwas, wird sie erweitert, nicht umgangen.
- Die Seiten müssen auch auf dem Handy passen. In Tabellen können Spalten als unwichtig markiert werden, die fallen in der mobilen Auflösung (unter 768 px) weg. Dort zeigt ein Tipp auf die Zeile alle Spalten im Popup.
- Matrix: steht direkt im kompletten `main`, ohne ContentBox. Matrix und ContentBox schließen sich aus. Die Details legen wir fest, wenn wir die erste Matrix anlegen.

### Drag and Drop

- Es gibt nur eine Umsetzung, alles in `components/`: `SortableArea` (der Bereich) und `useSortableItem` (ein verschiebbares Element samt Griff).
- Tabellenzeilen: `Table` mit `onReorder`, der Griff sitzt in der ersten sichtbaren Spalte.
- Alle anderen Listen (Kacheln, Badges, Karten …): `SortableList` mit `layout` (`vertical`, `horizontal` oder `grid`). `renderItem` bekommt den Griff und setzt ihn selbst.
- Verschoben wird nur am Griff, mit Maus, Finger oder Tastatur.
- Positionen speichern: `positionsByKey` (`components/positions.ts`) zählt neu durch (1, 2, 3 …). Alle Positionen gehen in genau einem POST an den Positions-Endpunkt, nie einzeln.

### Ordner

- `modul/<modul>/`: alles, was nur ein Modul braucht, z. B. `modul/users/` für alles zu Usern.
- `modul/login/`: Anmeldung, Aktivierungslink und die Sitzung. Alle Module lesen die Sitzung über `useSession()` (wer ist da, welche AR), die Stammdaten über `useMasterData()` (`components/`).
- `components/`: alles, was mehr als ein Modul nutzt.
- `layout/`: Header, Footer und das Grundgerüst.
- Es gibt nur einen `Subheader` (in `components/`). Die Module übergeben nur Parameter: Statuszelle, Tabs, sticky.
- `branding/`: alles Firmenspezifische.
- `api/`: der zentrale Client für alle Backend-Requests.

### Backend-Requests

- Keine Komponente ruft selbst `fetch` auf. Jeder Request läuft über den einen zentralen Client in `api/`.
- Die Funktionen zu den Endpunkten (eine pro Endpunkt, passend zum Controller im Backend) stehen in derselben Datei wie der Typ, z. B. `modul/users/Users.ts`.
- Der Client misst jeden Request einheitlich: Zustand, Status, Dauer, gesendete und empfangene Bytes, auf Wunsch auch die Inhalte. Die Werte gibt es direkt beim Aufruf und zentral für alle (z. B. für Ladespinner).

### Navigation

- Kein `history.back`.
- Verlässt man einen Tab (z. B. zum Formular „neuer User“), bekommt die nächste Seite den origin als String mit, also den Pfad des Tabs (z. B. `/admin/users`). Tiefere Seiten reichen denselben origin weiter, bis man wieder auf einem Tab-Link landet.
- Abbrechen und Speichern springen zum origin. Fehlt er, gilt ein festgelegter Fallback.
- Die Startseite mit den Kacheln lädt auch ohne Anmeldung, ebenso Module, die keine Anmeldung brauchen (ihre Endpunkte liegen unter `/api/rest/v1/public/`). Nur Module, die eine Anmeldung brauchen, liegen hinter `RequireLogin`. Eine Kachel erscheint nur, wenn der Besucher das Modul nutzen darf.
- Die Stammdaten (alle AR und ARC, später FSC und MSC) lädt das Frontend sofort beim Start über `/api/rest/v1/public/masterdata`, unabhängig von der Anmeldung. Sie liegen zentral für alle Module bereit und werden neu geladen, wenn ein Admin sie ändert.

### Knöpfe

- Jeder Knopf bekommt genau eine Art als Klasse, damit man sofort sieht, was er tut, und er überall gleich aussieht:
  - `button-save`: speichern, anlegen, hochladen, bestätigen
  - `button-cancel`: abbrechen, zurück
  - `button-other`: alles andere
- Dazu kommt der `NewEntityButton` zum Anlegen einer neuen Entity.
- In Tabellen steht ganz rechts die Aktionsspalte: `EditButton` (Bleistift, immer in Gelb- und Brauntönen) und `DeleteButton` (Mülleimer, immer in Rottönen). Löschen immer erst nach einer Sicherheitsabfrage im Popup. Bei Usern steht davor der `PasswordButton` (Schlüssel), der das Popup zum Passwortsetzen öffnet.
- Das Aussehen aller Knöpfe gestaltet das Theme komplett selbst.

### Hoverlay

- Keine `title`-Attribute. Jeder Hinweistext erscheint im Hoverlay (`components/Hoverlay.tsx`), einem aufpoppenden Feld.
- Das Aussehen bestimmt das Theme: immer ein Verlauf, auf dem Text an jeder Stelle lesbar bleibt (nie z. B. Weiß nach Schwarz).
- Dasselbe Aussehen dient später als Hintergrund der A4-Seiten im Browser (nicht beim PDF).

### Toast

- Rückmeldungen, die man sonst verpassen würde, erscheinen als Toast unten rechts: `showToast({ kind, title, text })` aus `components/toastStore.ts`, aufrufbar von überall. `components/Toasts.tsx` steht einmal im Layout.
- Nicht für jeden Request, nur für ausgewählte Rückmeldungen. Arten: `success`, `error`, `info`.
- Kein Paket, selbst gebaut. Die Klassen heißen `app-toast-*`, weil `.toast` Bootstrap gehört.
- Das Aussehen bestimmt das Theme: immer ein Verlauf, auf dem Text an jeder Stelle lesbar bleibt, wie beim Hoverlay.

### Mehrsprachigkeit

- Mehrsprachige Felder werden als JSON gespeichert, das Sprachkürzel ist der Schlüssel: `{"de": "…", "en": "…"}`.
- Die aktivierten Sprachen stehen in `branding/languages.ts`, die erste ist der Standard: `de`, `en` (mit US-Flagge), `bg`.
- Pro ContentBox wird eine Sprache gewählt: Die Flaggen aller aktivierten Sprachen stehen oben rechts in `contentbox-main`, beim Hover erscheint das Land in dessen Sprache.
- Vorerst werden nur die deutschen Texte befüllt.

### Sandbox für Firmen

- Das Frontend soll sich mit wenig Aufwand auf eine Firma zuschneiden lassen.
- Alles Firmenspezifische (Firmenname, Logo, Kontaktdaten, Standard-Symbole) steht an einer Stelle in `branding/`. Keine Komponente enthält es fest im Code.
- Texte nutzen Platzhalter wie `{firma}`, die beim Anzeigen ersetzt werden.
- Symbole werden über einen Namen angefordert (z. B. `user`, `delete`), nie über eine bestimmte Datei. Die Zuordnung Name → Symbol steht an einer Stelle.

### CSS

- `basic.css` wird immer geladen: Maße (z. B. Höhe des Headers) und alles, was immer gleich bleibt.
- Pro Modul optional ein eigenes CSS, aber nur, wenn ich es ausdrücklich sage.
- Jedes Theme ist genau eine Datei `public/themes/<name>.css`, darin steht alles, was ein Theme entscheiden darf. Davon gibt es mehrere, nah an der Vorlage. Das gewählte Theme wird als UsersSetting gespeichert.
- Bootstrap (nur CSS) für Werkzeuge wie Grid, Tabellen und Transparenz.
- Priorität: Bootstrap < `basic.css` < Theme.
- Nichts wird zufällig überschrieben, jede Überschreibung ist bewusst definiert:
  - Eigene Klassennamen dürfen nicht mit Bootstrap-Klassen zusammenstoßen.
  - `basic.css` ändert Bootstrap nur über dessen Variablen (`--bs-…`), in einem eigenen, gekennzeichneten Abschnitt.
  - Ein Theme setzt die Variablen, die `basic.css` dafür anbietet. Soll ein Theme etwas Neues ändern können, kommt zuerst eine Variable in `basic.css` dazu.
  - Ausnahme: Komponenten, die zum Design passen müssen, gestaltet das Theme komplett selbst (Aufbau, Effekt, Farben), `basic.css` enthält für sie nichts. Das sind bisher: alle Knöpfe (`button-save`, `button-cancel`, `button-other`, `NewEntityButton`, `PasswordButton`, `EditButton`, `DeleteButton`), das Hoverlay, der Toast (in `basic.css` nur die Leiste unten rechts), die Trennstriche der Tabellen und die Tabs im subheader (Abstände, Trennstriche, aktiver Tab). Bei den Tabs bleibt in `basic.css` nur, was das Wischen braucht.
- Theme-invariant, also in `basic.css` und nie im Theme: die Badges von AccessRole (Rechteck) und AccessRoleCollection (Pille) samt Verlaufsarten. Sie sehen in jedem Theme gleich aus.
- Farben werden immer als Hex (`#rrggbb`) eingegeben und gespeichert.
- Jeder Key wird im Frontend über `KeyInput` eingegeben. Die Key-Regel steht dort einmal in `components/keyRule.ts`, gleich wie im Backend in `HelperInputs.isValidKey`.
- Jede guid wird im Frontend über `GuidInput` eingegeben: nur 0–9 und a–f, die Bindestriche setzt das Feld selbst, eingefügter Text mit anderen Zeichen wird abgelehnt. Die Regel steht in `components/guidRule.ts`, gleich wie im Backend in `HelperInputs.isValidGuid`.
- Symbole: Bootstrap Icons, Tabler Icons (nur Umriss) und Lucide, alle MIT oder ISC. Gespeichert wird ein Symbol als `{"pack": …, "id": …}`.

## Entities

- Hibernate lädt immer nur eine einzelne Entity aus der DB.
- Keine JPA-Beziehungen zwischen Entities (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`).
- Fremdschlüssel sind einfache ID-Felder (z. B. `Long kundeId`) und werden von Hand aufgelöst, also über einen eigenen Repository-Aufruf.
- Hibernate erzeugt das Schema aus den Entities (`ddl-auto: update`). In der DB entstehen dadurch keine FK-Constraints, die Integrität liegt im Code.
- Stammdaten verweisen untereinander über sprechende Keys (z. B. `RECHNUNG`, `BILDER`), nie über Datenbank-ids. Nur so findet ein Import sie in jeder Datenbank wieder.
- Jede Entity, die im Admin gepflegt wird, gehört in Export und Import der Stammdaten (`MasterDataSection`). Sonst fehlt sie in jedem Export und lässt sich nicht übernehmen.
- Kein Zurücksetzen oder Massenlöschen der Stammdaten über die Oberfläche: zu gefährlich. Gelöscht wird einzeln.

## Dateispeicher (SFTP, `system/sftp`)

- Nur der `SftpService` redet mit dem Dateispeicher. Er kennt keine Rechte und keine Dateiarten, das entscheidet der Aufrufer.
- Alle Pfade sind relativ zum `root-dir` aus der `application.yml`. Der Service prüft sie und setzt die Wurzel selbst davor. Kein Aufrufer baut absolute Pfade.
- Der Ablagepfad kommt vom `SftpPathService`: `JJJJ/MM/TT[_n]/<sha256>.bin`, der Dateiname ist der Hash des Inhalts.
- Schreiben geht immer über `.part`, Größenprüfung und Umbenennen. Unter dem endgültigen Namen liegt nie eine halbe Datei.
- Ob die Grenze je User gilt, entscheidet der Pool über den `CurrentUsersProvider`, nicht der Aufrufer. Deshalb gibt es keine `user…`/`system…`-Methodenpaare.
- Alles, was den Server betrifft, steht in der `.env` (`sftp_host`, `sftp_port`, `sftp_user`, `sftp_pw`, `sftp_host_key`, optional `sftp_private_key_path`). Für den Wechsel auf ein NAS ändern sich nur Host und Host-Key.
- Fehler: 503 mit `Retry-After`, wenn der Dateispeicher nicht kann, 429, wenn der User seine Verbindungen ausgeschöpft hat. Meldungen nach außen ohne Pfade, die stehen im Log.

## Solr-Kerne, lesend (Vorgabe 2026-10-07)

Gilt für jeden Kern, heute und künftig. Je Kern gibt es genau drei Lesemethoden in drei getrennten Services, sonst liest nichts aus einem Kern. Jeder Aufrufer hat einen current user: ein Mensch über das Token oder der System-User aus der Datenbank (Service-Account in `Users`, mit eigenen Rollen). Beide gehen durch dieselben Methoden mit denselben Prüfungen, das Log zeigt, wer es war. Kein Leseweg ohne User, keine Methode, die „für das System“ eine Prüfung weglässt.

- **Existenzcheck.** Parameter nur die id. Löst sie auf und liefert ein abgespecktes Dokument, nur id, Dateiart und Status, oder nichts. Keine Rechteprüfung, jeder darf, auch ein öffentlicher Endpunkt. Mehr kommt nie zurück. Die Maske entscheidet damit vor dem Upload: Merge nur bei gleicher Dateiart und Status nicht DELETED, sonst lehnt der Upload ab. Das Backend fragt damit vor dem Anlegen.
- **Suche.** Freitext plus eingeloggte Filter, Seite und Größe, nach der Suchmaske der Vorlage. Tür: die AR `<KERN>` und `<KERN>_SUCHE`, auch für den Admin, sonst 403 vor Solr. Der Freitext wird zerlegt und escaped, nie roh durchgereicht; nur `+` und `-` sind Operatoren. Filter nur aus einer festen Liste je Kern, gleiches Feld ODER, verschiedene Felder UND. Ohne Suchwort ab drei Zeichen und ohne Filter keine Abfrage, sondern leere Antwort mit Hinweis. Pflicht-fq, hart im Backend: readAcl enthält eine AR des Users ODER userGrantedAccess seine guid, dazu Status ACTIVE oder ARCHIVED. DELETED und jeder andere Status sind nur über das spätere Admin-Auflösen erreichbar, eine Admin-Suche gibt es nicht. Bereichsbedingungen für Datumsfelder gibt es nur hier als Filter, nie im Auflösen. Antwort: Liste kompletter Dokumente mit allen Feldern außer dem Volltext, auch leer, nie 404, dazu Gesamtzahl, Seite, Größe. Keine Feldgrenze: Wer suchen darf, sieht alles, was die Dokumentgrenze zulässt. Paging gedeckelt. Mit Freitext nach Relevanz sortiert, sonst nach documentCreatedAt absteigend. Treffergrund je Treffer, immer, ohne Schalter (User 2026-10-07): je Feld der Wert mit den Fundstellen der Suchwörter und jedes gesetzten Filters, die Maske markiert sie; beim Volltext nur das Feld, nie ein Ausschnitt, denn der Volltext geht nie zum Client. Höchstens sechs Suchwörter, in Maske und Backend dieselbe Zahl; Solr deckelt bei 3000 Klauseln statt 1024 (solrconfig.xml und `solr.max.booleanClauses` in der Compose). Die Suchmaske ist ein eigenes Modul über alle Kerne wie in der Vorlage (`/search`, Kernschalter, Chips, `fsc.` für die Dateiart als Filter, Direktsprung bei exakt getippter id), kein Tab je Kern. Dazu je Kern die Vervollständigung beim Tippen (zweite Methode des Suchservice, dieselbe Tür und dieselben fq, nur die Suggested-Zwillinge und der Volltext, 15 kompakte Treffer, bis zu zehn Filterangebote aus den Treffern): Die Angebote stehen über der Eingabe, Tab loggt das erste ein. Nicht suchbar heißt stumm leer, den Hinweis gibt die Suche.
- **Auflösen.** Exakte Treffer auf einem oder mehreren Feldern, mehrere Werte ODER, mehrere Felder UND, keine Suggested-Zwillinge, kein Freitext. Tür: die AR `<KERN>`. Nach id darf jeder mit `<KERN>` auflösen, so ist jedes Get ein Auflösen. Nach einem Hook nur mit `HOOK_FULL` oder einer Hook-Freigabe, nach einem anderen Feld nur mit einer Feldfreigabe für diesen Kern. Pflicht-fq: readAcl enthält eine AR des Users ODER userGrantedAccess seine guid. Zurück kommen nur die Felder, die der User lesen darf. Drei Schalter an derselben Methode, keine eigenen Methoden: ein Ausgang fürs Backend mit genannten Feldern, die nie zum Client gehen (sftpPath für den Download, Dateiart und Status fürs Bearbeiten); `writable` als Schreibcheck, dann gilt statt des Lese-fq allein writeAcl enthält eine AR des Users, kein Treffer heißt 403 (Schreiben impliziert kein Lesen); Bedingungen, die das Backend setzt, ohne Rechteprüfung (Meine Freigaben: userGrantedAccess gleich eigene guid).
- **Feldgrenze** je Kern, nie je Dokument: mit `<KERN>_FULL` oder `<KERN>_SUCHE` alle Felder, sonst die Minimum-Felder plus die Freigaben des Users. Jedes Feld ist Minimum oder Erweitert (`readAccessLevel`). Lesen ist nicht Auflösen: Was er lesen darf, sagt die Feldgrenze, wonach er auflösen darf, sagen Rollen und Freigaben.
- **Zwei Freigabearten fürs Auflösen**, beide machen das Feld auch lesbar: die Hook-Freigabe serverweit ohne Kern, denn jeder Kern trägt sämtliche Hooks und jeder Hook ist in jedem Kern dasselbe Feld; die Feldfreigabe je Kern, denn ein normales Feld gibt es nur in seinem Kern. `HOOK_FULL` ist die Hook-Freigabe für alle Hooks auf einmal. Der User braucht dazu immer die Tür `<KERN>` des Kerns.
- Suche und Auflösen bleiben getrennte Services, obwohl einer ginge: übersichtlicher, und die Suche braucht keine Feldgrenze.
- **`_version_`** (Entschieden 2026-10-07): Solrs eigener Zähler je Dokument, Solr setzt ihn bei jedem Schreiben selbst. Jede Antwort der Suche und des Auflösens trägt ihn, in jedem Kern, als Text, weil die Zahl 19 Stellen hat und JavaScript sie verfälscht. Wir schreiben ihn nie und vergleichen ihn nie: Beim Schreiben geht er nicht an Solr, keine Konfliktprüfung, der Letzte gewinnt. Die Revision speichert ihn nicht (User 2026-10-08, er sagt nichts), das Register trägt je Änderung nur die Felder, deren Wert sich wirklich geändert hat; beim Zurückladen wird `_version_` ignoriert, denn Solr vergibt es bei jedem Schreiben selbst, ein mitgeschickter alter Wert wäre eine Bedingung und ein 409.
- Kein gemeinsamer Leseservice für alle Kerne (Vorgabe 2026-10-07, ersetzt die Mechanik in `solr/document` vom 2026-10-03). Je Kern drei Lese-Services im Fachpaket, jeder baut seine Solr-Abfragen selbst und ruft die rohe Solr-Schicht `solr/document/SolrDocumentService` direkt. Kernunabhängig bleiben nur die rohe Schicht, die Rechterechnung (`solr/access`), denn Token und Rollen sind in jedem Kern gleich, und die Zerlegung des Suchtexts (`solr/document/model/SolrSearchText`), denn die Operatoren `+` und `-` sind in jedem Kern dieselben. Die Formprüfung ist je Kern (`DmsSolrDocumentChecker`). Namen nach `naming.md`. Jeder Weg zu einem Kern steht in `docs/backend/solr-zugriff.md`, bevor er gebaut wird.
- **Pflichtfelder eines Kerns.** Die Felder, mit denen das Backend rechnet (im DMS readAcl, writeAcl, status, fileSubClassKey, sftpPath, …), stehen mit Namen hart im Code des Kerns. Kein Check beim Start und kein eigener Endpunkt dafür: Fehlt ein Feld, fliegen Fehler, der Admin ist eingewiesen. Sobald die Kerne stehen, hält das Frontend je Kern hart die erwarteten Felder mit Typ und Schaltern und schlägt im Kern-Tab fehlende zum Anlegen vor. Ein Feld mit Schreibregel `BACKEND_ONLY` hat im Kern-Tab keinen Löschknopf, denn das Backend braucht es; nur seine Schalter lassen sich ändern.

## Solr-Kerne, schreibend (Vorgabe 2026-10-07)

Gilt für jeden Kern. Je Kern genau ein Schreibservice im Fachpaket, die Mechanik einmal in `solr/`, die rohen Schreibmethoden des `SolrDocumentService` package-private. Jeder Schreibvorgang hat einen current user, Mensch oder System-User, und steht mit ihm im Log. Zwei Fälle, sonst nichts:

- **Anlegen.** Nur wenn der Existenzcheck nichts findet. Volles Dokument. Das Recht kommt aus der Regel des Kerns, aus der writeAcl entsteht, im DMS die Schreibrolle der Dateiart. Kein Lesen nötig: Anlegen braucht keine Leserolle.
- **Atomic Update.** Nur an einem vorhandenen Dokument, das das Auflösen mit `writable` findet, sonst 403. Nur die genannten Felder. Immer changingUsers ADD und lastChangedAt SET dazu. Ob ein Vorgang revisioniert wird, steht am Enum-Wert des Vorgangs (`DmsSolrDocumentWriteProcess`, gebaut 2026-10-08), nicht in der Schreibmethode: Ein revisionierter Vorgang lädt mit dem Schreib-fq das ganze Dokument, behält nur Änderungen, die gegen diesen Stand wirklich etwas ändern (bleibt keine, wird weder gesichert noch geschrieben), sichert den Stand als ZIP mit Registerzeile und genau diesen Feldnamen und hängt fileIdRevisionNo INC an dieselbe Änderungsliste; ein Update vieler Dokumente mit revisioniertem Vorgang läuft je Dokument einzeln. Anlegen und Dateifreigaben sind nicht revisioniert. Die eine Ausnahme von Schreibcheck und Stempel ist der Download-Zähler (User 2026-10-08): downloadCount INC 1 nach jedem Download und Viewer-Abruf, Buchhaltung des Backends, keine Änderung durch einen Menschen, die Leseprüfung hat der Download gemacht. Soft-Delete ist ein Update des Status; danach gibt es das Dokument für User auf keinem Weg mehr, denn jede User-Abfrage trägt `status:(ACTIVE OR ARCHIVED)` (User 2026-10-08), zurückholen kann es der Admin im DMS-Manager. Merge beim Upload derselben Bytes ist ein Update der Hooks. Die Dateifreigabe ist ein Update von userGrantedAccess über eine Liste von ids aus dem Auflösen. Ein vorhandenes Dokument wird nie voll neu geschrieben: Solr löschte stillschweigend jedes nicht geschickte Feld, und die Feldprüfung wäre umgangen.

Der Aufruf: die id und eine Liste von Feldänderungen, jede mit Feldname, Operation und Werten. Der Service prüft je Änderung Feld im Kern, Operation passt zu einwertig oder Liste, Werte passen zum Typ, Aufrufer darf das Feld setzen. Erst dann der Modifier.

| Feld | Operation | Solr |
|---|---|---|
| einwertig | SET | setzen, null nimmt das Feld raus |
| Zahl | INC | hochzählen |
| Liste | SET_ALL | ganze Liste ersetzen, leer nimmt das Feld raus |
| Liste | ADD | genannte Werte dazu, ohne Doppelte (`add-distinct`, nie `add`) |
| Liste | REMOVE | genannte Werte raus, andere bleiben |

- Der Service vergleicht nie selbst alt gegen neu. Wer ein Delta will, nennt die Werte; wer eine Liste will, nennt die ganze Liste. Abgeleitete Listen immer SET_ALL aus der Quelle: readAcl und writeAcl setzt der Schreibservice genau dann neu aus der Dateiart, wenn er fileSubClassKey schreibt, also beim Anlegen und beim Dateiartwechsel. Bei jedem anderen Schreibvorgang bleiben sie unberührt. Deltas nur, wo jemand einzelne Werte pflegt: userGrantedAccess ADD beim Freigeben, REMOVE beim Entzug, changingUsers ADD. Hooks beim Bearbeiten SET_ALL, die Maske schickt die fertige Liste.
- **Schreibregel je Feld** in `solr_field`, Spalte `clientWriteRule`, im Kern-Tab klickbar, nie in Solr: `CLIENT_SETS_AND_CHANGES` (Client setzt beim Anlegen und ändert beim Bearbeiten: fileDescription, fileIndex, status, fileSubClassKey, documentLanguage, fileName), `CLIENT_SETS_ONCE` (nur beim Anlegen, danach ignoriert: versionChain, fileSizeBytes), `BACKEND_ONLY` (nie vom Client: sftpPath, readAcl, writeAcl, mimeType, uploader, changingUsers, lastChangedAt, bildVorschauBase64Png). Schickt der Client ein Feld, das er nicht darf, wird es ignoriert, nicht abgelehnt; der Controller gibt es nicht weiter. Das Backend setzt seine Felder nach denen des Clients, ein mitgeschickter Wert wird überschrieben. Die eingebauten id, cursorDate, userGrantedAccess sind fest `BACKEND_ONLY`. Hooks sind immer Client. Neue Felder beginnen als `BACKEND_ONLY`, der Admin schaltet frei. Die Sonderprüfungen bleiben Code des Kerns: Pflicht, Werteliste, Prüfung gegen die Datei, Schreibrolle der neuen Dateiart beim Wechsel.
- **Schreiben impliziert kein Lesen.** Beispiel Bewerbung: Dateiart BEWERBUNG mit Leserolle PERSONAL und Schreibrollen PERSONAL und GAST_BEWERBUNG. Ein öffentlicher Endpunkt nimmt die Datei an und ruft den Schreibservice mit einem festen Service-Account aus `Users`, der nur GAST_BEWERBUNG hat; Dateiart fest im Endpunkt. Antwort nur „angenommen“: Das Dokument nach dem Anlegen holt das Auflösen mit der Lesegrenze des Users, und die gibt dem Gast nichts. Mehr als ein Merge derselben Bytes kann er nicht, und das bestimmt die Maske. Ein eigener Status hält solche Dokumente aus der normalen Suche; dafür kommt später ein eng abgegrenztes Admin-Auflösen ohne Rechteprüfung, keine Admin-Suche (User 2026-10-07).
- **System-User.** Einmal angelegt, Service-Account, steht per Default in der Schreib-ACL jeder Dateiart. Läuft Ablauf und Entzug der Freigaben und jeden Systemjob, mit denselben Prüfungen wie ein Mensch.
- **Vorgänge im Code.** Je Kern eine öffentliche Methode je Vorgang, benannt nach dem Vorgang, nichts Allgemeines: im DMS `createDmsSolrDocumentFromUpload`, `mergeHookValuesFromRepeatedUpload`, `createNextVersionInVersionChain`, `editDmsSolrDocument`, `softDeleteDmsSolrDocument`; in der Freigabe `addGranteeToDocuments`, `removeGranteeFromDocuments`. Jeder Vorgang trägt seinen Namen als Enum-Wert, Pflichtparameter der Mechanik, der mit current user, Kern, id und Feldnamen im Log steht. Dasselbe für die Lesewege: je Vorgang eine Methode im Lese-Service des Kerns, keine Methode, die „irgendwas“ lädt.
- **Merge**, dieselben Bytes noch einmal hochgeladen: nur bei gleicher Dateiart und Status ACTIVE oder ARCHIVED, mit Schreibcheck am vorhandenen Dokument. Kommt der Upload mit versionChain, muss es die Kette des vorhandenen Dokuments sein, sonst 400. Ergänzt werden nur die Hooks (`ADD`, Vorhandenes bleibt, keine Doppelten) und die Beschreibung (je Sprache hinten angehängt, wenn nicht identisch), dazu changingUsers `ADD` und lastChangedAt `SET`, und das nur, wenn tatsächlich etwas ergänzt wurde. fileIndex wird nicht gemergt: Schickt der Upload einen anderen Index als den des Originals, lehnt der Merge ab. Alles andere bleibt, auch readAcl und writeAcl, der Dateiname des neuen Uploads wird ignoriert.
- **Versionskette.** Eine Kette hat durchgehend dieselbe Dateiart. Neue Fassung: Der Client schickt versionChain, das Backend löst die Kette mit der Lesegrenze des Users auf, unbekannt und unsichtbar sehen gleich aus. Die Dateiart kommt vom Kopf der Kette, nie vom Client, der User braucht ihre Schreibrolle. Die neue Fassung bekommt version plus eins und ACTIVE, danach wird jedes ACTIVE-Glied der Kette auf ARCHIVED gesetzt, je Glied mit Schreibcheck. Nie ein manuelles Umhängen einer Datei in eine andere Kette.
- **Durchläufe** über viele Dokumente (Reindex, ACL-Nachzug, Export, Ketten-Reparatur) sind Backend-Prozesse als System-User. Der Client startet sie und sieht einen Statusmonitor, mehr nicht. Blättern per cursorMark ist Sache der Suchmechanik. Dokumente aller Status erreicht nur das spätere, eng abgegrenzte Admin-Auflösen ohne Rechteprüfung: konkrete Werte, Treffer oder nichts. Es gibt keine Admin-Suche, der Admin sucht wie jeder User (User 2026-10-07).

## Betrieb und Entwicklung

- Die Compose läuft immer auf dem Pi.
- Postgres und Solr laufen immer auf dem Pi, auch beim Entwickeln.
- Zum Entwickeln startet das Backend lokal in VS Code (Frontend: noch offen).
- Solange wir entwickeln, werden die Ports aus der Compose nach außen gemappt.
- Docker-Logs liegen auf der HDD des Pi (`/mnt/hdd`).
- Docker-Logs zusammen maximal 500 MB. Bei jedem neuen Container das Limit pro Container so anpassen, dass die Summe 500 MB bleibt.

## Konfiguration

- Sämtliche Backend-Parameter stehen in `spring/src/main/resources/application.yml`. Keine `application.properties`.
- Werte aus der `.env` werden dort mit `${variable}` eingebunden.
