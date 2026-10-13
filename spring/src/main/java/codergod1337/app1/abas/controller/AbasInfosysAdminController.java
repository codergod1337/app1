package codergod1337.app1.abas.controller;

import codergod1337.app1.abas.model.AbasInfosys;
import codergod1337.app1.abas.model.AbasInfosysMetaRequest;
import codergod1337.app1.abas.model.AbasInfosysRunRequest;
import codergod1337.app1.abas.service.AbasInfosysFetchService;
import codergod1337.app1.abas.service.AbasInfosysService;
import codergod1337.app1.system.HelperInputs;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Infosysteme bei ABAS: die hinterlegten Aufrufe (Gruppe, Name, Body-Vorlage, Anzeigeorte) anlegen und pflegen, dazu
 * für das Formular die Liste aller Infosysteme bei ABAS und die Felder eines Infosystems, und ein Lauf eines
 * hinterlegten Aufrufs mit roher Antwort zum Hineinschauen. Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/**
 * und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/abas/infosys")
public class AbasInfosysAdminController {

	private final AbasInfosysFetchService abasInfosysFetchService;
	private final AbasInfosysService abasInfosysService;

	public AbasInfosysAdminController(AbasInfosysFetchService abasInfosysFetchService,
			AbasInfosysService abasInfosysService) {
		this.abasInfosysFetchService = abasInfosysFetchService;
		this.abasInfosysService = abasInfosysService;
	}

	// ===== Die hinterlegten Aufrufe =====

	/** Alle hinterlegten Aufrufe, nach Position und Key */
	@GetMapping
	public List<AbasInfosys> getAllAbasInfosys() {
		return abasInfosysService.getAllAbasInfosys();
	}

	/** Alle Felder. Den key, die Vorlage und die Anzeigeorte prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AbasInfosys createAbasInfosys(@Valid @RequestBody AbasInfosys newAbasInfosysData) {
		return abasInfosysService.createAbasInfosys(newAbasInfosysData.getKey(), newAbasInfosysData.getDisplayName(),
				newAbasInfosysData.getInfosysGroup(), newAbasInfosysData.getInfosysName(),
				newAbasInfosysData.getRequestBodyTemplate(), newAbasInfosysData.getListingPosition(),
				newAbasInfosysData.getDisplayContextKeys());
	}

	/** Alle Felder. Der key im Body bestimmt, welcher Aufruf geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public AbasInfosys updateAbasInfosys(@Valid @RequestBody AbasInfosys changedAbasInfosysData) {
		return abasInfosysService.updateAbasInfosys(changedAbasInfosysData.getKey(),
				changedAbasInfosysData.getDisplayName(), changedAbasInfosysData.getInfosysGroup(),
				changedAbasInfosysData.getInfosysName(), changedAbasInfosysData.getRequestBodyTemplate(),
				changedAbasInfosysData.getListingPosition(), changedAbasInfosysData.getDisplayContextKeys());
	}

	/** Nur per key. 200 mit dem gelöschten Aufruf, 400 bei ungültigem key, 404 wenn es ihn nicht gibt. */
	@DeleteMapping
	public AbasInfosys deleteAbasInfosys(@RequestBody Map<String, Object> abasInfosysToDelete) {
		String key = abasInfosysToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return abasInfosysService.deleteAbasInfosys(key);
	}

	// ===== Bei ABAS nachsehen und ausführen =====

	/** Alle Infosysteme bei ABAS, roh: content.data.links[], je Eintrag description „gruppe/NAME“ */
	@GetMapping("/list")
	public JsonNode fetchAbasInfosysList() {
		return abasInfosysFetchService.fetchAbasInfosysList();
	}

	/** Die Felder eines Infosystems, roh. Body {@link AbasInfosysMetaRequest}. 400 ohne Gruppe oder Name, 404 unbekannt. */
	@PostMapping("/meta")
	public JsonNode fetchAbasInfosysMeta(@RequestBody AbasInfosysMetaRequest metaRequest) {
		return abasInfosysFetchService.fetchAbasInfosysMeta(metaRequest.infosysGroup(), metaRequest.infosysName());
	}

	/**
	 * Einen hinterlegten Aufruf ausführen. Body {@link AbasInfosysRunRequest}: der Key des Aufrufs und die Werte seiner
	 * Platzhalter. 400 bei ungültigem Key, fehlendem Platzhalter oder wenn ABAS die Eingabe ablehnt, 404 ohne den
	 * Aufruf oder bei unbekanntem Infosystem, 503 wenn ABAS nicht antwortet.
	 */
	@PostMapping("/raw")
	public JsonNode fetchAbasInfosysRaw(@RequestBody AbasInfosysRunRequest runRequest) {
		if (!HelperInputs.isValidKey(runRequest.abasInfosysKey())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "abasInfosysKey fehlt oder ist ungültig");
		}
		return abasInfosysFetchService.fetchAbasResponseByAbasInfosys(
				abasInfosysService.getAbasInfosysByKey(runRequest.abasInfosysKey()), runRequest.valueByPlaceholderName());
	}

}
