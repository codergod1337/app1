package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasBelegnummerLookupRequest;
import codergod1337.app1.abas.model.AbasBelegnummerLookupResult;
import codergod1337.app1.abas.service.AbasBelegnummerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Belegnummer nachschlagen (User 2026-10-11): jeder Angemeldete, die Tür ist der Pfad unter /api/rest/v1/**. Die
 * Nummer steht im Body, nicht in der URL. Hier nur die Form des Bodys.
 */
@RestController
@RequestMapping("/api/rest/v1/abas/belegnummer")
public class AbasBelegnummerController {

	private final AbasBelegnummerService abasBelegnummerService;

	public AbasBelegnummerController(AbasBelegnummerService abasBelegnummerService) {
		this.abasBelegnummerService = abasBelegnummerService;
	}

	/** Body {@link AbasBelegnummerLookupRequest}: {"belegnummer": "600012"}. 200 mit found true oder false, 400 ohne Nummer. */
	@PostMapping
	public AbasBelegnummerLookupResult lookupBelegnummer(@RequestBody AbasBelegnummerLookupRequest lookupRequest) {
		if (lookupRequest.belegnummer() == null || lookupRequest.belegnummer().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "belegnummer fehlt");
		}
		return abasBelegnummerService.lookupBelegnummer(lookupRequest.belegnummer());
	}

}
