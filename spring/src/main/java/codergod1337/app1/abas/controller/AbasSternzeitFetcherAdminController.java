package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasSternzeitFetcherStatus;
import codergod1337.app1.abas.service.AbasSternzeitFetchWorkerPool;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Queue-Fetcher in der Übersicht: sein Zustand für die große Badge und der Schalter. Keine Admin-Prüfung hier: Der
 * Pfad /api/rest/v1/admin/** und @PreAuthorize im Pool sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/fetcher")
public class AbasSternzeitFetcherAdminController {

	private final AbasSternzeitFetchWorkerPool abasSternzeitFetchWorkerPool;

	public AbasSternzeitFetcherAdminController(AbasSternzeitFetchWorkerPool abasSternzeitFetchWorkerPool) {
		this.abasSternzeitFetchWorkerPool = abasSternzeitFetchWorkerPool;
	}

	/** Zustand, Worker, Queue und die Zähler seit dem Start; die Übersicht fragt das im Takt */
	@GetMapping
	public AbasSternzeitFetcherStatus getAbasSternzeitFetcherStatus() {
		return abasSternzeitFetchWorkerPool.getAbasSternzeitFetcherStatus();
	}

	/** Der Schalter. Body {"running": true} schaltet ein, false pausiert. Zurück kommt der neue Zustand. */
	@PostMapping
	public AbasSternzeitFetcherStatus switchAbasSternzeitFetcher(@RequestBody Map<String, Object> switchRequest) {
		if (!(switchRequest.get("running") instanceof Boolean running)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "running fehlt oder ist kein Wahrheitswert");
		}
		if (running) {
			abasSternzeitFetchWorkerPool.resume();
		} else {
			abasSternzeitFetchWorkerPool.pause();
		}
		return abasSternzeitFetchWorkerPool.getAbasSternzeitFetcherStatus();
	}

}
