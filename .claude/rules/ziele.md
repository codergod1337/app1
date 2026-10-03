# Ziele und Pläne

## Nächste Schritte

1. Users-Controller: User anlegen und verwalten.
2. Admin-Endpunkt für AccessRole: anlegen und verwalten. Dazu die Zuordnung User ↔ AccessRole, damit ein Admin die Rolle `ADMIN` bekommen kann.
3. Frontend: darüber die ersten Daten anlegen.
4. Sobald ein Admin mit der Rolle `ADMIN` existiert: Spring Security (Login mit JWT, Gültigkeitsbereiche über `area`, z. B. `INTERN` und `EXTERN`). `/api/rest/v1/admin/**` wird mit der AR `ADMIN` abgesichert. User bearbeiten ist kein Admin-Endpunkt.
   - PUT auf User: Ein Admin darf immer überschreiben (auch beim Import) und die guid eines Users ändern. Ein normaler User darf nur sich selbst ändern und nie die guid. Dazu der Provider für den current user, der System-User und eine Annotation (z. B. `@AlsSystem`), die Jobs ohne Token als System-User laufen lässt.
   - Vorher: Zuordnung User ↔ AR (`AccessRoleUsersAssignment`), sonst trägt niemand `ADMIN` und der Admin sperrt sich aus.
   - Login analog zur Vorlage: JWT und Refresh-Token als Cookies, kein CSRF. Jeder Login mit 2FA (Aktivierungslink per Mail). Bis es Mailversand gibt, steht der Link als Text „Aktivierungslink“ in der Antwort und öffnet sich in einem neuen Fenster.
   - area: Es gibt je einen Login-Pfad pro area (`/api/rest/v1/public/intern/login/…` und `/api/rest/v1/public/extern/login/…`), die area steht als Claim im Token. Die übrigen Endpunkte haben keine area im Pfad, der Zugriff läuft über die AR.
   - Stammdaten über einen public Endpunkt ausgeben: alle AR und alle ARC, später auch FSC und MSC.
   - Admin-Rechte vergeben verlangt immer eine eigene 2FA, auch in der area ohne Login-2FA. Das gilt für jeden Weg, auf dem jemand an `ADMIN` kommt:
     - die AR `ADMIN` einem User zuordnen,
     - eine ARC, die `ADMIN` enthält, einem User zuordnen,
     - `ADMIN` in eine ARC aufnehmen, die schon Usern zugeordnet ist,
     - eine ARC mit `ADMIN` als Slave an eine andere ARC hängen, falls Slaves beim Auflösen ins Token mitzählen,
     - der Import der Stammdaten, wenn er `ADMIN` direkt oder über eine ARC zuordnet. Er schreibt über dieselben Services, die Prüfung greift dort also von selbst. Bis dahin zeigt die Vorschau, wer `ADMIN` bekäme.
     Die Prüfung sitzt im Service, nicht im Frontend, damit sie kein Weg umgeht.
   - Sperrliste pro User im Arbeitsspeicher („Tokens dieses Users gelten erst ab Zeitpunkt X“), damit Änderungen an Rechten sofort wirken und nicht erst nach Ablauf des JWT. Gesetzt bei: AR-Änderung, User gelöscht, Passwort oder guid geändert, Diebstahl erkannt. Der Filter vergleicht `iat` mit diesem Zeitpunkt, das Frontend holt sich per Refresh ein neues Token. Nach einem Neustart darf die Liste leer sein, weil dann ohnehin alle JWTs ungültig sind.

## Danach (Reihenfolge noch offen)

