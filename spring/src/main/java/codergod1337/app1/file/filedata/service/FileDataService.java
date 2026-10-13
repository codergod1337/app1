package codergod1337.app1.file.filedata.service;

import codergod1337.app1.file.filedata.model.FileDataUploadProbe;
import codergod1337.app1.file.filedata.model.FileDataUploadProbe.PreviewOutcome;
import codergod1337.app1.file.filedata.model.FileDataUploadProbe.TikaOutcome;
import codergod1337.app1.file.filedata.model.FileDataUploadResult;
import codergod1337.app1.file.filedata.model.FileDataUploadResult.FileDataUploadStatus;
import codergod1337.app1.file.filedata.service.PreviewImageService.NoImageDataException;
import codergod1337.app1.file.filedata.service.PreviewImageService.PreviewImage;
import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.solr.dms.model.DmsBinaryStartDownload;
import codergod1337.app1.solr.dms.model.DmsExistence;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsUtilsService;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.sftp.service.SftpPathService;
import codergod1337.app1.system.sftp.service.SftpService;
import codergod1337.app1.system.tika.TikaService;
import codergod1337.app1.system.tika.TikaService.TikaReadException;
import codergod1337.app1.system.tika.TikaService.TikaResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alles, was mit den Bytes einer Datei zu tun hat: der Upload (prüfen, lesen, SFTP, dann das Dokument über den
 * SolrDocumentDmsService speichern) und der Download (Fakten vom SolrDocumentDmsService, Bytes vom SFTP streamen,
 * zählen). Der SolrDocumentDmsService prüft dabei nur Rechte und schreibt Dokumente, Dateien kennt er nicht.
 */
@Service
public class FileDataService {

	private static final Logger log = LoggerFactory.getLogger(FileDataService.class);

	private final SolrDocumentDmsService solrDocumentDmsService;
	private final SolrDocumentDmsUtilsService solrDocumentDmsUtilsService;
	private final FileSubClassService fileSubClassService;
	private final FileExtensionService fileExtensionService;
	private final TikaService tikaService;
	private final PreviewImageService previewImageService;
	private final DocumentLanguageService documentLanguageService;
	private final SftpService sftpService;
	private final SftpPathService sftpPathService;
	private final CurrentUsersProvider currentUsersProvider;

	public FileDataService(SolrDocumentDmsService solrDocumentDmsService,
			SolrDocumentDmsUtilsService solrDocumentDmsUtilsService, FileSubClassService fileSubClassService,
			FileExtensionService fileExtensionService, TikaService tikaService,
			PreviewImageService previewImageService, DocumentLanguageService documentLanguageService,
			SftpService sftpService, SftpPathService sftpPathService, CurrentUsersProvider currentUsersProvider) {
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.solrDocumentDmsUtilsService = solrDocumentDmsUtilsService;
		this.fileSubClassService = fileSubClassService;
		this.fileExtensionService = fileExtensionService;
		this.tikaService = tikaService;
		this.previewImageService = previewImageService;
		this.documentLanguageService = documentLanguageService;
		this.sftpService = sftpService;
		this.sftpPathService = sftpPathService;
		this.currentUsersProvider = currentUsersProvider;
	}

	// ===== Upload =====

	public FileDataUploadProbe probeUpload(MultipartFile file, Map<String, Object> clientFieldValues) {
		// Die Uploadliste ruft das vor dem Hochladen: prüfen, lesen, rechnen, nichts speichern. Fehler von Tika oder beim
		// Vorschaubild stehen in der Antwort, Fehler an Werten oder Rechten sind 400 und 403.
		return prepare(file, clientFieldValues).probe();
	}

