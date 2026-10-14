package codergod1337.app1.message.archive.service;

import codergod1337.app1.abas.model.AbasBelegnummerLookupResult;
import codergod1337.app1.abas.service.AbasBelegnummerService;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.graph.mail.model.GraphMailProperties;
import codergod1337.app1.graph.mailbox.model.GraphMailRecipient;
import codergod1337.app1.graph.mailbox.model.GraphMailView;
import codergod1337.app1.graph.mailbox.service.GraphMailboxReadService;
import codergod1337.app1.graph.mailbox.service.MailboxService;
import codergod1337.app1.message.archive.model.MessageArchiveAttachment;
import codergod1337.app1.message.archive.model.MessageArchiveAttachmentState;
import codergod1337.app1.message.archive.model.MessageArchiveMailFile;
import codergod1337.app1.message.archive.model.MessageArchiveNumber;
import codergod1337.app1.message.archive.model.MessageArchiveParticipant;
import codergod1337.app1.message.archive.model.MessageArchivePreparation;
import codergod1337.app1.message.messagesubclass.model.MessageSubClass;
import codergod1337.app1.message.messagesubclass.service.MessageSubClassService;
import codergod1337.app1.solr.dms.model.DmsExistence;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.solr.kontakte.service.SolrDocumentKontakteService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.tika.TikaService;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Das Aufbereiten einer Mail vor dem Archivieren (User 2026-10-14, nach der Aufbereiten-Seite der Vorlage, der Scan dort
 * im Browser liegt hier im Backend): alles, was die Seite zeigt und vorschlägt, in einer Antwort. Gespeichert wird
 * nichts. Alles läuft mit den Rechten des Archivierenden: das Postfach (MailboxService), die DMS-Dokumente, die Kontakte.
 *
 * Anhänge: erst nur der Hash (getExistence); gibt es die Bytes schon, wird das ganze DMS-Dokument mit den Rechten des
 * Users geholt, daraus kommen Dateiart und die Flags in wtf. Bekommt er es nicht, ist das ein Fehler (NO_ACCESS). Für
 * neue Anhänge der feste Teil des Dateiart-Vorschlags aus den Triggerwörtern, nur für Dateiarten, in die er schreiben
 * darf. Die Trigger-Hooks rechnet die Seite dazu, damit der Vorschlag mitspringt, sobald Hooks gesetzt werden.
 *
 * Nummern: Ziffernfolgen ab 4 Stellen als eigenes Wort aus Betreff und neuem Teil, mit Schrägstrich verbundene einzeln
 * (Regel der Vorlage), jede bei ABAS über die Belegnummer, höchstens 3 gleichzeitig und 30 je Mail. Zurück kommen nur
 * die, die ABAS kennt, mit dem Hook, dessen abasObjectTypes ihren Typ nennen.
 *
 * Kontakte: jede Adresse aus Von, Gesendet von, An, Cc und Bcc exakt im Kern KONTAKTE, außer unseren eigenen auf der
 * Domäne der Postfächer. Ohne die AR KONTAKTE bleiben sie leer.
 *
 * Triggerwörter treffen als Teilstring ohne Rücksicht auf Groß- und Kleinschreibung: „Rechnung“ trifft auch
 * „Rechnungsnummer“, im Deutschen mit seinen zusammengesetzten Wörtern das Nützlichere.
 */
@Service
public class MessageArchivePreparationService {

	private static final Logger log = LoggerFactory.getLogger(MessageArchivePreparationService.class);

	/** Der Hook der Flags am DMS-Dokument */
	public static final String FLAG_HOOK_KEY = "wtf";

	/** Flag einer Signatur: wird still eingetragen, nie gelistet, bekommt nie Hooks */
	public static final String SIGNATURE_FLAG = "ATTACHMENT_SIGNATURE";

	/** Flag einer Datei, die nicht archiviert wird, z. B. unsere eigenen Firmendokumente zurück vom Kunden */
	public static final String IGNORE_FLAG = "ATTACHMENT_IGNORE";

	/** Ab wie vielen Ziffern eine Zahl ein Kandidat ist (Vorlage) */
	private static final int MIN_NUMBER_DIGITS = 4;

	/** Mehr Nummern fragt eine Mail nicht bei ABAS an */
	private static final int MAX_NUMBERS = 30;

