package codergod1337.app1.file.filesubclass.service;

import codergod1337.app1.file.fileextension.repository.FileExtensionRepository;
import codergod1337.app1.file.filesubclass.model.FileSubClass;
import codergod1337.app1.file.filesubclass.repository.FileSubClassRepository;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.model.AccessRoleSymbol;
import codergod1337.app1.system.access.service.AccessRoleService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Dateiarten (FSC). Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus dem Token.
 *
 * AR-Keys in den Rechtelisten: Wird eine AR gelöscht, bleibt ihr Key hier stehen. Er wirkt nicht mehr, weil niemand
 * die AR hat. Beim nächsten Speichern fallen unbekannte Keys still heraus. So muss das Paket system die Dateiarten
 * nicht kennen.
 */
@Service
public class FileSubClassService {

	private final FileSubClassRepository fileSubClassRepository;
	private final AccessRoleService accessRoleService;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme die Prüfung „gibt es diese Endung?“ über den FileExtensionService. Der braucht aber
	 * diesen Service, um eine gelöschte Endung allen Dateiarten wegzunehmen. Deshalb hier ausnahmsweise direkt das
	 * fremde FileExtensionRepository, und zwar nur lesend (existsById).
	 */
	private final FileExtensionRepository fileExtensionRepository;

	public FileSubClassService(FileSubClassRepository fileSubClassRepository, AccessRoleService accessRoleService,
			FileExtensionRepository fileExtensionRepository) {
		this.fileSubClassRepository = fileSubClassRepository;
		this.accessRoleService = accessRoleService;
		this.fileExtensionRepository = fileExtensionRepository;
	}

