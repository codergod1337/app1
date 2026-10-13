package codergod1337.app1.abas.model;

import java.util.List;

/**
 * Der Body von {@code POST /api/rest/v1/admin/abas/sternzeit/raw}: die rohe Antwort von ABAS zu einer Kennung, alles im
 * Body, nichts in der URL (User 2026-10-10).
 *
 * @param sternzeit       die Kennung in Klammerform, z. B. (87448,19,0)
 * @param headFieldNames  die Kopffelder, die ABAS liefern soll; null oder leer heißt alle ({@code headFields=*}), mit
 *                        Namen wird die Antwort klein
 * @param tableFieldNames die Tabellenfelder; null oder leer heißt, der Parameter geht nicht mit
 */
public record AbasSternzeitRawRequest(String sternzeit, List<String> headFieldNames, List<String> tableFieldNames) {
}
