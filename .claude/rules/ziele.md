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

- SFTP: Anbindung (`system/sftp`) und Container (`app1_sftp`) sind gebaut (2026-10-02). Offen: Host-Key und `.env` auf den Pi, Container starten, erster Upload über den File-Kern.
- Solr: Container und Anbindung. Beim File-Kern ist Solr der einzige Speicher.
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
  - Solr mit Passwort. Zugangsdaten nur in der `.env`, nie im Repo.
  - HTTPS mit gültigem Zertifikat und HSTS, dazu ein Content-Security-Policy-Header.
  - HDD verschlüsselt (LUKS), Backups verschlüsselt, Wiederherstellen einmal getestet.
- Betrieb:
  - Updates für Pi, Docker-Images und Pakete regelmäßig einspielen.
  - Laufen weitere Dienste unter Subdomains von `pahling.de`, das CSRF-Verfahren der Vorlage wieder einbauen, weil `SameSite` Subdomains als dieselbe Site behandelt.
  - Gäste-WLAN vom LAN trennen, weil der interne Login keine 2FA hat.
- Organisatorisch: Löschkonzept (User, Protokolle, Dateien), TOMs und Verzeichnis der Verarbeitungstätigkeiten dokumentieren.

## Später

- Mailversand über das private Hotmail-Konto mit Microsoft Graph (kein Exchange, kein Zertifikat):
  - App-Registrierung im Azure-Portal: nur persönliche Microsoft-Konten, öffentliche Clientflows an, delegierte Rechte `Mail.Send` und `offline_access`. Die Client-ID kommt in die `.env`.
  - Einmalige Anmeldung per Device-Code-Ablauf. Das Backend speichert das Refresh-Token verschlüsselt (nicht im Repo).
  - Versand per `POST /me/sendMail`, Access-Token (ca. 1 h) holt sich das Backend selbst über das Refresh-Token, z. B. mit MSAL4J.
  - Ein Systemjob frischt das Token einmal pro Woche auf, damit es die 90 Tage ohne Benutzung nie erreicht. Neu anmelden nur nach Passwortwechsel oder entzogenem Zugriff.
- E-Mail-Formatprüfung wieder einbauen, nach der Entwicklungsphase: `@Email` an `Users.email` (Backend) und `type="email"` im Formular `NewUsersForm` (Frontend).
- Navigationsverlauf in der DB: die letzten 500 URLs, die ein User geladen hat, mit Zeitstempel speichern.
- Docker-Logs angehen, sobald alle Container in der Compose sind: pro Container festlegen, welche Events er loggt und welche nicht.
