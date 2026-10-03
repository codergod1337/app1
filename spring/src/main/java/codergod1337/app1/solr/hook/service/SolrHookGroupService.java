package codergod1337.app1.solr.hook.service;

import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.model.SolrHookGroup;
import codergod1337.app1.solr.hook.repository.SolrHookGroupRepository;
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
 * Die Hook-Gruppen, nur für die Anzeige. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus
 * dem Token. Die Tabelle liegt im Speicher wie die Hooks: unveränderliche Map, nur ersetzt, neu geladen nach jedem
 * Commit. Siehe SolrHookService.
 */
@Service
public class SolrHookGroupService {

	/** Sortiert nach listingPosition und bei gleicher Position nach key, in der Datenbank wie im Speicher */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	private final SolrHookGroupRepository solrHookGroupRepository;

	/**
	 * ZIRKELSCHLUSS: Löschen fragt hier, ob noch ein Hook die Gruppe nutzt. Umgekehrt müsste der SolrHookService beim
	 * Schreiben eines Hooks hier fragen, ob es die Gruppe gibt, und nutzt dafür ausnahmsweise direkt das
	 * SolrHookGroupRepository, nur lesend.
	 */
	private final SolrHookService solrHookService;

	/** Die ganze Tabelle nach key, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<String, SolrHookGroup> solrHookGroupsByKey = Map.of();

	public SolrHookGroupService(SolrHookGroupRepository solrHookGroupRepository, SolrHookService solrHookService) {
		this.solrHookGroupRepository = solrHookGroupRepository;
		this.solrHookService = solrHookService;
	}

	@PostConstruct
	void loadSolrHookGroups() {
		reloadSolrHookGroups();
	}

	/** Neue Gruppe. 400 wenn der key ungültig ist, 409 wenn er vergeben ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHookGroup createSolrHookGroup(String key, String displayName, int listingPosition) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde die bestehende Gruppe überschreiben
		if (solrHookGroupRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		SolrHookGroup solrHookGroup = solrHookGroupRepository.save(new SolrHookGroup(key, displayName, listingPosition));
		reloadSolrHookGroupsAfterCommit();
		return solrHookGroup;
	}

	/** Der key bestimmt nur, welche Gruppe geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHookGroup updateSolrHookGroup(String key, String displayName, int listingPosition) {
		SolrHookGroup solrHookGroup = findSolrHookGroupForWrite(key);
		solrHookGroup.setDisplayName(displayName);
		solrHookGroup.setListingPosition(listingPosition);
		SolrHookGroup saved = solrHookGroupRepository.save(solrHookGroup);
		reloadSolrHookGroupsAfterCommit();
		return saved;
	}

	/**
	 * Neue Positionen mehrerer Gruppen auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<SolrHookGroup> changeSolrHookGroupPositions(Map<String, Integer> newPositionByKey) {
		List<SolrHookGroup> changed = new ArrayList<>();
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			SolrHookGroup solrHookGroup = findSolrHookGroupForWrite(newPosition.getKey());
			solrHookGroup.setListingPosition(newPosition.getValue());
			changed.add(solrHookGroup);
		}
		solrHookGroupRepository.saveAll(changed);
		reloadSolrHookGroupsAfterCommit();
		return solrHookGroupRepository.findAll(LISTING_ORDER);
	}

	/** Löscht die Gruppe. 409, solange noch ein Hook darin steht: erst die Hooks umhängen. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrHookGroup deleteSolrHookGroup(String key) {
		SolrHookGroup solrHookGroup = findSolrHookGroupForWrite(key);
		long hooksInGroup = solrHookService.getAllSolrHooks().stream().map(SolrHook::getHookGroupKey)
				.filter(key::equals).count();
		if (hooksInGroup > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"noch " + hooksInGroup + " Hooks in dieser Gruppe, erst umhängen");
		}
		solrHookGroupRepository.delete(solrHookGroup);
		reloadSolrHookGroupsAfterCommit();
		return solrHookGroup;
	}

	/** Aus dem Speicher. Gibt es die Gruppe nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	public SolrHookGroup getSolrHookGroupByKey(String key) {
		SolrHookGroup solrHookGroup = solrHookGroupsByKey.get(key);
		if (solrHookGroup == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Hook-Gruppe mit diesem key");
		}
		return solrHookGroup;
	}

	/**
	 * Alle Gruppen aus dem Speicher, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch
	 * ohne Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<SolrHookGroup> getAllSolrHookGroups() {
		return List.copyOf(solrHookGroupsByKey.values());
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isSolrHookGroupKeyAvailable(String key) {
		return !solrHookGroupsByKey.containsKey(key);
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private SolrHookGroup findSolrHookGroupForWrite(String key) {
		return solrHookGroupRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Hook-Gruppe mit diesem key"));
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie im SolrHookService. */
	private void reloadSolrHookGroupsAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadSolrHookGroups();
				}
			});
		} else {
			reloadSolrHookGroups();
		}
	}

	/** Die ganze Tabelle neu lesen und die Map ersetzen. */
	private void reloadSolrHookGroups() {
		Map<String, SolrHookGroup> byKey = new LinkedHashMap<>();
		for (SolrHookGroup solrHookGroup : solrHookGroupRepository.findAll(LISTING_ORDER)) {
			byKey.put(solrHookGroup.getKey(), solrHookGroup);
		}
		solrHookGroupsByKey = Collections.unmodifiableMap(byKey);
	}

}
