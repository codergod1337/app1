package codergod1337.app1.graph.mail.model;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Der Mailversand über Graph (application.yml, app1.graph.mail), die Werte aus der .env.
 *
 * @param systemSenderAddress das Postfach im Tenant, in dessen Namen das System selbst verschickt, z. B. die 2FA-Mail.
 *                            Später kann eine Mail auch im Namen eines Users gehen, dann steht sein Postfach in der
 *                            Nachricht, nicht hier.
 * @param requestTimeout      so lange darf ein Aufruf von Graph dauern
 * @param domain              die Domäne der Postfächer im Tenant ohne @, z. B. pahling.de. Endet die E-Mail eines Users
 *                            darauf, darf er mit der Rolle des Archivierens auch aus seinem eigenen Postfach archivieren.
 *                            Leer: Kein eigenes Postfach zählt.
 */
@ConfigurationProperties(prefix = "app1.graph.mail")
public record GraphMailProperties(String systemSenderAddress, Duration requestTimeout, String domain) {
}
