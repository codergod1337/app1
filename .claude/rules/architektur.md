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

## Frontend

- React mit TypeScript, gebaut mit Vite, im Ordner `frontend-ts/`.

### Aufbau einer Seite

- Immer da sind nur `header`, `main` und `footer`. Der `footer` ist unabhängig vom Modul.
- In `main` rendert das Modul selbst: optional seinen `subheader` (mit Bootstrap `sticky-top` bleibt er beim Scrollen stehen), dann `main-content` mit dem eigentlichen Inhalt.
- In `main-content` steht genau eine von drei Darstellungen: ContentBox, Matrix oder A4-Ansicht.
- ContentBox: `contentbox` mit `contentbox-header`, `contentbox-main` und `contentbox-footer`. Der Footer ist fürs Design immer da, nur sein Inhalt (Text, Knöpfe) ist optional. In ContentBoxen stehen auch Tabellen.
- Tabelle: einmal zentral definiert (`components/Table.tsx`), mit Kopfzeile, Zeile A und Zeile B (abwechselnd, A und B dürfen gleich aussehen).
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
