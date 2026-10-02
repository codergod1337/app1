package codergod1337.app1.file.filesubclass.controller;

import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import codergod1337.app1.system.HelperInputs;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
 * Admin legt Dateiarten an und pflegt sie. Gelesen wird über die Stammdaten. Die freigeschalteten Endungen pflegt die
 * Freischalt-Matrix, nicht dieser Controller.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/filesubclass")
public class FileSubClassAdminController {

	private final FileSubClassService fileSubClassService;

	public FileSubClassAdminController(FileSubClassService fileSubClassService) {
		this.fileSubClassService = fileSubClassService;
	}

	/** Alle Felder außer extensions, die startet leer. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FileSubClass createFileSubClass(@Valid @RequestBody FileSubClass newFileSubClassData) {
		return fileSubClassService.createFileSubClass(newFileSubClassData.getKey(), newFileSubClassData.getDisplayName(),
				newFileSubClassData.getDescription(), newFileSubClassData.getListingPosition(),
				newFileSubClassData.getSymbol(), newFileSubClassData.getColor(),
				newFileSubClassData.getReadAccessRoleKeys(), newFileSubClassData.getWriteAccessRoleKeys());
	}

	/** Alle Felder außer extensions. Der key im Body bestimmt, welche Dateiart geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public FileSubClass updateFileSubClass(@Valid @RequestBody FileSubClass changedFileSubClassData) {
		return fileSubClassService.updateFileSubClass(changedFileSubClassData.getKey(),
				changedFileSubClassData.getDisplayName(), changedFileSubClassData.getDescription(),
				changedFileSubClassData.getListingPosition(), changedFileSubClassData.getSymbol(),
				changedFileSubClassData.getColor(), changedFileSubClassData.getReadAccessRoleKeys(),
				changedFileSubClassData.getWriteAccessRoleKeys());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"RECHNUNG": 1, "DATENBLATT": 2}.
	 * Das Frontend zählt durch. Zurück kommen alle Dateiarten in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<FileSubClass> changeFileSubClassPositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es die Dateiart zu diesem key?
			if (fileSubClassService.isFileSubClassKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Dateiart mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return fileSubClassService.changeFileSubClassPositions(checkedPositionByKey);
	}

	/**
	 * Lese- und Schreibrollen mehrerer Dateiarten auf einmal, für die FSC-ACL-Matrix:
	 * [{"key": "RECHNUNG", "readAccessRoleKeys": ["MITARBEITER"], "writeAccessRoleKeys": []}, …].
	 * Je Dateiart die vollständigen neuen Listen. 200 mit allen Dateiarten, 400 bei ungültiger Eingabe, 404 wenn es
	 * eine Dateiart nicht gibt.
	 */
	@PutMapping("/accessroles")
	public List<FileSubClass> changeFileSubClassAccessRoleKeys(
			@RequestBody List<Map<String, Object>> changedAccessRoleKeysData) {
		Map<String, List<String>> readAccessRoleKeysByKey = new LinkedHashMap<>();
		Map<String, List<String>> writeAccessRoleKeysByKey = new LinkedHashMap<>();
		for (Map<String, Object> changedAccessRoleKeys : changedAccessRoleKeysData) {
			String key = changedAccessRoleKeys.get("key") instanceof String keyValue ? keyValue : null;
			if (!HelperInputs.isValidKey(key)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
			}
			if (!(changedAccessRoleKeys.get("readAccessRoleKeys") instanceof List<?> readValues)
					|| !(changedAccessRoleKeys.get("writeAccessRoleKeys") instanceof List<?> writeValues)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"readAccessRoleKeys oder writeAccessRoleKeys fehlt bei " + key);
			}
			List<String> readAccessRoleKeys = new ArrayList<>();
			for (Object readValue : readValues) {
				if (!(readValue instanceof String accessRoleKey) || !HelperInputs.isValidKey(accessRoleKey)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ungültiger AR-Key bei " + key);
				}
				readAccessRoleKeys.add(accessRoleKey);
			}
			List<String> writeAccessRoleKeys = new ArrayList<>();
			for (Object writeValue : writeValues) {
				if (!(writeValue instanceof String accessRoleKey) || !HelperInputs.isValidKey(accessRoleKey)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ungültiger AR-Key bei " + key);
				}
				writeAccessRoleKeys.add(accessRoleKey);
			}
			readAccessRoleKeysByKey.put(key, readAccessRoleKeys);
			writeAccessRoleKeysByKey.put(key, writeAccessRoleKeys);
		}
		return fileSubClassService.changeFileSubClassAccessRoleKeys(readAccessRoleKeysByKey, writeAccessRoleKeysByKey);
	}

	/**
	 * Die freigeschalteten Endungen mehrerer Dateiarten auf einmal, für die FileMatrix:
	 * [{"key": "RECHNUNG", "extensions": ["pdf"]}, …], je Dateiart die ganze neue Liste, eine leere schaltet alle ab.
	 * 200 mit allen Dateiarten, 400 bei ungültiger Eingabe oder einer Endung, die der Katalog nicht kennt, 404 wenn es
	 * eine Dateiart nicht gibt.
	 */
	@PutMapping("/extensions")
	public List<FileSubClass> changeFileSubClassExtensions(
			@RequestBody List<Map<String, Object>> changedExtensionsData) {
		Map<String, List<String>> extensionsByKey = new LinkedHashMap<>();
		for (Map<String, Object> changedExtensions : changedExtensionsData) {
			String key = changedExtensions.get("key") instanceof String keyValue ? keyValue : null;
			if (!HelperInputs.isValidKey(key)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
			}
			if (!(changedExtensions.get("extensions") instanceof List<?> extensionsValue)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "extensions fehlt bei " + key);
			}
			List<String> extensions = new ArrayList<>();
			for (Object extensionValue : extensionsValue) {
				if (!(extensionValue instanceof String extension) || extension.isBlank()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ungültige Endung bei " + key);
				}
				extensions.add(extension);
			}
			extensionsByKey.put(key, extensions);
		}
		return fileSubClassService.changeFileSubClassExtensions(extensionsByKey);
	}

	/** Nur per key. 200 mit der gelöschten Dateiart, 400 bei ungültigem key, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public FileSubClass deleteFileSubClass(@RequestBody Map<String, Object> fileSubClassToDelete) {
		String key = fileSubClassToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return fileSubClassService.deleteFileSubClass(key);
	}

}
