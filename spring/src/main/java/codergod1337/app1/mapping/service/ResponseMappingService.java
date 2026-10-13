package codergod1337.app1.mapping.service;

import codergod1337.app1.mapping.model.ResponseMapping;
import codergod1337.app1.mapping.model.ResponseMappingFetchWay;
import codergod1337.app1.mapping.model.ResponseMappingFetchWayKind;
import codergod1337.app1.mapping.model.ResponseMappingLine;
import codergod1337.app1.mapping.model.ResponseMappingLineIdRole;
import codergod1337.app1.mapping.model.ResponseMappingLineKind;
import codergod1337.app1.mapping.model.ResponseMappingMatchCondition;
import codergod1337.app1.mapping.model.ResponseMappingSubfetchInputParameter;
import codergod1337.app1.mapping.model.ResponseMappingTableColumn;
import codergod1337.app1.mapping.model.ResponseMappingWithLines;
import codergod1337.app1.mapping.repository.ResponseMappingLineRepository;
import codergod1337.app1.mapping.repository.ResponseMappingRepository;
import codergod1337.app1.system.HelperInputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Die Gesamt-Mappings anlegen, ändern, löschen und lesen, immer samt Einträgen. Schreiben nur mit ADMIN. Vor jedem
 * Schreiben wird jeder Eintrag geprüft: Ein Fehler soll hier als 400 mit Klartext auffallen, nicht erst beim Lauf eines
 * Workers. Beim Ändern werden die Einträge komplett ersetzt, der Client schickt immer das ganze Mapping. Das Ausführen
 * eines Mappings gegen eine Antwort kommt als eigener Service.
 */
@Service
public class ResponseMappingService {

	private static final Logger log = LoggerFactory.getLogger(ResponseMappingService.class);

	private static final TypeReference<List<ResponseMappingMatchCondition>> MATCH_CONDITION_LIST = new TypeReference<>() {
	};

	private static final TypeReference<List<ResponseMappingSubfetchInputParameter>> SUBFETCH_INPUT_PARAMETER_LIST = new TypeReference<>() {
	};

	private static final TypeReference<List<ResponseMappingTableColumn>> TABLE_COLUMN_LIST = new TypeReference<>() {
	};

	private static final TypeReference<List<String>> PATH_LIST = new TypeReference<>() {
	};

	private final ResponseMappingRepository responseMappingRepository;
	private final ResponseMappingLineRepository responseMappingLineRepository;
	private final ObjectMapper objectMapper;

	public ResponseMappingService(ResponseMappingRepository responseMappingRepository,
			ResponseMappingLineRepository responseMappingLineRepository, ObjectMapper objectMapper) {
		this.responseMappingRepository = responseMappingRepository;
		this.responseMappingLineRepository = responseMappingLineRepository;
		this.objectMapper = objectMapper;
	}

	/** Alle nach Key, je Mapping seine Einträge. Ohne @PreAuthorize: Der Worker liest sie später ohne Security-Context. */
	public List<ResponseMappingWithLines> getAllResponseMappings() {
		List<ResponseMappingLine> allLines = responseMappingLineRepository.findAllByOrderByResponseMappingIdAscPositionAsc();
		List<ResponseMappingWithLines> result = new ArrayList<>();
		for (ResponseMapping responseMapping : responseMappingRepository.findAllByOrderByKeyAsc()) {
			List<ResponseMappingLine> lines = allLines.stream()
					.filter(line -> responseMapping.getId().equals(line.getResponseMappingId())).toList();
			result.add(new ResponseMappingWithLines(responseMapping, lines));
		}
		return result;
	}

	/** Eines per id samt Einträgen, 404 ohne */
	public ResponseMappingWithLines getResponseMappingById(long id) {
		ResponseMapping responseMapping = findResponseMapping(id);
		return new ResponseMappingWithLines(responseMapping,
				responseMappingLineRepository.findByResponseMappingIdOrderByPositionAsc(id));
	}

