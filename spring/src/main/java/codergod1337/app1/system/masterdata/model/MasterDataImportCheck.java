package codergod1337.app1.system.masterdata.model;

import java.util.List;

/**
 * Was ein Import tun würde, oder nach dem Import: was er getan hat. Probelauf und Import rechnen genau gleich.
 *
 * @param ready          nichts blockiert, der Import darf schreiben
 * @param blockers       was den ganzen Import verhindert, z. B. kein gültiges ZIP oder ein unbekanntes Format
 * @param formatVersion  Version des Formats laut manifest.json, null ohne lesbares Manifest
 * @param exportedAt     wann exportiert wurde, laut Manifest
 * @param exportedBy     wer exportiert hat, laut Manifest
 * @param sections       je Bereich die Zeilen mit ihrem Ergebnis, in der Reihenfolge des Imports
 * @param usersConflicts User, die es bei uns unter derselben E-Mail, aber anderer guid gibt
 * @param adminGrants    wer durch den Import ADMIN bekäme, direkt oder über eine ARC
 */
public record MasterDataImportCheck(boolean ready, List<String> blockers, Integer formatVersion, String exportedAt,
		String exportedBy, List<SectionCheck> sections, List<UsersConflict> usersConflicts, List<String> adminGrants) {

	/** Was mit einer Zeile passiert */
	public enum RowStatus {
		/** wird angelegt, sofern der Bereich angehakt ist */
		NEW,
		/** gibt es schon, wird übersprungen */
		EXISTING,
		/** bewusst nicht übernommen, z. B. im Konflikt eines Users abgewählt */
		SKIPPED,
		/** ungültig: Pflichtfeld, Format oder doppelt in der Datei */
		INVALID,
		/** verwaist: das Ziel gibt es weder bei uns noch im Import */
		ORPHAN,
		/** passt nicht zu dem, was bei uns steht, z. B. der User hat schon eine andere ARC */
		CONFLICT
	}

	/**
	 * Ein Bereich der Datei.
	 *
	 * @param present ob die Datei im ZIP steckt
	 * @param selected ob der Bereich zum Schreiben angehakt ist
	 */
	public record SectionCheck(MasterDataSection section, boolean present, boolean selected, int newCount,
			int existingCount, int skippedCount, int problemCount, List<RowCheck> rows) {
	}

	/**
	 * Eine Zeile der Datei.
	 *
	 * @param id          was sie eindeutig macht, z. B. der key, die guid oder guid und key
	 * @param label       lesbar, z. B. die E-Mail des Users
	 * @param displayName mehrsprachiges JSON, falls die Entity einen Namen hat, sonst null
	 * @param messages    was dazu zu sagen ist: Fehler, weggelassene Verweise, Abweichungen von unserem Stand
	 */
	public record RowCheck(String id, String label, String displayName, RowStatus status, List<String> messages) {
	}

	/**
	 * Ein User aus der Datei, den es bei uns unter derselben E-Mail, aber anderer guid gibt. Unser System bleibt, wie es
	 * ist: Was aus der Datei an ihm hängt, geht an unsere guid, soweit es angehakt ist.
	 *
	 * @param details              die Details aus der Datei, null wenn sie keine hat
	 * @param accessRoleCollection die ARC aus der Datei, null wenn sie keine hat
	 */
	public record UsersConflict(String fileGuid, String existingGuid, String email, String label,
			ConflictItem details, List<ConflictItem> settings, List<ConflictItem> accessRoles,
			ConflictItem accessRoleCollection) {
	}

	/**
	 * Ein Teil eines Users im Konflikt.
	 *
	 * @param key     z. B. der Key der Einstellung oder der AR
	 * @param status  was passieren würde, wenn er übernommen wird: NEW, EXISTING, ORPHAN oder CONFLICT
	 * @param taken   ob er laut Auswahl übernommen wird
	 * @param message z. B. warum er nicht übernommen werden kann
	 */
	public record ConflictItem(String key, RowStatus status, boolean taken, String message) {
	}

}
