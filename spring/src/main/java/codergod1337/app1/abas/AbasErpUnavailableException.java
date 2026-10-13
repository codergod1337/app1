package codergod1337.app1.abas;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * ABAS ist nicht eingerichtet, nicht erreichbar, antwortet nicht in der Frist oder lehnt unseren Zugang ab. Das liegt
 * an uns oder am ERP, nicht am Aufrufer: 503. Die Meldung nach außen bleibt kurz, URL und Antwort des Servers stehen
 * nur im Log.
 */
public class AbasErpUnavailableException extends ResponseStatusException {

	public AbasErpUnavailableException(String reason) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason);
	}

	public AbasErpUnavailableException(String reason, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
	}

}
