# SFTP-Dateispeicher

Alle Dateien der App (Uploads, später Revisionen und Exporte) liegen nicht in der Datenbank, sondern auf einem SFTP-Server. Beim Entwickeln und auf dem Pi ist das der Container `app1_sftp` aus der Compose. Später kann ein NAS an seine Stelle treten, ohne dass sich am Code etwas ändert.

## Funktionsweise

### Container

`app1_sftp` ist ein `atmoz/sftp`: Alpine plus OpenSSH, mehr nicht. Auf Docker Hub gibt es das Image nur für amd64, der Pi ist arm64. Deshalb baut die Compose es aus dem Git-Repo des Projekts selbst, mit festem Commit. Die Compose legt darin einen User an (`sftp_user` und `sftp_pw` aus der `.env`), sperrt ihn in sein Home (chroot) und bindet den Ordner `docker_mounts/sftp/sftproot` des Servers als `/sftproot` darin ein. Aus Sicht des Users gibt es nur `/sftproot`, das ist der `root-dir` in der `application.yml`.

Der Container benutzt einen festen Host-Key, `docker_mounts/sftp/ssh_host_ed25519_key`. Ohne den würde er bei jedem Neuanlegen einen neuen erzeugen, und das Backend würde den Server nicht mehr erkennen. Der Key liegt neben `sftproot`, nicht darin, sonst sähe ihn der SFTP-User.

```
docker_mounts/sftp/
  ssh_host_ed25519_key       privater Host-Key, chmod 600
  ssh_host_ed25519_key.pub   öffentlicher Teil, steht in jeder .env als sftp_host_key
  sftproot/                  die Dateien, gehört uid 1001
```

### Backend (`system/sftp`)

Nur der `SftpService` redet mit dem Dateispeicher. Er kennt keine Rechte und keine Dateiarten, das entscheidet der Aufrufer. Er bietet: schreiben, lesen, nachsehen, löschen.

- **Host-Key-Pinning.** Der Host-Key ist der Ausweis des Servers, kein Zugang. Das Backend akzeptiert nur den Server, dessen öffentlicher Host-Key in der `.env` unter `sftp_host_key` steht. Ein Mensch würde beim ersten Verbinden gefragt, ob er dem Server vertraut, das Backend kann niemanden fragen, deshalb bekommt es den Key fest vorgegeben. Steht unter der Adresse ein anderer Server, kommt die Verbindung nicht zustande. Das verhindert drei Dinge: dass jemand dem Backend falsche Dateien unterschiebt, dass das Backend sein Passwort an einen falschen Server schickt, und dass ein zwischengeschalteter Server Uploads und Downloads mitliest. SFTP-Clients wie FileZilla brauchen den Key nicht, sie melden sich mit User und Passwort an und bestätigen den Fingerprint einmal selbst.
- **Anmeldung** mit Passwort (`sftp_pw`) oder, wenn `sftp_private_key_path` gesetzt ist, mit einem privaten Schlüssel.
- **Verbindungspool** (`SftpConnectionPool`). Verbindungen bleiben offen und werden verliehen, weil der SSH-Handschlag bei jeder Datei zu teuer wäre. Zwei Grenzen: eine für alle Verbindungen zusammen und eine je angemeldetem User, damit ein User mit vielen Uploads nicht alle anderen blockiert. Läuft das Backend aus sich heraus (Jobs, Import), gilt nur die globale Grenze. Ist der Server beim Start nicht da, startet das Backend trotzdem und versucht es alle 30 Sekunden erneut.
- **Ablage** (`SftpPathService`): `JJJJ/MM/TT[_n]/<sha256>.bin`, relativ zum `root-dir`. Der Dateiname ist der SHA-256 des Inhalts, zwei verschiedene Dateien bekommen also nie denselben Pfad. Ab einer festgelegten Zahl Dateien am Tag kommt ein weiterer Tagesordner `TT_1`, `TT_2` und so weiter.
- **Schreiben** geht immer über eine `.part`-Datei: schreiben, Größe prüfen, umbenennen. Bricht ein Upload ab, bleibt nie eine halbe Datei unter dem endgültigen Namen liegen. Der Hash wird beim Durchreichen mitgerechnet, nichts liegt ganz im Arbeitsspeicher, auch Dateien von vielen Gigabyte nicht.
- **Pfade** sind immer relativ. Der Service prüft sie (kein `..`, kein absoluter Pfad) und setzt die Wurzel selbst davor. Kein Aufrufer kann die Wurzel verlassen.
- **Fehler** nach außen: 503 mit `Retry-After`, wenn der Dateispeicher nicht erreichbar oder ausgelastet ist, 429, wenn der User seine Verbindungen ausgeschöpft hat, 404, wenn die Datei nicht da ist. Die Meldungen enthalten keine Pfade, die stehen im Log.

