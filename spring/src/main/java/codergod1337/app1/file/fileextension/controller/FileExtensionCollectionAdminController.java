package codergod1337.app1.file.fileextension.controller;

import codergod1337.app1.file.fileextension.model.FileExtensionCollection;
import codergod1337.app1.file.fileextension.service.FileExtensionCollectionService;
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
 * Admin pflegt die Gruppen der Endungen. Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/fileextensioncollection")
public class FileExtensionCollectionAdminController {

	private final FileExtensionCollectionService fileExtensionCollectionService;

	public FileExtensionCollectionAdminController(FileExtensionCollectionService fileExtensionCollectionService) {
		this.fileExtensionCollectionService = fileExtensionCollectionService;
	}

	/** Alle Felder. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FileExtensionCollection createFileExtensionCollection(
			@Valid @RequestBody FileExtensionCollection newFileExtensionCollectionData) {
		return fileExtensionCollectionService.createFileExtensionCollection(newFileExtensionCollectionData.getKey(),
				newFileExtensionCollectionData.getDisplayName(), newFileExtensionCollectionData.getSymbolShape(),
				newFileExtensionCollectionData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welche Gruppe geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public FileExtensionCollection updateFileExtensionCollection(
			@Valid @RequestBody FileExtensionCollection changedFileExtensionCollectionData) {
		return fileExtensionCollectionService.updateFileExtensionCollection(changedFileExtensionCollectionData.getKey(),
				changedFileExtensionCollectionData.getDisplayName(), changedFileExtensionCollectionData.getSymbolShape(),
				changedFileExtensionCollectionData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"BILDER": 1, "CAD": 2}. Das
	 * Frontend zählt durch. Zurück kommen alle Gruppen in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<FileExtensionCollection> changeFileExtensionCollectionPositions(
			@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es die Gruppe zu diesem key?
			if (fileExtensionCollectionService.isFileExtensionCollectionKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Gruppe mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return fileExtensionCollectionService.changeFileExtensionCollectionPositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit der gelöschten Gruppe, 400 bei ungültigem key, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public FileExtensionCollection deleteFileExtensionCollection(
			@RequestBody Map<String, Object> fileExtensionCollectionToDelete) {
		String key = fileExtensionCollectionToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return fileExtensionCollectionService.deleteFileExtensionCollection(key);
	}

}
