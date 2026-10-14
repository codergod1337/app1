package codergod1337.app1.message.archive.service;

import codergod1337.app1.file.filedata.model.FileDataUploadResult;
import codergod1337.app1.file.filedata.service.FileDataService;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.graph.mailbox.model.GraphMailRecipient;
import codergod1337.app1.graph.mailbox.model.GraphMailView;
import codergod1337.app1.graph.mailbox.service.GraphMailboxReadService;
import codergod1337.app1.graph.mailbox.service.MailboxService;
import codergod1337.app1.message.archive.model.MessageArchiveAttachmentAction;
import codergod1337.app1.message.archive.model.MessageArchiveMailFile;
import codergod1337.app1.message.archive.model.MessageArchiveParticipant;
import codergod1337.app1.message.archive.model.MessageArchiveRequest;
import codergod1337.app1.message.archive.model.MessageArchiveResult;
import codergod1337.app1.message.archive.service.MessageArchivePreparationService.MessageArchiveDmsState;
import codergod1337.app1.message.messagesubclass.model.MessageSubClass;
import codergod1337.app1.message.messagesubclass.service.MessageSubClassService;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsUtilsService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.solr.nachrichten.service.SolrDocumentNachrichtenService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Archiviert eine Mail (User 2026-10-14, nach dem NachrichtArchivierenService der Vorlage): Anhänge ins DMS, die Nachricht
 * in den Kern NACHRICHTEN. Wie ein DMS-Upload: Die Feldwerte unter den Namen aus solr_field gehen an
 * SolrDocumentNachrichtenService, die Werte der Mail kommen frisch von Graph, die eigenen setzt dieser Service, die Hooks
 * kommen vom User. Alles mit den Rechten des Archivierenden.
 *
 * Vom Client kommen nur Entscheidungen. Den Stand jeder Datei im DMS bestimmt dieser Service neu, wie das Aufbereiten
 * (MessageArchivePreparationService). Erst wird alles geprüft (Zugriff, Dateiart, Schreibrolle, Endung), dann geschrieben:
 * Scheitert eine Prüfung, liegt nichts halb im DMS.
 *
 * Signaturen sind eine Einbahnstraße: Die Nachricht nennt die DMS-id (signatureAttachments), das Signatur-Dokument bekommt
 * nie Hooks und keinen Verweis auf Nachrichten. Neue Signaturen gehen als Bild (BILDER) mit wtf = ATTACHMENT_SIGNATURE
 * ins DMS, neue ignorierte als TEMP mit wtf = ATTACHMENT_IGNORE, damit sie beim nächsten Mal am Hash erkannt werden.
 *
 * Bilder im Mailtext werden Verweise dms:<sha256>: cid:-Bilder über die contentId ihres Anhangs, data:-Bilder über ihren
 * Hash, beides nur, wenn die Datei danach im DMS liegt. Ein kaputtes data:-Bild (abgeschnittenes Base64) fällt aus dem Text.
 */
@Service
public class MessageArchiveService {

	private static final Logger log = LoggerFactory.getLogger(MessageArchiveService.class);

	/** Die Dateiart einer Signatur (Vorlage) */
	private static final String SIGNATURE_FILE_SUB_CLASS_KEY = "BILDER";

	/** Die Dateiart einer neuen Datei, die nie archiviert wird (User 2026-10-14) */
	private static final String IGNORE_FILE_SUB_CLASS_KEY = "TEMP";

	/** Ein eingebettetes Bild verweist im HTML der Mail auf die contentId seines Anhangs */
	private static final Pattern IMAGE_AS_CID = Pattern.compile("(src\\s*=\\s*[\"'])cid:([^\"']+)([\"'])",
			Pattern.CASE_INSENSITIVE);

	/** Ein img-Tag mit leerer Quelle, was von einem kaputten data:-Bild übrig bleibt */
	private static final Pattern IMAGE_WITHOUT_SOURCE = Pattern.compile("<img\\b[^>]*\\bsrc=[\"']\\s*[\"'][^>]*>",
			Pattern.CASE_INSENSITIVE);

	/** Höchstens so viele Mails fragt die Postfachliste auf einmal, ob sie archiviert sind; die Liste holt 50 */
	private static final int MAX_ARCHIVED_LOOKUPS = 200;

