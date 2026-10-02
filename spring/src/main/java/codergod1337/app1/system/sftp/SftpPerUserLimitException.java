package codergod1337.app1.system.sftp;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der angemeldete User hat schon so viele Übertragungen offen, wie ihm zustehen, und die Wartezeit auf einen freien
 * Platz lief ab. Nichts ist kaputt, es liegt am Aufrufer: 429 mit Retry-After. Ohne diese Grenze könnte ein Einzelner
 * mit zwanzig Uploads den ganzen Pool belegen.
 */
public class SftpPerUserLimitException extends ResponseStatusException {

	public SftpPerUserLimitException(int maxConnectionsPerUser) {
		super(HttpStatus.TOO_MANY_REQUESTS, "höchstens " + maxConnectionsPerUser
				+ " Übertragungen gleichzeitig, bitte gleich noch einmal versuchen");
	}

	@Override
	public HttpHeaders getHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, SftpUnavailableException.RETRY_AFTER_SECONDS);
		return headers;
	}

}
