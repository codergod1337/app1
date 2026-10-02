package codergod1337.app1.file.fileextension.service;

import codergod1337.app1.file.fileextension.model.FileExtension;
import codergod1337.app1.file.fileextension.repository.FileExtensionCollectionRepository;
import codergod1337.app1.file.fileextension.repository.FileExtensionRepository;
import codergod1337.app1.file.filesubclass.service.FileSubClassService;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Der Endungskatalog. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus dem Token. */
@Service
public class FileExtensionService {

	/** Klein, ohne Punkt, wie sie an echten Dateinamen verglichen wird, z. B. pdf */
	private static final Pattern VALID_EXTENSION = Pattern.compile("[a-z0-9]{1,20}");

	private final FileExtensionRepository fileExtensionRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diese Gruppe?“ über den FileExtensionCollectionService. Der
	 * braucht aber diesen Service, um beim Löschen einer Gruppe ihre Endungen auf „ohne Gruppe“ zu setzen. Deshalb hier
	 * ausnahmsweise direkt das fremde FileExtensionCollectionRepository, und zwar nur lesend (existsById).
	 */
	private final FileExtensionCollectionRepository fileExtensionCollectionRepository;

	/**
	 * DROHENDER ZIRKELSCHLUSS: Wird eine Endung gelöscht, nimmt dieser Service sie allen Dateiarten weg. Der
	 * FileSubClassService müsste umgekehrt hier fragen, ob es eine Endung gibt. Damit es keinen Kreis gibt, liest er
	 * stattdessen direkt das FileExtensionRepository (dort kommentiert).
	 */
	private final FileSubClassService fileSubClassService;

	public FileExtensionService(FileExtensionRepository fileExtensionRepository,
			FileExtensionCollectionRepository fileExtensionCollectionRepository, FileSubClassService fileSubClassService) {
		this.fileExtensionRepository = fileExtensionRepository;
		this.fileExtensionCollectionRepository = fileExtensionCollectionRepository;
		this.fileSubClassService = fileSubClassService;
	}

	/** Neue Endung. 400 bei ungültiger Endung oder unbekannter Gruppe, 409 wenn es sie schon gibt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileExtension createFileExtension(String extension, String description, String fileExtensionCollectionKey,
			boolean dangerous, int listingPosition, boolean ocr, boolean ki, boolean imagePreview) {
		if (!isValidExtension(extension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"extension ist ungültig: nur a–z und 0–9, höchstens 20 Zeichen, ohne Punkt");
		}
		// Pflicht: save mit einer vorhandenen Endung wäre ein Update und würde sie überschreiben
		if (fileExtensionRepository.existsById(extension)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "die Endung gibt es schon");
		}
		checkFileExtensionCollectionExists(fileExtensionCollectionKey);
		return fileExtensionRepository.save(new FileExtension(extension, description, fileExtensionCollectionKey,
				dangerous, listingPosition, ocr, ki, imagePreview));
	}

	/** Die Endung bestimmt nur, welche geändert wird, sie selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileExtension updateFileExtension(String extension, String description, String fileExtensionCollectionKey,
			boolean dangerous, int listingPosition, boolean ocr, boolean ki, boolean imagePreview) {
		FileExtension fileExtension = getFileExtensionByExtension(extension);
		checkFileExtensionCollectionExists(fileExtensionCollectionKey);
		fileExtension.setDescription(description);
		fileExtension.setFileExtensionCollectionKey(fileExtensionCollectionKey);
		fileExtension.setDangerous(dangerous);
		fileExtension.setListingPosition(listingPosition);
		fileExtension.setOcr(ocr);
		fileExtension.setKi(ki);
		fileExtension.setImagePreview(imagePreview);
		return fileExtensionRepository.save(fileExtension);
	}

	/**
	 * Neue Positionen mehrerer Endungen auf einmal, z. B. nach Drag and Drop innerhalb einer Gruppe. Der Controller
	 * hat Endungen und Zahlen schon geprüft. Zurück kommen alle Endungen in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<FileExtension> changeFileExtensionPositions(Map<String, Integer> newPositionByExtension) {
		for (Map.Entry<String, Integer> newPosition : newPositionByExtension.entrySet()) {
			FileExtension fileExtension = getFileExtensionByExtension(newPosition.getKey());
			fileExtension.setListingPosition(newPosition.getValue());
			fileExtensionRepository.save(fileExtension);
		}
		return getAllFileExtensions();
	}

	/** Löscht die Endung und nimmt sie allen Dateiarten weg. Gibt sie zurück, wie sie vor dem Löschen war. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileExtension deleteFileExtension(String extension) {
		FileExtension fileExtension = getFileExtensionByExtension(extension);
		fileSubClassService.removeFileExtensionFromAllFileSubClasses(extension);
		fileExtensionRepository.delete(fileExtension);
		return fileExtension;
	}

	/** Gibt es die Endung nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	@Transactional(readOnly = true)
	public FileExtension getFileExtensionByExtension(String extension) {
		return fileExtensionRepository.findById(extension)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Endung " + extension));
	}

	/**
	 * Alle Endungen, sortiert nach listingPosition und bei gleicher Position nach der Endung. Für alle, auch ohne
	 * Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	@Transactional(readOnly = true)
	public List<FileExtension> getAllFileExtensions() {
		return fileExtensionRepository.findAll(Sort.by("listingPosition", "extension"));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isFileExtensionAvailable(String extension) {
		return !fileExtensionRepository.existsById(extension);
	}

	/** true, wenn der Text eine gültige Endung ist: nur a–z und 0–9, höchstens 20 Zeichen, ohne Punkt. */
	public static boolean isValidExtension(String extension) {
		return extension != null && VALID_EXTENSION.matcher(extension).matches();
	}

	/**
	 * Eine Gruppe wird gelöscht: Ihre Endungen stehen danach ohne Gruppe. Aufgerufen vom
	 * FileExtensionCollectionService, deshalb ohne @PreAuthorize: die Prüfung sitzt dort.
	 */
	@Transactional
	public void removeFileExtensionCollectionFromAllFileExtensions(String fileExtensionCollectionKey) {
		for (FileExtension fileExtension : fileExtensionRepository
				.findByFileExtensionCollectionKey(fileExtensionCollectionKey)) {
			fileExtension.setFileExtensionCollectionKey(null);
			fileExtensionRepository.save(fileExtension);
		}
	}

	/** 400, wenn es die Gruppe nicht gibt. null heißt ohne Gruppe und ist erlaubt. */
	private void checkFileExtensionCollectionExists(String fileExtensionCollectionKey) {
		// ZIRKELSCHLUSS: direkt das fremde FileExtensionCollectionRepository, siehe Feld fileExtensionCollectionRepository
		if (fileExtensionCollectionKey != null && !fileExtensionCollectionRepository.existsById(fileExtensionCollectionKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Gruppe mit key " + fileExtensionCollectionKey);
		}
	}

}
