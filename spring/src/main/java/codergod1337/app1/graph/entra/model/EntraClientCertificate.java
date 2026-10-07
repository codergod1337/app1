package codergod1337.app1.graph.entra.model;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;

/**
 * Das geladene Schlüsselmaterial des Backends: der private Schlüssel, mit dem die Assertion signiert wird, und das,
 * was das Backend vom Zertifikat braucht. Den SHA-256-Fingerabdruck trägt die Assertion im Kopf (x5t#S256), damit
 * Entra weiß, gegen welches hochgeladene Zertifikat es die Signatur prüft. Das Ablaufdatum dient der Warnung im Log.
 *
 * @param privateKey                            der private Schlüssel, verlässt nie den Server
 * @param certificateSha256ThumbprintBase64Url  SHA-256 über die DER-Bytes des Zertifikats, Base64URL ohne Füllzeichen
 * @param certificateExpiresAt                  Ablauf des Zertifikats (notAfter)
 */
public record EntraClientCertificate(RSAPrivateKey privateKey, String certificateSha256ThumbprintBase64Url,
		Instant certificateExpiresAt) {
}