- Oberfläche komplett in der gewählten Sprache (Vorgabe 2026-10-03, react-i18next): Alle festen UI-Texte (Spaltenköpfe, Knöpfe, Platzhalter, Meldungen, Hoverlay-Erklärungen, Popup-Titel) stehen in `src/branding/locales/{de,en,bg}.json`, gleich aufgebaut, verschachtelt nach Modul (`common`, `layout`, `solrmanager`, …); jeder Text ein ganzer Satz mit Platzhaltern, Mehrzahl über `_one`/`_other`, de ist die Quelle. In Komponenten `const { t } = useTranslation()` und `t('solrmanager.cores.title')`, mit Auszeichnung `<Trans i18nKey components={{ strong: <strong /> }}>`, außerhalb von Komponenten `i18n.t`. Schlüssel typisiert (`src/i18next.d.ts`), `npm run lint` prüft über `scripts/check-locales.mjs`, dass alle drei Dateien dieselben Schlüssel haben. Angebunden an `useLanguage()` im `UsersSettingsProvider`. Datentexte (displayName, description) laufen weiter über `translate()`. Umgestellt: Layout, gemeinsame Komponenten (Table, Popup, DeletePopup, KeyInput, SolrFieldNameInput, SymbolPicker, SymbolPopup, ColorField, ValuesInput) und das ganze SolrManager-Modul. Offen: admin, users, profile, login, masterdata, accessrole, filesubclass, fileextension, home; deren `origin.label` bleibt bis dahin deutscher Text (`translateLabel` lässt ihn durch).

- User-Settings und Sprache (gebaut 2026-10-03, nach der Vorlage): `UsersSettings` (users_guid, key, value) sind Anzeigeeinstellungen, nie Rechte. Jeder mit gültigem Token liest und schreibt nur die eigenen über `/api/rest/v1/userssettings` (GET, PUT {key, value}, DELETE {key}), die guid kommt aus dem Token; keine Vorgabe-Zeilen, fehlt eine Einstellung, gilt der Standard des Frontends. Frontend: `UsersSettingsProvider` (in `Layout`, unter Sitzung und Stammdaten) lädt die eigenen Zeilen nach dem Login, `useUsersSettings()` liefert settingValue, changeSetting, deleteSetting. Darauf `useLanguage()`: UsersSetting `LANGUAGE`, ohne Anmeldung die Wahl des Gasts in sessionStorage, sonst de. Gewählt über die Flagge im Header (`LanguageMenu`, links neben dem Benutzermenü). `translate` ohne Sprache nimmt die Sprache der Oberfläche, ein Wechsel baut alles unter dem Provider neu auf; die Flaggen der ContentBox starten in dieser Sprache. Weitere Settings (z. B. THEME) kommen über denselben Weg.

- SFTP: Anbindung (`system/sftp`) und Container (`app1_sftp`) laufen auf dem Pi (2026-10-02). Offen: erster Upload über den File-Kern.
- SFTP-Monitor und Schalter, Admin-Endpunkte in `system/sftp`, im Frontend eine ContentBox im Admin:
  - Status, nur lesend: erreichbar seit wann, Verbindungen verliehen/frei/maximal, welche User Plätze belegen, letzter Fehler mit Zeit, Zähler seit Start (Uploads, Downloads, Bytes, 503, 429).
  - Schalter pausieren/fortsetzen: laufende Übertragungen werden fertig, neue bekommen sofort 503 mit `Retry-After`, der Pool wärmt nicht vor. Nur im Arbeitsspeicher, nach einem Neustart läuft der Dateispeicher wieder.
  - Erst mit dem File-Kern: laufende Übertragungen mit Pfad, Richtung und Bytes bisher.
