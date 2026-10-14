package codergod1337.app1.graph.mailbox.service;

import codergod1337.app1.graph.entra.service.EntraGraphAccessTokenService;
import codergod1337.app1.graph.mail.GraphMailUnavailableException;
import codergod1337.app1.graph.mail.model.GraphMailProperties;
import codergod1337.app1.graph.mailbox.model.GraphMailAttachmentInfo;
import codergod1337.app1.graph.mailbox.model.GraphMailFolder;
import codergod1337.app1.graph.mailbox.model.GraphMailHeader;
import codergod1337.app1.graph.mailbox.model.GraphMailRecipient;
import codergod1337.app1.graph.mailbox.model.GraphMailView;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Liest Mails aus den Postfächern über Microsoft Graph (User 2026-10-14): die Liste eines Ordners, eine Mail mit allen
 * Feldern, ihre Anhänge. Nur lesen, nichts verschieben, nichts markieren. Voraussetzung in Entra: die
 * Anwendungsberechtigung Mail.Read mit Administratoreinwilligung, sonst antwortet Graph 403 und das steht im Log.
 *
 * Jede Methode prüft zuerst MailboxService.requireArchiveAccessToMailbox: die Rolle des Archivierens und den Zugriff
 * auf genau dieses Postfach. Wer eine Adresse von Hand einsetzt, die nicht seine ist, bekommt 403, bevor Graph gefragt
 * wird.
 *
 * Alle ids sind feste ids (Kopfzeile Prefer: IdType="ImmutableId"): Die gewöhnliche id ändert sich, sobald eine Mail
 * den Ordner wechselt, die feste bleibt. So gilt die beim Archivieren gespeicherte exchangeMailId auch, wenn die Mail
 * später verschoben wird.
 */
@Service
public class GraphMailboxReadService {

	private static final Logger log = LoggerFactory.getLogger(GraphMailboxReadService.class);

	private static final String GRAPH_URL = "https://graph.microsoft.com/v1.0";

	/** Wie viele Mails die Liste eines Ordners holt: eine Seite, neueste zuerst */
	private static final int MAIL_HEADER_COUNT = 50;

	/** Die Felder einer Zeile der Liste, nicht mehr: die ganze Mail kommt erst mit getMail */
	private static final String MAIL_HEADER_FIELDS = "id,subject,from,toRecipients,receivedDateTime,sentDateTime,"
			+ "hasAttachments,bodyPreview,internetMessageId";

	/**
	 * Alle Felder, die Graph an einer Nachricht kennt (Stand v1.0, wie die Vorlage). Ohne $select ließe Graph
	 * internetMessageHeaders und uniqueBody weg, das Mapping braucht aber beides.
	 */
	private static final String ALL_MAIL_FIELDS = String.join(",", "id", "createdDateTime", "lastModifiedDateTime",
			"changeKey", "categories", "receivedDateTime", "sentDateTime", "hasAttachments", "internetMessageId", "subject",
			"bodyPreview", "importance", "parentFolderId", "conversationId", "conversationIndex",
			"isDeliveryReceiptRequested", "isReadReceiptRequested", "isRead", "isDraft", "webLink",
			"inferenceClassification", "internetMessageHeaders", "body", "uniqueBody", "sender", "from", "toRecipients",
			"ccRecipients", "bccRecipients", "replyTo", "flag");

	/** Feste ids statt der gewöhnlichen, die sich beim Verschieben ändern */
	private static final String PREFER_IMMUTABLE_ID = "IdType=\"ImmutableId\"";

	/** Den Text immer als HTML, auch bei einer reinen Textmail: Anzeige und Archiv kennen so nur einen Fall */
	private static final String PREFER_HTML_BODY = "outlook.body-content-type=\"html\"";

	/** Ein eingebettetes Bild verweist im HTML der Mail auf die contentId seines Anhangs */
	private static final Pattern IMAGE_AS_CID = Pattern.compile("(src\\s*=\\s*[\"'])cid:([^\"']+)([\"'])",
			Pattern.CASE_INSENSITIVE);

