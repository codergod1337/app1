package codergod1337.app1.graph.entra.service;

import codergod1337.app1.graph.entra.model.EntraClientCertificate;
import codergod1337.app1.graph.entra.model.EntraProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lädt beim Start Schlüssel und Zertifikat des Backends (PEM-Dateien aus der .env) und baut daraus die
 * Client-Assertion für den Token-Endpunkt: ein JWT nach der Microsoft-Doku, signiert mit PS256, im Kopf der
 * SHA-256-Fingerabdruck des Zertifikats. Die Assertion gilt fünf Minuten und wird für jeden Token-Abruf neu gebaut.
 *
 * Ohne Konfiguration (app1.graph.entra unvollständig) lädt der Service nichts, das Backend startet trotzdem. Sind die
 * Werte da, aber eine Datei fehlt oder hat das falsche Format, startet das Backend nicht: Das ist ein Fehler in der
 * Einrichtung, der sofort auffallen soll, nicht erst beim ersten Login.
 */
@Service
public class EntraClientCertificateService {

	private static final Logger log = LoggerFactory.getLogger(EntraClientCertificateService.class);

	/** so lange gilt eine Assertion, die Microsoft-Doku empfiehlt höchstens 5 bis 10 Minuten */
	private static final Duration ASSERTION_VALIDITY = Duration.ofMinutes(5);

	/** ab hier warnt das Log bei jedem Start, dass das Zertifikat erneuert werden muss */
	private static final Duration EXPIRY_WARNING_PERIOD = Duration.ofDays(30);

	private static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
	private static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

	private final EntraProperties properties;

	/** null, solange app1.graph.entra nicht konfiguriert ist */
	private final EntraClientCertificate clientCertificate;

	public EntraClientCertificateService(EntraProperties properties) {
		this.properties = properties;
		if (properties.isConfigured()) {
			this.clientCertificate = loadClientCertificate();
		} else {
			this.clientCertificate = null;
			log.warn("Entra ist nicht konfiguriert (app1.graph.entra unvollständig): kein Zugriff auf Graph");
		}
	}

	/** Schlüssel und Zertifikat sind geladen, eine Assertion lässt sich bauen. */
	public boolean isClientCertificateLoaded() {
		return clientCertificate != null;
	}

	/**
	 * Eine frische Assertion: aud ist der Token-Endpunkt des Tenants, iss und sub die Client-ID, jti zufällig, gültig
	 * ab jetzt für fünf Minuten. Signiert mit PS256 (RSASSA-PSS), wie Microsoft es vorgibt.
	 */
	public String createClientAssertionJwt() {
		if (clientCertificate == null) {
			throw new IllegalStateException("Entra ist nicht konfiguriert");
		}
		Instant now = Instant.now();
		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.PS256)
				.type(JOSEObjectType.JWT)
				.x509CertSHA256Thumbprint(new Base64URL(clientCertificate.certificateSha256ThumbprintBase64Url()))
				.build();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.audience(properties.tokenUrl())
				.issuer(properties.clientId())
				.subject(properties.clientId())
				.jwtID(UUID.randomUUID().toString())
				.notBeforeTime(Date.from(now))
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(ASSERTION_VALIDITY)))
				.build();
		SignedJWT signedJwt = new SignedJWT(header, claims);
		try {
			signedJwt.sign(new RSASSASigner(clientCertificate.privateKey()));
		} catch (JOSEException e) {
			throw new IllegalStateException("die Assertion lässt sich nicht signieren", e);
		}
		return signedJwt.serialize();
	}

	private EntraClientCertificate loadClientCertificate() {
		RSAPrivateKey privateKey = readPrivateKey(Path.of(properties.privateKeyPath()));
		X509Certificate certificate = readCertificate(Path.of(properties.certificatePath()));
		String thumbprint;
		try {
			thumbprint = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Entra: Fingerabdruck des Zertifikats nicht berechenbar", e);
		}
		Instant expiresAt = certificate.getNotAfter().toInstant();
		Duration remaining = Duration.between(Instant.now(), expiresAt);
		if (remaining.isNegative()) {
			log.error("Das Entra-Zertifikat ist am {} abgelaufen, Entra lehnt jeden Token-Abruf ab: erneuern, "
					+ "siehe secrets/entra/ANLEITUNG.md", expiresAt);
		} else if (remaining.compareTo(EXPIRY_WARNING_PERIOD) < 0) {
			log.warn("Das Entra-Zertifikat läuft am {} ab, in {} Tagen: rechtzeitig erneuern, "
					+ "siehe secrets/entra/ANLEITUNG.md", expiresAt, remaining.toDays());
		} else {
			log.info("Entra-Zertifikat geladen, gültig bis {}", expiresAt);
		}
		return new EntraClientCertificate(privateKey, thumbprint, expiresAt);
	}

	/**
	 * PEM mit „BEGIN PRIVATE KEY“ (PKCS#8), so schreibt es openssl 3. „BEGIN RSA PRIVATE KEY“ (PKCS#1) ist ein anderes
	 * Format, das das JDK nicht liest, und wird mit klarer Meldung abgelehnt.
	 */
	private static RSAPrivateKey readPrivateKey(Path path) {
		String pem;
		try {
			pem = Files.readString(path, StandardCharsets.US_ASCII);
		} catch (IOException e) {
			throw new IllegalStateException("Entra: privater Schlüssel nicht lesbar: " + path, e);
		}
		if (!pem.contains(PEM_PRIVATE_KEY_BEGIN)) {
			throw new IllegalStateException("Entra: der private Schlüssel muss PKCS#8 sein (BEGIN PRIVATE KEY): " + path);
		}
		String base64 = pem.replace(PEM_PRIVATE_KEY_BEGIN, "").replace(PEM_PRIVATE_KEY_END, "").replaceAll("\\s", "");
		try {
			return (RSAPrivateKey) KeyFactory.getInstance("RSA")
					.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
		} catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
			throw new IllegalStateException("Entra: privater Schlüssel ungültig oder kein RSA-Schlüssel: " + path, e);
		}
	}

	private static X509Certificate readCertificate(Path path) {
		try (InputStream in = Files.newInputStream(path)) {
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
		} catch (IOException | GeneralSecurityException | ClassCastException e) {
			throw new IllegalStateException("Entra: Zertifikat nicht lesbar: " + path, e);
		}
	}

}
