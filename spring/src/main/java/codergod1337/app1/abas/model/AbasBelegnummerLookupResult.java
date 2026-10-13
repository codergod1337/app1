package codergod1337.app1.abas.model;

/**
 * Die Antwort der Belegnummer: was die Nummer ist und wo sie in ABAS steht.
 *
 * @param belegnummer              die eingetippte Nummer, getrimmt
 * @param found                    kennt ABAS die Nummer?
 * @param sternzeit                die Sternzeit des Objekts, z. B. {@code (5168,4,0)}; null, wenn nicht gefunden
 * @param objectType               der rohe Typ, wie ABAS ihn in {@code meta.objectType} nennt, z. B. {@code obj:data:4:5};
 *                                 null, wenn nicht gefunden oder die Antwort ihn nicht trägt
 * @param responseMappingKey       der Key unseres Mappings zu diesem Typ, der Klartext der Oberfläche (ARTIKEL,
 *                                 KONTAKTE_FIRMA); null, wenn es keins gibt
 * @param responseMappingTargetKey das Ziel dieses Mappings (SOLR_ARTIKEL): daran erkennt die Oberfläche, in welche Mappe
 *                                 die primaryId führt; null ohne Mapping oder ohne Ziel
 * @param primaryId                die id, die das Mapping dieses Typs vergibt (beim Artikel die Artikelnummer, der Weg
 *                                 in die Mappe); null ohne Mapping oder ohne PRIMARY-Eintrag
 */
public record AbasBelegnummerLookupResult(String belegnummer, boolean found, String sternzeit, String objectType,
		String responseMappingKey, String responseMappingTargetKey, String primaryId) {
}
