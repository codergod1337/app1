package codergod1337.app1.solr.document.service;

import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.solr.document.model.SolrDocumentFieldChangeOperation;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Schreib-Mechanik der Spiegelkerne aus ABAS, einmal für alle (User 2026-10-13, vorher privat im
 * SolrDocumentArtikelService): aus den gemappten Werten das Dokument bauen und es anlegen oder per Atomic Update
 * ändern. Kennt keinen Kern und keine Rechte, die Schreibservices der Kerne rufen sie mit ihrem Kern, ihrem Ziel und
 * ihren Sync-Feldern, und sie prüfen vorher selbst, wer schreiben darf.
 *
 * Das Dokument: id ist der Wert des Eintrags mit der Rolle PRIMARY; jedes Feld des Kerns mit command bekommt den Wert
 * seines Keys, nach seinem Typ gewandelt, ohne Wert als null; dazu die drei Sync-Felder. Schreiben: ein neues
 * Dokument voll, ein vorhandenes per Atomic Update mit SET je Feld (User 2026-10-13): null nimmt das Feld heraus, sonst
 * bliebe eine in ABAS gelöschte Verbindung für immer stehen; Felder ohne command, etwa eine Ablage, bleiben unberührt.
 */
@Service
public class SolrMirrorCoreDocumentWriteService {

	private static final Logger log = LoggerFactory.getLogger(SolrMirrorCoreDocumentWriteService.class);

	/** Der Wert des Sync-Status, wenn jeder Subfetch geklappt hat */
	public static final String SYNC_STATUS_OK = "OK";

	/** Der Wert des Sync-Status mit Warnungen: Das Dokument steht, ein Subfetch fehlt, die Gründe stehen im Fehlerfeld */
	public static final String SYNC_STATUS_WARN = "WARN";

	private final SolrFieldService solrFieldService;
	private final SolrDocumentService solrDocumentService;

	public SolrMirrorCoreDocumentWriteService(SolrFieldService solrFieldService,
			SolrDocumentService solrDocumentService) {
		this.solrFieldService = solrFieldService;
		this.solrDocumentService = solrDocumentService;
	}