- SolrManager (Frontend-Modul `modul/solrmanager/`, Backend-Paket `solr` mit `hook`, `core`, `field`; Tabellen `solr_*`), eine stark abgespeckte Solr-Admin-Oberfläche, in dieser Reihenfolge:
  1. Hooks: `SolrHook` (key = Solr-Feldname, displayName, description, hookGroupKey, look, listingPosition), Service hält die Tabelle als Kopie im Speicher, Tab „Hooks“. Reines Postgres, läuft ohne Solr. Gebaut 2026-10-02. Hook-Gruppen (2026-10-03): eigene Entity `SolrHookGroup` (Tabelle `solr_hook_group`: key nach der normalen Key-Regel, displayName mehrsprachig, listingPosition), Tab „Hook-Gruppen“, Stammdaten-Bereich `SOLR_HOOK_GROUPS` vor den Hooks. Nur für die Anzeige, ein Hook zeigt mit `hookGroupKey` auf seine Gruppe, ohne Gruppe erlaubt; das Backend prüft beim Schreiben, dass es die Gruppe gibt, und löscht eine Gruppe nur ohne Hooks darin (409). Kern-Tab und Hook-Auswahl zeigen die Chips je Gruppe in der Reihenfolge der Gruppen, Hooks ohne Gruppe zuletzt. Die Gruppen werden später noch einmal überarbeitet. Aussehen (2026-10-03): kein `look` am Hook, alle Chips sehen gleich aus, der Look steht fest in basic.css (`solr-hook-chip`): ein Fels, anthrazit mit 3D-Wölbung, darüber rot glühende Risse als SVG-Hintergrund. `SolrLook` gibt es nur noch am Kern. `SolrHookPicker` (nach der HookAuswahl der Vorlage, Hooks aus den Stammdaten statt fester Liste): Knopf, Popup mit Suchfeld, Gruppen mit Chip und Feldname, Teilstring über Feldname und Anzeigename, Enter nimmt den ersten Treffer; im Tab „Hooks“ zum Suchen und Bearbeiten, später in jeder Maske, die Hooks setzt (Upload, Bearbeiten). Presets der Vorlage nicht übernommen.
  2. Kerne: `SolrCore` (key nach der normalen Key-Regel, zugleich der Kernname in Solr, displayName, look, listingPosition), Tab „Kerne“ und je Kern ein eigener Tab unter `/solrmanager/<key>`. Gebaut 2026-10-02, reines Postgres.
  - Aussehen: `SolrLook` (Embeddable im Kern, Spalten flach): Verlauf aus drei Farben, Schrift, Schriftschatten, Schatten, Verlaufsart, Symbol. Angezeigt über eigene Komponenten, kein Badge: `SolrCoreEmblem` für den Kern (Emblem-Form noch zu gestalten, bis dahin abgerundetes Rechteck) und `SolrHookChip` für den Hook (Pille, nach der FilterBadge der Vorlage: Hook und Wert, Angebot oder gesetzter Filter mit Mülleimer, der Wert als Link). Der Chip hat einen festen Look in basic.css, in jedem Kern gleich. Felder eines Kerns (Schritt 3) bekommen Chips in den Farben des Kerns. Container `app1_solr` mit Basis-Configset steht in der Compose (2026-10-03, `docs/backend/solr.md`), SolrJ kommt mit Schritt 4.
  3. Felder je Kern: `SolrField` (id, coreKey, name, type aus dem Katalog `SolrFieldType`, die Schalter indexed, stored, multiValued, docValues, required, suggest, description, listingPosition), Tabelle `solr_field`, eindeutig je Kern und name. Es gibt genau zwei Arten von Solr-Feldern: Hooks (in jedem Kern gleich, unten als Chips) und normale Felder je Kern, sonst nichts. Kein `system`-Kennzeichen: `id` ist ein normales Feld wie jedes andere. Kern-Tab wie eine Tabellenkalkulation (`SolrFieldSheet`, gebaut auf der zentralen `Table`): eine Zeile je Feld, alles direkt in der Zeile bearbeiten, Schalter speichern sofort, Texte nach kurzer Pause, Reihenfolge per Drag and Drop, die leere Zeile unten legt ein neues Feld an. Gebaut 2026-10-02, reines Postgres.
     - ID des Dokuments: Solr lässt den `uniqueKey` nach dem Anlegen eines Kerns nicht mehr ändern, deshalb heißt er in jedem Kern `id`. `id` ist immer der Primärschlüssel des Kerns, vom Backend mit dem Kern angelegt und beim Start nachgezogen, und steht fest: String, indexed, required, nie multiValued, nicht löschbar (Backend lehnt ab); stored, docValues, Beschreibung und Position sind frei. Keine Wahl einer Quelle am Kern (die frühere `idSource` ist weg): Was in `id` kommt, steht in der Beschreibung des Feldes, z. B. beim DMS der Hash der Bytes (`sha256`), und wer den Kern befüllt, hält sich daran. Im Sheet steht `id` immer an Position 1 und ist nicht verschiebbar (`Table` mit `fixedRow`, Frontend), die Zeile trägt den Schlüssel; die Beschreibung jedes Feldes steht zusätzlich als Hoverlay am Feldnamen. Ebenso in jedem Kern vom Backend angelegt: `cursorDate` (Datum, indexed, stored, docValues), der Fortschrittszeiger eines Reindex-Laufs. Beide stehen in `SolrCore.BUILT_IN_FIELD_NAMES`: nicht löschbar, Typ fest, Import überspringt sie als vorhanden.
     - Offen: je Feld festlegen, ob es per Atomic Update änderbar ist oder ein komplettes Neuschreiben des Dokuments braucht.
     - Mehrsprachige Texte immer in de, en und bg (Vorgabe 2026-10-03). Hooks, Hook-Gruppen und die Felder des Kerns FILE sind dreisprachig; offen: die Feldbeschreibungen der Kerne ARTIKEL und TESTTEST sind noch nur de.
     - Zwei Darstellungen eines Hooks: `SolrHookChip` als Filter (Hook und Wert, Angebot oder gesetzter Filter mit Mülleimer) und am Dokument das Hook-Modul `SolrHookFields` nach HookFelder der Vorlage (2026-10-03): nur gefüllte Hooks, je Hook eine Zeile mit dem Chip und den Werten; Bearbeiten mit Mülleimer am Chip (leert das Feld), `ValuesInput` (generisch in `components/`, nach WerteInput der Vorlage: Leertaste, Semikolon, Enter loggt ein, eingefügte Listen werden zerlegt) und unten der `SolrHookPicker`; Nur-Lesen über `SolrHookValues` (Chip, dahinter jeder Wert als Badge). Hauseigene Artikelnummern (`ARTICLE_NUMBER_HOOK_KEYS`: artikelNummer, masterArtikelNummer) sind Links in die Artikelmappe `/artikel/<nummer>` wie in der Vorlage, alle anderen Werte später der Weg in die Suche mit gesetztem Filter (`solrHookValuePath`). Erster Einsatz: Upload und Dokumentseite des File-Kerns.
  4. Abgleich: Den Ordner eines Kerns legt der Container an (Startkette in der Compose: `precreate-core` je Key aus `solr_cores` in der `.env`, aus dem Basis-Configset `solr/configsets/app1` mit nur den Feldtypen, `id`, `_version_` und `_root_`; vorhandene Kerne bleiben unberührt). Nicht das Backend, denn im user-managed Modus gibt es keine Configsets-API, die kopiert, und CoreAdmin CREATE mit `configSet` teilt eine Schemadatei zwischen allen Kernen. Das Backend legt die Felder per Schema-API an (`add-field`, `replace-field`, `add-copy-field` für die Suggest-Zwillinge, alles in einer Anfrage), beim Start und nach jeder Änderung, löscht nie und meldet Typänderungen nur. Typänderungen heißen Neuaufbau des Kerns (Ordner löschen, Container neu, Dokumente wieder einspielen), deshalb alle echten Felder `stored`. Besprochen 2026-10-03:
     - Container `app1_solr` (2026-10-03): `solr:10.0.0` ohne eigenen Build, `--user-managed`, Daten in `docker_mounts/solr`, Alias `solrhost` ohne Unterstrich (Java-URI). Basic Auth mit `blockUnknown` aus einer `security.json`, die die Startkette bei jedem Start aus `solr_user` und `solr_pw` der `.env` erzeugt (gesalzener doppelter SHA-256, gegen die Solr-Doku und den Solr-Algorithmus in Java geprüft). Kein Host-Key wie beim SFTP: HTTP im Docker-Netz, die Identität des Servers sichert später nur TLS am nginx, Solr ist nie von außen erreichbar. Kein JWT-Plugin: Es braucht einen Identity-Provider mit festem Schlüssel und für die Admin-Oberfläche einen OAuth2-Endpunkt; das Backend erzeugt seinen RSA-Schlüssel bei jedem Start neu und spricht Solr als Dienst an. Später bei Bedarf `MultiAuthPlugin`.
     - Solr-Systemfelder `_version_` (Optimistic Concurrency, plong, nur docValues) und `_root_` gehören ins Basis-Configset, nicht in `solr_field`; der Abgleich fasst sie nie an. `_version_` kommt mit jedem gelesenen Dokument und geht als Bedingung mit zurück (Konflikt heißt 409), bei einem Vollschreiben wird es gestrichen.
     - Schreiben per Atomic Update: `set`, `add-distinct`, `remove`, `inc`. Solr indiziert intern trotzdem neu, gespart wird das Mitschicken des ganzen Dokuments. Voraussetzung erfüllt: alle echten Felder stored, Suggest-Zwillinge nicht stored. Ein neues Dokument (Upload) wird voll geschrieben.
     - Client: SolrJ mit `HttpJettySolrClient` aus `solr-solrj-jetty`, nie `HttpJdkSolrClient` (hängt nach Verbindungsabbruch für immer, SOLR-17707), Timeouts wie in der Vorlage. Statt fester Beans je Kern ein Register, das je Kern-Key aus Postgres seinen Client hält.
     - Kein DTO je Kern: Das Dokument ist eine Map (SolrJ `SolrDocument`), zum Frontend JSON. Die Feldliste ist das Schema für beide Seiten; das Backend prüft jede Änderung dagegen (Feld im Kern oder Hook, Typ, multiValued, `id` nie, `cursorDate` nur System, Zwillinge nie).
     - Eine Solr-Schicht ohne Fachwissen: get(core, id), atomicUpdate(core, id, Änderungen), write(core, doc), delete(core, id). Darüber dünne Services je Kern mit dem Zugriffsfilter aus dem Token, den Fachregeln (DMS: Schreibrecht aus der Dateiart, Status, Revision; Artikel: nur lesen) und den Nebenwirkungen (Revisions-ZIP vor dem Schreiben).
  - Hooks gelten in jedem Kern identisch, auch in den Spiegelkernen aus ABAS. In Solr ist jeder Hook in jedem Kern dasselbe Feld mit festen Einstellungen (`SolrHook.FIELD_*`): string, indexed, stored, multiValued, docValues, suggest; nie required, jeder Hook ist je Dokument optional. Der Abgleich (Schritt 4) legt ihn so an. Der Kern-Tab zeigt alle Hooks als Katalog in einer Hoverlay-Box (`SolrHookCatalog`, je Gruppe die Liste mit Chip und Feldname, dieselbe Liste wie in der Hook-Auswahl), ohne Erklärtext. Ein Hook trägt keine Rechte, jeder Hook ist freigebbar. Drei Achsen, alle additiv:
    - Hooks: System-AR `HOOK_FULL` heißt jeder Hook mit jedem Wert, die Prüfung wird übersprungen. Ohne `HOOK_FULL` zählen nur die Freigaben des current users (eigene Entity in Postgres, nach den Kernen: User, Hook, Wert, wer wann freigegeben hat). Sie legen fest, welche Hooks mit welchen Werten er in den Filter setzen darf. Alles andere wird vor Solr abgelehnt, die freigegebenen Werte gehen als fq über den terms-Parser mit, nie als Nachfilter.
    - Kern: `<KERN>` heißt Auflösen in diesem Kern (Hook gleich Eingabe, exakt, keine Suggested-Felder). `<KERN>_SUCHE` heißt zusätzlich Suchen über die erlaubten Hooks (Teileingabe, Suggested-Felder).
    - Dokumente: die Dateiart, als fq wie bisher.
    - Der HookAccessChecker ist kernunabhängig und liefert pro Request: full, oder je Hook die erlaubten Werte. Nichts davon ins Token.