	/** Was mit einer Datei geschieht, nachdem alles geprüft ist */
	private enum Step {
		/** neu, an die Nachricht, mit Dateiart und Hooks */
		UPLOAD_ARCHIVE,
		/** neu, als Signatur */
		UPLOAD_SIGNATURE,
		/** neu, nie archivieren */
		UPLOAD_IGNORE,
		/** vorhanden, an die Nachricht, die Hooks kommen dazu */
		MERGE,
		/** vorhanden, bekommt das Flag ATTACHMENT_IGNORE */
		FLAG_IGNORE,
		/** bekannte Signatur, nur der Verweis */
		SIGNATURE_REFERENCE,
		/** nichts */
		NOTHING
	}

	/** Eine geprüfte Datei mit ihrem Schritt, der Dateiart für den Upload und dem vorhandenen Dokument */
	private record PlannedFile(MessageArchiveMailFile file, Step step, String fileSubClassKey,
			Map<String, Object> existingDocument) {
	}

	private final GraphMailboxReadService graphMailboxReadService;
	private final MessageArchivePreparationService messageArchivePreparationService;
	private final SolrDocumentNachrichtenService solrDocumentNachrichtenService;
	private final MessageSubClassService messageSubClassService;
	private final FileSubClassService fileSubClassService;
	private final FileDataService fileDataService;
	private final SolrDocumentDmsService solrDocumentDmsService;
	private final SolrDocumentDmsUtilsService solrDocumentDmsUtilsService;
	private final SolrHookService solrHookService;
	private final CurrentUsersProvider currentUsersProvider;
	private final ObjectMapper objectMapper;

	public MessageArchiveService(GraphMailboxReadService graphMailboxReadService,
			MessageArchivePreparationService messageArchivePreparationService,
			SolrDocumentNachrichtenService solrDocumentNachrichtenService, MessageSubClassService messageSubClassService,
			FileSubClassService fileSubClassService, FileDataService fileDataService,
			SolrDocumentDmsService solrDocumentDmsService, SolrDocumentDmsUtilsService solrDocumentDmsUtilsService,
			SolrHookService solrHookService, CurrentUsersProvider currentUsersProvider, ObjectMapper objectMapper) {
		this.graphMailboxReadService = graphMailboxReadService;
		this.messageArchivePreparationService = messageArchivePreparationService;
		this.solrDocumentNachrichtenService = solrDocumentNachrichtenService;
		this.messageSubClassService = messageSubClassService;
		this.fileSubClassService = fileSubClassService;
		this.fileDataService = fileDataService;
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.solrDocumentDmsUtilsService = solrDocumentDmsUtilsService;
		this.solrHookService = solrHookService;
		this.currentUsersProvider = currentUsersProvider;
		this.objectMapper = objectMapper;
	}

	/**
	 * Archiviert die Mail. 400 bei fehlenden Angaben, unbekanntem Hook, fehlender Entscheidung oder Dateiart, 403 ohne
	 * Rolle, ohne Zugriff aufs Postfach, auf ein vorhandenes DMS-Dokument oder ohne Schreibrolle der Dateiart, 409 wenn
	 * die Mail schon im Archiv liegt.
	 */
	@PreAuthorize("hasAnyRole('" + MailboxService.ARCHIVE_ACCESS_ROLE_KEY + "', 'ADMIN')")
	public MessageArchiveResult archiveMessage(MessageArchiveRequest request) {
		requireText(request.mailbox(), "mailbox");
		requireText(request.exchangeMailId(), "exchangeMailId");
		requireText(request.messageSubClassKey(), "messageSubClassKey");
		if (request.folder() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "folder fehlt");
		}
		MessageSubClass messageSubClass = messageSubClassService.getMessageSubClassByKey(request.messageSubClassKey());
		Map<String, List<String>> hooks = checkedHooks(request.hooks());
		Map<String, MessageArchiveRequest.Attachment> choicesBySha256 = new HashMap<>();
		for (MessageArchiveRequest.Attachment choice : request.attachments() == null ? List.<MessageArchiveRequest.Attachment>of()
				: request.attachments()) {
			if (choice != null && choice.sha256() != null) {
				choicesBySha256.put(choice.sha256(), choice);
			}
		}

