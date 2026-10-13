package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasSternzeitQueueEntry;
import codergod1337.app1.abas.service.AbasSternzeitQueueService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Queue in der Übersicht des AbasManagers. Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und
 * @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/queue")
public class AbasSternzeitQueueAdminController {

	/** So viele Einträge zeigt die Übersicht (User 2026-10-10) */
	private static final int TOP_LIMIT = 50;

	private final AbasSternzeitQueueService abasSternzeitQueueService;

	public AbasSternzeitQueueAdminController(AbasSternzeitQueueService abasSternzeitQueueService) {
		this.abasSternzeitQueueService = abasSternzeitQueueService;
	}

	/** Die vordersten 50 in FIFO-Ordnung, was als Nächstes drankommt; die Übersicht fragt das im Takt */
	@GetMapping("/top")
	public List<AbasSternzeitQueueEntry> getAbasSternzeitQueueTop() {
		return abasSternzeitQueueService.getAbasSternzeitQueueTop(TOP_LIMIT);
	}

}
