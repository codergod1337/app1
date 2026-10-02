# Erkenntnisse aus der Vorlage

Zwischenstand vom 2026-09-30, damit die Vorlage nicht jedes Mal neu durchsucht werden muss.
Die Vorlage kann sich seitdem geändert haben: im Zweifel dort nachsehen.

- Vorlage: `../../hobby/home-plus-extern/intern-instance/` (relativ zu diesem Repo)
- Java-Code dort unter `backend/src/main/java/de/pahling/home/`, unten abgekürzt mit `…/`
- Doku dort unter `docs/` (u. a. `login-api.md`, `dms-revisionen.md`, `dms-fsc-aenderungen.md`, `dms-erstelldatum.md`)

Pro Thema: was die Vorlage macht, wo sie Schwächen hat, und was für app1 entschieden oder nur vorgeschlagen ist.

## Entschieden für app1

- Solr ist beim File-Kern der einzige Speicher der Metadaten, wie in der Vorlage. Keine Kopie in Postgres.
- Endpunkte und Aufteilung Controller/Service: siehe `rules/architektur.md`.

## User

Vorlage, `…/sys/user/model/`:
- `Users`: guid, email (kleingeschrieben, dient als Login), username, vorname, nachname, created_at, info und profil_text (mehrsprachiges JSON), dienstkonto.
- `UsersCredentials`: password_hash, updated_at, password_expires_at (ohne Wirkung), users_status (`ACTIVE`, `UNACTIVATED`, `TEMPORARILY_BLOCKED`). Kein Leseweg nach außen.
- `UsersDetails`: Kontaktfelder, kunden_nummer/lieferanten_nummer, pw_unsuccessfull (Fehlversuche).
- `UsersSetting`: users_guid + key + value.

## AccessRole (AR) und AccessRoleCollection (ARC)

Vorlage, `…/sys/access/model/`, keine JPA-Beziehungen:
- `AccessRole` (`access_role`): PK `key` (String, nicht änderbar, nur A–Z, 0–9, `_`, max. 200, geprüft in `…/sys/key/KeyFormat`), display_name und description (mehrsprachiges JSON), `system_managed` (nicht löschbar, gesetzt genau dann, wenn das Backend den Key fest im Code prüft), Farben, show_in_dropdown, listing_position.
- `AccessRoleCollection` (`access_role_collection`): PK `key` (Präfix `ARC_`), `access_role_keys` (JSON-Array als Text), slave_arc_keys (Vertretung), organigramm_parent_key, Farben, Position.
- `AccessRoleUsersAssignment`: users_guid, access_role_key, assigned_at, eindeutig pro Paar.
- `AccessRoleCollectionUsersAssignment`: users_guid, access_role_collection_key, assigned_at, eindeutig pro Paar.

Ablauf in der Vorlage:
- AR und ARC werden nur beim Ausstellen des Tokens aufgelöst: `LoginService.doIssueJwt` → `AccessRoleCollectionService.systemGetEffectiveAccessRoleKeys` → flache Liste im Claim `roles`. ARC landen nie im Token.
- Geprüft wird über Pfadregeln in `…/sys/security/SecurityConfig` (jeder Eintrag in `roles` wird zu `ROLE_<key>`, dann `hasRole`) und im Code über `CurrentUsersProvider.currentUsersAccessRoleKeys()`. Kein `@PreAuthorize`.
- Admin-Endpunkte: `/api/rest/admin/accessrole`, `…/accessrolecollection`, `…/accessroleusersassignment`, `…/accessrolecollectionusersassignment`.
- Startdaten: `…/sys/bootstrap/DataInitializer` (ApplicationRunner, legt nur Fehlendes an).
- Namensregel für Service-Methoden: `admin…` (ohne Filter), `user…` (Rechte fest eingebaut), `system…` (ohne Sitzung). Wird nicht erzwungen, mehrere Fehler der Vorlage entstehen genau dort.

Schwächen der Vorlage:
- Startdaten legen den Admin `alice` mit Passwort `123` an.
- Rechtevergaben werden nicht protokolliert (wer, wann).
- Rollen- und ARC-Katalog sind anonym lesbar.

## Login und JWT

