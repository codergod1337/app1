# Namenskonventionen

## Spring (`spring/`)

- Group: `codergod1337`
- Artifact: `app1-spring`
- Name: `gebieter` (Hauptklasse `GebieterApplication`)
- Basis-Package: `codergod1337.app1`, ohne `io.github`

## Env-Variablen

- Klein geschrieben mit Unterstrich, vorne der Dienst: `postgres_user`, `postgres_pw`

## Docker Compose

- Container heißen `app1_<dienst>`: `app1_postgres`, `app1_solr` usw.
