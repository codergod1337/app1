package codergod1337.app1.solr.field.service;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.repository.SolrCoreRepository;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.repository.SolrFieldRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Die Tabelle liegt im Speicher, wie Hooks und Kerne: Die Suche baut später je Request aus den Feldern eines Kerns ihre
 * Abfrage, ohne Postgres. Unveränderliche Map nach id in Listenreihenfolge (Kern, Position, Name), nur ersetzt, neu
 * geladen nach jedem Commit. Siehe SolrHookService.
 *
 * Die Felder id und cursorDate (SolrCore.BUILT_IN_FIELD_NAMES) legt der SolrCoreService mit jedem Kern an
 * (createSolrFieldIfMissing). Beide lassen sich nicht löschen, ihr Typ steht fest. id ist der Primärschlüssel
 * (uniqueKey) jedes Kerns: string, indexed, required, nie multiValued, was hineinkommt, sagt seine Beschreibung.
 * cursorDate ist der Fortschrittszeiger eines Reindex-Laufs, ein Datum. Alles andere an beiden (bei cursorDate auch die
 * Schalter) ist frei wie bei jedem Feld.
 */
@Service
public class SolrFieldService {

	/** Kern, dann Position, dann Name */
	private static final Comparator<SolrField> LISTING_ORDER = Comparator.comparing(SolrField::getCoreKey)
			.thenComparingInt(SolrField::getListingPosition).thenComparing(SolrField::getName);

	private final SolrFieldRepository solrFieldRepository;
	private final SolrHookService solrHookService;

	/**
	 * ZIRKELSCHLUSS: Eigentlich käme „gibt es diesen Kern?“ über den SolrCoreService. Der braucht aber diesen Service,
	 * um beim Anlegen eines Kerns das Feld id anzulegen und beim Löschen die Felder mitzulöschen. Deshalb hier
	 * ausnahmsweise direkt das fremde SolrCoreRepository, nur lesend.
	 */
	private final SolrCoreRepository solrCoreRepository;

	/** Die ganze Tabelle nach id, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<Long, SolrField> solrFieldsById = Map.of();

	public SolrFieldService(SolrFieldRepository solrFieldRepository, SolrHookService solrHookService,
			SolrCoreRepository solrCoreRepository) {
		this.solrFieldRepository = solrFieldRepository;
		this.solrHookService = solrHookService;
		this.solrCoreRepository = solrCoreRepository;
	}

	@PostConstruct
	void loadSolrFields() {
		reloadSolrFields();
	}

	/**
	 * Neues Feld. 400 bei ungültigem name, unbekanntem Kern, einem Hook-Namen (den gibt es in jedem Kern schon) oder
	 * suggest bei einem Typ ohne Zwilling. 409 wenn es das Feld in diesem Kern schon gibt.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField createSolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, String description,
			int listingPosition) {
		// ZIRKELSCHLUSS: direkt das fremde SolrCoreRepository, siehe Feld solrCoreRepository
		if (coreKey == null || !solrCoreRepository.existsById(coreKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unbekannter Kern");
		}
		if (!SolrFieldName.isValid(name)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name ist kein gültiger Solr-Feldname");
		}
		if (isSolrHookKey(name)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"name ist ein Hook, den gibt es in jedem Kern schon");
		}
		requireType(type);
		requireSuggestAllowed(type, suggest);
		// Pflicht: der Unique-Index würde sonst erst beim Commit knallen, ohne lesbare Meldung
		if (solrFieldRepository.existsByCoreKeyAndName(coreKey, name)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "das Feld gibt es in diesem Kern schon");
		}
		SolrField solrField = solrFieldRepository.save(new SolrField(coreKey, name, type, indexed, stored, multiValued,
				docValues, required, suggest, description, listingPosition));
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	/**
	 * Ein Feld, das der Code mitbringt, z. B. id mit jedem Kern. Gibt es das Feld schon, bleibt es, wie es ist. Danach
	 * ist es ein normales Feld wie jedes andere. Aufgerufen vom SolrCoreService, deshalb ohne @PreAuthorize: die Prüfung
	 * sitzt dort. Position 0, damit es ganz oben steht.
	 */
	@Transactional
	public SolrField createSolrFieldIfMissing(String coreKey, String name, SolrFieldType type, boolean indexed,
			boolean stored, boolean multiValued, boolean docValues, boolean required, String description) {
		SolrField existing = solrFieldRepository.findByCoreKey(coreKey).stream()
				.filter(candidate -> candidate.getName().equals(name)).findFirst().orElse(null);
		if (existing != null) {
			return existing;
		}
		SolrField saved = solrFieldRepository.save(new SolrField(coreKey, name, type, indexed, stored, multiValued,
				docValues, required, false, description, 0));
		reloadSolrFieldsAfterCommit();
		return saved;
	}

