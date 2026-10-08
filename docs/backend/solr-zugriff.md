Lesbar machen in VS Code: `Strg+Shift+V` (Vorschau), `Strg+K V` (Vorschau daneben).

# Zugriff auf die Solr-Kerne: das Register

Wer liest und wer schreibt in einen Kern, je Vorgang eine Zeile. Die Regel dazu steht in `.claude/rules/architektur.md`, Abschnitte „Solr-Kerne, lesend“ und „Solr-Kerne, schreibend“ (Vorgabe 2026-10-07). Jeder neue Weg zu einem Kern steht hier, bevor er gebaut wird.

## Wer überhaupt mit Solr spricht

Genau ein Client-Halter, `codergod1337.app1.solr.client.SolrClientRegistry`. Zwei Klassen holen sich dort einen Client:

| Klasse | Was | Prüfung |
|---|---|---|
| `codergod1337.app1.solr.document.service.SolrDocumentService` | die rohe Schicht für Dokumente: `queryDocumentsPage`, `writeNewDocument`, `atomicUpdateDocument`, `atomicUpdateManyDocuments`. Rufen dürfen sie nur die Services je Kern, die unten stehen | keine, mit Absicht |
| `codergod1337.app1.solr.sync.SolrSchemaSyncService` | nur Schema-API und Config-API (Felder, Zwillinge, copyFields, `query.maxBooleanClauses`), nie Dokumente, beim Start und auf `POST /api/rest/v1/admin/solr/sync` | ADMIN |

Kein hartes Löschen. Gelöscht wird weich über den Status des Kerns. Es gibt keinen gemeinsamen Lese- oder Schreibservice für alle Kerne: Jeder Kern baut seine Abfragen und Updates selbst. Kernunabhängig sind nur die Rechterechnung aus dem Token, `codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService`, und die Zerlegung des Suchtexts, `codergod1337.app1.solr.document.model.SolrSearchText` (`+` Pflicht, `-` Ausschluss, alles andere escaped, höchstens sechs Wörter); beide sprechen nie mit Solr.

## Kern DMS

Kern-Key `DMS`, Rollen `DMS`, `DMS_FULL`, `DMS_SUCHE`, dazu `HOOK_FULL`. Dokumentgrenze: readAcl oder userGrantedAccess. Statusgrenze (User 2026-10-08): jede User-Abfrage trägt `status:(ACTIVE OR ARCHIVED)` (`DmsStateService.VISIBLE_STATUS_FILTER_QUERY`), lesend wie beim Schreibcheck, auch beim Auflösen über die Versionskette; ein gelöschtes Dokument gibt es für User auf keinem Weg. Nur der Existenzcheck läuft ohne. DELETED und PENDING_ADMIN sieht der Admin im DMS-Manager. Die Feldnamen, mit denen das Backend rechnet, stehen in `codergod1337.app1.dms.model.DmsSolrDocumentFieldNames`. Die Formprüfung macht `codergod1337.app1.dms.service.DmsSolrDocumentChecker` gegen die Felddefinition des Kerns DMS, ohne Solr.

### Lesen, drei Services

