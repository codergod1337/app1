package codergod1337.app1.system.state.service;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.state.model.EntityState;
import codergod1337.app1.system.state.repository.EntityStateRepository;
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
 * Die Status aller Entities, die einen brauchen. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen
 * kommen aus dem Token. Die Tabelle liegt im Speicher wie die Hook-Gruppen: unveränderliche Map, nur ersetzt, neu
 * geladen nach jedem Commit. Ein Modul, das einen Status braucht (z. B. das DMS), legt ihn beim Start an, falls er
 * fehlt, und ändert ihn danach nie mehr: Was der Admin pflegt, bleibt.
 */
@Service
public class EntityStateService {

	/** Sortiert nach listingPosition und bei gleicher Position nach key, in der Datenbank wie im Speicher */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	private final EntityStateRepository entityStateRepository;

	/** Die ganze Tabelle nach key, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<String, EntityState> entityStatesByKey = Map.of();

	public EntityStateService(EntityStateRepository entityStateRepository) {
		this.entityStateRepository = entityStateRepository;
	}

	@PostConstruct
	void loadEntityStates() {
		reloadEntityStates();
	}

	/** Neuer Status. 400 wenn key oder scope ungültig sind, 409 wenn der key vergeben ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public EntityState createEntityState(String key, String displayName, String scope, int listingPosition) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde den bestehenden Status überschreiben
		if (entityStateRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		EntityState entityState = entityStateRepository
				.save(new EntityState(key, displayName, checkedScope(scope), listingPosition));
		reloadEntityStatesAfterCommit();
		return entityState;
	}

	/**
	 * Beim Start eines Moduls: der Status, den es braucht, falls er fehlt. Ohne Rechteprüfung, es läuft kein User.
	 * Gibt es den key schon, bleibt der Status, wie der Admin ihn gepflegt hat.
	 */
	@Transactional
	public EntityState createEntityStateIfMissing(String key, String displayName, String scope, int listingPosition) {
		EntityState existing = entityStateRepository.findById(key).orElse(null);
		if (existing != null) {
			return existing;
		}
		EntityState entityState = entityStateRepository
				.save(new EntityState(key, displayName, checkedScope(scope), listingPosition));
		reloadEntityStatesAfterCommit();
		return entityState;
	}

	/** Der key bestimmt nur, welcher Status geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public EntityState updateEntityState(String key, String displayName, String scope, int listingPosition) {
		EntityState entityState = findEntityStateForWrite(key);
		entityState.setDisplayName(displayName);
		entityState.setScope(checkedScope(scope));
		entityState.setListingPosition(listingPosition);
		EntityState saved = entityStateRepository.save(entityState);
		reloadEntityStatesAfterCommit();
		return saved;
	}

	/**
	 * Neue Positionen mehrerer Status auf einmal, z. B. nach Drag and Drop. Der Controller hat keys und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<EntityState> changeEntityStatePositions(Map<String, Integer> newPositionByKey) {
		List<EntityState> changed = new ArrayList<>();
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			EntityState entityState = findEntityStateForWrite(newPosition.getKey());
			entityState.setListingPosition(newPosition.getValue());
			changed.add(entityState);
		}
		entityStateRepository.saveAll(changed);
		reloadEntityStatesAfterCommit();
		return entityStateRepository.findAll(LISTING_ORDER);
	}

	/**
	 * Löscht den Status. Dokumente, die ihn tragen, behalten den key, nur der Anzeigename fehlt dann. Ob noch welche
	 * damit in Solr stehen, prüft später der Service des Kerns.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public EntityState deleteEntityState(String key) {
		EntityState entityState = findEntityStateForWrite(key);
		entityStateRepository.delete(entityState);
		reloadEntityStatesAfterCommit();
		return entityState;
	}

	/** Aus dem Speicher. Gibt es den Status nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	public EntityState getEntityStateByKey(String key) {
		EntityState entityState = entityStatesByKey.get(key);
		if (entityState == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Status mit diesem key");
		}
		return entityState;
	}

	/**
	 * Alle Status aus dem Speicher, sortiert nach listingPosition und bei gleicher Position nach key. Für alle, auch
	 * ohne Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<EntityState> getAllEntityStates() {
		return List.copyOf(entityStatesByKey.values());
	}

	/** Die Status eines Bereichs in Listenreihenfolge: die mit diesem scope und die ohne scope, denn die gelten überall. */
	public List<EntityState> getEntityStatesByScope(String scope) {
		return entityStatesByKey.values().stream()
				.filter(entityState -> entityState.getScope() == null || entityState.getScope().equals(scope))
				.toList();
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isEntityStateKeyAvailable(String key) {
		return !entityStatesByKey.containsKey(key);
	}

	/** Leer heißt überall, sonst die normale Key-Regel */
	private static String checkedScope(String scope) {
		if (scope == null || scope.isBlank()) {
			return null;
		}
		if (!HelperInputs.isValidKey(scope)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope ist ungültig");
		}
		return scope;
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private EntityState findEntityStateForWrite(String key) {
		return entityStateRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Status mit diesem key"));
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie im SolrHookService. */
	private void reloadEntityStatesAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadEntityStates();
				}
			});
		} else {
			reloadEntityStates();
		}
	}

	/** Die ganze Tabelle neu lesen und die Map ersetzen. */
	private void reloadEntityStates() {
		Map<String, EntityState> byKey = new LinkedHashMap<>();
		for (EntityState entityState : entityStateRepository.findAll(LISTING_ORDER)) {
			byKey.put(entityState.getKey(), entityState);
		}
		entityStatesByKey = Collections.unmodifiableMap(byKey);
	}

}
