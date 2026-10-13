package codergod1337.app1.abas.model;

import java.util.Map;

/**
 * Der Body von {@code POST /api/rest/v1/admin/abas/infosys/raw}: einen hinterlegten Aufruf ausführen und die rohe
 * Antwort holen, alles im Body, nichts in der URL. Seit 2026-10-11 geht jede Infosystem-Anfrage über einen hinterlegten
 * Aufruf (User-Vorgabe): Gruppe, Name und Aktionen stehen in seiner Vorlage, hier kommen nur die Werte der Platzhalter.
 *
 * @param abasInfosysKey         der Key des hinterlegten Aufrufs ({@link AbasInfosys}), z. B. {@code PRODLIST}
 * @param valueByPlaceholderName je Platzhalter der Vorlage sein Wert, z. B. {@code {"artikelNummer": "10502057"}};
 *                               fehlt ein Platzhalter, 400 mit seinem Namen
 */
public record AbasInfosysRunRequest(String abasInfosysKey, Map<String, String> valueByPlaceholderName) {
}
