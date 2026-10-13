package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasSternzeitMappingPreview;
import codergod1337.app1.abas.service.AbasSternzeitMappingService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Eine Sternzeit über ihr Mapping in den Kern bringen, zum Ansehen oder zum Schreiben. Keine Admin-Prüfung hier: Der
 * Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/sternzeit/mapping")
public class AbasSternzeitMappingAdminController {

	private final AbasSternzeitMappingService abasSternzeitMappingService;

	public AbasSternzeitMappingAdminController(AbasSternzeitMappingService abasSternzeitMappingService) {
		this.abasSternzeitMappingService = abasSternzeitMappingService;
	}

	/** Body {"sternzeit": "(610762,2,0)"}: holen, mappen, das Dokument zeigen, nichts schreiben. 404 ohne passendes Mapping. */
	@PostMapping("/preview")
	public AbasSternzeitMappingPreview previewAbasSternzeitMapping(@RequestBody Map<String, Object> previewRequest) {
		return abasSternzeitMappingService.previewAbasSternzeitMapping(sternzeitOf(previewRequest));
	}

	/** Dasselbe, und das Dokument wird in den Kern geschrieben */
	@PostMapping("/write")
	public AbasSternzeitMappingPreview writeAbasSternzeitMapping(@RequestBody Map<String, Object> writeRequest) {
		return abasSternzeitMappingService.writeAbasSternzeitMapping(sternzeitOf(writeRequest));
	}

	private static String sternzeitOf(Map<String, Object> body) {
		if (body == null || !(body.get("sternzeit") instanceof String sternzeit) || sternzeit.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sternzeit fehlt");
		}
		return sternzeit.trim();
	}

}
