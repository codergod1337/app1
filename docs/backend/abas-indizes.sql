-- ===========================================================================
-- AbasManager: Teilindizes für den Ernstfall (10^8 bis 10^9 Sternzeiten)
-- ===========================================================================
--
-- Die übrigen Indizes stehen als @Index an den Entities AbasSternzeit und
-- AbasSternzeitQueueEntry, Hibernate legt sie beim Start selbst an. Teilindizes,
-- also Indizes mit WHERE-Bedingung, kann JPA nicht ausdrücken, deshalb stehen
-- sie hier und werden von Hand angelegt.
--
-- Wozu: Der AbasManager zählt Mengen, die immer klein sind (Sätze ohne Typ,
-- Einträge in Arbeit, Einträge mit Fehlern). Ohne Teilindex liest Postgres
-- dafür die ganze Tabelle, obwohl es um ein paar Dutzend Zeilen geht. Mit
-- Teilindex steht die Antwort sofort, weil der Index nur diese Zeilen enthält.
--
-- Wann: heute, solange die Tabellen klein sind, dann dauert es Millisekunden.
-- CONCURRENTLY steht trotzdem dabei, damit die Zeilen auch später im laufenden
-- Betrieb benutzbar sind: Der Aufbau sperrt die Tabelle dann nicht.
--
-- Achtung: CREATE INDEX CONCURRENTLY läuft nicht in einer Transaktion, also
-- jede Zeile einzeln ausführen, nicht als Block in BEGIN/COMMIT.

-- Geholt, aber noch ohne Objekttyp: die Sätze, bei denen etwas klemmt.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_abas_sternzeit_without_type
    ON abas_sternzeit (sternzeit) WHERE object_type_id IS NULL;

-- Queue-Einträge, die gerade ein Worker hält.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_abas_sternzeit_queue_claimed
    ON abas_sternzeit_queue (claimed_at) WHERE claimed_at IS NOT NULL;

-- Queue-Einträge mit mindestens einem Fehlversuch.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_abas_sternzeit_queue_failed
    ON abas_sternzeit_queue (attempts) WHERE attempts > 0;

-- ---------------------------------------------------------------------------
-- Danach einmal ANALYZE: Die Gesamtzahlen der Übersicht kommen ab einer halben
-- Million Zeilen aus der Schätzung reltuples in pg_class, und die stammt vom
-- letzten ANALYZE. Ohne ihn steht dort -1, und das Backend fällt auf das teure
-- count(*) zurück.
-- ---------------------------------------------------------------------------
ANALYZE abas_sternzeit;
ANALYZE abas_sternzeit_queue;