		// die Mail frisch von Graph; beide Aufrufe prüfen den Zugriff aufs Postfach
		JsonNode mail = graphMailboxReadService.getMail(request.mailbox(), request.exchangeMailId());
		JsonNode graphAttachments = graphMailboxReadService.getMailAttachments(request.mailbox(), request.exchangeMailId());
		GraphMailView mailView = graphMailboxReadService.toMailView(mail, graphAttachments);
		String internetMessageId = mail.path("internetMessageId").asString("").trim();
		if (internetMessageId.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Mail hat keine internetMessageId");
		}
		if (solrDocumentNachrichtenService.existsByInternetMessageId(internetMessageId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "diese Mail liegt bereits im Archiv");
		}

		// 1. alles prüfen, nichts schreiben
		List<PlannedFile> plannedFiles = new ArrayList<>();
		for (MessageArchiveMailFile file : MessageArchivePreparationService.mailFilesOf(mail, graphAttachments)) {
			if (file.bytes() != null) {
				plannedFiles.add(plan(file, choicesBySha256.get(file.sha256())));
			}
		}

		// 2. ins DMS
		String subject = mailView.subject();
		List<String> attachmentIds = new ArrayList<>();
		List<String> signatureIds = new ArrayList<>();
		Set<String> storedSha256 = new LinkedHashSet<>();
		int newAttachments = 0;
		int mergedAttachments = 0;
		int ignored = 0;
		for (PlannedFile plannedFile : plannedFiles) {
			MessageArchiveMailFile file = plannedFile.file();
			switch (plannedFile.step()) {
				case UPLOAD_ARCHIVE -> {
					Map<String, Object> values = new LinkedHashMap<>(hooks);
					values.put("fileSubClassKey", plannedFile.fileSubClassKey());
					values.put("fileDescription", multilingual("Anhang der Mail: " + subject, "Attachment of the mail: " + subject,
							"Прикачен файл към имейла: " + subject));
					FileDataUploadResult result = upload(file, values);
					addDistinct(attachmentIds, result.id());
					storedSha256.add(file.sha256());
					if (result.status() == FileDataUploadResult.FileDataUploadStatus.CREATED) {
						newAttachments++;
					} else {
						mergedAttachments++;
					}
				}
				case UPLOAD_SIGNATURE -> {
					Map<String, Object> values = new LinkedHashMap<>();
					values.put("fileSubClassKey", SIGNATURE_FILE_SUB_CLASS_KEY);
					values.put(MessageArchivePreparationService.FLAG_HOOK_KEY, List.of(MessageArchivePreparationService.SIGNATURE_FLAG));
					values.put("fileDescription", multilingual("Signatur aus einer Mail", "Signature from a mail", "Подпис от имейл"));
					addDistinct(signatureIds, upload(file, values).id());
					storedSha256.add(file.sha256());
				}
				case UPLOAD_IGNORE -> {
					Map<String, Object> values = new LinkedHashMap<>();
					values.put("fileSubClassKey", IGNORE_FILE_SUB_CLASS_KEY);
					values.put(MessageArchivePreparationService.FLAG_HOOK_KEY, List.of(MessageArchivePreparationService.IGNORE_FLAG));
					values.put("fileDescription", multilingual("Wird beim Archivieren von Mails nie archiviert",
							"Is never archived when archiving mails", "Никога не се архивира при архивиране на имейли"));
					upload(file, values);
					storedSha256.add(file.sha256());
					ignored++;
				}
				case MERGE -> {
					// die Dateiart des vorhandenen Dokuments: dann mergt der Upload die Hooks, ohne Beschreibung, die bleibt
					Map<String, Object> values = new LinkedHashMap<>(hooks);
					values.put("fileSubClassKey", plannedFile.fileSubClassKey());
					addDistinct(attachmentIds, upload(file, values).id());
					storedSha256.add(file.sha256());
					mergedAttachments++;
				}
				case FLAG_IGNORE -> {
					List<String> flags = new ArrayList<>(MessageArchivePreparationService
							.valuesOf(plannedFile.existingDocument().get(MessageArchivePreparationService.FLAG_HOOK_KEY)));
					flags.add(MessageArchivePreparationService.IGNORE_FLAG);
					Map<String, Object> fieldValues = solrDocumentDmsUtilsService.checkClientFieldValues(
							Map.of(MessageArchivePreparationService.FLAG_HOOK_KEY, flags), false);
					solrDocumentDmsService.editDmsDocument(file.sha256(), fieldValues);
					storedSha256.add(file.sha256());
					ignored++;
				}
				case SIGNATURE_REFERENCE -> {
					addDistinct(signatureIds, file.sha256());
					storedSha256.add(file.sha256());
				}
				case NOTHING -> {
					// vorhanden und ignoriert liegt im DMS, eine übersprungene neue Datei nicht
					if (plannedFile.existingDocument() != null) {
						storedSha256.add(file.sha256());
					}
					ignored++;
				}
			}
		}