| Vorgang | Methode | Endpunkt | Tür | fq | Felder |
|---|---|---|---|---|---|
| Existenzcheck | `codergod1337.app1.dms.service.DmsSolrDocumentExistenceService.loadDmsSolrDocumentExistenceCheck` | `GET /dms/exists/{sha256}` | angemeldet | keins | id, Dateiart, Status |
| Rechte des Users | `codergod1337.app1.dms.service.DmsExactFieldValueResolveService.currentUserDmsAccessForClient` | `GET /dms/access` | DMS | kein Solr | |
| Auflösen-Tab | `…DmsExactFieldValueResolveService.resolveDmsSolrDocumentsPageForResolveTab` | `POST /dms/resolve` | DMS, Auflöserechte je Feld | Lese-fq | Feldgrenze plus `_version_` als Text |
| Dokumentseite | `…loadDmsSolrDocumentForDocumentPage` | `GET /dms/solr-document/{id}` | DMS | Lese-fq, dazu writeAcl für das Flag | Feldgrenze |
| Antwort des Uploads | `…loadDmsSolrDocumentForUploadAnswer` | in `POST /dms/upload` | DMS | Lese-fq | Feldgrenze, null wenn unsichtbar |
| Download | `…loadDmsSolrDocumentFieldsForDownload` | `GET /dms/download/{id}` | DMS | Lese-fq | sftpPath, fileName, mimeType, fileSizeBytes, nie zum Client |
| Vor dem Schreiben, der Schreibcheck | `…loadDmsSolrDocumentFieldsForWrite` | intern | DMS | allein writeAcl | genannte Felder |
| Vor einem revisionierten Schreiben | `…loadWholeDmsSolrDocumentForWrite` | intern | DMS | allein writeAcl | alle stored fields, der Stand fürs ZIP und für den Vergleich, was sich wirklich ändert, nie zum Client |
| Revisionsliste | `codergod1337.app1.dms.service.DmsSolrDocumentRevisionService.loadDmsSolrDocumentRevisions` | `GET /dms/solr-document/{id}/revisions` | DMS, Tor ist die Dokumentseite (Lese-fq) | nur das Tor | das Register aus Postgres, kein ZIP |
| Eine Revision | `…DmsSolrDocumentRevisionService.loadDmsSolrDocumentRevision` | `GET /dms/solr-document/{id}/revision/{revisionNo}` | DMS, dasselbe Tor | nur das Tor | `document.json` aus dem ZIP, auf die Feldgrenze des Users geschnitten |
| Glieder einer Kette | `…loadDmsVersionChainMembers` | intern, neue Fassung und Dateiartwechsel | DMS | Lese-fq, Bedingung versionChain vom Backend | version, status, Dateiart |
| Versionskette für den Client | `…resolveDmsVersionChainForClient` | `GET /dms/version-chain/{versionChain}` | DMS, keine Such-Rolle, ein Auflösen | Lese-fq, Bedingung versionChain vom Backend | Feldgrenze plus `_version_` als Text, aufsteigend nach version, wenn der User das Feld lesen darf; der Fahrplan und die Maske der neuen Fassung |
| Meine Freigaben | `…resolveDmsSolrDocumentsPageForMyGrants` | `POST /dms/my-grants/documents` | DMS | Lese-fq, Bedingung eigene guid vom Backend | Feldgrenze |
| ids vor einem Update vieler | `…resolveWritableDmsSolrDocumentIdsAmong` | intern | DMS | allein writeAcl | nur ids |
| Kontrollblick | `…loadGranteesOfDmsSolrDocument` | `GET /dms/solr-document/{id}/grants` | ADMIN | Lese-fq | userGrantedAccess |
| Freigabe-Vorschau | `…resolveWritableDmsSolrDocumentsPageForDocumentGrantPreview` | `POST /admin/dms/grant/document/preview` | ADMIN, Auflöserechte je Feld | allein writeAcl | Feldgrenze |
| Freigabe setzen, ids | `…resolveWritableDmsSolrDocumentIdsForDocumentGrant` | in `POST /admin/dms/grant/document` | ADMIN, Auflöserechte je Feld | allein writeAcl | nur ids, weniger als 500 |
| Entzug und Ablauf, ids | `…resolveWritableDmsSolrDocumentIdsCarryingGrantee` | in `DELETE` und `POST …/expire` | ADMIN | allein writeAcl, Bedingungen aus dem Register plus userGrantedAccess gleich Empfänger, beides vom Backend | nur ids |
| Suche | `codergod1337.app1.dms.service.DmsSearchService.searchDmsSolrDocumentsPage` | `POST /dms/search` | DMS und DMS_SUCHE, auch für den Admin | Lese-fq, Status ACTIVE oder ARCHIVED, dazu die Filter des Clients aus der festen Liste (Hooks, `EXACT_FILTER_FIELD_NAMES`) und Zeiträume (`DATE_RANGE_FILTER_FIELD_NAMES`), fremde Felder überlesen | alles außer searchableWords plus `_version_` als Text, je Treffer der Treffergrund (Felder und Wörter, Volltext nur als Feld). edismax über id, fileName, fileIndex, fileDescription, searchableWords, jeden Hook und die Suggested-Zwillinge, Recency-Boost; ohne Suchwort und Filter keine Abfrage, sondern leere Seite mit Hinweis; höchstens 100 je Seite, 25 Seiten |
| Vervollständigung beim Tippen | `…DmsSearchService.suggestDmsSolrDocuments` | `POST /dms/search/suggest` | DMS und DMS_SUCHE | dieselben fq wie die Suche | kompakte Felder plus Hooks, 15 Treffer über die Suggested-Zwillinge und searchableWords, kein Highlighting; dazu bis zu zehn Filterangebote aus den Treffern (Hook-Werte und fileIndex als Ganzwert-Präfix, fileName als Wortanfang). Nicht suchbar heißt stumm leer |

### Schreiben, ein Service