	public FileDataUploadResult upload(MultipartFile file, Map<String, Object> clientFieldValues) {
		// Die Uploadliste ruft das: wie die Probe, dann speichern. Dieselben Bytes gibt es genau einmal, ihre id ist der
		// Hash: schon da und gleiche Dateiart → mergen, schon da und anders → nichts, sonst SFTP und Solr.
		// Steht versionChain in der Map, ist es ein Versionsupdate (User 2026-10-10): prepare hat den Kopf der Kette.
		Prepared prepared = prepare(file, clientFieldValues);
		String id = prepared.probe().sha256();
		DmsExistence existence = solrDocumentDmsService.getExistence(id);
		if (existence.exists()) {
			if (prepared.previous() != null) {
				// eine neue Fassung braucht neue Bytes
				return new FileDataUploadResult(FileDataUploadStatus.EXISTS, id, null, prepared.probe());
			}
			boolean sameFileSubClass = prepared.fileSubClass().getKey().equals(existence.fileSubClassKey());
			boolean mergeableStatus = "ACTIVE".equals(existence.status()) || "ARCHIVED".equals(existence.status());
			if (!sameFileSubClass || !mergeableStatus) {
				return new FileDataUploadResult(FileDataUploadStatus.EXISTS, id, null, prepared.probe());
			}
			solrDocumentDmsService.mergeFieldsBecauseIdenticalBinary(id, prepared.probe().document());
			return new FileDataUploadResult(FileDataUploadStatus.MERGED, id, solrDocumentDmsService.getById(id),
					prepared.probe());
		}
		// erst der SFTP, dann Solr: ein Dokument ohne Datei wäre eine Lüge, eine Datei ohne Dokument nur Platz
		String sftpPath = sftpPathService.relativePath(id);
		try (InputStream in = file.getInputStream()) {
			sftpService.writeFileData(in, sftpPath);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Datei konnte nicht gelesen werden");
		}
		Map<String, Object> document = documentOf(prepared, sftpPath);
		if (prepared.previous() != null) {
			solrDocumentDmsService.createNextVersionOfChain(prepared.previous().get("id").toString(), document);
		} else {
			solrDocumentDmsService.createFromUpload(document);
		}
		log.info("Upload: {} als {} ({} Bytes), {}", prepared.probe().fileName(), id, prepared.probe().sizeBytes(), sftpPath);
		return new FileDataUploadResult(FileDataUploadStatus.CREATED, id, solrDocumentDmsService.getById(id),
				prepared.probe().asStored());
	}

	/** Die gemeinsame Vorarbeit beider Wege: die Probe, dazu die Dateiart und bei einem Versionsupdate der Kopf der Kette */
	private record Prepared(FileDataUploadProbe probe, FileSubClass fileSubClass, Map<String, Object> previous) {
	}

