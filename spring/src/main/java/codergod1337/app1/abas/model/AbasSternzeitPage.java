package codergod1337.app1.abas.model;

import java.util.List;

/**
 * Eine Seite der Übersicht, geblättert per Keyset über die Kennung.
 *
 * @param sternzeiten   die Zeilen, aufsteigend nach Kennung
 * @param lastSternzeit die letzte Kennung dieser Seite, der Anker für die nächste; null bei leerer Seite
 * @param hasMore       ob hinter dieser Seite noch Zeilen liegen
 */
public record AbasSternzeitPage(List<AbasSternzeitForClient> sternzeiten, String lastSternzeit, boolean hasMore) {
}