	/**
	 * Die id bestimmt, welches Feld geändert wird. Kern und Name ändern sich nie, bei id und cursorDate auch nicht der
	 * Typ, bei id auch nicht indexed, required und multiValued (400). Alle anderen Felder werden ersetzt.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField updateSolrField(Long id, SolrFieldType type, boolean indexed, boolean stored, boolean multiValued,
			boolean docValues, boolean required, boolean suggest, String description, int listingPosition) {
		SolrField solrField = findSolrFieldForWrite(id);
		requireType(type);
		requireSuggestAllowed(type, suggest);
		requireBuiltInFieldFixed(solrField, type, indexed, multiValued, required);
		solrField.setType(type);
		solrField.setIndexed(indexed);
		solrField.setStored(stored);
		solrField.setMultiValued(multiValued);
		solrField.setDocValues(docValues);
		solrField.setRequired(required);
		solrField.setSuggest(suggest);
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
	 * Löscht das Feld aus unserer Tabelle. 409 bei id und cursorDate, die hat jeder Kern. Was in Solr mit dem Feld
	 * passiert, entscheidet der Abgleich (SolrManager Schritt 4). Gibt das Feld zurück, wie es vor dem Löschen war.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField deleteSolrField(Long id) {
		SolrField solrField = findSolrFieldForWrite(id);
		if (SolrCore.BUILT_IN_FIELD_NAMES.contains(solrField.getName())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					solrField.getName() + " hat jeder Kern, das Feld bleibt");
		}
		solrFieldRepository.delete(solrField);
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	/** Ein Kern wird gelöscht: alle seine Felder mit. Aufgerufen vom SolrCoreService, deshalb ohne @PreAuthorize. */
	@Transactional
	public void deleteSolrFieldsByCoreKey(String coreKey) {
		solrFieldRepository.deleteAll(solrFieldRepository.findByCoreKey(coreKey));
		reloadSolrFieldsAfterCommit();
	}

	/** Aus dem Speicher. Gibt es das Feld nicht, kommt 404. Für alle, ohne @PreAuthorize. */
	public SolrField getSolrFieldById(Long id) {
		SolrField solrField = id != null ? solrFieldsById.get(id) : null;
		if (solrField == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Feld mit dieser id");
		}
		return solrField;
	}

	/**
	 * Alle Felder aller Kerne aus dem Speicher, sortiert nach Kern, listingPosition und name. Für alle, auch ohne Login,
	 * ohne @PreAuthorize: Die Stammdaten sind öffentlich.
	 */
	public List<SolrField> getAllSolrFields() {
		return List.copyOf(solrFieldsById.values());
	}

	/** Die Felder eines Kerns aus dem Speicher, in Listenreihenfolge */
	public List<SolrField> getSolrFieldsByCoreKey(String coreKey) {
		return solrFieldsById.values().stream().filter(solrField -> solrField.getCoreKey().equals(coreKey)).toList();
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isSolrFieldIdKnown(Long id) {
		return id != null && solrFieldsById.containsKey(id);
	}

	/** Hooks gibt es in jedem Kern, ein Feld darf nicht so heißen */
	private boolean isSolrHookKey(String name) {
		return solrHookService.getAllSolrHooks().stream().map(SolrHook::getKey).anyMatch(name::equals);
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

	/**
	 * Die festen Felder jedes Kerns: id ist der Primärschlüssel, string, indexed, required, nie multiValued. cursorDate
	 * bleibt ein Datum, der Reindex filtert und sortiert danach. 400 bei jedem Versuch, das zu ändern.
	 */
	private static void requireBuiltInFieldFixed(SolrField solrField, SolrFieldType type, boolean indexed,
			boolean multiValued, boolean required) {
		if (SolrCore.ID_FIELD_NAME.equals(solrField.getName())
				&& (type != SolrFieldType.STRING || !indexed || multiValued || !required)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"id ist der Primärschlüssel jedes Kerns: string, indexed, required, nicht multiValued");
		}
		if (SolrCore.CURSOR_DATE_FIELD_NAME.equals(solrField.getName()) && type != SolrFieldType.DATE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursorDate bleibt ein Datum, jeder Kern hat es");
		}
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
				}
			});
		} else {
			reloadSolrFields();
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