		// 3. der Mailtext mit Verweisen dms:<id> statt der Bilder
		Map<String, String> sha256ByContentId = new HashMap<>();
		for (PlannedFile plannedFile : plannedFiles) {
			if (plannedFile.file().contentId() != null) {
				sha256ByContentId.put(plannedFile.file().contentId(), plannedFile.file().sha256());
			}
		}
		String uniqueBody = withDmsReferences(mail.path("uniqueBody").path("content").asString(""), sha256ByContentId,
				storedSha256);
		String wholeBody = mail.path("body").path("content").asString("");
		// eine leere ganze Mail wird nie gespeichert, egal was gewählt ist (Vorlage)
		String emailBody = request.wholeMail() && !wholeBody.isBlank()
				? withDmsReferences(wholeBody, sha256ByContentId, storedSha256) : null;

		// 4. die Felder des Kerns NACHRICHTEN, Namen wie in solr_field
		MessageArchivePreparationService.MessageArchiveParticipants participants = messageArchivePreparationService
				.participantsOf(mail, mailView);
		Map<String, Object> fields = new LinkedHashMap<>();
		String id = solrDocumentNachrichtenService.nextNachrichtenId();
		fields.put("id", id);
		fields.put("messageSubClassKey", messageSubClass.getKey());
		fields.put("readAcl", orEmpty(messageSubClass.getReadAccessRoleKeys()));
		fields.put("writeAcl", orEmpty(messageSubClass.getWriteAccessRoleKeys()));
		fields.put("mailbox", request.mailbox().trim().toLowerCase(Locale.ROOT));
		fields.put("mailboxFolder", request.folder().getGraphName());
		fields.put("archivedBy", currentUsersProvider.getCurrentUsersGuid().toString());
		fields.put("archivedAt", Instant.now());
		fields.put("exchangeMailId", textOf(mail.path("id")));
		fields.put("internetMessageId", internetMessageId);
		fields.put("conversationId", textOf(mail.path("conversationId")));
		fields.put("conversationIndex", textOf(mail.path("conversationIndex")));
		fields.put("inReplyTo", headerOf(mail, "In-Reply-To"));
		fields.put("threadIndex", headerOf(mail, "Thread-Index"));
		String references = headerOf(mail, "References");
		fields.put("references", references == null ? List.of() : List.of(references.trim().split("\\s+")));
		fields.put("subject", subject);
		fields.put("bodyPreview", textOf(mail.path("bodyPreview")));
		fields.put("uniqueBody", uniqueBody.isBlank() ? null : uniqueBody);
		fields.put("emailBody", emailBody);
		fields.put("searchableWords", (subject + " "
				+ MessageArchivePreparationService.textOfHtml(emailBody != null ? emailBody : uniqueBody)).trim());
		fields.put("sentDateTime", mailView.sentDateTime());
		fields.put("receivedDateTime", mailView.receivedDateTime());
		fields.put("importance", mailView.importance());
		fields.put("hasAttachments", !attachmentIds.isEmpty());
		if (mailView.from() != null) {
			fields.put("fromName", nameOf(mailView.from()));
			fields.put("fromAddress", mailView.from().address());
		}
		JsonNode sender = mail.path("sender").path("emailAddress");
		String senderAddress = sender.path("address").asString("").trim().toLowerCase(Locale.ROOT);
		if (!senderAddress.isEmpty()) {
			String senderName = sender.path("name").asString("").trim();
			fields.put("senderName", senderName.isEmpty() ? senderAddress : senderName);
			fields.put("senderAddress", senderAddress);
		}
		putRecipients(fields, "toRecipient", mailView.toRecipients());
		putRecipients(fields, "ccRecipient", mailView.ccRecipients());
		putRecipients(fields, "bccRecipient", mailView.bccRecipients());
		fields.put("fromKontaktSternzeit", contactIdsOf(participants.participants(), MessageArchiveParticipant.Role.FROM));
		fields.put("toRecipientKontaktSternzeit", contactIdsOf(participants.participants(), MessageArchiveParticipant.Role.TO));
		fields.put("ccRecipientKontaktSternzeit", contactIdsOf(participants.participants(), MessageArchiveParticipant.Role.CC));
		fields.put("bccRecipientKontaktSternzeit", contactIdsOf(participants.participants(), MessageArchiveParticipant.Role.BCC));
		fields.put("attachments", attachmentIds);
		fields.put("signatureAttachments", signatureIds);
		fields.putAll(hooks);
		solrDocumentNachrichtenService.createNachrichtenDocument(fields);