- Solr: Anbindung per SolrJ (Schritt 4 des SolrManagers), der Container steht seit 2026-10-03. Beim File-Kern ist Solr der einzige Speicher.
- File-Kern, ähnlich dem DMS der Vorlage.
- A4-Ansicht (Details später):
  - Das A4-Seitenverhältnis bleibt immer erhalten.
  - Jedes A4-Blatt hat einen Vorlagetyp. Die Vorlage definiert eigene Felder.
  - Jedes Feld hat eine id, eine Position (linke obere Ecke), Breite, Höhe und Randbreite.
  - Im Browser nutzen die Seiten das Aussehen des Hoverlays als Hintergrund (nicht beim PDF).

## Zugriff auf die Solr-Kerne

Oberste Regel: Die Suche für normale User wird durch keine zusätzliche Rechteprüfung langsamer.

- Jeder Kern-Service liest die AR des Users aus dem Token und baut daraus den Filter (`fq`) fest in jede Abfrage ein. Kein Aufrufer kann ihn weglassen, es gibt keinen Datenbankzugriff dafür.
- Das gilt für jeden Weg: Suche, Auflösen per Feld oder id, Download, Facetten, Vorschläge, Highlighting, Export. Der SFTP-Pfad kommt beim Download nur aus dem gefilterten Solr-Dokument, nie vom Client.
- Schreiben wird mit demselben Filter geprüft: Findet die Abfrage mit der id und dem Schreibfilter das Dokument, darf der User schreiben.
- Pro Kern zwei Rollen wie in der Vorlage: Suchen (Freitext, z. B. `ARTIKEL_SUCHE`) und Auflösen (feste Felder, exakte Werte, z. B. `ARTIKEL`).
- Rollen sind rein additiv: Jede AR öffnet etwas, keine verbietet etwas. Eingeschränkt ist ein User, weil ihm Rollen fehlen.
- DMS: Dort haben wir die Datenhoheit, der Kern ist die Datenbank der Metadaten. Wer eine Datei lesen oder schreiben darf, entscheidet allein ihre Dateiart (FSC-ACL-Matrix). Das gilt auch für Mail-Anhänge: Wer eine Mail archiviert, ordnet jeden Anhang einer Dateiart zu, und damit sind seine Rechte festgelegt.
- Artikel- und Kontakte-Kern sind nur ein Spiegel aus dem Fremdsystem. Wir schreiben dort nichts hinein, deshalb stehen dort auch keine Freigaben.

