package codergod1337.app1.abas.service;

import codergod1337.app1.abas.AbasErpUnavailableException;
import codergod1337.app1.abas.model.AbasProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Der eine Weg zu ABAS: Jeder Zugriff auf das ERP läuft hier durch. Nur der HTTP-Kern: Adresse, Anmeldung, Zertifikat,
 * Fristen, die Antwort einmal parsen und die Deutung der Fehler, sonst nichts. Welche Anfrage gestellt wird, wissen die
 * zwei Services daneben: {@link AbasSternzeitResponseFetchService} für ein Objekt per Sternzeit (GET) und
 * {@link AbasInfosysResponseFetchService} für ein Infosystem (POST mit den Eingaben im Body). Deshalb sind buildUri und
 * exchange package-private: Kein anderes Paket spricht mit ABAS. Keine Rechteprüfung hier: Wer fragen darf, haben
 * Controller und Services vorher entschieden.
 *
 * Die Antwort kommt immer als geparster {@link JsonNode}, 1 bis 3000 KB, nie als Text: Die Verarbeitung findet im
 * Backend statt und navigiert im Baum, ein Text müsste jeder Abnehmer erneut parsen.
 *
 * Fehler nach außen: 404, wenn ABAS die Kennung nicht kennt; 400, wenn ABAS die Eingabe ablehnt, mit dem Klartext aus
 * seiner Antwort; sonst 503 ({@link AbasErpUnavailableException}), auch bei abgelehntem Zugang, denn das liegt an
 * unseren Zugangsdaten, nicht am Aufrufer.
 */
@Service
public class AbasErpRestClientService {

	private static final Logger log = LoggerFactory.getLogger(AbasErpRestClientService.class);

	/** So lang darf der Grund in einer Meldung nach außen höchstens werden */
	private static final int SHORT_REASON_MAX_LENGTH = 200;

	/**
	 * Die Antwortform (User 2026-10-10): Feldsammlungen als Map nach Feldname statt als Liste, also
	 * {@code head.fields.nummer.value} statt {@code head.fields[3].value}; value, text und links bleiben. So ist der
	 * Pfad im JSON-Baum zugleich die Adresse einer Mapping-Vorschrift, immer über Namen, nie über Positionen. Die
	 * dritte Form {@code application/abas.objects.simple+json} fiele flacher aus, verlöre aber {@code text}.
	 */
	private static final MediaType ABAS_OBJECTS_JSON = MediaType.valueOf("application/abas.objects+json");

	private final AbasProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public AbasErpRestClientService(AbasProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.builder().requestFactory(buildRequestFactory()).build();
	}

	/** host und mandant stehen in der Konfiguration: Anfragen lassen sich bauen. */
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	/**
	 * Die Basis jeder Anfrage, {@code https://<host>:<port>/<basePath>/r/<mandant>}, damit das Frontend die Anfrage
	 * vor dem Absenden als URL zeigen kann (User 2026-10-10); null, wenn ABAS nicht eingerichtet ist.
	 */
	public String getAbasErpBaseUrl() {
		return properties.isConfigured() ? buildUri().build().toUriString() : null;
	}

	// ===== Der Kern: eine Stelle für Adresse, Anmeldung, Fehler =====

	/**
	 * Eine Anfrage an ABAS mit Basic Auth, die Antwort einmal geparst. GET ohne Body, POST mit JSON-Body, dann geht
	 * Content-Type application/json mit. Jeder Fehler wird hier gedeutet und übersetzt. Package-private: Nur die zwei
	 * Fetch-Services rufen das.
	 */
	JsonNode exchange(HttpMethod method, URI uri, String jsonBody, String operation) {
		if (!properties.isConfigured()) {
			throw new AbasErpUnavailableException("ABAS ist nicht eingerichtet: abas_host und abas_mandant fehlen");
		}
		log.info("ABAS {}: {} {}", operation, method, uri);
		String body;
		try {
			RestClient.RequestBodySpec requestSpec = restClient.method(method)
					.uri(uri)
					.accept(ABAS_OBJECTS_JSON)
					.header("Accept-Language", "de")
					.headers(headers -> headers.setBasicAuth(properties.user(), properties.pw() == null ? "" : properties.pw()));
			if (jsonBody != null) {
				requestSpec = requestSpec.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
			}
			body = requestSpec.retrieve().body(String.class);
		} catch (RestClientResponseException e) {
			throw translateResponseError(e, uri, operation);
		} catch (RestClientException e) {
			// das Netz, nicht ABAS: Frist abgelaufen, abgelehnt, Host unbekannt
			log.warn("ABAS nicht erreichbar, {} ({}:{}): {}", operation, uri.getHost(), uri.getPort(),
					e.getMostSpecificCause().getMessage());
			throw new AbasErpUnavailableException("ABAS ist gerade nicht erreichbar", e);
		}
		if (body == null || body.isBlank()) {
			log.error("ABAS antwortet leer, {} URL={}", operation, uri);
			throw new AbasErpUnavailableException("ABAS hat leer geantwortet");
		}
		try {
			return objectMapper.readTree(body);
		} catch (RuntimeException e) {
			log.error("ABAS antwortet ohne lesbares JSON, {} URL={}: {}", operation, uri, e.getMessage());
			throw new AbasErpUnavailableException("ABAS hat kein lesbares JSON geliefert", e);
		}
	}