	/**
	 * Alle Mappings zu einem Typ samt Einträgen, auch leer: Das Frontend liest den Typ aus einer geholten Antwort und
	 * fragt hier, ob es dazu schon Mappings gibt (User 2026-10-10). 400 ohne Typ.
	 */
	public List<ResponseMappingWithLines> getResponseMappingsByObjectType(String responseObjectType) {
		if (isBlank(responseObjectType)) {
			throw badRequest("responseObjectType fehlt");
		}
		List<ResponseMappingWithLines> result = new ArrayList<>();
		for (ResponseMapping responseMapping : responseMappingRepository
				.findByResponseObjectTypeOrderByKeyAsc(responseObjectType.trim())) {
			result.add(new ResponseMappingWithLines(responseMapping,
					responseMappingLineRepository.findByResponseMappingIdOrderByPositionAsc(responseMapping.getId())));
		}
		return result;
	}

	/** Neu, samt Einträgen. 400 bei ungültigem Key, Typ, Ziel, Bedingung oder Eintrag, 409 wenn der Key vergeben ist. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public ResponseMappingWithLines createResponseMapping(ResponseMappingWithLines newResponseMappingData) {
		ResponseMapping newData = requireResponseMapping(newResponseMappingData);
		if (!HelperInputs.isValidKey(newData.getKey())) {
			throw badRequest("key ist ungültig");
		}
		if (responseMappingRepository.existsByKey(newData.getKey())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		ResponseMapping saved = responseMappingRepository.save(new ResponseMapping(newData.getKey(),
				checkedResponseObjectType(newData.getResponseObjectType()),
				normalizedMatchConditionsJson(newData.getMatchConditionsJson()),
				checkedTargetKey(newData.getTargetKey()), normalizedIgnoredPathsJson(newData.getIgnoredPathsJson()),
				normalizedFetchWayJson(newData.getFetchWayJson())));
		List<ResponseMappingLine> lines = responseMappingLineRepository
				.saveAll(checkedLines(saved.getId(), newResponseMappingData.lines()));
		log.info("Mapping {} angelegt: Typ {}, {} Einträge", saved.getKey(), saved.getResponseObjectType(),
				lines.size());
		return new ResponseMappingWithLines(saved, lines);
	}

	/** Die id bestimmt das Mapping, der Key ändert sich nie; alle anderen Felder und sämtliche Einträge werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public ResponseMappingWithLines updateResponseMapping(long id,
			ResponseMappingWithLines changedResponseMappingData) {
		ResponseMapping changedData = requireResponseMapping(changedResponseMappingData);
		ResponseMapping responseMapping = findResponseMapping(id);
		responseMapping.setResponseObjectType(checkedResponseObjectType(changedData.getResponseObjectType()));
		responseMapping.setMatchConditionsJson(normalizedMatchConditionsJson(changedData.getMatchConditionsJson()));
		responseMapping.setTargetKey(checkedTargetKey(changedData.getTargetKey()));
		responseMapping.setIgnoredPathsJson(normalizedIgnoredPathsJson(changedData.getIgnoredPathsJson()));
		responseMapping.setFetchWayJson(normalizedFetchWayJson(changedData.getFetchWayJson()));
		ResponseMapping saved = responseMappingRepository.save(responseMapping);
		responseMappingLineRepository.deleteByResponseMappingId(id);
		List<ResponseMappingLine> lines = responseMappingLineRepository
				.saveAll(checkedLines(id, changedResponseMappingData.lines()));
		log.info("Mapping {} geändert: {} Einträge", saved.getKey(), lines.size());
		return new ResponseMappingWithLines(saved, lines);
	}

	/**
	 * Neue Positionen der Einträge eines gespeicherten Mappings nach Drag and Drop (User 2026-10-10, wie bei den AR): jede
	 * id bekommt ihre Position, alle in einem Aufruf, nichts anderes am Eintrag ändert sich. 404, wenn eine id kein Eintrag
	 * ist; 400, wenn die ids zu verschiedenen Mappings gehören. Zurück kommt das Mapping mit den Einträgen in neuer Folge.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public ResponseMappingWithLines changeResponseMappingLinePositions(Map<Long, Integer> newPositionById) {
		if (newPositionById.isEmpty()) {
			throw badRequest("keine Positionen");
		}
		List<ResponseMappingLine> changed = new ArrayList<>();
		Long responseMappingId = null;
		for (Map.Entry<Long, Integer> newPosition : newPositionById.entrySet()) {
			ResponseMappingLine line = responseMappingLineRepository.findById(newPosition.getKey())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
							"kein Eintrag mit id " + newPosition.getKey()));
			if (responseMappingId == null) {
				responseMappingId = line.getResponseMappingId();
			} else if (!responseMappingId.equals(line.getResponseMappingId())) {
				throw badRequest("die Einträge gehören zu verschiedenen Mappings");
			}
			line.setPosition(newPosition.getValue());
			changed.add(line);
		}
		if (responseMappingId == null) {
			throw badRequest("keine Positionen");
		}
		responseMappingLineRepository.saveAll(changed);
		log.info("Mapping {}: {} Einträge neu sortiert", responseMappingId, changed.size());
		return getResponseMappingById(responseMappingId.longValue());
	}

	/** Löscht Mapping und Einträge per id, 404 ohne. Ob ein Solr-Feld einen Key daraus nutzt, prüft später die Command-Spalte. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public ResponseMappingWithLines deleteResponseMapping(long id) {
		ResponseMappingWithLines responseMappingWithLines = getResponseMappingById(id);
		responseMappingLineRepository.deleteByResponseMappingId(id);
		responseMappingRepository.delete(responseMappingWithLines.responseMapping());
		log.info("Mapping {} gelöscht", responseMappingWithLines.responseMapping().getKey());
		return responseMappingWithLines;
	}

	// ===== Die Prüfungen =====

	private ResponseMapping findResponseMapping(long id) {
		return responseMappingRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Mapping mit id " + id));
	}

	private static ResponseMapping requireResponseMapping(ResponseMappingWithLines responseMappingWithLines) {
		if (responseMappingWithLines == null || responseMappingWithLines.responseMapping() == null) {
			throw badRequest("responseMapping fehlt");
		}
		return responseMappingWithLines.responseMapping();
	}

	private static String checkedResponseObjectType(String responseObjectType) {
		if (isBlank(responseObjectType)) {
			throw badRequest("responseObjectType fehlt");
		}
		return responseObjectType.trim();
	}

	/** null oder leer heißt kein Ziel, sonst Key-Regel */
	private static String checkedTargetKey(String targetKey) {
		if (isBlank(targetKey)) {
			return null;
		}
		if (!HelperInputs.isValidKey(targetKey.trim())) {
			throw badRequest("targetKey ist ungültig");
		}
		return targetKey.trim();
	}

