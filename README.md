# app1

Demo-Fullstack-App: Spring Boot (Backend), React mit TypeScript (Frontend), Postgres und SFTP als Container. Dieses Repo enthält nur Code, keine Daten und keine Zugangsdaten.

## Doku

- [Backend](docs/backend/README.md): Spring, Pakete, Endpunkte, Konfiguration, Starten
  - [SFTP-Dateispeicher](docs/backend/sftp.md): Funktionsweise und Installation
- [Frontend](docs/frontend/README.md): React, Ordner, Grundsätze, Starten

## Ordner

```
app1/
  gitfiles/          dieses Repo
    docker-compose.yml, env.beispiel, .env, app1-deploy.sh
    spring/          Backend
    frontend-ts/     Frontend
    docs/            diese Doku
  docker_mounts/     ein Ordner pro Container (postgres, sftp), nicht in Git
```

Die Compose erwartet `docker_mounts` neben `gitfiles`. Das Repo wird deshalb in einen Ordner namens `gitfiles` geklont. Der Name des Ordners darüber ist egal.

## Betrieb

Die Container (Postgres, SFTP) laufen auf einem Server, bei uns ein Raspberry Pi, auch beim Entwickeln. Backend und Frontend laufen beim Entwickeln lokal auf dem PC und sprechen die Container über das LAN an. Solange entwickelt wird, sind die Ports der Container nach außen gemappt.

## Installation

### 1. Repo und `.env`

```
mkdir app1 && cd app1
git clone https://github.com/codergod1337/app1.git gitfiles
cp gitfiles/env.beispiel gitfiles/.env
```

`.env` ausfüllen, jede Variable ist in `env.beispiel` erklärt. Die `.env` liegt immer in `gitfiles/`, wird von der Compose und vom Backend gelesen und steht in `.gitignore`.

### 2. Server: Container

Auf dem Server, im Ordner `gitfiles`:

1. Host-Key und Ordner für den SFTP-Container anlegen, siehe [SFTP-Installation](docs/backend/sftp.md#installation).
2. Container starten:

```
docker compose up -d
```

Beim ersten Mal baut `up` das SFTP-Image selbst, siehe [SFTP](docs/backend/sftp.md#container). `app1-deploy.sh` macht dasselbe, baut vorher aber alle eigenen Images ohne Cache neu, holt die Basis-Images frisch und räumt danach alte Images weg. Für den Alltag reicht `docker compose up -d`.

### 3. PC: Backend und Frontend

Siehe [Backend starten](docs/backend/README.md#starten) und [Frontend starten](docs/frontend/README.md#starten).