## Eingeschränkte User (Sublieferanten, „Light“)

Ziel: Vor dem Gang ins Internet genau festlegen können, was ein Sublieferant sieht. Der Standard ist: nichts.

- Ein Light-User hat nur die AR `ARTIKEL_LIGHT` (`system = true`, weil sie im Code geprüft wird). Er bekommt keine Suchrolle und keine Kernrolle anderer Kerne, also kein `ARTIKEL`, `NACHRICHTEN` oder `KONTAKTE`. Damit sind alle anderen Kerne für ihn ohne weitere Prüfung zu.
- Freigaben (`AccessGrant`) liegen in Postgres: pro User oder pro AR (z. B. `BULGARIEN` für die fünf Mitarbeiter eines Sublieferanten) eine Liste von Artikelnummern. Dazu wer wann freigegeben hat.
- Freigaben pflegen dürfen Admin und Geschäftsleitung über eine eigene AR, geprüft per `@PreAuthorize`. Dazu eine Freigabemaske: pro User oder AR die freigegebenen Artikel, pro Artikel wer ihn freigegeben bekommen hat.
- Ein eigener Light-Service mit eigenen Endpunkten unter `/api/rest/v1/artikellight/…`, abgesichert über den Pfad mit `ARTIKEL_LIGHT`. Die internen Services bleiben unverändert.
  - Ein Freigabe-Auflöse-Service liefert dem Light-User die Liste seiner freigegebenen Artikelnummern. Die Liste ist nur zum Navigieren da.
  - Jeder Aufruf prüft, ob die Artikelnummer für diesen User oder eine seiner AR freigegeben ist. Artikelnummern lassen sich erraten.
  - Es gibt genau eine Light-Sicht, sie dient nur dazu, den Artikel produzieren zu können. Sie fragt nur die Felder einer festen Positivliste ab (`fl`), verbotene Felder verlassen Solr nie. Ein neues Feld im Mapper ist für Light-User unsichtbar, bis es in die Liste kommt.
  - Enthalten: Stammdaten, Technik, Stückliste, Arbeitsplan, Prüfaufträge und QM. Nicht enthalten: Kunden, Lieferanten, Hersteller, Preise, Lager, Disposition, Verlauf, interne Felder.
  - Die Antwort ist eine feste Klasse mit genau den erlaubten Feldern, als bewusste Ausnahme von „DTOs möglichst vermeiden“, weil sie die Sicherheitsgrenze ist. Ein Test schlägt fehl, sobald ein Feld außerhalb der Liste in der Antwort steht.
  - Bauteile aus der Stückliste werden mit Nummer, Name und Menge gezeigt. Auflösen kann der Light-User ein Bauteil nur, wenn es selbst freigegeben ist.
  - Dateien zum Artikel kommen aus dem DMS: freigegebene Artikelnummer und Dateiart lesbar für seine Rolle.
  - Nachrichten zum Artikel kommen ebenfalls über den Light-Service: freigegebene Artikelnummer und Nachrichtenart (`MessageSubClass`) lesbar für seine Rolle. Ausgeliefert werden nur Betreff, Datum, Text und Anhänge, nicht die Absender und Empfänger. Anhänge nur, wenn ihre Dateiart für ihn lesbar ist.