		log.info("Nachricht {} archiviert aus {} von {}: '{}', {} neue und {} vorhandene Anhänge, {} Signaturen, {} ignoriert",
				id, request.mailbox(), currentUsersProvider.getCurrentUsersGuid(), subject, newAttachments, mergedAttachments,
				signatureIds.size(), ignored);
		return new MessageArchiveResult(id, newAttachments, mergedAttachments, signatureIds.size(), ignored);
	}

	/**
	 * Die Markierung der Postfachliste: welche dieser Mails schon im Archiv liegen, internetMessageId -> laufende Nummer.
	 * Mit der Rolle des Archivierens, ohne Leserecht an den Nachrichten: Wer archiviert, muss sehen, was schon drin ist,
	 * auch wenn er es nicht lesen darf. Höchstens so viele wie eine Seite der Liste.
	 */
	@PreAuthorize("hasAnyRole('" + MailboxService.ARCHIVE_ACCESS_ROLE_KEY + "', 'ADMIN')")
	public Map<String, String> getArchivedNachrichtenIds(List<String> internetMessageIds) {
		if (internetMessageIds.size() > MAX_ARCHIVED_LOOKUPS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"höchstens " + MAX_ARCHIVED_LOOKUPS + " internetMessageIds je Anfrage");
		}
		return solrDocumentNachrichtenService.archivedIdsByInternetMessageIds(internetMessageIds);
	}

	// ===== Prüfen =====

	/** Den Stand der Datei neu bestimmen und mit der Entscheidung des Users zu einem Schritt machen; prüft alles vor dem Schreiben */
	private PlannedFile plan(MessageArchiveMailFile file, MessageArchiveRequest.Attachment choice) {
		MessageArchiveDmsState dmsState = messageArchivePreparationService.dmsStateOf(file.sha256());
		MessageArchiveAttachmentAction action = choice == null ? null : choice.action();
		return switch (dmsState.state()) {
			case NO_ACCESS -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"der Anhang " + file.name() + " liegt schon im DMS, aber du bekommst das Dokument nicht");
			case SIGNATURE -> new PlannedFile(file, Step.SIGNATURE_REFERENCE, null, dmsState.document());
			case IGNORED -> action == MessageArchiveAttachmentAction.ARCHIVE
					? planMerge(file, dmsState)
					: new PlannedFile(file, Step.NOTHING, null, dmsState.document());
			case MERGE -> {
				if (action == MessageArchiveAttachmentAction.IGNORE) {
					requireWriteAcl(file, dmsState.document());
					yield new PlannedFile(file, Step.FLAG_IGNORE, null, dmsState.document());
				}
				if (action == MessageArchiveAttachmentAction.SKIP) {
					yield new PlannedFile(file, Step.NOTHING, null, dmsState.document());
				}
				if (action == MessageArchiveAttachmentAction.SIGNATURE) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"der Anhang " + file.name() + " liegt schon im DMS und wird keine Signatur mehr");
				}
				yield planMerge(file, dmsState);
			}
			case NEW -> {
				if (action == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Entscheidung zum Anhang " + file.name());
				}
				yield switch (action) {
					case ARCHIVE -> {
						if (choice.fileSubClassKey() == null || choice.fileSubClassKey().isBlank()) {
							throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Dateiart für den Anhang " + file.name());
						}
						yield new PlannedFile(file, Step.UPLOAD_ARCHIVE, requireUploadable(file, choice.fileSubClassKey()), null);
					}
					case SIGNATURE -> new PlannedFile(file, Step.UPLOAD_SIGNATURE,
							requireUploadable(file, SIGNATURE_FILE_SUB_CLASS_KEY), null);
					case IGNORE -> new PlannedFile(file, Step.UPLOAD_IGNORE, requireUploadable(file, IGNORE_FILE_SUB_CLASS_KEY), null);
					case SKIP -> new PlannedFile(file, Step.NOTHING, null, null);
				};
			}
			case NO_FILE -> new PlannedFile(file, Step.NOTHING, null, null);
		};
	}

	/** Merge in die Dateiart des vorhandenen Dokuments: Der Upload verlangt ihre Schreibrolle */
	private PlannedFile planMerge(MessageArchiveMailFile file, MessageArchiveDmsState dmsState) {
		return new PlannedFile(file, Step.MERGE, requireUploadable(file, dmsState.fileSubClassKey()), dmsState.document());
	}

	/** Die Dateiart gibt es, der User darf hinein schreiben, und sie nimmt die Endung der Datei an; sonst 400 oder 403 */
	private String requireUploadable(MessageArchiveMailFile file, String fileSubClassKey) {
		FileSubClass fileSubClass = fileSubClassService.getFileSubClassByKey(fileSubClassKey);
		if (!messageArchivePreparationService.isWritableForCurrentUser(fileSubClass)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"keine Schreibrolle der Dateiart " + fileSubClassKey + " für den Anhang " + file.name());
		}
		String extension = extensionOf(file.name());
		if (fileSubClass.getExtensions() == null || !fileSubClass.getExtensions().contains(extension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Endung " + (extension.isEmpty() ? "(keine)" : extension)
					+ " des Anhangs " + file.name() + " ist bei der Dateiart " + fileSubClassKey + " nicht freigeschaltet");
		}
		return fileSubClassKey;
	}

	/** Das Flag an einem vorhandenen Dokument setzen heißt es ändern: writeAcl des Dokuments, ADMIN immer */
	private void requireWriteAcl(MessageArchiveMailFile file, Map<String, Object> document) {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		boolean allowed = accessRoleKeys.contains("ADMIN") || MessageArchivePreparationService.valuesOf(document.get("writeAcl"))
				.stream().anyMatch(accessRoleKeys::contains);
		if (!allowed) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"du darfst das vorhandene Dokument zum Anhang " + file.name() + " nicht ändern");
		}
	}

	/** Nur angelegte Hooks (400 sonst), Werte getrimmt, ohne leere und Doppelte; leere Hooks fallen weg */
	private Map<String, List<String>> checkedHooks(Map<String, List<String>> hooks) {
		if (hooks == null) {
			return Map.of();
		}
		Set<String> knownHookKeys = solrHookService.getAllSolrHooks().stream().map(SolrHook::getKey)
				.collect(Collectors.toSet());
		Map<String, List<String>> checked = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> hook : hooks.entrySet()) {
			if (!knownHookKeys.contains(hook.getKey())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kein Hook: " + hook.getKey());
			}
			Set<String> values = new LinkedHashSet<>();
			for (String value : hook.getValue() == null ? List.<String>of() : hook.getValue()) {
				if (value != null && !value.isBlank()) {
					values.add(value.trim());
				}
			}
			if (!values.isEmpty()) {
				checked.put(hook.getKey(), new ArrayList<>(values));
			}
		}
		return checked;
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " fehlt");
		}
	}

	// ===== Schreiben =====

	/** Über den normalen Upload, mit allen seinen Prüfungen; die Datei als MultipartFile aus den Bytes von Graph */
	private FileDataUploadResult upload(MessageArchiveMailFile file, Map<String, Object> values) {
		String contentType = file.contentType() == null ? "application/octet-stream" : file.contentType();
		return fileDataService.upload(new MailAttachmentMultipartFile(file.name(), contentType, file.bytes()), values);
	}

	/** Ein mehrsprachiges JSON wie {"de":"…","en":"…","bg":"…"} für fileDescription */
	private String multilingual(String de, String en, String bg) {
		Map<String, String> texts = new LinkedHashMap<>();
		texts.put("de", de);
		texts.put("en", en);
		texts.put("bg", bg);
		return objectMapper.writeValueAsString(texts);
	}

	/**
	 * Bilder im Mailtext werden Verweise dms:<sha256>, aber nur, wenn die Datei danach im DMS liegt: data:-Bilder über
	 * ihren Hash, cid:-Bilder über die contentId ihres Anhangs. Ein kaputtes data:-Bild fällt samt img-Tag aus dem Text,
	 * ein cid ohne Anhang bleibt stehen, statt einen falschen Verweis zu erfinden.
	 */
	private static String withDmsReferences(String html, Map<String, String> sha256ByContentId, Set<String> storedSha256) {
		if (html == null || html.isBlank()) {
			return "";
		}
		Matcher image = MessageArchivePreparationService.IMAGE_AS_DATA_URI.matcher(html);
		StringBuilder withoutBase64 = new StringBuilder();
		boolean brokenImage = false;
		while (image.find()) {
			byte[] bytes = MessageArchivePreparationService.decodedOrNull(image.group(2));
			if (bytes == null) {
				image.appendReplacement(withoutBase64, "");
				brokenImage = true;
				continue;
			}
			String sha256 = MessageArchivePreparationService.sha256Hex(bytes);
			image.appendReplacement(withoutBase64,
					Matcher.quoteReplacement(storedSha256.contains(sha256) ? "dms:" + sha256 : image.group()));
		}
		image.appendTail(withoutBase64);
		String withoutBroken = brokenImage ? IMAGE_WITHOUT_SOURCE.matcher(withoutBase64).replaceAll("") : withoutBase64.toString();

		Matcher cid = IMAGE_AS_CID.matcher(withoutBroken);
		StringBuilder withReferences = new StringBuilder();
		while (cid.find()) {
			String sha256 = sha256ByContentId.get(cid.group(2).trim());
			cid.appendReplacement(withReferences, Matcher.quoteReplacement(sha256 != null && storedSha256.contains(sha256)
					? cid.group(1) + "dms:" + sha256 + cid.group(3) : cid.group()));
		}
		cid.appendTail(withReferences);
		return withReferences.toString();
	}

	// ===== Werte =====

	/** Name und Adresse in derselben Reihenfolge; ohne Namen steht die Adresse da, damit die Listen gleich lang bleiben */
	private static void putRecipients(Map<String, Object> fields, String prefix, List<GraphMailRecipient> recipients) {
		fields.put(prefix + "Names", recipients.stream().map(MessageArchiveService::nameOf).toList());
		fields.put(prefix + "Addresses", recipients.stream().map(GraphMailRecipient::address).toList());
	}

	private static String nameOf(GraphMailRecipient recipient) {
		return recipient.name() == null || recipient.name().isBlank() ? recipient.address() : recipient.name();
	}

	/** Die Sternzeiten (ids) der Kontakte aller Beteiligten dieser Rolle, ohne Doppelte */
	private static List<String> contactIdsOf(List<MessageArchiveParticipant> participants, MessageArchiveParticipant.Role role) {
		Set<String> ids = new LinkedHashSet<>();
		for (MessageArchiveParticipant participant : participants) {
			if (participant.role() == role) {
				for (Map<String, Object> contact : participant.contacts()) {
					Object id = contact.get("id");
					if (id != null) {
						ids.add(id.toString());
					}
				}
			}
		}
		return new ArrayList<>(ids);
	}

	/** Eine Zeile aus internetMessageHeaders, null ohne */
	private static String headerOf(JsonNode mail, String name) {
		for (JsonNode header : mail.path("internetMessageHeaders")) {
			if (name.equalsIgnoreCase(header.path("name").asString(""))) {
				String value = header.path("value").asString("").trim();
				return value.isEmpty() ? null : value;
			}
		}
		return null;
	}

	/** Die Endung nach der Regel des Uploads: nach dem letzten Punkt, klein; ohne Punkt leer */
	private static String extensionOf(String fileName) {
		if (fileName == null) {
			return "";
		}
		int dot = fileName.lastIndexOf('.');
		return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static void addDistinct(List<String> ids, String id) {
		if (id != null && !ids.contains(id)) {
			ids.add(id);
		}
	}

	private static List<String> orEmpty(List<String> values) {
		return values == null ? List.of() : values;
	}

	private static String textOf(JsonNode node) {
		String text = node.asString("");
		return text.isBlank() ? null : text;
	}

}
