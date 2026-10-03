package codergod1337.app1.solr.hook.service;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.repository.SolrHookGroupRepository;
import codergod1337.app1.solr.hook.repository.SolrHookRepository;
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
 * Die Hooks. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus dem Token.
 *
 * DIE TABELLE LIEGT IM SPEICHER: Bei jeder Suche wird geprüft, über welche Hooks der User fragen darf. Das soll Solr der
 * einzige Dienst bleiben, mit dem gesprochen wird, also kein Zugriff auf Postgres je Abfrage. Der Service hält deshalb
 * die ganze Tabelle als unveränderliche Map, die nie geändert, sondern nur ersetzt wird: Leser sehen immer einen ganzen
 * Stand. Geladen wird beim Start und nach jedem Schreiben, aber erst nach dem Commit, sonst stünde im Speicher, was die
 * Datenbank gleich zurückrollt. Dieser Prozess ist der einzige Schreiber, der Speicher kann also nicht veralten.
 */
@Service
public class SolrHookService {

	/** Sortiert nach listingPosition und bei gleicher Position nach key, in der Datenbank wie im Speicher */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	private final SolrHookRepository solrHookRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme „gibt es diese Gruppe?“ über den SolrHookGroupService. Der braucht aber diesen
	 * Service, um beim Löschen einer Gruppe zu prüfen, ob noch Hooks darin stehen. Deshalb hier ausnahmsweise direkt
	 * das fremde SolrHookGroupRepository, nur lesend.
	 */
	private final SolrHookGroupRepository solrHookGroupRepository;

	/** Die ganze Tabelle nach key, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<String, SolrHook> solrHooksByKey = Map.of();

	public SolrHookService(SolrHookRepository solrHookRepository, SolrHookGroupRepository solrHookGroupRepository) {
		this.solrHookRepository = solrHookRepository;
		this.solrHookGroupRepository = solrHookGroupRepository;
	}

	@PostConstruct
	void loadSolrHooks() {
		reloadSolrHooks();
	}

	/** Neuer Hook. 400 wenn der key kein Solr-Feldname ist oder es die Gruppe nicht gibt, 409 wenn der key vergeben ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHook createSolrHook(String key, String displayName, String description, String hookGroupKey,
			int listingPosition) {
		if (!SolrFieldName.isValid(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist kein gültiger Solr-Feldname");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde den bestehenden Hook überschreiben
		if (solrHookRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		SolrHook solrHook = solrHookRepository.save(new SolrHook(key, displayName, description,
				checkedHookGroupKey(hookGroupKey), listingPosition));
		reloadSolrHooksAfterCommit();
		return solrHook;
	}

	/**
	 * Der key bestimmt nur, welcher Hook geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt.
	 * 400, wenn es die Gruppe nicht gibt.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHook updateSolrHook(String key, String displayName, String description, String hookGroupKey,
			int listingPosition) {
		SolrHook solrHook = findSolrHookForWrite(key);
		solrHook.setDisplayName(displayName);
		solrHook.setDescription(description);
		solrHook.setHookGroupKey(checkedHookGroupKey(hookGroupKey));
		solrHook.setListingPosition(listingPosition);
		SolrHook saved = solrHookRepository.save(solrHook);
		reloadSolrHooksAfterCommit();
		return saved;
	}

	/**
	 * Neue Positionen mehrerer Hooks auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<SolrHook> changeSolrHookPositions(Map<String, Integer> newPositionByKey) {
		List<SolrHook> changed = new ArrayList<>();
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			SolrHook solrHook = findSolrHookForWrite(newPosition.getKey());
			solrHook.setListingPosition(newPosition.getValue());
			changed.add(solrHook);
		}
		solrHookRepository.saveAll(changed);
		reloadSolrHooksAfterCommit();
		return solrHookRepository.findAll(LISTING_ORDER);
	}

	/**
	 * Löscht den Hook endgültig. Was in den Kernen mit dem Feld passiert, entscheidet der Abgleich mit Solr (SolrManager
	 * Schritt 4). Gibt den Hook zurück, wie er vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHook deleteSolrHook(String key) {
		SolrHook solrHook = findSolrHookForWrite(key);
		solrHookRepository.delete(solrHook);
		reloadSolrHooksAfterCommit();
		return solrHook;
	}

	/** Aus dem Speicher. Gibt es den Hook nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	public SolrHook getSolrHookByKey(String key) {
		SolrHook solrHook = solrHooksByKey.get(key);
		if (solrHook == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Hook mit diesem key");
		}
		return solrHook;
	}

	/**
	 * Alle Hooks aus dem Speicher, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne
	 * Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<SolrHook> getAllSolrHooks() {
		return List.copyOf(solrHooksByKey.values());
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isSolrHookKeyAvailable(String key) {
		return !solrHooksByKey.containsKey(key);
	}

	/** Die Gruppe: leer heißt keine (null), sonst muss es sie geben (400). */
	private String checkedHookGroupKey(String hookGroupKey) {
		if (hookGroupKey == null || hookGroupKey.isBlank()) {
			return null;
		}
		// ZIRKELSCHLUSS: direkt das fremde SolrHookGroupRepository, siehe Feld solrHookGroupRepository
		if (!solrHookGroupRepository.existsById(hookGroupKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unbekannte Hook-Gruppe " + hookGroupKey);
		}
		return hookGroupKey;
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private SolrHook findSolrHookForWrite(String key) {
		return solrHookRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Hook mit diesem key"));
	}

	/**
	 * Der Speicher wird nach dem Commit neu geladen, nie davor. Läuft gerade eine Transaktion (immer bei den
	 * Schreibmethoden, auch beim Stammdaten-Import, der viele Hooks in einer anlegt), wartet das Laden auf ihren
	 * Commit. Mehrere Anmeldungen in derselben Transaktion laden mehrfach, das ist bei dieser Tabellengröße egal.
	 */
	private void reloadSolrHooksAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadSolrHooks();
				}
			});
		} else {
			reloadSolrHooks();
		}
	}

	/** Die ganze Tabelle neu lesen und die Map ersetzen. Nicht einzeln nachpflegen: Das kann nicht auseinanderlaufen. */
	private void reloadSolrHooks() {
		Map<String, SolrHook> byKey = new LinkedHashMap<>();
		for (SolrHook solrHook : solrHookRepository.findAll(LISTING_ORDER)) {
			byKey.put(solrHook.getKey(), solrHook);
		}
		solrHooksByKey = Collections.unmodifiableMap(byKey);
	}

}
