# Solr

Der Suchindex. Jeder Kern des SolrManagers (Postgres, Tabelle `solr_core`) ist ein Kern in Solr, beim File-Kern ist Solr der einzige Speicher der Dokumente. Beim Entwickeln und auf dem Pi läuft Solr als Container `app1_solr` aus der Compose: Version 10, ein Knoten ohne ZooKeeper (`--user-managed`).

## Funktionsweise

### Container

`app1_solr` ist das offizielle Image `solr:10.0.0`, kein eigener Build. Alles, was unseres ist, kommt von außen hinein:

- `docker_mounts/solr` ist `/var/solr` im Container: die Kerne mit Index und Schema, die Logs und die `security.json`. Gehört uid 8983, dem User `solr` im Container.
- `solr/configsets/app1` aus dem Repo ist das Basis-Configset, nur lesbar eingebunden: `solrconfig.xml` und `managed-schema.xml` mit den Feldtypen und den Systemfeldern, sonst nichts.
- Aus der `.env`: `solr_cores` (welche Kerne der Container anlegt), `solr_user` und `solr_pw` (Basic Auth), `solr_port`.

Die Startkette in der Compose macht bei jedem Start drei Dinge: Kernordner anlegen, `security.json` schreiben, Solr starten. Im Docker-Netz heißt der Container zusätzlich `solrhost`, weil Java keinen Unterstrich im Hostnamen akzeptiert; das Backend auf dem Pi spricht später `http://solrhost:8983/solr` an.

### Wie ein Kern entsteht

Ein Kern hat drei Schichten:

1. **Definition in Postgres** (SolrManager): der Kern (`solr_core`), seine Felder (`solr_field`) und die Hooks, die in jedem Kern gleich sind. Das ist die Wahrheit. Das Backend hält sie als Kopie im Speicher und liest sie nie je Anfrage aus Postgres.
2. **Ordner auf der Platte** (`docker_mounts/solr/data/<key>`): `conf/` mit `solrconfig.xml` und `managed-schema.xml`, `data/` mit dem Index, dazu `core.properties`. Legt die Startkette an: `precreate-core <key> /opt/app1/configsets/app1` kopiert das Basis-Configset je Kern aus `solr_cores`, aber nur, wenn der Ordner fehlt. Ein bestehender Kern wird nie angefasst, sein Schema gehört ab da der Schema-API. Solr findet die Ordner beim Hochfahren selbst.
3. **Felder per Schema-API** (Schritt 4 des SolrManagers, noch nicht gebaut): Das Backend liest beim Start und nach jeder Änderung im SolrManager das Schema des Kerns (`GET /solr/<key>/schema/fields` und `/schema/copyfields`) und vergleicht es mit Postgres. Fehlt ein Feld: `add-field`. Weicht ein Schalter ab (indexed, stored, multiValued, docValues, required): `replace-field`. Hat ein Feld `suggest`: dazu der Zwilling `<name>Suggested` (Typ `text_suggest` bei Texten, `code_suggest` bei String und Zahlen; nicht stored, nicht docValues, multiValued) und ein `add-copy-field`. Alle Befehle gehen in einer Anfrage (`POST /solr/<key>/schema`), Solr schreibt die `managed-schema.xml` des Kerns neu und lädt ihn einmal nach. Gelöscht wird nie: Ein Feld, das es in Solr gibt und in Postgres nicht mehr, wird nur gemeldet. Ebenso eine Typänderung, denn Lucene schreibt einen Typ nicht um; sie heißt Neuaufbau des Kerns.

Warum legt nicht das Backend den Kern an? Im user-managed Modus kann die CoreAdmin-API einen Kern nur aus einem Ordner laden, den es schon gibt, oder ihn auf ein gemeinsames Configset zeigen lassen. Gemeinsam heißt wörtlich eine Schemadatei für alle Kerne, jede Änderung per Schema-API träfe alle. Die Configsets-API, die ein Configset je Kern kopiert, gibt es nur im Cloud-Modus mit ZooKeeper. Deshalb kopiert der Container, und das Backend baut darauf die Felder.

Was im Basis-Schema steht und warum:

| Eintrag                                                                                                                       | Grund                                                                                                                          |
|-------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Feldtypen `string`, `boolean`, `plong`, `pdouble`, `pdate`, `text_minimal`, `text_word`, `text_words`, `text_suggest`, `code_suggest` | der Katalog `SolrFieldType` im Backend wählt nur daraus, die Namen stehen dort fest                                            |
| `id` (string, indexed, stored, required)                                                                                      | der `uniqueKey` muss beim Anlegen des Kerns existieren. stored, docValues, Beschreibung und Position zieht das Backend aus Postgres nach |
| `_version_` (plong, nur docValues)                                                                                            | Optimistic Concurrency: kommt mit jedem gelesenen Dokument, geht als Bedingung mit zurück                                       |
| `_root_` (string, indexed)                                                                                                    | für geschachtelte Dokumente, ungenutzt, Solr erwartet es                                                                       |

`cursorDate` steht nicht im Basis-Schema: Es ist ein eingebautes Feld aus Postgres wie `id` und kommt über die Schema-API.

Die Kommentare im Basis-Schema überleben die erste Änderung per Schema-API nicht, Solr schreibt die Datei dann aus dem Speicher neu. Erklärt wird deshalb hier, nicht dort.

### Neuaufbau eines Kerns