	/** So viele Anfragen an ABAS gleichzeitig, eine dauert etwa eine Sekunde (Vorlage) */
	private static final int ABAS_PARALLEL = 3;

	/** Punkte: das Postfach hängt an der Nachrichtenart */
	private static final int MAILBOX_POINTS = 3;

	/** Punkte je Treffer eines Triggerworts in Betreff oder Mailtext */
	private static final int MAIL_WORD_POINTS = 1;

	/** Punkte je Treffer eines Triggerworts in Dateiname oder Text des Anhangs */
	private static final int ATTACHMENT_WORD_POINTS = 2;

	private static final String KUNDEN_NUMMER_HOOK_KEY = "kundenNummer";
	private static final String LIEFERANTEN_NUMMER_HOOK_KEY = "lieferantenNummer";

	/** Das Feld der Adresse im Kern KONTAKTE, auflösbar ohne Rücksicht auf Groß- und Kleinschreibung */
	private static final String CONTACT_EMAIL_FIELD_NAME = "email";

	/** Eine Ziffernfolge als eigenes Wort, mit Schrägstrich verbundene als Gruppe; danach Ende, Leerraum oder . , ? */
	private static final Pattern NUMBER_GROUP = Pattern.compile("(?<!\\S)\\d+(?:/\\d+)*(?=$|\\s|[.,?])");

	/** Ein Typ ohne die Vorsilbe obj:data:, damit 3:1 und obj:data:3:1 dasselbe meinen */
	private static final Pattern OBJECT_TYPE_PREFIX = Pattern.compile("^obj:data:");

	/** Ein Bild, das als Base64 direkt im HTML steht (Vorlage); das Archivieren tauscht es gegen dms:<id> */
	public static final Pattern IMAGE_AS_DATA_URI = Pattern.compile("data:image/([a-z0-9.+-]+);base64,([A-Za-z0-9+/=\\s]+)",
			Pattern.CASE_INSENSITIVE);

	private final GraphMailboxReadService graphMailboxReadService;
	private final GraphMailProperties graphMailProperties;
	private final SolrDocumentDmsService solrDocumentDmsService;
	private final FileSubClassService fileSubClassService;
	private final MessageSubClassService messageSubClassService;
	private final SolrHookService solrHookService;
	private final AbasBelegnummerService abasBelegnummerService;
	private final SolrDocumentKontakteService solrDocumentKontakteService;
	private final TikaService tikaService;
	private final CurrentUsersProvider currentUsersProvider;

