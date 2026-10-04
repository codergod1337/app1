package codergod1337.app1.solr.grant.model;

import java.util.List;

/**
 * Eine neue Feldfreigabe, wie die Maske sie schickt: Empfänger, Kern, die erweiterten Felder oder Hooks, optional ein
 * Ablauf nach ISO 8601. Das Format prüft der Controller, die Regeln der Service.
 */
public record NewAdvancedFieldGrantRequest(String granteeUsersGuid, String coreKey, List<String> fieldNames,
		String validUntil) {
}
