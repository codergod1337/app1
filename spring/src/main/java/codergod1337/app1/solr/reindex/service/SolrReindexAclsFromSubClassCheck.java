package codergod1337.app1.solr.reindex.service;

import codergod1337.app1.solr.reindex.model.SolrReindexCheck;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Die Prüfung readAcl und writeAcl gegen die Art des Dokuments (User 2026-10-14): im DMS die Dateiart
 * (fileSubClassKey), in den Nachrichten die Nachrichtenart (messageSubClassKey). Der Sollstand aller Arten kommt beim
 * Bauen einmal herein, je Dokument nur ein Vergleich im Speicher: Iststand und Sollstand als Mengen, fehlt ein Schlüssel
 * oder steht einer zu viel drin, sind sie ungleich. Nur dann wird gesetzt, und nur die abweichende Liste.
 *
 * Ein Dokument ohne Art bleibt unberührt, eines mit einer Art, die es nicht mehr gibt, auch; die steht einmal im Log.
 */
public class SolrReindexAclsFromSubClassCheck implements SolrReindexCheck {

	private static final Logger log = LoggerFactory.getLogger(SolrReindexAclsFromSubClassCheck.class);

	public static final String READ_ACL_FIELD_NAME = "readAcl";
	public static final String WRITE_ACL_FIELD_NAME = "writeAcl";

	private final String subClassFieldName;
	private final Map<String, List<String>> readAccessRoleKeysByKey;
	private final Map<String, List<String>> writeAccessRoleKeysByKey;

	/** Die Arten, die es nicht mehr gibt und schon im Log stehen */
	private final Set<String> reportedUnknownKeys = ConcurrentHashMap.newKeySet();

	/**
	 * @param subClassFieldName         das Feld mit dem key der Art, z. B. fileSubClassKey
	 * @param readAccessRoleKeysByKey   je key der Art die Soll-readAcl, nie null
	 * @param writeAccessRoleKeysByKey  je key der Art die Soll-writeAcl, nie null
	 */
	public SolrReindexAclsFromSubClassCheck(String subClassFieldName, Map<String, List<String>> readAccessRoleKeysByKey,
			Map<String, List<String>> writeAccessRoleKeysByKey) {
		this.subClassFieldName = subClassFieldName;
		this.readAccessRoleKeysByKey = Map.copyOf(readAccessRoleKeysByKey);
		this.writeAccessRoleKeysByKey = Map.copyOf(writeAccessRoleKeysByKey);
	}

	@Override
	public Set<String> loadedFieldNames() {
		return Set.of(subClassFieldName, READ_ACL_FIELD_NAME, WRITE_ACL_FIELD_NAME);
	}

	@Override
	public boolean addChanges(Map<String, Object> document, SolrInputDocument update) {
		Object key = document.get(subClassFieldName);
		if (key == null) {
			return false;
		}
		List<String> readAccessRoleKeys = readAccessRoleKeysByKey.get(key.toString());
		List<String> writeAccessRoleKeys = writeAccessRoleKeysByKey.get(key.toString());
		if (readAccessRoleKeys == null || writeAccessRoleKeys == null) {
			if (reportedUnknownKeys.add(key.toString())) {
				log.warn("Nachziehen: Art {} in {} gibt es nicht mehr, die ACLs dieser Dokumente bleiben", key, subClassFieldName);
			}
			return false;
		}
		boolean changed = false;
		if (!Set.copyOf(valuesOf(document.get(READ_ACL_FIELD_NAME))).equals(Set.copyOf(readAccessRoleKeys))) {
			update.addField(READ_ACL_FIELD_NAME, Map.of("set", readAccessRoleKeys));
			changed = true;
		}
		if (!Set.copyOf(valuesOf(document.get(WRITE_ACL_FIELD_NAME))).equals(Set.copyOf(writeAccessRoleKeys))) {
			update.addField(WRITE_ACL_FIELD_NAME, Map.of("set", writeAccessRoleKeys));
			changed = true;
		}
		return changed;
	}

	/** Die Werte eines Felds als Texte, egal ob Solr einen oder mehrere liefert; leer ohne Wert */
	private static List<String> valuesOf(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof Collection<?> values) {
			return values.stream().map(String::valueOf).toList();
		}
		return List.of(String.valueOf(value));
	}

}
