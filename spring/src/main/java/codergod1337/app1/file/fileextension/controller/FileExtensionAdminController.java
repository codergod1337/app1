package codergod1337.app1.file.fileextension.controller;

import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.service.FileExtensionService;
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
 * Admin pflegt den Endungskatalog. Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/fileextension")
public class FileExtensionAdminController {

	private final FileExtensionService fileExtensionService;

	public FileExtensionAdminController(FileExtensionService fileExtensionService) {
		this.fileExtensionService = fileExtensionService;
	}

	/** Alle Felder. Die Endung prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FileExtension createFileExtension(@Valid @RequestBody FileExtension newFileExtensionData) {
		return fileExtensionService.createFileExtension(newFileExtensionData.getExtension(),
				newFileExtensionData.getDescription(), newFileExtensionData.getFileExtensionCollectionKey(),
				newFileExtensionData.isDangerous(), newFileExtensionData.getListingPosition(), newFileExtensionData.isOcr(),
				newFileExtensionData.isKi(), newFileExtensionData.isImagePreview());
	}

	/** Alle Felder. Die Endung im Body bestimmt, welche geändert wird, sie selbst ändert sich nie. */
	@PutMapping
	public FileExtension updateFileExtension(@Valid @RequestBody FileExtension changedFileExtensionData) {
		return fileExtensionService.updateFileExtension(changedFileExtensionData.getExtension(),
				changedFileExtensionData.getDescription(), changedFileExtensionData.getFileExtensionCollectionKey(),
				changedFileExtensionData.isDangerous(), changedFileExtensionData.getListingPosition(),
				changedFileExtensionData.isOcr(), changedFileExtensionData.isKi(), changedFileExtensionData.isImagePreview());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jede Endung bekommt ihre Position, {"pdf": 1, "docx": 2}.
	 * Das Frontend zählt durch. Zurück kommen alle Endungen in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<FileExtension> changeFileExtensionPositions(@RequestBody Map<String, Object> newPositionByExtension) {
		Map<String, Integer> checkedPositionByExtension = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByExtension.entrySet()) {
			// 1. gibt es die Endung?
			if (fileExtensionService.isFileExtensionAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Endung " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByExtension.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return fileExtensionService.changeFileExtensionPositions(checkedPositionByExtension);
	}

	/** Nur per Endung. 200 mit der gelöschten Endung, 400 ohne Endung, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public FileExtension deleteFileExtension(@RequestBody Map<String, Object> fileExtensionToDelete) {
		String extension = fileExtensionToDelete.get("extension") instanceof String extensionValue ? extensionValue : null;
		if (extension == null || extension.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "extension fehlt");
		}
		return fileExtensionService.deleteFileExtension(extension);
	}

}
