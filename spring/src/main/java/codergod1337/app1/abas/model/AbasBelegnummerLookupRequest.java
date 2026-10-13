package codergod1337.app1.abas.model;

/**
 * Der Body von {@code POST /api/rest/v1/abas/belegnummer}: eine Nummer, von der niemand weiß, was sie ist, und ABAS
 * sagt es (User 2026-10-11, nach der Bezugsnummer der Vorlage).
 *
 * @param belegnummer die eingetippte Beleg- oder Bezugsnummer, z. B. {@code 600012}
 */
public record AbasBelegnummerLookupRequest(String belegnummer) {
}
