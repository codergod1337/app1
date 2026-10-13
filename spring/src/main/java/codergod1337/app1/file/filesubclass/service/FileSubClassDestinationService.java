package codergod1337.app1.file.filesubclass.service;

import codergod1337.app1.abas.repository.AbasInfosysRepository;
import codergod1337.app1.file.filesubclass.model.FileSubClassDestination;
import codergod1337.app1.file.filesubclass.repository.FileSubClassDestinationRepository;
import codergod1337.app1.file.filesubclass.repository.FileSubClassRepository;
import codergod1337.app1.system.HelperInputs;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Anzeigeorte der Dateiarten (User 2026-10-13). Wer was darf, steht als @PreAuthorize über der Methode, die Rollen
 * kommen aus dem Token. Die Tabelle liegt im Speicher wie die Status: unveränderliche Map, nur ersetzt, neu geladen
 * nach jedem Commit. Dateiart und Infosystem-Aufruf prüfen beim Speichern über {@link #isFileSubClassDestinationKeyKnown}
 * gegen diese Kopie.
 *
 * ZIRKELSCHLUSS: Ob eine Dateiart oder ein Aufruf den Ort noch trägt, käme eigentlich über deren Services. Die brauchen
 * aber diesen Service zur Prüfung ihrer Anzeigeorte. Deshalb hier ausnahmsweise die fremden Repositories direkt, nur
 * lesend, fürs Löschen.
 */
@Service
public class FileSubClassDestinationService {

	/** Sortiert nach listingPosition und bei gleicher Position nach key, in der Datenbank wie im Speicher */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	private final FileSubClassDestinationRepository fileSubClassDestinationRepository;
	private final FileSubClassRepository fileSubClassRepository;
	private final AbasInfosysRepository abasInfosysRepository;

	/** Die ganze Tabelle nach key, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<String, FileSubClassDestination> fileSubClassDestinationsByKey = Map.of();

	public FileSubClassDestinationService(FileSubClassDestinationRepository fileSubClassDestinationRepository,
			FileSubClassRepository fileSubClassRepository, AbasInfosysRepository abasInfosysRepository) {
		this.fileSubClassDestinationRepository = fileSubClassDestinationRepository;
		this.fileSubClassRepository = fileSubClassRepository;
		this.abasInfosysRepository = abasInfosysRepository;
	}

	@PostConstruct
	void loadFileSubClassDestinations() {
		reloadFileSubClassDestinations();
	}

	/** Neuer Anzeigeort. 400 wenn der key ungültig ist, 409 wenn er vergeben ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClassDestination createFileSubClassDestination(String key, String displayName, String description,
			int listingPosition) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde den bestehenden Ort überschreiben
		if (fileSubClassDestinationRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		FileSubClassDestination saved = fileSubClassDestinationRepository
				.save(new FileSubClassDestination(key, displayName, blankToNull(description), listingPosition));
		reloadFileSubClassDestinationsAfterCommit();
		return saved;
	}

	/** Der key bestimmt nur, welcher Ort geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClassDestination updateFileSubClassDestination(String key, String displayName, String description,
			int listingPosition) {
		FileSubClassDestination destination = findFileSubClassDestinationForWrite(key);
		destination.setDisplayName(displayName);
		destination.setDescription(blankToNull(description));
		destination.setListingPosition(listingPosition);
		FileSubClassDestination saved = fileSubClassDestinationRepository.save(destination);
		reloadFileSubClassDestinationsAfterCommit();
		return saved;
	}

	/**
	 * Neue Positionen mehrerer Orte auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<FileSubClassDestination> changeFileSubClassDestinationPositions(Map<String, Integer> newPositionByKey) {
		List<FileSubClassDestination> changed = new ArrayList<>();
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			FileSubClassDestination destination = findFileSubClassDestinationForWrite(newPosition.getKey());
			destination.setListingPosition(newPosition.getValue());
			changed.add(destination);
		}
		fileSubClassDestinationRepository.saveAll(changed);
		reloadFileSubClassDestinationsAfterCommit();
		return fileSubClassDestinationRepository.findAll(LISTING_ORDER);
	}

	/** Löscht den Ort. 409, solange eine Dateiart oder ein Infosystem-Aufruf ihn noch in den Anzeigeorten trägt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public FileSubClassDestination deleteFileSubClassDestination(String key) {
		FileSubClassDestination destination = findFileSubClassDestinationForWrite(key);
		List<String> carriers = new ArrayList<>();
		fileSubClassRepository.findAll().stream().filter(fileSubClass -> fileSubClass.getDisplayContextKeys().contains(key))
				.forEach(fileSubClass -> carriers.add("Dateiart " + fileSubClass.getKey()));
		abasInfosysRepository.findAll().stream().filter(abasInfosys -> abasInfosys.isShownIn(key))
				.forEach(abasInfosys -> carriers.add("Aufruf " + abasInfosys.getKey()));
		if (!carriers.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Anzeigeort " + key + " wird noch genutzt von " + String.join(", ", carriers));
		}
		fileSubClassDestinationRepository.delete(destination);
		reloadFileSubClassDestinationsAfterCommit();
		return destination;
	}

	/**
	 * Alle Orte aus dem Speicher, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne
	 * Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<FileSubClassDestination> getAllFileSubClassDestinations() {
		return List.copyOf(fileSubClassDestinationsByKey.values());
	}

	/** Gibt es den Ort? Dateiart und Aufruf fragen das beim Speichern ihrer Anzeigeorte, aus dem Speicher. */
	public boolean isFileSubClassDestinationKeyKnown(String key) {
		return key != null && fileSubClassDestinationsByKey.containsKey(key);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isFileSubClassDestinationKeyAvailable(String key) {
		return !fileSubClassDestinationsByKey.containsKey(key);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text;
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private FileSubClassDestination findFileSubClassDestinationForWrite(String key) {
		return fileSubClassDestinationRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Anzeigeort mit diesem key"));
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie beim Status. */
	private void reloadFileSubClassDestinationsAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadFileSubClassDestinations();
				}
			});
		} else {
			reloadFileSubClassDestinations();
		}
	}

	/** Die ganze Tabelle neu lesen und die Map ersetzen. */
	private void reloadFileSubClassDestinations() {
		Map<String, FileSubClassDestination> byKey = new LinkedHashMap<>();
		for (FileSubClassDestination destination : fileSubClassDestinationRepository.findAll(LISTING_ORDER)) {
			byKey.put(destination.getKey(), destination);
		}
		fileSubClassDestinationsByKey = Collections.unmodifiableMap(byKey);
	}

}
