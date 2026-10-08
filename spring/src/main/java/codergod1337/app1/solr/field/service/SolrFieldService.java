package codergod1337.app1.solr.field.service;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.core.repository.SolrCoreRepository;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldClientWriteRule;
import codergod1337.app1.solr.field.model.SolrFieldCodeDefinition;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.repository.SolrFieldRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.solr.sync.SolrDefinitionChangedEvent;
import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.service.AccessRoleService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Felder der Solr-Kerne. Wer was darf, steht als @PreAuthorize über der Methode, die Rollen kommen aus dem Token.
 *
 * Die Tabelle liegt im Speicher, wie Hooks und Kerne: Die Lese-Services rechnen je Request aus Token und dieser Kopie,
 * welche Felder ein User bekommt, ohne Postgres. Unveränderliche Map nach id in Listenreihenfolge (Felder jedes Kerns
 * zuerst, dann je Kern, darin Position und Name), nur ersetzt, neu geladen nach jedem Commit. Siehe SolrHookService.
 *
 * Der Name eines Feldes ist eindeutig über alle Kerne. coreKey leer heißt in jedem Kern, auch id steht so in der
 * Tabelle, aus dem Code (SolrField). Zwei Wege schreiben hier: der Admin im Kern-Tab und der handgemachte
 * Initialisierer eines Kerns beim Start ({@link #defineSolrFieldFromCode}), der ohne Token läuft und deshalb keine
 * @PreAuthorize trägt.
 *
 * readAccessRoleKeys, backendOnly und clientWriteRule stehen nur hier, nie in Solr: Der Abgleich ignoriert sie, die
 * Lesemechanik baut daraus, welche Felder sie anfordert, die Schreibmechanik, welche Felder der Client setzen darf.
 * required heißt, der Client muss das Feld beim Anlegen mitschicken (User 2026-10-09): Es geht deshalb nur zusammen
 * mit einer Schreibregel, bei der der Client beim Anlegen setzt, ein Backend-Feld ist nie required.
 */
@Service
public class SolrFieldService {

	private static final Logger log = LoggerFactory.getLogger(SolrFieldService.class);

	/** Felder jedes Kerns zuerst, dann je Kern, darin Position, dann Name */
	private static final Comparator<SolrField> LISTING_ORDER = Comparator
			.comparing(SolrField::getCoreKey, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparingInt(SolrField::getListingPosition).thenComparing(SolrField::getName);

	private final SolrFieldRepository solrFieldRepository;
	private final SolrHookService solrHookService;
	private final AccessRoleService accessRoleService;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme „gibt es diesen Kern?“ über den SolrCoreService. Der braucht aber diesen Service,
	 * um beim Löschen eines Kerns die Felder mitzulöschen. Deshalb hier ausnahmsweise direkt das fremde
	 * SolrCoreRepository, nur lesend.
	 */
	private final SolrCoreRepository solrCoreRepository;

	/** Meldet jede Änderung nach dem Commit, der SolrSchemaSyncService zieht Solr dann nach */
	private final ApplicationEventPublisher applicationEventPublisher;

	/** Die ganze Tabelle nach id, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<Long, SolrField> solrFieldsById = Map.of();

	public SolrFieldService(SolrFieldRepository solrFieldRepository, SolrHookService solrHookService,
			AccessRoleService accessRoleService, SolrCoreRepository solrCoreRepository,
			ApplicationEventPublisher applicationEventPublisher) {
		this.solrFieldRepository = solrFieldRepository;
		this.solrHookService = solrHookService;
		this.accessRoleService = accessRoleService;
		this.solrCoreRepository = solrCoreRepository;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@PostConstruct
	void loadSolrFields() {
		reloadSolrFields();
	}

	// ===== Admin =====

	/**
	 * Neues Feld. 400 bei ungültigem name (dazu zählt id, das kommt aus dem Code), bei einem Hook-Namen (den gibt es in
	 * jedem Kern schon), bei unbekanntem Kern, unbekannter AR, suggest bei einem Typ ohne Zwilling oder required an
	 * einem Feld, das der Client nicht setzt. 409 wenn es den Namen schon gibt, in welchem Kern auch immer. coreKey
	 * leer heißt in jedem Kern. clientWriteRule null heißt BACKEND_ONLY.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField createSolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, List<String> readAccessRoleKeys,
			boolean backendOnly, SolrFieldClientWriteRule clientWriteRule, String displayName, String description,
			int listingPosition) {
		String checkedCoreKey = checkedCoreKey(coreKey);
		requireNewName(name);
		requireType(type);
		requireSuggestAllowed(type, suggest);
		requireRequiredOnlyWhenClientSets(clientWriteRule, required);
		SolrField solrField = solrFieldRepository.save(new SolrField(checkedCoreKey, name, type, indexed, stored,
				multiValued, docValues, required, suggest, checkedAccessRoleKeys(readAccessRoleKeys, backendOnly),
				backendOnly, clientWriteRule, displayName, description, listingPosition));
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	/**
	 * Die id bestimmt, welches Feld geändert wird. Kern und Name ändern sich nie, alles andere wird ersetzt. 400 bei
	 * unbekannter AR, suggest bei einem Typ ohne Zwilling oder required an einem Feld, das der Client nicht setzt.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField updateSolrField(Long id, SolrFieldType type, boolean indexed, boolean stored, boolean multiValued,
			boolean docValues, boolean required, boolean suggest, List<String> readAccessRoleKeys, boolean backendOnly,
			SolrFieldClientWriteRule clientWriteRule, String displayName, String description, int listingPosition) {
		SolrField solrField = findSolrFieldForWrite(id);
		requireType(type);
		requireSuggestAllowed(type, suggest);
		requireRequiredOnlyWhenClientSets(clientWriteRule, required);
		solrField.setType(type);
		solrField.setIndexed(indexed);
		solrField.setStored(stored);
		solrField.setMultiValued(multiValued);
		solrField.setDocValues(docValues);
		solrField.setRequired(required);
		solrField.setSuggest(suggest);
		solrField.setReadAccessRoleKeys(checkedAccessRoleKeys(readAccessRoleKeys, backendOnly));
		solrField.setBackendOnly(backendOnly);
		solrField.setClientWriteRule(clientWriteRule);
		solrField.setDisplayName(displayName);
		solrField.setDescription(description);
		solrField.setListingPosition(listingPosition);
		SolrField saved = solrFieldRepository.save(solrField);
		reloadSolrFieldsAfterCommit();
		return saved;
	}

	/**
	 * Neue Positionen mehrerer Felder auf einmal, z. B. nach Drag and Drop. Der Controller hat ids und Zahlen schon
	 * geprüft. Eine Transaktion: scheitert eine, bleibt keine geändert. Zurück kommen alle in neuer Reihenfolge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<SolrField> changeSolrFieldPositions(Map<Long, Integer> newPositionById) {
		List<SolrField> changed = new ArrayList<>();
		for (Map.Entry<Long, Integer> newPosition : newPositionById.entrySet()) {
			SolrField solrField = findSolrFieldForWrite(newPosition.getKey());
			solrField.setListingPosition(newPosition.getValue());
			changed.add(solrField);
		}
		solrFieldRepository.saveAll(changed);
		reloadSolrFieldsAfterCommit();
		return sorted(solrFieldRepository.findAll());
	}

	/**
	 * Löscht das Feld aus unserer Tabelle. Was in Solr mit dem Feld passiert, entscheidet der Abgleich, der löscht nie.
	 * Ein Feld, das der Initialisierer eines Kerns im Code führt, kommt beim nächsten Start wieder. Gibt das Feld
	 * zurück, wie es vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField deleteSolrField(Long id) {
		SolrField solrField = findSolrFieldForWrite(id);
		solrFieldRepository.delete(solrField);
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	/** Ein Kern wird gelöscht: seine Felder mit, nicht die Felder jedes Kerns. Aufgerufen vom SolrCoreService, deshalb ohne @PreAuthorize. */
	@Transactional
	public void deleteSolrFieldsByCoreKey(String coreKey) {
		solrFieldRepository.deleteAll(solrFieldRepository.findByCoreKey(coreKey));
		reloadSolrFieldsAfterCommit();
	}

	// ===== Der Initialisierer eines Kerns =====

	/**
	 * Ein Feld, wie der Code eines Kerns es festlegt, beim Start ohne Token, deshalb ohne @PreAuthorize. Gibt es die
	 * Zeile nicht, entsteht sie komplett aus der Definition. Gibt es sie, zieht der Code type, Schalter, suggest,
	 * backendOnly, clientWriteRule, displayName und description nach; readAccessRoleKeys und listingPosition bleiben
	 * dem Admin, nur eine leere AR-Liste bekommt die Startwerte (SolrFieldCodeDefinition). Eine AR, die es nicht gibt,
	 * fällt mit einer Warnung weg, der Start bricht nicht ab.
	 *
	 * @return true, wenn die Zeile neu war
	 */
	@Transactional
	public boolean defineSolrFieldFromCode(SolrFieldCodeDefinition definition) {
		requireType(definition.type());
		requireSuggestAllowed(definition.type(), definition.suggest());
		requireRequiredOnlyWhenClientSets(definition.clientWriteRule(), definition.required());
		List<String> accessRoleKeys = knownAccessRoleKeys(definition.readAccessRoleKeys(), definition.backendOnly(),
				definition.name());
		SolrField existing = solrFieldRepository.findByName(definition.name()).orElse(null);
		if (existing == null) {
			solrFieldRepository.save(new SolrField(definition.coreKey(), definition.name(), definition.type(),
					definition.indexed(), definition.stored(), definition.multiValued(), definition.docValues(),
					definition.required(), definition.suggest(), accessRoleKeys, definition.backendOnly(),
					definition.clientWriteRule(), definition.displayName(), definition.description(),
					definition.listingPosition()));
			reloadSolrFieldsAfterCommit();
			return true;
		}
		existing.setType(definition.type());
		existing.setIndexed(definition.indexed());
		existing.setStored(definition.stored());
		existing.setMultiValued(definition.multiValued());
		existing.setDocValues(definition.docValues());
		existing.setRequired(definition.required());
		existing.setSuggest(definition.suggest());
		existing.setBackendOnly(definition.backendOnly());
		existing.setClientWriteRule(definition.clientWriteRule());
		existing.setDisplayName(definition.displayName());
		existing.setDescription(definition.description());
		if (existing.getReadAccessRoleKeys().isEmpty() || definition.backendOnly()) {
			existing.setReadAccessRoleKeys(accessRoleKeys);
		}
		solrFieldRepository.save(existing);
		reloadSolrFieldsAfterCommit();
		return false;
	}

	// ===== Lesen =====

	/** Aus dem Speicher. Gibt es das Feld nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	public SolrField getSolrFieldById(Long id) {
		SolrField solrField = id != null ? solrFieldsById.get(id) : null;
		if (solrField == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Feld mit dieser id");
		}
		return solrField;
	}

	/**
	 * Alle Felder aller Kerne aus dem Speicher, die Felder jedes Kerns zuerst, dann je Kern nach listingPosition und
	 * name. Für alle, auch ohne Login, ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<SolrField> getAllSolrFields() {
		return List.copyOf(solrFieldsById.values());
	}

	/** Die Felder eines Kerns aus dem Speicher, in Listenreihenfolge: die Felder jedes Kerns zuerst, dann seine eigenen */
	public List<SolrField> getSolrFieldsByCoreKey(String coreKey) {
		return solrFieldsById.values().stream()
				.filter(solrField -> solrField.isInEveryCore() || solrField.getCoreKey().equals(coreKey)).toList();
	}

	/** Das Feld mit diesem Namen aus dem Speicher, null wenn es keins gibt. Der Name ist eindeutig über alle Kerne. */
	public SolrField findSolrFieldByName(String name) {
		return solrFieldsById.values().stream().filter(solrField -> solrField.getName().equals(name)).findFirst()
				.orElse(null);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isSolrFieldIdKnown(Long id) {
		return id != null && solrFieldsById.containsKey(id);
	}

	// ===== Prüfungen =====

	/** Leer heißt in jedem Kern (null), sonst muss es den Kern geben (400). */
	private String checkedCoreKey(String coreKey) {
		if (coreKey == null || coreKey.isBlank()) {
			return null;
		}
		// ZIRKELSCHLUSS: direkt das fremde SolrCoreRepository, siehe Feld solrCoreRepository
		if (!solrCoreRepository.existsById(coreKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unbekannter Kern");
		}
		return coreKey;
	}

	/** Ein neuer Name: gültig (id und Solrs eigene Felder sind es nicht), kein Hook, noch in keinem Kern vergeben */
	private void requireNewName(String name) {
		if (!SolrFieldName.isValid(name)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name ist kein gültiger Solr-Feldname");
		}
		if (solrHookService.getAllSolrHooks().stream().map(SolrHook::getKey).anyMatch(name::equals)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"name ist ein Hook, den gibt es in jedem Kern schon");
		}
		// Pflicht: der Unique-Index würde sonst erst beim Commit knallen, ohne lesbare Meldung
		if (solrFieldRepository.existsByName(name)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"das Feld " + name + " gibt es schon, der Name ist über alle Kerne eindeutig");
		}
	}

	private static void requireType(SolrFieldType type) {
		if (type == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type fehlt");
		}
	}

	private static void requireSuggestAllowed(SolrFieldType type, boolean suggest) {
		if (suggest && !type.isSuggestable()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"suggest geht bei " + type + " nicht, dieser Typ bekommt keinen Zwilling");
		}
	}

	/** required heißt, der Client muss es beim Anlegen mitschicken: sinnlos bei einem Feld, das nur das Backend setzt. null heißt BACKEND_ONLY. */
	private static void requireRequiredOnlyWhenClientSets(SolrFieldClientWriteRule clientWriteRule, boolean required) {
		if (required && (clientWriteRule == null || !clientWriteRule.isClientAllowedToSetOnCreate())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"required geht nur bei einem Feld, das der Client beim Anlegen setzt; ein Backend-Feld setzt das Backend selbst");
		}
	}

	/** Die AR-Liste des Admins: jeder key gültig und vorhanden (400), ohne Doppelte; bei backendOnly immer leer */
	private List<String> checkedAccessRoleKeys(List<String> accessRoleKeys, boolean backendOnly) {
		if (backendOnly || accessRoleKeys == null) {
			return List.of();
		}
		Set<String> known = knownAccessRoleKeys();
		Set<String> checked = new LinkedHashSet<>();
		for (String accessRoleKey : accessRoleKeys) {
			if (!HelperInputs.isValidKey(accessRoleKey) || !known.contains(accessRoleKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, accessRoleKey + " ist keine bekannte AR");
			}
			checked.add(accessRoleKey);
		}
		return List.copyOf(checked);
	}

	/** Die AR-Liste aus dem Code: unbekannte fallen mit Warnung weg, der Start bricht nicht ab */
	private List<String> knownAccessRoleKeys(List<String> accessRoleKeys, boolean backendOnly, String fieldName) {
		if (backendOnly) {
			return List.of();
		}
		Set<String> known = knownAccessRoleKeys();
		Set<String> kept = new LinkedHashSet<>();
		for (String accessRoleKey : accessRoleKeys) {
			if (known.contains(accessRoleKey)) {
				kept.add(accessRoleKey);
			} else {
				log.warn("Feld {}: die AR {} aus dem Code gibt es nicht, sie fällt weg", fieldName, accessRoleKey);
			}
		}
		return List.copyOf(kept);
	}

	private Set<String> knownAccessRoleKeys() {
		Set<String> known = new LinkedHashSet<>();
		for (AccessRole accessRole : accessRoleService.getAllAccessRoles()) {
			known.add(accessRole.getKey());
		}
		return known;
	}

	/** Zum Schreiben aus der Datenbank, nicht aus dem Speicher: so bleibt die Entity an der Transaktion. */
	private SolrField findSolrFieldForWrite(Long id) {
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt");
		}
		return solrFieldRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Feld mit dieser id"));
	}

	/** Nach dem Commit neu laden, nie davor. Gleiches Muster wie im SolrHookService. */
	private void reloadSolrFieldsAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					reloadSolrFields();
					applicationEventPublisher.publishEvent(new SolrDefinitionChangedEvent(null));
				}
			});
		} else {
			reloadSolrFields();
			applicationEventPublisher.publishEvent(new SolrDefinitionChangedEvent(null));
		}
	}

	/** Die ganze Tabelle neu lesen und die Map ersetzen. */
	private void reloadSolrFields() {
		Map<Long, SolrField> byId = new LinkedHashMap<>();
		for (SolrField solrField : sorted(solrFieldRepository.findAll())) {
			byId.put(solrField.getId(), solrField);
		}
		solrFieldsById = Collections.unmodifiableMap(byId);
	}

	private static List<SolrField> sorted(List<SolrField> solrFields) {
		return solrFields.stream().sorted(LISTING_ORDER).toList();
	}

}
