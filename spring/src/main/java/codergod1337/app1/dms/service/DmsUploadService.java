package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsDocumentLanguages;
import codergod1337.app1.dms.model.DmsSolrDocumentExistenceCheck;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentUploadFacts;
import codergod1337.app1.dms.service.PreviewImageService.NoImageDataException;
import codergod1337.app1.dms.service.PreviewImageService.PreviewImage;
import codergod1337.app1.dms.service.TikaService.TikaReadException;
import codergod1337.app1.dms.service.TikaService.TikaResult;
import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.system.sftp.service.SftpPathService;
import codergod1337.app1.system.sftp.service.SftpService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * {@code prepare}: die Felder des Clients ohne die, die er nicht setzen darf (Schreibregel je Feld), gegen den Kern DMS
 * und die Regeln des DMS prüfen, die Datei mit Tika lesen, den SHA-256 rechnen, bei Bildern das Vorschaubild.
 *
 * Speichern heißt (Vorgabe 2026-10-07): Existenzcheck, dann entweder Merge derselben Bytes (gleiche Dateiart, Status
 * ACTIVE oder ARCHIVED) oder Bytes auf den SFTP und ein neues Dokument, als neue Datei oder als neue Fassung einer
 * Versionskette. Jeder Schreibvorgang läuft über den DmsSolrDocumentWriteService, dieser Service schreibt nie selbst in
 * den Kern. Erst der SFTP, dann Solr: Ein Dokument ohne Datei wäre eine Lüge, eine Datei ohne Dokument nur Platz.
 *
 * Wer das darf: jeder mit der AR DMS, zum Speichern dazu die Schreibrolle der Dateiart.
 */
@Service
public class DmsUploadService {

	private static final Logger log = LoggerFactory.getLogger(DmsUploadService.class);

	private final SolrCoreService solrCoreService;
	private final DmsSolrDocumentChecker dmsSolrDocumentChecker;
	private final DmsSolrDocumentExistenceService dmsSolrDocumentExistenceService;
	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsSolrDocumentWriteService dmsSolrDocumentWriteService;
	private final FileSubClassService fileSubClassService;
	private final FileExtensionService fileExtensionService;
	private final DmsStateService dmsStateService;
	private final TikaService tikaService;
	private final PreviewImageService previewImageService;
	private final SftpService sftpService;
	private final SftpPathService sftpPathService;
	private final DmsDocumentLanguageService dmsDocumentLanguageService;

	public DmsUploadService(SolrCoreService solrCoreService, DmsSolrDocumentChecker dmsSolrDocumentChecker,
			DmsSolrDocumentExistenceService dmsSolrDocumentExistenceService,
			DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsSolrDocumentWriteService dmsSolrDocumentWriteService, FileSubClassService fileSubClassService,
			FileExtensionService fileExtensionService, DmsStateService dmsStateService, TikaService tikaService,
			PreviewImageService previewImageService, SftpService sftpService, SftpPathService sftpPathService,
			DmsDocumentLanguageService dmsDocumentLanguageService) {
		this.solrCoreService = solrCoreService;
		this.dmsSolrDocumentChecker = dmsSolrDocumentChecker;
		this.dmsSolrDocumentExistenceService = dmsSolrDocumentExistenceService;
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsSolrDocumentWriteService = dmsSolrDocumentWriteService;
		this.fileSubClassService = fileSubClassService;
		this.fileExtensionService = fileExtensionService;
		this.dmsStateService = dmsStateService;
		this.tikaService = tikaService;
		this.previewImageService = previewImageService;
		this.sftpService = sftpService;
		this.sftpPathService = sftpPathService;
		this.dmsDocumentLanguageService = dmsDocumentLanguageService;
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
		/** neues Dokument, Bytes auf dem SFTP, Dokument in Solr, als neue Datei oder neue Fassung */
		CREATED,
		/** dieselben Bytes lagen schon in derselben Dateiart: Hooks und Beschreibung ergänzt, nichts hochgeladen */
		MERGED,
		/** dieselben Bytes liegen unter einer anderen Dateiart oder mit einem Status, der keinen Merge erlaubt: nichts geändert */
		EXISTS
	}

	/**
	 * Das Ergebnis des Uploads: was passiert ist, die id, das Dokument mit der Lesegrenze des Users (null bei EXISTS und
	 * wenn er es nicht sehen darf, denn Anlegen braucht kein Lesen), dazu die Probe.
	 */
	public record DmsUploadResult(UploadStatus status, String id, Map<String, Object> document, DmsUploadProbe probe) {
	}

