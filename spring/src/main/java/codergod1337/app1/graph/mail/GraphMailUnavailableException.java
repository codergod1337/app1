package codergod1337.app1.graph.mail;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Eine Mail konnte nicht verschickt werden: Graph lehnt ab, antwortet nicht, oder die Vorlage fehlt. Das liegt an uns
 * oder an Microsoft, nicht am Aufrufer: 503. Die Meldung nach außen bleibt allgemein, Details stehen nur im Log.
 */
public class GraphMailUnavailableException extends ResponseStatusException {

	public GraphMailUnavailableException(String reason) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason);
	}

	public GraphMailUnavailableException(String reason, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
	}

}
