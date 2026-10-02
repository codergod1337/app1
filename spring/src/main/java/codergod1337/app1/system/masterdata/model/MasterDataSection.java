package codergod1337.app1.system.masterdata.model;

/**
 * Die Bereiche der Stammdaten, je eine Datei im ZIP mit einem Datensatz pro Zeile (JSONL). Die Reihenfolge ist die
 * des Imports: Was später kommt, verweist nur auf Früheres.
 *
 * Jede neue Entity, die im Admin gepflegt wird, kommt hier dazu, sonst fehlt sie in jedem Export.
 */
public enum MasterDataSection {

	ACCESS_ROLES("access-roles.jsonl"),
	ACCESS_ROLE_COLLECTIONS("access-role-collections.jsonl"),
	FILE_EXTENSION_COLLECTIONS("file-extension-collections.jsonl"),
	FILE_EXTENSIONS("file-extensions.jsonl"),
	FILE_SUB_CLASSES("file-sub-classes.jsonl"),
	USERS("users.jsonl"),
	USERS_DETAILS("users-details.jsonl"),
	USERS_SETTINGS("users-settings.jsonl"),
	ACCESS_ROLE_USERS_ASSIGNMENTS("access-role-users-assignments.jsonl"),
	ACCESS_ROLE_COLLECTION_USERS_ASSIGNMENTS("access-role-collection-users-assignments.jsonl");

	private final String fileName;

	MasterDataSection(String fileName) {
		this.fileName = fileName;
	}

	/** Der Name der Datei im ZIP */
	public String getFileName() {
		return fileName;
	}

}
