Lesbar machen in VS Code: `Strg+Shift+V` (Vorschau), `Strg+K V` (Vorschau daneben).

# Zugriff auf die Solr-Kerne: das Register

Wer liest und wer schreibt in einen Kern, je Vorgang eine Zeile. Die Regel dazu steht in `.claude/rules/architektur.md`, Abschnitte „Solr-Kerne, lesend“ und „Solr-Kerne, schreibend“ (Vorgabe 2026-10-07). Jeder neue Weg zu einem Kern steht hier, bevor er gebaut wird.

## Wer überhaupt mit Solr spricht

Genau ein Client-Halter, `codergod1337.app1.solr.client.SolrClientRegistry`. Zwei Klassen holen sich dort einen Client:

| Klasse | Was | Prüfung |
|---|---|---|
| `codergod1337.app1.solr.document.service.SolrDocumentService` | die rohe Schicht für Dokumente: `queryDocumentsPage`, `writeNewDocument`, `atomicUpdateDocument`, `atomicUpdateManyDocuments`. Rufen dürfen sie nur die Services je Kern, die unten stehen | keine, mit Absicht |
| `codergod1337.app1.solr.sync.SolrSchemaSyncService` | nur Schema-API, nie Dokumente, beim Start und auf `POST /api/rest/v1/admin/solr/sync` | ADMIN |

Kein hartes Löschen. Gelöscht wird weich über den Status des Kerns. Es gibt keinen gemeinsamen Lese- oder Schreibservice für alle Kerne: Jeder Kern baut seine Abfragen und Updates selbst. Kernunabhängig sind nur die Rechterechnung aus dem Token, `codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService`, und die Zerlegung des Suchtexts, `codergod1337.app1.solr.document.model.SolrSearchText` (`+` Pflicht, `-` Ausschluss, alles andere escaped, höchstens sechs Wörter); beide sprechen nie mit Solr.

## Kern DMS

Kern-Key `DMS`, Rollen `DMS`, `DMS_FULL`, `DMS_SUCHE`, dazu `HOOK_FULL`. Dokumentgrenze: readAcl oder userGrantedAccess. Die Feldnamen, mit denen das Backend rechnet, stehen in `codergod1337.app1.dms.model.DmsSolrDocumentFieldNames`. Die Formprüfung macht `codergod1337.app1.dms.service.DmsSolrDocumentChecker` gegen die Felddefinition des Kerns DMS, ohne Solr.

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
| Glieder einer Kette | `…loadDmsVersionChainMembers` | intern, neue Fassung und Dateiartwechsel | DMS | Lese-fq, Bedingung versionChain vom Backend | version, status, Dateiart |
| Meine Freigaben | `…resolveDmsSolrDocumentsPageForMyGrants` | `POST /dms/my-grants/documents` | DMS | Lese-fq, Bedingung eigene guid vom Backend | Feldgrenze |
| ids vor einem Update vieler | `…resolveWritableDmsSolrDocumentIdsAmong` | intern | DMS | allein writeAcl | nur ids |
| Kontrollblick | `…loadGranteesOfDmsSolrDocument` | `GET /dms/solr-document/{id}/grants` | ADMIN | Lese-fq | userGrantedAccess |
| Freigabe-Vorschau | `…resolveWritableDmsSolrDocumentsPageForDocumentGrantPreview` | `POST /admin/dms/grant/document/preview` | ADMIN, Auflöserechte je Feld | allein writeAcl | Feldgrenze |
| Freigabe setzen, ids | `…resolveWritableDmsSolrDocumentIdsForDocumentGrant` | in `POST /admin/dms/grant/document` | ADMIN, Auflöserechte je Feld | allein writeAcl | nur ids, weniger als 500 |
| Entzug und Ablauf, ids | `…resolveWritableDmsSolrDocumentIdsCarryingGrantee` | in `DELETE` und `POST …/expire` | ADMIN | allein writeAcl, Bedingungen aus dem Register plus userGrantedAccess gleich Empfänger, beides vom Backend | nur ids |
| Suche | `codergod1337.app1.dms.service.DmsSearchService.searchDmsSolrDocumentsPage` | `POST /dms/search` | DMS und DMS_SUCHE, auch für den Admin | Lese-fq, Status ACTIVE oder ARCHIVED, dazu die Filter des Clients aus der festen Liste (Hooks, `EXACT_FILTER_FIELD_NAMES`) und Zeiträume (`DATE_RANGE_FILTER_FIELD_NAMES`), fremde Felder überlesen | alles außer searchableWords plus `_version_` als Text, je Treffer der Treffergrund (Felder und Wörter, Volltext nur als Feld). edismax über id, fileName, fileIndex, fileDescription, searchableWords, jeden Hook und die Suggested-Zwillinge, Recency-Boost; ohne Suchwort und Filter keine Abfrage, sondern leere Seite mit Hinweis; höchstens 100 je Seite, 25 Seiten |
| Vervollständigung beim Tippen | `…DmsSearchService.suggestDmsSolrDocuments` | `POST /dms/search/suggest` | DMS und DMS_SUCHE | dieselben fq wie die Suche | kompakte Felder plus Hooks, 15 Treffer über die Suggested-Zwillinge und searchableWords, kein Highlighting; dazu bis zu zehn Filterangebote aus den Treffern (Hook-Werte und fileIndex als Ganzwert-Präfix, fileName als Wortanfang). Nicht suchbar heißt stumm leer |

