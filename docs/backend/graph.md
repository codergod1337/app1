Lesbar machen in VS Code: `Strg+Shift+V` (Vorschau), `Strg+K V` (Vorschau daneben).

# Microsoft Graph

Alles, was über Microsoft Graph läuft, liegt im Feature-Paket `graph`. Heute verschickt das Backend damit Mails, zuerst nur den Aktivierungslink der 2FA. Später kommen Postfächer dazu: Archivieren von Mails und Versand im Namen eines Users. Im Frontend gehört dazu das Modul GraphManager (Kachel, AR ADMIN), heute mit dem Reiter „Mailvorlagen“.

```
graph/
  entra/     Identität gegenüber Microsoft: Zertifikat, Assertion, Token
  mail/      Vorlagen, Platzhalter, Rendern, Senden
  mailbox/   Postfächer, aus denen archiviert wird, und wer auf welches zugreifen darf
```

## entra: wie sich das Backend ausweist

Kein Client-Secret, sondern ein Zertifikat. Der private Schlüssel liegt nur auf dem Server, in Entra ist das Zertifikat (der öffentliche Teil) bei der App-Registrierung hochgeladen. Wie es entsteht und erneuert wird, steht in `app1/secrets/entra/ANLEITUNG.md` außerhalb des Repos.

1. `EntraClientCertificateService` liest beim Start Schlüssel und Zertifikat (PEM) und baut je Token-Abruf eine Assertion: ein JWT mit `aud` (Token-Endpunkt des Tenants), `iss` und `sub` (Client-ID), `jti`, `nbf`, `iat`, `exp` (5 Minuten), signiert mit PS256, im Kopf der SHA-256-Fingerabdruck des Zertifikats (`x5t#S256`), so wie die Microsoft-Doku es vorgibt. Läuft das Zertifikat in unter 30 Tagen ab, warnt das Log bei jedem Start.
2. `EntraGraphAccessTokenService` holt mit der Assertion am Token-Endpunkt ein Zugriffstoken für Graph (Scope `https://graph.microsoft.com/.default`) und hält es im Speicher. Es gilt rund eine Stunde, ab fünf Minuten vor dem Ablauf wird ein neues geholt. Ein Token für alles, was Graph ist.

`EntraProperties` ist `app1.graph.entra.*` aus der `.env` (`entra_tenant_id`, `entra_client_id`, `entra_private_key_path`, `entra_certificate_path`). Fehlt etwas, startet das Backend trotzdem und schreibt eine Warnung, Graph ist dann nicht erreichbar. Stimmen die Werte, aber eine Datei fehlt oder hat das falsche Format, startet es nicht. Nimbus (über den oauth2-resource-server schon im Backend) signiert, der `RestClient` spricht Microsoft an. Keine neue Abhängigkeit, kein MSAL.

## mail: Vorlagen und Versand

- `GraphMailTemplate` (Tabelle `graph_mail_template`): `key`, `type`, `displayName`, `description`, `subject`, `htmlBody` (alle Texte mehrsprachig de, en, bg), `listingPosition`. Je Typ genau eine Vorlage. Beim Start legt `GraphMailTemplateService` für jeden Typ ohne Vorlage die Standardtexte aus `GraphMailTemplateDefaultTexts` an, was der Admin danach ändert, bleibt.
- `GraphMailTemplateType`: wofür eine Vorlage da ist und welche Platzhaltergruppen sie benutzen darf. Heute `LOGIN_2FA` mit den Gruppen `users` und `login`.
- Platzhalter heißen `{gruppe.name}`, z. B. `{users.vorname}` oder `{login.confirmLink}`. Jede Gruppe ist ein Bean, der `GraphMailPlaceholderGroup` umsetzt: Key, Namen (für den Editor), Beispielwerte (für das Muster). `UsersGraphMailPlaceholderGroup` liefert die Felder des Users (email, username, vorname, nachname), `LoginGraphMailPlaceholderGroup` Link, Token und Gültigkeit. Eine neue Gruppe ist eine neue Klasse plus ihr Eintrag am Typ, sonst nichts. Beim Speichern prüft der Service jeden Platzhalter gegen die Gruppen des Typs, unbekannte sind 400.
- `GraphMailTemplateService.sendGraphMailFromTemplate(type, language, placeholderValuesByGroup, recipientEmail)`: Vorlage des Typs laden, Text in der Sprache (Rückfall de, dann der erste vorhandene), Platzhalter ersetzen (im HTML escaped), `GraphMailMessage` bauen und an `GraphMailSendService` geben.
- `GraphMailMessage` trägt den Absender selbst. Heute setzt die Vorlage das Systempostfach aus `app1.graph.mail.system-sender-address` (.env `graph_mail_sender`), später kann hier das Postfach eines Users stehen, ohne dass sich am Versand etwas ändert.
- `GraphMailSendService.sendGraphMail(message)`: POST sendMail im Namen des Absenders, keine Kopie in „Gesendete Elemente“. Lehnt Graph ab oder antwortet nicht: `GraphMailUnavailableException` (503), Status und Antwort nur im Log. Voraussetzung in Entra: die Anwendungsberechtigung `Mail.Send` mit Administratoreinwilligung, sonst antwortet Graph 403. Empfohlen eine Application Access Policy in Exchange Online, die die App auf das Absenderpostfach beschränkt.

