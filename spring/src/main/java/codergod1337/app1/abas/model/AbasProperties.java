package codergod1337.app1.abas.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zugang zum ABAS-ERP und die Grenzen des Worker-Pools (application.yml, app1.abas). Alles, was den Server betrifft,
 * kommt aus der .env; der Server steht nur im LAN oder VPN. Die Adresse entsteht aus drei Teilen:
 * {@code https://<host>:<port>/<basePath>/r/<mandant>/…}, so lassen sich Server, Port und Mandant einzeln umstellen.
 *
 * @param host           Adresse des ABAS-Servers
 * @param port           Port der Middleware, meist 4443
 * @param mandant        der Mandant hinter /r/
 * @param basePath       der Pfad zwischen Port und /r/, bei ABAS die Middleware "mw"
 * @param user           mit diesem Benutzer fragen wir bei ABAS an (Basic Auth)
 * @param pw             sein Passwort
 * @param certPath       Pfad zum Zertifikat des ABAS-Servers als PEM. Gesetzt: Nur genau dieses Zertifikat wird
 *                       akzeptiert, der Hostname spielt keine Rolle, so wie der Host-Key beim SFTP. Leer: der
 *                       Truststore des JDK, dann muss das Zertifikat von einer bekannten Stelle stammen.
 * @param connectTimeout Verbindungsaufbau, im LAN Millisekunden
 * @param requestTimeout so lange darf eine einzelne Antwort ausbleiben, ein großes Objekt braucht Sekunden
 * @param fetch          der Worker-Pool, siehe {@link Fetch}
 */
@ConfigurationProperties(prefix = "app1.abas")
public record AbasProperties(String host, int port, String mandant, String basePath, String user, String pw,
		String certPath, Duration connectTimeout, Duration requestTimeout, Fetch fetch) {

	/**
	 * Die Grenzen des Worker-Pools (AbasSternzeitFetchWorkerPool) und des Watchers.
	 *
	 * @param parallel      so viele Sternzeiten holt der Pool gleichzeitig bei ABAS, 1 bis 100
	 * @param watchInterval der Takt des Watchers: alte Claims freigeben, den Pool wecken, wenn etwas anliegt
	 * @param claimTimeout  ein Claim, der so lange steht, gehört einem abgestürzten Worker und wird freigegeben
	 * @param maxAttempts   ab so vielen Fehlversuchen bleibt ein Eintrag mit seinem Fehler liegen und wird nicht mehr
	 *                      gezogen
	 * @param startPaused   true: nach dem Start erst auf Knopfdruck holen, z. B. vor einem Massenimport
	 */
	public record Fetch(int parallel, Duration watchInterval, Duration claimTimeout, int maxAttempts,
			boolean startPaused) {
	}

	/** host und mandant stehen: Anfragen lassen sich bauen. */
	public boolean isConfigured() {
		return host != null && !host.isBlank() && mandant != null && !mandant.isBlank();
	}

	/** Ein Zertifikat ist hinterlegt: Nur ihm wird vertraut. */
	public boolean usesPinnedCertificate() {
		return certPath != null && !certPath.isBlank();
	}

}