	/** Neue Dateiart, noch ohne freigeschaltete Endungen. 400 bei ungültigem key, 409 wenn er vergeben ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClass createFileSubClass(String key, String displayName, String description, int listingPosition,
			AccessRoleSymbol symbol, String color, List<String> readAccessRoleKeys, List<String> writeAccessRoleKeys) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde die bestehende Dateiart überschreiben
		if (fileSubClassRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		Set<String> knownAccessRoleKeys = knownAccessRoleKeys();
		List<String> checkedWriteAccessRoleKeys = keepKnownAccessRoleKeys(writeAccessRoleKeys, knownAccessRoleKeys);
		List<String> checkedReadAccessRoleKeys = withWriteAccessRoleKeys(
				keepKnownAccessRoleKeys(readAccessRoleKeys, knownAccessRoleKeys), checkedWriteAccessRoleKeys);
		return fileSubClassRepository.save(new FileSubClass(key, displayName, description, listingPosition, symbol,
				color, checkedReadAccessRoleKeys, checkedWriteAccessRoleKeys, List.of()));
	}

	/**
	 * Der key bestimmt nur, welche Dateiart geändert wird, er selbst ändert sich nie. Alle anderen Felder werden
	 * ersetzt, außer den freigeschalteten Endungen: die pflegt nur die Freischalt-Matrix.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClass updateFileSubClass(String key, String displayName, String description, int listingPosition,
			AccessRoleSymbol symbol, String color, List<String> readAccessRoleKeys, List<String> writeAccessRoleKeys) {
		FileSubClass fileSubClass = getFileSubClassByKey(key);
		Set<String> knownAccessRoleKeys = knownAccessRoleKeys();
		List<String> checkedWriteAccessRoleKeys = keepKnownAccessRoleKeys(writeAccessRoleKeys, knownAccessRoleKeys);

		fileSubClass.setDisplayName(displayName);
		fileSubClass.setDescription(description);
		fileSubClass.setListingPosition(listingPosition);
		fileSubClass.setSymbol(symbol);
		fileSubClass.setColor(color);
		fileSubClass.setReadAccessRoleKeys(withWriteAccessRoleKeys(
				keepKnownAccessRoleKeys(readAccessRoleKeys, knownAccessRoleKeys), checkedWriteAccessRoleKeys));
		fileSubClass.setWriteAccessRoleKeys(checkedWriteAccessRoleKeys);
		return fileSubClassRepository.save(fileSubClass);
	}

	/**
	 * Setzt Lese- und Schreibrollen mehrerer Dateiarten auf einmal, z. B. nach dem Anmalen in der FSC-ACL-Matrix. Je
	 * key die vollständigen neuen Listen, alle übrigen Felder bleiben. Schreiben schließt Lesen ein, unbekannte AR-Keys
	 * fallen still heraus. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle Dateiarten.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<FileSubClass> changeFileSubClassAccessRoleKeys(Map<String, List<String>> readAccessRoleKeysByKey,
			Map<String, List<String>> writeAccessRoleKeysByKey) {
		Set<String> knownAccessRoleKeys = knownAccessRoleKeys();
		for (Map.Entry<String, List<String>> readAccessRoleKeys : readAccessRoleKeysByKey.entrySet()) {
			FileSubClass fileSubClass = getFileSubClassByKey(readAccessRoleKeys.getKey());
			List<String> checkedWriteAccessRoleKeys = keepKnownAccessRoleKeys(
					writeAccessRoleKeysByKey.get(readAccessRoleKeys.getKey()), knownAccessRoleKeys);
			fileSubClass.setReadAccessRoleKeys(withWriteAccessRoleKeys(
					keepKnownAccessRoleKeys(readAccessRoleKeys.getValue(), knownAccessRoleKeys), checkedWriteAccessRoleKeys));
			fileSubClass.setWriteAccessRoleKeys(checkedWriteAccessRoleKeys);
			fileSubClassRepository.save(fileSubClass);
		}
		return getAllFileSubClasses();
	}

	/**
	 * Setzt die freigeschalteten Endungen einer Dateiart auf genau diese Liste, für die Freischalt-Matrix (FileMatrix):
	 * eine Endung oder alle einer Gruppe in einem Request. Jede Endung muss im Katalog stehen, sonst 400. Doppelte
	 * fallen weg. Eine leere Liste heißt: die Art nimmt nichts an.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClass changeFileSubClassExtensions(String key, List<String> extensions) {
		FileSubClass fileSubClass = getFileSubClassByKey(key);
		Set<String> checkedExtensions = new LinkedHashSet<>();
		for (String extension : extensions) {
			// ZIRKELSCHLUSS: direkt das fremde FileExtensionRepository, siehe Feld fileExtensionRepository
			if (!fileExtensionRepository.existsById(extension)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Katalog kennt die Endung nicht: " + extension);
			}
			checkedExtensions.add(extension);
		}
		fileSubClass.setExtensions(new ArrayList<>(checkedExtensions));
		return fileSubClassRepository.save(fileSubClass);
	}

	/**
	 * Die freigeschalteten Endungen mehrerer Dateiarten auf einmal, für die FileMatrix: je Dateiart die vollständige
	 * neue Liste, geprüft wie bei einer einzelnen. Alles in einer Transaktion. Zurück kommen alle Dateiarten.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<FileSubClass> changeFileSubClassExtensions(Map<String, List<String>> extensionsByKey) {
		for (Map.Entry<String, List<String>> changedExtensions : extensionsByKey.entrySet()) {
			changeFileSubClassExtensions(changedExtensions.getKey(), changedExtensions.getValue());
		}
		return getAllFileSubClasses();
	}

	/**
	 * Neue Positionen mehrerer Dateiarten auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen
	 * schon geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<FileSubClass> changeFileSubClassPositions(Map<String, Integer> newPositionByKey) {
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			FileSubClass fileSubClass = getFileSubClassByKey(newPosition.getKey());
			fileSubClass.setListingPosition(newPosition.getValue());
			fileSubClassRepository.save(fileSubClass);
		}
		return getAllFileSubClasses();
	}

	/**
	 * Löscht die Dateiart endgültig. Dateien, die ihren key tragen, haben danach keinen Namen und kein Symbol mehr.
	 * Gibt die Dateiart zurück, wie sie vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClass deleteFileSubClass(String key) {
		FileSubClass fileSubClass = getFileSubClassByKey(key);
		fileSubClassRepository.delete(fileSubClass);
		return fileSubClass;
	}

	/** Gibt es die Dateiart nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	@Transactional(readOnly = true)
	public FileSubClass getFileSubClassByKey(String key) {
		return fileSubClassRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Dateiart mit diesem key"));
	}

	/**
	 * Alle Dateiarten, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne Login,
	 * ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	@Transactional(readOnly = true)
	public List<FileSubClass> getAllFileSubClasses() {
		return fileSubClassRepository.findAll(Sort.by("listingPosition", "key"));
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public boolean isFileSubClassKeyAvailable(String key) {
		return !fileSubClassRepository.existsById(key);
	}

	/**
	 * Eine Endung wird aus dem Katalog gelöscht: Keine Dateiart nimmt sie danach noch an. Aufgerufen vom
	 * FileExtensionService, deshalb ohne @PreAuthorize: die Prüfung sitzt dort.
	 */
	@Transactional
	public void removeFileExtensionFromAllFileSubClasses(String extension) {
		for (FileSubClass fileSubClass : fileSubClassRepository.findAll()) {
			if (fileSubClass.getExtensions() != null && fileSubClass.getExtensions().contains(extension)) {
				fileSubClass.setExtensions(fileSubClass.getExtensions().stream()
						.filter(candidate -> !candidate.equals(extension))
						.toList());
				fileSubClassRepository.save(fileSubClass);
			}
		}
	}

	/** Alle AR-Keys, die es gibt: ein Aufruf statt einer Prüfung je Key */
	private Set<String> knownAccessRoleKeys() {
		return accessRoleService.getAllAccessRoles().stream().map(AccessRole::getKey).collect(Collectors.toSet());
	}

	/** Nur Keys, die es als AR gibt, ohne Doppelte. Unbekannte fallen still heraus, siehe Klassenkommentar. */
	private static List<String> keepKnownAccessRoleKeys(List<String> accessRoleKeys, Set<String> knownAccessRoleKeys) {
		if (accessRoleKeys == null) {
			return List.of();
		}
		return new ArrayList<>(accessRoleKeys.stream()
				.filter(knownAccessRoleKeys::contains)
				.collect(Collectors.toCollection(LinkedHashSet::new)));
	}

	/** Schreiben schließt Lesen ein: Wer eine Datei ändern darf, muss sie auch sehen. */
	private static List<String> withWriteAccessRoleKeys(List<String> readAccessRoleKeys,
			List<String> writeAccessRoleKeys) {
		Set<String> allReadAccessRoleKeys = new LinkedHashSet<>(readAccessRoleKeys);
		allReadAccessRoleKeys.addAll(writeAccessRoleKeys);
		return new ArrayList<>(allReadAccessRoleKeys);
	}

}
