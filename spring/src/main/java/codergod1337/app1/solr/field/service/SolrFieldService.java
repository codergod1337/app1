package codergod1337.app1.solr.field.service;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.repository.SolrCoreRepository;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldClientWriteRule;
import codergod1337.app1.solr.field.model.SolrFieldReadAccessLevel;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.repository.SolrFieldRepository;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.solr.sync.SolrDefinitionChangedEvent;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Die Tabelle liegt im Speicher, wie Hooks und Kerne: Die Suche baut später je Request aus den Feldern eines Kerns ihre
 * Abfrage, ohne Postgres. Unveränderliche Map nach id in Listenreihenfolge (Kern, Position, Name), nur ersetzt, neu
 * geladen nach jedem Commit. Siehe SolrHookService.
 *
 * Die Felder id, cursorDate und userGrantedAccess (SolrCore.BUILT_IN_FIELD_NAMES) legt der SolrCoreService mit jedem
 * Kern an (createSolrFieldIfMissing). Keins lässt sich löschen, ihr Typ steht fest. id ist der Primärschlüssel
 * (uniqueKey) jedes Kerns: string, indexed, required, nie multiValued, immer Minimum, was hineinkommt, sagt seine
 * Beschreibung. cursorDate ist der Fortschrittszeiger eines Reindex-Laufs, ein Datum. userGrantedAccess trägt die guids
 * mit Dateifreigabe: string, indexed, multiValued, nie required, sonst fände die Dokumentgrenze nichts. Alles andere an
 * den dreien ist frei wie bei jedem Feld.
 *
 * readAccessLevel (Minimum oder Erweitert) und clientWriteRule (wer das Feld setzt) stehen nur hier, nie in Solr: Der
 * Abgleich ignoriert sie, die Lesemechanik baut daraus, welche Felder sie anfordert, die Schreibmechanik, welche Felder
 * der Client setzen darf. Die eingebauten Felder sind immer BACKEND_ONLY.
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

	/** Meldet jede Änderung nach dem Commit, der SolrSchemaSyncService zieht Solr dann nach */
	private final ApplicationEventPublisher applicationEventPublisher;

	/** Die ganze Tabelle nach id, in Listenreihenfolge. Wird nur ersetzt, nie verändert. */
	private volatile Map<Long, SolrField> solrFieldsById = Map.of();

	public SolrFieldService(SolrFieldRepository solrFieldRepository, SolrHookService solrHookService,
			SolrCoreRepository solrCoreRepository, ApplicationEventPublisher applicationEventPublisher) {
		this.solrFieldRepository = solrFieldRepository;
		this.solrHookService = solrHookService;
		this.solrCoreRepository = solrCoreRepository;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@PostConstruct
	void loadSolrFields() {
		reloadSolrFields();
	}

	/**
	 * Neues Feld. 400 bei ungültigem name, unbekanntem Kern, einem Hook-Namen (den gibt es in jedem Kern schon) oder
	 * suggest bei einem Typ ohne Zwilling. 409 wenn es das Feld in diesem Kern schon gibt. readAccessLevel null heißt
	 * Erweitert, clientWriteRule null heißt BACKEND_ONLY, z. B. bei einem Import aus der Zeit vor dem Schalter.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField createSolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest,
			SolrFieldReadAccessLevel readAccessLevel, SolrFieldClientWriteRule clientWriteRule, String description,
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
				docValues, required, suggest, readAccessLevel, clientWriteRule, description, listingPosition));
		reloadSolrFieldsAfterCommit();
		return solrField;
	}

	/**
	 * Ein eingebautes Feld, das der Code mitbringt, z. B. id mit jedem Kern. Gibt es das Feld schon, bleibt es, wie es
	 * ist, mit zwei Ausnahmen: id wird auf Minimum gezogen, und jedes eingebaute Feld auf BACKEND_ONLY, falls es das noch
	 * nicht ist (Kerne aus der Zeit vor dem Schalter). Danach ist es ein normales Feld wie jedes andere. Aufgerufen vom
	 * SolrCoreService, deshalb ohne @PreAuthorize: die Prüfung sitzt dort. Position 0, damit es ganz oben steht.
	 */
	@Transactional
	public SolrField createSolrFieldIfMissing(String coreKey, String name, SolrFieldType type, boolean indexed,
			boolean stored, boolean multiValued, boolean docValues, boolean required,
			SolrFieldReadAccessLevel readAccessLevel, String description) {
		SolrField existing = solrFieldRepository.findByCoreKey(coreKey).stream()
				.filter(candidate -> candidate.getName().equals(name)).findFirst().orElse(null);
		if (existing != null) {
			boolean repair = false;
			if (SolrCore.ID_FIELD_NAME.equals(name)
					&& existing.getReadAccessLevel() != SolrFieldReadAccessLevel.MINIMUM_SET_EVERY_READER) {
				existing.setReadAccessLevel(SolrFieldReadAccessLevel.MINIMUM_SET_EVERY_READER);
				repair = true;
			}
			if (existing.getClientWriteRule() != SolrFieldClientWriteRule.BACKEND_ONLY) {
				existing.setClientWriteRule(SolrFieldClientWriteRule.BACKEND_ONLY);
				repair = true;
			}
			if (repair) {
				SolrField repaired = solrFieldRepository.save(existing);
				reloadSolrFieldsAfterCommit();
				return repaired;
			}
			return existing;
		}
		SolrField saved = solrFieldRepository.save(new SolrField(coreKey, name, type, indexed, stored, multiValued,
				docValues, required, false, readAccessLevel, SolrFieldClientWriteRule.BACKEND_ONLY, description, 0));
		reloadSolrFieldsAfterCommit();
		return saved;
	}

	/**
	 * Die id bestimmt, welches Feld geändert wird. Kern und Name ändern sich nie, bei den eingebauten Feldern auch nicht
	 * der Typ, bei id auch nicht indexed, required und multiValued, bei userGrantedAccess nicht indexed, multiValued und
	 * required (400). id bleibt immer Minimum, die eingebauten Felder bleiben immer BACKEND_ONLY, egal was ankommt. Alle
	 * anderen Felder werden ersetzt.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public SolrField updateSolrField(Long id, SolrFieldType type, boolean indexed, boolean stored, boolean multiValued,
			boolean docValues, boolean required, boolean suggest, SolrFieldReadAccessLevel readAccessLevel,
			SolrFieldClientWriteRule clientWriteRule, String description, int listingPosition) {
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
		solrField.setReadAccessLevel(SolrCore.ID_FIELD_NAME.equals(solrField.getName())
				? SolrFieldReadAccessLevel.MINIMUM_SET_EVERY_READER
				: readAccessLevel);
		solrField.setClientWriteRule(SolrCore.BUILT_IN_FIELD_NAMES.contains(solrField.getName())
				? SolrFieldClientWriteRule.BACKEND_ONLY
				: clientWriteRule);
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
	 * Löscht das Feld aus unserer Tabelle. 409 bei den eingebauten Feldern, die hat jeder Kern. Was in Solr mit dem Feld
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
	 * bleibt ein Datum, der Reindex filtert und sortiert danach. userGrantedAccess bleibt string, indexed, multiValued
	 * und nie required: Die Dokumentgrenze filtert danach (fq), mehrere User je Dokument, und ein Dokument ohne Freigabe
	 * ist der Normalfall. 400 bei jedem Versuch, das zu ändern.
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
		if (SolrCore.USER_GRANTED_ACCESS_FIELD_NAME.equals(solrField.getName())
				&& (type != SolrFieldType.STRING || !indexed || !multiValued || required)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"userGrantedAccess trägt die Dateifreigaben jedes Kerns: string, indexed, multiValued, nicht required");
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
