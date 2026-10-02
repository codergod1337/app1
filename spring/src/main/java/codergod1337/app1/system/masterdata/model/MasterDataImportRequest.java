package codergod1337.app1.system.masterdata.model;

import java.util.Map;
import java.util.Set;

/**
 * Prüfen und Importieren bekommen dasselbe: das ZIP, die angehakten Bereiche und die Auswahl bei Usern, die es bei uns
 * unter anderer guid schon gibt.
 *
 * @param zip                   das ZIP als Base64, Jackson macht daraus die Bytes
 * @param sections              was geschrieben werden soll. Verweise auf nicht angehakte Bereiche gelten nur, wenn das
 *                              Ziel schon in der Datenbank steht.
 * @param usersConflictDecisions je guid aus der Datei: was davon an unseren User geht. Fehlt ein User, geht alles Neue.
 */
public record MasterDataImportRequest(byte[] zip, Set<MasterDataSection> sections,
		Map<String, UsersConflictDecision> usersConflictDecisions) {

	/**
	 * Die Auswahl bei einem User, der bei uns unter derselben E-Mail, aber anderer guid existiert. Was angehakt ist,
	 * bekommt unser User, vorausgesetzt, er hat es noch nicht.
	 *
	 * @param details              die Details übernehmen
	 * @param settingKeys          diese Einstellungen übernehmen
	 * @param accessRoleKeys       diese AR zuordnen
	 * @param accessRoleCollection die ARC zuordnen
	 */
	public record UsersConflictDecision(boolean details, Set<String> settingKeys, Set<String> accessRoleKeys,
			boolean accessRoleCollection) {
	}

}