Vorlage:
- `spring-boot-starter-oauth2-resource-server` (Nimbus). `…/sys/security/JwtConfig`: RSA-2048, bei jedem Start neu im Speicher erzeugt, RS256, kein `iss`/`aud`.
- Claims: `sub` (User-guid), `iat`, `exp`, `roles`, `session` (Platz 1–3), `bereich` (`INTERN`/`EXTERN`).
- Access-Token 31 Minuten. Refresh-Token: 32 Byte Zufall, in `users_refresh_token` nur als SHA-256, wechselt bei jedem Refresh (`familyId`, Wiederverwendung sperrt alle Ketten des Users), 14 Tage, max. 3 Sitzungen, liest Status und Rollen beim Refresh neu.
- Cookies, alle httpOnly außer CSRF: `home_jwt`, `home_refresh` (Pfad `/api/rest/public`), `home_csrf` (lesbar, Double-Submit über `…/sys/security/CsrfProtectionFilter` und Header `X-CSRF-Token`). Extern: `home_extern_jwt`, `home_extern_refresh`, SameSite Strict.
- Frontend `frontend/src/auth/AuthProvider.jsx`: Status über `GET /api/rest/session`, Refresh bei 2/3 der Laufzeit, nur einer gleichzeitig.
- Filterketten: `…/extern/security/ExternSecurityConfig` (`@Order(1)`, `/api/rest/extern/**`, verlangt `BEREICH_EXTERN`), `…/intern/dienstkonto/security/DienstkontoSecurityConfig` (`@Order(2)`, Login nur von festgelegten IPs, 24-h-Token), `…/sys/security/SecurityConfig` (alles andere, STATELESS, am Ende `denyAll`).
- 2FA: start → confirm → claim (`users_login_2fa`), Notausgang `users_loginskip`, Drosselung `login_tracker`.
- Passwort: Browser bildet SHA-256, Server speichert BCrypt darüber.

Schwächen der Vorlage:
- Die interne Kette prüft `bereich` nicht: ein externes Token gilt auch intern.
- 2FA wirkungslos, `start` liefert den Bestätigungs-Token direkt mit.
- `X-Real-IP` wird ungeprüft übernommen, der Backend-Port ist offen: IP-Freigaben lassen sich umgehen.
- Secrets im Repo (Azure-Client-Secret im Code, SFTP-Passwort in `application.yaml`).
- SHA-256 im Browser ist faktisch das Passwort (Pass-the-hash), Passwortstärke nicht prüfbar.
- Access-Token nicht widerrufbar. Dienstkonto-Login ohne Bremse. Interne Drosselung systemweit 1 s, ein Client kann alle Logins blockieren.

Vorschläge für app1, noch nicht entschieden:
- Passwort im Klartext über HTTPS, Hash erst auf dem Server (Argon2id).
- Jede Filterkette prüft ihre area streng.
- Transparenz: User sieht eigene Sitzungen und Anmeldeversuche und kann Sitzungen beenden.
- Bremse gegen Passwort-Raten pro Konto und pro IP.
- Kein Admin mit festem Passwort im Code.
- Offen: Wer darf welche area bekommen? Dienstkonten als eigene area?

## Compose der Vorlage

`intern-instance/docker-compose.yml`:
- Profile `dev` (nur Infrastruktur), `deploy` (dazu Frontend und Backend), `live` (wie deploy, SFTP übernimmt das NAS).
- Frontend: Build node → nginx, nginx leitet `/api/` an das Backend weiter (`client_max_body_size 5g`, `proxy_request_buffering off`, Timeout 1800 s).
- Backend: Build temurin JDK → JRE alpine, `.env` als Datei eingebunden, Multipart-Zwischenablage als Volume.
- Netzwerk-Aliase ohne Unterstrich (`solrhost`), weil `java.net.URI` keine Unterstriche im Hostnamen akzeptiert. Betrifft auch app1, die Container heißen `app1_<dienst>`.

## SFTP

