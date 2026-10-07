package codergod1337.app1.graph.entra;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Entra ist nicht eingerichtet, nicht erreichbar oder lehnt den Token-Abruf ab. Das liegt an uns oder an Microsoft,
 * nicht am Aufrufer: 503. Die Meldung nach außen bleibt allgemein, Status und Antwort von Microsoft stehen nur im Log.
 */
public class EntraUnavailableException extends ResponseStatusException {

	public EntraUnavailableException(String reason) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason);
	}

	public EntraUnavailableException(String reason, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
	}

}
