package codergod1337.app1.solr.grant.model;

import java.util.List;

/**
 * Eine neue Hook-Freigabe, wie die Maske sie schickt: Empfänger, die Hooks, optional ein Ablauf nach ISO 8601. Kein
 * Kern, die Freigabe gilt serverweit. Das Format prüft der Controller, die Regeln der Service.
 */
public record NewHookGrantRequest(String granteeUsersGuid, List<String> hookKeys, String validUntil) {
}