- Noch offen:
  - Gehören Stück- und Rüstzeiten aus dem Arbeitsplan in die Light-Sicht, oder sind sie als Kalkulationsgrundlage vertraulich?
  - Gehören die Referenzen „Werkzeug“ und „Benötigt“ in die Light-Sicht?
  - Wann `AccessGrant` und die Freigabemaske gebaut werden. Das hängt nicht von Solr ab, der Light-Service erst, wenn der Artikelkern steht.
- Vorgeschlagen, nicht entschieden:
  - Beim Archivieren einer Mail steht neben der gewählten Dateiart der Hinweis, wenn sie für eine Light-Rolle lesbar ist („wird für Sublieferanten sichtbar“).
  - „Vorschau als User“: die Artikelmappe und die Dokumente genau so, wie ein bestimmter User sie bekommt.

## Vor dem Gang ins Internet

Erst wenn alles erledigt ist, wird der Zugang von außen freigeschaltet.

- Login und Zugriff:
  - Echte 2FA per Mail für den Login von außen. Die simulierte 2FA (Bestätigungs-Token in der Antwort) ist keine Sicherheit.
  - Bremse gegen Passwort-Raten pro Konto und pro IP, dazu ein Protokoll der Anmeldungen (`login_tracker`).
  - Fehlerantworten verraten keine Interna (keine Stacktraces, Pfade oder SQL).
  - Suche in Solr: Eingaben des Users escapen, damit niemand den ACL-Filter per Query umgeht.
