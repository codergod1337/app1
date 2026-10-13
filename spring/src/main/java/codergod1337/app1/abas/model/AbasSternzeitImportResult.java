package codergod1337.app1.abas.model;

/**
 * Was der Import einer Datei getan hat, der zweite Schritt (User 2026-10-13). Während des Laufs kommt derselbe Record
 * als Zwischenstand.
 *
 * @param found     alle Nennungen einer Sternzeit in der Datei, Doppelte mitgezählt
 * @param filtered  nicht gewählt: Objekttyp oder Datenbank stand nicht in der Auswahl
 * @param queued    neu zum Holen in die Queue gelegt
 * @param skipped   gewählt, aber schon in der Queue oder doppelt in der Datei
 * @param ignored   gewählt, aber auf der Sperrliste, nicht eingereiht
 */
public record AbasSternzeitImportResult(long found, long filtered, long queued, long skipped, long ignored) {
}