Nötig bei einer Typänderung oder einem geänderten Feldtyp im Basis-Configset, denn die Kopie im Kern bekommt Änderungen am Basis-Configset nie mit. Beim File-Kern vorher die Dokumente sichern, Solr ist dort der einzige Speicher: Alle echten Felder sind `stored`, ein Export mit `fl=*` enthält alles bis auf die Suggest-Zwillinge, die Solr aus den copyFields selbst neu füllt.

```
docker compose stop app1_solr
sudo rm -r ../docker_mounts/solr/data/<key>
docker compose up -d app1_solr
```

Danach legt das Backend die Felder neu an, und die Dokumente werden wieder eingespielt.

### Passwort

Solr läuft mit Basic Auth: `security.json` in `/var/solr/data` mit dem `BasicAuthPlugin`, `blockUnknown` an, ein User mit der Rolle `admin` und der Berechtigung `all`. Ohne Anmeldung antwortet Solr mit 401, auch die Admin-Oberfläche fragt nach dem Login.

Solr speichert kein Passwort, sondern `base64(sha256(sha256(salz + passwort))) base64(salz)`. Die Startkette rechnet das bei jedem Containerstart aus `solr_user` und `solr_pw` der `.env` neu, mit frischem Salz, und schreibt die Datei. Ein neues Passwort heißt deshalb: `.env` ändern, `docker compose up -d`. Nichts muss von Hand erzeugt werden.

Kein Host-Key wie beim SFTP. Dort weist sich der Server mit seinem Schlüssel aus, weil SSH keine Zertifikate kennt und das Backend sonst sein Passwort an jeden schicken würde, der unter der Adresse antwortet. Solr spricht HTTP: Der Ausweis des Servers wäre ein TLS-Zertifikat, und das gibt es später nur am nginx nach außen. Im Docker-Netz und im LAN läuft Solr unverschlüsselt, der Port wird nie nach außen gemappt. Das einzige Geheimnis ist das Passwort, es geht mit jeder Anfrage als Basic-Auth-Header mit.

Warum nicht das JWT-Plugin von Solr? Es ist für einen Identity-Provider gedacht (Keycloak, Okta), dem Solr und die App gemeinsam vertrauen: Solr prüft fremde Tokens gegen einen öffentlichen Schlüssel, die Admin-Oberfläche meldet sich per OpenID Connect beim Provider an. Wir haben keinen Provider. Das Backend ist selbst Aussteller seiner Tokens, mit einem RSA-Schlüssel, der bei jedem Start neu entsteht (`JwtConfig`). Solr könnte sie nur prüfen, wenn der Schlüssel fest wäre und als JWK in der `security.json` stünde, oder wenn Solr ihn vom Backend holen könnte, das beim Entwickeln auf dem PC läuft. Für die Admin-Oberfläche bräuchte das Backend außerdem einen OAuth2-Authorization-Endpunkt. Das Token des Users braucht Solr nicht: Das Backend filtert die Rechte selbst (fq) und spricht Solr als Dienst an, dafür reicht ein Passwort. Käme später ein Identity-Provider, betreibt `MultiAuthPlugin` Basic und JWT nebeneinander; es ändert sich nur die Startkette.

| Variable     | Bedeutung                                                                                   |
|--------------|---------------------------------------------------------------------------------------------|
| `solr_host`  | Adresse des Servers. Für das Backend auf dem Pi später `solrhost`                           |
| `solr_port`  | Port auf dem Server. Die Compose mappt Port 8983 des Containers darauf                       |
| `solr_user`  | User, gilt für Container und Backend zugleich                                               |
| `solr_pw`    | Passwort, ebenso. Nur Buchstaben und Ziffern                                                |
| `solr_cores` | die Kerne, die der Container beim Start anlegt: Keys aus dem SolrManager, durch Komma getrennt |

## Installation

Alles zum Container liegt in `docker_mounts/solr/` neben `gitfiles/`, nicht in Git. Alle Befehle auf dem Server im Ordner `gitfiles/`.

### 1. Ordner

```
mkdir -p ../docker_mounts/solr
sudo chown 8983:8983 ../docker_mounts/solr
```

Das `chown` gilt dem User `solr` im Container (uid 8983). Sonst kann er beim Start nichts anlegen und bricht ab.

### 2. `.env`

Den Solr-Block aus `env.beispiel` ausfüllen. `solr_user`, `solr_pw` und `solr_cores` gehören zeichengleich in jede `.env`, aus der ein Backend diesen Container anspricht, auch in die auf dem Entwickler-PC. `solr_host` ist dort die LAN-Adresse des Servers.

### 3. Starten und prüfen

```
docker compose up -d
docker logs app1_solr
curl -u <solr_user>:<solr_pw> "http://localhost:<solr_port>/solr/admin/cores?action=STATUS&wt=json"
```

Beim ersten Mal holt `up` das Image, ein paar hundert MB. Im Log steht je Kern `Created <key>` (beim nächsten Start `already exists`), danach fährt Solr hoch, ein Fehler darf nicht darin stehen. Die Antwort auf `curl` listet die Kerne, ohne `-u` kommt 401. Die Admin-Oberfläche: `http://<server>:<solr_port>/solr`, Anmeldung mit `solr_user` und `solr_pw`.

### 4. Neuer Kern

Im SolrManager anlegen, den Key in `solr_cores` ergänzen (in jeder `.env`), dann `docker compose up -d`: Die Compose legt den Container neu an, die Startkette den Ordner. Die Felder kommen vom Backend (Schritt 4).