### Schreiben, ein Service

Alle über `codergod1337.app1.dms.service.DmsSolrDocumentWriteService`, je Vorgang eine Methode, Enum `codergod1337.app1.dms.model.DmsSolrDocumentWriteProcess`, der Name steht mit current user, id und Feldnamen im Log. Jeder Vorgang eines Menschen: changingUsers ADD, lastChangedAt SET. readAcl und writeAcl genau dann, wenn fileSubClassKey geschrieben wird, aus der Dateiart in Postgres. `_version_` geht nie mit. Felder des Clients laufen durch die Schreibregel je Feld (`solr_field.clientWriteRule`), was er nicht darf, wird ignoriert.

| Vorgang | Methode | Endpunkt | Prüfung davor | Geschrieben |
|---|---|---|---|---|
| Upload neu | `createDmsSolrDocumentFromUpload` | `POST /dms/upload` | Existenzcheck leer, Dateiart Pflicht, Endung freigeschaltet, Schreibrolle der Dateiart | volles Dokument, ACLs aus der Dateiart, versionChain neu, version 1 |
| Merge | `mergeHookValuesFromRepeatedUpload` | `POST /dms/upload` | gleiche Dateiart, Status ACTIVE oder ARCHIVED, writeAcl, passende versionChain und fileIndex | Hooks ADD, Beschreibung je Sprache angehängt, nur wenn etwas dazukam |
| Neue Fassung | `createNextVersionInVersionChain` | `POST /dms/upload` mit versionChain | Kette sichtbar, Dateiart vom Kopf, Endung, Schreibrolle | volles Dokument mit version plus eins; dann je ACTIVE-Glied status SET ARCHIVED (`DMS_NEW_VERSION_ARCHIVE_PREVIOUS`) |
| Bearbeiten | `editDmsSolrDocument` | `PATCH /dms/solr-document` | writeAcl, Regeln je Feld | nur die geschickten Felder; bei Dateiartwechsel fileSubClassKey SET und ACLs SET_ALL, auch in jedem anderen Glied der Kette (`DMS_EDIT_FILE_SUB_CLASS_OF_CHAIN`) |
| Soft-Delete | `softDeleteDmsSolrDocument` | `DELETE /dms/solr-document` | writeAcl | status SET DELETED |
| Freigabe setzen | `addGranteeToDmsSolrDocuments` | in `POST /admin/dms/grant/document` | ADMIN, ids noch einmal gegen writeAcl | userGrantedAccess ADD guid |
| Entzug, Ablauf | `removeGranteeFromDmsSolrDocuments` | in `DELETE` und `POST …/expire` | ADMIN, ids noch einmal gegen writeAcl | userGrantedAccess REMOVE guid. Was der current user nicht schreiben darf, behält die guid, steht im Log |

Nicht geschrieben wird je: ein vorhandenes Dokument voll neu, sftpPath, fileSizeBytes, versionChain nach dem Anlegen, `_version_`.

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
- Revision als ZIP vor jedem Bearbeiten, mit `_version_` vorher und nachher, dann fileIdRevisionNo INC.
- Kern ARTIKEL: drei eigene Lese-Services, ein Schreibservice, ein Freigabe-Service, derselbe Aufbau.
