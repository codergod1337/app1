package codergod1337.app1.solr.field.service;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.repository.SolrCoreRepository;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldClientWriteRule;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.repository.SolrFieldRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.repository.SolrHookRepository;
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
 * zuerst, dann je Kern erst die normalen Felder, dann die Hook-Zeilen, darin Position und Name), nur ersetzt, neu
 * geladen nach jedem Commit. Siehe SolrHookService.
 *
 * Zwei Arten Zeilen (SolrField): normale Felder, angelegt im Kern-Tab oder per Import, und Hook-Zeilen, eine je Kern und
 * Hook, die dieser Service selbst anlegt und löscht, wenn ein Hook oder ein Kern entsteht oder verschwindet. An einer
 * Hook-Zeile ändert der Admin nur Leseliste, Suchgewicht und Position. Kein Code legt normale Felder an: Der SolrManager
 * ist das Werkzeug der Entwicklung, der Export sichert den Stand (User 2026-10-09).
 *
 * readAccessRoleKeys, clientWriteRule und searchWeight stehen nur hier, nie in Solr: Der Abgleich ignoriert sie, die
 * Lesemechanik baut daraus fl, Auflöserechte und qf, die Schreibmechanik, welche Felder der Client setzen darf.
 * required heißt, der Client muss das Feld beim Anlegen mitschicken (User 2026-10-09): Es geht deshalb nur zusammen
 * mit einer Schreibregel, bei der der Client beim Anlegen setzt, ein Backend-Feld ist nie required.
 */
@Service
public class SolrFieldService {