	private final EntraGraphAccessTokenService accessTokenService;
	private final MailboxService mailboxService;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public GraphMailboxReadService(GraphMailProperties properties, EntraGraphAccessTokenService accessTokenService,
			MailboxService mailboxService, ObjectMapper objectMapper) {
		this.accessTokenService = accessTokenService;
		this.mailboxService = mailboxService;
		this.objectMapper = objectMapper;
		// eigener JDK-Client mit den Timeouts aus den Properties, wie beim Versand
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build());
		requestFactory.setReadTimeout(properties.requestTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** Die neuesten Mails eines Ordners, neueste zuerst. 403 ohne Zugriff auf das Postfach. */
	public List<GraphMailHeader> getMailHeaders(String mailboxEmailAddress, GraphMailFolder folder) {
		String mailbox = checkedMailbox(mailboxEmailAddress);
		if (folder == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "folder fehlt");
		}
		JsonNode response = graphGet(GRAPH_URL + "/users/{mailbox}/mailFolders/{folder}/messages"
				+ "?$top={top}&$select={select}&$orderby={orderby}", mailbox, folder.getGraphName(), MAIL_HEADER_COUNT,
				MAIL_HEADER_FIELDS, "receivedDateTime desc");
		List<GraphMailHeader> headers = new ArrayList<>();
		for (JsonNode mail : response.path("value")) {
			List<String> toRecipientAddresses = new ArrayList<>();
			for (JsonNode recipient : mail.path("toRecipients")) {
				String address = addressOf(recipient);
				if (address != null) {
					toRecipientAddresses.add(address);
				}
			}
			headers.add(new GraphMailHeader(textOf(mail.path("id")), mail.path("subject").asString(""),
					textOf(mail.path("from").path("emailAddress").path("name")), addressOf(mail.path("from")),
					toRecipientAddresses, instantOf(mail.path("receivedDateTime")), instantOf(mail.path("sentDateTime")),
					mail.path("hasAttachments").asBoolean(false), mail.path("bodyPreview").asString(""),
					textOf(mail.path("internetMessageId"))));
		}
		log.info("Graph: {} Mails aus {} von {} gelesen", headers.size(), folder.getGraphName(), mailbox);
		return headers;
	}

	/** Eine Mail mit allen Feldern, das Graph-JSON unverändert. 403 ohne Zugriff, 404 wenn das Postfach sie nicht hat. */
	public JsonNode getMail(String mailboxEmailAddress, String exchangeMailId) {
		String mailbox = checkedMailbox(mailboxEmailAddress);
		return graphGet(GRAPH_URL + "/users/{mailbox}/messages/{id}?$select={select}", mailbox,
				checkedExchangeMailId(exchangeMailId), ALL_MAIL_FIELDS);
	}

	/**
	 * Die Anhänge einer Mail, das value-Array von Graph samt Bytes (contentBytes, Base64), je Anhang mit Bytes um ein Feld
	 * ergänzt: sha256, der Hex-Hash der dekodierten Bytes. Das ist dieselbe Rechnung wie die DMS-id, derselbe Anhang
	 * ergibt hier denselben Wert. Nie über den Base64-Text hashen, der ergäbe einen anderen. Anhänge ohne Bytes (eine
	 * angehängte Mail, ein Verweis) bekommen keinen Hash. Nur für das Backend, die Bytes gehen nicht an den Client.
	 */
	public JsonNode getMailAttachments(String mailboxEmailAddress, String exchangeMailId) {
		String mailbox = checkedMailbox(mailboxEmailAddress);
		JsonNode attachments = graphGet(GRAPH_URL + "/users/{mailbox}/messages/{id}/attachments", mailbox,
				checkedExchangeMailId(exchangeMailId)).path("value");
		for (JsonNode attachment : attachments) {
			if (attachment instanceof ObjectNode attachmentObject && attachmentObject.hasNonNull("contentBytes")) {
				byte[] bytes = Base64.getDecoder().decode(attachmentObject.get("contentBytes").asString());
				attachmentObject.put("sha256", sha256Hex(bytes));
			}
		}
		return attachments;
	}

	/**
	 * Eine Mail zum Anzeigen (User 2026-10-14): Kopf, die ganze Mail und den neuen Teil als HTML, die Anhänge ohne Bytes.
	 * Die eingebetteten Bilder (cid:) werden in beiden Texten gegen data:-Bilder aus den Anhängen getauscht, so braucht
	 * der Client die Anhänge nicht. Ein cid ohne Anhang bleibt stehen, statt einen falschen Verweis zu erfinden.
	 */
	public GraphMailView getMailView(String mailboxEmailAddress, String exchangeMailId) {
		return toMailView(getMail(mailboxEmailAddress, exchangeMailId), getMailAttachments(mailboxEmailAddress, exchangeMailId));
	}

	/**
	 * Die Ansicht aus schon geholter Mail und schon geholten Anhängen (mit sha256), ohne Graph zu fragen: für das
	 * Aufbereiten, das die Anhänge ohnehin braucht und sie nicht zweimal holen soll.
	 */
	public GraphMailView toMailView(JsonNode mail, JsonNode attachments) {
		Map<String, String> dataUriByContentId = new HashMap<>();
		List<GraphMailAttachmentInfo> attachmentInfos = new ArrayList<>();
		for (JsonNode attachment : attachments) {
			String contentId = contentIdOf(attachment);
			if (contentId != null && attachment.hasNonNull("contentBytes")) {
				String contentType = attachment.path("contentType").asString("");
				dataUriByContentId.put(contentId, "data:" + (contentType.isBlank() ? "application/octet-stream" : contentType)
						+ ";base64," + attachment.get("contentBytes").asString());
			}
			attachmentInfos.add(new GraphMailAttachmentInfo(attachment.path("name").asString(""),
					textOf(attachment.path("contentType")), attachment.path("size").asLong(0),
					textOf(attachment.path("sha256")), attachment.path("isInline").asBoolean(false)));
		}
		return new GraphMailView(textOf(mail.path("id")), mail.path("subject").asString(""),
				recipientOf(mail.path("from")), recipientsOf(mail.path("toRecipients")),
				recipientsOf(mail.path("ccRecipients")), recipientsOf(mail.path("bccRecipients")),
				instantOf(mail.path("sentDateTime")), instantOf(mail.path("receivedDateTime")),
				textOf(mail.path("importance")),
				withInlineImages(mail.path("body").path("content").asString(""), dataUriByContentId),
				withInlineImages(mail.path("uniqueBody").path("content").asString(""), dataUriByContentId),
				attachmentInfos);
	}

