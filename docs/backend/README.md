# Backend

Spring Boot mit Java (LTS, die Version steht in `spring/pom.xml` unter `java.version`), gebaut mit dem Maven-Wrapper. Liegt in `spring/`.

- Group `codergod1337`, Artifact `app1-spring`, Name `gebieter` (Hauptklasse `GebieterApplication`)
- Basis-Package `codergod1337.app1`
- Datenbank: Postgres über JPA/Hibernate
- Login: Spring Security mit JWT (Nimbus), Tokens als Cookies

## Themen

- [SFTP-Dateispeicher](sftp.md): wie Dateien abgelegt werden, Installation des Containers
- [Solr](solr.md): Container, wie ein Kern entsteht, Passwort, Installation

## Pakete

Package by Feature. Unter `codergod1337.app1` liegt `system` (alles, was die App zum Laufen braucht) und daneben je ein Paket pro Feature. Ein Feature kommt als eigenes Paket dazu, ohne dass andere Pakete angepasst werden.

```
codergod1337.app1
  system/
    security/     Login, JWT, current user
    user/         Users, UsersCredentials, UsersDetails, UsersSettings
    access/       AccessRole, AccessRoleCollection und ihre Zuordnung zu Usern
    masterdata/   Export und Import der Stammdaten
    sftp/         Dateispeicher
  file/           Dateiarten und Endungen (FileSubClass, FileExtension)
  solr/           SolrManager: Hooks, Hook-Gruppen, Kerne, Felder. Tabellen heißen solr_*
```

Jedes Paket ist aufgeteilt in `controller/`, `service/`, `repository/` und `model/`. In `model/` liegen Entities, Enums und, wo nötig, DTOs.

Controller prüfen die Form der Eingabe und reichen an den Service weiter. Ein Service nutzt nur sein eigenes Repository, Daten einer anderen Klasse holt er über deren Service.

## Endpunkte

Schema: `/api/rest/v1/<zugriff>/<endpunkt>`. Am Pfad ist ablesbar, wer ihn aufrufen darf:

| Segment  | Bedeutung                                |
|----------|------------------------------------------|
| `public` | ohne Anmeldung                           |
| `admin`  | angemeldet mit der AccessRole `ADMIN`    |
| keins    | angemeldet, die Rechte prüft der Service |

Daten kommen im Request-Body. Nur GET darf eine guid oder eine Long-ID in der URL tragen.

Den ausführenden User liefert ein Provider aus dem Security-Context. Läuft das Backend aus sich heraus (Jobs, Import), ist das der System-User.

## Entities

- Keine JPA-Beziehungen zwischen Entities. Fremdschlüssel sind einfache ID-Felder und werden im Code aufgelöst.
- Hibernate legt das Schema aus den Entities an (`ddl-auto: update`). In der DB gibt es dadurch keine FK-Constraints, die Integrität liegt im Code.
- Stammdaten verweisen untereinander über sprechende Keys, nie über Datenbank-IDs, damit ein Import sie in jeder Datenbank wiederfindet.

## Konfiguration

Alles steht in `spring/src/main/resources/application.yml`. Werte, die je Umgebung anders sind oder geheim bleiben müssen, kommen aus der `.env` im Repo-Root und werden dort mit `${variable}` eingebunden. Die `application.yml` lädt die `.env` selbst, egal ob das Backend aus dem Repo-Root oder aus `spring/` gestartet wird.

## Starten

Voraussetzungen: Java, eine ausgefüllte `.env`, erreichbare Container (Postgres, SFTP).

```
cd spring
./mvnw spring-boot:run
```

Unter Windows `mvnw.cmd`. Das Backend hört auf Port 6969 (`server.port` in der `application.yml`).