	/** Die gemeinsame Vorarbeit beider Wege */
	private record PreparedUpload(JsonNode clientFieldValues, Map<String, Object> document, FileSubClass fileSubClass,
			String fileName, String fileExtension, String contentType, long sizeBytes, String sha256, TikaOutcome tika,
			PreviewOutcome preview, String documentLanguage) {

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
	 * Der Upload: wie die Probe, dann speichern. Dieselben Bytes gibt es im DMS genau einmal, ihre id ist der Hash:
	 * Liegen sie schon in dieser Dateiart mit Status ACTIVE oder ARCHIVED, werden Hooks und Beschreibung ergänzt
	 * (MERGED), sonst passiert nichts (EXISTS). Sonst gehen die Bytes auf den SFTP und das Dokument nach Solr (CREATED):
	 * mit versionChain als neue Fassung der Kette, deren Dateiart vom Kopf der Kette kommt, ohne als neue Datei mit der
	 * Dateiart des Clients.
	 */
	@PreAuthorize("hasRole('DMS')")
	public DmsUploadResult uploadFile(JsonNode data, MultipartFile file) {
		PreparedUpload prepared = prepare(data, file);
		String versionChain = text(prepared.document(), DmsSolrDocumentFieldNames.VERSION_CHAIN);
		if (versionChain == null && prepared.fileSubClass() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY + " fehlt");
		}

		DmsSolrDocumentExistenceCheck existence = dmsSolrDocumentExistenceService
				.loadDmsSolrDocumentExistenceCheck(prepared.sha256());
		if (existence.exists()) {
			return mergeOrRefuse(prepared, existence);
		}

		String sftpPath = sftpPathService.relativePath(prepared.sha256());
		try (InputStream in = file.getInputStream()) {
			sftpService.writeFileData(in, sftpPath);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Datei konnte nicht gelesen werden");
		}
		DmsSolrDocumentUploadFacts facts = new DmsSolrDocumentUploadFacts(prepared.sha256(),
				normalizeFileName(prepared.fileName(), prepared.fileExtension()), prepared.fileExtension(),
				prepared.tika().ok() && prepared.tika().mimeType() != null ? prepared.tika().mimeType()
						: prepared.contentType(),
				prepared.sizeBytes(), sftpPath,
				// Erstelldatum aus der Datei, sonst der Upload: Das Feld ist immer gefüllt, die Trefferliste sortiert danach
				prepared.tika().ok() && prepared.tika().createdAt() != null ? prepared.tika().createdAt() : Instant.now(),
				prepared.tika().ok() ? prepared.tika().text() : null,
				prepared.preview().ok() ? prepared.preview().base64Png() : null, prepared.documentLanguage());
		String id = versionChain != null
				? dmsSolrDocumentWriteService.createNextVersionInVersionChain(facts, prepared.clientFieldValues())
				: dmsSolrDocumentWriteService.createDmsSolrDocumentFromUpload(facts, prepared.clientFieldValues());
		log.info("Upload: {} als {} ({} Bytes), {}", prepared.fileName(), id, prepared.sizeBytes(), sftpPath);
		return new DmsUploadResult(UploadStatus.CREATED, id, documentForUploadAnswer(id), prepared.toProbe(true));
	}

	// ===== Vorarbeit =====

	/**
	 * Erst die Felder des Clients ohne die, die er nicht setzen darf (die Mechanik ignoriert sie, hier fallen sie weg,
	 * damit Probe und Upload dasselbe sehen), dann die Form gegen den Kern DMS (400 mit allen Beanstandungen) und die
	 * Regeln des DMS, dann liest es die Datei einmal durch: Tika nimmt sich, was es braucht, der Rest wird nur noch für
	 * den Hash gelesen. Hat die Endung im Katalog ein Vorschaubild, wird die Datei dafür ein zweites Mal gelesen.
	 */
	private PreparedUpload prepare(JsonNode data, MultipartFile file) {
		// Ohne den Kern gibt es keine Maske, die Meldung des Service reicht
		solrCoreService.getSolrCoreByKey(DmsSolrDocumentFieldNames.CORE_KEY);
		JsonNode clientFieldValues = dmsSolrDocumentWriteService.removeFieldsTheClientMayNotSet(data, true);
		// eine eigene Kopie: Die Sprachregel schreibt gleich hinein
		Map<String, Object> document = new LinkedHashMap<>(dmsSolrDocumentChecker.checkNewDmsSolrDocument(clientFieldValues));
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
		String documentLanguage = applyDocumentLanguageRule(document, fileExtension, tika);
		PreviewOutcome preview = renderPreview(file, fileExtension, tika.orientation());

		log.info("Upload-Vorarbeit: {} ({} Bytes, sha256 {}), {} Felder, Tika {} in {} ms, Vorschau {}", fileName,
				file.getSize(), sha256, document.size(), tika.ok() ? tika.mimeType() : "fehlgeschlagen",
				tika.durationMs(), preview.attempted() ? (preview.ok() ? preview.bytes() + " Bytes" : "fehlgeschlagen")
						: "keine");
		return new PreparedUpload(clientFieldValues, document, fileSubClass, fileName, fileExtension,
				file.getContentType(), file.getSize(), sha256, tika, preview, documentLanguage);
	}

	// ===== Speichern =====