	/**
	 * Das Dokument, wie es in den Kern geschrieben würde. 400, wenn das Mapping nicht auf targetKey zielt, keinen PRIMARY
	 * hat oder dessen Wert leer ist. Die Fehlerpolitik der Vorlage: Warnungen der Subfetches stoppen nichts, sie stehen
	 * im Dokument, syncStatus WARN findet sie.
	 */
	public Map<String, Object> buildMirrorCoreDocumentFromMappedValues(String coreKey, String targetKey,
			ResponseMappingExecutionResult result, String syncDateFieldName, String syncStatusFieldName,
			String syncErrorFieldName) {
		if (!targetKey.equals(result.targetKey())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Mapping " + result.responseMappingKey() + " zielt nicht auf " + targetKey);
		}
		if (result.primaryIdKey() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Mapping " + result.responseMappingKey() + " hat keinen Eintrag mit der Rolle PRIMARY");
		}
		Object id = firstValue(result.valuesByKey().get(result.primaryIdKey()));
		if (id == null || String.valueOf(id).isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"der PRIMARY-Key " + result.primaryIdKey() + " hat in der Antwort keinen Wert");
		}
		Map<String, Object> document = new LinkedHashMap<>();
		document.put(SolrDocumentService.ID_FIELD_NAME, String.valueOf(id));
		for (SolrField solrField : solrFieldService.getAllSolrFields()) {
			boolean ofCore = solrField.getCoreKey() == null || coreKey.equals(solrField.getCoreKey());
			if (solrField.getCommand() == null || !ofCore
					|| SolrDocumentService.ID_FIELD_NAME.equals(solrField.getName())) {
				continue;
			}
			List<Object> values = result.valuesByKey().get(solrField.getCommand());
			// null bleibt drin: Das Feld hat einen Eintrag im Mapping, nur keinen Wert in dieser Antwort
			document.put(solrField.getName(), values == null ? null : convertForField(solrField, values));
		}
		document.put(syncDateFieldName, Instant.now());
		if (result.warnings().isEmpty()) {
			document.put(syncStatusFieldName, SYNC_STATUS_OK);
			// ein alter Fehlertext aus einem früheren Lauf muss raus
			document.put(syncErrorFieldName, null);
		} else {
			document.put(syncStatusFieldName, SYNC_STATUS_WARN);
			document.put(syncErrorFieldName, String.join(" | ", result.warnings()));
		}
		return document;
	}

	/**
	 * Schreibt das Dokument in den Kern: neu voll, vorhanden per Atomic Update mit SET je Feld. writeProcessName steht
	 * mit Kern und id im Log, z. B. ARTIKEL_WRITE_FROM_MAPPING. Keine Rechteprüfung hier, die macht der Schreibservice
	 * des Kerns vor dem Aufruf.
	 */
	public void writeMirrorCoreDocument(String coreKey, Map<String, Object> document, String writeProcessName) {
		String id = String.valueOf(document.get(SolrDocumentService.ID_FIELD_NAME));
		if (!existsMirrorCoreDocument(coreKey, id)) {
			solrDocumentService.writeNewDocument(coreKey, document);
			log.info("{}: Dokument {} im Kern {} mit {} Feldern angelegt", writeProcessName, id, coreKey, document.size());
			return;
		}
		Map<String, Map<String, Object>> modifiersByFieldName = new LinkedHashMap<>();
		for (Map.Entry<String, Object> field : document.entrySet()) {
			if (!SolrDocumentService.ID_FIELD_NAME.equals(field.getKey())) {
				Map<String, Object> modifier = new LinkedHashMap<>();
				modifier.put(SolrDocumentFieldChangeOperation.SET.getSolrModifier(), field.getValue());
				modifiersByFieldName.put(field.getKey(), modifier);
			}
		}
		solrDocumentService.atomicUpdateDocument(coreKey, id, modifiersByFieldName);
		log.info("{}: Dokument {} im Kern {} per Atomic Update mit {} Feldern geändert", writeProcessName, id, coreKey,
				modifiersByFieldName.size());
	}

	/**
	 * Gibt es das Dokument schon? Echtzeit-Lesen der id ohne Feldgrenze: sieht auch ein Dokument, das Solr noch nicht
	 * aufgefrischt hat (User 2026-10-14, Auffrischen alle 10 s). Sonst würde ein zweites Schreiben derselben id innerhalb
	 * der 10 s voll anlegen statt ändern und die Felder ohne command löschen.
	 */
	private boolean existsMirrorCoreDocument(String coreKey, String id) {
		return solrDocumentService.existsById(coreKey, id);
	}

	private static Object firstValue(List<Object> values) {
		if (values == null) {
			return null;
		}
		return values.stream().filter(value -> value != null && !String.valueOf(value).isBlank()).findFirst()
				.orElse(null);
	}

	/**
	 * Der Wert für ein Feld nach seinem Typ: multiValued als Liste, eine Lücke bei string als leerer Text, damit die
	 * Listen des Mappings zeilengleich bleiben (die index-parallelen Listen der Vorlage); sonst der erste Wert. Was sich
	 * nicht wandeln lässt (ein Datum ohne Form, eine Zahl mit Text), fällt mit einer Zeile im Log weg.
	 */
	private static Object convertForField(SolrField solrField, List<Object> values) {
		if (solrField.isMultiValued()) {
			List<Object> converted = new ArrayList<>(values.size());
			for (Object value : values) {
				Object element = convertValue(solrField, value);
				if (element != null) {
					converted.add(element);
				} else if (solrField.getType().name().equals("STRING")) {
					converted.add("");
				}
			}
			return converted.isEmpty() ? null : converted;
		}
		return convertValue(solrField, firstValue(values));
	}

	private static Object convertValue(SolrField solrField, Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		try {
			return switch (solrField.getType()) {
			case STRING, EMAIL, TEXT_EDGE, TEXT_SUCHE -> text.isEmpty() ? null : text;
			case LONG -> value instanceof Number number ? number.longValue() : Long.valueOf(text);
			case DOUBLE -> value instanceof Number number ? number.doubleValue() : Double.valueOf(text.replace(',', '.'));
			case BOOLEAN -> value instanceof Boolean bool ? bool : parseBoolean(text);
			case DATE -> text.isEmpty() ? null : OffsetDateTime.parse(text).toInstant();
			};
		} catch (NumberFormatException | DateTimeParseException e) {
			log.warn("Feld {}: Wert '{}' passt nicht zum Typ {}, bleibt leer", solrField.getName(), text,
					solrField.getType());
			return null;
		}
	}

	/** true, false, ja, nein und die ABAS-Form (Yes) und (No); alles andere bleibt leer */
	private static Boolean parseBoolean(String text) {
		String normalized = text.replace("(", "").replace(")", "").trim().toLowerCase();
		return switch (normalized) {
		case "true", "yes", "ja", "1" -> Boolean.TRUE;
		case "false", "no", "nein", "0" -> Boolean.FALSE;
		default -> null;
		};
	}

}
