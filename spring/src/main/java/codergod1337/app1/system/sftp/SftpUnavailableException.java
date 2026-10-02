package codergod1337.app1.system.sftp;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Dateispeicher ist nicht erreichbar, eine Übertragung ist gescheitert oder der Pool war zu lange voll. Das liegt
 * an uns, nicht am Aufrufer: 503 mit Retry-After, ein Aufrufer darf es gleich noch einmal versuchen. Die Meldung nach
 * außen bleibt allgemein, Pfade und Ursache stehen nur im Log.
 */
public class SftpUnavailableException extends ResponseStatusException {

	/** so lange soll ein Aufrufer warten, bevor er es noch einmal versucht */
	static final String RETRY_AFTER_SECONDS = "30";

	public SftpUnavailableException(String reason) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason);
	}

	public SftpUnavailableException(String reason, Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, reason, cause);
	}

	@Override
	public HttpHeaders getHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
		return headers;
	}

}
