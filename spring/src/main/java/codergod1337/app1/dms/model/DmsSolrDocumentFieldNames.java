package codergod1337.app1.dms.model;

/**
 * Die Felder des Kerns DMS, mit denen das Backend rechnet, hart im Code (Vorgabe 2026-10-07): Pflichtfelder des Kerns.
 * Kein Check beim Start, fehlt eins in der Felddefinition, fliegen Fehler. Welche davon der Client setzen darf, steht
 * nicht hier, sondern als Schreibregel je Feld in der Felddefinition (SolrFieldClientWriteRule). readAcl und writeAcl
 * heißen in SolrCurrentUserCoreAccessService, id, cursorDate und userGrantedAccess in SolrCore.
 */
public final class DmsSolrDocumentFieldNames {

	/**
	 * Der Kern des DMS im SolrManager. Heißt wie das Modul und die AR, denn aus dem Kern-Key folgen die Rollen DMS,
	 * DMS_SUCHE und DMS_FULL (Entschieden 2026-10-03).
	 */
	public static final String CORE_KEY = "DMS";

	/** Der Status, ein EntityState des Bereichs DMS: ACTIVE, ARCHIVED, PENDING_ADMIN, DELETED (DmsStateService) */
	public static final String STATUS = "status";

	/** Die Versionskette als UUID, alle Fassungen einer Datei tragen dieselbe */
	public static final String VERSION_CHAIN = "versionChain";

	/** Die Nummer der Fassung in ihrer Kette, 1 beim Upload, plus eins je neuer Fassung */
	public static final String VERSION = "version";

	/** Die Dateiart (FileSubClass.key), aus ihr kommen readAcl und writeAcl */
	public static final String FILE_SUB_CLASS_KEY = "fileSubClassKey";

	public static final String FILE_NAME = "fileName";
	public static final String FILE_EXTENSION = "fileExtension";
	public static final String FILE_INDEX = "fileIndex";
	/** mehrsprachiges JSON */
	public static final String FILE_DESCRIPTION = "fileDescription";
	public static final String FILE_SIZE_BYTES = "fileSizeBytes";
	public static final String MIME_TYPE = "mimeType";
	public static final String SFTP_PATH = "sftpPath";
	public static final String DOCUMENT_LANGUAGE = "documentLanguage";
	public static final String DOCUMENT_UPLOADED_AT = "documentUploadedAt";
	public static final String DOCUMENT_CREATED_AT = "documentCreatedAt";
	public static final String LAST_CHANGED_AT = "lastChangedAt";
	/** die guid des Users, der die Datei hochgeladen hat */
	public static final String UPLOADER = "uploader";
	/** die guids aller User, die je an dem Dokument geschrieben haben */
	public static final String CHANGING_USERS = "changingUsers";
	public static final String DOWNLOAD_COUNT = "downloadCount";
	public static final String FILE_ID_REVISION_NO = "fileIdRevisionNo";
	public static final String SEARCHABLE_WORDS = "searchableWords";
	public static final String IMAGE_PREVIEW_BASE64_PNG = "bildVorschauBase64Png";
	public static final String OCR = "ocr";
	public static final String KI = "ki";
	public static final String IS_PUBLIC = "isPublic";

	private DmsSolrDocumentFieldNames() {
	}

}