	/** Felder jedes Kerns zuerst, dann je Kern, darin normale Felder vor Hook-Zeilen, dann Position, dann Name */
	private static final Comparator<SolrField> LISTING_ORDER = Comparator
			.comparing(SolrField::getCoreKey, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(SolrField::isHookField).thenComparingInt(SolrField::getListingPosition)
			.thenComparing(SolrField::getName);

	private final SolrFieldRepository solrFieldRepository;
	private final AccessRoleService accessRoleService;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme „gibt es diesen Kern?“ und „welche Kerne gibt es?“ über den SolrCoreService. Der
	 * braucht aber diesen Service, um beim Anlegen eines Kerns die Hook-Zeilen und beim Löschen die Felder mitzuziehen.
	 * Deshalb hier ausnahmsweise direkt das fremde SolrCoreRepository, nur lesend.
	 */
	private final SolrCoreRepository solrCoreRepository;

	/**
	 * ZIRKELSCHLUSS: Eigentlich kämen die Hooks über den SolrHookService. Der braucht aber diesen Service, um beim Anlegen
	 * eines Hooks seine Zeile in jedem Kern anzulegen. Deshalb hier ausnahmsweise direkt das fremde SolrHookRepository,
	 * nur lesend.
	 */
	private final SolrHookRepository solrHookRepository;

	/** Meldet jede Änderung nach dem Commit, der SolrSchemaSyncService zieht Solr dann nach */
	private final ApplicationEventPublisher applicationEventPublisher;

	/** Die ganze Tabelle nach id, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<Long, SolrField> solrFieldsById = Map.of();

	public SolrFieldService(SolrFieldRepository solrFieldRepository, AccessRoleService accessRoleService,
			SolrCoreRepository solrCoreRepository, SolrHookRepository solrHookRepository,
			ApplicationEventPublisher applicationEventPublisher) {
		this.solrFieldRepository = solrFieldRepository;
		this.accessRoleService = accessRoleService;
		this.solrCoreRepository = solrCoreRepository;
		this.solrHookRepository = solrHookRepository;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@PostConstruct
	void loadSolrFields() {
		reloadSolrFields();
	}

	// ===== Admin =====

	/**
	 * Neues normales Feld. 400 bei ungültigem name, bei unbekanntem Kern, unbekannter AR, suggest bei einem Typ ohne
	 * Zwilling, required an einem Feld, das der Client nicht setzt, oder Suchgewicht an einem Feld ohne Wortanfang. 409
	 * wenn es den Namen schon gibt, in welchem Kern auch immer, auch als Hook. coreKey leer heißt in jedem Kern.
	 * clientWriteRule null heißt BACKEND_ONLY.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField createSolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, List<String> readAccessRoleKeys,
			SolrFieldClientWriteRule clientWriteRule, int searchWeight, String displayName, String description,
			int listingPosition, String command) {
		String checkedCoreKey = checkedCoreKey(coreKey);
		requireNewName(name);
		requireType(type);
		requireSuggestAllowed(type, suggest);
		requireRequiredOnlyWhenClientSets(clientWriteRule, required);
		requireSearchWeightAllowed(indexed, searchWeight);
		SolrField newSolrField = new SolrField(checkedCoreKey, name, type, indexed, stored, multiValued, docValues,
				required, suggest, checkedAccessRoleKeys(readAccessRoleKeys), clientWriteRule, searchWeight, displayName,
				description, listingPosition);
		newSolrField.setCommand(checkedCommand(command));
		SolrField solrField = solrFieldRepository.save(newSolrField);
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	/**
	 * Die id bestimmt, welches Feld geändert wird. Kern und Name ändern sich nie. Bei einer Hook-Zeile ändern sich nur
	 * Leseliste, Suchgewicht und Position, die Technik steht fest. Sonst wird alles andere ersetzt. 400 bei unbekannter
	 * AR, suggest bei einem Typ ohne Zwilling, required an einem Feld, das der Client nicht setzt, oder Suchgewicht an
	 * einem Feld ohne Wortanfang.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField updateSolrField(Long id, SolrFieldType type, boolean indexed, boolean stored, boolean multiValued,
			boolean docValues, boolean required, boolean suggest, List<String> readAccessRoleKeys,
			SolrFieldClientWriteRule clientWriteRule, int searchWeight, String displayName, String description,
			int listingPosition, String command) {
		SolrField solrField = findSolrFieldForWrite(id);
		// das Mapping darf auch einen Hook füllen, z. B. artikelNummer aus ARTIKEL_NUMMER
		solrField.setCommand(checkedCommand(command));
		if (solrField.isHookField()) {
			requireSearchWeightAllowed(solrField.isIndexed(), searchWeight);
			solrField.setReadAccessRoleKeys(checkedAccessRoleKeys(readAccessRoleKeys));
			solrField.setSearchWeight(searchWeight);
			solrField.setListingPosition(listingPosition);
		} else {
			requireType(type);
			requireSuggestAllowed(type, suggest);
			requireRequiredOnlyWhenClientSets(clientWriteRule, required);
			requireSearchWeightAllowed(indexed, searchWeight);
			solrField.setType(type);
			solrField.setIndexed(indexed);
			solrField.setStored(stored);
			solrField.setMultiValued(multiValued);
			solrField.setDocValues(docValues);
			solrField.setRequired(required);
			solrField.setSuggest(suggest);
			solrField.setReadAccessRoleKeys(checkedAccessRoleKeys(readAccessRoleKeys));
			solrField.setClientWriteRule(clientWriteRule);
			solrField.setSearchWeight(searchWeight);
			solrField.setDisplayName(displayName);
			solrField.setDescription(description);
			solrField.setListingPosition(listingPosition);
		}
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
	 * Löscht ein normales Feld aus unserer Tabelle. Eine Hook-Zeile geht nur mit ihrem Hook (400). Was in Solr mit dem
	 * Feld passiert, entscheidet der Abgleich, der löscht nie. Gibt das Feld zurück, wie es vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField deleteSolrField(Long id) {
		SolrField solrField = findSolrFieldForWrite(id);
		if (solrField.isHookField()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"die Zeile eines Hooks geht nur mit dem Hook, im Tab Hooks");
		}
		solrFieldRepository.delete(solrField);
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	// ===== Kerne und Hooks, aufgerufen von SolrCoreService und SolrHookService, deshalb ohne @PreAuthorize =====

	/** Ein Kern wird gelöscht: seine Felder und seine Hook-Zeilen mit, nicht die Felder jedes Kerns. */
	@Transactional
	public void deleteSolrFieldsByCoreKey(String coreKey) {
		solrFieldRepository.deleteAll(solrFieldRepository.findByCoreKey(coreKey));
		reloadSolrFieldsAfterCommit();
	}

	/** Ein Kern entsteht: für jeden Hook seine Zeile in diesem Kern, Technik fest, ohne Leserolle, Startgewicht der Suche. */
	@Transactional
	public void createHookFieldsForCore(String coreKey) {
		// ZIRKELSCHLUSS: direkt das fremde SolrHookRepository, siehe Feld solrHookRepository
		for (SolrHook solrHook : solrHookRepository.findAll()) {
			if (solrFieldRepository.findByCoreKeyAndName(coreKey, solrHook.getKey()).isEmpty()) {
				solrFieldRepository.save(SolrField.forHook(coreKey, solrHook.getKey(), solrHook.getListingPosition()));
			}
		}
		reloadSolrFieldsAfterCommit();
	}

	/** Ein Hook entsteht: seine Zeile in jedem Kern, Technik fest, ohne Leserolle, Startgewicht der Suche. */
	@Transactional
	public void createHookFields(String hookKey, int listingPosition) {
		// ZIRKELSCHLUSS: direkt das fremde SolrCoreRepository, siehe Feld solrCoreRepository
		for (SolrCore solrCore : solrCoreRepository.findAll()) {
			if (solrFieldRepository.findByCoreKeyAndName(solrCore.getKey(), hookKey).isEmpty()) {
				solrFieldRepository.save(SolrField.forHook(solrCore.getKey(), hookKey, listingPosition));
			}
		}
		reloadSolrFieldsAfterCommit();
	}

	/** Ein Hook verschwindet: seine Zeilen in allen Kernen mit. */
	@Transactional
	public void deleteHookFields(String hookKey) {
		solrFieldRepository.deleteAll(solrFieldRepository.findByHookKey(hookKey));
		reloadSolrFieldsAfterCommit();
	}

	/**
	 * Der Import: Leseliste und Suchgewicht einer Hook-Zeile, die der Hook-Import gerade angelegt hat. Gibt es die Zeile
	 * nicht, ist der Hook oder der Kern nicht da, dann 404.
	 */
	@Transactional
	public SolrField changeHookFieldFromImport(String coreKey, String hookKey, List<String> readAccessRoleKeys,
			int searchWeight) {
		SolrField solrField = solrFieldRepository.findByCoreKeyAndName(coreKey, hookKey)
				.filter(SolrField::isHookField)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"keine Zeile für den Hook " + hookKey + " im Kern " + coreKey));
		solrField.setReadAccessRoleKeys(checkedAccessRoleKeys(readAccessRoleKeys));
		solrField.setSearchWeight(Math.max(-1, searchWeight));
		SolrField saved = solrFieldRepository.save(solrField);
		reloadSolrFieldsAfterCommit();
		return saved;
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