Die Grenzen und Timeouts stehen in der `application.yml` unter `app1.sftp`. Alles, was den Server betrifft, kommt aus der `.env`:

| Variable                | Bedeutung                                                                |
|-------------------------|--------------------------------------------------------------------------|
| `sftp_host`             | Adresse des Servers                                                      |
| `sftp_port`             | Port. Die Compose mappt Port 22 des Containers darauf                    |
| `sftp_user`             | User, gilt für Container und Backend zugleich                            |
| `sftp_pw`               | Passwort, ebenso                                                         |
| `sftp_host_key`         | öffentlicher Host-Key des Servers: Typ und Schlüssel, ohne Hostnamen     |
| `sftp_private_key_path` | optional: privater Schlüssel des Backends statt Passwort. Leer: Passwort |

### Wechsel auf ein NAS

In der `.env` nur `sftp_host` und `sftp_host_key` ändern. Das NAS muss dem User denselben `root-dir` zeigen (`/sftproot`), sonst `root-dir` in der `application.yml` anpassen. Der Container `app1_sftp` fällt dann aus der Compose heraus.

## Installation

Alles zum Container liegt in `docker_mounts/sftp/` neben `gitfiles/`, nicht in Git. Nach einem frischen Clone einmal anlegen. Alle Befehle auf dem Server im Ordner `gitfiles/`.

### 1. Host-Key und Ordner

Einmalig. Das Schlüsselpaar bleibt für immer dasselbe, ein neues hieße: Jede `.env` braucht den neuen öffentlichen Key.

```
mkdir -p ../docker_mounts/sftp/sftproot
ssh-keygen -t ed25519 -N "" -C "app1_sftp host key" -f ../docker_mounts/sftp/ssh_host_ed25519_key
chmod 600 ../docker_mounts/sftp/ssh_host_ed25519_key
sudo chown 1001:100 ../docker_mounts/sftp/sftproot
```

Das `chown` gilt dem User im Container (uid 1001 laut Compose). Sonst kann er nichts schreiben, jeder Upload scheitert mit 503.

### 2. `.env`

Den SFTP-Block aus `env.beispiel` ausfüllen. Den Wert für `sftp_host_key` liefert:

```
cut -d " " -f 1,2 ../docker_mounts/sftp/ssh_host_ed25519_key.pub
```

`sftp_user`, `sftp_pw` und `sftp_host_key` gehören zeichengleich in jede `.env`, aus der ein Backend diesen Container anspricht, auch in die auf dem Entwickler-PC. `sftp_host` ist dort die LAN-Adresse des Servers.

### 3. Starten und prüfen

```
docker compose up -d
docker logs app1_sftp
ssh-keyscan -p <sftp_port> localhost
```

Beim ersten Mal baut `up` das Image, das dauert auf dem Pi ein bis zwei Minuten. Im Log darf kein Fehler stehen, am Ende `Executing sshd`. `ssh-keyscan` muss genau den Schlüssel aus der `.env` zeigen.

### 4. Backend

Beim Start meldet es im Log:

```
SFTP-Server <host>:<port> erreichbar, Host-Key geprüft, Anmeldung als <user> ok
```

Steht stattdessen `SFTP-Vorwärmen fehlgeschlagen`, probiert es der Pool alle 30 Sekunden erneut. Ursache ist fast immer ein Host-Key, der nicht zum Container passt, oder ein anderes Passwort als in der Compose. Scheitert erst der Upload mit 503, fehlt das `chown`.
