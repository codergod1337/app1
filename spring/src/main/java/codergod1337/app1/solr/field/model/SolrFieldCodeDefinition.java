package codergod1337.app1.solr.field.model;

import java.util.List;

/**
 * Ein Feld, wie der handgemachte Schema-Initialisierer eines Kerns es im Code festlegt (Vorgabe 2026-10-08): Der Code
 * ist die Wahrheit für alles, was Solr braucht und was das Backend mit dem Feld tut, Postgres die Wahrheit für das, was
 * der Admin im Kern-Tab pflegt. Beim Start schreibt der Initialisierer jede Definition nach solr_field:
 *
 * <ul>
 * <li>Gibt es die Zeile nicht, entsteht sie komplett aus dieser Definition, auch readAccessRoleKeys und listingPosition
 * als Startwerte.</li>
 * <li>Gibt es sie, zieht der Code type, die Solr-Schalter, suggest, backendOnly, clientWriteRule, displayName und
 * description nach. readAccessRoleKeys und listingPosition bleiben, wie der Admin sie gesetzt hat; nur eine leere
 * AR-Liste bekommt die Startwerte, damit ein Feld nicht aus Versehen für niemanden lesbar bleibt.</li>
 * </ul>
 *
 * @param coreKey             der Kern, null heißt in jedem Kern
 * @param name                der Feldname in Solr, eindeutig über alle Kerne
 * @param readAccessRoleKeys  Startwert: die ARs, die das Feld lesen dürfen
 * @param backendOnly         nie zum Client
 * @param displayName         mehrsprachiges JSON de, en, bg
 * @param description         mehrsprachiges JSON de, en, bg
 */
public record SolrFieldCodeDefinition(String coreKey, String name, SolrFieldType type, boolean indexed,
		boolean stored, boolean multiValued, boolean docValues, boolean required, boolean suggest,
		List<String> readAccessRoleKeys, boolean backendOnly, SolrFieldClientWriteRule clientWriteRule,
		String displayName, String description, int listingPosition) {

	public SolrFieldCodeDefinition {
		readAccessRoleKeys = readAccessRoleKeys != null ? List.copyOf(readAccessRoleKeys) : List.of();
	}

}