	/** Die Bedingungen lesen, jede prüfen und normalisiert zurückschreiben; null oder leer heißt keine */
	private String normalizedMatchConditionsJson(String matchConditionsJson) {
		List<ResponseMappingMatchCondition> conditions = parseList(matchConditionsJson, MATCH_CONDITION_LIST,
				"matchConditionsJson");
		int position = 0;
		for (ResponseMappingMatchCondition condition : conditions) {
			position++;
			if (condition == null || isBlank(condition.path()) || condition.equals() == null) {
				throw badRequest("Bedingung " + position + " braucht path und equals");
			}
		}
		return objectMapper.writeValueAsString(conditions);
	}

	/** Die Ignorierliste lesen, leere Adressen weglassen und normalisiert zurückschreiben; null oder leer heißt keine */
	private String normalizedIgnoredPathsJson(String ignoredPathsJson) {
		List<String> paths = parseList(ignoredPathsJson, PATH_LIST, "ignoredPathsJson").stream()
				.filter(path -> !isBlank(path)).map(String::trim).toList();
		return objectMapper.writeValueAsString(paths);
	}

	/** Den Fetchweg lesen, prüfen und normalisiert zurückschreiben; er ist Pflicht */
	private String normalizedFetchWayJson(String fetchWayJson) {
		if (isBlank(fetchWayJson)) {
			throw badRequest("fetchWayJson fehlt");
		}
		ResponseMappingFetchWay fetchWay;
		try {
			fetchWay = objectMapper.readValue(fetchWayJson, ResponseMappingFetchWay.class);
		} catch (RuntimeException e) {
			throw badRequest("fetchWayJson ist kein lesbares JSON: " + e.getMessage());
		}
		if (fetchWay == null || fetchWay.kind() == null) {
			throw badRequest("fetchWayJson: kind fehlt (STERNZEIT, INFOSYS oder JSON)");
		}
		if (fetchWay.kind() == ResponseMappingFetchWayKind.STERNZEIT && isBlank(fetchWay.sternzeit())) {
			throw badRequest("fetchWayJson: Beispiel-Sternzeit fehlt");
		}
		if (fetchWay.kind() == ResponseMappingFetchWayKind.INFOSYS && !HelperInputs.isValidKey(fetchWay.abasInfosysKey())) {
			// den hinterlegten Aufruf kennt das Mapping nur über seinen Key; ob es ihn gibt, zeigt der Lauf
			throw badRequest("fetchWayJson: Infosystem braucht den Key eines hinterlegten Aufrufs");
		}
		return objectMapper.writeValueAsString(fetchWay);
	}