Vorlage:
- Container `atmoz/sftp:latest` (nur im Profil dev), Port 22, Volume auf `/home/<user>/sftproot`, Benutzer und Passwort fest im Compose.
- Backend-Paket `…/sftp/`, Library JSch-Fork `com.github.mwiede:jsch`:
  - `SftpConnectionPool`: faire Semaphore global und je User, eine SSH-Session je Kanal, `stat(root)` als Lebenstest beim Ausleihen, Vorwärmen im Daemon-Thread (Start blockiert nicht, wenn der Server fehlt).
  - `PooledSftpChannel` (AutoCloseable, gibt beim Schließen an den Pool zurück).
  - `SftpService`: `user…`/`system…` für write, read, exists, delete. Legt Ordner Ebene für Ebene an, alles als Stream.
  - `SftpPathService`: Dateien unter `JJJJ/MM/TT[_n]/<sha256>.bin`, Revisionen unter `dmsBackup/JJJJ/MM/TT/<id>_<rev>.zip`.
  - Konfiguration `gebieter.sftp.*`: max. 12 Verbindungen, 2 je User, Timeouts 15 s / 600 s / 30 s.
  - Fehler `SftpUnavailableException`, `SftpPerUserLimitException`, übersetzt in `…/sys/error/GlobalExceptionHandler`.

Schwächen der Vorlage: Passwort im Repo, Host-Key-Prüfung aus, Image nicht gepinnt, `put` schreibt direkt ans Ziel (abgebrochener Upload hinterlässt eine halbe Datei unter dem Hash-Namen), `system…`-Weg nur per Konvention vor Controllern geschützt, `stat` bei jedem Ausleihen und je Elternordner.

Entschieden für app1 (2026-10-02), gebaut in `system/sftp`:
- Host-Key-Pinning über `sftp_host_key` in der `.env`, Passwort oder Schlüssel (`sftp_private_key_path`), nichts davon im Repo.
- Ablage wie die Vorlage nach Datum `JJJJ/MM/TT[_n]/<sha256>.bin` mit `max-files-per-directory`, Zähler bewusst nur im Speicher.
- Schreiben über `.part` mit Größenprüfung und Umbenennen. Ein Weg mit bekanntem Hash, einer, der den Hash beim Streamen mitrechnet.
- Keine `user…`/`system…`-Methodenpaare: Der Pool fragt den `CurrentUsersProvider`, ob ein Mensch angemeldet ist.
- NAS-Wechsel: nur `sftp_host` und `sftp_host_key` ändern, `root-dir` steht in der `application.yml`.

## Solr

Vorlage:
- `solr/Dockerfile`: `FROM solr:<version>`, kopiert `configsets/<kern>/conf/{managed-schema.xml,solrconfig.xml}` ins Image.
- Compose-`command`: `precreate-core` je Kern, danach `managed-schema.xml` bei jedem Start drüberkopieren, dann `solr-foreground --user-managed` (ohne das startet Solr 10 im Cloud-Modus). `SOLR_HEAP`, `mem_limit`.
- `solrconfig.xml`: `ManagedIndexSchemaFactory mutable=false`, autoCommit 15 s, autoSoftCommit 5 s.
- Backend: SolrJ mit `solr-solrj-jetty` und `HttpJettySolrClient` (JDK-Client wegen SOLR-17707 gemieden), je Kern eine Bean in `…/solr/SolrConfig`. `DmsSchemaPruefung` gleicht beim Start die Feldkonstanten mit der Schema-API ab.
- Kerne: `dms` und `nachrichten` als einziger Speicher, `artikel` und `kontakte` als Spiegel aus einem Fremdsystem.

Schwächen der Vorlage:
- Solr ohne Passwort bei offenem Port.
- Schreiben per Lesen-Ändern-Schreiben ohne `_version_`: parallele Änderungen überschreiben sich.
- Base64-Vorschaubild geht mit jeder Trefferliste mit.

Vorschläge für app1, noch nicht entschieden:
- Solr mit Passwort.
- `_version_` für Optimistic Locking nutzen.

## File-Kern (in der Vorlage: DMS)

