package codergod1337.app1.file.filesubclass.controller;

import codergod1337.app1.file.filesubclass.model.FileSubClassDestination;
import codergod1337.app1.file.filesubclass.service.FileSubClassDestinationService;
import codergod1337.app1.system.HelperInputs;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin legt Anzeigeorte der Dateiarten an und pflegt sie (User 2026-10-13). Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/filesubclass/destination")
public class FileSubClassDestinationAdminController {

	private final FileSubClassDestinationService fileSubClassDestinationService;

	public FileSubClassDestinationAdminController(FileSubClassDestinationService fileSubClassDestinationService) {
		this.fileSubClassDestinationService = fileSubClassDestinationService;
	}

	/** Alle Felder. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FileSubClassDestination createFileSubClassDestination(
			@Valid @RequestBody FileSubClassDestination newFileSubClassDestinationData) {
		return fileSubClassDestinationService.createFileSubClassDestination(newFileSubClassDestinationData.getKey(),
				newFileSubClassDestinationData.getDisplayName(), newFileSubClassDestinationData.getDescription(),
				newFileSubClassDestinationData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welcher Ort geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public FileSubClassDestination updateFileSubClassDestination(
			@Valid @RequestBody FileSubClassDestination changedFileSubClassDestinationData) {
		return fileSubClassDestinationService.updateFileSubClassDestination(changedFileSubClassDestinationData.getKey(),
				changedFileSubClassDestinationData.getDisplayName(), changedFileSubClassDestinationData.getDescription(),
				changedFileSubClassDestinationData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"ARTIKELMAPPE_DOKUMENTE": 1,
	 * "KUNDENMAPPE": 2}. Das Frontend zählt durch. Zurück kommen alle Orte in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<FileSubClassDestination> changeFileSubClassDestinationPositions(
			@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			if (!HelperInputs.isValidKey(newPosition.getKey())
					|| fileSubClassDestinationService.isFileSubClassDestinationKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Anzeigeort mit key " + newPosition.getKey());
			}
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		return fileSubClassDestinationService.changeFileSubClassDestinationPositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit dem gelöschten Ort, 400 bei ungültigem key, 404 wenn es ihn nicht gibt, 409 wenn er genutzt wird. */
	@DeleteMapping
	public FileSubClassDestination deleteFileSubClassDestination(@RequestBody Map<String, Object> destinationToDelete) {
		String key = destinationToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return fileSubClassDestinationService.deleteFileSubClassDestination(key);
	}

}
