package codergod1337.app1.graph.mail.service;

import codergod1337.app1.graph.entra.service.EntraGraphAccessTokenService;
import codergod1337.app1.graph.mail.GraphMailUnavailableException;
import codergod1337.app1.graph.mail.model.GraphMailMessage;
import codergod1337.app1.graph.mail.model.GraphMailProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Verschickt eine fertige Mail über Microsoft Graph im Namen des Absenders, der in der Nachricht steht. Nur das: kein
 * Rendern, keine Vorlage, kein Wissen über Login oder 2FA. Voraussetzung in Entra: die Anwendungsberechtigung
 * Mail.Send mit Administratoreinwilligung. Fehlt sie, gibt es ein Token, Graph antwortet aber 403, und das steht im
 * Log.
 */
@Service
public class GraphMailSendService {

	private static final Logger log = LoggerFactory.getLogger(GraphMailSendService.class);

	private static final String GRAPH_URL = "https://graph.microsoft.com/v1.0";

	private final EntraGraphAccessTokenService accessTokenService;
	private final RestClient restClient;

	public GraphMailSendService(GraphMailProperties properties, EntraGraphAccessTokenService accessTokenService) {
		this.accessTokenService = accessTokenService;
		// eigener JDK-Client mit den Timeouts aus den Properties, wie beim Token-Abruf
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build());
		requestFactory.setReadTimeout(properties.requestTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** Entra ist eingerichtet: Mails lassen sich verschicken. */
	public boolean isConfigured() {
		return accessTokenService.isConfigured();
	}

	/**
	 * Verschickt die Mail. Keine Kopie in „Gesendete Elemente“ des Absenders. Graph antwortet bei Erfolg 202 ohne
	 * Inhalt. Lehnt Graph ab oder antwortet nicht: 503, Status und Antwort nur im Log.
	 */
	public void sendGraphMail(GraphMailMessage mailMessage) {
		if (mailMessage.recipientEmail() == null || !mailMessage.recipientEmail().contains("@")) {
			throw new IllegalArgumentException("recipientEmail fehlt oder ist keine Adresse");
		}
		if (mailMessage.senderAddress() == null || !mailMessage.senderAddress().contains("@")) {
			throw new IllegalArgumentException("senderAddress fehlt oder ist keine Adresse");
		}
		String accessToken = accessTokenService.getGraphAccessToken();

		// die Nachricht im Format von Graph
		Map<String, Object> mail = Map.of(
				"message", Map.of(
						"subject", mailMessage.subject(),
						"body", Map.of("contentType", "HTML", "content", mailMessage.htmlBody()),
						"toRecipients", List.of(Map.of("emailAddress", Map.of("address", mailMessage.recipientEmail())))),
				"saveToSentItems", false);

		try {
			restClient.post()
					.uri(GRAPH_URL + "/users/{sender}/sendMail", mailMessage.senderAddress())
					.headers(headers -> headers.setBearerAuth(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.body(mail)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientResponseException e) {
			log.error("Graph lehnt den Mailversand an {} ab: Status {} Antwort {}", mailMessage.recipientEmail(),
					e.getStatusCode(), e.getResponseBodyAsString());
			throw new GraphMailUnavailableException("der Mailversand ist gerade nicht möglich", e);
		} catch (RestClientException e) {
			log.error("Mailversand über Graph an {} fehlgeschlagen: {}", mailMessage.recipientEmail(), e.getMessage());
			throw new GraphMailUnavailableException("der Mailversand ist gerade nicht möglich", e);
		}
		log.info("Mail über Graph verschickt von {} an {} (Betreff: {})", mailMessage.senderAddress(),
				mailMessage.recipientEmail(), mailMessage.subject());
	}

}