- Erreichbarkeit:
  - Nur nginx ist nach außen gemappt. Postgres, Solr, SFTP und der Port des Backends liegen nur im Docker-Netz (heute ist Postgres noch auf dem Host offen).
  - Der interne Login (`/api/rest/v1/public/intern/login/…`) ist nur aus dem LAN erreichbar: nginx lässt dort nur LAN-Adressen durch. Erst dann sagt die area `INTERN` im Token verlässlich, dass die Anmeldung aus dem LAN kam.
  - nginx antwortet nur auf die eigenen Hostnamen und reicht `/solr` nie durch.
  - Nach jedem Deploy von außen prüfen (z. B. übers Handynetz), dass nur der gewollte Eingang antwortet.
- Dienste und Daten:
  - Solr mit Passwort: erledigt 2026-10-03 (Basic Auth, `security.json` aus der `.env`). Zugangsdaten nur in der `.env`, nie im Repo.
  - HTTPS mit gültigem Zertifikat und HSTS, dazu ein Content-Security-Policy-Header.
  - HDD verschlüsselt (LUKS), Backups verschlüsselt, Wiederherstellen einmal getestet.
- Betrieb:
  - Updates für Pi, Docker-Images und Pakete regelmäßig einspielen.
  - Laufen weitere Dienste unter Subdomains von `pahling.de`, das CSRF-Verfahren der Vorlage wieder einbauen, weil `SameSite` Subdomains als dieselbe Site behandelt.
  - Gäste-WLAN vom LAN trennen, weil der interne Login keine 2FA hat.