Alle über `codergod1337.app1.dms.service.DmsSolrDocumentWriteService`, je Vorgang eine Methode, Enum `codergod1337.app1.dms.model.DmsSolrDocumentWriteProcess`, der Name steht mit current user, id und Feldnamen im Log. Jeder Vorgang eines Menschen: changingUsers ADD, lastChangedAt SET. readAcl und writeAcl genau dann, wenn fileSubClassKey geschrieben wird, aus der Dateiart in Postgres. `_version_` geht nie mit. Felder des Clients laufen durch die Schreibregel je Feld (`solr_field.clientWriteRule`), was er nicht darf, wird ignoriert. Die Internetfreigabe `isPublic` setzt und ändert nur, wer die AR `DMS_PUBLIC` hat (User 2026-10-08): beim Anlegen ist `true` ohne die Rolle 403, beim Bearbeiten jede Änderung, beim Zurückladen bleibt das Feld ohne die Rolle, wie es heute ist. Ob ein Vorgang revisioniert wird (Spalte „Revision“), steht am Enum-Wert, beide Update-Methoden fragen ihn: Nur Änderungen, die gegen den Stand davor wirklich etwas ändern, bleiben (der Stempel changingUsers und lastChangedAt zählt nicht; bleibt nichts, wird weder gesichert noch geschrieben), davor der ganze Stand als ZIP mit genau diesen Feldnamen im Register, fileIdRevisionNo INC im selben Update.

| Vorgang | Methode | Endpunkt | Prüfung davor | Geschrieben | Revision |
|---|---|---|---|---|---|
| Upload neu | `createDmsSolrDocumentFromUpload` | `POST /dms/upload` | Existenzcheck leer, Dateiart Pflicht, Endung freigeschaltet, Schreibrolle der Dateiart | volles Dokument, ACLs aus der Dateiart, versionChain neu, version 1, fileIdRevisionNo 0 | nein, kein Vorzustand |
| Merge | `mergeHookValuesFromRepeatedUpload` | `POST /dms/upload` | gleiche Dateiart, Status ACTIVE oder ARCHIVED, writeAcl, passende versionChain und fileIndex | Hooks ADD, Beschreibung je Sprache angehängt, nur wenn etwas dazukam | ja |
| Neue Fassung | `createNextVersionInVersionChain` | `POST /dms/upload` mit versionChain im JSON, ein Parameter des Uploads, kein Feld des Clients | Kette sichtbar, Dateiart vom Kopf (schon in der Vorarbeit des Uploads, damit die Endungsprüfung dagegen läuft), Endung, Schreibrolle, jedes sichtbare ACTIVE-Glied schreibbar (sonst 403, bevor etwas geschrieben ist); dieselben Bytes sind EXISTS, kein Merge, eine neue Fassung braucht neue Bytes (User 2026-10-08) | volles Dokument mit version plus eins; dann je ACTIVE-Glied status SET ARCHIVED (`DMS_NEW_VERSION_ARCHIVE_PREVIOUS`) | Anlegen nein, Archivieren je Glied ja |
| Bearbeiten | `editDmsSolrDocument` | `PATCH /dms/solr-document` | writeAcl, Regeln je Feld | nur die geschickten Felder; bei Dateiartwechsel fileSubClassKey SET und ACLs SET_ALL, auch in jedem anderen Glied der Kette (`DMS_EDIT_FILE_SUB_CLASS_OF_CHAIN`, je Glied einzeln) | ja, je Glied eigenes ZIP |
| Soft-Delete | `softDeleteDmsSolrDocument` | `DELETE /dms/solr-document` | writeAcl | status SET DELETED, danach für User unsichtbar, keine Antwort | ja |
| Zurückladen | `restoreDmsSolrDocumentRevision` | `POST /dms/solr-document/revision/restore`, Body `{id, revisionNo}` | writeAcl, Revision im Register (404 sonst) | jedes Feld aus dem ZIP wörtlich, SET oder SET_ALL, ein heute vorhandenes Feld ohne Wert im ZIP raus; nicht id, `_version_`, fileIdRevisionNo; changingUsers aus dem ZIP plus Wiederhersteller am Ende, lastChangedAt jetzt; unbekannte Felder übersprungen und geloggt. Antwort null, wenn der User das Dokument danach nicht mehr sehen darf | ja, der heutige Stand davor |
| Download-Zähler | `countDownloadOfDmsSolrDocument` | nach `GET /dms/download/{id}`, auch der Viewer | die Leseprüfung des Downloads, kein writeAcl-Check: wer lesen darf, lädt | downloadCount INC 1, ohne changingUsers und lastChangedAt; scheitert Solr, nur ein Log, die Bytes sind schon beim Client | nein, Buchhaltung |
| Freigabe setzen | `addGranteeToDmsSolrDocuments` | in `POST /admin/dms/grant/document` | ADMIN, ids noch einmal gegen writeAcl | userGrantedAccess ADD guid | nein, eigenes Register |
| Entzug, Ablauf | `removeGranteeFromDmsSolrDocuments` | in `DELETE` und `POST …/expire` | ADMIN, ids noch einmal gegen writeAcl | userGrantedAccess REMOVE guid. Was der current user nicht schreiben darf, behält die guid, steht im Log | nein |

