package codergod1337.app1.solr.field.model;

/**
 * Wer ein Feld beim Lesen bekommt. Nur in unserer Felddefinition, nie in Solr: Der Schema-Abgleich ignoriert es, die
 * Lesedienste bauen daraus je Request, welche Felder sie anfordern (fl). Wer ein Feld nicht lesen darf, darf auch nicht
 * danach auflösen, sortieren oder facettieren.
 */
public enum SolrFieldReadAccessLevel {

	/**
	 * Grundansicht des Kerns: Wer ein Dokument überhaupt sehen darf (readAcl oder userGrantedAccess), bekommt das Feld
	 * immer, auch ohne Vollsicht. id ist immer Minimum.
	 */
	MINIMUM_SET_EVERY_READER,

	/**
	 * Nur mit Vollsicht ({@code <KERN>_FULL} oder {@code <KERN>_SUCHE}) oder mit einer Feldfreigabe für diesen Kern
	 * (SolrAdvancedFieldGrantAccessPerCore). Alle anderen sehen das Feld nie. Standard für neue Felder.
	 */
	ADVANCED_FULL_SIGHT_OR_FIELD_GRANT

}