Vorlage, Solr-Kern `dms`, Feldnamen in `…/dms/service/DmsFelder`:
- `id` = SHA-256 der Bytes, `guid` = Versionskette, `version`, `fileIdRevisionNo`.
- `status`: ACTIVE, ARCHIVED, PENDING_ADMIN, DELETED, REINDEX_ERROR.
- uploader, changingUsers, Zeitstempel, downloadCount.
- fileName (normalisiert), fileExtension, mimeType, fileSizeBytes, sftpPath.
- fileSubClassKey, isPublic, readAcl/writeAcl (`GROUP:<ar-key>`), userReadAcl (User-guids), Flags tikaSearch/ocr/ki.
- fileDescription (mehrsprachig), bildVorschauBase64Png, searchableWords, documentLanguage.
- Hook-Felder als Verweise auf Fachobjekte (artikelNummer, kundenNummer, relatedFileDataIds …), in allen Kernen gleich benannt; `*Suggested` mit EdgeNGram für Vorschläge beim Tippen.

Postgres in der Vorlage:
- `FileSubClass` (Dateiart): key, Lese- und Schreib-AR, erlaubte Endungen, Flags.
- `FileExtension`, `FileExtensionCollection`.
- `DmsHistory` (dms_id, revision_no, backup_path), `DmsDocReindexQueue`.

Ablauf Hochladen, `…/dms/service/FileDataService.doUploadFileData`:
1. Dateiart laden, Schreibrecht prüfen, Endung gegen die Erlaubnisliste prüfen.
2. SHA-256 beim Lesen berechnen. Gibt es die Bytes schon, werden die Angaben zusammengeführt statt neu angelegt.
3. Bytes auf den SFTP.
4. Vorschaubild und Tika-Text (`TextAufbereitungService`: deutsches Stemming, Komposita), Sprache, Erstelldatum.
5. In Solr schreiben, Rechte aus der Dateiart ins Dokument kopieren.
6. Vorgängerversion auf ARCHIVED, Soft-Commit.

Weitere Abläufe in der Vorlage:
- Download: `StreamingResponseBody` → `SftpService` → `transferTo`. „Gibt es nicht“ und „darfst du nicht“ sind dieselbe Antwort.
- Rechte: `…/dms/service/DmsAclsToFqFilter` baut `(isPublic:true OR readAcl:("GROUP:A" …))` plus Status-Filter.
- Revisionen: `…/dms/history/service/DmsRevisionService` sichert vor jeder Änderung durch einen Menschen ein Zip (`dokument.json`, `manifest.json`) auf den SFTP. Wiederherstellen ist möglich.
- Löschen: normal nur Status DELETED. Hart löschen nur als Admin (Zips, dann Solr, dann Datei).
- Suche `…/dms/service/DmsSolrSearchService`: edismax, Gewichtung nach Feldern, neuere Dokumente weiter oben, Highlighting als Treffergrund.
- Sicherung: `DmsExportService`/`DmsImportService` (JSONL im Zip).

Schwächen der Vorlage:
- `id` = Hash: dieselbe Datei kann nur einmal im System vorkommen, Verweise zeigen auf eine Version statt auf das Dokument.
- Der Download-Zähler schreibt das ganze Dokument neu und kann parallele Änderungen überschreiben.
- Originaldateiname geht durch die Normalisierung verloren.
- Tika läuft während des Hochladens ohne Größen- und Zeitlimit.
- Kein Zusammenhalt zwischen SFTP, Solr und Postgres: bei Fehlern bleiben verwaiste Dateien.
- Hook-Felder müssen an mehreren Stellen gepflegt werden (Schema, `DmsFelder`, Import, andere Kerne).
- Rechte sind ins Dokument kopiert: Änderungen an der Dateiart brauchen einen manuellen Reindex.
- Hartes Löschen prüft nicht, ob andere Features noch auf das Dokument verweisen.
- Der öffentliche existencecheck gibt bei öffentlichen Dokumenten alles heraus, auch Volltext und Pfad.

Vorschläge für app1, noch nicht entschieden:
- Eigene Dokument-ID, getrennt vom Hash der Bytes.
- Allgemeine Verknüpfung mit anderen Features, damit neue Features am File-Kern nichts ändern müssen.
- Tika im Hintergrund statt beim Hochladen.
- Originaldateinamen behalten.
- Weil Solr der einzige Speicher ist: Export als Sicherung von Anfang an.