	/** Jeden Eintrag prüfen und als neue Entity am Mapping anlegen, Position ab 1 in der geschickten Reihenfolge; PRIMARY höchstens einmal */
	private List<ResponseMappingLine> checkedLines(Long responseMappingId, List<ResponseMappingLine> lines) {
		List<ResponseMappingLine> checked = new ArrayList<>();
		int position = 0;
		boolean primarySeen = false;
		for (ResponseMappingLine line : lines == null ? List.<ResponseMappingLine>of() : lines) {
			position++;
			if (line == null || line.getKind() == null) {
				throw badRequest("Eintrag " + position + ": kind fehlt");
			}
			String where = "Eintrag " + position + " (" + (isBlank(line.getKey()) ? "ohne Key" : line.getKey()) + ")";
			if (!HelperInputs.isValidKey(line.getKey())) {
				throw badRequest(where + ": key ist ungültig");
			}
			if (isBlank(line.getOnlyIfPath()) != (line.getOnlyIfEquals() == null)) {
				throw badRequest(where + ": Bedingung braucht onlyIfPath und onlyIfEquals");
			}
			if (line.getIdRole() == ResponseMappingLineIdRole.PRIMARY) {
				if (primarySeen) {
					throw badRequest(where + ": PRIMARY gibt es schon, nur eine eindeutige id je Mapping");
				}
				primarySeen = true;
			}
			String sourcePath = null;
			String elementPath = null;
			String abasInfosysKey = null;
			String subfetchInputParametersJson = null;
			String tableColumnsJson = null;
			String tableLineKey = null;
			switch (line.getKind()) {
			case VALUE -> {
				sourcePath = requiredSourcePath(line, where);
				elementPath = blankToNull(line.getElementPath());
			}
			case TABLE -> {
				sourcePath = requiredSourcePath(line, where);
				tableColumnsJson = objectMapper.writeValueAsString(checkedTableColumns(line, where));
			}
			case SUBFETCH_STERNZEIT -> {
				List<ResponseMappingSubfetchInputParameter> parameters = checkedSubfetchInputParameters(line, where);
				if (parameters.size() != 1
						|| !ResponseMappingSubfetchInputParameter.STERNZEIT_PARAMETER_NAME.equals(parameters.get(0).name())) {
					throw badRequest(where + ": der Sternzeit-Fetch braucht genau die Eingabe "
							+ ResponseMappingSubfetchInputParameter.STERNZEIT_PARAMETER_NAME);
				}
				if (isBlank(parameters.get(0).path()) && isBlank(parameters.get(0).value())) {
					throw badRequest(where + ": die Sternzeit kommt aus dem JSON (path) oder statisch (value)");
				}
				subfetchInputParametersJson = objectMapper.writeValueAsString(parameters);
				tableLineKey = checkedTableLineKey(line, lines, where);
			}
			case SUBFETCH_INFOSYS -> {
				if (!HelperInputs.isValidKey(line.getAbasInfosysKey())) {
					throw badRequest(where + ": der Infosystem-Fetch braucht den Key eines hinterlegten Infosystems");
				}
				abasInfosysKey = line.getAbasInfosysKey();
				subfetchInputParametersJson = objectMapper.writeValueAsString(checkedSubfetchInputParameters(line, where));
				tableLineKey = checkedTableLineKey(line, lines, where);
			}
			}
			checked.add(new ResponseMappingLine(responseMappingId, position, line.getKind(), sourcePath, elementPath,
					blankToNull(line.getOnlyIfPath()), line.getOnlyIfEquals(), line.getKey(), line.getIdRole(),
					abasInfosysKey, subfetchInputParametersJson, tableColumnsJson, tableLineKey));
		}
		return checked;
	}

