package codergod1337.app1.file.fileextension.service;

import codergod1337.app1.file.fileextension.model.FileExtensionCollection;
import codergod1337.app1.file.fileextension.model.SymbolShape;
import codergod1337.app1.file.fileextension.repository.FileExtensionCollectionRepository;
import codergod1337.app1.system.HelperInputs;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Die Gruppen der Endungen. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus dem Token. */
@Service
public class FileExtensionCollectionService {

	private final FileExtensionCollectionRepository fileExtensionCollectionRepository;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Beim Löschen einer Gruppe stehen ihre Endungen über diesen Service danach ohne Gruppe. Der
	 * FileExtensionService müsste umgekehrt hier fragen, ob es eine Gruppe gibt. Damit es keinen Kreis gibt, liest er
	 * stattdessen direkt das FileExtensionCollectionRepository (dort kommentiert).
	 */
	private final FileExtensionService fileExtensionService;

	public FileExtensionCollectionService(FileExtensionCollectionRepository fileExtensionCollectionRepository,
			FileExtensionService fileExtensionService) {
		this.fileExtensionCollectionRepository = fileExtensionCollectionRepository;
		this.fileExtensionService = fileExtensionService;
	}

	/** Neue Gruppe. 400 bei ungültigem key, 409 wenn es den key schon gibt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileExtensionCollection createFileExtensionCollection(String key, String displayName, SymbolShape symbolShape,
			int listingPosition) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde die bestehende Gruppe überschreiben
		if (fileExtensionCollectionRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		return fileExtensionCollectionRepository
				.save(new FileExtensionCollection(key, displayName, symbolShape, listingPosition));
	}

	/** Der key bestimmt nur, welche Gruppe geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileExtensionCollection updateFileExtensionCollection(String key, String displayName, SymbolShape symbolShape,
			int listingPosition) {
		FileExtensionCollection fileExtensionCollection = getFileExtensionCollectionByKey(key);
		fileExtensionCollection.setDisplayName(displayName);
		fileExtensionCollection.setSymbolShape(symbolShape);
		fileExtensionCollection.setListingPosition(listingPosition);
		return fileExtensionCollectionRepository.save(fileExtensionCollection);
	}

	/**
	 * Neue Positionen mehrerer Gruppen auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Zurück kommen alle Gruppen in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<FileExtensionCollection> changeFileExtensionCollectionPositions(Map<String, Integer> newPositionByKey) {
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			FileExtensionCollection fileExtensionCollection = getFileExtensionCollectionByKey(newPosition.getKey());
			fileExtensionCollection.setListingPosition(newPosition.getValue());
			fileExtensionCollectionRepository.save(fileExtensionCollection);
		}
		return getAllFileExtensionCollections();
	}

	/** Löscht die Gruppe, ihre Endungen stehen danach ohne Gruppe. Gibt sie zurück, wie sie vor dem Löschen war. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileExtensionCollection deleteFileExtensionCollection(String key) {
		FileExtensionCollection fileExtensionCollection = getFileExtensionCollectionByKey(key);
		fileExtensionService.removeFileExtensionCollectionFromAllFileExtensions(key);
		fileExtensionCollectionRepository.delete(fileExtensionCollection);
		return fileExtensionCollection;
	}

	/** Gibt es die Gruppe nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	@Transactional(readOnly = true)
	public FileExtensionCollection getFileExtensionCollectionByKey(String key) {
		return fileExtensionCollectionRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Gruppe mit key " + key));
	}

	/**
	 * Alle Gruppen, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne Login, ohne
	 * @PreAuthorize: Die Form braucht jede Anzeige einer Datei.
	 */
	@Transactional(readOnly = true)
	public List<FileExtensionCollection> getAllFileExtensionCollections() {
		return fileExtensionCollectionRepository.findAll(Sort.by("listingPosition", "key"));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isFileExtensionCollectionKeyAvailable(String key) {
		return !fileExtensionCollectionRepository.existsById(key);
	}

}