	public MessageArchivePreparationService(GraphMailboxReadService graphMailboxReadService,
			GraphMailProperties graphMailProperties, SolrDocumentDmsService solrDocumentDmsService,
			FileSubClassService fileSubClassService, MessageSubClassService messageSubClassService,
			SolrHookService solrHookService, AbasBelegnummerService abasBelegnummerService,
			SolrDocumentKontakteService solrDocumentKontakteService, TikaService tikaService,
			CurrentUsersProvider currentUsersProvider) {
		this.graphMailboxReadService = graphMailboxReadService;
		this.graphMailProperties = graphMailProperties;
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.fileSubClassService = fileSubClassService;
		this.messageSubClassService = messageSubClassService;
		this.solrHookService = solrHookService;
		this.abasBelegnummerService = abasBelegnummerService;
		this.solrDocumentKontakteService = solrDocumentKontakteService;
		this.tikaService = tikaService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Alles für die Aufbereiten-Seite einer Mail. 403 ohne die Rolle des Archivierens oder ohne Zugriff aufs Postfach. */
	@PreAuthorize("hasAnyRole('" + MailboxService.ARCHIVE_ACCESS_ROLE_KEY + "', 'ADMIN')")
	public MessageArchivePreparation prepareMessageArchive(String mailbox, String exchangeMailId) {
		// Mail und Anhänge je einmal von Graph; beide Aufrufe prüfen den Zugriff aufs Postfach
		JsonNode mail = graphMailboxReadService.getMail(mailbox, exchangeMailId);
		JsonNode attachments = graphMailboxReadService.getMailAttachments(mailbox, exchangeMailId);
		GraphMailView mailView = graphMailboxReadService.toMailView(mail, attachments);

		// der Text für Triggerwörter und Nummern: Betreff und neuer Teil, bei einer Weiterleitung ohne eigenes Wort die ganze Mail
		String newPartText = textOfHtml(mailView.uniqueBodyHtml());
		String mailText = (mailView.subject() + " " + (newPartText.isBlank() ? textOfHtml(mailView.bodyHtml()) : newPartText))
				.toLowerCase(Locale.ROOT);

		List<MessageArchiveAttachment> archiveAttachments = attachmentsOf(mailFilesOf(mail, attachments), mailText);
		List<MessageArchiveNumber> numbers = numbersOf(mailView.subject() + "\n" + newPartText);
		MessageArchiveParticipants participants = participantsOf(mail, mailView);

		return new MessageArchivePreparation(mailView, archiveAttachments, numbers, participants.participants(),
				participants.contactsAllowed(), messageSubClassBaseScoresOf(mailbox, mailText),
				suggestedHooksOf(participants.participants(), numbers));
	}

	// ===== Dateien der Mail und ihr Stand im DMS, gemeinsam mit dem Archivieren =====

	/** Der Stand einer Datei im DMS und, wenn es sie schon gibt und der User es lesen darf, das Dokument */
	public record MessageArchiveDmsState(MessageArchiveAttachmentState state, Map<String, Object> document,
			String fileSubClassKey) {
	}

	/** Die Beteiligten einer Mail mit ihren Kontakten; contactsAllowed false ohne die AR KONTAKTE */
	public record MessageArchiveParticipants(List<MessageArchiveParticipant> participants, boolean contactsAllowed) {
	}

	/**
	 * Alle Dateien einer Mail: die Anhänge von Graph (mit sha256, ohne Bytes als Verweis) und die Bilder, die als data:
	 * direkt im HTML stehen, aus der ganzen Mail und dem neuen Teil, wie Graph sie liefert. Dieselben Bytes nur einmal,
	 * ein kaputtes Bild (abgeschnittenes Base64, Outlook) fällt weg.
	 */
	public static List<MessageArchiveMailFile> mailFilesOf(JsonNode mail, JsonNode attachments) {
		List<MessageArchiveMailFile> files = new ArrayList<>();
		Set<String> seenSha256 = new LinkedHashSet<>();
		for (JsonNode attachment : attachments) {
			String name = attachment.path("name").asString("");
			String contentType = textOf(attachment.path("contentType"));
			long size = attachment.path("size").asLong(0);
			boolean inline = attachment.path("isInline").asBoolean(false);
			String sha256 = textOf(attachment.path("sha256"));
			String contentId = contentIdOf(attachment);
			if (sha256 == null || !attachment.hasNonNull("contentBytes")) {
				files.add(new MessageArchiveMailFile(name, contentType, null, null, size, inline, contentId, false));
				continue;
			}
			byte[] bytes = Base64.getDecoder().decode(attachment.get("contentBytes").asString());
			seenSha256.add(sha256);
			files.add(new MessageArchiveMailFile(name, contentType, bytes, sha256, bytes.length, inline, contentId, false));
		}
		int imageNumber = 0;
		for (String html : List.of(mail.path("body").path("content").asString(""),
				mail.path("uniqueBody").path("content").asString(""))) {
			Matcher image = IMAGE_AS_DATA_URI.matcher(html);
			while (image.find()) {
				byte[] bytes = decodedOrNull(image.group(2));
				if (bytes == null) {
					continue;
				}
				String sha256 = sha256Hex(bytes);
				if (seenSha256.add(sha256)) {
					imageNumber++;
					String type = image.group(1).toLowerCase(Locale.ROOT);
					String extension = type.replace("jpeg", "jpg").replace("svg+xml", "svg");
					files.add(new MessageArchiveMailFile("bild-im-text-" + imageNumber + "." + extension, "image/" + type, bytes,
							sha256, bytes.length, true, null, true));
				}
			}
		}
		return files;
	}

	/**
	 * Der Stand einer Datei im DMS: erst nur der Hash; gibt es die Bytes schon, das ganze Dokument mit den Rechten des
	 * Users. Bekommt er es nicht, NO_ACCESS. Sonst nach den Flags in wtf SIGNATURE, IGNORED oder MERGE.
	 */
	public MessageArchiveDmsState dmsStateOf(String sha256) {
		DmsExistence existence = solrDocumentDmsService.getExistence(sha256);
		if (!existence.exists()) {
			return new MessageArchiveDmsState(MessageArchiveAttachmentState.NEW, null, null);
		}
		Map<String, Object> document = readableDmsDocumentOf(sha256);
		if (document == null) {
			return new MessageArchiveDmsState(MessageArchiveAttachmentState.NO_ACCESS, null, existence.fileSubClassKey());
		}
		List<String> flags = valuesOf(document.get(FLAG_HOOK_KEY));
		MessageArchiveAttachmentState state = flags.contains(SIGNATURE_FLAG) ? MessageArchiveAttachmentState.SIGNATURE
				: flags.contains(IGNORE_FLAG) ? MessageArchiveAttachmentState.IGNORED : MessageArchiveAttachmentState.MERGE;
		Object fileSubClassKey = document.get("fileSubClassKey");
		return new MessageArchiveDmsState(state, document, fileSubClassKey == null ? null : fileSubClassKey.toString());
	}

	/** Von, Gesendet von, An, Cc und Bcc mit den Kontakten zu ihren Adressen */
	public MessageArchiveParticipants participantsOf(JsonNode mail, GraphMailView mailView) {
		List<MessageArchiveParticipant> participants = new ArrayList<>();
		addParticipant(participants, MessageArchiveParticipant.Role.FROM, mailView.from());
		GraphMailRecipient sender = senderOf(mail);
		if (sender != null && (mailView.from() == null || !sender.address().equals(mailView.from().address()))) {
			addParticipant(participants, MessageArchiveParticipant.Role.SENDER, sender);
		}
		mailView.toRecipients().forEach(recipient -> addParticipant(participants, MessageArchiveParticipant.Role.TO, recipient));
		mailView.ccRecipients().forEach(recipient -> addParticipant(participants, MessageArchiveParticipant.Role.CC, recipient));
		mailView.bccRecipients().forEach(recipient -> addParticipant(participants, MessageArchiveParticipant.Role.BCC, recipient));
		boolean contactsAllowed = withContacts(participants);
		return new MessageArchiveParticipants(participants, contactsAllowed);
	}

	// ===== Anhänge =====

	private List<MessageArchiveAttachment> attachmentsOf(List<MessageArchiveMailFile> files, String mailText) {
		// nur Dateiarten, in die der User schreiben darf: Die anderen könnte er beim Archivieren gar nicht wählen
		List<FileSubClass> writableFileSubClasses = fileSubClassService.getAllFileSubClasses().stream()
				.filter(this::isWritableForCurrentUser).toList();
		Map<String, Integer> mailHitsByFileSubClassKey = new HashMap<>();
		for (FileSubClass fileSubClass : writableFileSubClasses) {
			mailHitsByFileSubClassKey.put(fileSubClass.getKey(), hitsOf(fileSubClass.getTriggerWords(), mailText));
		}

		List<MessageArchiveAttachment> result = new ArrayList<>();
		for (MessageArchiveMailFile file : files) {
			if (file.bytes() == null) {
				result.add(new MessageArchiveAttachment(file.name(), file.contentType(), file.size(), null, file.inline(),
						false, MessageArchiveAttachmentState.NO_FILE, null, Map.of()));
				continue;
			}
			MessageArchiveDmsState dmsState = dmsStateOf(file.sha256());
			Map<String, Integer> scores = new LinkedHashMap<>();
			if (dmsState.state() == MessageArchiveAttachmentState.NEW) {
				String attachmentText = (file.name() + " " + tikaTextOf(file.bytes(), file.name(), file.contentType()))
						.toLowerCase(Locale.ROOT);
				// jede beschreibbare Dateiart, auch mit 0: Die Seite rechnet die Trigger-Hooks dazu und wählt nur unter diesen
				for (FileSubClass fileSubClass : writableFileSubClasses) {
					scores.put(fileSubClass.getKey(), mailHitsByFileSubClassKey.get(fileSubClass.getKey()) * MAIL_WORD_POINTS
							+ hitsOf(fileSubClass.getTriggerWords(), attachmentText) * ATTACHMENT_WORD_POINTS);
				}
			}
			result.add(new MessageArchiveAttachment(file.name(), file.contentType(), file.size(), file.sha256(),
					file.inline(), file.embeddedInText(), dmsState.state(), dmsState.fileSubClassKey(), scores));
		}
		return result;
	}

	/** Das DMS-Dokument, so weit der User es lesen darf; null, wenn er es nicht bekommt (keine AR DMS, keine Leserolle) */
	private Map<String, Object> readableDmsDocumentOf(String sha256) {
		try {
			return solrDocumentDmsService.getById(sha256);
		} catch (ResponseStatusException e) {
			if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value() || e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				return null;
			}
			throw e;
		}
	}

