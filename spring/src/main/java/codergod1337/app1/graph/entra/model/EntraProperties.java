package codergod1337.app1.graph.entra.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Die Identität des Backends gegenüber Microsoft Entra (application.yml, app1.graph.entra), alle Werte aus der .env.
 * App-only mit Zertifikat: Der private Schlüssel bleibt auf dem Server, über die Leitung geht nur eine damit
 * signierte, fünf Minuten gültige Assertion. Kein Client-Secret. Nichts Fachliches: Was mit dem Token gemacht wird
 * (Mail, später Postfächer), entscheiden die anderen Unterpakete von graph.
 *
 * @param tenantId        die Verzeichnis-ID des Tenants
 * @param clientId        die Anwendungs-ID der App-Registrierung, in der das Zertifikat hochgeladen ist
 * @param privateKeyPath  Pfad zum privaten Schlüssel des Backends (PEM, PKCS#8, unverschlüsselt)
 * @param certificatePath Pfad zum Zertifikat (PEM, X.509), dasselbe, das in Entra hochgeladen ist
 * @param requestTimeout  so lange darf ein Aufruf des Token-Endpunkts dauern
 */
@ConfigurationProperties(prefix = "app1.graph.entra")
public record EntraProperties(String tenantId, String clientId, String privateKeyPath, String certificatePath,
		Duration requestTimeout) {

	/** Alles da, was der Token-Abruf braucht. Fehlt etwas, gibt es kein Token, das Backend startet trotzdem. */
	public boolean isConfigured() {
		return StringUtils.hasText(tenantId) && StringUtils.hasText(clientId) && StringUtils.hasText(privateKeyPath)
				&& StringUtils.hasText(certificatePath);
	}

	/** Der Token-Endpunkt des Tenants, zugleich die Audience der Assertion. */
	public String tokenUrl() {
		return "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
	}

}
