# Namenskonventionen

## Spring (`spring/`)

- Group: `codergod1337`
- Artifact: `app1-spring`
- Name: `gebieter` (Hauptklasse `GebieterApplication`)
- Basis-Package: `codergod1337.app1`, ohne `io.github`

## Methoden (Spring)

- Methodennamen nennen immer die Entity, um die es geht: `createUsers`, nicht `create`. Ein Service kann auch andere Entities anlegen oder ändern, am Namen muss erkennbar sein, was entsteht.
- Parameter heißen nach ihrem Inhalt, nie allgemein wie `input`, `data` oder `obj`. Beispiel: `newUsersData` für die Daten eines neu anzulegenden Users.
- Wer eine Methode auslöst, steht nicht im Namen. Das ergibt sich aus dem current user (siehe `architektur.md`, Zugriffsfälle).

## Env-Variablen

- Klein geschrieben mit Unterstrich, vorne der Dienst: `postgres_user`, `postgres_pw`

## Docker Compose

- Container heißen `app1_<dienst>`: `app1_postgres`, `app1_solr` usw.
