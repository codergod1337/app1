package codergod1337.app1.solr.core.service;

import codergod1337.app1.solr.SolrLook;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.repository.SolrCoreRepository;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.sync.SolrDefinitionChangedEvent;
import codergod1337.app1.system.HelperInputs;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Solr-Kerne. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus dem Token.
 *
 * Die Tabelle liegt im Speicher, aus demselben Grund wie bei den Hooks: Die Suche läuft später je Request über die Kerne,
 * ohne Postgres. Unveränderliche Map, nur ersetzt, neu geladen nach jedem Commit. Siehe SolrHookService.
 */
@Service
public class SolrCoreService {

	/**
	 * Keys, die kein Kern tragen darf, weil das Frontend sie als eigene Tabs unter /solrmanager/ benutzt. Jeder Kern
	 * bekommt dort seinen Tab unter /solrmanager/<key>. Der Router unterscheidet keine Groß- und Kleinschreibung,
	 * deshalb großgeschrieben wie die Keys.
	 */
	private static final Set<String> RESERVED_KEYS = Set.of("HOOKS", "HOOKGROUPS", "CORES");

	/** Sortiert nach listingPosition und bei gleicher Position nach key, in der Datenbank wie im Speicher */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	private final SolrCoreRepository solrCoreRepository;

	/**
	 * ZIRKELSCHLUSS: Dieser Service löscht beim Löschen eines Kerns dessen Felder über den SolrFieldService. Der müsste
	 * umgekehrt „gibt es diesen Kern?“ hier fragen, nutzt dafür aber ausnahmsweise direkt das SolrCoreRepository.
	 */
	private final SolrFieldService solrFieldService;

	/** Meldet jede Änderung nach dem Commit, der SolrSchemaSyncService zieht Solr dann nach */
	private final ApplicationEventPublisher applicationEventPublisher;

	/** Die ganze Tabelle nach key, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<String, SolrCore> solrCoresByKey = Map.of();

	public SolrCoreService(SolrCoreRepository solrCoreRepository, SolrFieldService solrFieldService,
			ApplicationEventPublisher applicationEventPublisher) {
		this.solrCoreRepository = solrCoreRepository;
		this.solrFieldService = solrFieldService;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * true, wenn der Text ein Kern-Key sein darf: die normale Key-Regel (HelperInputs.isValidKey, z. B. DMS oder
	 * ARTIKEL), und keiner der Keys, die das Frontend für eigene Tabs braucht. Der Key ist zugleich der Name des Kerns
	 * in Solr. Für Controller und Import.
	 */
	public static boolean isValidSolrCoreKey(String key) {
		return HelperInputs.isValidKey(key) && !RESERVED_KEYS.contains(key);
	}

	@PostConstruct
	void loadSolrCores() {
		reloadSolrCores();
	}

	/**
	 * Neuer Kern. 400 wenn der key ungültig ist, 409 wenn er vergeben ist. Seine Felder kommen aus solr_field: die ohne
	 * Kern hat er sofort, die Zeilen aller Hooks legt der SolrFieldService gleich an, seine eigenen legt der Admin im
	 * Kern-Tab an oder der Import bringt sie mit.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrCore createSolrCore(String key, String displayName, SolrLook look, int listingPosition) {
		if (!isValidSolrCoreKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde den bestehenden Kern überschreiben
		if (solrCoreRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		SolrCore solrCore = solrCoreRepository.save(new SolrCore(key, displayName, look, listingPosition));
		solrFieldService.createHookFieldsForCore(key);
		reloadSolrCoresAfterCommit();
		return solrCore;
	}

	/** Der key bestimmt nur, welcher Kern geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrCore updateSolrCore(String key, String displayName, SolrLook look, int listingPosition) {
		SolrCore solrCore = findSolrCoreForWrite(key);
		solrCore.setDisplayName(displayName);
		solrCore.setLook(look);
		solrCore.setListingPosition(listingPosition);
		SolrCore saved = solrCoreRepository.save(solrCore);
		reloadSolrCoresAfterCommit();
		return saved;
	}

	/**
	 * Neue Positionen mehrerer Kerne auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<SolrCore> changeSolrCorePositions(Map<String, Integer> newPositionByKey) {
		List<SolrCore> changed = new ArrayList<>();
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			SolrCore solrCore = findSolrCoreForWrite(newPosition.getKey());
			solrCore.setListingPosition(newPosition.getValue());
			changed.add(solrCore);
		}
		solrCoreRepository.saveAll(changed);
		reloadSolrCoresAfterCommit();
		return solrCoreRepository.findAll(LISTING_ORDER);
	}

	/**
	 * Löscht den Kern aus unserer Tabelle, mit allen seinen Feldern. Was in Solr mit dem Kern passiert, entscheidet der
	 * Abgleich (SolrManager Schritt 4). Gibt den Kern zurück, wie er vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrCore deleteSolrCore(String key) {
		SolrCore solrCore = findSolrCoreForWrite(key);
		solrFieldService.deleteSolrFieldsByCoreKey(key);
		solrCoreRepository.delete(solrCore);
		reloadSolrCoresAfterCommit();
		return solrCore;
	}

	/** Aus dem Speicher. Gibt es den Kern nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	public SolrCore getSolrCoreByKey(String key) {
		SolrCore solrCore = solrCoresByKey.get(key);
		if (solrCore == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Kern mit diesem key");
		}
		return solrCore;
	}

	/**
	 * Alle Kerne aus dem Speicher, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch ohne
	 * Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<SolrCore> getAllSolrCores() {
		return List.copyOf(solrCoresByKey.values());
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isSolrCoreKeyAvailable(String key) {
		return !solrCoresByKey.containsKey(key);
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private SolrCore findSolrCoreForWrite(String key) {
		return solrCoreRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Kern mit diesem key"));
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie im SolrHookService. */
	private void reloadSolrCoresAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadSolrCores();
					applicationEventPublisher.publishEvent(new SolrDefinitionChangedEvent(null));
				}
			});
		} else {
			reloadSolrCores();
			applicationEventPublisher.publishEvent(new SolrDefinitionChangedEvent(null));
		}
	}

	/** Die ganze Tabelle neu lesen und die Map ersetzen. */
	private void reloadSolrCores() {
		Map<String, SolrCore> byKey = new LinkedHashMap<>();
		for (SolrCore solrCore : solrCoreRepository.findAll(LISTING_ORDER)) {
			byKey.put(solrCore.getKey(), solrCore);
		}
		solrCoresByKey = Collections.unmodifiableMap(byKey);
	}

}