	/**
	 * Dieselben Bytes gibt es schon. Gleiche Dateiart und Status ACTIVE oder ARCHIVED: der Schreibservice ergänzt Hooks
	 * und Beschreibung, mit Schreibcheck am vorhandenen Dokument (MERGED). Andere Dateiart oder ein anderer Status:
	 * nichts, die Dateiart eines Dokuments wechselt nur das Bearbeiten, denn sie entscheidet über die Rechte (EXISTS).
	 */
	private DmsUploadResult mergeOrRefuse(PreparedUpload prepared, DmsSolrDocumentExistenceCheck existence) {
		String id = prepared.sha256();
		boolean sameFileSubClass = prepared.fileSubClass() != null
				&& prepared.fileSubClass().getKey().equals(existence.fileSubClassKey());
		boolean mergeableStatus = DmsStateService.ACTIVE.equals(existence.status())
				|| DmsStateService.ARCHIVED.equals(existence.status());
		if (!sameFileSubClass || !mergeableStatus) {
			log.info("Upload: {} liegt schon unter Dateiart {} mit Status {}, nichts geändert", id,
					existence.fileSubClassKey(), existence.status());
			return new DmsUploadResult(UploadStatus.EXISTS, id, null, prepared.toProbe(false));
		}
		dmsSolrDocumentWriteService.mergeHookValuesFromRepeatedUpload(id, prepared.clientFieldValues());
		return new DmsUploadResult(UploadStatus.MERGED, id, documentForUploadAnswer(id), prepared.toProbe(false));
	}

	/** Das Dokument nach dem Schreiben, mit der Lesegrenze des Users; null, wenn er es nicht sehen darf */
	private Map<String, Object> documentForUploadAnswer(String id) {
		DmsSolrDocumentForClient loaded = dmsExactFieldValueResolveService.loadDmsSolrDocumentForUploadAnswer(id);
		return loaded == null ? null : loaded.document();
	}

	/**
	 * Die Sprache nach der Regel der Endungsgruppe (DmsDocumentLanguageService): In einer Gruppe ohne Sprache ist ein Wert
	 * vom Client ein Fehler, in einer mit Sprache muss er aus der Liste sein. Schickt der Client keinen, erkennt das
	 * Backend sie aus dem Text von Tika, sonst bleibt das Feld leer. Nie ein geratener Standard. Der Wert steht danach im
	 * geprüften Dokument der Probe und kommt als Tatsache des Uploads ins neue Dokument.
	 *
	 * @return die Sprache, die gilt, oder null
	 */
	private String applyDocumentLanguageRule(Map<String, Object> document, String fileExtension, TikaOutcome tika) {
		Object sent = document.get(DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE);
		String sentCode = sent == null || sent.toString().isBlank() ? null : sent.toString();
		document.remove(DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE);
		String problem = dmsDocumentLanguageService.problemWithDocumentLanguage(sentCode, fileExtension);
		if (problem != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
		}
		String effective = null;
		if (sentCode != null) {
			effective = DmsDocumentLanguages.normalizeCode(sentCode);
		} else if (tika.ok() && dmsDocumentLanguageService.documentLanguageAppliesToFileExtension(fileExtension)) {
			effective = dmsDocumentLanguageService.detectDocumentLanguageCode(tika.text());
		}
		if (effective != null) {
			document.put(DmsSolrDocumentFieldNames.DOCUMENT_LANGUAGE, effective);
		}
		return effective;
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

	/** Eine mitgeschickte Versionskette muss eine UUID sein, sonst zeigt die neue Fassung ins Leere */
	private static void requireVersionChainIsUuid(Map<String, Object> document) {
		Object versionChain = document.get(DmsSolrDocumentFieldNames.VERSION_CHAIN);
		if (versionChain == null) {
			return;
		}
		try {
			UUID.fromString(versionChain.toString());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DmsSolrDocumentFieldNames.VERSION_CHAIN + " ist keine UUID");
		}
	}

	/** Eine mitgeschickte Größe muss die der Datei sein: Weicht sie ab, hat das Frontend eine andere Datei gemeint */
	private static void requireFileSizeMatches(Map<String, Object> document, MultipartFile file) {
		Object sizeBytes = document.get(DmsSolrDocumentFieldNames.FILE_SIZE_BYTES);
		if (sizeBytes instanceof Long sent && sent != file.getSize()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DmsSolrDocumentFieldNames.FILE_SIZE_BYTES + " ("
					+ sent + ") passt nicht zur Datei (" + file.getSize() + " Bytes)");
		}
	}

	/** Ein mitgeschickter Status muss einer des Bereichs DMS sein, sonst steht im Dokument ein Status ohne Namen */
	private void requireStatusKnown(Map<String, Object> document) {
		Object status = document.get(DmsSolrDocumentFieldNames.STATUS);
		if (status != null && !dmsStateService.isDmsState(status.toString())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					DmsSolrDocumentFieldNames.STATUS + " " + status + " ist kein Status des Bereichs " + DmsStateService.SCOPE);
		}
	}

	/** Die Dateiart aus dem Dokument, null ohne. Eine unbekannte meldet der Service (404). */
	private FileSubClass fileSubClassOf(Map<String, Object> document) {
		Object fileSubClassKey = document.get(DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY);
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
