package codergod1337.app1.graph.entra.service;

import codergod1337.app1.graph.entra.EntraUnavailableException;
import codergod1337.app1.graph.entra.model.EntraProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Holt das Zugriffstoken für Graph (Client-Credentials, App-only, Nachweis per Assertion) und hält es im Speicher.
 * Microsoft stellt es rund eine Stunde aus; ab fünf Minuten vor dem Ablauf holt der nächste Aufrufer ein neues.
 * Synchronized, damit nie zwei Abrufe parallel laufen. Token und Assertion stehen nie im Log. Ein Token für alles,
 * was Graph ist: heute der Mailversand, später die Postfächer.
 */
@Service
public class EntraGraphAccessTokenService {

	private static final Logger log = LoggerFactory.getLogger(EntraGraphAccessTokenService.class);

	/** .default: alle Anwendungsberechtigungen, die der App-Registrierung für Graph erteilt sind */
	private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";

	/** fester Wert nach RFC 7523: Der Nachweis ist ein signiertes JWT, kein Secret */
	private static final String CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

	/** so lange vor dem Ablauf gilt das Token schon als verbraucht, damit es nicht mitten im Aufruf abläuft */
	private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);

	private final EntraProperties properties;
	private final EntraClientCertificateService clientCertificateService;
	private final RestClient restClient;

	private String cachedAccessToken;
	private Instant cachedAccessTokenExpiresAt = Instant.EPOCH;

	public EntraGraphAccessTokenService(EntraProperties properties,
			EntraClientCertificateService clientCertificateService) {
		this.properties = properties;
		this.clientCertificateService = clientCertificateService;
		// Eigener Client statt der Boot-Bean (die liegt in Boot 4 in einem eigenen Modul). JDK-Client mit den Timeouts
		// aus den Properties: Ohne Frist hinge ein Login, wenn Microsoft nicht antwortet.
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build());
		requestFactory.setReadTimeout(properties.requestTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** Entra ist konfiguriert und das Zertifikat geladen: Tokens lassen sich holen. */
	public boolean isConfigured() {
		return clientCertificateService.isClientCertificateLoaded();
	}

	/** Das Token aus dem Speicher, oder ein neues, wenn keins da ist oder es bald abläuft. 503, wenn Entra nicht kann. */
	public synchronized String getGraphAccessToken() {
		if (cachedAccessToken == null || Instant.now().isAfter(cachedAccessTokenExpiresAt.minus(RENEW_BEFORE_EXPIRY))) {
			requestGraphAccessToken();
		}
		return cachedAccessToken;
	}

	/** POST an den Token-Endpunkt des Tenants, der Nachweis ist die frisch signierte Assertion. */
	private void requestGraphAccessToken() {
		if (!clientCertificateService.isClientCertificateLoaded()) {
			throw new EntraUnavailableException("der Zugang zu Microsoft Graph ist nicht eingerichtet");
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.clientId());
		form.add("scope", GRAPH_SCOPE);
		form.add("client_assertion_type", CLIENT_ASSERTION_TYPE);
		form.add("client_assertion", clientCertificateService.createClientAssertionJwt());

		Map<String, Object> tokenResponse;
		try {
			tokenResponse = restClient.post()
					.uri(properties.tokenUrl())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
		} catch (RestClientResponseException e) {
			log.error("Entra lehnt den Token-Abruf ab: Status {} Antwort {}", e.getStatusCode(),
					e.getResponseBodyAsString());
			throw new EntraUnavailableException("Microsoft Graph ist gerade nicht erreichbar", e);
		} catch (RestClientException e) {
			log.error("Token-Abruf bei Entra fehlgeschlagen: {}", e.getMessage());
			throw new EntraUnavailableException("Microsoft Graph ist gerade nicht erreichbar", e);
		}

		if (tokenResponse == null || !(tokenResponse.get("access_token") instanceof String accessToken)
				|| accessToken.isBlank() || !(tokenResponse.get("expires_in") instanceof Number expiresInSeconds)) {
			log.error("Token-Antwort von Entra ohne access_token oder expires_in");
			throw new EntraUnavailableException("Microsoft Graph ist gerade nicht erreichbar");
		}
		cachedAccessToken = accessToken;
		cachedAccessTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds.longValue());
		log.info("Graph-Token geholt, gültig bis {}", cachedAccessTokenExpiresAt);
	}

}