Endpunkte (AR ADMIN): `GET/POST/PUT/DELETE /api/rest/v1/admin/graph/mailtemplate`, `POST …/position`, `POST …/preview` (Typ, Sprache, Betreff und Text aus dem Formular, zurück kommt das Muster mit Beispielwerten), `GET /api/rest/v1/admin/graph/mailtemplatetype` (die Typen mit ihren Gruppen). Die Vorlagen sind Stammdaten (Bereich `GRAPH_MAIL_TEMPLATES`), aber nicht Teil des öffentlichen Stammdaten-Endpunkts: Ihre Texte sind nicht öffentlich.

Test-Mail: `POST /api/rest/v1/admin/graph/testmail` mit `senderAddress`, `recipientEmail`, `subject`, `htmlBody` schickt eine Mail ohne Vorlage über den `GraphMailSendService`. Damit prüft der Admin Zertifikat, Berechtigung und ein Absenderpostfach, ohne einen Login zu starten.

## mailbox: Postfächer und Zugriff

- `Mailbox` (Tabelle `mailbox`): `emailAddress` (Schlüssel, klein), `displayName`, `description`, `listingPosition`, `accessRoleCollectionKeys` (die ARC-Positionen, die aus dem Postfach archivieren dürfen). Nur Dienstpostfächer, keine persönlichen Adressen. Von Hand gepflegt, nicht aus Graph geholt.
- Nicht in den öffentlichen Stammdaten. Die ganze Liste gibt es nur für den Admin: `GET/POST/PUT/DELETE /api/rest/v1/admin/graph/mailbox`, `POST …/position`.
- Jeder andere User: `GET /api/rest/v1/graph/mailbox/mine` liefert `MailboxesOfCurrentUser` mit `assignedMailboxes` und `ownEmailAddress`.
- Die Regel steht einmal in `MailboxService` und gilt für die Liste wie für jede Prüfung:
  1. Immer die AR `NACHRICHTEN_ARCHIVIEREN` (`@PreAuthorize`), sonst 403, auch für das eigene Postfach. ADMIN darf alles.
  2. Dienstpostfächer: die, an denen seine ARC aus dem Token oder eine ihrer direkt eingetragenen `slaveArcKeys` steht.
  3. Eigenes Postfach: seine E-Mail aus der DB, wenn sie auf `@` plus `app1.graph.mail.domain` endet (.env `graph_mail_domain`, ohne @). Leer: Kein eigenes Postfach zählt.
- `requireArchiveAccessToMailbox(emailAddress)` prüft genau diese Regel für eine Adresse, sonst 403. Jeder Archiv-Endpunkt ruft es als Erstes, bevor Graph gefragt wird. Die Auswahl im Frontend schützt nichts.
- AR und ARC kommen aus dem Token und wirken mit dem nächsten Token. Die ARC-Einträge am Postfach und die E-Mail des Users liest jeder Aufruf frisch aus der DB.
- Mails lesen: `GraphMailboxReadService` (Voraussetzung in Entra: `Mail.Read` als Anwendungsberechtigung mit Administratoreinwilligung). Jede Methode ruft zuerst `requireArchiveAccessToMailbox`. Alle ids sind feste ids (`Prefer: IdType="ImmutableId"`), sie ändern sich beim Verschieben nicht.
  - `getMailHeaders(mailbox, folder)`: die neuesten 50 aus `INBOX` oder `SENT_ITEMS` (`GraphMailFolder`), als `GraphMailHeader`, seit 2026-10-14 mit `internetMessageId`: Darüber fragt die Liste `POST /api/rest/v1/message/archive/archived`, welche Mails schon im Kern NACHRICHTEN liegen (siehe solr-zugriff.md). Endpunkt `POST /api/rest/v1/graph/mailbox/headers` mit `{mailbox, folder}`.
  - `getMail(mailbox, exchangeMailId)`: alle Felder samt `internetMessageHeaders`, `uniqueBody` und `body`, das Graph-JSON unverändert, die Quelle fürs Mapping. Endpunkt `POST /api/rest/v1/graph/mailbox/mail` mit `{mailbox, exchangeMailId}`.
  - `getMailAttachments(mailbox, exchangeMailId)`: die Anhänge samt Bytes, je Anhang mit Bytes dazu `sha256` = DMS-id. Nur im Backend, ohne Endpunkt.
  - `getMailView(mailbox, exchangeMailId)`: die Mail zum Anzeigen (`GraphMailView`): Kopf mit Name und Adresse getrennt, die ganze Mail (`bodyHtml`) und der neue Teil (`uniqueBodyHtml`), in beiden die `cid:`-Bilder gegen `data:`-Bilder der Anhänge getauscht, die Anhänge ohne Bytes (`GraphMailAttachmentInfo`). Endpunkt `POST /api/rest/v1/graph/mailbox/mail/view`. Der Text kommt immer als HTML (`Prefer: outlook.body-content-type="html"`).
  - Graph 404 wird 404, jede andere Ablehnung 503 mit Status und Antwort nur im Log. POST statt GET, weil die Postfachadresse nicht in die URL darf.
