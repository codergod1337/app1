package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsDocumentLanguages;
import codergod1337.app1.dms.service.PreviewImageService.NoImageDataException;
import codergod1337.app1.dms.service.PreviewImageService.PreviewImage;
import codergod1337.app1.dms.service.TikaService.TikaReadException;
import codergod1337.app1.dms.service.TikaService.TikaResult;
import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.document.service.SolrDocumentChecker;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.sftp.service.SftpPathService;
import codergod1337.app1.system.sftp.service.SftpService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Der Upload ins DMS. Zwei Wege, eine Verarbeitung: {@link #probeUpload} prüft, liest und verwirft (bleibt dauerhaft,
 * z. B. um die Spracherkennung zu testen), {@link #uploadFile} tut dasselbe und speichert danach. Gemeinsam ist
 * {@code prepare}: die Felder gegen den Kern DMS und die Regeln des DMS prüfen, die Datei mit Tika lesen, den SHA-256
 * rechnen, bei Bildern das Vorschaubild. Speichern heißt: Schreibrecht an der Dateiart, Dublette über den Hash, Bytes
 * auf den SFTP, Dokument nach Solr.
 *
 * Wer das darf: jeder mit der AR DMS. Welche Felder der User schicken darf, entscheidet später die Maske je Fall; hier
 * darf er jedes Feld des Kerns und jeden Hook schicken, nur nicht die Felder, die das Backend setzt.
 */
@Service
public class DmsUploadService {

	private static final Logger log = LoggerFactory.getLogger(DmsUploadService.class);

	/**
	 * Der Kern des DMS im SolrManager. Seine Felder sind die Maske. Heißt wie das Modul und die AR, denn aus dem Kern-Key
	 * folgen die Rollen DMS, DMS_SUCHE und DMS_FULL (Entschieden 2026-10-03, vorher FILE).
	 */
	public static final String CORE_KEY = "DMS";

	/**
	 * Felder, die nur das Backend setzt. Schickt der Client sie, ist das ein Fehler (400), kein stilles Überschreiben:
	 * fileName und fileExtension kommen aus dem Namen der hochgeladenen Datei, mimeType aus Tika (erkannt an den Bytes,
	 * nicht an der Endung), documentUploadedAt und sftpPath entstehen beim Speichern, readAcl und writeAcl kommen aus
	 * der Dateiart, ocr und ki sind vorerst pauschal false, fileIdRevisionNo zählt die Revisionen,
	 * bildVorschauBase64Png rechnet der PreviewImageService. uploader ist die guid des current users, changingUsers und
	 * lastChangedAt schreibt der Bearbeiten-Service bei jedem Atomic Update (guid des current users, jetzt), version
	 * vergibt das Speichern in einer Versionskette (die neue Fassung bekommt die nächsthöhere Nummer), downloadCount
	 * beginnt bei 0 und zählt nur der Download (bei einem Merge bleibt der Zähler des vorhandenen Dokuments). Die
	 * eingebauten Felder id, cursorDate und userGrantedAccess (die Dateifreigabe) lehnt schon der SolrDocumentChecker ab.
	 * Dieselbe Liste im Frontend: DmsFields.ts.
	 */
	public static final Set<String> BACKEND_FIELD_NAMES = Set.of("fileName", "fileExtension", "mimeType",
			"documentUploadedAt", "sftpPath", "readAcl", "writeAcl", "ocr", "ki", "fileIdRevisionNo",
			"bildVorschauBase64Png", "uploader", "changingUsers", "lastChangedAt", "version", "downloadCount");

	/** Der Status. Ist er dabei, muss er ein Status des Bereichs DMS sein (EntityState), sonst 400. */
	public static final String STATUS_FIELD_NAME = "status";

	/**
	 * Die Versionskette: Nur bei einer neuen Fassung schickt das Frontend die Kette der Originaldatei mit, dann muss
	 * sie eine UUID sein. Fehlt sie, ist die Datei neu, und das Speichern vergibt eine zufällige UUID.
	 */
	public static final String VERSION_CHAIN_FIELD_NAME = "versionChain";

	/** Die Größe liest das Frontend aus der Datei und schickt sie mit. Sie muss zur Datei passen, sonst 400. */
	public static final String FILE_SIZE_FIELD_NAME = "fileSizeBytes";

	/** Die Dateiart. Ist sie dabei, muss sie die Endung der Datei freigeschaltet haben, sonst 400. Beim Speichern Pflicht. */
	public static final String FILE_SUB_CLASS_FIELD_NAME = "fileSubClassKey";

	/**
	 * Die Sprache des Dokuments, ISO 639-1. Nur in Endungsgruppen mit Sprache, aus der Liste in DmsDocumentLanguages,
	 * vom Client oder aus dem Text erkannt, sonst leer. Die Regel wendet DmsDocumentLanguageService an.
	 */
	public static final String DOCUMENT_LANGUAGE_FIELD_NAME = "documentLanguage";

	private final SolrCoreService solrCoreService;
	private final SolrDocumentChecker solrDocumentChecker;
	private final SolrDocumentService solrDocumentService;
	private final SolrHookService solrHookService;
	private final FileSubClassService fileSubClassService;
	private final FileExtensionService fileExtensionService;
	private final DmsStateService dmsStateService;
	private final TikaService tikaService;
	private final PreviewImageService previewImageService;
	private final SftpService sftpService;
	private final SftpPathService sftpPathService;
	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final CurrentUsersProvider currentUsersProvider;
	private final DmsDocumentLanguageService dmsDocumentLanguageService;

	public DmsUploadService(SolrCoreService solrCoreService, SolrDocumentChecker solrDocumentChecker,
			SolrDocumentService solrDocumentService, SolrHookService solrHookService,
			FileSubClassService fileSubClassService, FileExtensionService fileExtensionService,
			DmsStateService dmsStateService, TikaService tikaService, PreviewImageService previewImageService,
			SftpService sftpService, SftpPathService sftpPathService, SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService,
			CurrentUsersProvider currentUsersProvider, DmsDocumentLanguageService dmsDocumentLanguageService) {
		this.dmsDocumentLanguageService = dmsDocumentLanguageService;
		this.solrCoreService = solrCoreService;
		this.solrDocumentChecker = solrDocumentChecker;
		this.solrDocumentService = solrDocumentService;
		this.solrHookService = solrHookService;
		this.fileSubClassService = fileSubClassService;
		this.fileExtensionService = fileExtensionService;
		this.dmsStateService = dmsStateService;
		this.tikaService = tikaService;
		this.previewImageService = previewImageService;
		this.sftpService = sftpService;
		this.sftpPathService = sftpPathService;
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.currentUsersProvider = currentUsersProvider;
	}

	// ===== Antworten =====

	/**
	 * Das Ergebnis der Probe: die Datei, wie der Browser sie schickte, ihre Endung nach unserer Regel, ihr SHA-256
	 * (die id des Dokuments), die geprüften Felder in der Reihenfolge des Kerns, alles von Tika und das Vorschaubild.
	 * stored sagt, ob die Datei danach gespeichert wurde: false bei der Probe, true im Upload.
	 */
	public record DmsUploadProbe(String fileName, String fileExtension, String contentType, long sizeBytes,
			String sha256, Map<String, Object> document, TikaOutcome tika, PreviewOutcome preview, boolean stored) {
	}

	/** Was Tika fand, oder warum nicht: ok false mit error, dann sind die anderen Angaben leer. */
	public record TikaOutcome(boolean ok, String error, String mimeType, Instant createdAt, Instant modifiedAt,
			Integer orientation, int textLength, String text, Map<String, List<String>> metadata, long durationMs) {

		static TikaOutcome of(TikaResult result) {
			return new TikaOutcome(true, null, result.mimeType(), result.createdAt(), result.modifiedAt(),
					result.orientation(), result.text().length(), result.text(), result.metadata(),
					result.durationMs());
		}

		static TikaOutcome failed(String error) {
			return new TikaOutcome(false, error, null, null, null, null, 0, "", Map.of(), 0);
		}

	}

	/**
	 * Das Vorschaubild, oder warum keins: attempted false, wenn die Endung im Katalog kein Vorschaubild hat; ok false
	 * mit error, wenn die Bytes kein lesbares Bild sind. base64Png ist rohes Base64 ohne Präfix.
	 */
	public record PreviewOutcome(boolean attempted, boolean ok, String error, String base64Png, int sourceWidth,
			int sourceHeight, int orientation, int bytes, long durationMs) {

		static PreviewOutcome of(PreviewImage image, long durationMs) {
			return new PreviewOutcome(true, true, null, image.base64Png(), image.sourceWidth(), image.sourceHeight(),
					image.orientation(), image.bytes(), durationMs);
		}

		static PreviewOutcome skipped() {
			return new PreviewOutcome(false, false, null, null, 0, 0, 0, 0, 0);
		}

		static PreviewOutcome failed(String error, long durationMs) {
			return new PreviewOutcome(true, false, error, null, 0, 0, 0, 0, durationMs);
		}

	}

	/** Was der Upload tat */
	public enum UploadStatus {
		/** neues Dokument, Bytes auf dem SFTP, Dokument in Solr */
		CREATED,
		/** dieselben Bytes lagen schon in derselben Dateiart: nur die Hooks ergänzt, nichts hochgeladen */
		MERGED,
		/** dieselben Bytes liegen unter einer anderen Dateiart: nichts geändert, die Dateiart wechselt nur das Bearbeiten */
		EXISTS
	}

	/** Das Ergebnis des Uploads: was passiert ist, die id, das Dokument wie es jetzt in Solr steht, dazu die Probe. */
	public record DmsUploadResult(UploadStatus status, String id, Map<String, Object> document, DmsUploadProbe probe) {
	}

	/** Gibt es diese Bytes schon im DMS? Mit id, Dateiart und Status des vorhandenen Dokuments. */
	public record DmsExistence(boolean exists, String id, String fileSubClassKey, String status) {
	}

	/** Die gemeinsame Vorarbeit beider Wege */
	private record PreparedUpload(Map<String, Object> document, FileSubClass fileSubClass, String fileName,
			String fileExtension, String contentType, long sizeBytes, String sha256, TikaOutcome tika,
			PreviewOutcome preview) {

		DmsUploadProbe toProbe(boolean stored) {
			return new DmsUploadProbe(fileName, fileExtension, contentType, sizeBytes, sha256, document, tika, preview,
					stored);
		}

	}

	// ===== Die beiden Wege =====

	/** Die Probe: prüfen, lesen, rechnen, verwerfen. Ein Fehler von Tika oder beim Vorschaubild steht in der Antwort. */
	@PreAuthorize("hasRole('DMS')")
	public DmsUploadProbe probeUpload(JsonNode data, MultipartFile file) {
		return prepare(data, file).toProbe(false);
	}

	/**
	 * Der Upload: wie die Probe, dann speichern. Die Dateiart ist Pflicht, der User braucht ihr Schreibrecht. Dieselben
	 * Bytes gibt es im DMS genau einmal, ihre id ist der Hash: Liegen sie schon in dieser Dateiart, werden nur die
	 * Hooks ergänzt (MERGED), in einer anderen passiert nichts (EXISTS). Sonst gehen die Bytes auf den SFTP und das
	 * Dokument nach Solr (CREATED). Erst der SFTP, dann Solr: Ein Dokument ohne Datei wäre eine Lüge, eine Datei ohne
	 * Dokument nur Platz.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsUploadResult uploadFile(JsonNode data, MultipartFile file) {
		PreparedUpload prepared = prepare(data, file);
		FileSubClass fileSubClass = prepared.fileSubClass();
		if (fileSubClass == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, FILE_SUB_CLASS_FIELD_NAME + " fehlt");
		}
		requireWriteAccess(fileSubClass);
		// uploader und changingUsers tragen die guid, nicht den username: Der kann sich ändern, die guid nie
		String usersGuid = currentUsersProvider.getCurrentUsersGuid().toString();

		Map<String, Object> existing = solrDocumentService.get(CORE_KEY, prepared.sha256());
		if (existing != null) {
			return mergeOrRefuse(prepared, existing, usersGuid);
		}

		Map<String, Object> document = buildDocument(prepared, fileSubClass, usersGuid);
		String sftpPath = (String) document.get("sftpPath");
		try (InputStream in = file.getInputStream()) {
			sftpService.writeFileData(in, sftpPath);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Datei konnte nicht gelesen werden");
		}
		solrDocumentService.write(CORE_KEY, document);
		log.info("Upload: {} als {} ({} Bytes) in Dateiart {} von {}, {}", prepared.fileName(), prepared.sha256(),
				prepared.sizeBytes(), fileSubClass.getKey(), usersGuid, sftpPath);
		return new DmsUploadResult(UploadStatus.CREATED, prepared.sha256(), solrDocumentService.get(CORE_KEY, prepared.sha256()),
				prepared.toProbe(true));
	}

	/** Gibt es diese Bytes schon? Für die Vorprüfung der Maske, bevor sie hochlädt. */
	@PreAuthorize("hasRole('DMS')")
	public DmsExistence exists(String sha256) {
		Map<String, Object> existing = solrDocumentService.get(CORE_KEY, sha256);
		if (existing == null) {
			return new DmsExistence(false, null, null, null);
		}
		return new DmsExistence(true, sha256, text(existing, FILE_SUB_CLASS_FIELD_NAME), text(existing, STATUS_FIELD_NAME));
	}

	// ===== Vorarbeit =====

	/**
	 * Prüft data gegen den Kern DMS (400 mit allen Beanstandungen) und die Regeln des DMS, dann liest es die Datei
	 * einmal durch: Tika nimmt sich, was es braucht, der Rest wird nur noch für den Hash gelesen. Hat die Endung im
	 * Katalog ein Vorschaubild, wird die Datei dafür ein zweites Mal gelesen.
	 */
	private PreparedUpload prepare(JsonNode data, MultipartFile file) {
		// Ohne den Kern gibt es keine Maske, die Meldung des Service reicht
		solrCoreService.getSolrCoreByKey(CORE_KEY);
		requireNoBackendFields(data);
		// eine eigene Kopie: Die Sprachregel schreibt gleich hinein
		Map<String, Object> document = new LinkedHashMap<>(solrDocumentChecker.checkDocument(CORE_KEY, data));
		requireVersionChainIsUuid(document);
		requireFileSizeMatches(document, file);
		requireStatusKnown(document);

		String fileName = file.getOriginalFilename();
		String fileExtension = extensionOf(fileName);
		FileSubClass fileSubClass = fileSubClassOf(document);
		requireExtensionAllowed(fileSubClass, fileExtension);

		MessageDigest digest = sha256();
		TikaOutcome tika;
		try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
			tika = readWithTika(in, fileName, file.getContentType());
			// Der Hash braucht jedes Byte, auch die, die Tika nicht mehr gelesen hat
			in.transferTo(OutputStream.nullOutputStream());
		} catch (IOException e) {
			log.warn("Upload: Datei {} nicht lesbar: {}", fileName, e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Datei konnte nicht gelesen werden");
		}
		String sha256 = HexFormat.of().formatHex(digest.digest());
		applyDocumentLanguageRule(document, fileExtension, tika);
		PreviewOutcome preview = renderPreview(file, fileExtension, tika.orientation());

		log.info("Upload-Vorarbeit: {} ({} Bytes, sha256 {}), {} Felder, Tika {} in {} ms, Vorschau {}", fileName,
				file.getSize(), sha256, document.size(), tika.ok() ? tika.mimeType() : "fehlgeschlagen",
				tika.durationMs(), preview.attempted() ? (preview.ok() ? preview.bytes() + " Bytes" : "fehlgeschlagen")
						: "keine");
		return new PreparedUpload(document, fileSubClass, fileName, fileExtension, file.getContentType(),
				file.getSize(), sha256, tika, preview);
	}

	// ===== Speichern =====

	/**
	 * Das Dokument, wie es nach Solr geht: die geprüften Felder des Clients, darüber die Felder des Backends. Die
	 * Reihenfolge ist egal, Solr kennt keine.
	 */
	private Map<String, Object> buildDocument(PreparedUpload prepared, FileSubClass fileSubClass, String usersGuid) {
		Instant now = Instant.now();
		Map<String, Object> document = new LinkedHashMap<>(prepared.document());
		document.put(SolrDocumentService.ID_FIELD_NAME, prepared.sha256());
		document.putIfAbsent(STATUS_FIELD_NAME, DmsStateService.ACTIVE);
		document.put("fileName", normalizeFileName(prepared.fileName(), prepared.fileExtension()));
		document.put("fileExtension", prepared.fileExtension());
		document.put("mimeType", prepared.tika().ok() && prepared.tika().mimeType() != null ? prepared.tika().mimeType()
				: prepared.contentType());
		document.put(FILE_SIZE_FIELD_NAME, prepared.sizeBytes());
		document.put("sftpPath", sftpPathService.relativePath(prepared.sha256()));
		document.put("uploader", usersGuid);
		document.put("changingUsers", List.of(usersGuid));
		document.put("documentUploadedAt", now);
		// Erstelldatum aus der Datei, sonst der Upload: Das Feld ist immer gefüllt, die Trefferliste sortiert danach
		document.put("documentCreatedAt",
				prepared.tika().ok() && prepared.tika().createdAt() != null ? prepared.tika().createdAt() : now);
		document.put("lastChangedAt", now);
		document.put("downloadCount", 0L);
		document.put("fileIdRevisionNo", 0L);
		document.put("version", 1L);
		document.putIfAbsent(VERSION_CHAIN_FIELD_NAME, UUID.randomUUID().toString());
		// die AR-Keys der Dateiart als Liste, ohne Präfix (Entschieden 2026-10-03): dieselbe Form liest die Grenze
		document.put(SolrCurrentUserCoreAccessService.READ_ACL_FIELD_NAME, accessRoleKeysOf(fileSubClass.getReadAccessRoleKeys()));
		document.put(SolrCurrentUserCoreAccessService.WRITE_ACL_FIELD_NAME, accessRoleKeysOf(fileSubClass.getWriteAccessRoleKeys()));
		document.putIfAbsent("isPublic", false);
		document.put("ocr", false);
		document.put("ki", false);
		// documentLanguage steht schon im Dokument oder fehlt mit Absicht: die Regel lief in prepare
		if (prepared.tika().ok() && !prepared.tika().text().isBlank()) {
			// Vorerst der rohe Text, Solr setzt ihn klein. Stemming und Komposita kommen mit der Textaufbereitung.
			document.put("searchableWords", prepared.tika().text());
		}
		if (prepared.preview().ok()) {
			document.put("bildVorschauBase64Png", prepared.preview().base64Png());
		}
		return document;
	}

	/**
	 * Dieselben Bytes gibt es schon. Gleiche Dateiart: Die Hooks aus dem Upload kommen dazu, nichts fällt weg, der
	 * current user steht in changingUsers. Andere Dateiart: nichts, die Dateiart eines Dokuments wechselt nur das
	 * Bearbeiten, denn sie entscheidet über die Rechte. Mergen ist ein Schreibvorgang am vorhandenen Dokument: Dessen
	 * writeAcl muss eine AR des Users enthalten, sonst wird nichts geschrieben.
	 */
	private DmsUploadResult mergeOrRefuse(PreparedUpload prepared, Map<String, Object> existing, String usersGuid) {
		String existingFileSubClassKey = text(existing, FILE_SUB_CLASS_FIELD_NAME);
		if (!prepared.fileSubClass().getKey().equals(existingFileSubClassKey)) {
			log.info("Upload: {} liegt schon unter Dateiart {}, nichts geändert", prepared.sha256(), existingFileSubClassKey);
			return new DmsUploadResult(UploadStatus.EXISTS, prepared.sha256(), existing, prepared.toProbe(false));
		}
		solrCurrentUserCoreAccessService.requireWriteAclMatchOfCurrentUserForDocument(CORE_KEY, prepared.sha256());
		Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			Object values = prepared.document().get(solrHook.getKey());
			if (values instanceof List<?> list && !list.isEmpty()) {
				changes.put(solrHook.getKey(), Map.of("add-distinct", list));
			}
		}
		changes.put("changingUsers", Map.of("add-distinct", usersGuid));
		changes.put("lastChangedAt", Map.of("set", Instant.now()));
		solrDocumentService.atomicUpdate(CORE_KEY, prepared.sha256(), changes);
		log.info("Upload: {} lag schon in Dateiart {}, {} Hooks ergänzt von {}", prepared.sha256(),
				existingFileSubClassKey, changes.size() - 2, usersGuid);
		return new DmsUploadResult(UploadStatus.MERGED, prepared.sha256(),
				solrDocumentService.get(CORE_KEY, prepared.sha256()), prepared.toProbe(false));
	}

	/**
	 * Die Sprache nach der Regel der Endungsgruppe (DmsDocumentLanguageService): In einer Gruppe ohne Sprache ist ein Wert
	 * vom Client ein Fehler, in einer mit Sprache muss er aus der Liste sein. Schickt der Client keinen, erkennt das
	 * Backend sie aus dem Text von Tika, sonst bleibt das Feld leer. Nie ein geratener Standard.
	 */
	private void applyDocumentLanguageRule(Map<String, Object> document, String fileExtension, TikaOutcome tika) {
		Object sent = document.get(DOCUMENT_LANGUAGE_FIELD_NAME);
		String sentCode = sent == null || sent.toString().isBlank() ? null : sent.toString();
		document.remove(DOCUMENT_LANGUAGE_FIELD_NAME);
		String problem = dmsDocumentLanguageService.problemWithDocumentLanguage(sentCode, fileExtension);
		if (problem != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
		}
		if (sentCode != null) {
			document.put(DOCUMENT_LANGUAGE_FIELD_NAME, DmsDocumentLanguages.normalizeCode(sentCode));
			return;
		}
		if (tika.ok() && dmsDocumentLanguageService.documentLanguageAppliesToFileExtension(fileExtension)) {
			String detected = dmsDocumentLanguageService.detectDocumentLanguageCode(tika.text());
			if (detected != null) {
				document.put(DOCUMENT_LANGUAGE_FIELD_NAME, detected);
			}
		}
	}

	/** Schreibrecht an der Dateiart: eine der Schreibrollen muss im Token stehen */
	private void requireWriteAccess(FileSubClass fileSubClass) {
		List<String> writeAccessRoleKeys = fileSubClass.getWriteAccessRoleKeys();
		List<String> own = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (writeAccessRoleKeys == null || writeAccessRoleKeys.stream().noneMatch(own::contains)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"kein Schreibrecht an der Dateiart " + fileSubClass.getKey());
		}
	}

	/** Eine Kopie der AR-Keys, null wird zur leeren Liste: Eine Dateiart ohne Leserolle liest niemand */
	private static List<String> accessRoleKeysOf(List<String> accessRoleKeys) {
		return accessRoleKeys == null ? List.of() : new ArrayList<>(accessRoleKeys);
	}

	// ===== Regeln =====

	/** Die Endung nach unserer Regel: nach dem letzten Punkt, klein geschrieben. Ohne Punkt leer. */
	static String extensionOf(String fileName) {
		if (fileName == null) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/**
	 * Der Dateiname fürs Dokument, nach der Regel der Vorlage: klein, Umlaute aufgelöst, Leerraum zu _, sonst nur
	 * a-z, 0-9, _ und -, dazu die Endung. So ist er wortweise suchbar und nie ein Pfad.
	 */
	static String normalizeFileName(String fileName, String fileExtension) {
		if (fileName == null || fileName.isBlank()) {
			return "datei";
		}
		int dot = fileName.lastIndexOf('.');
		String base = !fileExtension.isEmpty() && dot > 0 ? fileName.substring(0, dot) : fileName;
		base = base.toLowerCase(Locale.ROOT)
				.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
				.replaceAll("\\s+", "_")
				.replaceAll("[^a-z0-9_-]", "");
		if (base.isBlank()) {
			base = "datei";
		}
		String extension = fileExtension.replaceAll("[^a-z0-9]", "");
		return extension.isEmpty() ? base : base + "." + extension;
	}

	/** 400 mit allen Feldern des Backends, die der Client trotzdem geschickt hat */
	private static void requireNoBackendFields(JsonNode data) {
		if (data == null || !data.isObject()) {
			return; // das meldet der SolrDocumentChecker
		}
		List<String> sent = new ArrayList<>();
		for (Map.Entry<String, JsonNode> property : data.properties()) {
			if (BACKEND_FIELD_NAMES.contains(property.getKey())) {
				sent.add(property.getKey());
			}
		}
		if (!sent.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"setzt das Backend, nicht der Client: " + String.join(", ", sent));
		}
	}

	/** Eine mitgeschickte Versionskette muss eine UUID sein, sonst zeigt die neue Fassung ins Leere */
	private static void requireVersionChainIsUuid(Map<String, Object> document) {
		Object versionChain = document.get(VERSION_CHAIN_FIELD_NAME);
		if (versionChain == null) {
			return;
		}
		try {
			UUID.fromString(versionChain.toString());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, VERSION_CHAIN_FIELD_NAME + " ist keine UUID");
		}
	}

	/** Eine mitgeschickte Größe muss die der Datei sein: Weicht sie ab, hat das Frontend eine andere Datei gemeint */
	private static void requireFileSizeMatches(Map<String, Object> document, MultipartFile file) {
		Object sizeBytes = document.get(FILE_SIZE_FIELD_NAME);
		if (sizeBytes instanceof Long sent && sent != file.getSize()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					FILE_SIZE_FIELD_NAME + " (" + sent + ") passt nicht zur Datei (" + file.getSize() + " Bytes)");
		}
	}

	/** Ein mitgeschickter Status muss einer des Bereichs DMS sein, sonst steht im Dokument ein Status ohne Namen */
	private void requireStatusKnown(Map<String, Object> document) {
		Object status = document.get(STATUS_FIELD_NAME);
		if (status != null && !dmsStateService.isDmsState(status.toString())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					STATUS_FIELD_NAME + " " + status + " ist kein Status des Bereichs " + DmsStateService.SCOPE);
		}
	}

	/** Die Dateiart aus dem Dokument, null ohne. Eine unbekannte meldet der Service (404). */
	private FileSubClass fileSubClassOf(Map<String, Object> document) {
		Object fileSubClassKey = document.get(FILE_SUB_CLASS_FIELD_NAME);
		return fileSubClassKey == null ? null : fileSubClassService.getFileSubClassByKey(fileSubClassKey.toString());
	}

	/**
	 * Ist eine Dateiart dabei, muss sie die Endung freigeschaltet haben (FileMatrix). Eine Dateiart ohne Endungen nimmt
	 * nichts an. Die Maske prüft dasselbe vorher, das Backend verlässt sich nicht darauf.
	 */
	private static void requireExtensionAllowed(FileSubClass fileSubClass, String fileExtension) {
		if (fileSubClass == null) {
			return;
		}
		List<String> allowed = fileSubClass.getExtensions();
		if (allowed == null || !allowed.contains(fileExtension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Endung "
					+ (fileExtension.isEmpty() ? "(keine)" : fileExtension) + " ist bei der Dateiart "
					+ fileSubClass.getKey() + " nicht freigeschaltet");
		}
	}

	private TikaOutcome readWithTika(InputStream in, String fileName, String contentType) {
		try {
			return TikaOutcome.of(tikaService.read(in, fileName, contentType));
		} catch (TikaReadException e) {
			log.info("Upload: Tika konnte {} nicht lesen: {}", fileName, e.getMessage());
			return TikaOutcome.failed(e.getMessage());
		}
	}

	/** Nur, wenn die Endung im Katalog ein Vorschaubild hat. Die Datei wird dafür frisch geöffnet. */
	private PreviewOutcome renderPreview(MultipartFile file, String fileExtension, Integer orientation) {
		boolean wanted = fileExtensionService.getAllFileExtensions().stream()
				.filter(candidate -> candidate.getExtension().equals(fileExtension))
				.findFirst()
				.map(FileExtension::isImagePreview)
				.orElse(false);
		if (!wanted) {
			return PreviewOutcome.skipped();
		}
		long start = System.nanoTime();
		try (InputStream in = file.getInputStream()) {
			return PreviewOutcome.of(previewImageService.render(in, orientation), (System.nanoTime() - start) / 1_000_000);
		} catch (IOException | NoImageDataException e) {
			log.info("Upload: kein Vorschaubild für {}: {}", file.getOriginalFilename(), e.getMessage());
			return PreviewOutcome.failed(e.getMessage(), (System.nanoTime() - start) / 1_000_000);
		}
	}

	private static String text(Map<String, Object> document, String name) {
		Object value = document.get(name);
		return value == null ? null : value.toString();
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 nicht verfügbar", e);
		}
	}

}