- Organisatorisch: Löschkonzept (User, Protokolle, Dateien), TOMs und Verzeichnis der Verarbeitungstätigkeiten dokumentieren.

## Später

- Revisionssicherung im File-Kern, nach der Vorlage (`docs/dms-revisionen.md` dort, gebaut dort 2026-08-13). Entschieden 2026-10-02: kommt, aber erst nach dem File-Kern. Eine Änderungshistorie der Metadaten mit Zurückladen, keine GoBD.
  - Das Feld `fileIdRevisionNo` (long) bleibt dafür im Kern FILE: 0 nach dem Upload, plus eins bei jeder Änderung durch einen Menschen (Metadaten, Status, Dateiart, Freigaben). ReIndex und `downloadCount` zählen nicht. Die Zahl ist der Verweis vom Dokument auf seine Sicherungen, mehr steht nicht drin.
  - Vor jedem solchen Schreiben sichert das Backend den Stand vor der Änderung als ZIP auf dem SFTP unter `dmsBackup/Jahr/Monat/Tag/<id>_<revisionNo>.zip`: `dokument.json` mit allen stored fields (auch Volltext und Vorschau, Wiederherstellen braucht keinen Tika-Lauf) und `manifest.json`. Erst wenn das ZIP liegt, schreibt es Solr; scheitert das ZIP, scheitert die Änderung. Die Bytes der Datei sind nie dabei, neue Bytes sind ein neues Dokument der Versionskette.
  - Register in Postgres (Tabelle nach der Namensregel, z. B. `file_history`: Dokument-ID, revisionNo, Pfad relativ zur SFTP-Wurzel, User, Zeitpunkt, UNIQUE auf Dokument und Nummer). Das ZIP ist die Wahrheit, die Tabelle nur das Register.
  - Endpunkte: Liste der Revisionen, eine Revision als fertiges JSON (das Backend entpackt), Wiederherstellen ohne Rumpf nur mit id und Nummer. Wiederherstellen schreibt selbst eine neue Revision. Nicht wörtlich aus dem ZIP: Zähler, Rechte-Felder aus der Dateiart, Internetfreigabe.
  - Frontend: Box am Dokument, nur wenn es Revisionen gibt, Tabelle Feld, heute, vorher, nachher, je Revision ein Knopf „zurückladen“.
  - Endgültiges Löschen eines Dokuments räumt alle seine ZIPs und Registerzeilen mit weg.
- Mailversand über das private Hotmail-Konto mit Microsoft Graph (kein Exchange, kein Zertifikat):
  - App-Registrierung im Azure-Portal: nur persönliche Microsoft-Konten, öffentliche Clientflows an, delegierte Rechte `Mail.Send` und `offline_access`. Die Client-ID kommt in die `.env`.
  - Einmalige Anmeldung per Device-Code-Ablauf. Das Backend speichert das Refresh-Token verschlüsselt (nicht im Repo).
  - Versand per `POST /me/sendMail`, Access-Token (ca. 1 h) holt sich das Backend selbst über das Refresh-Token, z. B. mit MSAL4J.
  - Ein Systemjob frischt das Token einmal pro Woche auf, damit es die 90 Tage ohne Benutzung nie erreicht. Neu anmelden nur nach Passwortwechsel oder entzogenem Zugriff.
- E-Mail-Formatprüfung wieder einbauen, nach der Entwicklungsphase: `@Email` an `Users.email` (Backend) und `type="email"` im Formular `NewUsersForm` (Frontend).
- Navigationsverlauf in der DB: die letzten 500 URLs, die ein User geladen hat, mit Zeitstempel speichern.
- Docker-Logs angehen, sobald alle Container in der Compose sind: pro Container festlegen, welche Events er loggt und welche nicht.