	/** Der Subfetch je Zeile: leer heißt Kopf, sonst muss der Key ein TABLE-Eintrag desselben Mappings sein */
	private static String checkedTableLineKey(ResponseMappingLine line, List<ResponseMappingLine> lines, String where) {
		if (isBlank(line.getTableLineKey())) {
			return null;
		}
		String tableLineKey = line.getTableLineKey().trim();
		boolean found = lines.stream().anyMatch(candidate -> candidate != null
				&& candidate.getKind() == ResponseMappingLineKind.TABLE && tableLineKey.equals(candidate.getKey()));
		if (!found) {
			throw badRequest(where + ": je Zeile von " + tableLineKey + ", aber das ist kein Tabellen-Eintrag dieses Mappings");
		}
		return tableLineKey;
	}

	private static String requiredSourcePath(ResponseMappingLine line, String where) {
		if (isBlank(line.getSourcePath())) {
			throw badRequest(where + ": sourcePath fehlt");
		}
		return line.getSourcePath().trim();
	}

	/** Die Spalten einer Tabelle: mindestens eine, jede mit Namen (ohne Doppelte) und Adresse je Element */
	private List<ResponseMappingTableColumn> checkedTableColumns(ResponseMappingLine line, String where) {
		List<ResponseMappingTableColumn> columns = parseList(line.getTableColumnsJson(), TABLE_COLUMN_LIST,
				where + ": tableColumnsJson");
		if (columns.isEmpty()) {
			throw badRequest(where + ": eine Tabelle braucht mindestens eine Spalte");
		}
		List<ResponseMappingTableColumn> checked = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (ResponseMappingTableColumn column : columns) {
			if (column == null || isBlank(column.name()) || isBlank(column.elementPath())) {
				throw badRequest(where + ": jede Spalte braucht name und elementPath");
			}
			if (names.contains(column.name().trim())) {
				throw badRequest(where + ": Spalte " + column.name().trim() + " kommt zweimal vor");
			}
			names.add(column.name().trim());
			checked.add(new ResponseMappingTableColumn(column.name().trim(), column.elementPath().trim()));
		}
		return checked;
	}

	/** Die Eingaben eines Subfetchs: jede mit Namen, path oder value oder keins von beiden, nie beides */
	private List<ResponseMappingSubfetchInputParameter> checkedSubfetchInputParameters(ResponseMappingLine line,
			String where) {
		List<ResponseMappingSubfetchInputParameter> parameters = parseList(line.getSubfetchInputParametersJson(),
				SUBFETCH_INPUT_PARAMETER_LIST, where + ": subfetchInputParametersJson");
		List<ResponseMappingSubfetchInputParameter> checked = new ArrayList<>();
		for (ResponseMappingSubfetchInputParameter parameter : parameters) {
			if (parameter == null || isBlank(parameter.name())) {
				throw badRequest(where + ": jede Eingabe braucht einen Namen");
			}
			if (!isBlank(parameter.path()) && !isBlank(parameter.value())) {
				throw badRequest(where + ": Eingabe " + parameter.name() + " hat path und value, nur eins von beiden");
			}
			checked.add(new ResponseMappingSubfetchInputParameter(parameter.name().trim(), blankToNull(parameter.path()),
					blankToNull(parameter.value())));
		}
		return checked;
	}

	/** Die Eingaben eines Subfetch-Eintrags, gelesen; für die Ausführung und die Masken */
	public List<ResponseMappingSubfetchInputParameter> subfetchInputParametersOf(ResponseMappingLine line) {
		return parseList(line.getSubfetchInputParametersJson(), SUBFETCH_INPUT_PARAMETER_LIST,
				"Eintrag " + line.getKey() + ": subfetchInputParametersJson");
	}

	/** Die Spalten eines Tabellen-Eintrags, gelesen */
	public List<ResponseMappingTableColumn> tableColumnsOf(ResponseMappingLine line) {
		return parseList(line.getTableColumnsJson(), TABLE_COLUMN_LIST, "Eintrag " + line.getKey() + ": tableColumnsJson");
	}

	/** Eine JSON-Liste lesen; null oder leer ist eine leere Liste, unlesbar ist 400 mit dem Grund */
	private <T> List<T> parseList(String json, TypeReference<List<T>> listType, String fieldName) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			List<T> list = objectMapper.readValue(json, listType);
			return list == null ? List.of() : list;
		} catch (RuntimeException e) {
			throw badRequest(fieldName + " ist kein lesbares JSON: " + e.getMessage());
		}
	}

	private static ResponseStatusException badRequest(String reason) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static String blankToNull(String text) {
		return isBlank(text) ? null : text.trim();
	}

}
