package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasIgnoredSternzeit;
import codergod1337.app1.abas.service.AbasIgnoredSternzeitService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Sperrliste des AbasManagers. Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im
 * Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/ignored")
public class AbasIgnoredSternzeitAdminController {

	private final AbasIgnoredSternzeitService abasIgnoredSternzeitService;

	public AbasIgnoredSternzeitAdminController(AbasIgnoredSternzeitService abasIgnoredSternzeitService) {
		this.abasIgnoredSternzeitService = abasIgnoredSternzeitService;
	}

	/**
	 * Eine Kennung sperren. Body {"sternzeit": "(87448,19,0)", "reason": "…"}, reason optional. 201 mit der Sperre,
	 * 400 bei falscher Form, 409 wenn schon gesperrt. Ein wartender Queue-Eintrag wird dabei entfernt.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AbasIgnoredSternzeit ignoreAbasSternzeit(@RequestBody Map<String, Object> ignoreRequest) {
		if (!(ignoreRequest.get("sternzeit") instanceof String sternzeit) || sternzeit.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sternzeit fehlt");
		}
		Object reason = ignoreRequest.get("reason");
		if (reason != null && !(reason instanceof String)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason ist kein Text");
		}
		return abasIgnoredSternzeitService.ignoreAbasSternzeit(sternzeit.trim(), (String) reason);
	}

}