	/** cid:-Bilder gegen die data:-Bilder ihrer Anhänge tauschen; ein cid ohne Anhang bleibt stehen */
	private static String withInlineImages(String html, Map<String, String> dataUriByContentId) {
		if (html.isEmpty() || dataUriByContentId.isEmpty()) {
			return html;
		}
		Matcher image = IMAGE_AS_CID.matcher(html);
		StringBuilder result = new StringBuilder();
		while (image.find()) {
			String dataUri = dataUriByContentId.get(image.group(2).trim());
			image.appendReplacement(result,
					Matcher.quoteReplacement(dataUri == null ? image.group() : image.group(1) + dataUri + image.group(3)));
		}
		image.appendTail(result);
		return result.toString();
	}

	/** Die contentId eines Anhangs ohne spitze Klammern, wie das HTML sie hinter cid: nennt; null ohne */
	private static String contentIdOf(JsonNode attachment) {
		String contentId = attachment.path("contentId").asString("").trim();
		if (contentId.startsWith("<") && contentId.endsWith(">")) {
			contentId = contentId.substring(1, contentId.length() - 1).trim();
		}
		return contentId.isEmpty() ? null : contentId;
	}

	private static GraphMailRecipient recipientOf(JsonNode recipient) {
		String address = addressOf(recipient);
		return address == null ? null : new GraphMailRecipient(textOf(recipient.path("emailAddress").path("name")), address);
	}

	private static List<GraphMailRecipient> recipientsOf(JsonNode recipients) {
		List<GraphMailRecipient> result = new ArrayList<>();
		for (JsonNode recipient : recipients) {
			GraphMailRecipient graphMailRecipient = recipientOf(recipient);
			if (graphMailRecipient != null) {
				result.add(graphMailRecipient);
			}
		}
		return result;
	}

	// ===== Graph =====

	/** Ein GET mit Token und fester id. 404 von Graph wird 404, alles andere 503 mit Status und Antwort nur im Log. */
	private JsonNode graphGet(String uriTemplate, Object... uriVariables) {
		String accessToken = accessTokenService.getGraphAccessToken();
		String body;
		try {
			body = restClient.get()
					.uri(uriTemplate, uriVariables)
					.headers(headers -> {
						headers.setBearerAuth(accessToken);
						headers.add("Prefer", PREFER_IMMUTABLE_ID);
						headers.add("Prefer", PREFER_HTML_BODY);
					})
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Mail mit dieser id im Postfach");
			}
			log.error("Graph lehnt das Lesen ab: Status {} Antwort {}", e.getStatusCode(), e.getResponseBodyAsString());
			throw new GraphMailUnavailableException("das Postfach ist gerade nicht lesbar", e);
		} catch (RestClientException e) {
			log.error("Lesen über Graph fehlgeschlagen: {}", e.getMessage());
			throw new GraphMailUnavailableException("das Postfach ist gerade nicht lesbar", e);
		}
		if (body == null || body.isBlank()) {
			log.error("Graph antwortet ohne Inhalt");
			throw new GraphMailUnavailableException("das Postfach ist gerade nicht lesbar");
		}
		try {
			return objectMapper.readTree(body);
		} catch (JacksonException e) {
			log.error("Graph antwortet nicht mit JSON: {}", e.getOriginalMessage());
			throw new GraphMailUnavailableException("das Postfach ist gerade nicht lesbar", e);
		}
	}

	// ===== Prüfungen und Werte =====

	/** Erst die Rolle und der Zugriff auf genau dieses Postfach (403), dann die Adresse klein für die URL */
	private String checkedMailbox(String mailboxEmailAddress) {
		mailboxService.requireArchiveAccessToMailbox(mailboxEmailAddress);
		return mailboxEmailAddress.trim().toLowerCase(Locale.ROOT);
	}

	private static String checkedExchangeMailId(String exchangeMailId) {
		if (exchangeMailId == null || exchangeMailId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exchangeMailId fehlt");
		}
		return exchangeMailId.trim();
	}

	/** Die Adresse aus {emailAddress:{name,address}}, klein geschrieben; null ohne */
	private static String addressOf(JsonNode recipient) {
		String address = recipient.path("emailAddress").path("address").asString("");
		return address.isBlank() ? null : address.trim().toLowerCase(Locale.ROOT);
	}

	private static String textOf(JsonNode node) {
		String text = node.asString("");
		return text.isBlank() ? null : text;
	}

	/** Ein Zeitpunkt von Graph (ISO, z. B. 2026-10-14T08:15:00Z); null ohne oder wenn er nicht lesbar ist */
	private static Instant instantOf(JsonNode node) {
		String text = node.asString("");
		if (text.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(text);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 nicht verfügbar", e);
		}
	}

}