	private Prepared prepare(MultipartFile file, Map<String, Object> clientFieldValues) {
		// Erst die Werte des Clients gegen die Felddefinition, dann die Regeln des DMS (isPublic, Größe, Dateiart,
		// Endung, Schreibrolle), dann die Datei einmal lesen: Tika und der Hash, danach Sprache und Vorschaubild.
		String fileName = file.getOriginalFilename();
		String fileExtension = extensionOf(fileName);
		Map<String, Object> values = new LinkedHashMap<>(clientFieldValues);
		// versionChain ist kein Feldwert, sondern das Signal für ein Versionsupdate (User 2026-10-10): Ein normaler Upload
		// schickt es nie, der Kopf der Kette (Status ACTIVE) ist dann die Fassung, die abgelöst wird, seine Dateiart gilt.
		Map<String, Object> previous = null;
		Object versionChain = values.remove("versionChain");
		if (versionChain != null && !versionChain.toString().isBlank()) {
			previous = headOfVersionChain(versionChain.toString());
		}
		// Was der Upload selbst weiß, steht vor der Prüfung drin: der Name der Datei, bei der neuen Fassung die Dateiart
		// des Kopfes. Pflichtfelder (fileName, fileSubClassKey) schlagen so nur an, wenn wirklich etwas fehlt (400 beim
		// Upload der Liste, 2026-10-10). Ein Status vom Client zählt nicht: Der Upload setzt immer ACTIVE (User 2026-10-10).
		values.putIfAbsent("fileName", fileName);
		values.remove("status");
		if (previous != null) {
			values.put("fileSubClassKey", previous.get("fileSubClassKey"));
		}
		Map<String, Object> document = new LinkedHashMap<>(solrDocumentDmsUtilsService.checkClientFieldValues(values, true));
		requirePublicAllowed(document);
		requireFileSizeMatches(document, file);
		Object fileSubClassKey = document.get("fileSubClassKey");
		if (fileSubClassKey == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileSubClassKey fehlt");
		}
		FileSubClass fileSubClass = fileSubClassService.getFileSubClassByKey(fileSubClassKey.toString());
		requireWriteRoleOfFileSubClass(fileSubClass);
		requireExtensionAllowed(fileSubClass, fileExtension);

		MessageDigest digest = sha256();
		TikaOutcome tika;
		try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
			tika = readWithTika(in, fileName, file.getContentType());
			// der Hash braucht jedes Byte, auch die, die Tika nicht mehr gelesen hat
			in.transferTo(OutputStream.nullOutputStream());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Datei konnte nicht gelesen werden");
		}
		String sha256 = HexFormat.of().formatHex(digest.digest());
		applyDocumentLanguageRule(document, fileExtension, tika);
		PreviewOutcome preview = renderPreview(file, fileExtension, tika.orientation());
		log.info("Upload-Vorarbeit: {} ({} Bytes, sha256 {}), {} Felder, Tika {} in {} ms, Vorschau {}", fileName,
				file.getSize(), sha256, document.size(), tika.ok() ? tika.mimeType() : "fehlgeschlagen",
				tika.durationMs(), preview.attempted() ? (preview.ok() ? preview.bytes() + " Bytes" : "fehlgeschlagen")
						: "keine");
		return new Prepared(new FileDataUploadProbe(fileName, fileExtension, file.getContentType(), file.getSize(), sha256,
				document, tika, preview, false), fileSubClass, previous);
	}

	private Map<String, Object> headOfVersionChain(String versionChain) {
		// die Fassung mit Status ACTIVE ist der Kopf; was der User nicht sehen darf, fehlt in der Liste, dann 404
		if (!HelperInputs.isValidGuid(versionChain)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionChain ist keine UUID");
		}
		for (Map<String, Object> member : solrDocumentDmsService.getByVersionGuid(versionChain)) {
			if ("ACTIVE".equals(member.get("status"))) {
				return member;
			}
		}
		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine aktive Fassung in der Kette " + versionChain);
	}

	private Map<String, Object> documentOf(Prepared prepared, String sftpPath) {
		// Das ganze Dokument für Solr: die geprüften Werte des Clients, darüber die Felder des Backends, die gewinnen immer
		FileDataUploadProbe probe = prepared.probe();
		Date now = new Date();
		String usersGuid = currentUsersProvider.getCurrentUsersGuid().toString();
		Map<String, Object> document = new LinkedHashMap<>(probe.document());
		document.put("id", probe.sha256());
		document.put("fileName", normalizeFileName(probe.fileName(), probe.fileExtension()));
		document.put("fileExtension", probe.fileExtension());
		// MIME-Typ aus den Bytes (Tika), sonst die Angabe des Browsers
		document.put("mimeType", probe.tika().ok() && probe.tika().mimeType() != null ? probe.tika().mimeType()
				: probe.contentType());
		document.put("fileSizeBytes", probe.sizeBytes());
		document.put("sftpPath", sftpPath);
		document.put("uploader", usersGuid);
		document.put("changingUsers", List.of(usersGuid));
		document.put("documentUploadedAt", now);
		// Erstelldatum aus der Datei, sonst der Upload: Das Feld ist immer gefüllt, die Suche sortiert danach
		document.put("documentCreatedAt", probe.tika().ok() && probe.tika().createdAt() != null
				? Date.from(probe.tika().createdAt()) : now);
		document.put("lastChangedAt", now);
		document.put("downloadCount", 0L);
		document.put("fileIdRevisionNo", 0L);
		// neue Datei: Fassung 1 mit neuer Kette (zufällige UUID); Versionsupdate: Nummer des Kopfes plus eins, seine Kette
		Map<String, Object> previous = prepared.previous();
		long previousVersion = previous != null && previous.get("version") instanceof Number number ? number.longValue() : 0L;
		document.put("version", previous == null ? 1L : previousVersion + 1);
		document.put("versionChain", previous == null ? UUID.randomUUID().toString() : previous.get("versionChain"));
		document.put("fileSubClassKey", prepared.fileSubClass().getKey());
		document.put("readAcl", List.copyOf(prepared.fileSubClass().getReadAccessRoleKeys()));
		document.put("writeAcl", List.copyOf(prepared.fileSubClass().getWriteAccessRoleKeys()));
		document.put("ki", false);
		// jeder Upload beginnt ACTIVE, egal was der Client schickt (User 2026-10-10); isPublic darf der Client setzen
		document.put("status", "ACTIVE");
		document.putIfAbsent("isPublic", false);
		if (probe.tika().ok() && probe.tika().text() != null && !probe.tika().text().isBlank()) {
			document.put("searchableWords", probe.tika().text());
		}
		if (probe.preview().ok()) {
			document.put("bildVorschauBase64Png", probe.preview().base64Png());
		}
		return document;
	}

	// ===== Download =====

	public void streamDownload(DmsBinaryStartDownload file, HttpServletResponse response) throws IOException {
		// Die Download-Endpunkte rufen das mit den Fakten vom SolrDocumentDmsService: Kopfzeilen, Bytes vom SFTP in die
		// Antwort im Thread der Anfrage (so kennt der SFTP-Pool den User), danach der Zähltick. null heißt 404.
		if (file == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Datei");
		}
		response.setContentType(file.mimeType());
		if (file.fileSizeBytes() != null) {
			response.setContentLengthLong(file.fileSizeBytes());
		}
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString());
		// bricht der Empfänger ab, wirft readFileData, dann wird nicht gezählt
		sftpService.readFileData(file.sftpPath(), response.getOutputStream());
		response.flushBuffer();
		log.info("Download: {} ({})", file.id(), file.fileName());
		try {
			solrDocumentDmsService.countDownload(file.id());
		} catch (RuntimeException e) {
			// die Bytes sind schon beim Client, die Antwort lässt sich nicht zurücknehmen: nur ins Log
			log.warn("Download: downloadCount von {} ließ sich nicht hochzählen: {}", file.id(), e.getMessage());
		}
	}

	// ===== Regeln des Uploads =====

	private void requirePublicAllowed(Map<String, Object> document) {
		// isPublic setzt nur, wer DMS_PUBLIC hat, ADMIN immer
		if (!document.containsKey("isPublic")) {
			return;
		}
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.contains("ADMIN") && !accessRoleKeys.contains("DMS_PUBLIC")) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "isPublic nur mit DMS_PUBLIC");
		}
	}

	private static void requireFileSizeMatches(Map<String, Object> document, MultipartFile file) {
		// eine mitgeschickte Größe muss die der Datei sein: weicht sie ab, hat das Frontend eine andere Datei gemeint
		if (document.get("fileSizeBytes") instanceof Number sent && sent.longValue() != file.getSize()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"fileSizeBytes (" + sent + ") passt nicht zur Datei (" + file.getSize() + " Bytes)");
		}
	}

	private void requireWriteRoleOfFileSubClass(FileSubClass fileSubClass) {
		// anlegen darf, wer eine Schreib-AR der Dateiart hat, ADMIN immer
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (accessRoleKeys.contains("ADMIN")) {
			return;
		}
		for (String writeAccessRoleKey : fileSubClass.getWriteAccessRoleKeys()) {
			if (accessRoleKeys.contains(writeAccessRoleKey)) {
				return;
			}
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN,
				"keine Schreibrolle der Dateiart " + fileSubClass.getKey());
	}

	private static void requireExtensionAllowed(FileSubClass fileSubClass, String fileExtension) {
		// die Dateiart muss die Endung freigeschaltet haben (FileMatrix); die Maske prüft dasselbe, das Backend verlässt
		// sich nicht darauf
		List<String> allowed = fileSubClass.getExtensions();
		if (allowed == null || !allowed.contains(fileExtension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Endung "
					+ (fileExtension.isEmpty() ? "(keine)" : fileExtension) + " ist bei der Dateiart "
					+ fileSubClass.getKey() + " nicht freigeschaltet");
		}
	}

	private void applyDocumentLanguageRule(Map<String, Object> document, String fileExtension, TikaOutcome tika) {
		// Die Sprache setzt allein die Erkennung aus dem Text von Tika (User 2026-10-10), nur bei Endungsgruppen mit
		// Sprache; ein Wert vom Client zählt nicht, ohne Erkennung bleibt das Feld leer, nie ein geratener Standard
		document.remove("documentLanguage");
		if (tika.ok() && documentLanguageService.documentLanguageAppliesToFileExtension(fileExtension)) {
			String detected = documentLanguageService.detectDocumentLanguageCode(tika.text());
			if (detected != null) {
				document.put("documentLanguage", detected);
			}
		}
	}

	private TikaOutcome readWithTika(InputStream in, String fileName, String contentType) {
		try {
			TikaResult result = tikaService.read(in, fileName, contentType);
			return new TikaOutcome(true, null, result.mimeType(), result.createdAt(), result.modifiedAt(),
					result.orientation(), result.text().length(), result.text(), result.metadata(), result.durationMs());
		} catch (TikaReadException e) {
			log.info("Upload: Tika konnte {} nicht lesen: {}", fileName, e.getMessage());
			return TikaOutcome.failed(e.getMessage());
		}
	}

	private PreviewOutcome renderPreview(MultipartFile file, String fileExtension, Integer orientation) {
		// nur, wenn die Endung im Katalog ein Vorschaubild hat; die Datei wird dafür frisch geöffnet
		boolean wanted = false;
		for (FileExtension candidate : fileExtensionService.getAllFileExtensions()) {
			if (candidate.getExtension().equals(fileExtension)) {
				wanted = candidate.isImagePreview();
				break;
			}
		}
		if (!wanted) {
			return PreviewOutcome.skipped();
		}
		long start = System.nanoTime();
		try (InputStream in = file.getInputStream()) {
			PreviewImage image = previewImageService.render(in, orientation);
			return new PreviewOutcome(true, true, null, image.base64Png(), image.sourceWidth(), image.sourceHeight(),
					image.orientation(), image.bytes(), (System.nanoTime() - start) / 1_000_000);
		} catch (IOException | NoImageDataException e) {
			log.info("Upload: kein Vorschaubild für {}: {}", file.getOriginalFilename(), e.getMessage());
			return PreviewOutcome.failed(e.getMessage(), (System.nanoTime() - start) / 1_000_000);
		}
	}

	static String extensionOf(String fileName) {
		// die Endung nach unserer Regel: nach dem letzten Punkt, klein; ohne Punkt leer
		if (fileName == null) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	static String normalizeFileName(String fileName, String fileExtension) {
		// der Dateiname fürs Dokument: klein, Umlaute aufgelöst, Leerraum zu _, sonst nur a-z, 0-9, _ und -, dazu die
		// Endung; so ist er wortweise suchbar und nie ein Pfad
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

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 nicht verfügbar", e);
		}
	}

}
