package codergod1337.app1.system.sftp.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zugang und Grenzen des Dateispeichers (application.yml, app1.sftp). Alles, was den Server betrifft, kommt aus der
 * .env: Für den Wechsel auf ein NAS ändern sich nur host und hostKey.
 *
 * @param host                  Adresse des Servers
 * @param port                  Port, meist 22
 * @param user                  Anmeldename
 * @param pw                    Passwort, gilt nur ohne privateKeyPath
 * @param hostKey               öffentlicher Host-Key des Servers, Typ und Schlüssel ("ssh-ed25519 AAAA…"). Nur genau
 *                              dieser Server wird akzeptiert.
 * @param privateKeyPath        Pfad zum privaten Schlüssel des Backends. Gesetzt: Anmeldung mit Schlüssel statt
 *                              Passwort.
 * @param rootDir               Wurzel aller Dateien aus Sicht des SFTP-Users, alle Pfade des SftpService sind relativ
 *                              dazu
 * @param maxConnections        so viele Verbindungen dürfen insgesamt gleichzeitig ausgeliehen sein
 * @param maxConnectionsPerUser so viele davon ein einzelner angemeldeter User
 * @param minIdleConnections    so viele Verbindungen hält der Pool offen
 * @param connectTimeout        Verbindungsaufbau und Anmeldung
 * @param socketTimeout         so lange darf eine einzelne Antwort des Servers ausbleiben
 * @param borrowTimeout         so lange wartet ein Aufrufer auf einen freien Platz
 * @param idleTimeout           unbenutzte Verbindungen über minIdleConnections hinaus werden danach geschlossen
 * @param maxFilesPerDirectory  ab so vielen Dateien am Tag wird ein weiterer Tagesordner aufgemacht (SftpPathService)
 */
@ConfigurationProperties(prefix = "app1.sftp")
public record SftpProperties(String host, int port, String user, String pw, String hostKey, String privateKeyPath,
		String rootDir, int maxConnections, int maxConnectionsPerUser, int minIdleConnections, Duration connectTimeout,
		Duration socketTimeout, Duration borrowTimeout, Duration idleTimeout, int maxFilesPerDirectory) {

	/** Schlüssel statt Passwort, sobald ein Pfad gesetzt ist */
	public boolean usesPrivateKey() {
		return privateKeyPath != null && !privateKeyPath.isBlank();
	}

}