	/**
	 * Alles, was ein Kern hat, aus dem Speicher, in Listenreihenfolge: die Felder jedes Kerns, seine eigenen, seine
	 * Hook-Zeilen. Auflösen und Suche brauchen keine zweite Liste.
	 */
	public List<SolrField> getSolrFieldsByCoreKey(String coreKey) {
		return solrFieldsById.values().stream()
				.filter(solrField -> solrField.isInEveryCore() || solrField.getCoreKey().equals(coreKey)).toList();
	}

	/**
	 * Eine Zeile mit diesem Namen aus dem Speicher, null wenn es keine gibt. Bei einem Hook die erste seiner Zeilen, die
	 * Technik ist in jedem Kern dieselbe.
	 */
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

	/** Ein neuer Name: gültig (Solrs eigene Felder sind es nicht), in keinem Kern vergeben, auch nicht als Hook */
	private void requireNewName(String name) {
		if (!SolrFieldName.isValid(name)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name ist kein gültiger Solr-Feldname");
		}
		// Pflicht: der Unique-Index würde sonst erst beim Commit knallen, ohne lesbare Meldung
		if (solrFieldRepository.existsByName(name)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"den Namen " + name + " gibt es schon, als Feld oder Hook; ein Name bedeutet im ganzen System eines");
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

	/** Der lose Fremdschlüssel aufs Mapping: leer heißt keiner, sonst ein Key nach der Key-Regel; ob es den Eintrag gibt, prüft niemand hier */
	private static String checkedCommand(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		if (!HelperInputs.isValidKey(command.trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "command ist kein gültiger Key");
		}
		return command.trim();
	}

	/** required heißt, der Client muss es beim Anlegen mitschicken: sinnlos bei einem Feld, das nur das Backend setzt. null heißt BACKEND_ONLY. */
	private static void requireRequiredOnlyWhenClientSets(SolrFieldClientWriteRule clientWriteRule, boolean required) {
		if (required && (clientWriteRule == null || !clientWriteRule.isClientAllowedToSetOnCreate())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"required geht nur bei einem Feld, das der Client beim Anlegen setzt; ein Backend-Feld setzt das Backend selbst");
		}
	}

	/**
	 * -1 heißt nicht durchsucht und geht immer; ab 0 (durchsucht, 0 = Treffer ohne Punkte) nur mit indexed, ohne Index
	 * hat Solr nichts zum Durchsuchen. Sonst entscheidet der Admin (User 2026-10-09).
	 */
	private static void requireSearchWeightAllowed(boolean indexed, int searchWeight) {
		if (searchWeight < -1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "searchWeight ist kleiner als -1");
		}
		if (searchWeight >= 0 && !indexed) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"searchWeight ab 0 geht nur bei einem Feld mit indexed, -1 heißt nicht durchsucht");
		}
	}

	/** Die AR-Liste des Admins: jeder key gültig und vorhanden (400), ohne Doppelte, leer erlaubt */
	private List<String> checkedAccessRoleKeys(List<String> accessRoleKeys) {
		if (accessRoleKeys == null) {
			return List.of();
		}
		Set<String> known = new LinkedHashSet<>();
		for (AccessRole accessRole : accessRoleService.getAllAccessRoles()) {
			known.add(accessRole.getKey());
		}
		Set<String> checked = new LinkedHashSet<>();
		for (String accessRoleKey : accessRoleKeys) {
			if (!HelperInputs.isValidKey(accessRoleKey) || !known.contains(accessRoleKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, accessRoleKey + " ist keine bekannte AR");
			}
			checked.add(accessRoleKey);
		}
		return List.copyOf(checked);
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