	/** Darf der User in diese Dateiart schreiben? Dieselbe Regel wie der Upload: eine Schreib-AR, ADMIN immer */
	public boolean isWritableForCurrentUser(FileSubClass fileSubClass) {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains("ADMIN")
				|| fileSubClass.getWriteAccessRoleKeys().stream().anyMatch(accessRoleKeys::contains);
	}

	/** Der Text des Anhangs über Tika; kann Tika ihn nicht lesen, zählt nur der Dateiname */
	private String tikaTextOf(byte[] bytes, String name, String contentType) {
		try {
			String text = tikaService.read(new ByteArrayInputStream(bytes), name, contentType).text();
			return text == null ? "" : text;
		} catch (TikaService.TikaReadException e) {
			log.info("Archivieren: Tika liest {} nicht ({}), es zählt nur der Dateiname", name, e.getMessage());
			return "";
		}
	}

	// ===== Nachrichtenart =====

	/** Postfach an der Art 3 Punkte, je Treffer eines Triggerworts 1 Punkt; nur Arten mit Punkten */
	private Map<String, Integer> messageSubClassBaseScoresOf(String mailbox, String mailText) {
		String normalizedMailbox = mailbox.trim().toLowerCase(Locale.ROOT);
		Map<String, Integer> scores = new LinkedHashMap<>();
		for (MessageSubClass messageSubClass : messageSubClassService.getAllMessageSubClasses()) {
			int score = (messageSubClass.getMailboxEmailAddresses().contains(normalizedMailbox) ? MAILBOX_POINTS : 0)
					+ hitsOf(messageSubClass.getTriggerWords(), mailText) * MAIL_WORD_POINTS;
			if (score > 0) {
				scores.put(messageSubClass.getKey(), score);
			}
		}
		return scores;
	}

