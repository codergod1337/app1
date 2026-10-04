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

## Klassen, Records, Enums und Felder

Sprechende Benennung, und zwar MINDESTENS so präzise wie dieses Beispiel. Das ist das unterste Mindestmaß, nicht das Ziel:

```java
record SolrCoreSearchFieldDefinition(
    List<SolrFreeTextSearchFieldWithWeight> freeTextSearchFieldsWithWeight,
    List<String> facetValueCountFieldNames,
    List<String> highlightSnippetSourceFieldNames,
    String defaultSortFieldName,
    boolean defaultSortDescending)
```

- Der Name sagt, was die Sache tut und welche Daten sie anfasst, so konkret, dass man die Javadoc nicht braucht: `SolrReadAclAndGrantedAccessCheck`, nicht `SolrReadBoundary`; `freeTextSearchFieldsWithWeight`, nicht `queryFields`; `SolrCoreSearchFieldDefinition`, nicht `SolrSearchRules`.
- Verboten sind Oberbegriffe und Fachjargon als Name: `Boundary`, `Rules`, `Policy`, `Context`, `Info`, `Helper`, `Manager`, `Handler`, `data`, `item`, `value`, `config`.
- Ein zusammengesetzter Name ist lang, das ist gewollt. Lieber `highlightSnippetSourceFieldNames` als `highlightFields`.
- Ein Name, der unter dem Beispiel oben bleibt, wird nicht vorgeschlagen. Vor jedem Vorschlag jeden Namen daran messen, nicht erst nach der Rückfrage.

## Postgres-Tabellen

- Alles, was zu Solr gehört (Hooks, Kerne, Felder), heißt `solr_<name>`: `solr_hook`, `solr_core`, `solr_field`. So stehen die Tabellen in DBeaver untereinander.

## Env-Variablen

- Klein geschrieben mit Unterstrich, vorne der Dienst: `postgres_user`, `postgres_pw`

## Docker Compose

- Container heißen `app1_<dienst>`: `app1_postgres`, `app1_solr` usw.