- An welcher Nachrichtenart ein Postfach hängt, steht lose an der Nachrichtenart (`MessageSubClass.mailboxEmailAddresses`, ein Postfach an höchstens einer Art). Später wird damit eine Nachricht nach ihrem Postfach zugeordnet oder die Art vorausgewählt.

## Archivieren: Aufbereiten (Paket message.archive)

`POST /api/rest/v1/message/archive/prepare` mit `{mailbox, exchangeMailId}` liefert `MessageArchivePreparation`, alles für die Aufbereiten-Seite in einer Antwort. Gespeichert wird nichts, alles läuft mit den Rechten des Archivierenden (AR `NACHRICHTEN_ARCHIVIEREN` oder ADMIN, Postfachzugriff, DMS, Kontakte).

- Anhänge: erst nur der Hash (`getExistence`). Gibt es die Bytes schon, wird das ganze DMS-Dokument mit den Rechten des Users geholt: `wtf` = `ATTACHMENT_SIGNATURE` heißt `SIGNATURE` (still eintragen, nie listen), `ATTACHMENT_IGNORE` heißt `IGNORED` (ausgegraut, trotzdem wählbar), sonst `MERGE`. Bekommt er es nicht: `NO_ACCESS`, Archivieren gesperrt. Neue (`NEW`) bekommen je beschreibbarer Dateiart den festen Teil des Vorschlags: Triggerwörter in Betreff und Mailtext einfach, in Dateiname und Tika-Text doppelt.
- Nachrichtenart: fester Teil je Art, Postfach an der Art 3 Punkte, je Triggerwort-Treffer 1 Punkt. Die Seite rechnet je Trigger-Hook mit Wert 2 Punkte dazu (Art und Dateiart), so springt der Vorschlag mit den Hooks mit; ohne Treffer bei der Dateiart TEMP.
- Nummern: Ziffernfolgen ab 4 Stellen aus Betreff und neuem Teil (Regel der Vorlage), jede über die Belegnummer bei ABAS, 3 gleichzeitig, höchstens 30. Zurück nur die gefundenen, mit dem Hook, dessen `abasObjectTypes` den Typ nennen (`obj:data:` darf fehlen).
- Kontakte: jede Adresse aus Von, Gesendet von, An, Cc, Bcc exakt im Kern KONTAKTE, außer der eigenen Domäne. Ohne AR KONTAKTE leer. Vorgeschlagene Hooks: Kunden- und Lieferantennummern der Kontakte des Absenders (sonst der Empfänger) und die Nummern mit erkanntem Hook.
- Trigger stehen als Daten an `MessageSubClass` (`triggerWords`, `triggerHookKeys`), `FileSubClass` (`triggerWords`, `triggerHookKeys`) und `SolrHook` (`abasObjectTypes`), gepflegt in den Admin-Formularen.

## Die 2FA-Mail beim Login

1. `LoginService.startLogin` prüft Passwort und Status, legt die Wartezeile an (`UsersLogin2faService`) und bekommt Abholschein und Bestätigungs-Token.
2. Sprache des Users aus der UsersSetting `LANGUAGE` (ohne Einstellung de), Link aus `app1.login.confirm-url` plus `#` plus Token. Der Teil hinter dem `#` geht nie an einen Server, auch ein Link-Scanner im Postfach bestätigt damit nichts.
3. `sendGraphMailFromTemplate(LOGIN_2FA, …)` mit den Werten der Gruppen `users` (aus dem User) und `login` (Link, Token, Minuten) an die E-Mail des Users.
4. Scheitert der Versand: 503, die Transaktion rollt die Wartezeile zurück. Sonst antwortet `/start` nur mit dem Abholschein (`Login2faPollToken`).
5. Bestätigen auf zwei Wegen: Der Link öffnet die ConfirmLoginPage im neuen Fenster, oder der Token aus der Mail wird in der Wartephase der LoginPage eingefügt. Beides ruft `/confirm`, danach holt `/claim` die Sitzung.

## GraphManager im Frontend

`modul/graph/`: Kachel „GraphManager“, Reiter „Mailvorlagen“ mit Tabelle (Reihenfolge per Drag and Drop), Anlegen und Bearbeiten. Der Editor zeigt die Platzhalter des Typs (Klick kopiert), Betreff und Text je Sprache über die Flaggen der ContentBox und darunter das Muster, das der Preview-Endpunkt kurz nach jedem Tippen neu rendert. Das HTML des Musters läuft in einem iframe mit `sandbox`, ohne Skripte und ohne Zugriff auf die Seite. Reiter „Test-Mail“: Absender, Empfänger, Betreff und Text von Hand, Senden, Ergebnis im Footer und als Toast.