	// ===== Nummern =====

	private List<MessageArchiveNumber> numbersOf(String text) {
		List<String> candidates = numberCandidatesIn(text);
		if (candidates.isEmpty()) {
			return List.of();
		}
		Map<String, String> hookKeyByObjectType = hookKeyByObjectType();
		// höchstens 3 gleichzeitig; der Security-Context des Users geht mit in die Threads
		ExecutorService pool = new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(ABAS_PARALLEL));
		try {
			List<Future<AbasBelegnummerLookupResult>> lookups = new ArrayList<>();
			for (String candidate : candidates) {
				lookups.add(pool.submit(() -> lookupOrNull(candidate)));
			}
			List<MessageArchiveNumber> numbers = new ArrayList<>();
			for (Future<AbasBelegnummerLookupResult> lookup : lookups) {
				AbasBelegnummerLookupResult result = lookup.get();
				if (result != null && result.found()) {
					String hookKey = result.objectType() == null ? null
							: hookKeyByObjectType.get(objectTypeKeyOf(result.objectType()));
					numbers.add(new MessageArchiveNumber(result, hookKey));
				}
			}
			return numbers;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return List.of();
		} catch (ExecutionException e) {
			log.warn("Archivieren: Nummern bei ABAS nicht nachgeschlagen: {}", e.getMessage());
			return List.of();
		} finally {
			pool.shutdownNow();
		}
	}

	/** Eine Nummer bei ABAS; ein Fehler (ABAS nicht erreichbar) zählt als unbekannt und steht im Log */
	private AbasBelegnummerLookupResult lookupOrNull(String candidate) {
		try {
			return abasBelegnummerService.lookupBelegnummer(candidate);
		} catch (RuntimeException e) {
			log.info("Archivieren: Nummer {} bei ABAS nicht nachgeschlagen: {}", candidate, e.getMessage());
			return null;
		}
	}

	/**
	 * Die Nummern im Text, ohne Doppelte, in Reihenfolge des Auftretens, höchstens 30 (Regel der Vorlage): eine
	 * Ziffernfolge ab 4 Stellen als eigenes Wort. Mit Schrägstrich verbundene sind mehrere Nummern, jede einzeln geprüft;
	 * damit Telefonnummern und Daten draußen bleiben, gilt so eine Gruppe nur, wenn jeder Teil mindestens 4 Stellen hat
	 * und keiner mit 0 beginnt.
	 */
	static List<String> numberCandidatesIn(String text) {
		Set<String> found = new LinkedHashSet<>();
		Matcher group = NUMBER_GROUP.matcher(text == null ? "" : text);
		while (group.find() && found.size() < MAX_NUMBERS) {
			String[] parts = group.group().split("/");
			boolean joined = parts.length > 1;
			if (joined && Arrays.stream(parts).anyMatch(part -> part.length() < MIN_NUMBER_DIGITS || part.startsWith("0"))) {
				continue;
			}
			for (String part : parts) {
				if (part.length() >= MIN_NUMBER_DIGITS && found.size() < MAX_NUMBERS) {
					found.add(part);
				}
			}
		}
		return new ArrayList<>(found);
	}

	/** Je ABAS-Typ der Hook, dessen abasObjectTypes ihn nennen; der erste in Listenreihenfolge gewinnt */
	private Map<String, String> hookKeyByObjectType() {
		Map<String, String> hookKeyByObjectType = new HashMap<>();
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			for (String objectType : solrHook.getAbasObjectTypes()) {
				hookKeyByObjectType.putIfAbsent(objectTypeKeyOf(objectType), solrHook.getKey());
			}
		}
		return hookKeyByObjectType;
	}

	private static String objectTypeKeyOf(String objectType) {
		return OBJECT_TYPE_PREFIX.matcher(objectType.trim()).replaceFirst("");
	}

	// ===== Beteiligte und Kontakte =====

	private static void addParticipant(List<MessageArchiveParticipant> participants, MessageArchiveParticipant.Role role,
			GraphMailRecipient recipient) {
		if (recipient != null) {
			participants.add(new MessageArchiveParticipant(role, recipient.name(), recipient.address(), List.of()));
		}
	}

	/** Gesendet von: wer tatsächlich gesendet hat, steht nur im Graph-JSON, nicht in der Ansicht */
	private static GraphMailRecipient senderOf(JsonNode mail) {
		String address = mail.path("sender").path("emailAddress").path("address").asString("").trim().toLowerCase(Locale.ROOT);
		if (address.isEmpty()) {
			return null;
		}
		return new GraphMailRecipient(textOf(mail.path("sender").path("emailAddress").path("name")), address);
	}

	/**
	 * Die Kontakte je Beteiligtem, jede Adresse einmal gefragt; unsere eigenen Adressen auf der Domäne der Postfächer
	 * nicht. Ohne die AR KONTAKTE (403) bleibt alles leer: false.
	 */
	private boolean withContacts(List<MessageArchiveParticipant> participants) {
		String ownDomainSuffix = ownDomainSuffix();
		Map<String, List<Map<String, Object>>> contactsByAddress = new HashMap<>();
		for (int index = 0; index < participants.size(); index++) {
			MessageArchiveParticipant participant = participants.get(index);
			if (ownDomainSuffix != null && participant.address().endsWith(ownDomainSuffix)) {
				continue;
			}
			List<Map<String, Object>> contacts = contactsByAddress.get(participant.address());
			if (contacts == null) {
				try {
					contacts = solrDocumentKontakteService
							.resolveKontakteDocuments(Map.of(CONTACT_EMAIL_FIELD_NAME, participant.address()), 0).documents();
				} catch (ResponseStatusException e) {
					if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
						return false;
					}
					throw e;
				}
				contactsByAddress.put(participant.address(), contacts);
			}
			participants.set(index,
					new MessageArchiveParticipant(participant.role(), participant.name(), participant.address(), contacts));
		}
		return true;
	}

	/** „@pahling.de“ aus app1.graph.mail.domain, null ohne Domäne */
	private String ownDomainSuffix() {
		String domain = graphMailProperties.domain();
		if (domain == null || domain.isBlank()) {
			return null;
		}
		return "@" + domain.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
	}

	/**
	 * Die vorgeschlagenen Hooks: Kunden- und Lieferantennummern aus den Kontakten des Absenders, hat er keine (etwa bei
	 * einer gesendeten Mail, da sind wir der Absender), aus denen der Empfänger; dazu die Nummern mit erkanntem Hook.
	 */
	private static Map<String, List<String>> suggestedHooksOf(List<MessageArchiveParticipant> participants,
			List<MessageArchiveNumber> numbers) {
		Map<String, Set<String>> suggested = new LinkedHashMap<>();
		List<Map<String, Object>> senderContacts = participants.stream()
				.filter(participant -> participant.role() == MessageArchiveParticipant.Role.FROM)
				.flatMap(participant -> participant.contacts().stream()).toList();
		List<Map<String, Object>> contacts = !senderContacts.isEmpty() ? senderContacts
				: participants.stream().filter(participant -> participant.role() == MessageArchiveParticipant.Role.TO)
						.flatMap(participant -> participant.contacts().stream()).toList();
		for (Map<String, Object> contact : contacts) {
			for (String hookKey : List.of(KUNDEN_NUMMER_HOOK_KEY, LIEFERANTEN_NUMMER_HOOK_KEY)) {
				for (String value : valuesOf(contact.get(hookKey))) {
					suggested.computeIfAbsent(hookKey, key -> new LinkedHashSet<>()).add(value);
				}
			}
		}
		for (MessageArchiveNumber number : numbers) {
			if (number.suggestedHookKey() != null) {
				suggested.computeIfAbsent(number.suggestedHookKey(), key -> new LinkedHashSet<>())
						.add(number.lookup().belegnummer());
			}
		}
		Map<String, List<String>> result = new LinkedHashMap<>();
		suggested.forEach((hookKey, values) -> result.put(hookKey, new ArrayList<>(values)));
		return result;
	}

	// ===== Werte =====

	/** Wie oft die Wörter im Text stehen, als Teilstring; der Text ist schon klein geschrieben */
	private static int hitsOf(List<String> words, String lowerText) {
		int hits = 0;
		for (String word : words) {
			String needle = word.trim().toLowerCase(Locale.ROOT);
			if (needle.isEmpty()) {
				continue;
			}
			int from = 0;
			int index;
			while ((index = lowerText.indexOf(needle, from)) >= 0) {
				hits++;
				from = index + needle.length();
			}
		}
		return hits;
	}

	/** Grob vom HTML zum Text, nur zum Zählen und Suchen: ohne Stil- und Skriptblöcke, ohne Tags */
	public static String textOfHtml(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}
		return html.replaceAll("(?is)<(style|script)[^>]*>.*?</\\1>", " ").replaceAll("(?s)<[^>]+>", " ")
				.replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("\\s+", " ").trim();
	}

	/** Base64 dekodiert, null bei einem kaputten Bild (Outlook schneidet das Base64 manchmal mitten im Bild ab) */
	public static byte[] decodedOrNull(String base64) {
		try {
			byte[] bytes = Base64.getMimeDecoder().decode(base64);
			return bytes.length == 0 ? null : bytes;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Der Hex-Hash der Bytes, dieselbe Rechnung wie die DMS-id */
	public static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 nicht verfügbar", e);
		}
	}

	/** Die contentId eines Anhangs ohne spitze Klammern, wie das HTML sie hinter cid: nennt; null ohne */
	private static String contentIdOf(JsonNode attachment) {
		String contentId = attachment.path("contentId").asString("").trim();
		if (contentId.startsWith("<") && contentId.endsWith(">")) {
			contentId = contentId.substring(1, contentId.length() - 1).trim();
		}
		return contentId.isEmpty() ? null : contentId;
	}

	/** Die Werte eines Solr-Feldes als Texte: ein Wert, eine Liste oder nichts */
	public static List<String> valuesOf(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof Collection<?> values) {
			return values.stream().filter(entry -> entry != null && !entry.toString().isBlank()).map(Object::toString).toList();
		}
		return value.toString().isBlank() ? List.of() : List.of(value.toString());
	}

	private static String textOf(JsonNode node) {
		String text = node.asString("");
		return text.isBlank() ? null : text;
	}

}