Nicht geschrieben wird je: ein vorhandenes Dokument voll neu, sftpPath, fileSizeBytes, versionChain nach dem Anlegen, `_version_`.

### Revision

`codergod1337.app1.dms.service.DmsSolrDocumentRevisionService` (gebaut 2026-10-08 nach dem DmsRevisionService der Vorlage) schreibt nie in Solr: Er sichert den Stand vor einem revisionierten Schreiben, liest Sicherungen zurück und räumt sie weg. Der Schreibservice ruft ihn, nie umgekehrt.

- ZIP auf dem SFTP unter `dmsBackup/JJJJ/MM/TT[_n]/<id>_<revisionNo>.zip` (Tagesordner wie bei den Dateien, `SftpPathService.relativePathForFileName`): `document.json` mit jedem stored field des Stands davor, auch Volltext und Vorschaubild, ohne `_version_`; `manifest.json` mit Dokument-id, Nummer, User, Zeitpunkt und den geänderten Feldern. Zeitpunkte als ISO-8601-Text.
- Register `dms_solr_document_revision` (`codergod1337.app1.dms.model.DmsSolrDocumentRevision`): documentId, revisionNo, backupPath, changedByUsersGuid, changedAt, changedFieldNames (nur Felder, deren Wert sich wirklich geändert hat, ohne Stempel), UNIQUE auf (documentId, revisionNo). Die Zeile ist das Register, das ZIP die Wahrheit. Die Nummer ist die des gesicherten Stands, 0 heißt: so hochgeladen. Kein `_version_` (User 2026-10-08, es sagt nichts).
- Reihenfolge: ZIP, Registerzeile (gleiche Nummer ersetzt), Solr. Scheitert das ZIP, scheitert die Änderung. `_version_` lässt sich nie zurücksetzen, Solr vergibt es bei jedem Schreiben selbst, ein mitgeschickter Wert wäre eine Bedingung (409), beim Zurückladen wird es ignoriert.
- Lesen: Liste und eine Revision, Tor ist die Dokumentseite (Lese-fq am Dokument), die ACLs im ZIP sind Daten, nie eine Erlaubnis; die Revision kommt auf die Feldgrenze des Users geschnitten.
- `deleteAllRevisionsOfDocument` räumt ZIPs und Zeilen weg, für das endgültige Löschen, das es noch nicht gibt.

### Dateifreigabe des DMS

`codergod1337.app1.dms.service.DmsDocumentGrantService` orchestriert Vorschau, Freigeben, Entziehen und Ablauf für DMS-Dokumente: Lesen über die Lese-Tür, Schreiben über den Schreibservice, die Registerzeile über `codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService`. Endpunkte `codergod1337.app1.dms.controller.DmsDocumentGrantAdminController` unter `/api/rest/v1/admin/dms/grant/document` (`POST /preview`, `POST`, `DELETE`, `POST /expire`).

## Nur Postgres, kein Solr

| Klasse | Was |
|---|---|
| `codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService` | das Register der Dateifreigaben aller Kerne, Zeilen anlegen und Status wechseln. `GET /admin/solr/grant/document` |
| `codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService` | Feldfreigaben je Kern, nur normale Felder. `/admin/solr/grant/field` |
| `codergod1337.app1.solr.grant.service.SolrHookGrantAccessService` | Hook-Freigaben, serverweit. `/admin/solr/grant/hook`. `POST /admin/solr/grant/expire` räumt Hook- und Feldfreigaben |
| `codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService` | rechnet je Request aus Token und Kopien im Speicher: lesbare Felder, auflösbare Felder, Lese-fq, Schreib-fq. Hooks auflösbar mit HOOK_FULL oder Hook-Freigabe, Felder mit Feldfreigabe oder `<KERN>_SUCHE` |

## Offen

- Keine Admin-Suche (User 2026-10-07); später ein eng abgegrenztes Admin-Auflösen ohne Rechteprüfung, nur für konkrete Werte.
- System-User mit Default in der Schreib-ACL jeder Dateiart, Ablauf der Freigaben als Systemjob, öffentlicher Gast-Upload mit festem Service-Account.
- Durchläufe über viele Dokumente (Reindex, ACL-Nachzug nach geänderter Dateiart, Ketten-Reparatur) als Backend-Prozess mit Statusmonitor.
- Endgültiges Löschen eines Dokuments (Admin), räumt seine Revisionen mit weg.
- Kern ARTIKEL: drei eigene Lese-Services, ein Schreibservice, ein Freigabe-Service, derselbe Aufbau.