	/** Ein HTTP-Fehler von ABAS: 404 und 400 gehören dem Aufrufer, alles andere ist 503. */
	private ResponseStatusException translateResponseError(RestClientResponseException e, URI uri, String operation) {
		HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
		if (status == HttpStatus.NOT_FOUND) {
			return new ResponseStatusException(HttpStatus.NOT_FOUND, "ABAS kennt das nicht: " + operation);
		}
		if (status == HttpStatus.BAD_REQUEST) {
			// ABAS lehnt die Eingabe ab, z. B. eine Nummer, die das Feld nicht fasst: keine Störung, eine Zeile im Log
			log.warn("ABAS lehnt die Eingabe ab, {} URL={} Antwort={}", operation, uri, e.getResponseBodyAsString());
			return new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"ABAS lehnt die Eingabe ab: " + shortReason(e.getResponseBodyAsString(), status));
		}
		if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
			// kein Fehler des Aufrufers, sondern unserer Zugangsdaten: laut ins Log, samt Antwort des Servers
			log.error("ABAS lehnt unseren Zugang ab: Status {} URL={} Antwort={}", e.getStatusCode(), uri,
					e.getResponseBodyAsString());
			return new AbasErpUnavailableException("ABAS lehnt unseren Zugang ab (Status " + e.getStatusCode() + ")", e);
		}
		log.error("ABAS-Zugriff fehlgeschlagen, {} URL={} Status={} Antwort={}", operation, uri, e.getStatusCode(),
				e.getResponseBodyAsString());
		return new AbasErpUnavailableException("ABAS antwortet mit Status " + e.getStatusCode().value(), e);
	}

	/**
	 * Der Grund eines Fehlschlags in einem kurzen Satz. Antwortet ABAS mit einem Fehler-JSON, steht der Klartext in
	 * {@code content.error.text[0]}, genau der wird genommen; sonst der Status.
	 */
	private String shortReason(String responseBody, HttpStatus status) {
		String text = null;
		try {
			JsonNode node = objectMapper.readTree(responseBody).path("content").path("error").path("text");
			if (node.isArray() && !node.isEmpty()) {
				text = node.get(0).asString();
			}
		} catch (RuntimeException notJson) {
			// dann bleibt es beim Status
		}
		if (text == null || text.isBlank()) {
			return "Status " + status.value();
		}
		String oneLine = text.replaceAll("\\s+", " ").trim();
		return oneLine.length() <= SHORT_REASON_MAX_LENGTH ? oneLine
				: oneLine.substring(0, SHORT_REASON_MAX_LENGTH) + " …";
	}

	/** {@code https://<host>:<port>/<basePath>/r/<mandant>/<segmente…>}; die Fetch-Services hängen Query-Parameter an */
	UriComponentsBuilder buildUri(String... pathSegments) {
		UriComponentsBuilder builder = UriComponentsBuilder.newInstance().scheme("https").host(properties.host())
				.port(properties.port()).pathSegment(properties.basePath(), "r", properties.mandant());
		for (String segment : pathSegments) {
			builder.pathSegment(segment);
		}
		return builder;
	}

	// ===== Verbindung: Fristen und das Zertifikat =====

	/**
	 * Die Verbindung über HttpURLConnection mit den Fristen aus den Properties. Mit certPath wird nur genau dem
	 * hinterlegten Zertifikat vertraut, der Hostname spielt dann keine Rolle: Das Zertifikat ist die Identität des
	 * Servers, wie der Host-Key beim SFTP. Ohne certPath gilt der Truststore des JDK.
	 */
	private SimpleClientHttpRequestFactory buildRequestFactory() {
		SimpleClientHttpRequestFactory factory;
		if (properties.usesPinnedCertificate()) {
			SSLContext pinnedContext = buildPinnedSslContext(Path.of(properties.certPath()));
			factory = new SimpleClientHttpRequestFactory() {
				@Override
				protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
					if (connection instanceof HttpsURLConnection https) {
						https.setSSLSocketFactory(pinnedContext.getSocketFactory());
						https.setHostnameVerifier((hostname, session) -> true);
					}
					super.prepareConnection(connection, httpMethod);
				}
			};
		} else {
			factory = new SimpleClientHttpRequestFactory();
		}
		factory.setConnectTimeout(properties.connectTimeout());
		factory.setReadTimeout(properties.requestTimeout());
		return factory;
	}

	/** Ein SSLContext, dessen Truststore genau das eine Zertifikat aus der PEM-Datei enthält. */
	private static SSLContext buildPinnedSslContext(Path certPath) {
		try (InputStream certStream = Files.newInputStream(certPath)) {
			Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(certStream);
			KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			trustStore.load(null, null);
			trustStore.setCertificateEntry("abas", certificate);
			TrustManagerFactory trustManagerFactory = TrustManagerFactory
					.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
			log.info("ABAS-Zertifikat aus {} geladen, nur ihm wird vertraut", certPath);
			return sslContext;
		} catch (Exception e) {
			// ohne das Zertifikat kein Zugriff: Das Backend startet trotzdem, jede Anfrage an ABAS wird 503
			throw new IllegalStateException("ABAS-Zertifikat aus " + certPath + " nicht ladbar: " + e.getMessage(), e);
		}
	}

}
