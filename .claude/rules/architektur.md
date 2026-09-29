# Architektur

- Zwischen allen Klassen gilt lose Kopplung.

## Paketstruktur (Backend)

- Package by Feature: unter `codergod1337.app1` ein Paket `system` (Security, User, Rechte) und daneben je ein Paket pro Feature.
- Features lassen sich als eigenes Paket hinzufügen, ohne andere Pakete anzupassen.
- Jedes Paket ist immer aufgeteilt in `controller/`, `model/`, `repository/` und `service/`.
- Pakete können Unterpakete haben. Die sind genauso aufgeteilt (z. B. `feature_a/teil_x/controller/`).

## Entities

- Hibernate lädt immer nur eine einzelne Entity aus der DB.
- Keine JPA-Beziehungen zwischen Entities (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`).
- Fremdschlüssel sind einfache ID-Felder (z. B. `Long kundeId`) und werden von Hand aufgelöst, also über einen eigenen Repository-Aufruf.
- Hibernate erzeugt das Schema aus den Entities (`ddl-auto: update`). In der DB entstehen dadurch keine FK-Constraints, die Integrität liegt im Code.

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
